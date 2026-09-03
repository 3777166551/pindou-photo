# 照片变拼豆 PindouPhoto

把任何照片变成拼豆图纸的**完全离线**安卓工具。拍下你的猫、你的手绘、你的头像，生成可拼的图纸和豆单。

## 📥 下载安装

**[→ 点此前往 Releases 下载最新 APK](https://github.com/3777166551/pindou-photo/releases/latest)**

下载后直接安装（首次需允许"安装未知来源应用"）。要求 Android 7.0 及以上。

## ✨ 功能

- 🎨 **照片 → 图纸**：4 档通用色板（24/48/90/120 色），Artkal / 漫德 / Perler / Hama 官方色号对照
- 写实 / 抽象两种风格，k-means 取色 + Floyd–Steinberg 抖动，支持圆板
- 限色数合并：不限 / 12 / 18 / 26 / 40 色贪心合并
- ✂️ **纯本地去背景**（内置 U²-Net 模型，不需要联网）
- 🩹 **去水印**：框选 + 掩码修复
- 📷 **拍照识别图纸**：自动网格检测，旧图纸拍照即可复刻
- 🐾 **内置模板库**：萌宠动物 30 / 情绪气泡 30 / 迷你地牢 36（Kenney 素材，CC0）
- 📋 **豆单**：数量 / 占比 / 约重 / 成本统计 + 豆仓库存 + 缺豆替代建议
- 🧩 **拼豆辅助模式**：逐色定位 + 点格记进度 + 屏幕常亮，照着拼不迷路
- 🖌 画笔涂改 / 空白画布 / 文字图纸 / 项目存档 / 40 步撤销
- 📤 导出 PNG / PDF

> 完整功能清单、算法说明与构建文档见 **[docs/完整文档.md](docs/完整文档.md)**

## 🔒 隐私

**无网络权限，不联网、不收集任何数据。** 所有处理都在你的手机上完成。

## 开源组件

- 模板素材：[Kenney](https://kenney.nl)（CC0 公共领域）
- 去背景模型：[U²-Net](https://github.com/xuebinqin/U-2-Net)（Apache 2.0）
- 推理引擎：[ONNX Runtime](https://github.com/microsoft/onnxruntime)（MIT）

完整声明见 [THIRD_PARTY.md](THIRD_PARTY.md)。

## 🛠 从源码构建

零 Gradle 依赖，双击 `build_apk.bat` 即可出 APK（签名口令从环境变量 `PINDOU_KS_PASS` 读取，首次构建自动生成测试签名）。需要本地自备 `tools\` 目录（JDK17 + Android build-tools 34 + ONNX Runtime AAR），详细步骤见 [docs/完整文档.md](docs/完整文档.md)。也可以直接用 Android Studio 打开（标准 Gradle 工程）。

HarmonyOS 移植工程见 [harmony/](harmony/PORTING.md)。

## 📄 开源协议

本项目以 **GNU AGPL-3.0** 协议开源（完整文本见 [LICENSE](LICENSE)）。欢迎 Issue、PR 与模板投稿。

---

v2.26 · 完全免费 · 无广告
