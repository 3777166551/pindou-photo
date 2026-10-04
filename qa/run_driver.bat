@echo off
rem ============================================================
rem  Manual demo driver runner (NOT part of qa suite).
rem  Usage: qa\run_driver.bat <image> <cols> <outPngPrefix>
rem  ASCII-only file; driver prints ASCII only.
rem ============================================================
setlocal
cd /d "%~dp0.."

set "JAVA_HOME="
for /d %%d in (tools\jdk\jdk-*) do set "JAVA_HOME=%%d"
if "%JAVA_HOME%"=="" (
    echo [ERROR] JDK not found under tools\jdk
    exit /b 1
)
set "AJ=tools\asdk\platforms\android-34\android.jar"

"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -cp "qa\out;%AJ%" -sourcepath app\src\main\java -d qa\out qa\DriverFlatDemo.java
if errorlevel 1 (
    echo [DRIVER COMPILE FAILED]
    exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -cp "qa\out;%AJ%" DriverFlatDemo %1 %2 %3
