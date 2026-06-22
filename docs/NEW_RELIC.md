# New Relic APM Integration

This project ships configuration *hooks* for New Relic APM but does not bundle the agent jar
or a real license key — New Relic requires a (free-tier-available) account, and pulling their
distribution down automatically isn't something this repo does on your behalf. This document
covers what's already wired up and the steps to actually turn it on.

## What's already here

- `application.yml` defines a `newrelic.*` properties block (`enabled`, `app-name`,
  `license-key`), all overridable via the `NEW_RELIC_ENABLED`, `NEW_RELIC_APP_NAME`, and
  `NEW_RELIC_LICENSE_KEY` environment variables.
- `docker-compose.yml`'s `app` service passes `NEW_RELIC_ENABLED` and `NEW_RELIC_LICENSE_KEY`
  through from your shell/`.env` file.
- The `Dockerfile` defines an empty `JAVA_OPTS` build arg that the entrypoint expands onto the
  `java` command line (`java $JAVA_OPTS -jar app.jar`) — attaching the agent is a one-line
  change to that variable, not a Dockerfile rebuild.
- `.env.example` documents all of the above.

None of this talks to New Relic until you supply a real license key and the agent jar — until
then, `newrelic.enabled` stays `false` and the app behaves exactly as if New Relic didn't exist.

## Why New Relic alongside Prometheus/Grafana

The two are complementary, not redundant. Prometheus/Grafana (already fully wired up, see the
main README) cover time-series business and JVM metrics — throughput, success/failure rates,
processing latency percentiles. New Relic APM instead gives per-request distributed traces,
automatic error analytics with stack traces grouped by root cause, and (with the Kafka
instrumentation New Relic's Java agent auto-detects) producer/consumer span correlation across
the `transaction.created` → consumer → `transaction.processed` hop. If you only need dashboards
and alerting on numbers, Prometheus/Grafana alone is enough; New Relic earns its keep when you
need to answer "why was *this specific* transaction slow" rather than "are transactions slow in
aggregate."

## Enabling it locally with Docker Compose

1. Sign up at [newrelic.com](https://newrelic.com) (a free tier exists) and grab your **license
   key** from your account's API keys page.
2. Download the New Relic Java agent zip from New Relic's download page and unzip it somewhere
   on your host, e.g. `~/newrelic-agent/`. You should end up with `newrelic.jar` and a
   `newrelic.yml` inside that folder.
3. Edit `newrelic.yml` and set `license_key: YOUR_KEY_HERE` and `app_name: transaction-processing-system`
   (or leave `app_name` and rely on the `NEW_RELIC_APP_NAME` env var — the agent reads both).
4. Add a bind mount and the `-javaagent` flag to the `app` service in `docker-compose.yml`:

   ```yaml
   app:
     environment:
       NEW_RELIC_ENABLED: "true"
       NEW_RELIC_LICENSE_KEY: "your-license-key"
       JAVA_OPTS: "-javaagent:/app/newrelic/newrelic.jar"
     volumes:
       - ~/newrelic-agent:/app/newrelic:ro
   ```

5. `docker compose up -d --build app`. Within a couple of minutes the application should appear
   under APM & Services in the New Relic UI as `transaction-processing-system`.

## Enabling it without Docker (running the jar directly)

```bash
java -javaagent:/path/to/newrelic-agent/newrelic.jar \
     -Dnewrelic.config.license_key=your-license-key \
     -Dnewrelic.config.app_name=transaction-processing-system \
     -jar target/transaction-processing-system.jar
```

## Turning it off

Leave `NEW_RELIC_ENABLED` unset (or `false`) and don't pass `-javaagent` — this is the default
state of the repo as delivered. The `newrelic.*` Spring properties existing in `application.yml`
has no runtime cost either way; they're only read if you write code that inspects them, which
nothing in this codebase currently does (the agent itself reads its own `newrelic.yml`/env vars,
independent of the Spring property tree).
