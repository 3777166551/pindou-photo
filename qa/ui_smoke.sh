#!/usr/bin/env bash
# ============================================================
#  Extended UI smoke test: black-box click-through of the main
#  flows (home cards, knowledge, templates, blank canvas editor,
#  bead list, palette manager, inventory, text generator).
#  Runs against the CI emulator (en locale) so it also verifies
#  the English translations end to end.
#  Hard steps exit 1 on failure; soft steps only log.
#  Usage: bash qa/ui_smoke.sh   (needs adb + booted emulator)
# ============================================================
set -u
export PATH="$ANDROID_HOME/platform-tools:$PATH"

SHOTS=shots
mkdir -p "$SHOTS"
FAIL=""
i=0

log() { echo "[smoke] $*"; }
snap() {
  i=$((i + 1))
  adb shell screencap -p /sdcard/s.png > /dev/null 2>&1
  adb pull /sdcard/s.png "$SHOTS/$(printf '%02d' $i)_$1.png" > /dev/null 2>&1
}

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
  # 去掉 \r,避免 bounds 解析受影响
  tr -d '\r' < ui.xml > ui2.xml && mv ui2.xml ui.xml
}

# 取包含指定文本的节点中心并点击;must=1 时失败即退出
tap_text() {
  local txt="$1" must="${2:-1}" b x1 y1 x2 y2
  for _ in 1 2 3; do
    dump_ui
    b=$(grep -o "text=\"[^\"]*${txt}[^\"]*\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" ui.xml \
      | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
    if [ -n "$b" ]; then
      x1=$(echo "$b" | sed 's/\[\([0-9]*\),.*/\1/')
      y1=$(echo "$b" | sed 's/\[[0-9]*,\([0-9]*\)\].*/\1/')
      x2=$(echo "$b" | sed 's/.*\]\[\([0-9]*\),.*/\1/')
      y2=$(echo "$b" | sed 's/.*,\([0-9]*\)\].*/\1/')
      adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
      sleep 1.2
      log "tapped: $txt"
      return 0
    fi
    sleep 1
  done
  if [ "$must" = "1" ]; then
    echo "[smoke] FAIL: text not found: $txt"
    FAIL="$FAIL|$txt"
    exit 1
  fi
  log "soft-miss: $txt"
  return 0
}

check_text() {
  local txt="$1" must="${2:-1}"
  dump_ui
  if grep -q "text=\"[^\"]*${txt}[^\"]*\"" ui.xml; then
    log "found: $txt"
    return 0
  fi
  if [ "$must" = "1" ]; then
    echo "[smoke] FAIL: expected text missing: $txt"
    snap fail
    exit 1
  fi
  log "soft-miss text: $txt"
}

wait_boot() {
  for _ in $(seq 1 60); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return 0
    sleep 5
  done
  echo "[smoke] emulator boot timeout"; exit 1
}

# ---------- 启动 ----------
adb wait-for-device
wait_boot
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r ${APK:-apk/*.apk} > /dev/null

adb shell am start -n com.pindou.app/.SplashActivity
sleep 8
snap home
check_text "Start a new pattern"
log "home OK"

# ---------- 拼豆知识 ----------
tap_text "Bead basics"
sleep 1.5
check_text "What are fuse beads?"
snap knowledge
adb shell input keyevent 4; sleep 1

# ---------- 模板库 ----------
tap_text "Templates"
sleep 2
tap_text "Categories" 0
sleep 1.5
snap templates
adb shell input keyevent 4; sleep 1.5
tap_text "Close" 0
adb shell input keyevent 4; sleep 1

# ---------- 空白画布进入编辑器 ----------
tap_text "Blank canvas"
sleep 4
check_text "Chart"
snap editor
log "editor opened"

# 画两笔
adb shell input swipe 300 700 600 900 300
adb shell input swipe 500 650 700 850 300
sleep 1
snap painted

# ---------- 标签页切换 ----------
tap_text "Bead list"
sleep 1
check_text "Usage"
snap list
tap_text "Preview"
sleep 1
snap preview_tab
tap_text "Chart"
sleep 1

# ---------- 我的色板管理 ----------
tap_text "My palettes"
sleep 2
check_text "My palettes"
snap palettes
tap_text "New"
sleep 1.5
check_text "New palette"
tap_text "Add color"
sleep 1.5
snap color_picker
check_text "Color name" 0
tap_text "OK"
sleep 1
tap_text "Save"
sleep 1.5
check_text "tap to manage" 0
snap palette_saved
adb shell input keyevent 4; sleep 1.5
check_text "Chart" 0

# ---------- 豆仓库存 ----------
tap_text "Inventory"
sleep 2
snap inventory
tap_text "Cancel"
sleep 1

# ---------- 恢复默认 + 返回首页 ----------
tap_text "Reset" 0
adb shell input keyevent 4; sleep 2

# ---------- 文字生成 ----------
tap_text "Text art" 0
sleep 1.5
check_text "Text to bead pattern" 0
adb shell input text "HI"
sleep 0.5
tap_text "Generate" 0
sleep 8
snap textgen
adb shell input keyevent 4; sleep 1.5
adb shell input keyevent 4; sleep 1.5

# ---------- 我的项目(空) ----------
tap_text "My projects" 0
sleep 1.5
snap projects
adb shell input keyevent 4; sleep 1

# ---------- 崩溃检查 ----------
snap final
if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d | grep -A 40 "FATAL EXCEPTION" | head -80
  echo "[smoke] APP CRASHED"
  exit 1
fi
log "no fatal exceptions"
log "ALL UI SMOKE STEPS PASSED"
