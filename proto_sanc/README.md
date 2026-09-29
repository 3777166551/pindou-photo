# proto_sanc — 三转二原型验证(不动 APP 代码)

2026-09-20 起,路线A/B 的 PC 原型验证沙盒。全部脚本以**空闲优先级**运行,不抢前台 CPU;
写出只落在 `out/`;豆色板/模型全部复用仓库既有资产,零下载。

## 文件

- `sanc_common.py` 模型推理封装(AnimeGANv3/WBC/线稿/YuNet)+ Reinhard/deblue + 写出防护
- `bead_palette_data.py` APP 90 色档字面量(与 `BeadPalettes.java` T1+T2+T3 一致)
- `pipe_a.py` 路线A:现行 APP 效果复现 vs 对齐人像管线(Ghibli/JP_face/WBC 三变体 + 线稿叠层)
- `pipe_b.py` 路线B:图纸量化对比(现行逐格最近豆 vs 动漫模式:平滑+中心加权+限色 kmeans+墨线描边豆)
- `run_all.py` 总控,产出 `out/report.md`
- `SPIKE_C_真机验证指南.md` 路线C(端侧扩散)真机验证步骤,无需本机算力

## 运行

```bat
python -X utf8 proto_sanc\run_all.py
```

## 看结果

1. `out/sheet_a_face000.png` 等四张:最左"Current APP v2.58"就是现行效果,右边是路线A 各变体;
2. `out/sheet_b_face000_s58.png` 等:风格化输入 → 现行量化 → 动漫模式 → 豆子渲染;
3. `out/report.md` 各阶段耗时与量化指标。
