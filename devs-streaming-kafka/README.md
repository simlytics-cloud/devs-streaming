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

Retention can be time- or size-based; topic deletion between runs is no longer needed. By default,
`RemoteModelStarter.startRemoteModel` retains an existing topic or creates it when absent.

To intentionally reuse a `runId` during debugging, call the overload accepting
`TopicResetMode.RECREATE`. It deletes the topic, waits for Kafka to finish the asynchronous deletion,
and then creates a fresh topic. This is destructive and must be selected explicitly; it is never
inferred from a repeated `runId`.

### Shared client properties

```hocon
kafka {
  properties {
    bootstrap.servers = "localhost:9092"
    security.protocol = SASL_SSL
    sasl.mechanism = PLAIN
    sasl.jaas.config = "org.apache.kafka.common.security.plain.PlainLoginModule required username='user' password='password';"

    # Optional standard Kafka producer or consumer tuning goes here.
    # e.g. linger.ms = 5
  }
}
```

Pass `config.getConfig("kafka.properties")` to every Kafka adapter and to
`RemoteModelStarter`. These are standard Apache Kafka client properties and are shared by the
producer, consumer, and admin client.

The library supplies String serializers for the producer and String deserializers for the consumer.
It also defaults the consumer to `enable.auto.commit=false` and `auto.offset.reset=earliest`; a
configured value overrides either default. Pekko Kafka connector settings are internal, so users do
not need to extend or configure `pekko.kafka.consumer` for normal use. The runtime always sets the
consumer group ID to `runId:receiverId`; do not configure `group.id`.

### Remote producer / consumer (non-Java clients)

If a remote component publishes records directly (not through this library), it must:
1. Serialize the key as a UTF-8 string equal to `runId`.
2. Set the `X-Run-Id`, `X-Receiver-Id`, and `X-Sequence` headers on every record.
3. Set `auto.offset.reset=earliest` and use a group ID of the form `runId:receiverId`.

A consumer on the remote side should filter on `X-Run-Id` before processing the payload.

## Key classes

**`KafkaLocalProxy`** — the primary entry point for most use cases. Creates a matched publisher
and receiver pair in a single actor. Configure it with `ProxyProperties`, which carries `runId`,
the local and remote component names, the producer and consumer topics, and one shared Kafka
configuration. `KafkaProxySimulatorProvider` is the corresponding `DevsSimulatorProvider` for use with
`CoupledModelFactory`.

**`KafkaDevsStreamProxy`** — one-way Kafka publisher actor. Use this when you only need to send
DEVS messages outbound (e.g., to a remote simulator that handles its own receive path). Requires
`componentName`, `runId`, the producer topic, and the shared Kafka configuration.

**`KafkaReceiver`** — one-way Kafka consumer actor. Subscribes to a topic and forwards matching
messages to a local DEVS actor. Requires `receiverId`, `runId`, the consumer topic, and the shared
Kafka configuration from `kafka.properties`.

**`KafkaMessagePublisher`** — low-level `MessagePublisher` implementation wrapping
`KafkaProducer<String, String>`. Keyed by `runId`; adds the three standard headers on every
`publish` call. Generally used indirectly through the actors above.

**`KafkaMessageReceiver`** — low-level `MessageReceiver` implementation wrapping Pekko Kafka's
`Consumer.plainSource`. Filters by `X-Run-Id` header, uses stable group ID, and passes
deserialized payloads to a handler. Generally used indirectly through the actors above.

**`KafkaUtils`** — static helpers that adapt the shared Kafka configuration for an `AdminClient`,
Pekko consumer settings, and `KafkaProducer<String, String>` (via `createStringKeyProducer`).

**`RemoteModelStarter`** — starts a remote model using one shared Kafka configuration. It forwards
the topic and all configured Kafka client properties, including broker and security settings, to the
remote model. Construct a `RemoteModelStartRequest` and call
`startRemoteModel(request)`; the request makes topic reset behavior and run-ID suffix generation
explicit.
