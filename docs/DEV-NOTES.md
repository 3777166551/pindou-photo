# 开发踩坑记录 (DEV NOTES)

> 本页记录开发/构建/CI 过程中真实踩过的坑，按"现象 → 原因 → 修法"组织。
> 后来者遇到同类问题可以直接对号入座。最后更新：2026-09-16

## 1. Git 忽略规则误伤了真实资源文件（最隐蔽的一个）

**现象**：仓库推到 GitHub 后，任何人 clone 下来都编译不过——`EditorActivity` 里
上百个 `R.id.*` / `R.layout.activity_editor` 符号找不到；CI 上 R 类生成为空。
但本地 `build_apk.bat` 一直正常。

**原因**：根目录曾有一个同名杂散文件 `activity_editor.xml`（调试残留），
`.gitignore` 里写了**不带路径锚点的裸名规则** `activity_editor.xml`——
gitignore 的裸名模式会匹配**任意目录层级**，把真正的
`app/src/main/res/layout/activity_editor.xml` 也忽略了。它从未进过仓库。

**修法**：
- 根目录残留文件的忽略规则全部加 `/` 锚点：`/activity_editor.xml`、`/classes.dex` 等
- `git add -f` 补交真布局
- 排查同类问题的命令：
  `git ls-files --others --ignored --exclude-standard app/src`
  （列出被 ignore 规则挡在仓库外的源码树文件）

**教训**：写 .gitignore 时，凡是要排除"根目录某个具体文件"，一律加 `/` 前缀。

## 2. AGP 7.4 + compileSdk 34：R 类生成为空

**现象**：Gradle 构建时 `processDebugResources` 显示成功，
但 `compileDebugJavaWithJavac` 报海量 `cannot find symbol`，
符号明细全是 `location: class id / class layout`——R 类存在但一个字段都没有。

**原因**：AGP 官方要求 compileSdk 34 搭配 **AGP 8.1.1+**；
AGP 7.4 的资源管线对 API 34 的资源格式处理有缺陷，产出了空 R。

**修法**：AGP 升级到 8.1.4 + Gradle 8.2，同时把 AndroidManifest 的
`package="..."` 属性移除（AGP 8 用 build.gradle 的 `namespace`）。
注意：本工程的**原生构建（build_apk.bat，裸 aapt2）仍需要 Manifest 里的
package 属性**，两者可以共存（AGP 8 下保留该属性只产生告警）。

## 3. res 目录里混入非 XML 文件：Gradle 直接拒绝

**现象**：CI Gradle 构建报
`check_colors.txt: The file name must end with .xml`（mergeDebugResources 失败）。

**原因**：某次调试把 findstr 的输出文件存进了 `res/layout/`。裸 aapt2 的
`compile --dir` 会静默忽略非 XML 文件（所以本地一直没发现），Gradle 的
资源合并器则严格拒绝。

**修法**：删除残留文件。**教训**：这正是"CI 用 Gradle、本地用裸 aapt2"
双构建体系的价值——两条路径会互相暴露对方容忍的问题。

## 4. Gradle 构建缺 ONNX Runtime 依赖

**现象**：CI 编译报 `package ai.onnxruntime does not exist`（MlSegmenter 等）。

**原因**：本地原生构建用的是 `tools/ort_aar/classes.jar`（gitignored 的本地物），
而 Gradle 构建的 `dependencies {}` 是空的。

**修法**：`app/build.gradle` 加
`implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.1'`（MIT）。
本地 bat 构建保持 ort_aar 不变，两条路径等价。

## 5. CI 上安卓模拟器的四个连环坑

按踩到的顺序：

1. **`sdkmanager: command not found`**：runner 的 PATH 不含 SDK 命令行工具。
   每个相关 step 显式
   `export ANDROID_HOME=/usr/local/lib/android/sdk` 和
   `export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"`。
2. **`Unknown AVD name [smoke]`**：avdmanager 在 A 步骤创建的 AVD，
   B 步骤的模拟器找不到——GitHub Actions 每个 step 是独立 shell，
   AVD 家目录环境变量不一致。修法：job 级固定
   `ANDROID_AVD_HOME: ${{ github.workspace }}/.android-avd` 并在创建前 mkdir。
3. **`ProbeKVM: This user doesn't have permissions to use KVM`**：
   /dev/kvm 存在但 runner 用户不在 kvm 组。修法：启动前
   `sudo chmod 666 /dev/kvm`（KVM 可用时硬件加速，启动 1~2 分钟；
   无 KVM 软模拟会非常慢甚至超时）。
4. **冒烟步骤 `adb: command not found`**：同坑 1，冒烟 step 也要导出 PATH。

另外：正在运行的 job 拿不到实时日志（对象存储 404），只能等结束后下载；
`emulator boot timeout` 时模拟器八成是秒退了（看 ERROR 行），不是真的慢。

## 6. GitHub Token 权限的两个硬规则

- 推送任何触碰 `.github/workflows/*` 的提交，细粒度 PAT 必须单独勾选
  **Workflows: Read and write**（勾了 Contents 也没用，一律拒绝）。
- 需要的权限按需勾选：Contents(读写) + Workflows(读写) + Pull request(读)
  基本够用；**用完即删**。

## 7. Java 数组初始化器不能混装类型

`double[][][] x = { {{...},{...},2.0425}, ... }`——前两个是 `double[]`、
第三个是裸 `double`，javac 直接报 `incompatible types`。写参考数据
（向量 + 标量成对）时拆成两个平行数组。

## 8. 模型与许可证（合规向）

- AnimeGANv3 模型是**自定义非商业许可**：F-Droid 会判为非自由资产
  （要上 F-Droid 需出 Lite 构建剔除它）；Google Play / 酷安 / 国内商店无碍。
- U²-Net(u2netp, Apache-2.0) 与 ONNX Runtime(MIT) 无此类限制。
- 详见 THIRD_PARTY.md 与本文档的"发布渠道计划"章节（完整文档开头）。

## 9. 已知的平台限制（未修，有意保留）

- 本地 bat 构建只打包 `arm64-v8a` 的 ONNX so：**32 位老手机会闪退**。
  需要支持则从 Gradle 依赖的 AAR 里补拷 `armeabi-v7a`（ort_aar 目前只有 arm64）。
- 上架国内商店需软件著作权登记；Google Play 需隐私政策 URL
  （可用 GitHub Pages 承载 DISCLAIMER）。

## 10. bat 批处理文件必须保持纯 ASCII（v2.34 教训）

**现象**：`qa\run_tests.bat` 里写了 UTF-8 中文注释，cmd 按 ANSI（本机 GBK）
逐行解析 bat，中文直接碎成乱码并被当成命令执行，报
`'曞…Windows' 不是内部或外部命令`，但脚本居然还继续往下跑（rem 行的乱码
把 rem 吞了），极难排查。

**修法**：仓库里所有 bat（build_apk.bat / compile_check.bat / qa\run_tests.bat）
注释只用英文 ASCII。UTF-8 中文注释请放进 sh 或 md 文件。

## 11. 别用 PowerShell Set-Content 改 Java 源码（v2.34 教训）

**现象**：`-Encoding UTF8` 在 Windows PowerShell 5 里默认带 BOM 写出，
javac 报 `illegal character: '\ufeff'`。

**修法**：改源码一律用编辑器/工具的精确替换；若已混入 BOM，用
`[System.IO.File]::WriteAllText($p,$t,(New-Object System.Text.UTF8Encoding($false)))`
剥掉。文件头三个字节是 `EF BB BF` 即为带 BOM。

## 12. 色板体系的扩展点备忘（v2.34 起的形状）

- 运行时自定义色板槽位：`BeadBrandCharts.customs`（List，可多套），
  增删改只能走 `CustomPalettes`（带写盘 + `revision()` 自增 + 色板名缓存重置）。
- 选择器下标：0~3 通用档，4~7 品牌表，`BeadPalettes.customSlotStart()` 起为
  自定义；`getPalette` 对越界自动收拢到最后一套。
- EditorActivity 在 `onResume` 对比 `CustomPalettes.revision()` 决定是否重建
  色板下拉框；改过自定义色板内容才会清修格并重新生成。
- 色板下拉框监听器加了"位置没变就跳过"守卫——`setAdapter` 会异步触发
  onItemSelected，重入会把用户手动修格/空白画布涂色全清掉。

## 13. .NET 正则 `[^"]` 会跨行（v2.35 教训，险些大面积毁码）

**现象**：批量替换 Java 字符串字面量用了 `"[^"]*锚点[^"]*"`。在 .NET 里
**否定字符类默认匹配换行符**（只有 `.` 不匹配），结果一个锚点从某行的一个引号
一路吞到几行后的下一个引号，中间整段代码被替换成了 `getString(...)`，
把赋值语句拼成了无法编译的怪物，而且坏得很隐蔽（恰好在注释/引号密集区）。

**修法**：凡是想限定"行内"的匹配，字符类必须显式排除回车换行：
`"[^\r\n"]*锚点[^\r\n"]*"`。批量替换脚本（tools/i18n_apply.ps1）已按此写法,
并对每条映射报告 MISS,替换前先 `git status` 干净、出问题可 `git checkout` 回滚。

**教训**：大规模机械替换前,先用一小段样例验证正则的"可跨越范围",
并确保工作区干净可以整体回滚——这次靠 git checkout -- 三个文件救回来。

## 14. i18n 的形状(v2.35 起)

- `values/`（中文默认）+ `values-en` + `values-ja`：strings.xml + arrays.xml
  （tier/brick/denoise/week/generic_color_names 120 色）+ knowledge.xml。
- 纯 Java 数据类（BeadPalettes/BeadColor）**不能引用 R 类**（qa 在桌面 JVM
  编译会炸"程序包 R 不存在"）,本地化文本经 `util/L10n.apply(context)`
  在 Activity onCreate 套到静态字段上;不调用时保持中文默认,qa 不受影响。
- CI 模拟器是英文环境,qa/ui_smoke.sh 的文本锚点全部用英文串,
  顺带把英文翻译也端到端验了。新增 UI 时记得三语一起补键。

## 15. Java2D 径向渐变的"透明黑"灰边(v2.39 教训)

**现象**:GenCandyHero 用 RadialGradientPaint 生成柔光斑,边缘发灰发暗,
整张英雄卡像蒙了层土。

**原因**:渐变终点色写成 `new Color(0, true)`(纯透明黑)。插值发生在
**非预乘 RGBA 空间**,alpha 衰减过程中 RGB 一路向 (0,0,0) 靠,光斑边缘
等于叠了层半透明黑。

**修法**:终点色 = 同 RGB + alpha 0:
`new Color((rgb>>16)&0xFF,(rgb>>8)&0xFF,rgb&0xFF,0)`。
GenPastelBg 老代码没踩坑是因为它用 hasAlpha(rgb,0f) 保住了 RGB。

## 16. 贴纸风 UI 资产的再生成入口(v2.39 起)

- `tools\gen_candy_assets.bat`:重生成 tile_pegboard.png(拼板孔点阵底)
  + bg_hero_pastel.png(英雄卡)。改糖果色先改 GenPegboardTile/GenCandyHero
  的色值再跑 bat(Bash 里直接 javac .java 会被 Mimosa 钩子拦)。
- 框选视觉集中在 `view/SelectionPainter.java`(墨衬底+黄油角标+三分线),
  CropView 与去水印框共用;全 APP 框选改色只动这一个文件。
- 弹窗壳走 `CandyAlertDialog` 主题(bg_dialog.xml),新增 AlertDialog 零成本;
  但自定义 View 的弹窗(setView)不吃壳的内边距,内容自己留边。
- 公共类文件名必须与类名一致(SelectionPainter 差点以 SelectionStyle.java
  入库,javac 直接拒);PowerShell -replace 批量改色后用
  `[IO.File]::WriteAllText(路径, 内容, UTF8Encoding($false))` 落盘防 BOM。

## 17. 按钮 drawable 别用 `<ripple>` 壳(v2.39 教训,CI 截图验收发现)

layer-list 里包 `<ripple><shape>…</shape></ripple>` 的按钮背景
(bg_btn_primary/bg_btn_secondary),在 API 30 x86_64 模拟器(swiftshader)
上整层内容(糖果粉渐变+墨描边)渲染成磨砂半透明灰,像旧玻璃拟态;
同结构的纯 shape 版本(bg_card/bg_tab_track/bg_chip)全部正常。
修复=去掉 ripple 壳;按压反馈由 Anim.pressScale 缩放承担,无损失。
静态审代码看不出来,是 CI 云端模拟器截图验收(见 ROADMAP 四)抓出来的——
UI 改动必须过一遍 CI 截图,别只跑 qa。

## 18. UI 测试走"推 git → CI 云端模拟器"(2026-09-07 定型)

本机不可行路线:x86_64 镜像要 hypervisor(AEHD/WHPX 需管理员+重启);
模拟器 37.x 直接拒绝 x86 主机跑 arm64 镜像。标准流程、拉截图产物步骤、
en 环境与 emoji 字体的坑,见 ROADMAP.md「四」的 ★ 条目,不赘述。

## 19. 生成脚本必须"拥有"生成文件的全部内容(v2.44 教训)

**现象**:`tools\gen_charts.ps1` 重新生成 BeadBrandCharts.java(加 Nabbi)
后,整包编译炸 29 个"找不到符号"——customCount/customAt/make 等
自定义色板方法全没了。

**原因**:这些方法是 v2.33 之后**手写进生成文件的**,生成脚本里没有;
重生成等于整文件覆盖,手写部分静默蒸发。此前每次加品牌必然踩。

**修法**:把 customs 成员组补进 gen_charts.ps1 的输出(生成文件里
手写成员清零,全部归生成器所有),重跑一次生成即恢复。
**教训**:凡是"脚本生成 + 后期手改"的文件,要么把改动搬回脚本,
要么在脚本头写警告——二选一,不能放着不管。

## 20. 自定义 View 手势别依赖 GestureDetector 的默认接力(v2.44 教训)

真机反馈取景裁剪选框拖不动/缩放不了(CI 模拟器无此问题,纯 JVM 读码
也复现不了)。修法是把 CropView 的拖动从 GestureDetector.onScroll 改为
ACTION_MOVE 里自己算 `x-lastX`(双指缩放保留 ScaleGestureDetector,
双击复位手写判定),并补 `requestDisallowInterceptTouchEvent`——
消除对检测器内部接力时序和父容器拦截策略的依赖。
**教训**:全屏触摸画布类自定义 View,手势自己算最稳;检测器适合
标准列表/卡片场景。改完务必真机过一遍(CI 模拟器测不出这类机型差异)。

## 21. 样式里的 0dp+weight 别带进纵向列(v2.45 教训,CI 完整流程冒烟抓出)

**现象**:首页「我的项目」卡(btnProjects)在任何设备上都**看不见、点不到**,
a11y 树里也没这个节点;「Keep going」标题和页脚之间留一大段空白。
v2.39 糖果贴纸风改版引入,靠人肉点查永远发现不了(看不见的东西没法点),
直到 qa/ui_smoke.sh 扩展完整拼豆流程(存档→首页→我的项目→重开)才在
第六轮 CI 咬住:FAIL: id not found: btnProjects。

**原因**:btnProjects 直接用了 `@style/ToolCard`——该样式是给 2 列网格
半宽卡设计的:`layout_width=0dp + layout_weight=1`。它又是纵向列的
直接子 View,还覆写 orientation=horizontal:纵向 LinearLayout 里
weight 分配的是**剩余高度**,于是这张卡 = 宽度 0dp(什么都不画)
+ 高度吃光整列剩余空间(空白段) + 零尺寸视图不进 a11y 树
(自动化找不到)。其他卡片都包在横排 wrapper 行里所以没事。

**修法**:btnProjects 显式声明 `layout_width=match_parent +
layout_height=wrap_content`(XML 属性优先于 style),渲染为
全宽横排行卡(图标在左文案在右,本来就是设计意图)。
**教训**:复用带 0dp/weight 的样式时先问一句"父容器方向对不对";
UI 冒烟别只走查"能看见的东西",要覆盖跨页面的状态链路(存档→回
首页→再进),隐形入口只有靠链路断点才现形。
## 22. 重新生成图纸后,先清完成度标记再刷豆单(v2.45 教训,CI 模糊测试抓出)

**现象**:monkey fuzz(种子 424242)稳定砸出
`ArrayIndexOutOfBoundsException: length=3364; index=7329`,
崩在 countDonePerColor → BeadPattern.cellAt。真实用户路径:拼豆辅助
模式下点格标记(如 116×116 图,index 可达 13455)→ 切换板子尺寸
重新生成 → 新图只有 3364 格 → 豆单刷新统计时拿旧索引访问新图纸。

**原因**:regenerate() 的 UI 回调里,`beadDone.clear()`(注释明确写着
"重新生成后格子变了,完成度标记失效,清空重来")被放在
`adapter.notifyDataSetChanged()` **之后**——刷新时遍历的还是没清的
旧集合。顺序错误;clear 本身一直都在,只是晚了三行。

**修法**:①clear/rollBeadDay 提前到 notifyDataSetChanged 之前;
②countDonePerColor 加 `k < 0 || k >= cols*rows` 防御过滤
(loadProject 读取路径本就有同款防护,4040 行)。
**教训**:①"清状态"和"触发重算"的先后顺序,重算链路越长越容易在
中间读到脏数据,改 UI 回调时先看依赖;②monkey fuzz 对这类"状态残留
+ 参数切换"崩溃是原子弹级的,人类手测很难凑齐时序。
## 23. 自定义 View 进 XML 必须有 (Context,AttributeSet) 构造器;资源 ID 现场取证靠 logcat(v2.49 教训,CI 专项冒烟抓出)

**现象一**:ar-smoke 两轮都在点「AR 试摆」chip 的瞬间整进程死亡回首页,
主界面无任何异常。logcat 抓栈才现形:`InflateException →
NoSuchMethodException: FakeArView.<init>[Context, AttributeSet]`——
XML 里写了 `<com.pindou.app.view.FakeArView>`,类里却只提供单参构造器,
setContentView 当场炸。

**修法**:补二参构造器。后又出现 findViewById 找不到自定义视图的诡异
NPE,直接弃用该页 XML,改纯代码构建 UI(与对话框/CropView 同款写法),
从根上消灭这一类问题。

**教训**:①自定义 View 一进布局文件,四个构造器约定就是硬约束,编译期
不报错、运行期必炸;②CI 冒烟失败时第一件事抓 `adb logcat -d` 的
FATAL 栈(已固化进 ar_smoke.sh 的 die()),别靠截图猜。
## 24. 无相机设备上 ACTION_IMAGE_CAPTURE 会抛 SecurityException(fuzz 抓出)

**现象**:monkey(种子 90210)砸中首页「Take a photo」,
`SecurityException: Permission Denial: starting Intent
{act=android.media.action.IMAGE_CAPTURE...}` 直接崩进程。
takePhoto 只接了 ActivityNotFoundException。

**修法**:再补 catch SecurityException → 同款 toast。无摄像头模拟器/
无相机应用的设备(含部分电视盒)走到这里不再崩。

## 25. 双击检测的延迟任务不能吞掉不同格的待定标记(v2.49 教训,CI 冒烟抓出)

**现象**:庆祝流程 CI 步骤(8×8 圆板辅助模式逐格点 64 个格心)在 CI 上
从未到过 100%,基线(0200077)与后续所有提交同样失败;本地人工测却一直
好的。logcat 无异常、进度文字也不报错,就是标记数上不去。

**原因**:拼豆辅助的单击走"抬起后 postDelayed 260ms 再落账"的流程
(为了和双击复位缩放区分)。而下一次点按的 ACTION_UP 会**无条件
removeCallbacks 掉上一个还没执行的待定标记**——`adb input tap` 每格
间隔 ~250ms < 260ms,于是每次快速点下一格都把上一格的标记吞掉,
64 格点完只登记最后一格。人手点格间隔普遍 > 260ms 所以从未复现;
快速连点的真实用户同样会踩中。

**修法**:只有与上一击同格(±24dp)才可能构成双击——不同格时把上一个
待定标记**立即执行落账**,再挂本次的延迟任务。双击复位语义不变,
快速连点每格必生效。
**教训**:①"延迟确认 + 抬手取消"的模式要问一句:取消的到底是什么?
跨手势的取消必须限定在同手势语义内;②"本地测过"对时序类 bug 是零
证据——CI 上 adb 点按的节奏和人手完全不同,这类 bug 只有 CI 抓得到。

## 26. regenerate 无条件清空拼豆进度(v2.50 审计抓出,严重级)

**现象**:重开项目存档、或调亮度/对比度/色板/品牌等任何触发重新生成的
参数,拼豆辅助里标好的完成度全部归零。用户标了几十格的大图,存档第二天
打开就是空白板。

**原因**:loadProject 先从存档恢复 beadDone,随后照例调 regenerate();
而 regenerate 的 UI 回调里 beadDone.clear() 无条件执行(这是 monkey fuzz
越界崩溃修复留下的"防御"),恢复的标记被自己清掉。

**修法**:网格尺寸没变(仅调色/风格/品牌等)时保留 beadDone,只剔除
变空格/变板外的格子;尺寸变了(索引全变)才清空。清空仍必须在豆单刷新
之前(DEV-NOTES 22 的约束不变)。CI 补持久化回归用例:8x8 标到 100% ->
存档 -> 重开 -> 断言 100% 仍在。

**教训**:1) "防御性清空"要区分"索引失效"和"用户数据"——前者该清,
后者该尽力保留;2) 修复 A 崩溃时加的清空逻辑,就是 B 数据丢失的原因,
修 bug 前先问这行清理 protect 的是什么;3) 涉及用户进度的路径必须有
回归用例盯着,否则每次重构都在静默丢数据。

## 27. cmd 管道里 %ERRORLEVEL% 是"旧值",本地编译通过可能是假象(v2.54 教训,CI 抓出)

**现象**:VerifyActivity 导错包名(util.AppFileProvider 应为 provider.),本地
`cmd /c compile_check.bat 2>&1 | findstr "error" & echo COMPILE:%ERRORLEVEL%`
显示 COMPILE:0"通过",推到 CI Gradle 才炸 cannot find symbol。

**原因**:①cmd 在**整行解析时**就展开 %ERRORLEVEL%,拿到的是本行执行前的
旧值,跟编译结果无关;②findstr 过滤只看关键字,真正该看的是 bat 自己打的
`COMPILE OK`/`[COMPILE CHECK FAILED]` 文本。

**修法**:判断编译结果一律看输出文本(compile_check.bat 尾部会打 COMPILE OK);
要拿真实退出码用 `call` 或分行执行。**教训**:本地绿灯不可信时,CI 是唯一
真相——这也再次体现"CI 用 Gradle、本地用裸 aapt2"双构建互相暴露问题的价值。

## 28. 裸 aapt2 管线不支持 lambda(v2.53 埋雷,v2.54 本地编译才炸)

**现象**:EditorActivity 里 TTS 初始化用了 `status -> {...}` lambda,CI Gradle
(JDK17)编译通过,本地 compile_check.bat 报"找不到方法 metafactory"。

**原因**:bat 管线 `-source 1.8 -bootclasspath android.jar;core-lambda-stubs`
组合对部分 android.jar 接口的 lambda 转译失败;项目代码历来全用匿名内部类
正是这个原因(此前没写成文档,新代码踩雷)。

**修法**:全项目约定**不写 lambda**,一律匿名内部类。教训:项目级的隐性
编码约定必须写进文档,否则下一个写代码的人(包括 AI)必踩。

## 29. smoke 脚本 tap_text 模糊匹配会点到"提示语"(v2.54 教训)

**现象**:验收页冒烟 `tap_text "Compare"` 报告"tapped",但比对从未执行——
页面提示语 "…then Compare" 里也含 Compare,模糊匹配正则命中**先出现的
提示 TextView**(不可点),按钮根本没按。

**修法**:可点控件用 `tap_text_exact`(全文相等);提示语与按钮同词时,
写提示语文案刻意避开按钮词,或按钮改 resource-id。教训:文本定位的模糊
匹配永远可能命中"提到这个词的地方",不只是按钮。

## 30. 梯子(系统代理)的正确用法:curl/git 都要显式指定(v2.54 实测)

**现象**:用户开了梯子后 curl 直连 commons.wikimedia.org 仍 Connection
aborted;github push 也依旧抽风。

**原因**:梯子是**系统代理模式**(注册表 ProxyServer=127.0.0.1:7890,
Clash 系),浏览器/系统流量走它,**curl/git 不自动走**。

**修法**:①curl 加 `-x http://127.0.0.1:7890`;②git 加
`-c http.proxy=http://127.0.0.1:7890 push/fetch`,走代理后一次就通;
③梯子关闭时 api.github.com 直连仍通,REST API 降级推送照旧
(tools/api_push_fallback.ps1 已参数化:`-FilesCsv 'a; b; c' -Msg '...'`,
注意 cmd 给 ps1 传数组参数会变成字面量字符串,用分号分隔字符串在脚本内
split)。④batch 文件里 URL 的 %XX 百分号编码必须写 %%(cmd 会把 %XX%
当变量吞掉,导致下载 404)——或干脆用 PowerShell 读列表逐条 curl。

## 31. strings.xml 里多个 %.0f 占位符过不了 aapt2(v2.55 教训)

**现象**:六边形板的图纸标注文案写成 `宽约 %.0f × 高 %.0f cm`
(两个非位置占位符),本地 qa(桌面 JVM)全绿,compile_check.bat 的
aapt2 环节报 `multiple substitutions specified in non-positional format`,
三份 strings.xml 全炸,编译直接失败。

**原因**:aapt2 要求一条字符串里有**多个**格式占位符时必须带位置序号
(`%1$.0f`),单个时才允许 `%.0f`;桌面 JVM 测试不碰资源,这类错只有
aapt2/Gradle 能抓。

**修法**:多占位符一律写 `%1$…`、`%2$…`(fmt_sheet_hex 已改);
`String.format` 侧传参不用变。**教训**:新增多参数文案后,先跑
compile_check.bat 再说 qa 全绿——qa 编不过资源,两边覆盖面不同。


## 32. Android 15/16 拦 shell am start 非导出 Activity（v2.58 真机实测）

**现象**：真机（vivo V2536A，Android 16）上 `adb shell am start -n
com.pindou.app/.MainActivity` 抛 `SecurityException: Permission Denial
... not exported from uid 10449`——MainActivity/EditorActivity 等全部
exported=false，shell（uid 2000）不再有 START_ANY_ACTIVITY 特权。
同一次 walk 里部分非导出 am start 又成功过（时机相关），表现为
"截图张冠李戴/慢一拍"。

**影响**：real_walk.bat 的非导出页步骤在 Android 15+ 真机上半数失效；
CI 模拟器（API 30）不受影响。

**修法/对策**：真机驱动改纯 UI 点击——monkey 拉 LAUNCHER 进首页
（`adb shell monkey -p com.pindou.app -c
android.intent.category.LAUNCHER 1`）+ `uiautomator dump` 解析
bounds 后 `input tap`。qa/out/uinfo.ps1 是现成的 dump 解析器
（列 text/resource-id/clickable/center）。待办：walk 脚本正式改造
（ROADMAP 六-3）。

## 33. OriginOS 无线调试：锁屏杀连接 + 端口轮换（v2.58 真机实测）

**现象**：vivo（OriginOS 6/Android 16）无线调试可用但很凶：①每次
**锁屏**都会掐断无线 adb 连接；②解锁后**端口轮换**（连接端口每次
不同），旧端口 connect 直接 Connection refused；③灭屏 2 分钟（默认
screen_off_timeout）就会锁屏触发①。

**修法/对策**（实战验证）：
- 连上第一件事:`settings put global stay_on_while_plugged_in 7` +
  `settings put system screen_off_timeout 600000`,屏幕不灭就不锁,
  连接和端口都稳;测完恢复原值。
- 每次重连都要用户报「无线调试」主页**当前**显示的 IP:端口,配对
  (pair,配对弹窗里的端口)与连接(connect,主页端口)是**两个端口**,
  且都轮换;配对关系本身保留,只需重新 connect。
- 多别名:`adb devices` 会同时列 ip:port 和 mdns 名两条,所有命令带
  `-s <ip:port>` 或 `set ANDROID_SERIAL=`,否则 more than one device。
- 心跳保活(每 15s shell echo)实测**救不了锁屏杀**,只对空闲断连
  有点用,别指望它。

## 34. adb 37.x 的 devices 输出是 CRLF,findstr 锚点全灭（v2.58 真机实测）

**现象**：real_walk.bat 的设备检查 `adb devices | findstr /c:"device$"`
在明确有 device 时也匹配失败,走查误报"无设备"。单测式验证:
`adb devices > 文件` 后看字节,行尾是 `\r\n`——`$` 锚在 `\r` 前
永远不成立。旧版 adb 输出 LF 所以老代码曾经能用。

**修法**：不要解析 devices 文本,用状态命令:`adb -s <serial>
get-state`(多设备时必须带 -s);或 `adb devices | more +1 |
findstr "device"`(跳过表头,子串匹配,offline/unauthorized 不含
"device" 字样)。real_walk.bat 已改为 `-s %ANDROID_SERIAL% get-state`。

**教训**：①cmd 管道里 %ERRORLEVEL% 是旧值(DEV-NOTES 27)曾让一次
"验证通过"完全是假象——判断脚本分支要看实际输出文本;②凡是
"上一版能用,现在莫名失败"的文本解析,先 dump 字节看行尾。

## 35. PowerShell 写 .java 会被本机安全钩子改坏:数字 0 集体变 x（v2.59 改版实测）

**现象**：用 `powershell -File xxx.ps1` 里的 `[IO.File]::WriteAllText`
批量替换 .java 里的颜色字面量,写盘后**文件里所有字符 `0` 被替换成
`x`**(`90`→`9x`、`0.5f`→`x.5f`、`0xFF40354E`→`xFF4x3x54E`),15 个
被写文件全部损坏,javac 报 273 个错。开篇说的「Bash 里 .java 写入
命令被拦」的升级版:钩子没拦,而是**放行了写入但改了内容**,比直接
拦截更隐蔽,编译前毫无感知。

**修法**：①`git checkout -- <files>` 回滚后,批量改 .java 一律改用
会话内置的 **Edit/Write 工具**(走 harness 文件 API,不经过 Bash,
钩子不碰,实测无损);②每次批量改 .java 后立刻 `compile_check.bat`
再继续下一步;③校验脚本里用 `git show` 读原文件做内容对比时注意
PowerShell 管道会把 UTF-8 按 GBK 重解码,中文注释必失真——对比逻辑
要么只比 ASCII 字面量,要么走字节级比较,否则全是假 DIFFERS。

## 36. bg_pegboard.xml 名不副实四年:一直引用粉彩渐变大图（v2.59 视觉重制破案）

**现象**：v2.59 两轮视觉重制后 CI 截图里页面背景仍是暖奶油/粉彩
渐变,与新的 surface 底色完全对不上;解包 CI APK 验证 tile_pegboard.png
颜色是新的——问题不在资源,在引用。

**根因**：`drawable/bg_pegboard.xml` 自 v2.25 起 `android:src` 就指向
`@drawable/bg_pastel`(660KB 的柔焦粉彩渐变大图,`gravity=fill`),
名字叫 pegboard 实际从没平铺过点阵;GenPegboardTile 重生成 tile 全是
白做,设计文档里"拼板点阵母题背景"从未真正生效。

**修法**：bg_pegboard.xml 改回 `@drawable/tile_pegboard` +
`tileModeX/Y=repeat`,窗口背景瞬间变成 surface 色点阵。

**教训**：①**改视觉先核对 drawable 引用链**(资源文件本身对不对
之外,引用它的包装 XML 也要查),别只看名字;②"文档说 X"不等于
"代码是 X",四年前某次改版把引用换掉后文档没更新,后来者全被名字骗。

## 37. 清单声明了 CAMERA 却未授权 → ACTION_IMAGE_CAPTURE 直接 SecurityException(v2.62.2 真机反馈)

**现象**：首装后点首页「拍一张照片」立刻报「没有可用的相机应用」;
只要进过 AR 试摆/对位投屏(会弹权限框)就永远复现不了——用户报告
"相机按钮没有权限,点击报错,不知道为什么"。

**原因**:Android 6+ 规则——应用在清单里**声明了** android.permission.CAMERA
却**未获授权**时,任何 ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE
意图直接抛 SecurityException(不是 ActivityNotFoundException)。v2.49
加 AR 时把 CAMERA 写进清单,而运行时申请权限的入口只有 AR/对位两处;
拍照/验收走"系统相机意图零权限"的旧假设从此不成立。SecurityException
被当"无相机设备"兜底(DEV-NOTES 24),文案还误导。

**修法**：MainActivity / VerifyActivity 发相机意图前
checkSelfPermission → 未授权先 requestPermissions,授权回调再发;
拒绝给三语新文案 err_need_camera。

**教训**：①"这个 Intent 不需要权限"的结论有前提——清单没声明;
声明了就必须先拿到再发意图;②给 SecurityException 写兜底时要问
异常的真实主因(fuzz 在无相机的模拟器上跑,永远复现不出真机首装
未授权路径);③同类坑:VerifyActivity 与 MainActivity 同构,修一处
必须 grep 同款意图的全部发送点。


## 38. parseHexColor 返回带 alpha 的负数,调用点"负数=失败"判断永远为真(v2.63 豆仓实测)

**现象**:豆仓「➕ Add color」填好预填的 #E3242B ×100 点 OK,弹
"Invalid color, use #RRGGBB",库存永远空。自定义色板页的 hex 添加
同款死法;hex 实时预览(TextWatcher 里 rgb >= 0 才刷新)也从不生效。

**原因**:契约漂移。`PaletteShare.parseHexColor` 成功时返回
`(v & 0xFFFFFF) | 0xFF000000`(带 alpha,Java 有符号 int 恒为负),
失败返回 -1;而三个调用点全按旧契约"负数 = 失败"判断
(`rgb < 0` 报错 / `rgb >= 0` 刷新)——合法颜色必然被判失败。
附带雷:白色 #FFFFFF 的返回值 0xFFFFFFFF 恰好等于 -1,和失败
哨兵撞值,即使改对判断白色也添不进去。

**修法**:`parseHexColor` 回归裸 RGB 契约(成功 = 0xRRGGBB 恒正,
失败 = -1);调用点零改动即恢复正确;TestPaletteShare/TestCustomPalette
断言同步,新增"白色不撞 -1 哨兵"回归。TestInventory 只测后端
set/get,覆盖不到这条 UI 链——它是模拟器手动实测抓出来的。

**教训**:①跨层契约("成功返回什么")一旦漂移,编译器和多数测试
都抓不到——调用点的判断会跟着旧契约"自洽地错下去";②哨兵值选
-1 时必须检查合法值域是否包含 -1(带 alpha 的颜色里白色就是);
③"后端单测全绿"不等于"功能可用",UI 链路必须端到端点一遍
(本次是模拟器实操豆仓才现形)。

## 39. 简图清晰化:平坦图边界线条色统一(v2.63,用户提议的算法)

**用户痛点**:简单图片(卡通/赛璐璐/扁平插画)导入后边界不清楚、线条
颜色花花绿绿会变色——抗锯齿过渡 + 压缩噪声让同一根描边的像素散布在
多个色桶里,多数票各格各投各的,边界行"开杂色铺"。

**算法**(PatternEngine 投票路径尾部,自动门控,零开关):
1. 逐格纯度 = 格内不透明像素配到胜出豆的占比(主投票循环顺带产出);
2. 门控 isFlatSimple:纯度分布双峰(内部格 ≥0.85 过半、边界格 3%~20%、
   中间带 ≤25%)才触发;欠曝提亮门控激活时跳过;完全无边界格跳过;
3. 边界格(纯度 <0.62)线条色 = 格内"离纯色均值最远的离群像素"
   (深线浅底/浅线深底都适用);
4. 边界格按 4 连通聚成笔画,每笔画以线条色众数(4bit 桶均值)统一配豆。

**踩坑**:①ListView/AbsListView 吃 item margin(38 条同款思路的算法版:
"设了也白设");②门控首版漏了"欠曝提亮激活"与"完全无边界格"两个
反例,TestAlgoGate 门控用例 + TestFlatUnify 渐变用例当场咬住;③
笔画众数桶平票时 HashMap 迭代顺序不定——先按数量取最大(>非≥)保证
首个最大者胜,fixture 需让目标桶计数严格占优。

**教训**:TestInventory 只测后端、TestAlgoGate 用拟合阈值——两者都
覆盖不到"新路径该在何时触发"这类语义断言;新算法必须自带 fixture
级测试(合成图 + 期望输出),并且门控类算法要把反例写成用例。

## 40. 简图色板收敛(v2.63):渐变简图"一支太阳散成十几支黄"的治理

**现象**:AI 渐变卡通(平滑渐变+扁平色块)导入后边界已清晰(条目 39 的
功劳),但颜色发散——太阳的平滑渐变每一级都被配成一支近似豆,实图
58×58 出 17 种豆,黄系就占 10 种外加 3 颗豆的孤儿色,豆单没法照单买。

**算法**:finishVote 简图门控内、flatUnifyCells 之后追加
flatCollapsePalette(少数派吸收):每轮取用量最小的在用豆,并入
CIEDE2000 最近的在用豆;双档阈值——ΔE≤6 肉眼同色无条件并,
ΔE≤13 且占比 <2% 的少数派尾巴才并。异色少数派(蓝星/白点)最近邻
色差远超帽值,永远保留。ΔE00 对称性保证:被封禁的豆作为合并目标也
必超帽,每轮恰从 alive 移除一支,guard=n 内必终止。Options.flatCollapse
(默认开)可关;复杂照片不过简图门控,完全不受影响。

**实图效果**(太阳图 58×58,漫德 2.6mm 221 色):17 种→5 种
(灰底 1374 / 米黄光晕 1271 / 主黄 484 / 浅黄高光 224 / 橙暗边 11),
太阳内部斑驳消失,橙暗边与星星形状保留。对比图 screenshots/24_色板
收敛_太阳_收敛前_17种豆.png / 收敛后_5种豆.png。

**坑**:①循环终止标志把"封禁"当"无进展"——首个异色豆(蓝)被封禁
后 while 直接退出,一次合并都没发生(off/on 输出逐字相同暴露了它);
修法:封禁与合并都是从 alive 移除,循环按"每轮消一支"重写。②
BeadPattern.cellAt 返回的是色板下标而非 usedColors 下标,按
UsedColor.index 建 map 再渲染(旧测试 countReds 断言靠排序巧合通过,
别模仿)。③fixture 门控:边界环 3红1白纯度 0.75 落中间带不触发门控,
要 2红2白(0.5)才算边界格——门控阈值与 fixture 的换算要先算再写。

## 41. 成品照片转图纸(v2.64):豆格检测 + 逐豆采样

**需求**:店里/网上看到拼好的成品,拍一张照直接还原图纸(色号+豆单)。
与普通照片转图纸本质不同:成品照片里豆格已存在,普通重采样会把豆
格再切一遍 → 网格纹/串色。正确做法 = 检测晶格,一颗物理豆采一色。

**引擎**(PatternEngine,纯 JVM 可测):
- detectBeadGrid:降采样≤480 → 梯度幅值 → 横/纵自相关找豆距
  ("最小强峰"= 基频,避开 2p/3p 倍频;峰/均值 <1.12 判非晶格返回
  null),相位 = 一个豆距内边缘投影和最大的平移,抛物线插值细化。
- fromBeadPhoto:按网格线+豆距逐格采样"豆环"(外径 0.38 豆距,避开
  中心孔 0.16),剔除高光过曝(亮度>236 且极差<28)后逐通道取中位数
  (抗噪),CIEDE2000 就近配豆;finishPattern 收尾(圆板/六角板直通)。
- 相位归一必须到 [-豆距/2,+豆距/2):归到 [0,豆距) 时"最强线在第 k
  条"会让 cells 整体错位一格,首列丢失(检测网格还原率 13.7% 的根因)。

**界面**(BeadPhotoActivity,全程序化 UI):LatticeView 照片+网格叠加,
单指拖动平移原点;自动对格后台线程;± 步进改豆距(±6%);色板下拉
默认漫德 2.6mm;生成 → PatternShare.build → 项目 JSON(settings+
share)→ EditorActivity.pendingProjectJson 静态传递 —— 编辑器零改动,
色号/豆单/PDF/立牌/拼豆进度全部既有链路复用。主页新增 ToolCardWide
"成品转图纸"(ic_camera + bg_icon_mint)。

**坑**:①TextView.setBackgroundResource(颜色常量) = Resources
NotFoundException 当场崩(要 setBackgroundColor),模拟器一击即中;
②adb root 推到 /sdcard 的文件属主 root,APP 连自己的
Android/data/files 都读不了(EACCES,SELinux storage_file 拒绝)——
测试图要走 `cat > /data/data/<pkg>/cache/` + chown 到 app uid;
③该镜像 documentsui 选择器的"近期的图片"条目点击无响应(仅预览角标
clickable),BeadPhotoActivity 留了 `--es bp_uri <uri>` 直载通道给
模拟器 QA 用(exported=false,仅 root 可达,无安全面);④
BeadPattern.cellAt 返回色板下标不是 usedColors 下标(条目 40 已记,
本次渲染再次踩到)。

**测试**:TestBeadPhoto 13 项 —— 合成豆照(圆豆+中心孔+板底+光照
渐变+噪声)上验检测豆距±1.5/相位贴边≤2.5、真实网格还原≥96%、
检测网格还原≥88%、噪声图不触发、非法参数返 null。qa 27 套全绿。
