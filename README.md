# DEVS Streaming Framework
*Protocol for composing Distributed DEVS Models Across different
Simulation Frameworks*

The Discrete Event System Specification Streaming Framework (DEVS-SF) is
a data exchange specification that allows composition of DEVS models
running on different computers, potentially built with different DEVS
frameworks, into a single simulation run. It is a JSON data
specification developed for exchange over commercially available
streaming platforms, such as Apache Kafka, so simulation architectures
can be built using open-source technologies. The use of the DEVS-SF by
an existing DEVS implementation requires the development of a DEVS
simulator to consume DEVS-SF messages from an event stream, execute its
associated atomic or coupled model, and publish events back to an event
stream. DEVS-SF also enables the development of DEVS wrappers around
non-modular simulation architectures, such as High Level Architecture
(HLA) of the US Army's [Bifrost server](https://www.peostri.army.mil/onesaf), so they may execute as models
in a DEVS simulation. 

See [DEVS-SF Overview](docs/import/DEVS%20Streaming%20Framework%20Introduction.md) for more information.

## Enhanced logical-time coordination

The Java core supports locally configured mixed native clocks through
`CoupledModelFactory.enhanced()`, `PDevsCoordinator.enhanced()`, and
`RootCoordinator.enhanced()`. Typed child bindings map native long/double/decimal
timestamps to exact decimal `DecimalSimTime` seconds with
distinguished infinity; there is no common coordinator tick lattice. Developers
supply clock alignment, codecs, and optional per-route `TimingErrorBudget` monitors.
`CoordinatorExecutionConfig(clock)` defaults to no monitors;
`CoordinatorExecutionConfig(clock, budgets)` accepts a budget map. Neither carries
child order; no delivery rule is required. Child inputs automatically adapt
upward through their mapping and codec; parent external outputs and canonical
coordinator identity stay exact. Adapted inputs remain queued while intervening
events execute. Developers MUST validate adaptation suitability **before coupling**;
the runtime performs no modeling validation.

Legacy and enhanced coordinator/factory construction defaults to child identifiers
sorted by Java natural, case-sensitive `String` order, not map/provider insertion
order. Custom-order overloads accept `List<String>` before the optional tracker
(or factory tracker supplier), preserve its exact sequence in an immutable snapshot,
and reject null lists/elements, blank or duplicate identifiers, and missing or unknown
children. Ordering controls coordinator sends and bag assembly, not actor execution
or completion order: actors are not serialized, and whole-run reproducibility is
not guaranteed. Legacy map-based and recipient-first/two-pass ordering intentionally
changes to one resolved-order pass over affected children.

Migrate `CoordinatorExecutionConfig(clock, order[, budgets])` to
`CoordinatorExecutionConfig(clock[, budgets])`; omit the old list for natural order,
or move a deliberate custom list to coordinator/factory construction. See the guide
for a separate nonnatural-order example and the schedule's separate resolved-order input.

`DecimalTimeCodec` has immutable configurable `adaptationScale` (default 18 fractional
digits): acceptable exact decimals remain unchanged even at finer precision, while
nonterminating input bounds adapt upward at that scale. Declared granularity is
intersected algebraically with representation constraints. This resolution is not
supplier metadata. Finite-result and causality safeguards fail impossible deliveries;
initialization, reports, exact encoding/decoding, serialization, and transport do not round.

Supplier-native `TimeSemantics.timeGranularity()` is an `Optional<TimeGranularity>`:
use `TimeSemantics.of(scale, origin)` for no additional grid, or
`TimeSemantics.of(scale, origin, Optional.of(TimeGranularity.of(spacing)))` for a
positive decimal spacing anchored at native zero. Absence does not remove codec
constraints. Declare a grid when off-grid timestamps are unsupported, including
models that throw, silently round, or behave incorrectly; it is not a minimum
advance, tolerance, or common coordinator tick.

Enhanced coordinator hierarchies share an `EnhancedClockContext` and cannot mix
legacy coordinators. Deprecated legacy entry points remain available for separate
common-time simulations. Pacing is model-owned, not a coordinator wall-clock
deadline or lag-recovery mechanism. Transport defaults remain legacy numeric
long/double; explicitly configured exact-v1 endpoints also support decimal-native
communication and decimal-compatible recording. Every profile rejects enhanced
initialization context before publishing: exact JSON does not establish a remote
enhanced clock hierarchy.

See [Time Semantics](docs/time-semantics/time-semantics.md) for the Java construction
example, initialization requirements, delivery behavior, and tested limits.

## Exact JSON and opt-in transport

The default `DevsObjectMapper` now supports standalone decimal/decimal values and
observations using language-neutral tagged string components and typed positive
infinity. Historical long/double objects and legacy message scalar decoding remain
readable without archive rewrites. Exact transport requires explicit matching
`TimeEndpointPolicy` configuration and `wireProfile: "devs-exact-time-v1"`; it is
never inferred from a generic parameter or runtime time type.

See [Exact time wire v1](docs/time-semantics/exact-time-wire-v1.md) for limits,
grammars, examples and migration, and the [Kafka guide](devs-streaming-kafka/README.md)
for additive adapter APIs. The [v2 message schema](docs/devs-messages-v2.schema.json)
is a framework extension, not unchanged numeric ISO compatibility; the original
[numeric schema](docs/iso-21175-2-messages.schema.json) is preserved.

Observation JSON/JSONL, BSON string components and PostgreSQL JSONB preserve exact
values without numeric casts or database schema migration. Object/text ordering,
including Mongo's existing `time` index, is not exact chronological ordering.
Native mapping, representation and any declared grid constraints remain mandatory;
decimal serialization does not make `1/3` representable as a finite native decimal.

The delivery API migration is breaking: remove `DeliveryRule` maps, use
`CoordinatorExecutionConfig(clock, budgets)` with a `Map<InteractionKey, TimingErrorBudget>`,
or the clock-only constructor without monitors,
and call `ChildBinding.adapt(source)` without a rule. Native codecs add `encodeCeiling`
with one-argument and grid overloads; the existing grid-search `nextRepresentable`,
`DecimalTimeMapping.nextRepresentable`, and exact `encode`/`decode` remain available without
a codec-method rename. Revalidate former exact-only/custom-policy
compositions and recompile consumers; see the time-semantics guide for details.

The Java removal of `quantum()`, nullable granularity arguments, and `discrete()` is
breaking: migrate construction and recompile downstream consumers. Supplier metadata
OpenAPI version `2.0.0` uses an optional direct decimal `timeGranularity` object
`{"numerator":1,"denominator":1000}`; omit it when absent, never send null.
Obsolete `quantum` is rejected, with no alias. Timestamp, message, profile, and
persistence formats are unchanged; this metadata migration does not bump exact-v1.

Run `mvn test` for broker/database-free validation. Kafka and live database tests
have explicit opt-in gates documented in their module READMEs; skipped integration
tests are not evidence of live interoperability. `RemoteModelStarter` and external
server negotiation remain legacy, and distributed enhanced execution is deferred.



