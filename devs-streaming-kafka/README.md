# devs-streaming-kafka

Kafka transport adapter for the DEVS Streaming Framework. It bridges DEVS coordinator/simulator
actors across process boundaries by publishing and consuming ISO-21175 JSON messages over Apache
Kafka. Multiple simulation runs can share a single topic simultaneously; per-run isolation is
achieved through `runId`-based record keying and a lightweight header filter rather than topic
deletion.

## How it works

Every published record is keyed by `runId` (a String), which routes all messages for one run to
the same Kafka partition and preserves FIFO ordering within that run. Three headers are attached
to each record before it leaves the producer:

| Header | Content |
|---|---|
| `X-Run-Id` | simulation run identifier |
| `X-Receiver-Id` | target component name |
| `X-Sequence` | monotonic sequence number (UTF-8 string) |

On the consumer side, records are filtered by `X-Run-Id` before the JSON payload is deserialized,
so stale messages from prior runs are dropped cheaply. The consumer group ID is
`runId:receiverId`, giving stable, run-scoped offset tracking and crash resumability.

## Kafka configuration

### Topic

Create one shared topic with enough partitions to support your expected concurrency (one partition
per simultaneous run is a safe starting point). Single-partition topics work for sequential runs
but limit parallelism.

```
kafka-topics.sh --create --topic devs-sim \
  --partitions 8 --replication-factor 1 \
  --bootstrap-server localhost:9092
```

Retention can be time- or size-based; topic deletion between runs is no longer needed.

### Producer properties (Pekko config block)

```hocon
kafka-producer {
  bootstrap.servers = "localhost:9092"
  # Key and value serializers are set automatically by KafkaUtils.createStringKeyProducer.
  # Add any additional producer tuning here (e.g. linger.ms, batch.size).
}
```

### Consumer properties (Pekko config block)

```hocon
kafka-consumer {
  bootstrap.servers = "localhost:9092"
  auto.offset.reset = earliest   # required: late-joining consumers replay from the beginning
  enable.auto.commit = false      # offset management is handled by the Pekko Kafka stream
  # Group ID is set at runtime to "runId:receiverId"; do not set group.id here.
}
```

Pass these config blocks to `KafkaLocalProxy.ProxyProperties`, `KafkaDevsStreamProxy.create`, or
`KafkaReceiver.create` as shown in the class descriptions below.

### Remote producer / consumer (non-Java clients)

If a remote component publishes records directly (not through this library), it must:
1. Serialize the key as a UTF-8 string equal to `runId`.
2. Set the `X-Run-Id`, `X-Receiver-Id`, and `X-Sequence` headers on every record.
3. Set `auto.offset.reset=earliest` and use a group ID of the form `runId:receiverId`.

A consumer on the remote side should filter on `X-Run-Id` before processing the payload.

## Key classes

**`KafkaLocalProxy`** — the primary entry point for most use cases. Creates a matched publisher
and receiver pair in a single actor. Configure it with `ProxyProperties`, which carries `runId`,
the local and remote component names, the producer and consumer topics, and their Pekko config
blocks. `KafkaProxySimulatorProvider` is the corresponding `DevsSimulatorProvider` for use with
`CoupledModelFactory`.

**`KafkaDevsStreamProxy`** — one-way Kafka publisher actor. Use this when you only need to send
DEVS messages outbound (e.g., to a remote simulator that handles its own receive path). Requires
`componentName`, `runId`, `receiverId`, the producer topic, and a Pekko producer config block.

**`KafkaReceiver`** — one-way Kafka consumer actor. Subscribes to a topic and forwards matching
messages to a local DEVS actor. Requires `receiverId`, `runId`, the consumer topic, and a Pekko
consumer config block.

**`KafkaMessagePublisher`** — low-level `MessagePublisher` implementation wrapping
`KafkaProducer<String, String>`. Keyed by `runId`; adds the three standard headers on every
`publish` call. Generally used indirectly through the actors above.

**`KafkaMessageReceiver`** — low-level `MessageReceiver` implementation wrapping Pekko Kafka's
`Consumer.plainSource`. Filters by `X-Run-Id` header, uses stable group ID, and passes
deserialized payloads to a handler. Generally used indirectly through the actors above.

**`KafkaUtils`** — static helpers for creating an `AdminClient`, a `KafkaConsumer`, and
`KafkaProducer<String, String>` (via `createStringKeyProducer`). `deleteTopics` is retained for
compatibility but deprecated; run isolation no longer requires it.
