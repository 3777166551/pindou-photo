#!/usr/bin/env bash
# 稳定性专项(2026-09-29):与 smoke(功能走查)/fuzz(随机事件)互补,
# 盖三种 smoke/fuzz 都不触发的故障类:
#   ① 循环压力 —— 首页↔编辑器高频进出(Activity 重建/泄漏/窗口泄漏)
#   ② 转屏重建 —— 配置变更连发(状态保留/重建崩溃)
#   ③ 崩溃恢复 —— 运行中进程被 am crash 杀死后重开(存档/草稿兜底链)
#   ④ 内存水位 —— 循环前后 PSS 对比(泄漏趋势,宽松阈值)
# 全程 logcat FATAL/ANR 扫描(crash 段例外,am crash 的崩溃是预期的);
# 任一断言失败退出非零,截图/日志进 artifact。
set -u
export PATH="$ANDROID_HOME/platform-tools:$PATH"
PKG=com.pindou.app
SHOT=stability_shots
mkdir -p "$SHOT"
FAIL=0

snap() { adb exec-out screencap -p > "$SHOT/$1.png" 2>/dev/null; }

fatal_scan() {
  local tag="$1"
  if adb logcat -d 2>/dev/null | grep -E "FATAL EXCEPTION|ANR in $PKG" \
      > stability_fatal.txt; then
    echo "[stability] FAIL: FATAL/ANR after $tag"
    head -5 stability_fatal.txt
    FAIL=1
  fi
  adb logcat -c
}

pss_kb() {
  adb shell dumpsys meminfo -d "$PKG" 2>/dev/null | tr -d '\r' \
    | awk '/TOTAL PSS/ {print $3}' | head -1
}

front_is_ours() {
  adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' \
    | grep -q "topResumedActivity.*$PKG\|mResumedActivity.*$PKG" \
    || adb shell dumpsys window 2>/dev/null | tr -d '\r' \
    | grep -q "mCurrentFocus=Window{.*$PKG"
}

adb logcat -c

echo "=== 1/4 cycle pressure: home<->editor x20 ==="
# 热身+真实 PSS 基线(旧版在冷启动前取样,P0 恒空,水位检查形同虚设)
adb shell am start -n "$PKG/.MainActivity" 2>&1 | sed 's/^/[stability] am: /'
sleep 2
P0=$(pss_kb)
for i in $(seq 1 20); do
  adb shell am start -n "$PKG/.EditorActivity" 2>&1 | sed 's/^/[stability] am: /'
  sleep 1.5
  # 前台断言放 am start 之后、BACK 之前——BACK 本来就回桌面,旧版在
  # BACK 之后断言,逢 10 轮必挂(0930 run 实锤:截图=桌面,零 FATAL)
  if ! front_is_ours; then
    sleep 1.5
    if ! front_is_ours; then
      echo "[stability] FAIL: app not foreground at cycle $i (after am start)"
      FAIL=1
      snap "cycle_fail_$i"
    fi
  fi
  adb shell input keyevent 4 >/dev/null 2>&1
  sleep 0.5
  if [ $((i % 10)) -eq 0 ]; then
    snap "cycle_$i"
  fi
done
fatal_scan "cycle pressure"

echo "=== 2/4 rotation rebuild x10 (inside editor) ==="
adb shell am start -n "$PKG/.EditorActivity" 2>&1 | sed 's/^/[stability] am: /'
sleep 2.5
if ! front_is_ours; then
  # 兜底重拉(0930 run 实锤:循环段 BACK 回桌面后 am start 偶发不落前台)
  adb shell am start -n "$PKG/.EditorActivity" 2>&1 | sed 's/^/[stability] am2: /'
  sleep 2.5
fi
front_is_ours || { echo "[stability] FAIL: editor never foreground before rotations"; FAIL=1; }
adb shell settings put system accelerometer_rotation 0
for i in $(seq 1 10); do
  adb shell settings put system user_rotation $((i % 2))
  sleep 2.2
  if ! front_is_ours; then
    sleep 1.5
    if ! front_is_ours; then
      echo "[stability] FAIL: app not foreground at rotation $i"
      FAIL=1
      snap "rotation_fail_$i"
    fi
  fi
done
adb shell settings put system user_rotation 0
adb shell settings put system accelerometer_rotation 1
sleep 1
fatal_scan "rotation"
snap stability_rotation

echo "=== 3/4 crash-recovery x5 (am kill/crash then relaunch) ==="
adb shell am start -n "$PKG/.EditorActivity" >/dev/null 2>&1
sleep 2
for i in $(seq 1 5); do
  adb shell am crash "$PKG" >/dev/null 2>&1     # 注入崩溃(本段 FATAL 为预期)
  sleep 2
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 2.5
  front_is_ours || { echo "[stability] FAIL: no recovery after crash $i"; FAIL=1; }
  snap "recovery_$i"
  adb logcat -c                                  # 清掉预期崩溃,防污染后续扫描
done
fatal_scan "crash recovery"

echo "=== 4/4 memory watermark ==="
adb shell am start -n "$PKG/.EditorActivity" >/dev/null 2>&1
sleep 2
P1=$(pss_kb)
echo "[stability] PSS before=$P0 after=$P1 (KB)"
if [ -n "$P0" ] && [ -n "$P1" ]; then
  GROWTH=$(( P1 - P0 ))
  if [ "$GROWTH" -gt 80000 ]; then
    echo "[stability] FAIL: PSS grew ${GROWTH}KB (>80MB) — suspected leak"
    FAIL=1
  fi
  if [ "$P1" -gt 400000 ]; then
    echo "[stability] FAIL: PSS ${P1}KB too high"
    FAIL=1
  fi
fi
fatal_scan "memory"
snap stability_final

if [ "$FAIL" = "0" ]; then
  echo "[stability] ALL PASS"
else
  echo "[stability] FAILED"
  exit 1
fi
