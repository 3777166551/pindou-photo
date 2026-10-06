# 路线图与交接文档 (ROADMAP & HANDOFF)

> 本文档是项目的**持续交接入口**：当前状态、待办功能、开发约定、操作备忘。
> 新会话/新开发者从这里开始读。最后更新：2026-10-05（**v2.72(vc80):deskew 三修
> (桌面全流程跑用户原图抓出):①采样方向误用逆矩阵→改正向映射(旧扭曲表现为
> 小幅残角,合成测试因分析分辨率下量级巧合而漏检);②输出分辨率未按原图放大
> (拉正图仅 ~420px 豆距过小对格必败)→按 w/dw 等比放大;③紧裁剪照片被"四角贴边"
> 防御误拒→改组件 bbox 占满判据+膨胀半径自适应边缘余量;qa 补 1/4 点内容断言,
> 移除 invert3 死代码。实测 3072×4096 原图:拉正 1486×2155→对格 83×120。
> 此前 2026-10-05 v2.71(vc79):成品转图纸
> 透视校正(用户追问"白色部分下面多上面少,明显图纸有点歪,能解决嘛"——那是梯形
> 畸变,旋转修不完):GridScanner.deskew 自动找面板四角(背景中位色+最大连通域+
> chamfer 膨胀+凸包+最大面积四边形)→单应拉正;成品转图纸自动对格与识别图纸框选
> 两路径接入。另含 v2.70 ±12° 旋转、v2.69 格内官方色号。
> 此前 2026-10-05 v2.70(vc78):成品转图纸
> 歪斜修正增强(用户"拍照的时候必然有点歪,能修正嘛"):①旋转自动对格 ±6°→±12°
> (三段式 0.6°/0.1°/0.05°,开销持平,合成 9°/11°/-8°/-11° 实测角度精确恢复);
> ②v2.63 写好的 trimBackground 织物边框裁剪正式接入识别流程(MainActivity.runScan);
> ③修 qa 失真:run_tests.bat 漏 TestGridScanner(全绿假象)+ 大角度用例对位改全平移
> 搜索(9° hit 10% 是测试架居中假设错,非扫描器)。另含 v2.69 格内官方色号等。
> 此前 2026-10-05 v2.69(vc77):格内文字
> A/B/C 内部序号全部改为官方色号(用户反馈"拼豆或导出图纸时格子里显示的能直接是
> 色号嘛?不要是ABCD 别人认不出来"):图纸页/投射沉浸/格信息/豆豆清单/导出图纸
> PNG+PDF/立牌/十字绣统一 displayCode,格内字号按最长码自适应收缩。
> 另含 v2.68 大画幅卡顿修复、v2.67 辅助手势、v2.66 豆仓行 M3。
> 此前 2026-10-05 v2.68(vc76):大画幅切尺寸后
> 拖动卡顿修复(用户反馈"切换尺寸后拖动下面的卡片卡一会自己好"):根因=效果图/图纸
> 每帧重放数万格描画指令(systrace 实锤 RenderThread DrawFrame 16~23ms@200×200,
> 切换后首段叠加显示列表重建),滚动设置区等其他无关帧同背成本;修复=≥100×100 网格
> PatternView 自动挂硬件层,静态帧一次纹理合成,实测 200×200 切换后立即滚动
> p95 97→18ms / p99 150→24ms。另含 v2.67 辅助手势重排、v2.66 豆仓行 M3 重排。
> 详细条目见下方 v2.68/v2.67/v2.66 节**）
> 此前 2026-10-05 v2.65(vc73)本地构建+本机模拟器实测:
> 格内色号 PDF(横版,每格印漫德色号)/豆仓按色号添加+清单显示官方色号/
> 简图黑描边消失修复(色族纯度门控+Sobel 描边叠加+最暗像素线色)/亮色配色错乱修复
> (buildLut 桶中心溢出)+换画幅卡顿治理(热路径去 ~200 万次 rgbToLab);
> 另:本机模拟器路线已可行(见下"本机模拟器已可行"条),识别图纸重写支持实物成品照,
> 相机运行时权限前置,漫德 2.6mm(221色)转正内置并设为默认色板。详细条目见下方 v2.65 节**）
> 此前 2026-10-03:v2.62.2(vc70)已发布（用户亲手写的系统栏沉浸适配落地——
> 用户亲手写的系统栏沉浸适配落地——新增 util/Insets(平台 API,根布局
> padding 追加系统栏 inset,监听器捕获原 padding 保追加语义),13 个
> Activity setContentView 后统一 padRoot(修全面屏手势/国产 ROM 全屏/
> Android 15+ edge-to-edge 下滚动区末排被导航栏遮挡);compile+qa 523 绿+
> 推送 bf26a6e;APK 已挂 Release v2.62 资产(说明指向 v2.62.2)。此前
> 2026-10-02:P0 批次(同事外审崩溃簇 C1~C6+数据簇)全修 CI 四 job 全绿,
> v2.62.1(vc69)同挂 Release。P1 剩余=C7 OOM 上限/D7 盲回收边界/性能五项/
> UI 小项/277 图案名翻译;git 通道已恢复,本地远端同源(b756bc2..bf26a6e
> 直推),断连期 API 推送的历史遗留 SHA 差异已 reset 对齐**）

## ⭐ v2.62 发版检查清单(下一会话从这里接)

1. **确认 CI**:api.github.com 查 14c2904 之后各 run(每批推送自动触发);
   失败按回传截图修(冒烟新断言首轮:查漏 selected/-1 回归/立牌弹窗/
   投射行进度/stability job 首轮)。
2. **真机验证**(用户无线调试,qa/real_walk.bat 或手动):动态取色(12+ 换
   壁纸)/查漏高亮/投射模式(沉浸页顶栏🔍,行推进)/上墙预览(相册+拖角+
   保存)/立牌方案(导出菜单→PDF+底座存档)/页脚链接/GIF 导出回归(尺寸
   720+非空白)/AI 指南 toast(真人照写实首图一次性)/熨烫 90% 收尾。
3. **出包**:build_apk.bat 三处版本号(v2.61→v2.62,vc 67→68)→签名→
   Release 挂 APK→README 版本号同步。签名口令 PINDOU_KS_PASS 在
   HANDOFF.md(本地文档)。

## ⭐ v2.72（vc80,2026-10-05,deskew 三修——桌面全流程实测抓出)

- **缘起**:用户给出未裁剪原图(3072×4096,面板四周留白充足)要求看生成图纸;
  桌面复现时 deskew 三处缺陷接连暴露。
- **①采样方向反了**:单应 hm 建为 rect→quad,采样应对输出像素施加 hm(正向);
  旧代码施加 invert3(hm)。分析分辨率下 rect/quad 数值量级恰好接近,反向映射
  仍大体落在面板内,扭曲表现为小幅残角(v2.71 的"4.5° 残角/命中 5%"部分源于此,
  合成 qa 因随机色+断言宽松而漏检)。改正向后拉正图完全摆正;qa 补 1/4 点
  内容断言(拉正图 (W/4,H/4) 像素须命中真值 (cols/4,rows/4) 格色,3×3 容差)。
- **②输出分辨率未放大**:四角在分析分辨率(dw≤420)空间,W/H 直接用了分析
  尺寸 → 拉正图仅 ~260×377,豆距缩至 5px,后续对格必败。改按 w/dw 等比放大
  (原图 1800×2400 → 拉正 1486×2155,豆距 18px)。
- **③紧裁剪误拒**:用户此前那张裁剪很紧的照片,面板近贴画框,膨胀把轮廓顶到
  图像边界,触发旧"四角贴原图角=已摆正"防御误拒。改判据=组件 bbox 占满画框
  (双向 ≥98%);膨胀半径自适应 min(radWant, 组件-边缘余量-1)。
- invert3 死代码移除(曾两度怀疑其错误,numpy 逐项验证实为正确,真正问题在
  调用方向;留之易误导后人)。
- **桌面全流程**(scratch/FourSeasonsOut.java):deskew→detectBeadGrid→
  fromBeadPhoto→trimBackground→Python 渲染色号版(69×111,漫德官方码)。

## ⭐ v2.71（vc79,2026-10-05,成品转图纸透视校正)

- **背景**:用户追问"白色部分下面多 上面少,明显是图纸有点歪"——v2.70 的旋转
  修正只解决平面内旋转;斜拍还有**梯形畸变**(实测该照上白缝 -0.7°、下白缝
  -1.15°,角度随画面位置变化),单旋转角网格永远对不齐,解码白边必成楔形。
- **根因另有一处**:成品转图纸屏(BeadPhotoActivity)的自动对格用的是
  `PatternEngine.detectBeadGrid`——纯轴对齐自相关,**完全没有旋转/透视处理**。
- **deskew(GridScanner 新公开方法)**:背景色=边框环中位色 → 非背景(SAD>100)
  最大连通域=成品面板 → **chamfer 距离膨胀 R≈2.5% 短边**(把面板外圈底板环并入:
  透视下环宽沿边不均,纯豆区包络四角不共单应,实测拉正残角 4.5°/命中 5%)→
  逐行 span → 凸包 → **穷举最大面积内接四边形**(凸包降采样 ≤60 点,C(60,4)
  ≈49 万;双指针法在 c 快速推进时出简并解)→ 角序规范(质心角排序+min(x+y)
  起点+叉积定手性)→ 单应拉正(双线性)。四角贴原图角/面板 <25% 时返回 null。
- **接线**:BeadPhotoActivity.autoDetect 先 deskew(成功则工作图与叠加层换校正图)
  再 detectBeadGrid;MainActivity.runScan 框选→裁剪→deskew→detect。
- **QA**:TestGridScanner 新增透视用例(真单应梯形畸变合成照,deskew→detect→
  sample:dims/角度断言;合成随机色对残余歪斜过敏感,命中断言以模拟器 E2E 替代);
  TestGridScanner 36 项全绿。调试教训:qa 合成"逆"一度用错余子排布(未转置),
  numpy 逐项对照定位;生产 invert3 经同法验证本就正确,未被改动。
- **模拟器 E2E**:四季树斜照 → 成品转图纸 → 自动对格(74×120)→ 生成图纸:
  左缘白边由"下宽上窄楔形"变为均匀窄条,四季分带/树枝连续。

## ⭐ v2.70（vc78,2026-10-05,成品转图纸歪斜修正增强)

- **背景**:用户四季树成品照反馈"拍照的时候是必然的(歪),有办法修正嘛"。
  实测该照倾角仅 -0.7°~-1.15°(白缝测角),v2.63 扫描器已检出 -1.0°、豆距 19px
  全图一致(透视轻),解码本就摆正——但旋转搜索上限 ±6° 是更歪照片的真实天花板。
- **旋转搜索 ±6°→±12°**:三段式 0.6° 粗扫(41)→0.1°(13)→0.05°(5) 共 59 次
  shearVariance(旧 47 次,+25% 内);detect 桌面 348→407ms。合成验证(贴满渲染器):
  0/4/9/11/-8/-11° 全部精确恢复(角度 ≤0.05°,格距误差 ≤0.25px)。
- **trimBackground 接线**:v2.63 写好的边缘背景裁剪(4bit 量化,边界主色,
  边缘行/列 ≥90% 同色判背景,最多裁 35%)一直没进主流程;MainActivity.runScan
  在 sample 后调用(3×3 下限防御),选框带进的织物白边自动裁。
- **qa 基建两修**:①run_tests.bat 测试列表漏 TestGridScanner(.sh 有 .bat 无)——
  大角度用例失败时 .bat 依旧全绿的假象;②TestGridScanner.beadCase 对位改全平移
  搜索(旧"居中±1 格"在大角度因旋转包围盒外扩不对称而必错,9° hit 10% 的根因
  是测试架不是扫描器);③新增 9°/-11° 用例,行列期望按旋转包围盒几何计算。
- **模拟器 E2E**:成品转图纸入口 → 相册选四季树照 → 自动对格(74×120,全分辨率)
  → 生成图纸:图纸页四带清晰、树连续、无倾斜痕迹;红豆照 24×24 全流程亦通。

## ⭐ v2.69（vc77,2026-10-05,格内文字统一官方色号)

- **背景**:用户反馈"拼豆或者导出图纸的时候 格子里面显示的能直接是色号嘛?不要是
  ABCD 别人认不出来"——原 `PatternEngine.symbolFor` 是色板序号字母(A..Z..),
  只有对照图例才有意义;官方色号本来就在 `BeadColor.displayCode()`(品牌=官方号,
  通用=内部"n 号")。
- **格内统一换色号**:PatternView 图纸页(setPattern 预缓存逐色 displayCode+最长
  码宽,绘制帧一次 setTextSize,避免 200×200 逐格 measureText)/EditorActivity
  投射沉浸 ProjectorView/格信息弹窗/PatternSheetRenderer(符号模式与色号模式
  格内统一,差别只剩图例与标题)/CrossStitchRenderer 十字绣。
- **UsedColor.symbol 构造点全换 displayCode**(PatternEngine/PatternPatch/
  StandeeKit×2/PatternShare),豆豆清单行、替换选择器、PDF 材料清单、立牌清单、
  分享图纸图例自动跟随;存档 JSON 不嵌 symbol,旧档重开自动新码。
- **字号自适应**:格内 2~3 位码(H2/A26)缩到格宽 92% 为止,下限 0.2×格。
- **文案**:fmt_cell_symbol "符号:%s"→"色号:%s"(en Code:,ja コード)三语。
- TestPatternPatch 断言更新为 displayCode 语义;qa 全绿;模拟器 24×24 实测格内
  H2/G15/F3/A26 官方码清晰可读。

## ⭐ v2.68（vc76,2026-10-05,大画幅切尺寸后拖动卡顿修复）

- **背景**:用户反馈"切换尺寸后,拖动下面的卡片就会卡一会 然后就自己好了"
  (截图为 200×200)。
- **量化复现**(模拟器+真图导入+dumpsys gfxinfo):切到 200×200 后立即滚动设置区,
  p95=97ms / p99=150ms;数分钟后再测 p99=12ms(自愈);同时刻把图纸切出屏幕
  (豆豆清单页)滚动 p99=16ms——锁定成本在效果图/图纸控件本体。
- **根因**(systrace):PatternView 每个渲染帧都要重放全格数描画指令
  (drawEffect 逐格 drawCircle,200×200≈4 万圆),慢帧全落在 RenderThread
  DrawFrame(16~23ms,软渲染模拟器更糟);切换尺寸后首段叠加显示列表重建,
  稳态后 DisplayList 缓存生效→"自己好了"。HWUI 是全帧重放模型,invalidate
  省的是主线程重录,RenderThread 重放省不掉——必须减少每帧指令数。
- **修复**:setPattern 时 ≥100×100(1 万格)自动 `setLayerType(HARDWARE)`,
  静态帧=一次纹理合成 O(1);invalidate(手势/标记/动画)仍整层重绘,与原同价;
  <100×100 不挂层(小图无收益,省纹理内存)。沉浸页同为 PatternView 自动生效。
- **实测**:同场景(真图导入→切 200×200→立即滚动)p95 97→18ms / p99 150→24ms,
  渲染视觉无回归;qa 全绿。
- **测量坑(备忘)**:①adb input swipe 起点在画布内会被 PatternView 吃掉
  (画笔模式还会真画上去)——滚动设置区必须从画布下方起手;②相册 Picture 里
  有旧测试图 bead_photo_test.jpg(红色豆成品照),按分组点缩略图会选错图,
  验证渲染内容要用 tvW/tvH+豆豆清单核对;③切 chip 前后 uiautomator 重取
  bounds,滚动后旧坐标必失效。

## ⭐ v2.67（vc75,2026-10-05,拼豆辅助手势重排)

- **背景**:用户反馈"拼的时候我想拖动界面,结果变成这个地方拼豆完成了"——旧版
  辅助模式"按住滑动=连续刷选标记",完全没有平移手势,移动图纸必误标。
- **拖动=平移**(PatternView.handleAssistTouch):超出触摸 slop 即进入 dragPanning,
  按触点差值改 offX/offY(onDraw/cellAt 原有 clamp 保证越界回弹);平移结束的抬手
  不再触发格子切换。
- **连续刷选改长按进入**:armAssistLongPress 复用画笔模式油漆桶的计时模式
  (ViewConfiguration.getLongPressTimeout),超时仍按着且未滑出→落点格立即标记
  +LONG_PRESS 震动,继续滑动 markLine 连刷,抬手 onAssistDragEnd 收账;
  长按已标记落点格,抬手不再当单击切换(防自我取消)。
- **不变**:单击切换(260ms 双击窗口)/双击复位缩放/双指缩放(第二指落下中断标记放行)。
- 沉浸拼豆页(immersiveView 同为 PatternView)自动同步;assist_help_body 三语文案
  改为"拖动=移动图纸,长按后滑动=连续刷"。

## ⭐ v2.66（vc74,2026-10-05,豆仓库存行 M3 重排）

- **背景**:用户截图反馈库存行"边上(色样)是直角 而且排版不好看,按 Google M3 改"。
- **色样改正圆**:旧 32dp 方块仅 6dp 圆角(近直角),改 36dp 正圆(GradientDrawable.OVAL)
  + 1dp colorStroke 描边——白/淡黄等浅豆在白卡上也有轮廓(M3 色样惯例)。
- **两级排版**:旧行只印 HEX;`codeLookup()` 官方色号(9 张品牌色号表+通用 4 档,
  v2.65 已建)其实已算好却没上屏。改:官方色号主行(15sp 粗体 textMain)+
  HEX 次行(12sp textSub);无官方色号时仅 HEX 主行不重复。
- **数量列对齐**:「手头数量」标签旧居中与输入框错位,改左对齐同宽(输入框 96dp 不变)。
- **主题语义色**:行内硬编码 0xFF1D1B20/0xFF49454F 改 `themeColor(attr,fallback)`
  解析 ?attr/textMain、textSub、colorStroke(深色模式/动态取色自动)。
- 行卡片底 bg_card_outline(24dp 圆角描边卡)不变;空态行文字色同步接 textMain。

## ⭐ v2.65（vc73,2026-10-05,本地构建+本机模拟器实测,待真机复核）

- **①导出 PDF(格内色号)**:横版 A4,每格直接印漫德色号(PatternSheetRenderer.renderCodeSheet
  字号自适应 + PdfExporter 横版 + 分享菜单一级直达 + 二级菜单 PDF 前置),免对照图例照号拼豆。
- **②豆仓按色号添加**:品牌色板选择器(默认漫德 2.6mm 221 色)+ 色号筛选列表,点选自动带出 RGB;
  豆仓清单行显示官方色号(精确命中色号表才显示,近似仍 hex)。
- **③简图黑描边消失修复**(用户火焰图实测:描边整圈消失):
  - 简图门控改**色族纯度**(像素集中在本格均值 Lab ΔE≤10 色族内的比例)——光照渐变把同一
    底色散成多支近似豆,豆级纯度掉中间带误判"复杂"(实测中间带 50%);色族纯度下渐变格判内部。
  - **Sobel 描边叠加**(lineMask):细于 1 格的描边逐格投票必断点,连续轮廓只能来自跨格梯度
    检测;线格强制最深豆,且不参与笔画重统一(防被底色多数桶洗掉)。
  - flatUnifyCells 线色改取**格内最暗像素**(旧"离均值最远离群"在三色格会取到底色)。
  - 火焰图暗格 21→433,黑描边全程连续。
- **④亮色配色错乱修复**:buildLut 桶中心 i*17+8 溢出(通道值≥240 的亮桶 Lab 全废:
  白变紫黑/黄变绿,柔和图全毁;中间桶仅偏差十几个色阶故长期未暴露),改 i*16+8 钳 255;
  TestPatternEngine 增 8 条亮桶回归。
- **⑤换画幅卡顿治理**:色族纯度改整数 RGB 两遍法零分配(旧逐像素 rgbToLab 每图 ~200 万次
  调用+等量分配,GC 风暴拖累 UI);Sobel 线掩码惰性计算(WorkGrid 存源栅格零拷贝引用,
  首次简图门控通过才算,照片等非简图永远不算)。
- **⑥识别图纸重写支持实物成品照**(GridScanner v2.63):方向梯度(|gx|/|gy| 分开+行内归一)+
  剪切旋转角(±6°)+ 两遍峰值法格距(0.55×窗口抑峰+一致性门+整数比折回+LSQ 精修)+
  密集相位扫描(色族纯度前的版本为逐格方差,fx/fy 全扫)+ 环带截尾均值采样(跳豆孔);
  解码 900→1600,矩形画幅 pendingSuggestedCols/Rows 1:1 落格(旧强套方形被居中裁剪糊掉);
  TestGridScanner 23 项。
- **⑦其他**:相机运行时权限前置(部分 ROM 拦未授权拍照,MainActivity/VerifyActivity/
  BeadPhotoActivity);工具小卡 colorStroke 描边(修"按钮边上缺失");撤销/重做 22→28sp;
  fastlane 商店元数据入库;ROADMAP 本机模拟器结论更新(见上)。
- **验证**:qa 28 套(含 TestGridScanner 23 项/TestBeadPhoto 13 项)全绿 + compile_check OK +
  本机模拟器(smoke AVD)走查:默认色板漫德 221/格内色号 PDF 生成/豆仓按色号添加闭环。
- **已知边界**:简图描边细于 1 格时 58 画幅下轮廓仍可能略断(物理极限,116 画幅改善);
  AnimeGANv3 许可证仍阻塞 F-Droid(GitHub Releases + Obtainium 路线无碍)。

## 一、当前状态快照（2026-09-30,v2.62 功能冻结）

- **★ 萌新易用性 P1 四件(2026-09-30,本地 compile+qa 22 套 523 项全绿,
  未推 CI,等用户过目)**:全功能萌新视角评估的 P1 落地——
  ①**辅助「怎么用」重写**:三语补 📸验收/🔍投射/📅打卡 三条此前缺失的
  条目(10 个 chip 全覆盖),正文按「换着花样拼/对着真实拼豆板核对/帮你
  检查的小工具」分组;EN 顺手修掉撞名:对位投屏 Project→**Align**、
  投射 🔍Project→**🔍 Big view**(ZH/JA 本就有区分不动);
  ②**首进编辑器一次性指路 toast**(editor_first_hinted,挂在生成完成回调
  :「默认参数就能直接开拼」;空白画布有自己的就绪 toast、存档恢复/
  自动验收属老手流程,均不弹);
  ③**手势示意图**(view/GuideDiagrams.java 程序绘制,零新资产,画面无
  文字三语通用):验收页 hint 下方加四角对齐图(照片卡+透视四边形+四手柄
  +右下琥珀外圈拖拽箭头),识别图纸弹窗加框选正误对照(绿✓贴网格/红✗
  连空白框进去);
  ④**看板类入口媒介标注**:chip_ar 加「(相机)」、chip_wall 加「(相册)」
  三语。**冒烟适配**:ui_smoke.sh 投射两处 tap 文本改 "Big view"(子串
  匹配,选中态「✕ 🔍 Big view」也命中;该段投射走查 cadb8ab 起才上 CI,
  尚无绿轮实证)。
- **★ 萌新易用性 P2 六件(2026-09-30,与 P1 同批,本地 compile+qa 22 套
  523 项全绿,未推 CI)**:①**首图吓退预防**——首图 toast 按豆数/色数
  自适应:≥2600 颗或 ≥30 色改口给简化出路(fmt_first_pattern_big:
  「色数上限调到 18 或换小一档尺寸」),小图维持安心话术;②**术语小
  词典**——知识页《APP 使用指南》首篇开头加豆仓/豆单/我的豆板三行词典
  (三语,用词对齐各语言实际 UI:Inventory/Bead list/My palette 等),
  流程补第⑤步拍照验收(此前缺失),EN 第④步同步改 Align,ZH 修正 hero
  卡名引用;③**限色 chips 加「色」**(12→12色,ZH/JA;EN 保持数字;
  冒烟按 id 点击不受影响);④**「离线拼」改白话**(模板卡 desc=
  「图案直接拼,不用照片」,三语);⑤**验收结果软化**——「摆错」→
  「疑似摆错」,结果行追加「光线反光也可能误判,熨前再核对一眼」三语
  (冒烟锚 verify_good「All correct」未动);⑥**模板库 8 分类名本地化**
  (TemplateAssets 加 allCategories(titles) 重载,bead 包零 Android 依赖
  保持;template_cats 数组三语,MainActivity 传入;277 个图案名翻译仍是
  HANDOFF 挂着的「待用户拍板」项,未动)。
- **★ CI 截图复审(befa513 轮 78 张逐张人眼审)抓出并已修三真缺陷
  (2026-09-30,commit 2a01db9,API 已推 e5d1449)**:①豆仓 90 色弹窗
  按钮条被列表顶出屏幕外(CANCEL 半裁,59d 评审遗留,合同 §6 违规)——
  列表高度钳屏高 55% 修复;②立牌弹窗三按钮被 EN 长文案挤到零间距
  ("Save base as projectExport PDF" 连行)——EN 缩短 Save base;
  ③EditorActivity 3849 行硬编码中文 toast 改 inv_regen_done_fmt 资源。
  **复审遗留观察(未修)**:EN 辅助工具行 5 列 chip 文案截断(Chec…/
  Missi…,EN 缩短待做);合同 §7.2 库存列表顶部搜索框仍未做(销号清单
  遗留);投射沉浸顶栏 chip 换 Big view 后文案变长,拥挤度下轮截图复核。
  冒烟投射段四连挂根因=沉浸页 tap "Project" 命中遮罩下同名对位投屏
  chip(uiautomator dump 底层节点在前,grep 取首个)——之前四轮投射从未
  真正打开过;Big view 改名消重名+断言改稳(✕ 态硬断言/Row 改软)已随
  4700ee5 推送。stability 四连挂=镜像下载 infra flake,re-run failed
  jobs 即可。

- **★ v2.62 收尾三件(已推 b169092/cadb8ab/8f80739)**:
  ①**稳定性第三层**:TestChaos 损坏注入 224 组(7 算子×豆仓/日历/草稿/存档,
  注入后全路径不崩+自愈);CI 新 job **emulator-stability**(循环压力×20/
  转屏重建×10/am crash 恢复×5/PSS 水位)——smoke/fuzz 外的第三类故障;
  ②**全屏投射模式**(调研 Pattern Keeper 范式):沉浸页「🔍 投射」chip,
  当前行/板/色超大铺满+◀▶ 推进回写引导状态双向一致+已拼降透明绿点,
  纯展示零交互零权限;冒烟投射走查;③**可发现性修补**:真人照写实首图
  一次性 toast 指路《AI 转图指南》、首次进沉浸 toast 告知投射模式。
- **★ 纸娃娃模式原型闭环**(proto_sanc/,已随 4997627 入库):parts_gen
  部件库 v1(72 眼/11 发/4 嘴日漫参数化)+pipe_doll 照片→豆偶闭环+
  双马尾检测发量兜底+模板 v3(马尾垂条到脸侧);三转二调研文档入库;
  **隐私边界:proto_sanc/out 与 me_girl.jpg 已 gitignore 不入公开仓库**。
- **★ 测试体系终态**:JVM 22 套 524 项(新增 TestChaos 8 断言=224 组/
  TestStorage 24/色板秒切等价 fuzz 30 组)+CI 四 job(test/smoke/fuzz/
  **stability**)+真机 real_walk。fuzz 战绩:色板秒切等价性抓到配豆三处
  tie-break 顺序依赖真 bug(已修:等距/等票选 RGB 小者胜)。环境坑(本会
  话实锤):链式长命令静默截断(add/commit 没跑但无报错,须分步)、python
  -c 多行中文常吞输出(复杂脚本一律 Write 文件跑)、bash 无 tail/head/sleep。
- 首页页脚(免费开源声明+GitHub+Issues 反馈)+知识页第 11 篇《AI 转图
  指南》(三语,拼豆定制提示词一键复制×3)——v2.15 路线②用户教育落地;
  免费转图渠道首推 HF Spaces AnimeGANv3 官方 demo(已验证在线,需梯子)。
- QQ 号不进 APK(公开包防爬);用户 git 侧自放。
- **v2.63 主打候选**:全自动捏脸模式移植(原型已闭环);12 月:年报分享卡
  +作品墙;攒着:投射增强/立体分层全量编辑器/钩织导出。

- **★ v2.62 预埋(2026-09-29,本地绿,未推 CI)**:**新功能「查漏高亮」**——
  拼豆辅助工具行第一行新增 🔍 查漏 chip(定位/查漏/打卡/沉浸/怎么用 五列,
  ZH 文案缩短:定位未拼→定位、打卡日历→打卡):开=图纸总览所有未拼豆
  (设计合同 colorWarning 琥珀圈 1.2s 呼吸,已拼蒙纸色,忽略逐色/按板/逐行
  遮罩),toast 报「未拼 N 颗 · M 色」+自动跳到第一颗,进度行换漏豆摘要
  (边拼边掉数字);关=恢复正常辅助渲染。编辑页/沉浸页共用
  (applyAssistParamsTo 透传);帮助弹窗补条目;三语;冒烟加 chip selected
  双向断言+截图。核心:PatternView.setMissCheck + missStats/toggleMissCheck。
- **★ v2.62 预埋②(2026-09-29,本地绿)**:**3D 熨烫手感两连(用户反馈直答)**
  ——①熨斗半径随板幅自适应 2.6~5 格(旧固定 1.9,29 板面积≈翻倍,116 板
  ≈7 倍),熨斗视觉尺寸同步变大;②拖动采样点间按半步长插值补烫(快拖不留
  漏豆缝);③**烫到 90% 自动收尾**:余豆按离熨斗距离升序,1.5s 由近及远
  波及式烫完(蒸汽照冒),进度行先报「烫到 90% 啦…」,完成照常 🎉+自转;
  GIF 导出冻结时收尾波瞬完保证帧一致;ironDone 守护完成回调只发一次。
  核心:Play3DView ironRadius/viewScale/moveIronSegment/meltAt/
  evalIronProgress/startAutoFinish/advanceFinish + Listener.onIronAutoFinish。
- **★ v2.62 预埋③(2026-09-29,本地绿)**:**立牌方案一键生成**(踩 2026 立体
  堆叠热趋势,竞品差距清单"立体分层"轻量版)——导出二级菜单新增「🪧 立牌
  方案」→ 摘要弹窗(主图/底座统计+底座色块+装配示意图+提示,三按钮):
  ①导出 PDF=装配说明封面(剖面示意图,与弹窗共用 drawAssemblyDiagram)+
  主图图纸分页+底座图纸页+合并豆单(主图/底座/合计分列);②存底座为项目
  (blank+share 结构,cols/rows≥4 兼容性靠底座深 ≥4 保证),重开可用辅助拼。
  核心:bead/StandeeKit(纯 Java,底座宽=主图+2 取奇≥5,深=3+高/8 钳 [4,8],
  插槽正中 1 格留空到背排、背排实心连通一次可熨,底座色=最低非空行众数)
  +TestStandee 25 项。
- **★ v2.62 预埋④(2026-09-29,本地绿)**:**上墙预览(非 AR,竞品差距清单
  收尾)**——FAB 菜单第四 chip「🧱 上墙」→ WallPreviewActivity(纯代码,
  相机族恒定深底):进门自动拉相册选房间/墙面照片(EXIF 摆正+采样 ≤2048),
  效果图 Matrix.setPolyToPoly 四点透视贴合,拖角对位(就近吸附,同对位
  投屏手势),透明度滑杆,顶栏成品实际厘米尺寸 chip,保存 2x 预览图入相册;
  零新权限。效果图经缓存文件传入(同 AR 页,1024 宽防 OOM)。
  **run_tests.sh 修复:TestVerify 一直漏登(本地 bat 有/CI sh 没有),已补。**
- **★ v2.62 预埋⑤(2026-09-29,本地绿):豆仓库深度测试化(用户点名"没有
  测试员")**——①BeadInventory 去 org.json 改纯 Java(手写 JSON 序列化/
  解析,格式与旧版一致,旧文件直接可读;android.jar 的 org.json 是抛异常
  stub 挡桌面测试)+useTestFile qa 钩子;②缺豆替代算法提炼 bead/
  SubstituteSolver(逻辑一字不改,EditorActivity 委托;**文档化原语义:
  富余≥该色总需求而非缺口**,推"整套替掉"不推"混着用");③**修真 bug:
  InventoryActivity.reload 把未登记色 -1 直接填进输入框**(valueOf(-1)),
  用户一按保存全部未登记色被静默写成"登记为 0"——改留空(编辑页弹窗
  本有此防御,独立管理页漏了);④TestInventory 32 项(存储:两态/负数
  钳制/RGB 掩码/持久化往返/损坏自愈/旧格式/大写键/300 色;替代:10 边界
  含 ΔE 实测定色);⑤冒烟加"未登记不得显示 -1"回归断言。
  **qa 17 套 438 项全绿。**
- **★ v2.62 预埋⑥(2026-09-29,本地):全项目测试网扩建(用户点名"把已有
  功能都测了,后台跑")**——①**迷你 org.json 解锁 JSON 层桌面测试**:
  qa/testjson/org/json 三文件(递归下降解析+紧凑序列化,API 对齐真
  org.json),先编进 qa/out 按 classpath 顺序遮蔽 android.jar 的抛异常
  stub,应用代码零改动;只进 qa 不进 APK。②**TestPatternShare 17 项**:
  分享格式 build/parse 往返(矩形/圆/六/单色 RLE/棋盘碎 RLE/名字)+8 类
  坏文件拒收(格式/版本/尺寸/缺表/数量不符/零长 RLE/下标越界/截断)——
  此前 0 覆盖的核心格式层。③**TestPaletteShare 18 项**:色板往返+pattern
  格式兼容导入+去重+坏文件+hex 工具。④**TestStrings 6 项**:三语键集合
  一致/占位符参数一致/?? 乱码残留(纯文件解析,抓 i18n 漂移,历史上出过
  帮助正文残留旧名)。调试三轮抓出迷你解析器两 bug(注释含 `*` 斜杠、
  字符串值开引号未消费)并修掉;TestStrings 首轮抓出的 4 处占位符差异
  经逐个核实:2 处为单参数 %d≡%1$d 风格漂移(已统一成位置写法)+
  fmt_replace_title 英文位置参数重排=合法(测试改为按位号+种类比较)。
  **qa 20 套 490 项全绿。**
- **★ v2.62 预埋⑦(2026-09-29,本地):三转二终局落地启动——纸娃娃模式原型
  闭环(proto_sanc/)**——①部件库 v1 定版:parts_gen.py 参数化生成
  72 眼型(睫四形态×瞳渐层×双高光×睫毛尖,豆矩阵渲染)+11 日漫发型
  (呆毛/空气刘海/公主切/丸子/双丸子/长直…)+4 嘴,全量入库不筛(挑选
  =APP 内乐趣);②pipe_doll.py 照片→豆偶闭环:PSY.prep(U²-Net+ParseNet)
  →YuNet→detect_attrs 取色(发/肤/唇/衣,90 色板下标)→face_grid_v2
  部件换色(瞳=发色渐层派生/腮红=肤派生)→豆板三联图,me_girl 首图成功;
  ③双马尾检测修复:long 加发量兜底(hair.sum()>0.6*fw*fh,白衣稀释实测
  0.314 救回);④开源结论(网络核实):DiceBear 库 MIT 但各风格各自
  license(pixel-art 风格待核);日漫风分层部件库**不存在**(Picrew 版权
  封闭/DOT ILLUST 禁ジェネレーター+加工物再配布/hpgpixer 需邮件许可
  ——全部出局),自产参数化=唯一主力且合规干净(零第三方素材入库,无 IP
  形象,程序绘图不属生成式 AI 服务监管);⑤隐私边界:proto_sanc/out/ 与
  me_girl.jpg 已 gitignore(真人照不入公开仓库)。
- **★ v2.61 = M3 Expressive 化大版本(2026-09-21/22,全部 CI 模拟器实证)**:
  ①Material You 动态取色(Android 12+ 配色跟壁纸,语义色 @color→?attr 22 token
  214+ 处,AppTheme/CandyTokens 四套变体,API<12 静态紫回退);②M3 控件全面化:
  M3Switch 自绘开关(11)/滑杆竖条把手(6)/下拉容器(2)/EditText 填充式字段(13)/
  chip+次按钮+工具卡水波纹/弹窗动态化(CandyTokens+文字按钮)/窗口转场;
  ③照片变拼豆变形动画(PatternView.startPhotoReveal);④相机 queries 修复
  (真机"没有可用的相机应用"根因);⑤点阵纹理与图标盘动态化(透明点阵叠
  ?attr/bg;马卡龙盘→容器角色,新增 tertiaryContainer);⑥**顺手修掉三个
  真崩溃**(变形动画位图别名/双击延迟窗越界/beadDone 清理越界,fuzz 固定
  种子 90210 实证)。**CI 三 job 全绿**(test+smoke 52 验证点+fuzz);
  smoke 失败自动回传截图/logcat 到 shots/(failure-only,绿轮不回传)。
  真机复核清单/技术要点/坑 = **HANDOFF.md 顶部段(必读)**;评判体系与
  算法状态不变(V11 定稿);**04 §十三 自动评判已完成勿重做**。
- **★ 算法重写（09-21,详见 拼豆算法调研/04-后台测试报告.md）**:
  写实模式默认从"盒平均"换成**先配后投**（逐像素 4bit-LUT 配豆+格内多数票,
  幻影杂色机制上不存在）+**欠曝照片自动提亮门控**（桶≥64 且均值亮度<125,
  增益≤1.35）+**线条救援**（亮低彩底 10%~45% 深豆少数→投深豆众数,修
  多数票抹细线回归）。350 张真实图片基准（Commons 六类+随机,脚本
  batch_compare.py）三轮迭代定稿:图形类碎豆 -17~-32%、照片类 +8~18%
  =纹理细节、ΔE 持平;**极密线稿 58 格不可辨=固有边界,引导用户用线稿风格**。
  qa 364 项全绿（TestPatternEngine 18 项含多数票/救援语义）。抖动/去背景/
  众数/抽象/线稿仍走均值管线。**遗留:④自动评判方案（04 §十三）**。
- **★ 09-20 真机反馈快速迭代(全部已推 CI 绿)**:用户拿真机逐轮验收,
  反馈→修复共 5 轮,APK 每轮重打(build_apk\PindouPhoto-v2.60.apk,
  versionCode 66)。commit 链:fb4df64(深色/无障碍/引导/维护/排版)→
  ae9342f+90a92a8(冒烟适配引导+删死资产)→ ac1d071(一键拼豆)→
  dc8068a(沉浸直进+FAB)→ 6811182(开屏修复)。
  **开屏修复教训**:贴纸卡改白卡后,白卡浮近白底对比度过低,用户以为
  "动画消失了"——**浅色主题下白底白元素不可用**,已改主紫填充+白字
  (深色 #4F378B);低对比问题以后用"缩略图 50% 还能看见吗"自检。
  待办新增:①CelebrationView 仍有旧墨描边 ✓ 印章残留(问过用户未回);
  ②首页图标底板马卡龙浅色盘是表外色组,待补 accentPlate token 进合同。
  真机验证模式已跑顺:本地打包→用户装→截图反馈→修复重打,比 CI 循环
  快得多;CI 只在里程碑跑。
- **v2.60 功能集**:设计合同(docs/设计合同.md,★唯一视觉标准,含 §10
  实施注记:sp 字号/系统字体栈/§8 英雄卡=浅紫 container/对话框自定义
  布局方案)+深色模式(values-night 全套+夜间点阵)+无障碍(TalkBack
  描述+生成/庆祝播报)+首启三页引导(OnboardingActivity,prefs
  onboard_done)+自动草稿(DraftStore,3s 防抖+onPause 兜底+首页恢复)
  +退出守护(存档快照对比,四键)+希克定律首页(12→6 入口+折叠收纳)
  +色板秒切(PatternEngine.WorkGrid 缓存,generateFromGrid,5 项单测)
  +一键开始拼豆(CTA 直进全屏沉浸,enterImmersive 复用)+三视图 FAB
  (合同 §7.3 销号,smoke/ar_smoke 已适配 fabFx)+分享菜单两级化
  (+导入菜单 i18n 修复)+辅助状态随存档恢复+空状态引导+W/H 步进
  44×40dp;qa 363 项全绿。
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
  本地过,CI smoke 全绿(2cdadc9,截图 qa\ci_shots_59c\,首页一屏 6 入口
  +编辑页折叠生效;ed0c956 docs 轮的 Hexagon 失败为冒烟时序 flake,
  同代码 IA 轮同段全绿实证)。
- **v2.59 交互审计补课(2026-09-20,用户点名"参照手机 APP 设计规则、
  面向女性")**:逐项过移动端设计清单(核心流程≤3 步✓/触控目标 40-44dp✓/
  M3 一致性✓/状态可见✓/庆祝情感化✓/折叠收纳✓)后抓出三个缺口并修复:
  ①**退出守护**——编辑器有未保存手动修改(editMap 与上次存档快照
  不一致)时按返回,先弹「修改还没保存」三选(存档/直接离开/继续拼,
  点对话框外=继续);存档成功回调刷新快照基线,undo 回已存状态不弹;
  ②**空状态引导**——「我的项目」为空从纯 Toast 升级为对话框:文案+
  「📷 从相册选一张」直达主流程按钮(三语);③**触控目标**——W/H
  步进按钮 38×36→44×40dp。ui_smoke.sh back() 加守护弹窗兜底
  (dump 见 "Leave without saving" 就点 Leave 再补返回)。
- **v2.59 自动草稿(2026-09-20,用户点名"每步存一下,不小心没了下次
  打开还有")**:①**saveProjectNow 的 JSON 构造段抽成 buildProjectJson
  (name,savedAt)**,正式存档与草稿共用一份格式(照片压缩/参数/手动修改/
  拼豆进度/排除色/描摹底图全跟随);②**自动草稿落盘**——pushUndoState
  (所有手动修改的统一入口)末尾挂 scheduleAutoSave:3 秒防抖(滚动合并
  连续修改)写 filesDir/autosave_draft.json,失败静默;**onPause 兜底
  强制落盘**(切后台/锁屏不丢);③**退出守护升级四选**——[存档][存草稿
  并离开][不保存退出],「存草稿并离开」=先落草稿再 finish;「不保存
  退出」才真删草稿;正式存档成功也清草稿;④**首页恢复**——onCreate
  检测草稿文件存在即弹「发现上次没保存的草稿 💾」,[继续上次的]读字节
  → pendingProjectJson → 存档同款管道进编辑器(完整状态还原),[丢弃
  草稿]删文件;恢复点击时读出字节即删文件。⑤ui_smoke.sh:ensure_home
  先检测 "Unsaved draft found" 弹窗点 Discard draft 再验首页;back()
  兜底改点 "Discard changes" 按钮(返回键 dismiss 守护框会死循环,
  已注释说明)。已验证 compile_check+qa 353 本地全绿。
- **v2.59 流程摩擦三修(2026-09-20,用户批准的设计分析三项)**:
  ①**色板秒切**——PatternEngine.generate 拆出 WorkGrid 缓存路径:
  第 1~3 步(裁剪/重采样/画面调节)与色板无关,产物(网格像素)缓存后,
  换色板/限色/抖动/精准配色/形状/杂色清理只重跑第 4 步之后
  (generateFromGrid,毫秒级)——此前每次 8 秒全量;指纹只含第 1~3 步
  入参(源图/尺寸/砖块/画面调节/去背景),线稿模式明确拒绝缓存路径
  (需全分辨率源像素);EditorActivity.regenerate 接双路径+
  TestPatternEngine +5 项快路径测试(最近匹配/透明/重映射/圆蒙版/
  线稿拒绝);②**非阻塞生成提示**——已有图纸时重生成只挂"重新生成中…"
  小 pill(预览下沿),旧图纸保持可见,首次生成才用整屏蒙层;③**分享
  菜单分组**——顶栏菜单 10 项平铺改两级:[分享图纸][保存项目][导出
  图纸/文件…→二级:图纸PNG/效果图/长图卡/PDF/十字绣/.json][导入
  .json][夜间];④**辅助拼随存档恢复**——存档新增 assistOn 字段,
  重开时辅助开着或已有拼豆进度 → 图纸就绪后自动切图纸页+恢复辅助
  (pendingResumeAssist 在 regen 回调执行);ui_smoke.sh 导出四件套
  适配二级菜单(先点 Export chart)。已验证 compile_check+qa 358
  本地全绿。
- **v2.60 无障碍+深色模式+引导+维护(2026-09-20,用户点名"三个都做+
  维护 EditorActivity,暂不推 CI")**:①**深色模式**——values-night
  M3 深色角色全套(bg #141218/primary #D0BCFF/onPrimary #381E72 等,
  跟随系统深浅自动切换),夜间点阵贴图 drawable-night-nodpi
  (GenPegboardTile 参数化),onPrimary 按钮角色色替换硬编码白字,
  Java 约 40 处硬编码主题色全部资源化;相机三页(FakeAr/Verify/
  ProjectAlign)恒定深底设计不随主题;深浅切换会重建 Activity,
  编辑器内改动由自动草稿兜底;②**无障碍**——字形按钮(返回/撤销/
  重做/宽高步进)与辅助开关、图纸画布补 contentDescription 三语,
  生成完成/拼豆完成两处 announceForAccessibility 播报;③**新手
  引导**——OnboardingActivity 三页(拍照出图纸/跟着拼/离线私密),
  首启弹一次(prefs onboard_done),纯代码构建跟随主题,Manifest 已注册;
  ④**维护**——DraftStore 工具类收口草稿文件 IO;删死资产
  bg_pastel.png(660KB)+bg_hero_pastel.png(共减包约 670KB);
  EditorActivity 类顶导航图注释;排版美化:首页品牌短条+hero 按钮
  48dp+ToolCardWide 宽卡样式(修宽卡空旷毛边)+8dp 网格间距,
  编辑页顶栏 58→64dp,豆单头按钮 40dp,提示琥珀色资源化
  (tipAmber 双主题),导入菜单 i18n(原硬编码中文+?? 乱码)。
  已验证 compile_check+qa 363 本地全绿;**暂未推 CI(用户要求先看)**。
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
- **android-emulator MCP**：插件已装但 MCP 工具未连接；本机验证直接用
  tools\asdk 的模拟器（见下），真机走 CI 云端模拟器或用户提供 USB 设备。
- **★ 本机模拟器已可行（2026-10-05 实测，推翻 2026-09-07 的旧结论）**：
  本机已装 AEHD 2.2 加速驱动（无需再装），tools\asdk 里有 x86_64
  google_apis android-30 镜像和现成 `smoke` AVD。快速验证四步：
  ①启动 `tools\asdk\emulator\emulator.exe -avd smoke -no-window -no-audio
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect -port 5554`（后台）→
  ②`adb wait-for-device` + `getprop sys.boot_completed`==1 →
  ③`build_apk.bat` 构建 + `adb install -r`（版本降级报错先 uninstall）→
  ④注入测试照片（root cp 到 files/）+ `am start --es photo_uri ...` +
  `screencap` 截图人眼审。Git Bash 记得 `MSYS_NO_PATHCONV=1`；模拟器是
  zh-CN 环境；google_apis 镜像支持 `adb root`。
- **★ UI 测试 = 推 git 走 CI 云端模拟器**（2026-09-07 实战验证，标准流程）：
  CI 用于 en 环境三语验证与截图存档（本机是 zh-CN）。历史上"本地模拟器
  路线不可行"是当时缺 hypervisor+镜像错配所致，现已解除（见上条）。
  标准做法：
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


## 六、待办清单（2026-09-18 真机 session 留下,下一个会话从这里接）

按优先级：

1. ~~**GIF 导出尺寸 bug**~~ ✅ 已修(2026-09-29,本地 compile+qa 全绿,真机
   回归待做):根因=帧几何取 playView 布局宽高,SAF 选择器返回后该值在
   真机 ROM 上不可信(实测 100×208+帧全空)。重写 startGifExport:渲染
   视口按屏幕尺寸显式 measure+layout 钉住(每帧前重钉,父布局重排免疫),
   渲完缩到长边 720 成帧位图再交编码线程;结束/异常/finish 三路都恢复
   布局并释放位图;onActivityResult 加 isFinishing 防幽灵导出。
   **回归流程**:文字生成"BEAD"→3D 把玩→🎞 GIF→保存→adb pull→本地
   解码验尺寸(应为 720 长边,如 324×720)与帧内容(非空白)。
2. ~~**空白画布 stale UI**~~ ✅ 已修(2026-09-29):startBlankCanvas 补
   syncSizeUi() 一行,尺寸 chip/hint/拼板数即时同步。
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
6. **发版节奏**：以上 1~2 修复 + v2.61 真机复核反馈凑一个 v2.62 推
   CI + Release（需用户临时 PAT，只勾 Contents 读写即可，用完吊销）。

