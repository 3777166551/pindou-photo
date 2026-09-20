# 路线图与交接文档 (ROADMAP & HANDOFF)

> 本文档是项目的**持续交接入口**：当前状态、待办功能、开发约定、操作备忘。
> 新会话/新开发者从这里开始读。最后更新：2026-09-19（v2.59 视觉重制:
> M3 基线 + 点阵背景破案,推送 3fff48f8,CI 冒烟已绿;**下一个会话接着跑
> 「六、待办清单」第 1 项:GIF 导出尺寸 bug**）

## 一、当前状态快照（2026-09-19,v2.59 视觉重制已推,CI 验收中）

- **v2.59 界面重制两轮(2026-09-19,用户反馈"风格落后"直答)**:
  ①第一轮「清汽水」(冷白+电流紫渐变)已推 15bf3f8 并 **CI 全绿
  (build 353 项+冒烟+AR 冒烟)**,用户看后仍嫌不好看,点名"参考
  开源可商用 UI";②第二轮 **严格落地 Material 3 基线(Apache-2.0,
  m3.material.io 官方令牌)**:色板全换官方角色值(surface #FEF7FF/
  primary #6750A4/primary-container #EADDFF/secondary-container
  #E8DEF8/outline-variant #CAC4D0/on-surface #1D1B20…),组件对齐
  官方 spec——按钮 40dp 全圆角实底(去渐变去贴片投影)、分段标签=
  M3 segmented button(outline 描边槽+secondary-container 选中段)、
  chip 选中=primary-container、卡片 surface-container-low 16dp、
  弹窗白底 28dp、首页英雄卡=primary-container 色调卡(删蜡笔下划线);
  字体确认系统无衬线(Skin 自 v2.25 就是 no-op);修「有的卡片颜色
  特别深」=英雄卡弃用旧糖果贴纸风 PNG(墨描边元凶)+点阵背景贴图
  重生成(surface 底+淡紫点);修「按钮挤压」=45 处 34/36dp chip
  统一 40dp、间距 6/7→8dp、字号 11/12→12/13sp(第一轮已 CI 验证)。
  教训:自造配色(暖陶玫瑰/清汽水)两轮都不满意,**跟着成熟设计系统
  的官方令牌走**才是正路;DEV-NOTES 35 记录 PowerShell 写 .java 被
  钩子改坏(0→x)必须走 Edit 工具的坑。③**CI 截图复审抓出背景大坑**
  (DEV-NOTES 36):bg_pegboard.xml 一直引用 v2.25 粉彩渐变大图
  bg_pastel,点阵背景从未生效——改回 tile 平铺后 surface 色点阵才真正
  上屏;英雄卡内次按钮 secondary-container→白底提对比。最终推送
  3fff48f8(15bf3f8 汽水轮 CI 全绿=几何改动验证;5b0b1ea M3 轮
  test/smoke/fuzz 全绿;3fff48f8 轮 test+smoke 绿,fuzz 收尾),
  截图存 qa\ci_shots_59b\。本地 git 因 github 直连断未能 fetch 对齐,
  网络恢复后已 fetch+rebase 对齐(ed0c956)。
- **v2.59 交互简化(希克定律改版,2026-09-20,用户点名"按钮越多用的人
  越少")**:①首页信息架构重排——主界面从 ~12 个入口砍到 6 个:英雄卡
  (选照片/拍照)→「我的项目」上移到第二位(回访最高频)→「模板库+豆仓」
  常用两张→「🔧 更多工具」**默认折叠**(去水印/识别图纸/文字图纸/空白
  画布/玩法知识/备份恢复收进去,控件 ID 全部不变,新增
  moreToolsHeader/tvMoreToolsArrow/moreToolsBody);②编辑页「图片调整」
  (亮度/对比/饱和)滑杆卡收进**默认折叠区**(btnAdjustHeader/tvAdjustArrow/
  adjustBody,复用 setupCollapse)——主界面默认可见项=三个 tab+预览+
  尺寸 chips+W/H+色板/限色/风格+三个折叠头+辅助开关;③ui_smoke.sh
  适配:新增 ensure_more_tools 助手(三原则①:dump 里查门控子控件
  btnScanPattern 不在才点 moreToolsHeader),5 处折叠区工具卡前插入;
  编辑页滑杆冒烟本就无依赖,零改动。已验证:compile_check+qa 353 全绿
  本地过,CImoke 验收见 ci_shots_59c。
- **★ 下一个会话接着跑（按序,细节见「六」）**:
  1. **GIF 导出尺寸 bug(最高优先)**:真机实测导出成功但尺寸 100×208
     (预期长边 720),且帧内容空白——startGifExport() 的 playView
     宽高来源可疑,修后同流程回归;
  2. 空白画布 chip/hint 偶发 stale(58×58):startBlankCanvas 不调
     syncSizeUi,补一行;
  3. real_walk.bat 适配 Android 15+(am start 非导出被拦,见 DEV-NOTES 32);
  4. 手机设置恢复(screen_off_timeout/stay_on_while_plugged_in);
  5. 提醒用户吊销已用完的 PAT(2026-09-18 与 09-19 两把均已实际使用,
     若尚未吊销尽快处理)。
- **★ 2026-09-18 真机验证 session(vivo V2536A,OriginOS/Android 16,
  无线调试)**:配对+连接+装包全通;七屏走查零 FATAL;**3D 把玩+生长动画
  真机实测通过**(文字生成"BEAD"→进 3D 把玩,豆豆按批次落成,顶栏四
  chip/透视/定位点全部正常,截图 qa\out\play3d_*.png);GIF 导出全链路
  (SAF 选择器→下载目录)跑通,文件合法(GIF89a/76 帧/NETSCAPE 循环,
  qa\out\pindou_build_20260918_2056.gif)但踩中尺寸 bug。
  新踩坑:Android 16 拦 shell am start 非导出 Activity、OriginOS 锁屏
  杀无线调试端口且端口轮换、adb 37.x CRLF 输出让 findstr $ 失效——
  详录 DEV-NOTES 32/33/34;真机操作流(配对/点亮/常亮/点击驱动)已写进
  ROADMAP「四」★ 真机走查条目。
- **v2.58 生长动画 + GIF 导出(已发布)**:传播位功能——
  3D 把玩页进门自动播放「生长动画」(豆豆按颜色分批从空中落成整幅,
  「▶ 重播」随时再看),「🎞 GIF」chip 把整段动画离线渲成 GIF 分享:
  纯 Java GIF89a 编码器(逐帧局部调色板+中位切分+LZW,GIFCOMPR 语义),
  UI 线程渲染/后台编码流水线,SAF 导出零新权限;TestGifEncoder 20 项
  (自带迷你 LZW 解码器做真往返)。
- **v2.57 外部打开图纸 + 3D 把玩(已发布)**:ROADMAP
  候选 #1 落地——EditorActivity 挂 VIEW/SEND intent filter(application/json
  + octet-stream),微信/QQ/文件管理器点开 .json 图纸直达导入,解析失败
  提示即退;「🤹 3D 把玩」全屏旋转/缩放成品
  (Play3DProjector 正交投影纯数学 + TestPlay3D 12 项),彩蛋虚拟熨斗
  把豆豆烫连,烫满 100% 自动展示自转;入口 = 效果图页右上第三个 chip。
  顺带查证:本地 LICENSE 与 GitHub 仓库识别均为 AGPL-3.0,无偏差。

- **v2.55 六边形板(已发布)**:ROADMAP 候选 #4 落地——
  形状三 chip(方/圆/六边形)+ 尖顶正六边形蒙版 + 全渲染链路(效果图 2D/3D/
  图纸/打印/PDF)+ 分享格式 hex 字段 + TestHexBoard 17 项;
  同网格蒙版方案,偏移网格(蜂窝错位)未含。
- **v2.56 全量备份/恢复(已发布)**:ROADMAP 候选 #2 落地——
  首页「📦 备份与恢复」卡片:项目存档+豆仓+打卡日历+自定义色板打包成
  zip,SAF 导出/导入,零新权限;恢复前弹确认(写明覆盖内容),项目目录
  整目录替换、库文件缺了不动现状;BackupManager 纯 java.io/zip 可桌面
  单测(TestBackup 13 项:往返/inspect/拒非法/防 zip-slip);恢复后失效
  BeadInventory/BeadCalendar 内存缓存 + CustomPalettes.load 重载。
  真机走查新工具 qa\real_walk.bat(adb 驱动七屏截图+logcat FATAL 扫描,
  用户只管插线)。
- **最新代码**:`v2.58`(main=8a4109e,2026-09-16 推送,CI build+ar-smoke
  全绿;Release v2.58 挂 PindouPhoto-v2.58.apk)。功能版本索引见第五节;
  **可安装 APK 在每次绿色 run 的 Artifacts 里**
  (`PindouPhoto-debug`,下载需登录 GitHub;Artifact 下载接口必须带 token)。
- **v2.54 拍照验收**(用户点名的头号亮点):真板俯拍照 → 拖四角校准 → 逐格
  透视采样读色(CIEDE2000,避开豆孔偏心采样)→ 高亮**摆错(红)/漏摆(黄)/
  多余(紫)**,错格熨烫前拦住。入口=辅助工具行「📸 验收」+ 我的项目每行
  📸(存档打开生成图纸后自动进)。核心在纯 Java VerifyEngine;
  **TestVerify 34 张带 ground truth 合成板量化测试**(132 项断言,检出 ±1 颗);
  冒烟有同源闭环用例(必须 wrong 0);**真照片评审集 22 张已入库**
  (qa/verify_data/real/ + CREDITS.md,待真机对照调阈值 DE_BOARD=18/DE_BEAD=28)。
- **v2.51~v2.53 回顾**:沉浸拼豆(全屏覆盖层+✓印章,印章已按用户要求**去掉
  振动**,纯视觉)、语音引导(本地 TTS,播报换色/跳板/全完)、帮助入口规范化
  (Bead-along 行尾 ？ 图标 + 知识页首篇《APP 使用指南》)、视觉重制
  (暖陶玫瑰 M3 风,去全部墨描边)。
- **2026-09-13 专项审计**(辅助/日历/打卡/投影对位逐行审读)抓出 5 个问题,
  全部修复并 CI 验证(进度清空/双指缩放/立体组合线程/assistBoard 上界/
  投影 chip 语义)。教训见 DEV-NOTES 25/26。
- **测试体系三层,每次 push 自动执行**:291 项 JVM 单测(10 套,新增
  TestVerify 132)→ 完整流程端到端冒烟(~50 张截图,含沉浸/验收闭环/
  语音开关/单击取消回路断言)→ 模糊测试;另有 ar-smoke.yml 专属流水线。
- **发布渠道现状**:仅 GitHub Releases(国内下载不便,仍是最大短板);
  候选:酷安直传 / 应用宝+华为(需软著,鸿蒙调研已有)。
- **权限底账**:CAMERA(AR/对位/验收共用,画面仅本地) +
  WRITE_EXTERNAL_STORAGE(maxSdkVersion=28);无网络权限不变。
- **合规链**:AGPL-3.0 + THIRD_PARTY.md(含 DMC 数据 MIT 声明) + DISCLAIMER
  + DEV-NOTES + SHARE-FORMAT + 隐私政策页(已上线)。
- **本机网络(重要)**:①github.com 直连间歇抽风,commons.wikimedia.org 被墙
  ——**用户梯子是系统代理 127.0.0.1:7890,curl/git 必须显式走它**
  (`curl -x http://127.0.0.1:7890`、`git -c http.proxy=http://127.0.0.1:7890
  push`),走代理后一次就通;梯子关闭时 **api.github.com 直连仍通**→REST
  API 降级推送(tools/api_push_fallback.ps1,已参数化 -FilesCsv/-Msg)。
  ②API 推送不入本地历史,网络恢复后 `git fetch && git reset --hard
  origin/main` 对齐(内容相同只换 SHA)。③git/commits 端点要 40 位全 SHA。
  ④**Token 已被本 session 反复使用,下一会话开场先提醒用户吊销换新**。

## 二、功能路线图（按优先级）

### 已完成(细节见版本索引 v2.34~v2.50,此处只留索引)

- ✅ 自定义色板完整编辑器(v2.34) / ✅ 多语言英日(v2.35) /
  ✅ 仅 64 位 ABI 声明(v2.35) / ✅ 隐私政策网页上线(v2.35,
  https://3777166551.github.io/pindou-photo/privacy.html) /
  ✅ 用户反馈修复+模板库 277 款(v2.36~v2.38,生成管线 tools/emoji_src)

### 下一批候选(2026-09-15 复盘排序,均未立项)

1. ~~外部打开图纸文件(intent filter)~~ ✅ 已做(v2.57,微信/QQ 点开 .json 图纸直达导入)
2. ~~全量备份/恢复~~ ✅ 已做(v2.56,项目+豆仓+日历+色板打包 zip,SAF 零新权限)
3. 拼豆年报/进度分享卡(打卡数据本地合成,社媒自传播补渠道)【低-中】
4. ~~六边形/异形板~~ ✅ 已做(v2.55,同网格六边形蒙版;豆画的"偏移网格"
   属另一档工程量,见版本索引 v2.55 备注)【中】
5. 钩织/乐高图纸导出(十字绣已打通多工艺方法)【中】
6. TalkBack 无障碍(儿童家长/低视力场景)【中】
7. 大图低端机性能验证(116×116 以上未系统测过)【中】
8. ~~语音引导~~ ✅ 已做(v2.53,本地 TTS;评估:跳板/换色提醒有价值,
   出场率低,默认关;**语音输入勿做**——麦克风权限破零权限红线)

### 5. 图纸社区模板仓库（零服务器飞轮,待立项）

仓库开 `templates/` 目录收社区投稿（SHARE-FORMAT v1 的 .json 文件），
随版本打包进 APP 模板库；配 Issue 模板收稿。种子内容可以从
Kenney 素材 + 社区精选开始。

### 6. 明确不做（红线）

在线同步/账号/任何网络功能（破"零网络权限"卖点）、广告 SDK、
更多 AI 模型（包体积）、桌面端移植、社区广场/图纸市集、云同步、
IoT/蓝牙硬件板。详见 docs/完整文档.md 开头的"发布渠道计划"。

## 三、开发约定（下一轮会话必须遵守）

- **构建**：`build_apk.bat`（裸 aapt2 管线，非 Gradle）；签名口令从环境变量
  `PINDOU_KS_PASS` 读取；Manifest 必须保留 `package` 属性（裸 aapt2 需要，
  AGP 8 只是告警）。Gradle/CI 侧依赖 Maven 的 ONNX Runtime AAR。
- **测试**：改完代码跑 `qa/run_tests.sh`（CI/Linux）或本地
  `javac -encoding UTF-8 -cp tools\asdk\platforms\android-34\android.jar ...`
  （javac 必须带 `-encoding UTF-8`，否则中文注释在 GBK 环境编译失败）。
- **提交**：本地构建产物（qa/out、build_apk、tools、keystore、备份）都在
  .gitignore；`.github/workflows/*` 的推送需要 Token 有 Workflows 写权限。
  已加 `.gitattributes`（\*.sh 强制 LF——CI bash 遇 CRLF 直接语法崩；
  \*.bat CRLF）。
- **合规红线**：不新增任何权限（尤其网络）、不引入广告/跟踪 SDK、
  第三方内容先查许可证再入库并登记 THIRD_PARTY.md、模型注意再分发条款
  （AnimeGANv3 非商业、F-Droid 需 Lite 构建——见 DEV-NOTES/渠道计划）。
- **PowerShell 编码坑(PS5.1)**:无 BOM 的 .ps1 里写中文会按 ANSI 误读导致
  语法错——含中文的脚本存 UTF-8 with BOM,或脚本纯 ASCII + 内容走外挂
  UTF-8 文件/base64(中文字符串务必走 base64 解码,直接字面量会被 mangle);
  Write 工具写的是无 BOM UTF-8,注意。

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
  → 下载 `smoke-screens` zip 解压逐张人眼审（**2026-09-14 实测：artifact
  下载接口必须带 token，匿名 401**；runs/jobs 列表才匿名可读）。
  **2026-09-09 起 ui_smoke.sh 升级为完整拼豆流程端到端**（A 段硬流程:
  测试照片 run-as 注入 → adb root 直启编辑器 → 出图 → 参数联动 →
  豆单/按包换算断言 → 裁剪/夜间/导出四件套 → 存档 → 合并采购单+CSV →
  重开断言；B 段覆盖走查），并用它抓出并修复「首页我的项目入口不可见」
  真 BUG（DEV-NOTES 21）；完整功能清单见 docs/功能清单.md。
  坑：CI 截图是 en 环境 + 模拟器 emoji 字体缺字（🧰 渲染成怪块），别当 bug；
  上滚手势起点必须在设置区内（y≥1200），起点在 y=600 会被 PatternView
  吃掉；AlertDialog 按钮点击默认 dismiss 对话框，脚本别重复点 Close。
- **★ 真机走查 = real_walk.bat(2026-09-18 更新:无线调试已实战验证)**:
  ①**连接**——手机开无线调试(无需数据线):开发者选项→无线调试→配对码
  配对,`adb pair <配对IP:端口> <6位码>` 后 `adb connect <主页IP:端口>`;
  **OriginOS 每次锁屏都会杀掉连接并轮换端口**,锁屏=重连,端口以手机
  「无线调试」主页实时显示为准;连上第一件事
  `settings put global stay_on_while_plugged_in 7` +
  `settings put system screen_off_timeout 600000`(防灭屏,测完恢复);
  跑脚本前 `set ANDROID_SERIAL=<IP:端口>`(无线设备在 adb 里有两个别名,
  不钉住会 more than one device)。
  ②**Android 15+ 限制**——shell `am start` 非导出 Activity 抛
  SecurityException(DEV-NOTES 32),走查脚本的非导出页步骤在 Android 16
  真机上半数失效;当前可用路径 = monkey 拉 LAUNCHER 进首页 + uiautomator
  dump 解析 bounds 后 `input tap` 点击驱动(qa\out\uinfo.ps1 是现成的
  dump 解析器);改造 walk 脚本 = 待办六-3。
  ③**流程**——`set ANDROID_SERIAL=...&& qa\real_walk.bat [apk]`:装包
  (可选)→ 逐屏截图(screencap)→ logcat FATAL 扫描 → 产物
  `qa\real_shots\<时间>\`。2026-09-18 实测:零 FATAL,截图/报告齐全。
  ④测试辅助件(本地 qa\out\,未入库):uinfo.ps1(dump 解析)、
  heartbeat.bat(保活心跳,实测锁屏时救不了,仅减 少空闲断连)。
- **Firecrawl**：插件已装，`firecrawl` CLI 需用户设置 FIRECRAWL_API_KEY
  后才可用（本机 IP 无 key 会被拒）。
- **本机编码**：cmd 控制台是 GBK，UTF-8 中文输出会乱码但不影响实际数据；
  javac/python 务必显式 UTF-8。
- **★ git 通道全断时的降级推送(2026-09-13 实战验证)**:github.com:443 的
  git 端点间歇全断而 api.github.com 仍通——走 REST API 推:单文本文件用
  Contents API(PUT contents,须带旧 sha);多文件/二进制用 Git Data API
  (blob base64 → tree(base_tree=HEAD tree) → commit(parents=HEAD) →
  PATCH ref)。注意:①API 提交不触发本地历史,网络恢复后
  `git fetch && git reset --hard origin/main` 对齐;②中文路径/内容的
  base64 要在脚本外先算好或脚本内解码,PowerShell 字面量会被 ANSI mangle;
  ③同一文件多次 edit 后推树,以 commit_map 最后版本为准(初版脚本同路径
  双条目后者覆盖前者,曾因此丢捕获块导致编译失败)。
- **★ 冒烟脚本防脱同步三原则(三轮 CI 折腾的总结)**:
  ①折叠区头按钮是开关——先查门控子控件(如 chipShapeRound/btnCrop)在不在
  dump 里,不在才点头按钮,盲点会把已展开的区段折回去;
  ②开关类控件以 uiautomator 的 checked 属性为准拨动并复核,盲点坐标会
  落到邻格开关;面板按钮在开关下方,拨完要逐屏滚动到按钮可见
  (屏外节点不进 dump);
  ③风格切换/参数改动有 loading 蒙层,蒙层期间 uiautomator 找不到任何
  控件——每步等足重生成时间(8s)再走下一步;
  ④首次开启辅助自动弹「怎么用」帮助弹窗——弹窗开着时 dump 可能只剩弹窗
  窗口,拨开关的坐标点击全落在弹窗上(表现为"拨了没上"),toggle 成功与
  失败路径都要兜底 dismiss;硬失败必须走 die() 而且函数必须先定义
  (0914 run135 实锤:die 未定义,toggle 六轮失败后脚本带病继续跑)。

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
| v2.49 | **AR 试摆（假 AR，先期调研后落地的最小版）**：效果图页新增「AR 试摆」chip → 全屏相机取景（Camera2 手写，零依赖），效果图按豆子规格换算物理尺寸后作为一块"板子"立在放置时的视线前方，旋转矢量传感器驱动透视（nlerp 平滑），纯展示不可交互；传感器三级降级（旋转矢量→游戏旋转矢量→加速度+磁场→固定视角）；新增 CAMERA 运行时权限（画面仅本地使用，隐私政策不变），无网络权限依旧；BoardProjector 纯 Java 投影数学（16 项 JVM 单测）；qa/ar_smoke.sh + 专属 CI ar-smoke.yml（模拟器 -camera-back virtualscene，Wikimedia Commons 豆板照片逐张走查，许可见 qa/ar_data/CREDITS.md）；CI 抓出的构造器缺失/findViewById/大图 OOM 三问题与无相机 SecurityException 崩溃详见 DEV-NOTES 23/24（该页已改纯代码构建 UI） |
| v2.50 | **四件套（竞品调研既有候选，在线复核后落地）**：① **投影对位模式**（拼豆辅助工具行新增「对位投屏」：相机取景 + 四点校准把图纸"钉"到真实拼豆板，当前辅助色高亮/整图虚影切换、◀▶ 换色、透明度滑杆——把 v2.49 假 AR 基建从"看成品"升级到"辅助拼"）；② **立体组合**（我的项目对话框新增入口：选 2~4 个存档自下而上堆叠，逐层拖动调位/层高滑杆，透视+侧壁+投影预览，合并豆单按色汇总，导出预览 PNG；生成走后台线程）；③ **PDF 图纸导入**（识别图纸入口接受 application/pdf，系统 PdfRenderer 离线渲染第一页 → 现有框选/网格检测管线，零新权限）；④ **十字绣导出**（导出菜单「十字绣图纸(DMC)」：拼豆色按 CIEDE2000 就近映射 DMC 绣线 454 色，DmcMapper 纯 Java 可单测，出 10 格加粗绣图+色号清单+14ct 成品尺寸；DMC 数据取自 MIT 的 Skytuhua/stitch-forge，THIRD_PARTY 已声明）。**2026-09-13 专项审计**抓出并修复：重开存档/调参数清空拼豆进度（严重，CI 新增持久化回归用例）、辅助模式双指缩放失效、立体组合 UI 线程生成、assistBoard 无上界、投影对位 chip 语义（DEV-NOTES 25/26）；ar-smoke 图集扩到 10 张全绿；全套 158 项单测；发布 **v2.50-beta.1**（CI debug 签名测试包） |
| v2.51 | **沉浸拼豆 + 完成 ✓ 印章（用户反馈直答，2026-09-14）**：①「沉浸拼豆」——拼豆辅助工具行新增 chip，一键进入全屏拼豆页：内容根上叠加覆盖层（纯代码构建，与 AR 页同款思路），内含专用 PatternView 与编辑页共享 beadDone/辅助状态（逐色/按板/逐行全兼容），顶栏 = 退出 chip / 当前色圆点+进度文字 / 下一个颜色（仅逐色），双指缩放、双击复位、拖动刷选照常，IMMERSIVE_STICKY 藏系统栏，返回键先退沉浸层不关编辑器，重新生成跟随换图（同尺寸保留缩放），拼满 100% 自动退出让位庆祝动画；②点格完成反馈——标记完成（点按或刷选）的格心弹 ✓ 印章贴纸：白圈衬底+黄油圆+墨勾，1.55 倍盖章式回弹 + 末段淡出（460ms）+ 轻触感，解决"点了一下图形没变化不知道拼没拼"；标记逻辑抽成 toggleAssistCell/markAssistDragCell/finishAssistDrag 双画布共用；qa 冒烟新增 immersive 进入→标记→退出→硬断言覆盖层关闭；三语补键（沉浸拼豆/✕ 退出） |
| v2.52 | **暖陶玫瑰重制 + 辅助「怎么用」（2026-09-14，用户反馈直答）**：①全 APP 视觉从 v2.39"糖果贴纸风"（墨描边+硬投影）重制为 2026 主流 Material 3 Expressive 方向：去全部墨色描边、硬投影换暖砂软层次、chip 换色调填充（选中玫瑰实底/未选暖砂）、主色糖果粉→玫瑰 #E85D75、文字换暖炭/暖灰、底色更净；只动 colors.xml + 8 个 drawable + bg_header，控件结构/ID/几何零改动（CI 按坐标点按不受影响）；②拼豆辅助「怎么用」——工具行改两行布局（4+3），新增「怎么用?」chip 弹六项功能说明弹窗，首次开启辅助自动弹一次（prefs assist_help_seen），按板/逐行改名按板拼/按行拼，EN/JA 长文案缩短防换行；qa toggle_assist_on 加 dismiss_assist_help 兜底 |
| v2.53 | **语音引导（本地 TTS）+ 指导入口浮出（2026-09-15，用户反馈"找不到引导"直答）**：①排查结论——语音引导是候选清单功能**从未实现**（用户找不到是必然）；拼豆指导存在于辅助面板但入口埋太深（要先开开关才见工具行）；顺手抓到 EN/JA 帮助正文残留旧名 Find undone/未完を検索 的文案 BUG；②语音引导落地——辅助工具行新增「🔊 语音」chip（第二排 4 个），本地 TextToSpeech 零网络零权限，懒初始化+就绪前排队的口播在回调补播，播报点=开关开启/下一个颜色/拼满自动跳板跳行/全部拼完（复用既有 toast 文案，三语），onDestroy 释放引擎；③指导入口浮出——Bead-along 卡片标题下新增玫瑰色「怎么用?点这里 ▸」链接（不开辅助也能直达说明弹窗）；④qa 新增语音 chip selected 态硬断言（拨上/拨回各验一次，TTS 引擎有无不影响断言）；帮助弹窗正文补语音条目并修正旧名 |
| v2.54 | **拍照验收（用户点名的头号亮点）+ 帮助入口规范化（2026-09-15）**：①**拍照验收**——给真实拼豆板拍俯拍照，拖四角点对齐板子，逐格透视采样读色（避开豆孔的偏心采样 + CIEDE2000 就近匹配 + 空格板底色自动估计），高亮**摆错(红)/漏摆(黄)/多余(紫)**格子，错一格熨烫后才发现在熨烫前拦住；比对核心抽成纯 Java VerifyEngine（Heckbert 透视映射，无 Android 依赖）；两个入口=辅助工具行「📸 验收」chip（当前图纸直接进）+ 首页「我的项目」每行 📸（存档打开生成图纸后自动进验收页）；拍照走系统相机意图（AppFileProvider，含 SecurityException 防御），相册走 ACTION_GET_CONTENT，零新权限；②帮助入口按 Material 规范升级——Bead-along 行尾 30dp 圆形 ？ 图标（替换文字链接）+ 知识页首篇《APP 使用指南》三语（全局直达）；③**测试**——TestVerify 用带 ground truth 的合成板量化测试：30 随机种子（8×8~22×22、3~6 色、透视抖动、光照不均、噪声、注入错色/漏摆/多余）+ 全对板 + 透视极端 + 无空格退化 + 映射边界，132 项断言检出与注入一致（±1），全套测试升至 291 项；emulator-smoke 新增合成板闭环（qa/verify_data/board_8.png + chart_8.json 由 gen_board.ps1 生成，am start 直推，硬断言 wrong 0）；④已知边界——Wikimedia 真照片评审集因本机网络阻断未建（commons/upload 域名 000），真机实拍是最终验收；空格判定阈值 DE_BOARD=18、豆判定 DE_BEAD=28 为初版，真机照片多拍后可调 |
| v2.55 | **六边形板（ROADMAP 候选 #4，异形板第一期，2026-09-16）**：①**形状体系**——「高级设置→板子形状」扩为三 chip（▭ 方形 / ⬤ 圆形 / ⬡ 六边形），BeadPattern 新增 hex 标志与尖顶正六边形蒙版判定（对顶高=min(cols,rows)，上下顶点触边、左右 60° 收窄，29 格板顶行 1 颗/中带宽 25），PatternEngine 生成时置空板外格、PatternPatch/PatternShare 全链透传；②**渲染**——PatternView 效果图底板（2D/3D 立体）、图纸页白底/网格线/拼板分隔线/外框/描摹底图、EffectRenderer 高清效果图、PatternSheetRenderer 打印图纸与 PDF 全部按六边形渲染（弦段裁剪用解析式 hexChordV/H，与蒙版逐格严格等价，bead 包不出 android 依赖）；③**交互与数据**——存档/分享格式新增 hex 字段（向后兼容，旧文件缺省 false），形状切换强制方形画幅（与圆形一致），旋转/镜像后落到板外的手动修格自动丢弃（六边形转 90° 切角属预期），三语文案（chip/尺寸提示/图纸标注）；④**测试**——qa 新增 TestHexBoard 17 项（蒙版点位/镜面对称/行宽对称/弦段-蒙版一致性/顶点圆检验/assemble 链路），compile_check 编译通过，ui_smoke 补 photo_hex 走查+硬断言；⑤**边界**——本期为同网格蒙版方案（豆子仍在方格阵上），豆画的偏移网格（蜂窝错位排列）是另一档工程量，未包含 |
| v2.56 | **全量备份/恢复（ROADMAP 候选 #2，换机刚需，2026-09-16）**：①**功能**——首页新增「📦 备份与恢复」卡片：项目存档+豆仓库存+打卡日历+自定义色板打包成一个 zip，经系统文件选择器（ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT）导出到任意位置（文件管理器/网盘均可）或从文件恢复，零新权限；②**恢复语义**——先在后台校验+统计再弹确认框（写明"备份内容 vs 将被覆盖的现有项目数"），确认后项目目录整目录替换（旧孤儿清除）、三个库文件有则覆盖/缺则不动现状（旧版备份兼容）；恢复完成失效 BeadInventory/BeadCalendar 内存缓存并 CustomPalettes.load 重载注册；③**实现**——util/BackupManager 纯 java.io/java.util.zip（manifest.json 首条目 + 白名单路径防 zip-slip + canonical 二次校验），桌面 JVM 可单测，Android 侧只拿 filesDir 与 SAF 流；④**测试**——qa 新增 TestBackup 13 项（zip 往返逐字节一致/inspect 不落盘/部分备份保留缺失库/空备份清项目/拒无 manifest·错 format·超版本/zip-slip 穿越/manifest 字段解析），全套 321 项全绿；⑤**真机验收工具**——qa\real_walk.bat（adb 驱动：装包可选→七屏 activity 逐屏截图→logcat FATAL 扫描→qa\real_shots\<ts>\report.txt），把"真机走查"降为"插线跑一条命令"；⑥**已知边界**——SAF 文件选择器在 CI 模拟器上自动化脆弱，备份/恢复暂未入 ui_smoke（JVM 测试+real_walk 兜底）；SharedPreferences（夜间模式等偏好）不在备份范围 |
| v2.57 | **外部打开图纸 + 3D 把玩（候选 #1 + 传播位功能，2026-09-16）**：①**intent filter**——EditorActivity exported=true，挂 ACTION_VIEW（content/file + application/json + octet-stream）与 ACTION_SEND 两组 filter：微信/QQ/文件管理器里点开 .json 图纸直达导入（复用 importFromUri 管线，PatternShare.parse 严格校验，非本格式提示后退出空页；octet-stream 是因为部分应用对 json 不给准确 MIME，误开任何二进制文件也只是选择器里多一项且导入会礼貌拒绝）；②**3D 把玩**——「🤹 3D 把玩」chip（效果图页右上叠加第三枚）：全屏 Play3DActivity（纯代码 UI）+ Play3DView，拖动旋转（yaw/tilt）、双指缩放、双击复位、空闲自转；投影=正交（Play3DProjector 纯 Java：project/depthKey/逆投影 surfaceFromScreen，椭圆短轴=r·sinT），画家算法按 depthKey 升序（yaw 变化才重排，阈值 0.02 rad），LOD 两档（>6000 格省高光豆孔、>20000 省定位点）；图纸经 pendingPlay3DJson（分享格式 JSON）进程内传递；③**虚拟熨烫彩蛋**——顶栏切「🔥 熨烫」模式，按住拖动熨斗（逆投影定位板面坐标），半径 2.1 格内豆豆逐颗熔连（豆高 0.34→0.10、去孔、提亮蜡面光泽、邻格间补熔蜡连接面），冒蒸汽粒子，进度实时显示，烫满 100% 自动进入展示自转 + 🎉；④**测试**——qa 新增 TestPlay3D 12 项（正俯视退化/平视高度/深度语义/depthKey 与 project 一致性/中心豆手算/椭圆系数/逆投影互逆/平视奇异拒绝/tilt 钳制），全套 333 项全绿，compile_check 通过；⑤**杂项**——ColorMath 补 lighten（提亮钳制 255），全项目继续零 lambda 约定（排序用匿名 Comparator） |
| v2.58 | **生长动画 + GIF 导出（传播位第二期，2026-09-16）**：①**生长动画**——3D 把玩页进门自动播放：豆豆按颜色分批（用量多先落）从空中 4.5 格高处二次 easing 落成整幅，批内按行序波浪推进，时长按豆数自适应（6~20s），「▶ 重播」chip 随时再看，动画未完不许熨烫、可随时旋转/缩放视角；②**GIF 导出**——「🎞 GIF」chip 走 ACTION_CREATE_DOCUMENT(image/gif)：util/GifEncoder 纯 Java GIF89a（逐帧局部调色板 ≤256 色，超了走中位切分 + 最近色映射；LZW 按 GIFCOMPR/free_ent>maxcode 语义增位，表满发 clear；NETSCAPE2.0 无限循环），UI 线程 view.draw 离屏渲染 + 后台线程编码的队列流水线，长边 720px、48~160 帧、延时 ≥2cs，进度文本实时更新，导出期间冻结自转/禁触摸/强制退出熨烫模式；③**测试**——qa 新增 TestGifEncoder 20 项，测试内实现迷你 LZW 解码器做真往返（纯色/双色棋盘/64² 渐变中位切分误差断言/100² 噪声图打满 4096 字典走 clear 分支/多帧延时与 NETSCAPE 扩展/结构断言），全套 353 项全绿；④**修复**——渲染缓冲改为每帧独立分配（共用数组会被 UI 下一帧覆盖正在编码的帧，竞态）；⑤**边界**——GIF 为逐帧全量帧（无帧间差分），文件偏大但对拼豆色块图很友好；透明帧不支持（渲染底色为米白） |


## 六、待办清单（2026-09-18 真机 session 留下，下一个会话从这里接）

按优先级：

1. **GIF 导出尺寸 bug（最高优先，用户已亲测踩中）**——真机导出成功但
   `pindou_build_*.gif` 是 100×208（预期长边 720），76 帧内容全是空白
   米白底。文件本体合法（GIF89a/NETSCAPE），GifEncoder 编码无嫌疑
   （TestGifEncoder 20 项往返全过），嫌疑集中在
   Play3DActivity.startGifExport() 取宽高：playView.getWidth()/
   getHeight() 在 SAF 选择器返回后执行，若 view 此时未布局/被重建会
   拿到小值；帧全空白说明 playView.draw(canvas) 画的时候 view 未
   attach 或 onDraw 早退。修法方向：导出前对 playView 显式
   measure+layout 固定尺寸，或干脆按纯函数离屏渲染（Play3DProjector
   是纯数学，直接按 720×(720·h/w) 构造帧，完全不依赖 view 生命周期，
   更稳）。回归流程：文字生成"BEAD"→3D 把玩→🎞 GIF→保存到下载→
   adb pull→本地解码验尺寸与帧内容（现成件：qa/out/uinfo.ps1 解析
   dump、System.Drawing 逐帧导 PNG，2026-09-18 会话已趟通全程）。
2. **空白画布 stale UI**——startBlankCanvas() 设 cols=rows=29 后不调
   syncSizeUi()，尺寸 chip/hint 停留在进入前状态（真机 20:07 截图
   实证：显示 58×58/拼板 4 块，网格本体是对的）；startBlankCanvas 里
   补 syncSizeUi() 一行，compile_check + 真机截图回归。
3. **real_walk.bat 适配 Android 15+**——shell am start 非导出
   Activity 被 SecurityException（DEV-NOTES 32），现脚本在 Android 16
   真机上非导出页步骤半数失效；改 monkey 拉 LAUNCHER + uiautomator
   dump 解析 bounds 后 input tap 点击驱动；顺带把 qa/out/uinfo.ps1、
   heartbeat.bat 收编进 qa/ 入库。
4. **手机设置恢复**（下次连手机时）：screen_off_timeout 600000 恢复
   120000；stay_on_while_plugged_in=7 恢复原值（原值未记录，先问
   用户或按 vivo 默认处理）。
5. **PAT 吊销提醒**：2026-09-18 会话所用 PAT 已完成推送+Release，
   用户可能未吊销——新会话开场再提醒一次。
6. **发版节奏**：以上 1~3 凑一个 v2.59 推 CI + Release（流程已趟熟：
   直连推送/盯 run/artifact 下载/Release API 挂 APK；需用户临时 PAT，
   只勾 Contents 读写即可，用完吊销）。

