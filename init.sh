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
echo "Next steps:"
echo "1. Read feature_list.json to see current feature state"
echo "2. Pick ONE unfinished feature to work on"
echo "3. Implement only that feature"
echo "4. Re-run verification before claiming done"
echo ""
echo "Dev mode: cd flexmodel-server && ./mvnw quarkus:dev"
