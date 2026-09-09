#!/usr/bin/env bash
# ============================================================
#  Full-flow UI smoke test: black-box click-through.
#  A) COMPLETE fuse-bead pipeline: inject test photo into the
#     app dir (debug pkg + run-as) -> editor auto-generates ->
#     params (limit / brand palette / style / shape / bead size)
#     -> bead list stats (pack conversion) -> night mode ->
#     exports (PNG sheet / PDF / JSON / share card) -> save
#     project -> reopen from My projects -> merged shopping
#     list + CSV export.
#  B) Coverage walk: home cards, camera/scan entries (soft),
#     knowledge, template gallery + Daily pick full open,
#     blank-canvas editor (paint / symmetry / bead-size /
#     assist board+row / trace), palette manager, inventory,
#     text generator, 3D preview, line art, color-match entry,
#     projects dialog, crash check.
#  Runs against the CI emulator (en locale) so it also verifies
#  the English translations end to end.
#  Buttons with resource ids are tapped by id (locale-independent);
#  dialog buttons are tapped by visible text with scroll/retry.
#  Hard steps exit 1 on failure; soft steps only log.
#  Usage: bash qa/ui_smoke.sh   (needs adb + booted emulator,
#         run from repo root; APK path via APK= env or apk/)
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
  dump_ui || return 1
  b=$(grep -oi "$pat[^\>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" ui.xml \
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

# 按完全相等的可见文本点击(标题里含同词时避免误中,如「Save project」vs「Save」)
tap_text_exact() {
  local txt="$1" must="${2:-1}" n
  for n in 0 1 2 3; do
    if [ "$n" -gt 0 ]; then
      adb shell input swipe 540 1600 540 700 250; sleep 0.8
    fi
    if _tap_match "text=\"$txt\"" 0; then
      log "tapped exact: $txt"
      return 0
    fi
  done
  if [ "$must" = "1" ]; then
    echo "[smoke] FAIL: exact text not found: $txt"
    snap fail
    exit 1
  fi
  log "soft-miss exact text: $txt"
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
    echo "[smoke] FAIL: expected text missing: $txt"
    snap fail
    exit 1
  fi
  log "soft-miss text: $txt"
}

# 点当前屏幕上第一个输入框(存档命名等对话框)
tap_edittext() {
  local b x1 y1 x2 y2
  dump_ui || return 1
  b=$(grep -oi 'class="android.widget.EditText"[^\>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' ui.xml \
    | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
  [ -z "$b" ] && return 1
  b=${b#bounds=\"}; b=${b%\"}
  x1=${b%%,*};       x1=${x1#[}
  y1=${b#*,};        y1=${y1%%]*}
  y2=${b##*,};       y2=${y2%]}
  x2=${b#*][};       x2=${x2%%,*}
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  sleep 1
  return 0
}

# 等图纸生成完成:豆单摘要出现 "Total beads"(list tab 激活时可见)
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

# 确保回到首页:不在首页就拉起 Splash(exported,必能启动,自动进首页)
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
# 等主界面就绪;模拟器冷启动偶尔抽风(app 没起来停在桌面),
# 用 ensure_home 的重试 + monkey 兜底再拉两次
ensure_home
snap home
check_text "Start a new pattern"
log "home OK"

# ============================================================
# ★ 完整拼豆流程:照片 → 图纸 → 参数 → 豆单 → 导出 → 存档 →
#   重开 → 合并采购单(CI 硬流程,失败即退出)
# ============================================================
log "=== FULL PIPELINE: photo -> chart -> export -> save -> reopen ==="

# 0) 测试照片注入应用私有 files/(debug 包 run-as;file:// 与拍照流程同路径)
if [ ! -f qa/test_data/ci_photo.png ]; then
  echo "[smoke] FAIL: qa/test_data/ci_photo.png missing"
  exit 1
fi
adb push qa/test_data/ci_photo.png /data/local/tmp/ci_photo.png > /dev/null
adb shell chmod 644 /data/local/tmp/ci_photo.png
adb shell run-as $PKG mkdir -p files > /dev/null 2>&1
if ! adb shell run-as $PKG cp /data/local/tmp/ci_photo.png files/ci_photo.png; then
  adb shell run-as $PKG sh -c 'cp /data/local/tmp/ci_photo.png files/ci_photo.png'
fi
# root 视角核验文件就位(注入失败在此现形,别让后面瞎猜)
adb root > /dev/null 2>&1
adb wait-for-device
sleep 2
log "photo file in app dir:"
adb shell ls -l /data/data/$PKG/files/ | grep ci_photo || {
  echo "[smoke] FAIL: ci_photo.png not injected"
  exit 1
}

# 1) 首页「相册选图」入口(soft):选图器能拉起即算过,
#    不依赖系统选图器内部 UI(CI 上标签/布局不稳定)
tap_id btnGallery 0
sleep 2
snap gallery_resolver
back
sleep 1

# 2) 直接带照片 URI 进编辑器(与 onActivityResult→openEditor 同参数)。
#    EditorActivity 未导出:shell 无权限,run-as(app uid)会撞 Android 10
#    后台启动限制 —— 先 adb root(root 不受 exported/BAL 限制),run-as 兜底
PHOTO_URI="file:///data/data/com.pindou.app/files/ci_photo.png"
adb shell am start -n $PKG/.EditorActivity --es photo_uri "$PHOTO_URI"
sleep 4
dump_ui
if ! grep -qi "text=\"[^\"]*Bead list[^\"]*\"" ui.xml; then
  log "root am start did not open editor, retry via run-as"
  adb shell run-as $PKG am start -n $PKG/.EditorActivity --es photo_uri "$PHOTO_URI"
  sleep 4
fi
check_text "Bead list"
log "editor opened with photo"

# 3) 生成完成 → 三个 tab 各截一张(58×58 默认档)
tap_id tabList
gen_wait || { echo "[smoke] FAIL: pattern not generated"; snap fail; exit 1; }
snap photo_list
# 奶油底占大头 → 单色 ≥500 颗 → 「≈N pack(s) of 1000」按包换算必须出现;
# 摘要行含 Est. 时长 + 难度(v2.41)
check_text "pack(s)" 0
check_text "Est." 0
tap_id tabPattern
sleep 1
snap photo_chart
tap_id tabEffect
sleep 1
snap photo_effect

# 4) 参数联动(切 29×29 提速后续重生成)
tap_id chip29 0
sleep 5
# 限色 12 → 豆单颜色数变化(人眼审截图)
tap_id chipLimit1 0
sleep 6
tap_id tabList 0
sleep 1
snap photo_limit12
check_text "Colors" 0
# 豆单 tab 下设置面板是 GONE 的(587-594 行),先切回图纸 tab 再动控件
tap_id tabPattern
sleep 1
tap_id chipLimit0 0
sleep 5

# 5) 品牌色号表联动:Nabbi 表 → 色号列 + 自动切迷你规格。
#    之后整条管线保持 Nabbi 跑(导出/PDF/存档顺带覆盖品牌色号);
#    不切回通用表 —— 再开一次下拉再选中是纯状态卫生,还容易把
#    下拉列表滚乱引发后续控件连锁找不到(2026-09-09 第三轮教训)
tap_id paletteSpinner 0
sleep 1.5
tap_text "Nabbi" 0
sleep 1.2
snap photo_brand_nabbi
sleep 6

# 6) 抽象风格 + 砖块纹理 → 回写实
tap_id chipStyleAbs 0
sleep 1.5
tap_id chipBrickMid 0
sleep 6
snap photo_abstract
tap_id chipStyleReal 0
sleep 6

# 7) 圆形板 → 效果图/图纸按圆渲染。
#    上一步把面板停在样式卡,形状卡在上方:tap_id 只往下扫,
#    先滚回页顶再找(2026-09-09 第四轮教训:位置型连锁)
adb shell input swipe 540 600 540 2100 300; sleep 0.6
adb shell input swipe 540 600 540 2100 300; sleep 0.8
tap_id chipShapeRound 0
sleep 5
snap photo_round
tap_id chipShapeRect 0
sleep 4

# 8) 真照片裁剪:拖角缩小选区 → 拖中间移动 → OK 应用(重新生成)
tap_id btnCrop 0
sleep 2.5
adb shell input swipe 880 1410 620 1180 400; sleep 0.8
adb shell input swipe 540 900 400 780 400; sleep 0.8
snap photo_crop
tap_text "OK"
sleep 10

# 9) 夜间图纸(画布转暗,弹窗项即开即关)
tap_id btnMenu
sleep 1.5
tap_text "Night chart on" 0
sleep 1.5
snap photo_night
tap_id btnMenu
sleep 1.5
tap_text "Night chart off" 0
sleep 1.5

# 10) 导出四件套:PNG 图纸(存相册+toast)/ PDF / JSON / 分享长图
tap_id btnMenu
sleep 1.5
tap_text "Save chart image"
sleep 9
snap photo_export_png
check_text "Saved to Pictures" 0

tap_id btnMenu
sleep 1.5
tap_text "Export PDF" 0
sleep 14
snap photo_share_pdf
back
sleep 1.5

tap_id btnMenu
sleep 1.5
tap_text ".json" 0
sleep 9
snap photo_share_json
back
sleep 1.5

tap_id btnMenu
sleep 1.5
tap_text "Share card" 0
sleep 11
snap photo_share_card
back
sleep 1.5

# 11) 真图纸上的拼豆辅助:打卡日历
adb shell input swipe 540 1700 540 500 300; sleep 0.8
tap_id swBeadAssist 0
sleep 1.5
check_text "Find undone" 0
tap_id btnAssistCalendar 0
sleep 1.5
snap photo_calendar
tap_text "Close" 0
sleep 1
tap_id swBeadAssist 0
sleep 1
adb shell input swipe 540 600 540 2100 300; sleep 0.6

# 12) 存档项目:默认名(Beads_MMDD_HHMM)直接存,后面靠它重开。
#     标题含 Save 字样,按钮必须精确匹配防误点标题
tap_id btnMenu
sleep 1.5
tap_text "Save project"
sleep 1.5
dump_ui
PROJ_NAME=$(grep -o 'text="Beads_[0-9_]*"' ui.xml | head -1 | cut -d'"' -f2)
if [ -z "$PROJ_NAME" ]; then
  tap_edittext
  adb shell input text "CI_Flow_1"
  PROJ_NAME="CI_Flow_1"
fi
log "project name: $PROJ_NAME"
tap_text_exact "Save"
sleep 7
snap photo_saved

# 13) 我的项目 → 合并采购单(首项目默认勾选)→ Export CSV
back
ensure_home
tap_id btnProjects
sleep 2
check_text "$PROJ_NAME"
snap projects_list
tap_text "Merge shopping" 0
sleep 1.5
snap merge_pick
tap_text "Generate" 0
sleep 7
check_text "Merged shopping"
snap merge_bom
tap_text "Export CSV" 0
sleep 5
snap merge_csv
back
sleep 1
tap_text "Close" 0
sleep 1

# 14) 重开存档:状态还原(照片+设置重建图纸)
tap_text "$PROJ_NAME" 0
sleep 12
check_text "Bead list"
tap_id tabPattern 0
sleep 1
snap project_reopened
log "project reopened OK"
back
ensure_home

# ============================================================
# 覆盖走查(与历史版本一致的其余入口)
# ============================================================

# ---------- 相机 / 识别图纸入口(CI 模拟器无相机/选图,soft) ----------
tap_id btnCamera 0
sleep 1.5
snap camera_entry
tap_id btnScanPattern 0
sleep 2
snap scan_entry
back
sleep 1
ensure_home

# ---------- 拼豆知识 ----------
tap_id btnKnowledge
sleep 1.5
check_text "What are fuse beads?"
check_text "Project ideas" 0
snap knowledge
back
ensure_home

# ---------- 模板库 + 每日一拼直达出图 ----------
tap_id btnTemplates
sleep 2.5
check_text "Design templates" 0
check_text "Daily pick" 0
snap templates
tap_text "Daily pick"
sleep 9
check_text "Bead list"
tap_id tabList 0
sleep 1
check_text "Usage" 0
snap template_editor
back
sleep 1
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

# ---------- v2.42:对称绘画(趁画笔状态可靠,紧跟 painted) ----------
# 下滚一屏找 ✳ chip,切四象限;滚回顶部画一笔 -> 应出现 4 组对称
adb shell input swipe 540 1700 540 700 300; sleep 0.6
tap_id btnSym 0
sleep 0.5
check_text "Quad" 0
adb shell input swipe 540 600 540 2100 300; sleep 0.6
adb shell input swipe 540 600 540 2100 300; sleep 0.6
adb shell input swipe 300 700 600 900 300; sleep 0.5
snap sym_quad
# 切万花筒,再画一笔 -> 8 向对称
adb shell input swipe 540 1700 540 700 300; sleep 0.6
tap_id btnSym 0
sleep 0.5
adb shell input swipe 540 600 540 2100 300; sleep 0.6
adb shell input swipe 540 600 540 2100 300; sleep 0.6
adb shell input swipe 400 650 700 900 300; sleep 0.5
snap sym_kaleido
tap_id btnSym 0
sleep 0.4

# ---------- v2.40 新功能走查:豆子规格 / 按板引导 / 描摹行(全部 soft) ----------
# 迷你豆 2.6mm:切换后看板提示与摘要是否跟随(截图人眼审)
tap_id chipBeadMini 0
sleep 0.8
snap bead_mini
tap_id chipBeadStd 0
sleep 0.5
# 切 58×58(4 块板),开拼豆辅助 + 按板引导
tap_id chip58 0
sleep 1.5
# 拼豆模式卡在页面更下方,先手动滚两屏再确认卡名可见(tap_id 半屏点击会打空)
adb shell input swipe 540 1700 540 500 300; sleep 0.8
adb shell input swipe 540 1700 540 500 300; sleep 0.8
check_text "Bead-along" 0
tap_id swBeadAssist 0
sleep 1.5
check_text "Find undone" 0
tap_id btnAssistBoard 0
sleep 1
tap_id btnAssistNextBoard 0
sleep 0.8
# 顶部网格不随设置区滚动,直接截:应看到第 2 块板外墨框 + 板进度行
snap assist_board
# v2.44:逐行引导(板模式直接切行模式,下一行,截图带面板)
tap_id btnAssistRow 0
sleep 0.8
tap_id btnAssistNextBoard 0
sleep 0.5
snap assist_row
tap_id btnAssistRow 0
sleep 0.5
tap_id swBeadAssist 0
sleep 1
sleep 1
# 描摹行存在性(不真选图,避免文件选择器挂住流程)
adb shell input swipe 540 1500 540 900 300; sleep 0.6
tap_id btnTraceToggle 0
# 滚回页顶,别影响后续 tabList 等步骤
adb shell input swipe 540 600 540 2100 300; sleep 0.6
adb shell input swipe 540 600 540 2100 300; sleep 0.6

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

# ---------- v2.44:3D 效果预览(预览页右上角 chip) ----------
tap_id chip3d 0
sleep 1.5
snap effect3d
tap_id chip3d 0
sleep 0.5
back
ensure_home

# ---------- v2.43:线稿模式(文字位图切风格,出黑豆描线图纸) ----------
tap_id btnText 0
sleep 1.5
check_text "Text to bead pattern" 0
adb shell input tap 540 960
sleep 0.8
adb shell input text "HI"
sleep 0.5
tap_text "Generate" 0
sleep 8
tap_id chipStyleLine 0
sleep 3
adb shell input swipe 540 600 540 2100 300; sleep 0.6
adb shell input swipe 540 600 540 2100 300; sleep 0.6
snap lineart
back
ensure_home

# ---------- 我的项目(含完整流程存档) ----------
tap_id btnProjects 0
sleep 1.5
snap projects
back
sleep 1
ensure_home

# ---------- v2.42:拍照对色入口(空态截图,不真选图) ----------
tap_id btnInventoryHome 0
sleep 1.5
tap_id btnInvMatch 0
sleep 1.5
snap color_match
back
sleep 0.8
back
sleep 0.8
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
