@echo off
rem ============================================================
rem  "PindouPhoto" (Photo -> Perler Bead Pattern) APK builder
rem  No Gradle needed - uses raw Android build tools.
rem  Requires: tools\jdk  tools\asdk  (installed by tools\setup_tools.bat)
rem  Output:   build_apk\PindouPhoto-v1.0.apk
rem ============================================================
setlocal
cd /d "%~dp0"

set "TOOLS=%~dp0tools"
set "JAVA_HOME="
for /d %%d in (%TOOLS%\jdk\jdk-*) do set "JAVA_HOME=%%d"
if "%JAVA_HOME%"=="" (
    echo [ERROR] JDK not found under tools\jdk, run tools\setup_tools.bat first
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "SDK=%TOOLS%\asdk"
set "BT=%SDK%\build-tools\34.0.0"
set "AJ=%SDK%\platforms\android-34\android.jar"
set "LSTUBS=%SDK%\platforms\android-34\core-lambda-stubs.jar"
set "SRC=app\src\main"
set "OUT=build_apk"

if not exist "%AJ%" (
    echo [ERROR] android.jar not found, run tools\setup_tools.bat first
    exit /b 1
)

if exist %OUT% rmdir /s /q %OUT%
mkdir %OUT%

echo === [1/6] aapt2 compile resources ===
"%BT%\aapt2.exe" compile --dir %SRC%\res -o %OUT%\res.zip
if errorlevel 1 goto :err

echo === [2/6] aapt2 link ===
"%BT%\aapt2.exe" link -o %OUT%\base.apk -I "%AJ%" --manifest %SRC%\AndroidManifest.xml -R %OUT%\res.zip --java %OUT%\gen -A %SRC%\assets --min-sdk-version 24 --target-sdk-version 34 --version-code 45 --version-name "2.34" --auto-add-overlay
if errorlevel 1 goto :err

echo === [3/6] javac compile ===
mkdir %OUT%\classes
dir /s /b %OUT%\gen\*.java %SRC%\java\*.java > %OUT%\sources.txt
"%JAVA_HOME%\bin\javac.exe" -source 1.8 -target 1.8 -encoding UTF-8 -bootclasspath "%AJ%;%LSTUBS%" -classpath "%AJ%;tools\ort_aar\classes.jar" -d %OUT%\classes @%OUT%\sources.txt
if errorlevel 1 goto :err

echo === [4/6] d8 dex ===
mkdir %OUT%\dex
"%JAVA_HOME%\bin\jar.exe" cf %OUT%\classes.jar -C %OUT%\classes .
call "%BT%\d8.bat" --release --lib "%AJ%" --min-api 24 --output %OUT%\dex %OUT%\classes.jar
if errorlevel 1 goto :err

echo === [4.5/6] ONNX Runtime + U2NetP model ===
mkdir %OUT%\dex2
call "%BT%\d8.bat" --release --lib "%AJ%" --min-api 24 --output %OUT%\dex2 tools\ort_aar\classes.jar
if errorlevel 1 goto :err
ren %OUT%\dex2\classes.dex classes2.dex
if errorlevel 1 goto :err
if not exist %OUT%\lib\arm64-v8a mkdir %OUT%\lib\arm64-v8a
copy /y tools\ort_aar\jni\arm64-v8a\libonnxruntime.so %OUT%\lib\arm64-v8a\ >nul
copy /y tools\ort_aar\jni\arm64-v8a\libonnxruntime4j_jni.so %OUT%\lib\arm64-v8a\ >nul

echo === [5/6] package + zipalign ===
copy /y %OUT%\base.apk %OUT%\unsigned.apk >nul
pushd %OUT%
"%JAVA_HOME%\bin\jar.exe" -uf unsigned.apk -C dex classes.dex
if errorlevel 1 (popd & goto :err)
"%JAVA_HOME%\bin\jar.exe" -uf unsigned.apk -C dex2 classes2.dex
if errorlevel 1 (popd & goto :err)
"%JAVA_HOME%\bin\jar.exe" -uf unsigned.apk -C . lib
if errorlevel 1 (popd & goto :err)
popd
"%BT%\zipalign.exe" -f -p 4 %OUT%\unsigned.apk %OUT%\aligned.apk
if errorlevel 1 goto :err

echo === [6/6] sign ===
if "%PINDOU_KS_PASS%"=="" (
    echo [ERROR] keystore password not set. Set env var first:
    echo     setx PINDOU_KS_PASS "your-keystore-password"
    exit /b 1
)
if not exist pindou.keystore (
    "%JAVA_HOME%\bin\keytool.exe" -genkeypair -keystore pindou.keystore -alias pindou -keyalg RSA -keysize 2048 -validity 10000 -storepass "%PINDOU_KS_PASS%" -keypass "%PINDOU_KS_PASS%" -dname "CN=PindouApp, OU=Personal, O=Personal, C=CN"
    if errorlevel 1 goto :err
)
call "%BT%\apksigner.bat" sign --ks pindou.keystore --ks-key-alias pindou --ks-pass pass:%PINDOU_KS_PASS% --key-pass pass:%PINDOU_KS_PASS% --out %OUT%\PindouPhoto-v2.34.apk %OUT%\aligned.apk
if errorlevel 1 goto :err
call "%BT%\apksigner.bat" verify %OUT%\PindouPhoto-v2.34.apk
if errorlevel 1 goto :err

echo.
echo ============================================
echo  BUILD OK: %CD%\%OUT%\PindouPhoto-v2.34.apk
echo ============================================
exit /b 0

:err
echo.
echo [BUILD FAILED]
exit /b 1
