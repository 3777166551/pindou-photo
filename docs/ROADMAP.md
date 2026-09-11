# 路线图与交接文档 (ROADMAP & HANDOFF)

> 本文档是项目的**持续交接入口**：当前状态、待办功能、开发约定、操作备忘。
> 新会话/新开发者从这里开始读。最后更新：2026-09-07（v2.39 糖果贴纸风 UI 改版）

## 一、当前状态快照（2026-09-10,v2.48 已发）

- **已发三个正式 Release**(Assets 均为签名 APK):
  - **v2.46 第一个正式版**(versionCode 57):v2.45 全部功能 + 修复两个
    测试抓出的真 BUG(见 DEV-NOTES 21/22)
  - **v2.47 编辑页大整理**(58):常用参数前置,「高级设置」「图片处理」
    默认折叠,顶栏「分享」大按钮(小白友好;冒烟已适配折叠)
  - **v2.48 精细编辑三件套**(59):颜色排除+智能重映射(豆单⊘/存档持久)、
    轻点查色号弹窗、画笔吸管
- **测试体系三层,每次 push 自动执行**:148 项 JVM 单测(含 AR 投影数学
  TestBoardProjector 16 项) → 完整拼豆流程
  端到端冒烟(照片注入→出图→豆单断言→导出四件套→存档→合并采购→
  重开→**庆祝流程 8×8 标记 100%**)→ **模糊测试 emulator-fuzz**(边界
  图片/Monkey 2×3000/拟人漫游 260 步,本包 FATAL 或 ANR 即红);
  另有 **ar-smoke.yml 专属流水线**(v2.49,虚拟摄像头模拟器逐张走查
  Wikimedia Commons 豆板照片进 AR 试摆)
- **模糊测试战果**:monkey 种子 424242 砸出「辅助标记后切尺寸 → 豆单
  刷新越界崩溃」(已修);冒烟砸出「首页我的项目入口不可见」(已修)
- **Firecrawl 已可用**(用户配好 key):竞品增量发现见
  竞品功能比对2026-09.md 附录(吸管✅已做;候选:投影上板模式、
  立体分层图纸;不做:蓝牙硬件板/社区/云)
- **tools/promo/ 推广小工具**(B站视频/卡片生成)= 用户本地工具,
  .gitignore 的 tools/ 规则覆盖,**永不入库**(用户明确要求)
- **GitHub Token**:会话用完,提醒用户吊销;下次会话需用户提供新 Token
  (仅 push/Release 需要,匿名读公开仓库不限)
- 真机验收清单:qa/全面测试清单.md(已同步 v2.48)
- v2.38 及以前见"历史版本索引"。qa 四套 66 项全绿,compile_check 通过。
- 发布流程不变:build_apk.bat(PINDOU_KS_PASS,口令在 HANDOFF.md)→
  GitHub API 发 Release。**本版尚未出 APK/未发 Release**。
- **CI 全绿**：每次 push 自动跑 qa 测试套件 + Gradle 编译 + 云端模拟器 UI 冒烟
  （qa/ui_smoke.sh:首页/知识/模板/空白画布/清单/色板管理/豆仓/文字生成 全点击走查,
  英文环境运行顺带验证 i18n,截图存为构建产物）
- **测试**：`qa/` 四套 69 项断言全绿
  （TestColorMath 17 / TestPatternEngine 11 / TestPatternPatch 13 / TestCustomPalette 28），
  入口 `qa/run_tests.sh`（CI/Linux）或 `qa/run_tests.bat`（Windows 本地）；
  纯编译检查用根目录 `compile_check.bat`（aapt2+javac，不动 build_apk 产物）
- **合规链**：AGPL-3.0(LICENSE) + THIRD_PARTY.md(全部第三方声明,含 Fluent Emoji MIT)
  + DISCLAIMER.md + docs/DEV-NOTES.md(踩坑记录) + docs/SHARE-FORMAT.md(开放图纸/色板格式)
  + 隐私政策页 https://3777166551.github.io/pindou-photo/privacy.html(已上线)
- **权限底账**：全 APP 唯一权限 WRITE_EXTERNAL_STORAGE(maxSdkVersion=28)，
  Android 10+ 零权限、零网络
- **本机注意**：git 后台维护偶尔报 multi-pack-index 权限错(无害,推送成功即可);
  github.com 连接间歇性抽风,推送/下载用重试循环即可

## 二、功能路线图（按优先级）

### 1. ✅ 自定义色板完整编辑器（v2.34 已完成）

「我的色板」管理页（PaletteActivity，编辑器豆豆清单页入口）：多套自定义色板
增删改、单色编辑（RGB 滑杆/十六进制取色器 + 名称/色号）、按色相排序、
导出/导入（开放色板格式 `pindou-palette`，兼容取 pindou-pattern 的颜色表）、
豆仓一键重建"我的豆板"。数据存 `files/custom_palettes.json`（CustomPalettes），
运行时槽位在 BeadBrandCharts.customs（可多套），EditorActivity 靠
`CustomPalettes.revision()` 在 onResume 自动刷新色板下拉框。

### 2. ✅ 多语言(英/日)(v2.35 已完成)

全部 UI 串抽到 `values/strings.xml`(中文默认),`values-en` / `values-ja` 同步;
120 个通用色名、档位名、砖块/去噪档位、星期、知识页 7 篇文章均为三语资源数组,
由 `util/L10n.apply(context)` 在各 Activity onCreate 时套用(未调用时保持中文,
qa 纯 JVM 测试不受影响)。跟随系统语言,无应用内切换(如需 per-app 语言,
等 minSdk 提到 33 用系统设置或接 appcompat)。CI 冒烟跑在英文模拟器上,
顺带端到端验证英文串。

### 3. ✅ 全 ABI 决策:声明仅支持 64 位(v2.35 已定)

`app/build.gradle` 加 `ndk { abiFilters 'arm64-v8a' }`,Gradle/CI 构建的 APK
同样只含 arm64,与本地 bat 构建一致;商店侧按 64 位 APK 自动过滤 32 位设备,
列表再标注"仅支持 64 位设备"。32 位老手机明确不支持(2019 年后设备几乎全 64 位)。

### 4. ✅ 隐私政策网页(v2.35 已上线)

`docs/privacy.html`(中英双语,内容取自 DISCLAIMER + 数据安全说明)。
GitHub Pages 已通过 API 开通(main 分支 /docs 目录,并加 `docs/.nojekyll`
禁用 Jekyll——docs 里的 Markdown 代码示例会让 Jekyll 构建报错)。

**商店后台填这个 URL**:
`https://3777166551.github.io/pindou-photo/privacy.html`

### 5. 图纸社区模板仓库（零服务器飞轮）

仓库开 `templates/` 目录收社区投稿（SHARE-FORMAT v1 的 .json 文件），
随版本打包进 APP 模板库；配 Issue 模板收稿。种子内容可以从
Kenney 素材 + 社区精选开始。

### 5. ✅ 用户反馈修复 + 模板库大扩充(v2.36~v2.38 已完成)

- v2.36:吉卜力风强度滑杆(默认 65%)+模型输出色系统一(治黄绿偏色);
  模板库单屏改版+真实数量;取景裁剪(CropView,拖动/缩放选区);
  效果图 zoom=1 板底完整显示;首页豆仓独立管理页(InventoryActivity)
- v2.37:**照片 EXIF 8 方向全支持**(治"照片歪斜只显示一部分"——旧代码只处理
  3/6/8 且识别图纸完全没修);模板库换 Fluent Emoji 176 款
- v2.38:模板扩到 **8 大类 277 款**(新增游戏音乐/运动奖牌/出行工具/潮流符号,
  动物+16)。生成管线在 tools/emoji_src(本地,不入库):
  entries_data.py 策划清单 → resolve.py 出下载清单 → curl 走
  api.github.com contents(base64;raw CDN 被墙)→ quantize_data3.py
  Lab 最近邻量化到 120 色板(安全字母表 SAFE_ALPHABET,见 DEV-NOTES 14)→
  emit_java.py 发射 TemplateEmojiData(spec 带 0:32:32: 前缀,
  发射前逐条模拟解析,防启动崩溃复发)
- 首页豆仓卡片/模板卡片数字均为运行时统计,加素材自动变

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
- **★ UI 测试 = 推 git 走 CI 云端模拟器**（2026-09-07 实战验证，标准流程）：
  本机缺 hypervisor（装 AEHD/WHPX 要管理员+重启），且模拟器 37.x
  拒绝在 x86 主机跑 arm64 镜像，**本地模拟器路线不可行**。标准做法：
  ①改完 UI → 本地 `compile_check.bat` + `qa\run_tests.bat` 全绿 →
  ②push main（github 直连间歇抽风，重试 5~15 次，每次间隔 30~45s 可过；
  `git -c http.version=HTTP/1.1 push` 用一次性 token URL，不落盘）→
  ③CI 自动跑 test（qa 132 项）+ `emulator-smoke`（API30 x86_64 + KVM，
  `qa/ui_smoke.sh` 走查约 15~20 分钟）→
  ④拉截图验收：`GET /repos/3777166551/pindou-photo/actions/runs/{id}/artifacts`
  （公开仓库匿名也可读）→ 下载 `smoke-screens` zip 解压逐张人眼审。
  **2026-09-09 起 ui_smoke.sh 升级为完整拼豆流程端到端**（A 段硬流程:
  测试照片 run-as 注入 → adb root 直启编辑器 → 出图 → 参数联动 →
  豆单/按包换算断言 → 裁剪/夜间/导出四件套 → 存档 → 合并采购单+CSV →
  重开断言；B 段覆盖走查），并用它抓出并修复「首页我的项目入口不可见」
  真 BUG（DEV-NOTES 21）；完整功能清单见 docs/功能清单.md。
  坑：CI 截图是 en 环境 + 模拟器 emoji 字体缺字（🧰 渲染成怪块），别当 bug；
  上滚手势起点必须在设置区内（y≥1200），起点在 y=600 会被 PatternView
  吃掉；AlertDialog 按钮点击默认 dismiss 对话框，脚本别重复点 Close。
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
| v2.35 | 多语言（英/日）+ 强化 UI 点击冒烟 + 64 位 ABI 声明 + 隐私政策页 |
| v2.36 | 首批用户反馈修复：吉卜力风强度滑杆+色系统一、模板库单屏改版+真实数量、取景裁剪、效果图板底完整显示、首页豆仓管理页 |
| v2.37 | 照片方向修复（EXIF 8 方向全支持，歪斜/显示不全根治）+ 模板库全面换成 176 款 Fluent Emoji 流行模板（MIT） |
| v2.38 | 模板扩充至 8 大类 277 款（新增游戏音乐/运动奖牌/出行工具/潮流符号 + 动物扩充） |
| v2.39 | 全 APP 界面改版「糖果贴纸风」：墨描边贴纸卡/糖果粉主色/多巴胺图标盘/框选控件重绘（角标+角点拖拽+清晰度徽章）/全局弹窗换壳 |
| v2.40 | 三个新功能（竞品调研落地）：迷你豆 2.6mm 规格切换（尺寸/克重/PDF 全联动+随存档）、描摹模式（照片半透明垫底照着描+随存档）、按板引导（逐 29×29 板点亮+板进度+自动跳板）；CI 截图验收常态化（qa/ui_smoke.sh 覆盖新功能） |
| v2.41 | 第一档体验功能：贴纸风分享长图（效果图+数据卡 1080px 本地渲染+系统分享）、夜间图纸（画布转暗+编辑页亮度降档，偏好持久化）、时长/难度预估（豆单摘要行）、完成庆祝动画（熨斗扫过+贴纸弹跳+糖果纸屑，纯 Canvas）、微信/QQ 分享直达（包名定向，零 SDK） |
| v2.42 | 第二档功能：万花筒对称绘画（关/四象限/万花筒，D4 八向数学经 18 项单测）、拍照对色（豆仓入口，点豆找色号 CIEDE2000+一键登记，离线）、每日一拼（模板库按日期稳定推荐）、玩法库（知识页新增立体拼豆/小件清单灵感文章 ×3 语） |
| v2.43 | 线稿图纸模式（第三种本地风格，三转二调研遗留玩法落地）：索贝尔梯度提轮廓 → 网格盒平均 → 阈值化，黑豆描线+空格自己填色；描线灵敏度滑杆（阈值占最大梯度 0.55→0.10）；透明像素白底合成+覆盖率门槛（黑字透明底/抠图 PNG 可用、描边不污染透明区）；墨色自动取色板 L* 最深豆；qa 新增 TestLineArt 14 项（全套 101 项），冒烟走查文字位图切线稿 |
| v2.44 | 竞品调研落地的三件快赢 + 3D 预览试做 + 两个真机 BUG 修复：Nab Midi 品牌色号表（第 5 品牌 30 色，beadcolors 数据零漂移校准，gen_charts.ps1 补回手写 customs 成员）；豆单按包换算（≥500 颗显示 ≈N包）；拼豆辅助新增逐行引导（assistBoardMode 布尔重构为三态模式）；效果图 3D 预览 chip（纵向压缩 3/4 视角+圆柱光影+投影，纯 Canvas）；修复英雄卡糖珠装饰被当杂色（删除装饰重新生成）、修复取景裁剪手势失效（CropView 弃 GestureDetector 改自算+父容器拦截保护）；冒烟新增 assist_row/effect3d/crop_drag 三步 |
| v2.45 | 迷你规格品牌色号表 ×3（Artkal C·2.6mm 174 色 / Perler Mini·2.6mm 41 色 / Hama Mini·2.5mm 78 色，MIT 许可的 maxcleme/beadcolors 数据集 gen/v2，THIRD_PARTY 已声明）；品牌表自动联动豆子规格（选 2.6/2.5mm 表自动切迷你档+toast）；拍照对色 ColorMatchActivity（豆仓入口：选豆子照片→点豆→邻域平均色→全品牌 CIEDE2000 排序前 5→一键登记豆仓，纯离线）；qa 新增 TestBrandCharts 31 项数据质量测试（全套 132 项）；qa\全面测试清单.md 出炉供真机全面验收 |
| v2.46 | 第一个正式版：v2.45 全量 + 修复两个测试抓出的真 BUG（首页「我的项目」入口不可见=DEV-NOTES 21；辅助标记后切尺寸豆单越界崩溃=DEV-NOTES 22）；测试体系升级：完整拼豆流程端到端冒烟 + 模糊测试 emulator-fuzz（边界图片/Monkey/拟人漫游） |
| v2.47 | 编辑页大整理：常用参数前置，「高级设置」「图片处理」默认折叠，顶栏「分享」大按钮，清理孤儿标题；真 BUG 修复：首页「我的项目」入口不可见（cf25c3d） |
| v2.48 | 精细编辑三件套：颜色排除+智能重映射（豆单⊘/已排除条/存档持久）、轻点查色号弹窗、画笔吸管；真 BUG 修复：辅助标记后切尺寸豆单越界崩溃（5ba70e88）；庆祝流程进入 CI 冒烟（8×8 标记 100% 断言） |
| v2.49 | **AR 试摆（假 AR，先期调研后落地的最小版）**：效果图页新增「AR 试摆」chip → 全屏相机取景（Camera2 手写，零依赖），效果图按豆子规格换算物理尺寸后作为一块"板子"立在放置时的视线前方，旋转矢量传感器驱动透视（nlerp 平滑），纯展示不可交互；传感器三级降级（旋转矢量→游戏旋转矢量→加速度+磁场→固定视角）；新增 CAMERA 运行时权限（画面仅本地使用，隐私政策不变），无网络权限依旧；BoardProjector 纯 Java 投影数学（16 项 JVM 单测，全套 148 项），qa/ar_smoke.sh + 专属 CI ar-smoke.yml（模拟器 -camera-back virtualscene，5 张 Wikimedia Commons 豆板照片逐张走查，许可见 qa/ar_data/CREDITS.md，**已跑绿**）；**CI 抓出三个真问题**：① 自定义 View 缺 (Context,AttributeSet) 构造器 → XML 膨胀崩进程（DEV-NOTES 23）；② 再现 findViewById 找不到自定义视图的 NPE → 该页弃 XML 改纯代码构建 UI；③ AR 路径效果图渲染限宽 1024（58×58 全尺寸 3720²≈55MB 连续分配，导出路径不变，EffectRenderer.render 增 maxDim 重载）。顺带 fuzz 抓出存量 BUG：无相机设备 ACTION_IMAGE_CAPTURE 抛 SecurityException 崩进程（DEV-NOTES 24，已修）；全流程 smoke 的「庆祝 100%」步在本轮之前就已在 main 上红（基线 0200077 同步失败，与 AR 无关，待查） |
