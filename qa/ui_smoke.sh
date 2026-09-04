#!/usr/bin/env bash
# ============================================================
#  Extended UI smoke test: black-box click-through of the main
#  flows (home cards, knowledge, templates, blank canvas editor,
#  bead list, palette manager, inventory, text generator).
#  Runs against the CI emulator (en locale) so it also verifies
#  the English translations end to end.
#  Buttons with resource ids are tapped by id (locale-independent);
#  dialog buttons are tapped by visible text with scroll/retry.
#  Hard steps exit 1 on failure; soft steps only log.
#  Usage: bash qa/ui_smoke.sh   (needs adb + booted emulator)
# ============================================================
set -u
export PATH="$ANDROID_HOME/platform-tools:$PATH"

SHOTS=shots
PKG="com.pindou.app"
mkdir -p "$SHOTS"
i=0

log() { echo "[smoke] $*"; }
snap() {
  i=$((i + 1))
  adb shell screencap -p /sdcard/s.png > /dev/null 2>&1
  adb pull /sdcard/s.png "$SHOTS/$(printf '%02d' $i)_$1.png" > /dev/null 2>&1
}

dump_ui() {
  local n ok=1
  for n in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
    tr -d '\r' < ui.xml > ui2.xml && mv ui2.xml ui.xml
    if grep -q "com.pindou.app" ui.xml 2>/dev/null; then ok=0; break; fi
    sleep 2
  done
  return $ok
}

# 从 ui.xml 取第一个匹配属性的 bounds 中心并点击
# $1 = 属性匹配片段(如 "resource-id=\".../btnNew\""), $2 = must(1/0)
_tap_match() {
  local pat="$1" must="$2" b x1 y1 x2 y2
  dump_ui
  b=$(grep -o "$pat[^\>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" ui.xml \
    | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
  [ -z "$b" ] && return 1
  b=${b#bounds=\"}; b=${b%\"}          # -> [72,168][300,264]
  x1=${b%%,*};       x1=${x1#[}
  y1=${b#*,};        y1=${y1%%]*}
  y2=${b##*,};       y2=${y2%]}
  x2=${b#*][};       x2=${x2%%,*}
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  sleep 1.2
  return 0
}

# 按 resource-id 结尾点击(id 与语言无关),自动滚动查找
tap_id() {
  local id="$PKG:id/$1" must="${2:-1}" n
  for n in 0 1 2 3; do
    if [ "$n" -gt 0 ]; then
      adb shell input swipe 540 1600 540 700 250; sleep 0.8
    fi
    if _tap_match "resource-id=\"$id\"" 0; then
      log "tapped id: $1"
      return 0
    fi
  done
  if [ "$must" = "1" ]; then
    echo "[smoke] FAIL: id not found: $1"
    snap fail
    exit 1
  fi
  log "soft-miss id: $1"
}

# 按可见文本点击(对话框按钮等),自动滚动查找
tap_text() {
  local txt="$1" must="${2:-1}" n
  for n in 0 1 2 3; do
    if [ "$n" -gt 0 ]; then
      adb shell input swipe 540 1600 540 700 250; sleep 0.8
    fi
    if _tap_match "text=\"[^\"]*${txt}[^\"]*\"" 0; then
      log "tapped: $txt"
      return 0
    fi
  done
  if [ "$must" = "1" ]; then
    echo "[smoke] FAIL: text not found: $txt"
    snap fail
    exit 1
  fi
  log "soft-miss text: $txt"
}

check_text() {
  local txt="$1" must="${2:-1}" n
  for n in 1 2 3; do
    dump_ui
    if grep -q "text=\"[^\"]*${txt}[^\"]*\"" ui.xml; then
      log "found: $txt"
      return 0
    fi
    sleep 2
  done
  if [ "$must" = "1" ]; then
    echo "[smoke] FAIL: expected text missing: $txt"
    snap fail
    exit 1
  fi
  log "soft-miss text: $txt"
}

back() { adb shell input keyevent 4; sleep 1.5; }

# 确保回到首页:不在首页就 am start 拉回前台(不会重建任务)
ensure_home() {
  local n
  for n in 1 2 3; do
    dump_ui && grep -q "text=\"[^\"]*Start a new pattern[^\"]*\"" ui.xml && {
      log "home visible"; return 0
    }
    adb shell am start -n $PKG/.MainActivity > /dev/null 2>&1
    sleep 2
  done
  log "soft-miss: home not confirmed"
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
log "installing APK..."
if ! adb install -r ${APK:-apk/*.apk}; then
  echo "[smoke] FAIL: adb install failed"
  exit 1
fi

adb shell am start -n $PKG/.SplashActivity
sleep 4
# 等到主界面就绪(最多再等 40s)
for _ in $(seq 1 20); do
  dump_ui && grep -q "text=\"[^\"]*Start a new pattern[^\"]*\"" ui.xml && break
  sleep 2
done
sleep 2
snap home
check_text "Start a new pattern"
log "home OK"

# ---------- 拼豆知识 ----------
tap_id btnKnowledge
sleep 1.5
check_text "What are fuse beads?"
snap knowledge
back
ensure_home

# ---------- 模板库 ----------
tap_id btnTemplates
sleep 2.5
check_text "Design templates" 0
snap templates
tap_text "Close" 0
back
ensure_home

# ---------- 空白画布进入编辑器 ----------
tap_id btnBlank
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
tap_id tabList
sleep 1
check_text "Usage"
snap list

# ---------- 我的色板管理 ----------
tap_id btnPalettes
sleep 2
check_text "My palettes"
snap palettes
tap_id btnNew
sleep 1.5
check_text "New palette"
tap_id btnAddColor 0
tap_text "Add color" 0
sleep 1.5
snap color_picker
tap_text "OK"
sleep 1
tap_text "Save"
sleep 1.5
check_text "tap to manage" 0
snap palette_saved
back
sleep 1

# ---------- 豆豆清单 + 豆仓 ----------
tap_id tabList 0
sleep 1
tap_id btnInventory
sleep 2
snap inventory
tap_text "Cancel"
sleep 1
tap_id tabPattern 0
sleep 1

# ---------- 恢复默认 + 返回首页 ----------
tap_id btnReset 0
back
ensure_home
sleep 1

# ---------- 文字生成 ----------
tap_id btnText 0
sleep 1.5
check_text "Text to bead pattern" 0
adb shell input tap 540 960
sleep 0.8
adb shell input text "HI"
sleep 0.5
tap_text "Generate" 0
sleep 8
snap textgen
back
ensure_home

# ---------- 我的项目(空) ----------
tap_id btnProjects 0
sleep 1.5
snap projects
back
ensure_home

# ---------- 崩溃检查 ----------
snap final
if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d | grep -A 40 "FATAL EXCEPTION" | head -80
  echo "[smoke] APP CRASHED"
  exit 1
fi
log "no fatal exceptions"
log "ALL UI SMOKE STEPS PASSED"
