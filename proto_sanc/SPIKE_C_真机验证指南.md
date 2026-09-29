# 路线C真机 Spike 验证指南(SD1.5 QNN NPU img2img)

> 目的:在写任何一行自研集成代码之前,先花半天用现成开源件亲眼确认
> "端侧扩散 img2img 动漫化"的真实效果、速度与发热,决定要不要立项路线C。
> 本指南不含本地重计算,全部在目标手机上完成。

## 1. 硬件门槛(2026-09 已核实)

- **骁龙 8 Gen 1 及以上**(Hexagon V68+):SD1.5 QNN NPU 可跑;
- SDXL 级需 **8 Gen 3+**;
- 天玑:暂无公开 img2img NPU 工具链,只能用 MNN CPU/GPU 兜底(分钟级,仅评估用);
- 内存:预留 3GB 以上空闲 RAM。

## 2. 材料(全免费)

| 件 | 来源 |
|---|---|
| Local Dream(开源安卓 APP,img2img+inpainting+LoRA,QNN NPU) | github.com/xororz/local-dream 的 Releases |
| 动漫 SD1.5 QNN 权重(已有人移植) | huggingface.co/Mr-J-369/MeinaMix-SD1.5-qnn2.28(约 0.8~1.2GB) |
| 测试图 | 仓库 `tools/bgtest/people/face000.jpg`、`face003.jpg`、`images/user_girl.jpg`、`images/home.jpg` 拷进手机 |
| 8 Gen 3+ 加测(可选) | 社区 SDXL 动漫 QNN 转换(如 CelesteImperia SDXL QNN,HF 搜) |

## 3. 步骤

1. 安装 Local Dream APK,按其 README 放置/导入 QNN 模型;
2. 依次导入 4 张测试图,走 **img2img**;
3. 每张图测 9 组参数:`strength ∈ {0.35, 0.5, 0.65} × steps ∈ {4, 8, 12}`;
4. 每组记录:耗时(秒)、生成中 RAM 峰值、机身温度/是否降频、主观三分(像本人 0-5 / 二次元浓度 0-5 / 背景·手部崩坏 0-5)。

## 4. 判定门槛(立项/止步)

- **立项**:strength 0.5 档,身份保持可接受(≥3 分)且二次元浓度明显强于 `proto_sanc/out/sheet_a_*.png` 里路线A 的最好变体(≥4 分),单张 ≤15s;
- **止步**:脸崩/身份丢失严重,或需 12 步以上才像样导致 >30s;
- 灰色区:效果够好但速度慢 → 考虑只做"充电+联网外"的可选慢速档,或不做。

## 5. 若立项,自研集成的工作量预估(供决策)

- QNN 运行时 SoC 库按芯片发版(~几 MB~几十 MB);
- 权重按需下载包 ~1.2~1.5GB(下载器+校验+断点续传);
- img2img 管线:VAE encode→UNet 去噪(4~8 步)→VAE decode,strength 映射步数;
- AnimeGAN 路线保留为全机型默认,扩散入口仅对 8 Gen 1+ 且 RAM≥8GB 的机型可见;
- 风险:发热降频、各代 Hexagon 兼容性、动漫 QNN 权重需要自己建立转换流水线(社区工具 QNN 工具链 + onnx/ckpt 源)。

## 6. 兜底备选

- 天玑/中端骁龙:MNN-Diffusion 的 Sana img2img(github.com/alibaba/MNN,transformers/diffusion),CPU/GPU 路线,预计分钟级——只做技术评估,不建议作为产品档位。
