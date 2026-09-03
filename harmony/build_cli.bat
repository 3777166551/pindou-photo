@echo off
rem HarmonyOS CLI build using DevEco bundled toolchain
set DEVO=E:\DevEco Studio
set PATH=%DEVO%\jbr\bin;%DEVO%\tools\node;%DEVO%\tools\ohpm\bin;%PATH%
set NODE_OPTIONS=--max-old-space-size=8192
set DEVECO_SDK_HOME=%DEVO%\sdk
cd /d F:\delete\PDAPP\harmony

echo === [1/3] ohpm install ===
call "%DEVO%\tools\ohpm\bin\ohpm.bat" install --all
if errorlevel 1 goto :err

echo === [2/3] hvigor clean ===
call "%DEVO%\tools\hvigor\bin\hvigorw.bat" --stop-daemon
call "%DEVO%\tools\hvigor\bin\hvigorw.bat" --mode module -p module=entry@default -p product=default clean
if errorlevel 1 goto :err

echo === [3/3] hvigor assembleHap ===
call "%DEVO%\tools\hvigor\bin\hvigorw.bat" --mode module -p module=entry@default -p product=default assembleHap
if errorlevel 1 goto :err

echo.
echo BUILD OK
exit /b 0

:err
echo.
echo BUILD FAILED
exit /b 1
