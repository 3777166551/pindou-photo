@echo off
rem Download extra top-up images via proxy. ASCII only.
setlocal enabledelayedexpansion
set PROXY=http://127.0.0.1:7890
cd /d %~dp0datasets

for %%C in (pixelart lineart landscape portrait) do (
  set /a n=100
  for /f "usebackq delims=" %%U in (`type x_urls_%%C.txt`) do (
    set /a n+=1
    if not exist %%C\%%C_x_!n!.jpg curl -s -x %PROXY% -A "PindouAlgoTest/1.0" --max-time 60 -o "%%C\%%C_x_!n!.jpg" "%%U"
  )
  echo done %%C extra
)
echo EXTRA-DONE
