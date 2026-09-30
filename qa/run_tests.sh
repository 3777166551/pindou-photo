#!/usr/bin/env bash
# qa ?????????:?????????????????,????????????1??# ????android.jar(PatternEngine ??? Bitmap),????????
#   1. $ANDROID_HOME (CI)
#   2. ??? tools/asdk (build_apk.bat ?????SDK)
#   3. ??sdkmanager ??? platforms;android-34
set -e
cd "$(dirname "$0")/.."

AJ=""
if [ -n "$ANDROID_HOME" ] && [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
  AJ="$ANDROID_HOME/platforms/android-34/android.jar"
elif [ -f "tools/asdk/platforms/android-34/android.jar" ]; then
  AJ="tools/asdk/platforms/android-34/android.jar"
elif command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager --install "platforms;android-34" >/dev/null
  AJ="$ANDROID_HOME/platforms/android-34/android.jar"
fi
if [ -z "$AJ" ]; then
  echo "ERROR: android.jar not found (set ANDROID_HOME or keep tools/asdk)"
  exit 1
fi
echo "using android.jar: $AJ"

rm -rf qa/out
mkdir -p qa/out

# qa-only mini org.json: compiled first so qa/out shadows the android.jar
# stub at runtime (classpath order) - unlocks JSON-layer desktop tests
javac -encoding UTF-8 -d qa/out \
  qa/testjson/org/json/JSONException.java qa/testjson/org/json/JSONObject.java qa/testjson/org/json/JSONArray.java

javac -encoding UTF-8 -cp "$AJ" -sourcepath app/src/main/java -d qa/out \
  qa/TestColorMath.java qa/TestPatternEngine.java qa/TestPatternPatch.java \
  qa/TestCustomPalette.java qa/TestSymmetry.java qa/TestLineArt.java \
  qa/TestBrandCharts.java qa/TestCrossStitch.java qa/TestBoardProjector.java qa/TestHexBoard.java qa/TestBackup.java qa/TestPlay3D.java qa/TestGifEncoder.java qa/TestAlgoGate.java qa/TestVerify.java qa/TestStandee.java qa/TestInventory.java qa/TestPatternShare.java qa/TestPaletteShare.java qa/TestStrings.java qa/TestStorage.java qa/TestChaos.java

FAIL=0
for T in TestColorMath TestPatternEngine TestPatternPatch TestCustomPalette TestSymmetry TestLineArt TestBrandCharts TestBoardProjector TestCrossStitch TestHexBoard TestBackup TestPlay3D TestGifEncoder TestAlgoGate TestVerify TestStandee TestInventory TestPatternShare TestPaletteShare TestStrings TestStorage TestChaos; do
  echo "===== running $T ====="
  java -cp "qa/out:$AJ" "$T" || FAIL=1
done

if [ "$FAIL" = "0" ]; then
  echo "ALL QA TESTS PASSED"
else
  echo "QA TESTS FAILED"
  exit 1
fi
