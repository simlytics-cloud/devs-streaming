# Exact time wire v1

## Scope and compatibility

`devs-exact-time-v1` is an explicitly selected framework timestamp profile, not unchanged
numeric ISO JSON compatibility. [devs-messages-v2.schema.json](../devs-messages-v2.schema.json)
is its versioned successor message schema; the [numeric baseline](../iso-21175-2-messages.schema.json)
and its identifier remain unchanged. Schema version and time-profile version are separate.

Four capabilities remain distinct:

1. Exact standalone, observation, message-field and schedule-key JSON round-trips.
2. Decimal-native remote communication with matching endpoint policy and native representability.
3. Canonical rational recording/transport for explicitly compatible endpoints, without adaptation.
4. Distributed enhanced coordinators: **not supported by this profile**. Enhanced context export
   remains blocked. A future phase needs clock identity/origin initialization, remote hierarchy
   validation, routing/return identity, deterministic ordering, duplicate/restart and protocol work.

Long/double message timestamps retain JSON numbers and the original long-first/double-fallback
parser, including accepted legacy strings and overflow behavior. `1.0`/`1e0` remain double,
whereas tagged decimal `"1"` remains `DecimalSimTime`; rational `1/1` remains `QuantizedSimTime`.
Typed readers reject mismatched tags. No new value is converted through double or rounded.

Default `DevsObjectMapper.buildObjectMapper()` supports new **standalone/observation** objects,
while default message fields, schedule keys, and transport remain legacy. Historical standalone
long/double objects retain `timeType: "devs.iso.time.LongSimTime"`/`DoubleSimTime` and numeric `t`.
New time tags never resolve Java class names. Payload/envelope class metadata is not redesigned;
the language-neutral timestamp contract does not claim all arbitrary payloads are portable.

## Tagged timestamps

| Value | JSON |
|---|---|
| Decimal tenth | `{"timeType":"decimal","value":"0.1"}` |
| Decimal zero / integer | `{"timeType":"decimal","value":"0"}` / `{"timeType":"decimal","value":"1"}` |
| Negative / precise | `{"timeType":"decimal","value":"-12.5"}` / `{"timeType":"decimal","value":"0.12345678901234567890123456789"}` |
| Large / small exponent | `{"timeType":"decimal","value":"1E+1000"}` / `{"timeType":"decimal","value":"1E-1000"}` |
| Decimal infinity | `{"timeType":"decimal","infinity":"positive"}` |
| Rational third / minute | `{"timeType":"rational","numerator":"1","denominator":"3"}` / `{"timeType":"rational","numerator":"1","denominator":"60"}` |
| Rational zero / integer | `{"timeType":"rational","numerator":"0","denominator":"1"}` / `{"timeType":"rational","numerator":"1","denominator":"1"}` |
| Negative rational | `{"timeType":"rational","numerator":"-2","denominator":"3"}` |
| Large rational | `{"timeType":"rational","numerator":"123456789012345678901234567890","denominator":"7"}` |
| Rational infinity | `{"timeType":"rational","infinity":"positive"}` |

Timestamp objects are closed, with exactly one discriminator. Finite and infinity fields are
mutually exclusive. No null, NaN, negative infinity, numeric components, zero-denominator infinity,
legacy sentinel, actor reference or Java class name is valid in a new timestamp object.

Decimal grammar (ASCII, full string, no whitespace):

```text
-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?
```

Reject leading `+`, leading integer zeros, `.1`, `1.`, commas, hex, whitespace and nonfinite words.
Parse directly as `BigDecimal` without a rounding context. Accept exponents/trailing zeros;
output uses normalized `stripTrailingZeros().toString()`, with zero `"0"`: `1.2300` becomes `1.23`,
`1000.00` becomes `1E+3`, `-0.00` becomes `0`. Preservation promises mathematical value and concrete
type, **not** spelling, scale, signed zero or exponent spelling.

Numerator grammar: `-?(0|[1-9][0-9]*)`; denominator: `[1-9][0-9]*`. Components must be strings.
Reject leading `+`/zeros, whitespace, and signed/zero denominators. Normalize bounded `2/6` to
`1/3`, every zero (`0/99`, `-0`) to `0/1`, and emit reduced fractions with positive denominators.
Wire rejection of negative denominators is deliberate despite local constructor normalization.

## Fixed symmetric resource limits

- 4,096 decimal coefficient digits before normalization and at most 4,096 normalized precision.
- Absolute explicit exponent and effective scale before/after normalization at most 10,000.
  The canonical scientific exponent is also checked on **input and output**, avoiding accepted
  inputs that cannot round-trip under the same ceiling.
- 4,096 digits per rational component, excluding numerator sign, before/after reduction.
- 8,256 characters per timestamp string or complete object fragment, including actual incoming
  whitespace/escapes. Bounds are checked while parsing, before arbitrary-precision construction/GCD,
  and before output/publication. Unrelated payload strings do not inherit timestamp limits.

Higher limits require a separately agreed profile. Schema validates lexical forms, component
lengths, discriminator, exclusive finite/infinite shape, marker and original envelope/payload
constraints. Runtime additionally validates exponent/scale/precision, full fragment size,
duplicate keys, normalization, concrete types, native mapping/lattices and endpoint permission.
Protocol chronology, shared clock and supplier sentinel semantics remain independent checks.

Malformed or unsupported times produce bounded Jackson mapping errors with field paths and
stable categories: `malformed-time`, `unsupported-time-type`, `time-limit-exceeded`,
`profile-mismatch`. No default zero, object-to-double fallback or cross-type coercion is allowed.
Transport inbound paths log only bounded categories and drop invalid records; outbound validation
and encoding precede publication, sequence advancement, parent capture and termination effects.

## Endpoint and mapper selection

```java
ObjectMapper archiveMapper = DevsObjectMapper.buildObjectMapper();
ObjectMapper exactFieldMapper = DevsObjectMapper.buildObjectMapper(TimeWireProfile.EXACT_TIME_V1);
TimeEndpointPolicy policy = TimeEndpointPolicy.decimalNative();
DevsJsonBinding binding = new DevsJsonBinding(policy);
String payload = binding.encode(message);
DevsSimMessage decoded = binding.decode(payload);
```

The profile-selected mapper handles fields/keys; use `DevsJsonBinding` for portable message
envelopes and endpoint validation/markers, not mapper calls alone. Constructor/factory overloads
in core proxies and [Kafka adapters](../../devs-streaming-kafka/README.md) take policy explicitly.

Policies:

- `legacy()`: existing long/double transport with no marker.
- `decimalNative()`: exact profile accepting decimal fields only.
- `nativeEndpoint(type, mapping)`: exact profile plus existing native lattice/representability
  validation of both `eventTime` and `nextInternalTime`, including any declared native-zero
  `timeGranularity` grid. It never adapts time or accepts rational as a supplier-native encoding.
- `rationalCompatible()`: exact profile accepting canonical rational fields.
- `recorder(Set.of(...))`: exact profile with explicitly declared supported concrete time types.

Convenience policies such as `decimalNative()` select concrete types/profile; they do not
discover supplier metadata or enforce an undeclared grid. In Java, use
`TimeSemantics.of(scale, origin)` for absent granularity, or
`TimeSemantics.of(scale, origin, Optional.of(TimeGranularity.of(spacing)))` for a declared
positive rational spacing in native units. Absence leaves codec constraints intact. A grid is
anchored at native zero and describes unsupported off-grid timestamps (including exceptions,
silent rounding, or wrong behavior), not a minimum advance, tolerance, or common tick.
Initialization and reports remain exact and unadapted. Only local child-input delivery
uses automatic codec-owned upward adaptation, keeping source event time separate from
effective delivery time; endpoint validation, serialization, and transport never round.

Exact messages always require `wireProfile: "devs-exact-time-v1"`, including numeric or
timestamp-free messages. Exact endpoints reject missing/unsupported markers; legacy endpoints
reject markers and object timestamp fields. There is no inferred profile or silent downgrade.
Exact envelope message vocabulary is preflighted before legacy dynamic message resolution.
Top-level reserved `enhancedClock`, `enhancedContext`, `clockContext`, `clockId`, `compositionOrigin`,
`executionClock`, `coordinatorExecutionConfig`, and `parent` are rejected in exact messages;
unrelated additional-property behavior is unchanged.

```json
{
  "wireProfile": "devs-exact-time-v1",
  "messageType": "NextInternalTimeReport",
  "simulationRunId": "run-1",
  "messageId": "report-1",
  "senderId": "decimal-model",
  "receiverId": "coordinator",
  "eventTime": {"timeType": "decimal", "value": "0.1"},
  "nextInternalTime": {"timeType": "decimal", "infinity": "positive"}
}
```

Local `SimulationInitMessage` wrappers are validated, enhanced context rejected, and only the
wrapped `SimulationInit` serialized. Receivers reconstruct the local actor return path; actors
and clock context are never serialized. Bind remote native models through their native codecs:
coordinator conversion remains local, and only child-input delivery through the binding,
mapping, and native codec may adapt values upward. Developers MUST validate this adaptation's
suitability before coupling; runtime safeguards are not modeling validation. Parent outputs
and coordinator canonical identity remain exact, with no per-route delivery policy.
Rational `1/3` cannot be silently sent to a decimal/long/double-native endpoint.

`DecimalTimeCodec`'s immutable `adaptationScale` (default 18 fractional digits, configurable
by its `int` constructor) affects that local input adaptation only. Exactly acceptable
decimals retain their value even at finer precision; nonterminating bounds adapt upward
at the configured scale, and declared granularity intersects encoding constraints
algebraically. This resolution is neither supplier metadata nor a wire precision limit.
The breaking removal of `DeliveryRule` and addition of native codec `encodeCeiling`
(one-argument and grid overloads), retaining the existing grid-search `nextRepresentable`, are
documented in [Time Semantics](time-semantics.md#contract-migration-and-design-limits);
they do not change exact encoding/decoding, transport profiles, or persistence formats.

Exact schedule keys are opt-in: `exact-v1:decimal:<canonical-text>` and
`exact-v1:rational:<n>/<d>`, with `+infinity` in place of finite text. Numeric and historical
prefixed keys retain their behavior. Homogeneous sorted-map comparison rules remain unchanged;
mixed incomparable native types are not made valid. `KafkaSimTimeKey` is only a public legacy
compatibility artifact, not an active record-key strategy.

## Observation storage and migration

JSON, print streams and JSONL use the standalone binding. Mongo serializes to JSON then BSON;
decimal/rational components remain strings, with no Decimal128 conversion. PostgreSQL stores
time, payload and the whole document as JSONB; standalone and nested times agree. No column
migration or historical archive rewrite is needed, and IDs/catalog/shutdown behavior is unchanged.

Mongo's existing `(runId, branchId, time)` index, BSON object order and JSONB/text comparisons are
**not exact chronological ordering**. Use exact application comparison or design a separately
approved sortable key/index. `Branch.forkTime` remains a separate Double API.

Migration sequence:

1. Upgrade readers first; old readers may reject new exact observations.
2. Configure matching profiles and concrete time permissions at both endpoints.
3. Enable exact publishers; retain legacy constructors for existing numeric peers.
4. External peers must parse strings with exact arithmetic, recognize typed infinity, and honor
   grammar/limits and endpoint policy. Numeric schema acceptance alone is not negotiation.
5. Preserve Kafka run-ID String keys, all existing headers, group IDs, partitions and run filters.

`RemoteModelStarter` and external DTO-backed server startup remain legacy. No unsupported server
capability fields are invented; use direct additive adapters with agreed compatible endpoints.
Deployed servers, generated clients, archive/replay tools and database queries require independent
compatibility review. OpenAPI status remains supplier-native encoded **string** time; rational
canonical status would need a separate definition and explicit clock identity/origin. Supplier
`TimeValueEncoding`, int64 rational unit/granularity/origin components and native infinity sentinel policies are
unchanged by rational wire support.

The separate supplier metadata OpenAPI contract is version `2.0.0`: optional `timeGranularity`
is a direct rational object such as `{"numerator":1,"denominator":1000}`, not a nested wrapper.
Omit it when absent; null and obsolete `quantum` are rejected, with no alias. Java removal of
`quantum()`, nullable granularity arguments, and `discrete()` is breaking; migrate to
`Optional<TimeGranularity>` and recompile downstream consumers. This metadata migration changes
no timestamp, message, profile, schedule-key, or persistence format and does not bump
`devs-exact-time-v1`.

## Validation and integration gates

`mvn test` runs codecs, schemas/YAML, actor-kit/in-memory transport, JSONL, BSON conversion and
JSONB serialization consistency without external services. The native mapping/delivery suites
remain independent regressions. Kafka broker tests require `-Ddevs.kafka.integration=true`;
live storage requires `-Ddevs.mongodb.integration=true` or `-Ddevs.postgresql.integration=true`
and the configuration documented in each module README. These tests compile normally, but
skipped gated cases are not counted as passing live interoperability tests.