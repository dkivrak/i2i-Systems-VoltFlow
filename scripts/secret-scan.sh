#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

patterns='(AIza[0-9A-Za-z_-]{35}|AKIA[0-9A-Z]{16}|gh[pousr]_[0-9A-Za-z]{30,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)'

if rg --hidden --line-number --glob '!.git/**' --glob '!**/target/**' \
  --glob '!**/node_modules/**' --glob '!**/dist/**' \
  --glob '!scripts/secret-scan.sh' --regexp "$patterns" .; then
  echo "Potential secret material found. Review every match before committing." >&2
  exit 1
fi

for forbidden in '.env' 'VoltFlowProject.pdf'; do
  if git ls-files --error-unmatch "$forbidden" >/dev/null 2>&1; then
    echo "Forbidden file is tracked: ${forbidden}" >&2
    exit 1
  fi
done

echo "No high-confidence secret patterns or forbidden tracked files found."
