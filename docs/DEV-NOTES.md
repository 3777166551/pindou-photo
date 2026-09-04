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
