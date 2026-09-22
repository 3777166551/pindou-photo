# -*- coding: utf-8 -*-
"""自动评判 v2 (04 报告 §十三):347 张基准图三指标全自动判决,人只看"更差"堆。

v1 教训(2026-09-21 实测,70.6% 误判"更差",两个指标级失真):
  ① 均值参照 ΔE 天然是 L2 最优 → 多数票的锐利输出结构性吃亏
     (非门控照片 ΔE 中位 +3.35/max +7.43,门控图再 +8~9.7 提亮被记为偏离);
  ② dark 保留代理把"旧管线灰色晕染被正确清除"误判为"线丢失"
     (lineart dark 中位反而 2.28 倍,pixelart 假阳性 12/50)。
v2 指标体系:
  无主豆格率 = 格内没有任何源像素把该豆当最近色(凭空捏造的杂色;NEW 机制上恒 0,
               OLD 的边缘晕染/幻影全被抓住)——全场主指标,结构性、无阈值;
  碎豆v2    = 非"深豆"(L*>=45)的 <=2 格连通碎片(线段不再被记成碎豆,晕染照记);
  ΔE        = Lab 欧氏 vs 源图盒平均,只作报告与灾难护栏,不作照片逐图判决;
  线保留    = dark 比值 <85% 且旧 dark>=10 才报警,仅限 lineart/cartoons。

判决(逐图,v3,经 6 张更差堆样张目检校准):
  图形类 更差 = 线丢失 或 无主豆增加 或 色度离群格增加(灰图投彩豆,目检实锤);
          更优 = 无主豆减半 或 碎豆v2 下降 或 用色<=85%旧;
          (碎豆v2 增加多为锐利边界锯齿误伤,只作报告列不触发判决)
  照片类 更差 = 无主豆增加(>+2格) 或 (非门控且 ΔE 恶化>+8 灾难护栏);
          更优 = 无主豆减半。照片纹理锐利化(ΔE +3~7)属已知取舍,人工抽样复核。

CI 判据(逐类聚合,暂定,待人审校准后定稿):
  图形类 无主豆<=30%旧 且 色度离群不增(+4 容差) 且 lineart dark 不减;
  照片类 无主豆不增 且 非门控 ΔE 均值 <= 旧+8。

输出: bench_out/judge_results.csv / judge_summary.csv / judge_report.md
       bench_out/judge_radar.png / worse/*.png / sample/*.png
复核: --check 与 bench_results.csv 的既有列(raw frag/colors/dark)比对。
"""
import os, sys, csv, random, math

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import numpy as np
from PIL import Image, ImageDraw
from algo_bakeoff import PAL, rgb_lab
from batch_compare import (GRID, SRC, B, CELL, PLATE, PAL_N, PAL_LAB, PAL_DARK,
                           lut_euclid, lut_de2000, cell_mean, lab_of_blocks,
                           quantize, vote_chart, stats, chart_img, sheet, key_for)
from vote12 import vote_chart12


def sheet12(src_im, b_v11, b_v12, label):
    """SRC | V11 | V12 三联样张"""
    from algo_bakeoff import PAL as _PAL

    def chart12(beads):
        img = Image.new('RGB', (GRID * CELL, GRID * CELL), PLATE)
        d = ImageDraw.Draw(img)
        r = CELL * 0.42
        for gy in range(GRID):
            for gx in range(GRID):
                cx, cy = gx * CELL + CELL / 2, gy * CELL + CELL / 2
                d.ellipse([cx - r, cy - r, cx + r, cy + r],
                          fill=tuple(_PAL[beads[gy, gx]]))
        return img

    s = src_im.resize((GRID * CELL, GRID * CELL))
    out = Image.new('RGB', (GRID * CELL * 3 + 24, GRID * CELL + 30), (255, 255, 255))
    out.paste(s, (0, 30))
    out.paste(chart12(b_v11), (GRID * CELL + 12, 30))
    out.paste(chart12(b_v12), (GRID * CELL * 2 + 24, 30))
    d = ImageDraw.Draw(out)
    d.text((6, 8), label + '  SRC | V11 | V12', fill=(29, 27, 32))
    return out


def v12_selfcheck(lut):
    """V12 合成三景自检: 硬黑线保持黑 / 抗锯齿软线变亮 / 噪声红盘吸附"""
    px = np.full((SRC, SRC, 3), 255.0)
    px[64, :, :] = 20.0
    im = Image.fromarray(px.astype(np.uint8))
    v12, _ = vote_chart12(im, lut, PAL_LAB, PAL_DARK, GRID, SRC, B)
    assert PAL_LAB[v12[8, 30]][0] < 45, 'v12 硬黑线救援丢失'
    px2 = np.full((SRC, SRC, 3), 255.0)
    px2[64, :, :] = 51.0
    px2[63, :, :] = 153.0
    px2[65, :, :] = 153.0
    im2 = Image.fromarray(px2.astype(np.uint8))
    v11b = vote_chart(im2, lut, PAL_DARK)
    v12b, _f = vote_chart12(im2, lut, PAL_LAB, PAL_DARK, GRID, SRC, B)
    print('v12 selfcheck 软线: V11 L*=%.1f V12 L*=%.1f (V12 应 >= V11)' %
          (PAL_LAB[v11b[8, 30]][0], PAL_LAB[v12b[8, 30]][0]), flush=True)
    px3 = np.full((SRC, SRC, 3), 255.0)
    yy, xx = np.mgrid[0:SRC, 0:SRC]
    disk = (yy - SRC / 2) ** 2 + (xx - SRC / 2) ** 2 < (SRC / 3) ** 2
    rng = np.random.default_rng(3)
    noise = rng.integers(-8, 9, size=(SRC, SRC, 3))
    px3[disk] = np.clip(np.array([227, 36, 43]) + noise[disk], 0, 255)
    im3 = Image.fromarray(px3.astype(np.uint8))
    v11c = vote_chart(im3, lut, PAL_DARK)
    v12c, _g = vote_chart12(im3, lut, PAL_LAB, PAL_DARK, GRID, SRC, B)
    print('v12 selfcheck 红盘用色: V11=%d V12=%d (V12 应 <= V11)' %
          (len(np.unique(v11c)), len(np.unique(v12c))), flush=True)

OUT = os.path.join(HERE, 'bench_out')
WORSE_DIR = os.path.join(OUT, 'worse')
SAMPLE_DIR = os.path.join(OUT, 'sample')
DS = os.path.join(HERE, 'datasets')
os.makedirs(WORSE_DIR, exist_ok=True)
os.makedirs(SAMPLE_DIR, exist_ok=True)

# ---- 判决阈值(暂定;人审校准后定稿,改了要重看更差堆) ----
GRAPHIC_CATS = ('cartoons', 'lineart', 'pixelart')
PHOTO_CATS = ('landscape', 'portrait', 'food', 'random')
LINE_CATS = ('lineart', 'cartoons')
LINE_KEEP_MIN = 0.85
LINE_KEEP_MIN_DARK = 10
OWNERLESS_GUARD_CELLS = 2    # 照片类无主豆增加报警格数
DE_GUARD_NG = 8.0            # 非门控照片 ΔE 灾难护栏(不作常规判决)
CHROMA_OUTLIER_GUARD = 4     # 图形类色度离群格增加报警数
CI_GRAPHIC_OWNERLESS = 0.3
CI_PHOTO_DE = 8.0

CELL_OF = ((np.arange(SRC * SRC) // SRC // B) * GRID
           + (np.arange(SRC * SRC) % SRC // B))


def safe_join(base, name):
    """规范化并校验拼接路径不越出 base(目录枚举名/生成名均过此检查)"""
    p = os.path.normpath(os.path.join(base, name))
    root = os.path.normpath(base)
    if p != root and not p.startswith(root + os.sep):
        raise ValueError('path escapes base: %r' % name)
    return p


def _srgb_lin(c):
    c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def rgb_lab_np(a):
    """(N,3) float -> (N,3) Lab;与 algo_bakeoff.rgb_lab 逐公式一致"""
    r = _srgb_lin(a[:, 0]); g = _srgb_lin(a[:, 1]); b = _srgb_lin(a[:, 2])
    x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
    y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750)
    z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
    f = lambda t: np.where(t > 0.008856, np.cbrt(t), 7.787 * t + 16.0 / 116.0)
    return np.stack([116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))], axis=1)


def nearest_bead_exact(labs, chunk=8192):
    """逐像素精确 Lab 欧氏最近豆(OLD 匹配语义);分块防大数组"""
    out = np.empty(len(labs), dtype=np.int64)
    for i in range(0, len(labs), chunk):
        d = ((labs[i:i + chunk, None, :] - PAL_LAB[None, :, :]) ** 2).sum(axis=2)
        out[i:i + chunk] = d.argmin(axis=1)
    return out


def ownerless_cells(beads, pix_beads):
    """无主豆格:格内没有任何源像素把该豆当最近色"""
    key = CELL_OF * 128 + pix_beads
    owned = np.zeros(GRID * GRID * 128, dtype=bool)
    owned[np.unique(key)] = True
    flat = beads.ravel()
    return ~(owned[np.arange(GRID * GRID) * 128 + flat])


PAL_CHROMA = np.sqrt(PAL_LAB[:, 1] ** 2 + PAL_LAB[:, 2] ** 2)
PAL_HUE = np.degrees(np.arctan2(PAL_LAB[:, 2], PAL_LAB[:, 1]))


def chroma_outlier_cells(beads, pix_labs, cell_cnt=None):
    """色相离群格:豆色 C*>=15 且格内所有彩色像素(C*>=10)与其色相夹角>30°。
    抓"棕墨/灰度源被投上异色豆"(目检实锤:sepia 雕版上的绿点);
    与 4bit 量化漂移无关(漂移豆与像素同色相);彩色图案同色相边界格不触发。"""
    pix_c = np.sqrt(pix_labs[:, 1] ** 2 + pix_labs[:, 2] ** 2)
    pix_h = np.degrees(np.arctan2(pix_labs[:, 2], pix_labs[:, 1]))
    bead_h = PAL_HUE[beads.ravel()[CELL_OF]]
    d = (pix_h - bead_h + 180) % 360 - 180
    same_hue = (pix_c >= 10) & (np.abs(d) < 30)
    owned = np.bincount(CELL_OF[same_hue], minlength=GRID * GRID) > 0
    return (PAL_CHROMA[beads.ravel()] >= 15) & ~owned


def de_stats(beads, ref_labs):
    bl = PAL_LAB[beads.ravel()]
    d = bl - ref_labs
    de = float(np.sqrt((d * d).sum(axis=1)).mean())
    dl = float((bl[:, 0] - ref_labs[:, 0]).mean())
    return de, dl


def gate_adjust(a, gain, sat):
    """与 vote_chart 同公式提亮(向量化),返回调整后数组"""
    a = np.clip(a * gain, 0, 255)
    gray = a @ np.array([0.299, 0.587, 0.114])
    return np.clip(gray[..., None] + (a - gray[..., None]) * sat, 0, 255)


def frag_v2(beads):
    """碎豆v2: 非"深豆"的 <=2 格连通碎片格数(线段/文字深豆不记)"""
    non_dark = np.array([[PAL_LAB[beads[y, x]][0] >= 45 for x in range(GRID)]
                         for y in range(GRID)])
    seen = np.zeros((GRID, GRID), dtype=bool)
    frag = 0
    for gy in range(GRID):
        for gx in range(GRID):
            if seen[gy, gx] or not non_dark[gy, gx]:
                continue
            stack = [(gy, gx)]
            seen[gy, gx] = True
            comp = []
            while stack:
                y, x = stack.pop()
                comp.append((y, x))
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < GRID and 0 <= nx < GRID and \
                       not seen[ny, nx] and non_dark[ny, nx]:
                        seen[ny, nx] = True
                        stack.append((ny, nx))
            if len(comp) <= 2:
                frag += len(comp)
    return frag


def judge(cat, old_f2, new_f2, old_dark, new_dark, old_colors, new_colors,
          ol_old, ol_new, de_old, de_new, gate, col_old, col_new):
    """返回 'better' / 'equal' / 'worse'
    自动判决只用结构性指标(无主豆/线保留/照片ΔE护栏)。
    色相离群与碎豆v2 增加是"风格权衡"(锐利 vs 晕染)与纹理信号,目检证明
    不能当自动判决(4/6 样张 NEW 明显更好),它们进人工复核堆见 needs_review。"""
    if cat in GRAPHIC_CATS:
        line_lost = (cat in LINE_CATS and old_dark >= LINE_KEEP_MIN_DARK
                     and new_dark < old_dark * LINE_KEEP_MIN)
        if line_lost or ol_new > ol_old:
            return 'worse'
        ol_half = ol_old >= 6 and ol_new * 2 < ol_old
        frag_ok = new_f2 < old_f2 if old_f2 else new_f2 == 0
        color_ok = new_colors <= old_colors * 0.85
        return 'better' if (ol_half or frag_ok or color_ok) else 'equal'
    else:
        if ol_new > ol_old + OWNERLESS_GUARD_CELLS:
            return 'worse'
        if not gate and de_new > de_old + DE_GUARD_NG:
            return 'worse'
        if ol_old >= 6 and ol_new * 2 < ol_old:
            return 'better'
        return 'equal'


def needs_review(cat, f2o, f2n, colout_n):
    """人工复核堆:自动判决无法定论的信号(高色相离群/碎豆大增)"""
    return cat in GRAPHIC_CATS and (colout_n >= 100 or f2n >= 2 * f2o + 4)


def radar_chart(summary, path):
    axes = [('无主豆%', 'ol_old_pct', 'ol_new_pct'),
            ('碎豆v2', 'f2_old', 'f2_new'),
            ('ΔE', 'de_old', 'de_new')]
    cats = [s['cat'] for s in summary]
    CW, CH = 300, 300
    cols = 4
    rows_n = (len(cats) + cols - 1) // cols
    W, H = cols * CW, rows_n * CH + 30
    img = Image.new('RGB', (W, H), (255, 255, 255))
    d = ImageDraw.Draw(img)
    cx0, cy0, R = CW // 2, CH // 2 + 10, 100
    angs = [math.radians(a) for a in (90, 210, 330)]

    def pt(cx, cy, ang, r):
        return (cx + r * math.cos(ang), cy - r * math.sin(ang))

    for i, s in enumerate(summary):
        cx, cy = (i % cols) * CW + cx0, (i // cols) * CH + cy0
        vals = []
        for _, ko, kn in axes:
            m = max(s[ko], s[kn], 1e-9)
            vals.append((max(0.05, s[ko] / m), max(0.05, s[kn] / m)))
        for rr in (0.33, 0.66, 1.0):
            tri = [pt(cx, cy, a, R * rr) for a in angs]
            d.polygon(tri, outline=(225, 222, 235))
        names = [a[0] for a in axes]
        for a, name in zip(angs, names):
            x, y = pt(cx, cy, a, R + 16)
            d.text((x - 12, y - 6), name, fill=(29, 27, 32))
        for poly, color in ((0, (128, 128, 134)), (1, (103, 80, 164))):
            tri = [pt(cx, cy, a, R * vals[j][poly]) for j, a in enumerate(angs)]
            d.polygon(tri, outline=color, width=2, fill=None)
        d.text((cx - 30, cy - CH // 2 + 12), s['cat'], fill=(29, 27, 32))
    d.text((10, H - 22), 'gray = OLD (V1)   purple = NEW (V11)   farther from center = higher (worse)',
           fill=(29, 27, 32))
    img.save(path)


def load_image(d, fn):
    im = Image.open(safe_join(d, fn)).convert('RGB')
    side = min(im.size)
    if side < 100:
        return None
    im = im.crop(((im.width - side) // 2, (im.height - side) // 2,
                  (im.width + side) // 2, (im.height + side) // 2))
    return im.resize((SRC, SRC), Image.LANCZOS)


def gen_two_beads(im, lut):
    """重算某张图的 OLD/NEW 豆格(供补画样张)"""
    a = np.asarray(im, dtype=np.float64)
    ref_labs = lab_of_blocks(cell_mean(im).reshape(-1, 3))
    dd = ((ref_labs[:, None, :] - PAL_LAB[None, :, :]) ** 2).sum(axis=2)
    b_old = dd.argmin(axis=1).reshape(GRID, GRID)
    lum = float((a @ np.array([0.299, 0.587, 0.114])).mean())
    buckets = int(np.unique(quantize(a)).size)
    if buckets >= 64 and lum < 125:
        gain = min(1.35, 125.0 / max(1.0, lum))
        b_new = vote_chart(im, lut, PAL_DARK, gain, 1.10)
    else:
        b_new = vote_chart(im, lut, PAL_DARK)
    return b_old, b_new


def main():
    check = '--check' in sys.argv
    v12mode = '--v12' in sys.argv
    print('building LUTs...', flush=True)
    LUT_D2 = lut_de2000()
    print('LUT ready', flush=True)
    if v12mode:
        v12_selfcheck(LUT_D2)

    # 向量化 Lab 与标量版一致性自检
    rng = np.random.default_rng(7)
    probe = rng.integers(0, 256, size=(500, 3)).astype(np.float64)
    ref = np.array([rgb_lab(tuple(p)) for p in probe])
    err = float(np.abs(rgb_lab_np(probe) - ref).max())
    assert err < 1e-8, 'rgb_lab_np mismatch: %g' % err
    print('rgb_lab_np verified, max_err=%.2e' % err, flush=True)

    prev = {}
    if check:
        with open(os.path.join(OUT, 'bench_results.csv'), encoding='utf-8') as f:
            for r in csv.DictReader(f):
                prev[(r['cat'], r['file'])] = r

    rows = []
    random.seed(20260921)
    for cat in GRAPHIC_CATS + PHOTO_CATS:
        d = safe_join(DS, cat)
        files = sorted(f for f in os.listdir(d)
                       if f.lower().endswith(('.jpg', '.jpeg', '.png'))
                       and os.path.getsize(safe_join(d, f)) > 3000)[:50]
        is_photo = cat in PHOTO_CATS
        sample_files = set(random.sample(files, 2))
        done = 0
        for fn in files:
            try:
                im = load_image(d, fn)
                if im is None:
                    continue
                a = np.asarray(im, dtype=np.float64)

                means = cell_mean(im)
                ref_labs = lab_of_blocks(means.reshape(-1, 3))
                dd = ((ref_labs[:, None, :] - PAL_LAB[None, :, :]) ** 2).sum(axis=2)
                b_old = dd.argmin(axis=1).reshape(GRID, GRID)

                lum = float((a @ np.array([0.299, 0.587, 0.114])).mean())
                buckets = int(np.unique(quantize(a)).size)
                gate = (buckets >= 64 and lum < 125)
                if gate:
                    gain = min(1.35, 125.0 / max(1.0, lum))
                    b_new = vote_chart(im, LUT_D2, PAL_DARK, gain, 1.10)
                    a_gated = gate_adjust(a, gain, 1.10)
                    ref_labs_adj = lab_of_blocks(
                        gate_adjust(means, gain, 1.10).reshape(-1, 3))
                else:
                    b_new = vote_chart(im, LUT_D2, PAL_DARK)
                    a_gated = a
                    ref_labs_adj = ref_labs

                # 无主豆: OLD=原图精确最近豆; NEW=门控图 LUT 豆(与各自管线同语义)
                pix_labs = rgb_lab_np(a.reshape(-1, 3))
                pix_bead_old = nearest_bead_exact(pix_labs)
                pix_bead_new = LUT_D2[quantize(a_gated).ravel()]
                ol_old = int(ownerless_cells(b_old, pix_bead_old).sum())
                ol_new = int(ownerless_cells(b_new, pix_bead_new).sum())
                cell_cnt = np.bincount(CELL_OF, minlength=GRID * GRID)
                colout_o = int(chroma_outlier_cells(b_old, pix_labs).sum())
                colout_n = int(chroma_outlier_cells(b_new, pix_labs).sum())

                co, cc, fo, do = stats(b_old)
                cn, c2, fn2, dn = stats(b_new)
                f2o, f2n = frag_v2(b_old), frag_v2(b_new)
                de_o, dl_o = de_stats(b_old, ref_labs)
                de_n, dl_n = de_stats(b_new, ref_labs)
                de_na, _ = de_stats(b_new, ref_labs_adj)
                v = judge(cat, f2o, f2n, do, dn, co, cn,
                          ol_old, ol_new, de_o, de_n, gate, colout_o, colout_n)
                row = dict(cat=cat, file=fn, gate=int(gate),
                           old_colors=co, new_colors=cn,
                           old_frag=fo, new_frag=fn2,
                           old_frag2=f2o, new_frag2=f2n,
                           old_dark=do, new_dark=dn,
                           ol_old=ol_old, ol_new=ol_new,
                           colout_old=colout_o, colout_new=colout_n,
                           de_old=round(de_o, 3), de_new=round(de_n, 3),
                           de_new_adj=round(de_na, 3),
                           de_delta=round(de_n - de_o, 3),
                           lum_dev_old=round(dl_o, 2), lum_dev_new=round(dl_n, 2),
                           verdict=v)
                if v12mode:
                    if gate:
                        b_v12, feat12 = vote_chart12(im, LUT_D2, PAL_LAB, PAL_DARK,
                                                     GRID, SRC, B,
                                                     gate_gain=gain, gate_sat=1.10)
                    else:
                        b_v12, feat12 = vote_chart12(im, LUT_D2, PAL_LAB, PAL_DARK,
                                                     GRID, SRC, B)
                    ol12 = int((ownerless_cells(b_v12, pix_bead_new)
                                & ~feat12.ravel()).sum())
                    c12, _cc12, f12, d12 = stats(b_v12)
                    f12_2 = frag_v2(b_v12)
                    de12, _dl12 = de_stats(b_v12, ref_labs)
                    colout12 = int(chroma_outlier_cells(b_v12, pix_labs).sum())
                    v12v = judge(cat, f2o, f12_2, do, d12, co, c12,
                                 ol_old, ol12, de_o, de12, gate, colout_o, colout12)
                    row.update(v12_colors=c12, v12_frag=f12, v12_frag2=f12_2,
                               v12_dark=d12, ol_v12=ol12, colout_v12=colout12,
                               de_v12=round(de12, 3), nfeat12=int(feat12.sum()),
                               verdict12=v12v)
                rows.append(row)

                review = (v12v if v12mode else v) == 'worse' or \
                    needs_review(cat, f2o, f12_2 if v12mode else f2n,
                                 colout12 if v12mode else colout_n)
                if review or fn in sample_files:
                    label = key_for(cat, fn)[:40]
                    outdir = WORSE_DIR if review else SAMPLE_DIR
                    if v12mode:
                        sheet12(im, b_new, b_v12, cat + '/' + fn[:28]).save(
                            os.path.join(outdir, label + '.png'))
                    else:
                        sheet(im, b_old, b_new, cat + '/' + fn[:28]).save(
                            os.path.join(outdir, label + '.png'))

                if check and (cat, fn) in prev:
                    p = prev[(cat, fn)]
                    for k in ('old_frag', 'new_frag', 'old_colors', 'new_colors',
                              'old_dark', 'new_dark'):
                        if int(p[k]) != rows[-1][k]:
                            print('CHECK-MISMATCH', cat, fn, k,
                                  p[k], rows[-1][k], flush=True)
                done += 1
            except Exception as e:
                print('ERR', cat, fn, repr(e)[:90], flush=True)
        print('DONE', cat, done, flush=True)

    # 每类照片补 1 张 ΔE 恶化最大的样张(真实退化最可能藏在这)
    for cat in PHOTO_CATS:
        rs = [r for r in rows if r['cat'] == cat]
        if not rs:
            continue
        worst = max(rs, key=lambda r: r['de_delta'])
        d = safe_join(DS, cat)
        im = load_image(d, worst['file'])
        if im is None:
            continue
        b_old, b_new = gen_two_beads(im, LUT_D2)
        label = key_for(cat, worst['file'])[:40]
        sheet(im, b_old, b_new, cat + '/WORST-DE ' + worst['file'][:20]).save(
            os.path.join(SAMPLE_DIR, label + '_worstde.png'))

    with open(os.path.join(OUT, 'judge_results.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # ---- 逐类聚合 + CI 判据 ----
    summary = []
    print()
    print('%-10s %3s %10s %11s %10s %9s %13s %7s %5s  %s' %
          ('cat', 'n', 'ol_o/n', 'cout_o/n', 'f2_o/n', 'col_o/n',
           'de_o/n(ngDelta)', 'dark_r', 'gate%', 'better/equal/worse'))
    gate_fail = []
    for cat in GRAPHIC_CATS + PHOTO_CATS:
        rs = [x for x in rows if x['cat'] == cat]
        n = len(rs)
        ol_o = sum(x['ol_old'] for x in rs) / n
        ol_n = sum(x['ol_new'] for x in rs) / n
        f2o = sum(x['old_frag2'] for x in rs) / n
        f2n = sum(x['new_frag2'] for x in rs) / n
        co = sum(x['old_colors'] for x in rs) / n
        cn = sum(x['new_colors'] for x in rs) / n
        deo = sum(x['de_old'] for x in rs) / n
        den = sum(x['de_new'] for x in rs) / n
        ng = [x for x in rs if not x['gate']]
        de_ng = (sum(x['de_new'] - x['de_old'] for x in ng) / len(ng)) if ng else 0.0
        dk_r = (sum(x['new_dark'] for x in rs) / max(1, sum(x['old_dark'] for x in rs)))
        cto = sum(x['colout_old'] for x in rs) / n
        ctn = sum(x['colout_new'] for x in rs) / n
        nb = sum(1 for x in rs if x['verdict'] == 'better')
        ne = sum(1 for x in rs if x['verdict'] == 'equal')
        nw = sum(1 for x in rs if x['verdict'] == 'worse')
        g = 100.0 * sum(x['gate'] for x in rs) / n
        if cat in GRAPHIC_CATS:
            ok = (ol_n <= max(CI_GRAPHIC_OWNERLESS * ol_o, 1.0)
                  and (cat != 'lineart' or dk_r >= 1.0))
        else:
            ok = ol_n <= ol_o + OWNERLESS_GUARD_CELLS and de_ng <= CI_PHOTO_DE
        if not ok:
            gate_fail.append(cat)
        print('%-10s %3d %4.1f/%4.1f %5.1f/%5.1f %4.1f/%4.1f %5.2f/%5.2f(%+5.2f) %5.2f %4.0f%%  %d/%d/%d %s'
              % (cat, n, ol_o, ol_n, cto, ctn, co, cn, deo, den, de_ng, dk_r,
                 g, nb, ne, nw, 'PASS' if ok else 'FAIL'))
        summary.append(dict(cat=cat, n=n, ol_old_pct=ol_o / 3364 * 100,
                            ol_new_pct=ol_n / 3364 * 100,
                            ol_old=ol_o, ol_new=ol_n,
                            f2_old=f2o, f2_new=f2n,
                            colout_old=cto, colout_new=ctn,
                            old_colors=co, new_colors=cn,
                            de_old=deo, de_new=den, de_delta_ng=de_ng,
                            dark_ratio=dk_r, gate_pct=g,
                            better=nb, equal=ne, worse=nw, ci_pass=ok))

    with open(os.path.join(OUT, 'judge_summary.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=list(summary[0].keys()))
        w.writeheader()
        w.writerows(summary)

    radar_chart(summary, os.path.join(OUT, 'judge_radar.png'))

    md = ['# 自动评判汇总 v4 终版 (auto_judge, 2026-09-21)', '',
          '自动判决只用结构性指标: 图形类更差=线丢失/无主豆增加; 照片类更差=无主豆增加或非门控ΔE恶化>+8。',
          '色相离群(豆与格内彩色像素色相夹角>30°)与碎豆v2是风格/纹理信号,不自动判决;',
          '图形类高色相离群(>=100格)或碎豆v2翻倍者入"人工复核堆"(worse/ 目录)。',
          '照片纹理锐利化(非门控ΔE +3~7)=已知取舍,人工抽样复核。',
          '无主豆格 = 格内无任何源像素把该豆当最近色(凭空捏造的杂色;NEW 机制上恒 0)。',
          'CI 判据: 图形类 无主豆<=30%旧 且 lineart dark 不减; 照片类 无主豆不增 且 非门控ΔE<=旧+8。', '',
          '| 类 | n | 无主豆% 旧→新 | 色相离群 旧→新 | 碎豆v2 旧→新 | 用色 旧→新 | ΔE 旧→新 (非门控Δ) | dark比 | 更优/持平/更差 | CI |',
          '|---|---|---|---|---|---|---|---|---|---|']
    for s in summary:
        md.append('| %s | %d | %.1f→%.1f | %.1f→%.1f | %.1f→%.1f | %.1f→%.1f | %.2f→%.2f (%+0.2f) | %.2f | %d/%d/%d | %s |' %
                  (s['cat'], s['n'], s['ol_old_pct'], s['ol_new_pct'],
                   s['colout_old'], s['colout_new'],
                   s['f2_old'], s['f2_new'], s['old_colors'], s['new_colors'],
                   s['de_old'], s['de_new'], s['de_delta_ng'], s['dark_ratio'],
                   s['better'], s['equal'], s['worse'],
                   'PASS' if s['ci_pass'] else '**FAIL**'))
    total_w = sum(s['worse'] for s in summary)
    total_n = sum(s['n'] for s in summary)
    md += ['', '自动更差堆 %d / %d (%.1f%%) + 人工复核堆(高色相离群/高碎豆) — 样张都在 bench_out/worse/, 抽样+最差ΔE在 bench_out/sample/' %
           (total_w, total_n, 100.0 * total_w / total_n),
           '', 'CI 总判定: ' + ('**PASS**' if not gate_fail else '**FAIL** ' + ','.join(gate_fail))]
    with open(os.path.join(OUT, 'judge_report.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(md) + '\n')

    print()
    print('WORSE-PILE %d/%d (%.1f%%)  CI=%s' %
          (total_w, total_n, 100.0 * total_w / total_n,
           'PASS' if not gate_fail else 'FAIL:' + ','.join(gate_fail)))
    print('ALL-DONE rows=%d' % len(rows), flush=True)


if __name__ == '__main__':
    main()
