@echo off
rem ============================================================
rem  Fast non-destructive compile check (steps 1-3 of build_apk.bat):
rem  aapt2 resource link + javac over all sources. No signing, no
rem  output wiped - results go to build_check\, safe to re-run.
rem  Exit 0 = resources + Java compile OK.
rem  Keep this file ASCII-only: cmd parses bat files in ANSI codepage.
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
set "OUT=build_check"

if not exist "%AJ%" (
    echo [ERROR] android.jar not found, run tools\setup_tools.bat first
    exit /b 1
)

if exist %OUT% rmdir /s /q %OUT%
mkdir %OUT%

echo === [1/3] aapt2 compile resources ===
"%BT%\aapt2.exe" compile --dir %SRC%\res -o %OUT%\res.zip
if errorlevel 1 goto :err

echo === [2/3] aapt2 link ===
"%BT%\aapt2.exe" link -o %OUT%\base.apk -I "%AJ%" --manifest %SRC%\AndroidManifest.xml -R %OUT%\res.zip --java %OUT%\gen -A %SRC%\assets --min-sdk-version 24 --target-sdk-version 34 --auto-add-overlay
if errorlevel 1 goto :err

echo === [3/3] javac compile ===
mkdir %OUT%\classes
dir /s /b %OUT%\gen\*.java %SRC%\java\*.java > %OUT%\sources.txt
"%JAVA_HOME%\bin\javac.exe" -source 1.8 -target 1.8 -encoding UTF-8 -bootclasspath "%AJ%;%LSTUBS%" -classpath "%AJ%;tools\ort_aar\classes.jar" -d %OUT%\classes @%OUT%\sources.txt
if errorlevel 1 goto :err

echo.
echo ============
echo  COMPILE OK
echo ============
exit /b 0

:err
echo.
echo [COMPILE CHECK FAILED]
exit /b 1
