# -*- coding: utf-8 -*-
"""阈值灵敏度标定 (04 报告 §十三 方案3):桶阈值/亮度门控 + 线条救援带。

方法: 单参数扫描(其余固定在现行值),每参数 5 档;现行值 = 桶 64 / 亮度 125 / 救援 10%~45%。
门控扫描: datasets/random 50 张(照片代理);指标 = 触发率/触发集亮度(应远低于阈值)/
          提亮量(L* 收益)/漏掉的暗图(lum<阈值却因桶数没触发)。
救援扫描: datasets/lineart+cartoons 100 张(细线代理);指标 = 碎豆v2/raw碎豆/dark 线保留/
          ΔE/用色;基准线 = OLD(盒平均管线)同图指标。
一致性自检: 带参 vote 与 batch_compare.vote_chart 在现行参数(10/45)下逐格相等(抽 3 图)。

输出: bench_out/calib_gate.csv / calib_rescue.csv / calib_report.md
"""
import os, sys, csv, math

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import numpy as np
from algo_bakeoff import rgb_lab
from batch_compare import (GRID, SRC, B, PAL_N, PAL_LAB, PAL_DARK, lut_de2000,
                           cell_mean, lab_of_blocks, quantize, vote_chart,
                           stats, key_for)
from auto_judge import (safe_join, DS, OUT, GRAPHIC_CATS, PHOTO_CATS,
                        rgb_lab_np, nearest_bead_exact, ownerless_cells,
                        frag_v2, de_stats, load_image)

os.makedirs(OUT, exist_ok=True)
GATE_BUCKET, GATE_LUM = 64, 125          # 现行门控
RESCUE_LO, RESCUE_HI = 10, 45            # 现行救援带(百分比)
GATE_SCAN_BUCKETS = [32, 48, 64, 96, 128]
GATE_SCAN_LUMS = [100, 112, 125, 138, 150]
RESCUE_SCAN_LOS = [4, 7, 10, 15, 20]
RESCUE_SCAN_HIS = [30, 40, 45, 50, 60]


def vote_chart_p(img, lut, dark, lo, hi, gate_gain=1.0, gate_sat=1.0):
    """batch_compare.vote_chart 的带参复制(仅救援带 lo%~hi% 可调,其余逐行一致)"""
    a = np.asarray(img, dtype=np.float64)
    if gate_gain > 1.0:
        a = np.clip(a * gate_gain, 0, 255)
        gray = a @ np.array([0.299, 0.587, 0.114])
        a = np.clip(gray[..., None] + (a - gray[..., None]) * gate_sat, 0, 255)
    idx = lut[quantize(a)]
    blocks = idx.reshape(GRID, B, GRID, B)
    means = a.reshape(GRID, B, GRID, B, 3).mean(axis=(1, 3))
    out = np.zeros((GRID, GRID), dtype=np.int64)
    for gy in range(GRID):
        for gx in range(GRID):
            blk = blocks[gy, :, gx, :].ravel()
            out[gy, gx] = int(np.bincount(blk, minlength=PAL_N).argmax())
            if blk.size < 8:
                continue
            mr, mg, mb = means[gy, gx]
            lab_m = rgb_lab((int(mr), int(mg), int(mb)))
            chroma = math.sqrt(lab_m[1] ** 2 + lab_m[2] ** 2)
            dark_sel = blk[dark[blk]]
            dk = dark_sel.size * 100
            if lab_m[0] > 60 and chroma < 20 and dk >= blk.size * lo and dk <= blk.size * hi:
                out[gy, gx] = int(np.bincount(dark_sel, minlength=PAL_N).argmax())
    return out


def old_beads(im):
    ref_labs = lab_of_blocks(cell_mean(im).reshape(-1, 3))
    dd = ((ref_labs[:, None, :] - PAL_LAB[None, :, :]) ** 2).sum(axis=2)
    return dd.argmin(axis=1).reshape(GRID, GRID), ref_labs


def new_beads(im, lut, lo, hi):
    a = np.asarray(im, dtype=np.float64)
    lum = float((a @ np.array([0.299, 0.587, 0.114])).mean())
    buckets = int(np.unique(quantize(a)).size)
    if buckets >= GATE_BUCKET and lum < GATE_LUM:
        gain = min(1.35, GATE_LUM / max(1.0, lum))
        return vote_chart_p(im, lut, PAL_DARK, lo, hi, gain, 1.10), True
    return vote_chart_p(im, lut, PAL_DARK, lo, hi), False


def self_check(lut):
    """带参 vote 在现行参数下必须与 batch_compare.vote_chart 逐格相等"""
    d = safe_join(DS, 'lineart')
    files = sorted(f for f in os.listdir(d) if f.lower().endswith(('.jpg', '.jpeg', '.png')))[:3]
    for fn in files:
        im = load_image(d, fn)
        b1 = vote_chart(im, lut, PAL_DARK)
        b2 = vote_chart_p(im, lut, PAL_DARK, RESCUE_LO, RESCUE_HI)
        assert (b1 == b2).all(), 'self-check mismatch on %s' % fn
    print('self-check OK (param vote == batch vote @10/45)', flush=True)


def scan_gate(lut, files):
    d = safe_join(DS, 'random')
    rows = []
    settings = ([(b, GATE_LUM, 'bucket=%d' % b) for b in GATE_SCAN_BUCKETS]
                + [(GATE_BUCKET, v, 'lum=%d' % v) for v in GATE_SCAN_LUMS])
    for bk, lm, tag in settings:
        agg = dict(gated=0, n=0, lum_gated=0.0, dl_gated=0.0, de_gated=0.0,
                   missed=0, de_all_o=0.0, de_all_n=0.0)
        for fn in files:
            im = load_image(d, fn)
            a = np.asarray(im, dtype=np.float64)
            lum = float((a @ np.array([0.299, 0.587, 0.114])).mean())
            buckets = int(np.unique(quantize(a)).size)
            b_old, ref_labs = old_beads(im)
            if buckets >= bk and lum < lm:
                gain = min(1.35, lm / max(1.0, lum))
                b_new = vote_chart_p(im, lut, PAL_DARK, RESCUE_LO, RESCUE_HI, gain, 1.10)
                gated = True
            else:
                b_new = vote_chart_p(im, lut, PAL_DARK, RESCUE_LO, RESCUE_HI)
                gated = False
                if lum < lm:
                    agg['missed'] += 1
            de_o, _ = de_stats(b_old, ref_labs)
            de_n, _ = de_stats(b_new, ref_labs)
            agg['n'] += 1
            agg['de_all_o'] += de_o
            agg['de_all_n'] += de_n
            if gated:
                agg['gated'] += 1
                agg['lum_gated'] += lum
                agg['dl_gated'] += (de_stats(b_new, ref_labs)[1]
                                    - de_stats(b_old, ref_labs)[1])
                agg['de_gated'] += de_n - de_o
        n = max(1, agg['n'])
        g = max(1, agg['gated'])
        rows.append(dict(setting=tag, bucket=bk, lum=lm,
                         gate_pct=100.0 * agg['gated'] / n,
                         gated_mean_lum=round(agg['lum_gated'] / g, 1),
                         gated_lum_lift=round(-agg['dl_gated'] / g, 2),
                         gated_de_lift=round(agg['de_gated'] / g, 2),
                         missed_dark=agg['missed'],
                         de_old_all=round(agg['de_all_o'] / n, 2),
                         de_new_all=round(agg['de_all_n'] / n, 2)))
        print('gate %-12s gate=%3.0f%% gatedLum=%5.1f lift=%+.2f miss=%d'
              % (tag, rows[-1]['gate_pct'], rows[-1]['gated_mean_lum'],
                 rows[-1]['gated_de_lift'], agg['missed']), flush=True)
    return rows


def scan_rescue(lut, cat_files):
    rows = []
    settings = ([(lo, RESCUE_HI, 'lo=%d' % lo) for lo in RESCUE_SCAN_LOS]
                + [(RESCUE_LO, v, 'hi=%d' % v) for v in RESCUE_SCAN_HIS])
    for lo, hi, tag in settings:
        agg = dict(n=0, f2_o=0, f2_n=0, fr_o=0, fr_n=0, dk_o=0, dk_n=0,
                   de_o=0.0, de_n=0.0, col_o=0, col_n=0)
        for cat, files in cat_files:
            for fn in files:
                im = load_image(safe_join(DS, cat), fn)
                b_old, ref_labs = old_beads(im)
                b_new = vote_chart_p(im, lut, PAL_DARK, lo, hi)
                co, cc, fo, do = stats(b_old)
                cn, c2, fn2, dn = stats(b_new)
                agg['n'] += 1
                agg['f2_o'] += frag_v2(b_old)
                agg['f2_n'] += frag_v2(b_new)
                agg['fr_o'] += fo
                agg['fr_n'] += fn2
                agg['dk_o'] += do
                agg['dk_n'] += dn
                agg['col_o'] += co
                agg['col_n'] += cn
                agg['de_o'] += de_stats(b_old, ref_labs)[0]
                agg['de_n'] += de_stats(b_new, ref_labs)[0]
        n = max(1, agg['n'])
        rows.append(dict(setting=tag, lo=lo, hi=hi, n=n,
                         frag2_old=round(agg['f2_o'] / n, 1),
                         frag2_new=round(agg['f2_n'] / n, 1),
                         frag_old=round(agg['fr_o'] / n, 1),
                         frag_new=round(agg['fr_n'] / n, 1),
                         dark_old=round(agg['dk_o'] / n, 1),
                         dark_new=round(agg['dk_n'] / n, 1),
                         colors_old=round(agg['col_o'] / n, 1),
                         colors_new=round(agg['col_n'] / n, 1),
                         de_old=round(agg['de_o'] / n, 2),
                         de_new=round(agg['de_n'] / n, 2)))
        print('rescue %-8s f2 %5.1f->%5.1f dark %5.1f->%5.1f de %5.2f->%5.2f'
              % (tag, rows[-1]['frag2_old'], rows[-1]['frag2_new'],
                 rows[-1]['dark_old'], rows[-1]['dark_new'],
                 rows[-1]['de_old'], rows[-1]['de_new']), flush=True)
    return rows


def main():
    print('building LUT...', flush=True)
    lut = lut_de2000()
    self_check(lut)

    d = safe_join(DS, 'random')
    gate_files = sorted(f for f in os.listdir(d)
                        if f.lower().endswith(('.jpg', '.jpeg', '.png'))
                        and os.path.getsize(safe_join(d, f)) > 3000)[:50]
    print('=== gate scan: random x%d ===' % len(gate_files), flush=True)
    gate_rows = scan_gate(lut, gate_files)

    cat_files = []
    for cat in ('lineart', 'cartoons'):
        dd = safe_join(DS, cat)
        cat_files.append((cat, sorted(f for f in os.listdir(dd)
                                      if f.lower().endswith(('.jpg', '.jpeg', '.png'))
                                      and os.path.getsize(safe_join(dd, f)) > 3000)[:50]))
    print('=== rescue scan: lineart+cartoons x%d ===' %
          sum(len(f) for _, f in cat_files), flush=True)
    rescue_rows = scan_rescue(lut, cat_files)

    for name, rws in (('calib_gate.csv', gate_rows), ('calib_rescue.csv', rescue_rows)):
        with open(os.path.join(OUT, name), 'w', newline='', encoding='utf-8') as f:
            w = csv.DictWriter(f, fieldnames=list(rws[0].keys()))
            w.writeheader()
            w.writerows(rws)

    md = ['# 阈值灵敏度标定 (auto_calibrate, 2026-09-21)', '',
          '现行值: 桶阈值 64 / 亮度门控 125 / 救援带 10%%~45%%。单参数扫描,每参数 5 档。', '',
          '## 门控扫描(datasets/random 50 张,照片代理)', '',
          '| 设置 | 触发率 | 触发集均亮度 | 触发集 L* 收益 | 触发集 ΔE 偏移 | 漏暗图(lum<阈值未触发) |',
          '|---|---|---|---|---|---|']
    for r in gate_rows:
        md.append('| %s (桶%d/亮%d) | %.0f%% | %.1f | %+.2f | %+.2f | %d |' %
                  (r['setting'], r['bucket'], r['lum'], r['gate_pct'],
                   r['gated_mean_lum'], r['gated_lum_lift'],
                   r['gated_de_lift'], r['missed_dark']))
    md += ['', '## 救援带扫描(lineart+cartoons 100 张,细线代理)', '',
           '| 设置 | 碎豆v2 旧→新 | raw碎豆 旧→新 | dark 旧→新 | 用色 旧→新 | ΔE 旧→新 |',
           '|---|---|---|---|---|---|']
    for r in rescue_rows:
        md.append('| %s (带%d%%~%d%%) | %.1f→%.1f | %.1f→%.1f | %.1f→%.1f | %.1f→%.1f | %.2f→%.2f |' %
                  (r['setting'], r['lo'], r['hi'], r['frag2_old'], r['frag2_new'],
                   r['frag_old'], r['frag_new'], r['dark_old'], r['dark_new'],
                   r['colors_old'], r['colors_new'], r['de_old'], r['de_new']))
    md += ['', '判读要点: 门控=触发集均亮度应远低于阈值且漏暗图少;救援=dark 不掉(线保留)、'
           '碎豆v2 低、ΔE 不恶化;4%% 下限会过度救援投黑(报告 §11 实测),10%% 为定稿值。']
    with open(os.path.join(OUT, 'calib_report.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(md) + '\n')
    print('ALL-DONE', flush=True)


if __name__ == '__main__':
    main()
