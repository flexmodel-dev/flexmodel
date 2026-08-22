#!/bin/bash
set -e

echo "=== Flexmodel Harness Initialization ==="

echo "--- Java version check ---"
java -version

echo ""
echo "--- Clean and compile (all modules) ---"
mvn clean compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'
echo "OK: All modules compiled successfully"

echo ""
echo "--- Engine module tests ---"
mvn test -pl flexmodel-engine -q
echo "OK: flexmodel-engine tests passed"

echo ""
echo "=== Build Verification Complete ==="
echo ""
echo "Optional: Run full server tests (some tests may have known failures):"
echo "  mvn clean test -pl flexmodel-server -am"
echo ""
echo "Optional: Run UI E2E tests (Playwright, requires Node.js):"
if command -v node >/dev/null 2>&1; then
  if [ ! -d "flexmodel-ui/node_modules" ]; then
    echo "  Installing UI dependencies..."
    (cd flexmodel-ui && npm ci)
  fi
  if [ ! -d "flexmodel-ui/node_modules/playwright-core" ]; then
    echo "  Installing Playwright browser..."
    (cd flexmodel-ui && npx playwright install chromium)
  fi
  echo "  Running E2E tests..."
  (cd flexmodel-ui && npx playwright test)
else
  echo "  Skipped: Node.js not found. To run UI E2E tests manually:"
  echo "    cd flexmodel-ui && npm ci && npx playwright install chromium && npx playwright test"
fi
echo ""
echo "Next steps:"
echo "1. Read feature_list.json to see current feature state"
echo "2. Pick ONE unfinished feature to work on"
echo "3. Implement only that feature"
echo "4. Re-run verification before claiming done"
echo ""
echo "Dev mode: cd flexmodel-server && ./mvnw quarkus:dev"
