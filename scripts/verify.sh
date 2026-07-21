#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

(
  cd "$repository_root/backend"
  mvn test
)

(
  cd "$repository_root/telemetry-simulator"
  mvn test
)

(
  cd "$repository_root/frontend"
  npm ci
  npm run test -- --run
  npm run build
)

(
  cd "$repository_root"
  docker compose config --quiet
  ./scripts/secret-scan.sh
  git diff --check
)

echo "All VoltWise static verification steps passed."
