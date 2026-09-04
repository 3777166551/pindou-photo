# 路线图与交接文档 (ROADMAP & HANDOFF)

> 本文档是项目的**持续交接入口**：当前状态、待办功能、开发约定、操作备忘。
> 新会话/新开发者从这里开始读。最后更新：2026-09-04（v2.34 代码完成，待发布）

## 一、当前状态快照（2026-09-04）

- 主干 = **v2.34 待发布**：自定义色板完整编辑器已实现（见下），
  签名 APK 待用户用 build_apk.bat 构建（口令走 PINDOU_KS_PASS）
- **CI 全绿**：每次 push 自动跑 qa 测试套件 + Gradle 编译 + 云端模拟器冒烟
  （安装 APK → 启动 → 空白画布手绘 → 崩溃检查，截图存为构建产物）
- **测试**：`qa/` 四套 66 项断言全绿
  （TestColorMath 17 / TestPatternEngine 8 / TestPatternPatch 13 / TestCustomPalette 28），
  入口 `qa/run_tests.sh`（CI/Linux）或 `qa/run_tests.bat`（Windows 本地）；
  纯编译检查用根目录 `compile_check.bat`（aapt2+javac，不动 build_apk 产物）
- **合规链**：AGPL-3.0(LICENSE) + THIRD_PARTY.md(全部第三方声明)
  + DISCLAIMER.md + docs/DEV-NOTES.md(踩坑记录) + docs/SHARE-FORMAT.md(开放图纸格式)
- **权限底账**：全 APP 唯一权限 WRITE_EXTERNAL_STORAGE(maxSdkVersion=28)，
  Android 10+ 零权限、零网络

## 二、功能路线图（按优先级）

### 1. ✅ 自定义色板完整编辑器（v2.34 已完成）

「我的色板」管理页（PaletteActivity，编辑器豆豆清单页入口）：多套自定义色板
增删改、单色编辑（RGB 滑杆/十六进制取色器 + 名称/色号）、按色相排序、
导出/导入（开放色板格式 `pindou-palette`，兼容取 pindou-pattern 的颜色表）、
豆仓一键重建"我的豆板"。数据存 `files/custom_palettes.json`（CustomPalettes），
运行时槽位在 BeadBrandCharts.customs（可多套），EditorActivity 靠
`CustomPalettes.revision()` 在 onResume 自动刷新色板下拉框。

### 2. 多语言（英/日）—— Google Play 出海的最大杠杆

工程量：把所有中文串抽到 `res/values/strings.xml`，再出 `values-en`/`values-ja`。
注意：代码里有少量硬编码中文 Toast/文案（EditorActivity 等），需一并抽取；
docs/完整文档.md、README 双语化另算。

### 3. 全 ABI 打包或声明（上架前必须决策）

现状：本地 bat 构建只拷 `arm64-v8a` 的 ONNX so（tools/ort_aar 只有 arm64），
**32 位老手机会闪退**。二选一：
- ort_aar 补 `armeabi-v7a`（从 Gradle 依赖的 AAR 里解包）
- 商店声明"仅支持 64 位设备"（最省事，2019 年后设备几乎全 64 位）
Gradle/CI 构建的 debug APK 自动含全 ABI，不受影响。

### 4. 隐私政策网页（Google Play 强制）

用 GitHub Pages 承载一个页面（内容取自 DISCLAIMER.md + 数据安全说明），
把 URL 填进商店后台。半小时的事。

### 5. 图纸社区模板仓库（零服务器飞轮）

仓库开 `templates/` 目录收社区投稿（SHARE-FORMAT v1 的 .json 文件），
随版本打包进 APP 模板库；配 Issue 模板收稿。种子内容可以从
Kenney 素材 + 社区精选开始。

### 6. 明确不做（红线）

在线同步/账号/任何网络功能（破"零网络权限"卖点）、广告 SDK、
更多 AI 模型（包体积）、桌面端移植。详见 docs/完整文档.md 开头的"发布渠道计划"。

## 三、开发约定（下一轮会话必须遵守）

- **构建**：`build_apk.bat`（裸 aapt2 管线，非 Gradle）；签名口令从环境变量
  `PINDOU_KS_PASS` 读取；Manifest 必须保留 `package` 属性（裸 aapt2 需要，
  AGP 8 只是告警）。Gradle/CI 侧依赖 Maven 的 ONNX Runtime AAR。
- **测试**：改完代码跑 `qa/run_tests.sh`（CI/Linux）或本地
  `javac -encoding UTF-8 -cp tools\asdk\platforms\android-34\android.jar ...`
  （javac 必须带 `-encoding UTF-8`，否则中文注释在 GBK 环境编译失败）。
- **提交**：本地构建产物（qa/out、build_apk、tools、keystore、备份）都在
  .gitignore；`.github/workflows/*` 的推送需要 Token 有 Workflows 写权限。
- **合规红线**：不新增任何权限（尤其网络）、不引入广告/跟踪 SDK、
  第三方内容先查许可证再入库并登记 THIRD_PARTY.md、模型注意再分发条款
  （AnimeGANv3 非商业、F-Droid 需 Lite 构建——见 DEV-NOTES/渠道计划）。

## 四、给下一轮会话的操作备忘（环境相关，勿外传密钥）

- **GitHub 操作**：用户会临时提供细粒度 PAT（只勾 pindou-photo 仓库）。
  推送触碰 workflows 的提交需含 Workflows 写权限；用完提醒用户撤销。
  Token 只在命令行临时使用，**绝不写入任何文件**。
- **本机安全钩子（Mimosa）**：Bash 里出现 `.java` 路径的写入/编译命令会被拦
  （编译测试请走 bat 脚本或让 CI 跑）；Python 脚本里的动态 URL 请求会被按
  SSRF 拦（网络请求用 curl + 固定域名，或先 DNS 校验公网 IP）。
- **android-emulator MCP**：插件已装但本机无 SDK/模拟器，MCP 工具未连接；
  真机验证走 CI 云端模拟器（已建成）或用户提供 USB 设备。
- **Firecrawl**：插件已装，`firecrawl` CLI 需用户设置 FIRECRAWL_API_KEY
  后才可用（本机 IP 无 key 会被拒）。
- **本机编码**：cmd 控制台是 GBK，UTF-8 中文输出会乱码但不影响实际数据；
  javac/python 务必显式 UTF-8。

## 五、历史版本索引（详情见各 Release 说明）

| 版本 | 内容 |
|---|---|
| v2.26 | 开源基建（清理/协议/声明） |
| v2.27 | 逐色剩余计数 + 今日打卡（Pattern Keeper 式） |
| v2.28 | 油漆桶填充 + 全局替换色 |
| v2.29 | 生成质量三件套（众数取色/BFS 杂色清理/CIEDE2000） |
| v2.30 | PDF 封面/清单/页码 + 透明 PNG 导入 |
| v2.31 | 开放图纸格式 pindou-pattern + AnimeGANv3 离线风格化 |
| v2.32 | 滑动刷选 + 打卡日历 + 镜像绘画 + 合并采购单 |
| v2.33 | 我的豆板（豆仓生成色板）+ 色板数据官方级验证 + CI 模拟器冒烟 |
| v2.34 | 自定义色板完整编辑器（多套色板/单色增删改/RGB取色器/导入导出） |
