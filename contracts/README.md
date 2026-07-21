# VoltWise contracts

This directory is the language-neutral contract source for VoltWise HTTP payloads
and Kafka events. JSON Schemas use JSON Schema Draft 2020-12. Concrete payloads
live in [`examples`](examples), while reusable definitions and validation rules
live in [`schemas`](schemas).

## Kafka topics

| Topic | Producer | Consumer | Contract |
| --- | --- | --- | --- |
| `voltwise.asset-registration` | VoltWise Core | Telemetry Simulator | `asset-registration-event.schema.json` |
| `voltwise.telemetry` | Telemetry Simulator | VoltWise Core | `telemetry-event.schema.json` |

Quota and anomaly schemas describe Core audit-event payloads and do not imply
additional Kafka topics in the current two-topic topology.

The simulator sends malformed or repeatedly failing registration records to
`voltwise.asset-registration.dlt` after its configured retries.

Kafka message keys are stable aggregate identifiers. Both registration and
telemetry records use `homeId`, which keeps every appliance reading for a home
ordered on the same Kafka partition. The `applianceId` remains in each telemetry
payload. Consumers must not depend on Java type headers; values are ordinary
UTF-8 JSON.

Live home and appliance status objects can be returned immediately after asset
registration. Their required `lastUpdatedAt` field is therefore nullable until
the first telemetry reading arrives.

## Event envelope

Every Kafka event contains these metadata fields:

- `eventId`: globally unique UUID used for idempotency.
- `eventVersion`: positive schema version. Current contracts use `1`.
- `eventType`: one of the values declared in `common.schema.json`.
- `occurredAt`: ISO-8601 UTC instant (for example, `2026-07-21T12:00:01Z`).

Additive optional fields are backwards compatible within a version. Removing a
field, changing its meaning/type, or adding a required field requires a new
`eventVersion`. Producers must continue emitting the documented required fields;
consumers should ignore unknown additive fields.

## Shared enum values

| Enum | Values |
| --- | --- |
| `ApplianceType` | `REFRIGERATOR`, `KETTLE`, `OVEN`, `TELEVISION`, `WASHING_MACHINE`, `AIR_CONDITIONER`, `MICROWAVE`, `LAMP`, `COMPUTER` |
| `OperatingState` | `OFF`, `STANDBY`, `ON`, `HIGH_LOAD` |
| `ApplianceHealthStatus` | `NORMAL`, `ANOMALOUS` |
| `TariffState` | `NORMAL`, `PENALTY` |
| `NotificationStatus` | `PENDING`, `SENT`, `FAILED` |
| `QuotaThreshold` | `EIGHTY_PERCENT`, `ONE_HUNDRED_PERCENT` |

All timestamps are UTC instants and all monetary and measurement values are JSON
numbers. Implementations should deserialize currency and energy values into a
decimal type such as Java `BigDecimal`, never binary floating point.

## Validation

From the repository root, schemas and examples can be checked with any Draft
2020-12 validator. For example:

```bash
npx --yes ajv-cli@5 validate \
  --spec=draft2020 \
  -r 'contracts/schemas/common.schema.json' \
  -s contracts/schemas/telemetry-event.schema.json \
  -d contracts/examples/telemetry-event.json
```
