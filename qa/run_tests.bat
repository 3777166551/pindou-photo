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

rem qa-only mini org.json: compiled first so qa\out shadows the android.jar
rem stub at runtime (classpath order) - unlocks JSON-layer desktop tests
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -d qa\out ^
  qa\testjson\org\json\JSONException.java qa\testjson\org\json\JSONObject.java qa\testjson\org\json\JSONArray.java
if errorlevel 1 (
    echo [ORG.JSON COMPILE FAILED]
    exit /b 1
)

"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -cp "%AJ%" -sourcepath app\src\main\java -d qa\out ^
  qa\TestColorMath.java qa\TestPatternEngine.java qa\TestPatternPatch.java qa\TestCustomPalette.java qa\TestSymmetry.java qa\TestLineArt.java qa\TestBrandCharts.java qa\TestCrossStitch.java qa\TestBoardProjector.java qa\TestHexBoard.java qa\TestBackup.java qa\TestPlay3D.java qa\TestGifEncoder.java qa\TestVerify.java qa\TestAlgoGate.java qa\TestStandee.java qa\TestInventory.java qa\TestPatternShare.java qa\TestPaletteShare.java qa\TestStrings.java qa\TestStorage.java qa\TestChaos.java qa\TestBoardModule.java qa\TestSpringMotion.java qa\TestFlatUnify.java
if errorlevel 1 (
    echo [COMPILE FAILED]
    exit /b 1
)

set FAIL=0
for %%T in (TestColorMath TestPatternEngine TestPatternPatch TestCustomPalette TestSymmetry TestLineArt TestBrandCharts TestBoardProjector TestCrossStitch TestHexBoard TestBackup TestPlay3D TestGifEncoder TestVerify TestAlgoGate TestStandee TestInventory TestPatternShare TestPaletteShare TestStrings TestStorage TestChaos TestBoardModule TestSpringMotion TestFlatUnify) do (
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
