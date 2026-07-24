# VoltFlow contributor guide

## Repository map

- `backend/`: Spring Boot VoltFlow Core. Owns REST APIs, PostgreSQL persistence,
  Kafka telemetry consumption, Ignite live state, business rules, snapshots,
  recommendations, and notifications.
- `telemetry-simulator/`: autonomous Spring Boot simulator. It discovers assets
  from Kafka and emits stateful telemetry; it must never read the Core database.
- `frontend/`: React, Vite, and TypeScript dashboard served through nginx.
- `contracts/`: technology-neutral JSON examples, schemas, and contract notes.
- `scripts/`: operational checks such as the end-to-end smoke test.
- `docker-compose.yml`: the supported local integration environment.

## Common commands

Run commands from the repository root unless a command changes directory.

```bash
cp .env.example .env
docker compose config
docker compose up --build
./scripts/smoke-test.sh

cd backend && mvn test
cd telemetry-simulator && mvn test
cd frontend && npm ci && npm run test -- --run && npm run build
```

The frontend is available at `http://localhost:3000`, Swagger UI at
`http://localhost:8080/swagger-ui.html`, OpenAPI JSON at
`http://localhost:8080/v3/api-docs`, and Mailpit at `http://localhost:8025`.

## Engineering conventions

- Use Java 21 syntax only where it remains compatible with the pinned Spring
  Boot and Ignite client versions.
- Keep controllers thin. Business state transitions belong in focused services.
- Keep DTOs separate from JPA entities and live-cache records.
- Use `BigDecimal` for tariffs, budgets, and accumulated cost. Specify scale and
  rounding at calculation boundaries.
- Store and serialize timestamps in UTC using ISO-8601.
- Put schema changes in ordered Flyway migrations; do not enable Hibernate DDL
  generation outside tests.
- Keep permanent financial and audit data in PostgreSQL. Ignite contains only
  rebuildable live operational state.
- Treat one appliance telemetry event as exactly one anomaly evaluation cycle.
- External AI and SMTP calls must be bounded, asynchronous, failure-tolerant,
  and unable to stall Kafka listener threads.
- Frontend user-facing errors must be concise and must never contain backend
  stack traces.
- Add or update tests with every behavior change.

## Integration rules

The canonical Kafka topics are:

| Topic | Producer | Consumer group |
| --- | --- | --- |
| `voltflow.asset-registration` | Core | telemetry simulator |
| `voltflow.telemetry` | telemetry simulator | Core |

Corresponding `.dlt` topics receive records that exhaust the configured retry
policy. Asset registrations are captured in the same PostgreSQL transaction as
their master data, dispatched from the durable outbox only after commit, and
marked published only after Kafka acknowledgement. The simulator communicates
with the Core only through Kafka.

Every Kafka event must carry `eventId`, `eventVersion`, `eventType`, and an
ISO-8601 UTC `occurredAt`. Contract changes begin in `contracts/`, remain
backward compatible whenever possible, and are reflected in producer and
consumer tests.

Canonical enum values:

- `ApplianceType`: `REFRIGERATOR`, `KETTLE`, `OVEN`, `TELEVISION`,
  `WASHING_MACHINE`, `AIR_CONDITIONER`, `MICROWAVE`, `LAMP`, `COMPUTER`
- `OperatingState`: `OFF`, `STANDBY`, `ON`, `HIGH_LOAD`
- `ApplianceHealthStatus`: `NORMAL`, `ANOMALOUS`
- `TariffState`: `NORMAL`, `PENALTY`
- `NotificationStatus`: `PENDING`, `SENT`, `FAILED`
- `QuotaThreshold`: `EIGHTY_PERCENT`, `ONE_HUNDRED_PERCENT`

## Safety and repository hygiene

Never commit `.env`, API keys, passwords, SMTP or database credentials,
generated build output, IDE metadata, assignment PDFs, confidential screenshots,
or local runtime volumes. Do not add direct database access to the simulator or
make Ignite the system of record. Never force-push or rewrite another
contributor's work. Before pushing, run all three test suites, validate Compose,
inspect `git diff --check`, and scan tracked files for likely secrets.
