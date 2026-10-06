# PostgreSQL observation persistence

The sink uses the default `DevsObjectMapper` standalone time binding for the
`time` JSONB column and the nested observation time in `document` JSONB. Historical
long/double objects keep their Java-qualified `timeType` and numeric `t` layout.
Exact decimals have a string `value`; exact rationals have string `numerator`
and `denominator`, with `timeType` tags `decimal` and `rational`. Typed positive
infinity uses `infinity: "positive"` without finite components. Payload and whole
document serialization are unchanged; no SQL schema change, production numeric
conversion, or historical rewrite is required.

JSONB object or text ordering is not exact numerical chronological ordering.
Retrieve observations by IDs and decode times for application-level numerical
comparison rather than ordering by the time JSON or its text representation.

## Tests

Database/broker-free tests run by default. Live tests are skipped unless
`devs.postgresql.integration=true` is explicitly supplied. From the repository root:

```sh
mvn -pl devs-observation-postgresql -am test
mvn -pl devs-observation-postgresql -am -Ddevs.postgresql.integration=true test
```

Live tests require an already-running PostgreSQL instance and the
`devs.observation.postgresql` settings in the test `application.conf` (JDBC URL,
username, password, schema). Use only a disposable test database/schema: the
tests create the database if absent and **drop/recreate the configured schema
before each test**. The account needs those privileges, and
`drop-schema-on-shutdown` must be false for readback. The gate does not start a
database. Coexistence checks retrieve rows by observation IDs after actor restart
and shutdown, verifying time, payload, document, and catalog contents.