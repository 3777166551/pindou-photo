#!/usr/bin/env bash
# ============================================================
#  AR 试摆(假 AR)专项冒烟:v2.49
#  网上找的豆板照片(qa/ar_data,许可见同目录 CREDITS.md)
#  -> 注入 app 私有目录 -> 编辑器生成图纸 -> 效果页「AR preview」
#  chip -> 相机取景 + 陀螺仪叠层 -> 逐图截图人眼审。
#  硬断言:AR 页必须出现(「Re-center」chip + 提示文案),
#  退出后编辑器存活,全程无本包 FATAL EXCEPTION。
#  注意:CI 模拟器必须带虚拟摄像头启动(-camera-back virtualscene),
#  主 workflow 的 -camera-back none 会让 AR 页直接报错退出。
#  Usage: bash qa/ar_smoke.sh   (needs adb + booted emulator,
#         run from repo root; APK path via APK= env or apk/)
# ============================================================
set -u
export PATH="$ANDROID_HOME/platform-tools:$PATH"

SHOTS=shots
PKG="com.pindou.app"
mkdir -p "$SHOTS"
i=0

log() { echo "[ar-smoke] $*"; }
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

_tap_match() {
  local pat="$1" must="$2" b x1 y1 x2 y2
  dump_ui || return 1
  b=$(grep -oi "$pat[^\>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" ui.xml \
    | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
  [ -z "$b" ] && return 1
  b=${b#bounds=\"}; b=${b%\"}
  x1=${b%%,*};       x1=${x1#[}
  y1=${b#*,};        y1=${y1%%]*}
  y2=${b##*,};       y2=${y2%]}
  x2=${b#*][};       x2=${x2%%,*}
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  sleep 1.2
  return 0
}

tap_id() {
  local id="$PKG:id/$1" must="${2:-1}" n
  for n in 0 1 2 3 4 5 6; do
    if [ "$n" -gt 0 ]; then
      adb shell input swipe 540 1600 540 700 250; sleep 0.8
    fi
    if _tap_match "resource-id=\"$id\"" 0; then
      log "tapped id: $1"
      return 0
    fi
  done
  if [ "$must" = "1" ]; then
    echo "[ar-smoke] FAIL: id not found: $1"
    snap fail
    exit 1
  fi
  log "soft-miss id: $1"
}

tap_text() {
  local txt="$1" must="${2:-1}" n
  for n in 0 1 2 3 4 5 6; do
    if [ "$n" -gt 0 ]; then
      adb shell input swipe 540 1600 540 700 250; sleep 0.8
    fi
    if _tap_match "text=\"[^\"]*${txt}[^\"]*\"" 0; then
      log "tapped: $txt"
      return 0
    fi
  done
  if [ "$must" = "1" ]; then
    echo "[ar-smoke] FAIL: text not found: $txt"
    snap fail
    exit 1
  fi
  log "soft-miss text: $txt"
}

check_text() {
  local txt="$1" must="${2:-1}" n
  for n in 1 2 3; do
    dump_ui
    if grep -qi "text=\"[^\"]*${txt}[^\"]*\"" ui.xml; then
      log "found: $txt"
      return 0
    fi
    sleep 2
  done
  if [ "$must" = "1" ]; then
    echo "[ar-smoke] FAIL: expected text missing: $txt"
    snap fail
    exit 1
  fi
  log "soft-miss text: $txt"
}

# 等图纸生成完成:豆单摘要出现 "Total beads"
gen_wait() {
  local n
  for n in 1 2 3 4 5 6 7 8; do
    dump_ui
    if grep -qi "text=\"[^\"]*Total beads[^\"]*\"" ui.xml; then
      log "pattern generated"
      return 0
    fi
    sleep 4
    tap_id tabList 0 > /dev/null 2>&1
  done
  return 1
}

back() { adb shell input keyevent 4; sleep 1.5; }

ensure_home() {
  local n
  for n in 1 2 3; do
    dump_ui && grep -qi "text=\"[^\"]*Start a new pattern[^\"]*\"" ui.xml && {
      log "home visible"; return 0
    }
    adb shell am start -n $PKG/.SplashActivity > /dev/null 2>&1
    sleep 4
  done
  adb shell monkey -p $PKG 1 > /dev/null 2>&1
  sleep 3
  log "soft-miss: home not confirmed"
}

wait_boot() {
  for _ in $(seq 1 60); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return 0
    sleep 5
  done
  echo "[ar-smoke] emulator boot timeout"; exit 1
}

# ---------- 启动 ----------
adb wait-for-device
wait_boot
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
log "installing APK..."
if ! adb install -r ${APK:-apk/*.apk}; then
  echo "[ar-smoke] FAIL: adb install failed"
  exit 1
fi
# AR 页要开相机:debug 包直接授运行时权限,跳过权限弹窗
adb shell pm grant $PKG android.permission.CAMERA > /dev/null 2>&1 || true
adb root > /dev/null 2>&1
adb wait-for-device
sleep 2

adb shell am start -n $PKG/.SplashActivity
sleep 4
ensure_home
snap home

# ---------- 逐张豆板图:照片 -> 图纸 -> AR 试摆 ----------
IMAGES=(qa/ar_data/ar_*.jpg)
if [ "${#IMAGES[@]}" -lt 3 ]; then
  echo "[ar-smoke] FAIL: expected >=3 test images in qa/ar_data, found ${#IMAGES[@]}"
  exit 1
fi

for IMG in "${IMAGES[@]}"; do
  NAME=$(basename "$IMG" .jpg)
  log "=== board image: $NAME ==="
  adb push "$IMG" /data/local/tmp/ar_img.png > /dev/null
  adb shell chmod 644 /data/local/tmp/ar_img.png
  adb shell run-as $PKG mkdir -p files > /dev/null 2>&1
  if ! adb shell run-as $PKG cp /data/local/tmp/ar_img.png files/ar_img.png; then
    adb shell run-as $PKG sh -c 'cp /data/local/tmp/ar_img.png files/ar_img.png'
  fi
  adb shell ls -l /data/data/$PKG/files/ | grep ar_img || {
    echo "[ar-smoke] FAIL: image not injected ($NAME)"
    exit 1
  }

  # 直开编辑器(root 不受 exported/后台启动限制;与主冒烟同路径)
  adb shell am start -n $PKG/.EditorActivity \
    --es photo_uri "file:///data/data/$PKG/files/ar_img.png"
  sleep 4
  check_text "Bead list"
  gen_wait || { echo "[ar-smoke] FAIL: pattern not generated ($NAME)"; snap fail; exit 1; }

  # 效果页 -> AR chip -> AR 页出现(硬断言:摆正 chip;提示文案软检查)
  tap_id tabEffect 0
  sleep 1
  tap_id chipAr
  sleep 3.5
  check_text "Re-center"
  dump_ui
  grep -qi "text=\"[^\"]*Look around[^\"]*\"" ui.xml \
    || log "soft-miss: hint text not visible on $NAME"
  sleep 2                    # 等相机预览出帧 + 叠层画上去
  snap "ar_$NAME"

  # 摆正按钮可点(重新锚定),再截一张;板子应仍在前方
  tap_text "Re-center" 0
  sleep 2.5
  snap "ar_${NAME}_recenter"

  # 返回编辑器必须存活,再退回首页
  back
  sleep 1
  check_text "Bead list"
  back
  ensure_home
done

# ---------- 崩溃检查(只认本包) ----------
snap final
if adb logcat -d | grep -A 3 "FATAL EXCEPTION" | grep -q "Process: $PKG"; then
  adb logcat -d | grep -A 40 "FATAL EXCEPTION" | head -80
  echo "[ar-smoke] APP CRASHED"
  exit 1
fi
log "no fatal exceptions"
log "AR SMOKE PASSED"
