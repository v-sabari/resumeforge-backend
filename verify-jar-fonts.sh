#!/usr/bin/env bash
# verify-jar-fonts.sh
# Run after `mvn clean package` to confirm both font files are bundled.
# Usage: bash verify-jar-fonts.sh
# Place this script at project root (same level as pom.xml).

set -euo pipefail

JAR="target/resumeforge-ai-1.0.0.jar"

if [ ! -f "$JAR" ]; then
  echo "ERROR: JAR not found at $JAR. Run 'mvn clean package -DskipTests' first."
  exit 1
fi

echo "Checking font resources inside $JAR ..."
echo ""

REGULAR=$(jar tf "$JAR" | grep -c "BOOT-INF/classes/fonts/DejaVuSans.ttf" || true)
BOLD=$(jar tf "$JAR"    | grep -c "BOOT-INF/classes/fonts/DejaVuSans-Bold.ttf" || true)

if [ "$REGULAR" -eq 1 ]; then
  echo "  [OK]  BOOT-INF/classes/fonts/DejaVuSans.ttf"
else
  echo "  [FAIL] DejaVuSans.ttf NOT found inside JAR"
  echo "         Add the file to src/main/resources/fonts/ and rebuild."
fi

if [ "$BOLD" -eq 1 ]; then
  echo "  [OK]  BOOT-INF/classes/fonts/DejaVuSans-Bold.ttf"
else
  echo "  [FAIL] DejaVuSans-Bold.ttf NOT found inside JAR"
  echo "         Add the file to src/main/resources/fonts/ and rebuild."
fi

echo ""
if [ "$REGULAR" -eq 1 ] && [ "$BOLD" -eq 1 ]; then
  echo "SUCCESS: Both font files are correctly bundled in the JAR."
  exit 0
else
  echo "FAILURE: One or more font files are missing from the JAR."
  exit 1
fi