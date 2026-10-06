# MongoDB observation persistence

The sink uses the default `DevsObjectMapper` standalone time binding. Historical
long/double objects retain their Java-qualified `timeType` and numeric `t` layout.
Decimal times use `timeType: "decimal"` with a string `value`; rational times use
`timeType: "rational"` with string `numerator` and `denominator`. Both exact types
represent positive infinity with `infinity: "positive"` and no finite components.
The JSON-to-BSON path keeps exact components as strings, not `Decimal128`.
No schema migration, numeric conversion, or historical rewrite is required.

The existing `(runId, branchId, time)` index remains unchanged. BSON object/string
ordering is **not exact numerical chronological ordering**, including within an
exact time type or across mixed historical/new types. Retrieve observations by
IDs; decode times for any application-level numerical comparison.

## Tests

Database/broker-free serialization tests run by default. Live tests are skipped
unless `devs.mongodb.integration=true` is explicitly supplied. From the repository root:

```sh
mvn -pl devs-observation-mongodb -am test
mvn -pl devs-observation-mongodb -am -Ddevs.mongodb.integration=true test
```

Live tests require an already-running MongoDB instance, configured through
`mongodb.connection_string` (test default: `mongodb://localhost:27017/`). They
create randomly named test databases and drop them afterward. The gate does not
start a database. The live coexistence test reads by observation IDs after actor
restart and shutdown, and checks the type catalog without sorting by time.