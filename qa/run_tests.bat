@echo off
rem ============================================================
rem  qa test suite (Windows local, equivalent of qa/run_tests.sh)
rem  Compile all tests and run them one by one; exit 1 on any fail.
rem  Needs tools\jdk and tools\asdk (installed by setup_tools.bat).
rem  Keep this file ASCII-only: cmd parses bat files in ANSI codepage.
rem ============================================================
setlocal
cd /d "%~dp0.."

set "JAVA_HOME="
for /d %%d in (tools\jdk\jdk-*) do set "JAVA_HOME=%%d"
if "%JAVA_HOME%"=="" (
    echo [ERROR] JDK not found under tools\jdk, run tools\setup_tools.bat first
    exit /b 1
)
set "AJ=tools\asdk\platforms\android-34\android.jar"
if not exist "%AJ%" (
    echo [ERROR] android.jar not found, run tools\setup_tools.bat first
    exit /b 1
)
echo using android.jar: %AJ%

if exist qa\out rmdir /s /q qa\out
mkdir qa\out

"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -cp "%AJ%" -sourcepath app\src\main\java -d qa\out ^
  qa\TestColorMath.java qa\TestPatternEngine.java qa\TestPatternPatch.java qa\TestCustomPalette.java
if errorlevel 1 (
    echo [COMPILE FAILED]
    exit /b 1
)

set FAIL=0
for %%T in (TestColorMath TestPatternEngine TestPatternPatch TestCustomPalette) do (
    echo ===== running %%T =====
    "%JAVA_HOME%\bin\java.exe" -cp "qa\out;%AJ%" %%T
    if errorlevel 1 set FAIL=1
)

if "%FAIL%"=="0" (
    echo ALL QA TESTS PASSED
) else (
    echo QA TESTS FAILED
    exit /b 1
)
