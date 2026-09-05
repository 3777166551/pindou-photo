# 第三方组件与素材声明 (THIRD PARTY NOTICES)

本项目为纯免费、非营利、学习用途的开源项目,依赖以下第三方资源,在此一并致谢。
如你是相关权利人且认为使用方式不当,请提 Issue,我会第一时间处理。

## 运行时组件

| 组件 | 用途 | 许可证 | 来源 |
|---|---|---|---|
| ONNX Runtime (Android) | U²-Net 抠图模型的端侧推理 | MIT | https://github.com/microsoft/onnxruntime |
| u2netp 模型 (4.7MB) | 轻量主体分割(一键去背景) | Apache-2.0 | https://github.com/xuebinqin/U-2-Net |
| AnimeGANv3 吉卜力模型 (约 7MB) | 照片转吉卜力风(AI 风格化) | 自定义许可证(非商业免费) | https://github.com/TachibanaYoshino/AnimeGANv3 |

- ONNX Runtime 以 AAR 形式在本地构建时解包使用(仓库不含二进制,见 build_apk.bat 说明)。
- 随 APP 分发的 `app/src/main/assets/u2netp.onnx` 为 U²-Net 官方预训练小模型
  u2netp 的 ONNX 格式版本(格式转换,算法权重未修改);原始权重、论文与许可证
  见上方原仓库。按 Apache-2.0 要求,本页即其来源与许可证声明。
- **AnimeGANv3** © Asher Chan。随 APP 分发的
  `app/src/main/assets/animeganv3_ghibli.onnx` 为官方发布的
  `AnimeGANv3_large_Ghibli_c1_e299.onnx`(未修改)。其许可证为自定义条款:
  **非商业用途(学术研究、教学、个人创作等)可免费使用,商用需联系作者授权**
  (作者邮箱见原仓库 README)。本 APP 为免费、无广告、不开源商业化的非商业
  开源项目,符合该许可的使用范围;若你 fork 本项目用于商业用途,请自行
  获得作者授权。
- u2netp 模型文件随 APP 内置于 `app/src/main/assets/u2netp.onnx`,版权归 U²-Net 作者所有,按 Apache-2.0 分发。

## 模板素材

| 素材 | 许可证 | 来源 |
|---|---|---|
| 内置模板库中的 176 个流行表情图案(流行表情 / 萌宠动物 / 美食饮料 / 花草节日) | MIT | https://github.com/microsoft/fluentui-emoji |

- 内置模板库基于 **Microsoft Fluent Emoji**(3D 风格位图)生成:缩放至 32×32
  后逐像素量化映射到本 APP 的 120 色通用色板,以像素网格数据形式随 APP 分发。
  Fluent Emoji 以 MIT 许可证发布,版权归 Microsoft 所有;按 MIT 要求在此保留
  许可声明。量化与生成脚本见 `tools/emoji_src/`(仓库不分发原始位图)。
- 历史版本(v2.26~v2.35)的模板基于 Kenney 素材(CC0 公共领域,https://kenney.nl),
  v2.36 起已替换为上述 Fluent Emoji 版本。

## 色板数据

- Artkal / 漫德 MARD / Perler / Hama 等品牌色号的 RGB 参考值,基于公开资料与开源社区
  实测数据整理(含 Lospec 等开放色板),仅供配色参考;实物豆颜色请以各品牌官方色卡为准。
- 各品牌名称与色号体系归各自品牌方所有,本项目仅作兼容性引用。

## 构建期工具(不随 APP 分发)

| 工具 | 许可证 |
|---|---|
| Android SDK build-tools / platform (aapt2, d8, apksigner) | Android Open Source Project (Apache-2.0) |
| Eclipse Temurin JDK 17 | GPLv2 with Classpath Exception |

## 灵感与算法参考(未复制源码)

- **[Zippland/perler-beads](https://github.com/Zippland/perler-beads)**(AGPL-3.0)
  —— "众数取色像素化"与"连通区域杂色合并"的算法思路来源。本仓库参考其公开的
  算法文档后以 Java **独立实现**(`dominantResample` / `mergeNoiseRegions`),
  未复制其源码;按 AGPL-3.0 的开源精神在此明确致谢。
- **Sharma, Wu, Dalal (2005)**,*The CIEDE2000 Color-Difference Formula*
  —— `ColorMath.deltaE2000` 实现所依据的论文;论文附带的 34 对官方参考色对
  已用于本实现的数值验证(34/34 通过)。
- **Pattern Keeper / MakeBead 等同类工具** —— 逐色进度标记、油漆桶填充、
  全局替换色等功能的交互设计参考,均未使用其任何代码或素材。

## 关于本仓库代码

截至当前版本,本仓库全部 `.java` 源码均为独立实现,未复制任何第三方项目的源代码;
随 APP 分发的第三方内容仅限本页所列。若未来引入第三方代码,会在本页声明
其来源、许可证及修改说明,并保证与 AGPL-3.0 兼容。

## 本项目协议

本项目代码以 **GNU Affero General Public License v3.0 (AGPL-3.0)** 开源,详见 [LICENSE](LICENSE)。
