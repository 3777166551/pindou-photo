@echo off
rem ============================================================
rem  Real-device smoke walk (adb-driven, no emulator needed).
rem  Purpose: retire the "real device debt" without the developer
rem  spending time - plug the phone in, run this, send the folder.
rem
rem  Usage:
rem     qa\real_walk.bat              rem walk installed app
rem     qa\real_walk.bat build_apk\PindouPhoto-v2.56.apk
rem                                   rem install -r first, then walk
rem
rem  Output: qa\real_shots\<timestamp>\  screenshots + logcat + report
rem  Keep this file ASCII-only (DEV-NOTES 10).
rem ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0.."

set "ADB=tools\asdk\platform-tools\adb.exe"
if not exist "%ADB%" (
    for /f "delims=" %%p in ('where adb.exe 2^>nul') do set "ADB=%%p"
)
if not exist "%ADB%" (
    if not defined ADB (
        echo [ERROR] adb not found: tools\asdk\platform-tools\adb.exe nor PATH
        exit /b 1
    )
)

"%ADB%" get-state >nul 2>&1
if errorlevel 1 (
    echo [ERROR] no device state. Plug the phone in, enable USB debugging,
    echo         then accept the RSA prompt on the phone screen.
    "%ADB%" devices
    exit /b 1
)
"%ADB%" devices | findstr /c:"device$" >nul
if errorlevel 1 (
    echo [ERROR] device not in "device" state ^(unauthorized or offline^):
    "%ADB%" devices
    exit /b 1
)

set "TS=%date:~2,2%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%"
set "TS=%TS: =0%"
set "OUT=qa\real_shots\%TS%"
mkdir "%OUT%" >nul 2>&1

for /f "delims=" %%m in ('"%ADB%" shell getprop ro.product.model') do set "MODEL=%%m"
for /f "delims=" %%v in ('"%ADB%" shell getprop ro.build.version.release') do set "DROID=%%v"
for /f "delims=" %%a in ('"%ADB%" shell getprop ro.product.cpu.abilist') do set "ABIS=%%a"

echo ============================================================
echo  real-device walk
echo  model : %MODEL%
echo  android: %DROID%
echo  abis  : %ABIS%
echo  out   : %OUT%
echo ============================================================

if not "%~1"=="" (
    echo [step] install -r %~1
    "%ADB%" install -r "%~1" || (echo [ERROR] install failed & exit /b 1)
)

"%ADB%" logcat -c >nul 2>&1

set FATALS=0
set /a N=0

rem -- launch cold (splash entry point) ------------------------
call :step "home" "com.pindou.app/.SplashActivity"
call :wait 6
call :snap "home_after_splash"
call :wait 3

rem -- editor on a blank 29x29 canvas --------------------------
call :step "editor_blank" "com.pindou.app/.EditorActivity --ez blank_canvas true"
call :wait 7
call :snap "editor_blank"
"%ADB%" shell input keyevent 4
call :wait 2

rem -- knowledge page ------------------------------------------
call :step "knowledge" "com.pindou.app/.KnowledgeActivity"
call :wait 4
call :snap "knowledge"
"%ADB%" shell input keyevent 4
call :wait 2

rem -- bead inventory ------------------------------------------
call :step "inventory" "com.pindou.app/.InventoryActivity"
call :wait 4
call :snap "inventory"
"%ADB%" shell input keyevent 4
call :wait 2

rem -- palette manager -----------------------------------------
call :step "palette" "com.pindou.app/.PaletteActivity"
call :wait 4
call :snap "palette"
"%ADB%" shell input keyevent 4
call :wait 2

rem -- photo color match (no camera needed) --------------------
call :step "colormatch" "com.pindou.app/.ColorMatchActivity"
call :wait 4
call :snap "colormatch"
"%ADB%" shell input keyevent 4
call :wait 2

rem -- layered compose -----------------------------------------
call :step "layered" "com.pindou.app/.LayeredComposeActivity"
call :wait 4
call :snap "layered"
"%ADB%" shell input keyevent 4
call :wait 2

rem -- final home shot -----------------------------------------
call :snap "home_final"

"%ADB%" logcat -d > "%OUT%\logcat.txt" 2>&1
"%ADB%" shell getprop ro.build.version.sdk > "%OUT%\device.txt" 2>&1
echo model=%MODEL% android=%DROID% abis=%ABIS%>> "%OUT%\device.txt"

findstr /c:"FATAL EXCEPTION" "%OUT%\logcat.txt" >nul
if not errorlevel 1 set FATALS=1

(
echo real-device walk report
echo model  : %MODEL%
echo android: %DROID%
echo abis   : %ABIS%
echo steps  : %N%
echo fatal  : %FATALS%
) > "%OUT%\report.txt"

echo ============================================================
if "%FATALS%"=="1" (
    echo  RESULT: FATAL EXCEPTION FOUND - see %OUT%\logcat.txt
) else (
    echo  RESULT: clean walk, %N% steps, no FATAL
)
echo  screenshots: %OUT%\
echo  Send this folder ^(or just report.txt + the PNGs^) for review.
echo ============================================================
if "%FATALS%"=="1" exit /b 1
exit /b 0

:step
set /a N=N+1
echo [step %N%] start %~1: %~2
"%ADB%" shell am start -n %~2 >nul 2>&1
goto :eof

:snap
set /a N=N+1
"%ADB%" exec-out screencap -p > "%OUT%\%~1.png" 2>nul
echo [shot] %~1.png
goto :eof

:wait
ping -n %~1 127.0.0.1 >nul
goto :eof
