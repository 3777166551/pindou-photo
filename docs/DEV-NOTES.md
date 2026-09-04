# 开发踩坑记录 (DEV NOTES)

> 本页记录开发/构建/CI 过程中真实踩过的坑，按"现象 → 原因 → 修法"组织。
> 后来者遇到同类问题可以直接对号入座。最后更新：2026-09-04

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
