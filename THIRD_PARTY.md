# 第三方组件与素材声明 (THIRD PARTY NOTICES)

本项目为纯免费、非营利、学习用途的开源项目,依赖以下第三方资源,在此一并致谢。
如你是相关权利人且认为使用方式不当,请提 Issue,我会第一时间处理。

## 运行时组件

| 组件 | 用途 | 许可证 | 来源 |
|---|---|---|---|
| ONNX Runtime (Android) | U²-Net 抠图模型的端侧推理 | MIT | https://github.com/microsoft/onnxruntime |
| u2netp 模型 (4.7MB) | 轻量主体分割(一键去背景) | Apache-2.0 | https://github.com/xuebinqin/U-2-Net |

- ONNX Runtime 以 AAR 形式在本地构建时解包使用(仓库不含二进制,见 build_apk.bat 说明)。
- u2netp 模型文件随 APP 内置于 `app/src/main/assets/u2netp.onnx`,版权归 U²-Net 作者所有,按 Apache-2.0 分发。

## 模板素材

| 素材 | 许可证 | 来源 |
|---|---|---|
| 内置模板库中的 96 个专业像素素材(萌宠动物 / 情绪气泡 / 迷你地牢) | CC0 1.0 (Public Domain) | https://kenney.nl |

## 色板数据

- Artkal / 漫德 MARD / Perler / Hama 等品牌色号的 RGB 参考值,基于公开资料与开源社区
  实测数据整理(含 Lospec 等开放色板),仅供配色参考;实物豆颜色请以各品牌官方色卡为准。
- 各品牌名称与色号体系归各自品牌方所有,本项目仅作兼容性引用。

## 构建期工具(不随 APP 分发)

| 工具 | 许可证 |
|---|---|
| Android SDK build-tools / platform (aapt2, d8, apksigner) | Android Open Source Project (Apache-2.0) |
| Eclipse Temurin JDK 17 | GPLv2 with Classpath Exception |

## 本项目协议

本项目代码以 **GNU Affero General Public License v3.0 (AGPL-3.0)** 开源,详见 [LICENSE](LICENSE)。
