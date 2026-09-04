#!/usr/bin/env bash
# qa 测试套件入口:编译全部测试并逐个运行,任一失败即退出 1。
# 需要 android.jar(PatternEngine 引用 Bitmap),按顺序探测:
#   1. $ANDROID_HOME (CI)
#   2. 本地 tools/asdk (build_apk.bat 的随身 SDK)
#   3. 用 sdkmanager 现装 platforms;android-34
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
javac -encoding UTF-8 -cp "$AJ" -sourcepath app/src/main/java -d qa/out \
  qa/TestColorMath.java qa/TestPatternEngine.java qa/TestPatternPatch.java \
  qa/TestCustomPalette.java

FAIL=0
for T in TestColorMath TestPatternEngine TestPatternPatch TestCustomPalette; do
  echo "===== running $T ====="
  java -cp "qa/out:$AJ" "$T" || FAIL=1
done

if [ "$FAIL" = "0" ]; then
  echo "ALL QA TESTS PASSED"
else
  echo "QA TESTS FAILED"
  exit 1
fi
