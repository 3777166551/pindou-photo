#!/usr/bin/env bash
# ============================================================
#  Robustness fuzz: simulate a human "messing around" with the
#  app and probe boundary inputs. Three phases:
#    1) Boundary photos: tiny 8x8 / big 2200x2200 / transparent
#       PNG / grayscale / truncated corrupt PNG / zero-byte file
#       are each fed into the editor - app must never die.
#    2) Blind monkey: 2 fixed seeds x 3000 random events
#       (taps / swipes / occasional app switch), ignore-crashes
#       so it keeps hammering after something breaks.
#    3) Guided random walker: every step dumps the UI, picks a
#       RANDOM tappable control of OUR package and taps it, with
#       probability-based random swipes / BACK. 260 actions.
#  The run FAILS (exit 1) iff com.pindou.app crashes or ANRs.
#  System-app crashes don't count. Fixed seeds keep failures
#  reproducible.
#  Artifacts: fuzz_log.txt, monkey_<seed>.txt, fuzz_shots/
#  Usage: bash qa/ui_fuzz.sh  (adb + booted emulator, repo root)
# ============================================================
set -u
export PATH="$ANDROID_HOME/platform-tools:$PATH"

PKG="com.pindou.app"
LOG=fuzz_log.txt
SHOTS=fuzz_shots
mkdir -p "$SHOTS"
: > "$LOG"

log()  { echo "[fuzz] $*" | tee -a "$LOG"; }
snap() { adb shell screencap -p /sdcard/f.png > /dev/null 2>&1
         adb pull /sdcard/f.png "$SHOTS/$1.png" > /dev/null 2>&1; }

app_alive() { [ -n "$(adb shell pidof $PKG | tr -d '\r')" ]; }

fatal_scan() { adb logcat -d 2>/dev/null | grep -A 3 "FATAL EXCEPTION" \
                 | grep -q "Process: $PKG"; }
anr_scan()   { adb logcat -d 2>/dev/null | grep -q "ANR in $PKG"; }

check_healthy() {  # $1 = phase label
  if ! app_alive; then
    log "FAIL: app process dead ($1)"
    snap fail_alive
    exit 1
  fi
  if fatal_scan; then
    log "FAIL: app crash in logcat ($1)"
    adb logcat -d | grep -A 30 "FATAL EXCEPTION" | grep -B 2 -A 28 "Process: $PKG" | head -60
    snap fail_crash
    exit 1
  fi
  if anr_scan; then
    log "FAIL: ANR in logcat ($1)"
    snap fail_anr
    exit 1
  fi
}

wait_boot() {
  for _ in $(seq 1 60); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return 0
    sleep 5
  done
  echo "[fuzz] emulator boot timeout"; exit 1
}

# ---------- boot / install / prep ----------
adb wait-for-device
wait_boot
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
log "installing APK..."
if ! adb install -r ${APK:-apk/*.apk}; then
  echo "[fuzz] FAIL: adb install failed"
  exit 1
fi
adb root > /dev/null 2>&1
adb wait-for-device
sleep 2

log "injecting boundary photos into app files/"
adb shell run-as $PKG mkdir -p files > /dev/null 2>&1
for f in ci_photo fuzz_tiny fuzz_big fuzz_alpha fuzz_gray fuzz_trunc fuzz_zero; do
  adb push qa/test_data/$f.png /data/local/tmp/$f.png > /dev/null
  adb shell chmod 644 /data/local/tmp/$f.png
  adb shell run-as $PKG cp /data/local/tmp/$f.png files/$f.png > /dev/null 2>&1
done

# ---------- phase 1: boundary photos ----------
# 每张都直接开编辑器;应用可以拒绝(坏图弹 toast),但绝不允许崩/死
for f in fuzz_tiny fuzz_alpha fuzz_gray fuzz_trunc fuzz_zero fuzz_big ci_photo; do
  log "boundary photo: $f"
  adb shell am start -n $PKG/.EditorActivity \
    --es photo_uri "file:///data/data/$PKG/files/$f.png" > /dev/null 2>&1
  if [ "$f" = "fuzz_big" ]; then sleep 18; else sleep 7; fi
  check_healthy "boundary $f"
  snap "bnd_$f"
  adb shell input keyevent 4
  sleep 1.5
done
check_healthy "boundary phase done"
log "boundary phase OK"

# ---------- phase 2: blind monkey ----------
# 拟人乱点乱滑:65% 单击 / 25% 滑动 / 4% 切应用 / 6% 导航键
MONKEY_FLAGS="--throttle 50 --pct-touch 65 --pct-motion 25 --pct-appswitch 4 \
--pct-nav 6 --pct-syskeys 0 --ignore-crashes --ignore-timeouts \
--ignore-security-exceptions"
for SEED in 90210 424242; do
  log "monkey seed=$SEED events=3000"
  adb shell monkey -p $PKG $MONKEY_FLAGS -s $SEED 3000 > monkey_$SEED.txt 2>&1
  tail -3 monkey_$SEED.txt >> "$LOG"
  check_healthy "monkey seed=$SEED"
  snap "monkey_$SEED"
done
log "monkey phase OK"

# ---------- phase 3: guided random walker ----------
# 每步枚举本包可点控件 -> 随机点一个;小概率乱滑/返回;走 260 步
SEED=777
ACTS=260
miss=0
taps=0
adb shell am start -n $PKG/.SplashActivity > /dev/null 2>&1
sleep 4
for i in $(seq 1 $ACTS); do
  if ! app_alive; then
    log "FAIL: app died at walker action $i"
    snap fail_walker
    exit 1
  fi
  # dump 当前界面(3 次重试)
  ok=1
  for t in 1 2 3; do
    adb shell uiautomator dump /sdcard/w.xml > /dev/null 2>&1
    adb pull /sdcard/w.xml w.xml > /dev/null 2>&1
    tr -d '\r' < w.xml > w2.xml && mv w2.xml w.xml
    if grep -q "com.pindou.app" w.xml 2>/dev/null; then ok=0; break; fi
    sleep 1.5
  done
  if [ $i = 1 ] || [ $i = 5 ] || [ $i = 20 ]; then
    SZ=$(wc -c < w.xml 2>/dev/null | tr -d ' \r')
    CL=$(grep -c 'clickable="true"' w.xml 2>/dev/null)
    OP=$(grep -c "package=\"$PKG\"" w.xml 2>/dev/null)
    FOC=$(adb shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1 | tr -d '\r')
    log "walker act $i debug: xml=${SZ}B clickable=$CL ourpkg=$OP dumpok=$ok focus=$FOC"
  fi
  tags=""
  if [ "$ok" = "0" ]; then
    tags=$(grep -o '<node[^>]*>' w.xml 2>/dev/null | grep 'clickable="true"')
  fi
  centers=$(printf '%s\n' "$tags" | grep "package=\"$PKG\"" \
    | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
    | sed 's/bounds="//; s/"$//' \
    | awk -F'[],[],' '{ if ($2 < $4 && $3 < $5) print int(($2+$4)/2), int(($3+$5)/2) }')
  if [ -z "$(printf '%s\n' "$centers" | grep .)" ]; then
    # 两级回退:本包无可点控件时,点任意包的可点控件(对话框/选图器也算,
    # 和真人一样会点到系统 UI;排除桌面防误开别的 APP);仍为空才算 miss
    centers=$(printf '%s\n' "$tags" | grep -v 'package="com.google.android.apps.nexuslauncher"' \
      | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
      | sed 's/bounds="//; s/"$//' \
      | awk -F'[],[],' '{ if ($2 < $4 && $3 < $5) print int(($2+$4)/2), int(($3+$5)/2) }')
  fi
  if [ $i = 5 ]; then
    log "walker act 5 sample tags: $(printf '%s\n' "$tags" | head -c 260)"
  fi
  N=$(printf '%s\n' "$centers" | grep -c .)
  if [ "$N" = "0" ]; then
    miss=$((miss + 1))
    if [ "$miss" -ge 4 ]; then
      log "walker act $i: no targets x4, BACK + relaunch"
      adb shell input keyevent 4; sleep 1
      adb shell am start -n $PKG/.SplashActivity > /dev/null 2>&1
      sleep 3
      miss=0
    fi
    continue
  fi
  miss=0
  K=$(awk -v s="$SEED" -v i="$i" -v n="$N" 'BEGIN{ srand(s + i*7919); print int(rand()*n) }')
  XY=$(printf '%s\n' "$centers" | sed -n "$((K + 1))p")
  X=$(echo "$XY" | cut -d' ' -f1)
  Y=$(echo "$XY" | cut -d' ' -f2)
  adb shell input tap "$X" "$Y"
  taps=$((taps + 1))
  echo "walker act $i: tap $XY" >> "$LOG"
  sleep 0.9
  R=$(awk -v s="$SEED" -v i="$i" 'BEGIN{ srand(s + i*104729); print int(rand()*100) }')
  if [ "$R" -lt 10 ]; then
    D=$(awk -v s="$SEED" -v i="$i" 'BEGIN{ srand(s + i*15485863); print int(rand()*4) }')
    case $D in
      0) adb shell input swipe 540 1700 540 600 220 ;;
      1) adb shell input swipe 540 800 540 1900 220 ;;
      2) adb shell input swipe 200 1200 900 1200 220 ;;
      3) adb shell input swipe 900 1200 200 1200 220 ;;
    esac
    echo "walker act $i: random swipe dir=$D" >> "$LOG"
    sleep 0.8
  elif [ "$R" -lt 14 ]; then
    adb shell input keyevent 4
    echo "walker act $i: BACK" >> "$LOG"
    sleep 1.2
  fi
  if [ $((i % 60)) = "0" ]; then
    snap "walker_$i"
    check_healthy "walker $i"
  fi
done
check_healthy "walker done"
log "walker phase OK: $taps taps in $ACTS actions"

# ---------- verdict ----------
log "ALL FUZZ PHASES PASSED (no app crash / no ANR)"
log "note: system-app crashes are out of scope by design"
exit 0
