#!/usr/bin/env bash
# Lint, type-check and unit-test the frontend; exits non-zero on the first failure.
set -euo pipefail
cd "$(dirname "$0")"
npm run --silent lint
npm run --silent typecheck
npx vitest --run
