@echo off
rem Download dataset images: wikimedia via proxy, picsum direct. ASCII only.
setlocal enabledelayedexpansion
set PROXY=http://127.0.0.1:7890
cd /d %~dp0datasets

for %%C in (cartoons lineart pixelart landscape portrait food) do (
  mkdir %%C 2>nul
  set /a n=0
  for /f "usebackq delims=" %%U in (`type urls_%%C.txt`) do (
    set /a n+=1
    set /a nn=100!n!
    set nn=!nn:~-3!
    if not exist %%C\%%C_!nn!.jpg curl -s -x %PROXY% -A "PindouAlgoTest/1.0" --max-time 60 -o "%%C\%%C_!nn!.jpg" "%%U"
  )
  echo done %%C !n!
)

mkdir random 2>nul
set /a n=0
for /f "usebackq delims=" %%U in (`type urls_random.txt`) do (
  set /a n+=1
  set /a nn=100!n!
  set nn=!nn:~-3!
  if not exist random\rand_!nn!.jpg curl -s -L --max-time 45 -o "random\rand_!nn!.jpg" "%%U"
)
echo done random !n!
echo ALL-DOWNLOADS-DONE
