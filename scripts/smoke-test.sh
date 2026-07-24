#!/usr/bin/env bash
set -euo pipefail

backend_url="${BACKEND_URL:-http://localhost:8080}"
frontend_url="${FRONTEND_URL:-http://localhost:3000}"
timeout_seconds="${SMOKE_TIMEOUT_SECONDS:-180}"
started_at="$(date +%s)"

wait_for_url() {
  local name="$1"
  local url="$2"
  until curl --fail --silent --show-error "$url" >/dev/null 2>&1; do
    if (( $(date +%s) - started_at >= timeout_seconds )); then
      echo "Timed out waiting for ${name} at ${url}" >&2
      exit 1
    fi
    sleep 2
  done
  echo "OK: ${name} (${url})"
}

wait_for_url "backend health" "${backend_url}/actuator/health"
wait_for_url "OpenAPI" "${backend_url}/v3/api-docs"
wait_for_url "frontend" "${frontend_url}/"

unique_suffix="$(date +%s)"
auth_response="$(curl --fail --silent --show-error \
  -X POST "${backend_url}/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  --data "{
    \"email\": \"smoke-user-${unique_suffix}@example.com\",
    \"password\": \"SmokeTestPassword!\"
  }")"

auth_token="$(printf '%s' "$auth_response" \
  | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
  | head -n 1)"
if [[ -z "$auth_token" ]]; then
  echo "Account registration response did not contain a JWT: ${auth_response}" >&2
  exit 1
fi
echo "OK: registered and authenticated smoke-test user"

create_response="$(curl --fail --silent --show-error \
  -X POST "${backend_url}/api/v1/homes" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${auth_token}" \
  --data "{
    \"name\": \"Smoke Test Home ${unique_suffix}\",
    \"contactEmail\": \"smoke-${unique_suffix}@example.com\",
    \"monthlyBudget\": 500.00,
    \"normalTariffPerKwh\": 2.50,
    \"penaltyMultiplier\": 1.50,
    \"appliances\": [
      {
        \"name\": \"Smoke Test Kettle\",
        \"type\": \"KETTLE\",
        \"safePowerLimitWatts\": 2200
      }
    ]
  }")"

home_id="$(printf '%s' "$create_response" \
  | sed -n 's/^[[:space:]]*{"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
  | head -n 1)"
if [[ -z "$home_id" ]]; then
  echo "Home creation response did not contain a numeric id: ${create_response}" >&2
  exit 1
fi
echo "OK: created home ${home_id}"

status_deadline="$(( $(date +%s) + 45 ))"
while true; do
  status_response="$(curl --fail --silent --show-error \
    -H "Authorization: Bearer ${auth_token}" \
    "${backend_url}/api/v1/homes/${home_id}/status" 2>/dev/null || true)"
  timestamp_matches="$(printf '%s' "$status_response" \
    | grep -o '"lastUpdatedAt":"[^"]*"' || true)"
  timestamp_count="$(printf '%s\n' "$timestamp_matches" \
    | sed '/^$/d' \
    | wc -l \
    | tr -d ' ')"
  if [[ "$timestamp_count" -ge 2 ]]; then
    break
  fi
  if (( $(date +%s) >= status_deadline )); then
    echo "Timed out waiting for simulator/Kafka telemetry for home ${home_id}: ${status_response}" >&2
    exit 1
  fi
  sleep 1
done

echo "OK: simulator/Kafka/Core live telemetry round trip"
echo "VoltWise smoke test passed."
