# VoltWise telemetry simulator

The simulator is an autonomous Spring Boot process. It learns homes and
appliances only by consuming `voltwise.asset-registration`, keeps state in
memory, generates one reading per registered appliance per scheduler cycle, and
publishes UTF-8 JSON to `voltwise.telemetry`. It has no backend, PostgreSQL, or
Ignite dependency. Telemetry records are keyed by `homeId`, preserving the order
of all appliance readings for one home on a single Kafka partition.

Run locally with a Kafka broker on `localhost:9092`:

```bash
mvn spring-boot:run
```

Run tests:

```bash
mvn test
```

## Environment configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap addresses |
| `KAFKA_SIMULATOR_CONSUMER_GROUP` | unique per process | Override when deliberately sharing work across simulator replicas |
| `TELEMETRY_INTERVAL_MS` | `1000` | Delay between complete generation cycles |
| `SIMULATION_RANDOM_SEED` | `42` | Reproducible per-appliance random seed |
| `SIMULATION_ANOMALY_PROBABILITY` | `0.02` | Chance to start an over-limit burst |
| `SIMULATION_ANOMALY_BURST_CYCLES` | `3` | Consecutive readings in a random burst |
| `SIMULATION_ANOMALY_COOLDOWN_CYCLES` | `20` | Minimum normal cycles before another random burst |
| `SIMULATION_ANOMALY_DEMO_ENABLED` | `false` | Enables deterministic demo bursts |
| `SIMULATION_ANOMALY_DEMO_START_CYCLE` | `5` | First one-based demo cycle |
| `SIMULATION_ANOMALY_DEMO_REPEAT_CYCLES` | `0` | Repeat interval; zero means one burst |
| `SIMULATION_ANOMALY_DEMO_APPLIANCE_IDS` | empty | Comma-separated appliance IDs targeted by demo mode |

All Watt ranges, transition probabilities, and state durations are centralized
in `SimulationProperties`/`DefaultApplianceProfiles`. Any value can be overridden
with a Spring property. For example:

```bash
java -jar app.jar \
  --simulation.profiles.KETTLE.ranges.active.min-watts=1600 \
  --simulation.profiles.KETTLE.probabilities.start=0.10
```

Registration records are validated before they mutate runtime state. Duplicate
`eventId` values are ignored, and repeated listener failures are published to
`voltwise.asset-registration.dlt`. The default unique consumer group replays the
compacted registration log so an in-memory restart can rebuild its appliances.
Run a single simulator instance for the default local topology; replicas must use
an explicitly shared consumer group to avoid duplicate telemetry.
