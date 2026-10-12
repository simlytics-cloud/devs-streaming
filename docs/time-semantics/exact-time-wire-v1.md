# Exact time wire profile v1

`devs-exact-time-v1` is a decimal-only exact timestamp profile. Finite timestamps are
`{"timeType":"decimal","value":"<decimal string>"}`; positive infinity is
`{"timeType":"decimal","infinity":"positive"}`. Values are normalized `BigDecimal`
values and all components are strings. The profile rejects unknown fields, numeric decimal
values, removed timestamp tags and fraction-component forms.

Legacy long and double timestamp objects and numeric schedule keys remain supported under the
legacy numeric profile. Exact schedule keys use `exact-v1:decimal:<value>` (or `+infinity`).
Endpoint policies accept decimal timestamps only; this serialization capability does not permit
enhanced coordinator initialization or relax local mapping checks.
