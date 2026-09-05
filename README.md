# 照片变拼豆 PindouPhoto

把任何照片变成拼豆图纸的**完全离线**安卓工具。拍下你的猫、你的手绘、你的头像，生成可拼的图纸和豆单。

## 📥 下载安装

**[→ 点此前往 Releases 下载最新 APK](https://github.com/3777166551/pindou-photo/releases/latest)**

下载后直接安装（首次需允许"安装未知来源应用"）。要求 Android 7.0 及以上。

## ✨ 功能

- 🎨 **照片 → 图纸**：4 档通用色板（24/48/90/120 色），Artkal / 漫德 / Perler / Hama 官方色号对照
- 写实 / 抽象两种风格，k-means 取色 + Floyd–Steinberg 抖动，支持圆板
- ✨ **生成质量三档**：清晰轮廓(众数取色治灰边) / 杂色清理(连通域合并) / 精准配色(CIEDE2000)
- 限色数合并：不限 / 12 / 18 / 26 / 40 色贪心合并
- ✂️ **纯本地去背景**（内置 U²-Net 模型，不需要联网）
- 🩹 **去水印**：框选 + 掩码修复
- 📷 **拍照识别图纸**：自动网格检测，旧图纸拍照即可复刻
- 🐾 **内置模板库**：流行表情 / 萌宠动物 / 美食饮料 / 花草节日,176 款(Fluent Emoji 改编,MIT)
- 📋 **豆单**：数量 / 占比 / 约重 / 成本统计 + 豆仓库存 + 缺豆替代建议
- 🧩 **拼豆辅助模式**：逐色定位 + 点格/滑动刷选标记 + 定位未拼 + 逐色剩余/今日打卡 + **打卡日历** + 屏幕常亮
- 🖌 画笔涂改 / 油漆桶填充 / 全局替换色 / 左右镜像绘画 / 空白画布 / 文字图纸 / 项目存档 / 40 步撤销
- 🛒 **合并采购单**：勾选几个项目，把颜色用量汇总成一张采购单，可导出 CSV
- 📤 导出 PNG / PDF（PDF 含封面页、材料清单页、页码导航）
- 🫥 **透明导入**：抠好图的透明 PNG 直接进流水线，透明格子自动留空不摆豆
- 🎨 **AI 风格化**：内置 AnimeGANv3 吉卜力模型（离线推理），照片秒变动画风再转图纸
- 📦 **开放图纸格式**：一键导出/导入 `.json` 图纸文件，发给朋友或跨软件使用（规范见 [docs/SHARE-FORMAT.md](docs/SHARE-FORMAT.md)）

> 完整功能清单、算法说明与构建文档见 **[docs/完整文档.md](docs/完整文档.md)**

## 🔒 隐私

**无网络权限，不联网、不收集任何数据。** 所有处理都在你的手机上完成。

完整隐私政策(中英双语): **https://3777166551.github.io/pindou-photo/privacy.html**

## 开源组件

- 模板素材：[Kenney](https://kenney.nl)（CC0 公共领域）
- 去背景模型：[U²-Net](https://github.com/xuebinqin/U-2-Net)（Apache 2.0）
- 推理引擎：[ONNX Runtime](https://github.com/microsoft/onnxruntime)（MIT）

完整声明见 [THIRD_PARTY.md](THIRD_PARTY.md)。

## 🛠 从源码构建

零 Gradle 依赖，双击 `build_apk.bat` 即可出 APK（签名口令从环境变量 `PINDOU_KS_PASS` 读取，首次构建自动生成测试签名）。需要本地自备 `tools\` 目录（JDK17 + Android build-tools 34 + ONNX Runtime AAR），详细步骤见 [docs/完整文档.md](docs/完整文档.md)。也可以直接用 Android Studio 打开（标准 Gradle 工程）。

HarmonyOS 移植工程见 [harmony/](harmony/PORTING.md)。

## 📄 开源协议与合规

本项目以 **GNU AGPL-3.0** 协议开源（完整文本见 [LICENSE](LICENSE)）。欢迎 Issue、PR 与模板投稿。

- **第三方合规**：随 APP 分发的模型、素材及构建工具均按其原许可证使用，出处与许可证逐项列于 [THIRD_PARTY.md](THIRD_PARTY.md)（u2netp 模型 Apache-2.0、ONNX Runtime MIT、Kenney 素材 CC0）
- **独立实现声明**：本仓库全部源码为独立实现，未复制任何第三方项目的源码；算法灵感来源（如 Zippland/perler-beads 的公开算法文档）已在 THIRD_PARTY.md 致谢
- **用户数据**：零网络权限，不收集、不上传任何数据

## ⚠️ 免责声明

本软件按"现状"提供，不含任何担保；图纸色号与用量为算法估算，实物请以官方色卡为准。
使用即表示你已阅读并同意 [DISCLAIMER.md](DISCLAIMER.md) 与 [LICENSE](LICENSE) 的全部条款。

---

v2.33 · 完全免费 · 无广告
