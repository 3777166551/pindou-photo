# PindouPhoto for HarmonyOS NEXT(纯血鸿蒙)移植工程

> 目标:把安卓版(纯 Java,26 个版本迭代)移植到 HarmonyOS NEXT 原生应用,
> 界面/交互尽量与安卓版一致(柔焦粉彩玻璃拟态风)。
> 本目录是 DevEco Studio 工程;**用 DevEco Studio 5.0+ 打开本目录即可构建**
> (需要 Node.js/hvigor 与签名配置,详见文末)。

## 目录

```
harmony/
├── AppScope/app.json5                应用级配置
├── build-profile.json5               模块/签名配置
├── hvigorfile.ts                     构建脚本
├── oh-package.json5                  依赖(零三方依赖)
└── entry/src/main/
    ├── module.json5                  ability/权限声明(零网络权限)
    ├── ets/
    │   ├── entryability/EntryAbility.ets
    │   ├── pages/Index.ets           首页(卡片流,对齐安卓 activity_main)
    │   ├── pages/Editor.ets          编辑页(参数/图纸预览/豆单,对齐 activity_editor)
    │   ├── pages/Templates.ets       模板库(两级导航)
    │   ├── pages/Knowledge.ets       拼豆知识
    │   ├── components/PatternCanvas.ets  图纸画布(缩放/平移/画笔/辅助拼豆)
    │   └── core/                     核心算法(自安卓纯 Java 文件 1:1 移植)
    │       ├── ColorMath.ts
    │       ├── BeadColor.ts          BeadColor + 色板(通用 4 档)
    │       ├── BrandCharts.ts        品牌色号表(Artkal/漫德/Perler/Hama)
    │       ├── PatternEngine.ts      转图纸引擎(去 ML,用纯算法兜底去背景)
    │       ├── Segmenter.ts          纯算法去背景(边界聚类+Otsu+连通域)
    │       ├── WatermarkRemover.ts   框选去水印
    │       ├── GridScanner.ts        拍照识别图纸
    │       ├── TemplateData.ts       96 个 CC0 模板(动物/情绪/地牢)
    │       ├── BeadInventory.ts      豆仓库存(Preferences 持久化)
    │       └── ProjectStore.ts       项目存档(JSON 持久化)
    └── resources/base/...            图标/字符串
```

## 与安卓版的差异(明确砍掉/降级)

| 功能 | 安卓 | 鸿蒙版原因 |
|---|---|---|
| U²-Net ONNX 去背景 | 本地 ML 推理 | ONNX Runtime 官方不支持 NEXT;用纯算法去背景(Segmenter.ts,安卓版 v6 的兜底算法) |
| 云端 AI 二次元 | 已在 v2.26 移除 | 同安卓,不涉及 |
| PDF 导出 | Android PdfDocument | 阶段 1 先出 PNG;PDF 用纯代码生成器后续补 |
| 相机拍摄 | CameraX 相当物 | 阶段 1 用系统相册选择(photoAccessHelper);拍照后续补 |
| 40 步撤销 | 栈实现 | 同实现移植 |

## 构建步骤(本机没有 DevEco 时)

1. 安装 DevEco Studio 5.0+ 与 HarmonyOS NEXT SDK(含 ArkTS 编译链);
2. File → Open 打开 `harmony/` 目录,等待 hvigor 同步;
3. File → Project Structure → Signing Configs:登录华为账号自动签名(调试);
4. 连接鸿蒙 NEXT 真机/模拟器,Run。
