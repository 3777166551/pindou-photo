# -*- coding: utf-8 -*-
"""350 寮犵湡瀹炲浘鐗囧熀鍑?numpy 鍚戦噺鍖?涓?APP 鐨?4bit-LUT 璇箟涓€鑷?銆?V1 鐜扮姸 = 鐩掑钩鍧?sRGB) + Lab 娆ф皬鏈€杩?V11 鏂? = 鍏堥厤鍚庢姇(4bit LUT + de2000 + 鏍煎唴澶氭暟绁? + 娆犳洕鐓х墖鑷姩鎻愪寒
杈撳嚭: bench_out/bench_results.csv, summary_by_category.csv, sheets/*.png
"""
import os, sys, csv, hashlib, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from algo_bakeoff import PAL, rgb_lab, de2000
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
DS = os.path.join(HERE, 'datasets')
OUT = os.path.join(HERE, 'bench_out')
SHEETS = os.path.join(OUT, 'sheets')
os.makedirs(SHEETS, exist_ok=True)

GRID, SRC = 58, 464
B = SRC // GRID            # 8px 婧愬潡
CELL = 9
PLATE = (243, 239, 249)
PAL_N = len(PAL)
PAL_LAB = np.array([rgb_lab(c) for c in PAL])

def bucket_centers():
    keys = np.arange(4096)
    r = ((keys >> 8) & 15) * 17 + 8
    g = ((keys >> 4) & 15) * 17 + 8
    b = (keys & 15) * 17 + 8
    return np.stack([r, g, b], axis=1)

def lut_euclid():
    """V1 鐢?妗朵腑蹇?Lab 娆ф皬鏈€杩戣眴涓嬫爣(4096)"""
    rgb = bucket_centers()
    labs = np.array([rgb_lab(tuple(c)) for c in rgb])
    d = ((labs[:, None, :] - PAL_LAB[None, :, :]) ** 2).sum(axis=2)
    return d.argmin(axis=1)

def lut_de2000():
    """V11 鐢?妗朵腑蹇?CIEDE2000 鏈€杩戣眴涓嬫爣(4096)"""
    rgb = bucket_centers()
    out = np.zeros(4096, dtype=np.int64)
    for k in range(4096):
        lab = rgb_lab((int(rgb[k][0]), int(rgb[k][1]), int(rgb[k][2])))
        best, bd = 0, 1e18
        for i, p in enumerate(PAL_LAB):
            d = de2000(lab, p)
            if d < bd:
                bd, best = d, i
        out[k] = best
    return out

def lab_of_blocks(rgb_blocks):
    return np.array([rgb_lab(tuple(row)) for row in rgb_blocks])

def cell_mean(img):
    a = np.asarray(img, dtype=np.float64).reshape(GRID, B, GRID, B, 3)
    return a.mean(axis=(1, 3))

def quantize(a):
    a64 = a.astype(np.int64)
    return (a64[..., 0] >> 4 << 8) | (a64[..., 1] >> 4 << 4) | (a64[..., 2] >> 4)

def vote_chart(img, lut, dark, gate_gain=1.0, gate_sat=1.0):
    """鍏堥厤鍚庢姇:閫愬儚绱犻噺鍖栨煡 LUT,鏍煎唴澶氭暟绁?+ 绾挎潯鏁戞彺
    (浜綆褰╁簳涓?4%~45% 娣辫眴灏戞暟 鈫?鎶曟繁璞嗕紬鏁?缁嗙嚎涓嶈鎶规帀)"""
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
            if lab_m[0] > 60 and chroma < 20 and dk >= blk.size * 10 and dk <= blk.size * 45:
                out[gy, gx] = int(np.bincount(dark_sel, minlength=PAL_N).argmax())
    return out

PAL_DARK = np.array([l[0] < 45 for l in PAL_LAB])

def stats(beads):
    """beads: (58,58) 鈫?鐢ㄨ壊 / 鍚岃壊杩為€氬尯鎬绘暟 / 纰庤眴(鍖?=2 鏍? / 娣辫眴鏍兼暟(绾夸繚鐣?"""
    colors = int(np.unique(beads.ravel()).size)
    seen = np.zeros((GRID, GRID), dtype=bool)
    comp_total = 0
    frag = 0
    dark = 0
    for gy in range(GRID):
        for gx in range(GRID):
            if PAL_LAB[beads[gy, gx]][0] < 45:
                dark += 1
            if seen[gy, gx]:
                continue
            c = beads[gy, gx]
            stack = [(gy, gx)]
            seen[gy, gx] = True
            size = 0
            while stack:
                y, x = stack.pop()
                size += 1
                for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny, nx = y+dy, x+dx
                    if 0 <= ny < GRID and 0 <= nx < GRID and \
                       not seen[ny, nx] and beads[ny, nx] == c:
                        seen[ny, nx] = True
                        stack.append((ny, nx))
            comp_total += 1
            if size <= 2:
                frag += size
    return colors, comp_total, frag, dark

def chart_img(beads):
    img = Image.new('RGB', (GRID*CELL, GRID*CELL), PLATE)
    d = ImageDraw.Draw(img)
    r = CELL*0.42
    for gy in range(GRID):
        for gx in range(GRID):
            cx, cy = gx*CELL + CELL/2, gy*CELL + CELL/2
            d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=tuple(PAL[beads[gy, gx]]))
    return img

def sheet(src_im, b_old, b_new, label):
    s = src_im.resize((GRID*CELL, GRID*CELL))
    out = Image.new('RGB', (GRID*CELL*3 + 24, GRID*CELL + 30), (255, 255, 255))
    out.paste(s, (0, 30))
    out.paste(chart_img(b_old), (GRID*CELL+12, 30))
    out.paste(chart_img(b_new), (GRID*CELL*2+24, 30))
    d = ImageDraw.Draw(out)
    d.text((6, 8), label + '  SRC | OLD | NEW', fill=(29, 27, 32))
    return out

def key_for(cat, fn):
    h = hashlib.sha256(fn.encode('utf-8')).hexdigest()
    return cat + '_' + h

def main():
    print('building LUTs...', flush=True)
    LUT_E = lut_euclid()
    LUT_D2 = lut_de2000()
    print('LUTs ready', flush=True)

    cats = ['cartoons', 'lineart', 'pixelart', 'landscape', 'portrait', 'food', 'random']
    rows = []
    for cat in cats:
        d = os.path.join(DS, cat)
        if not os.path.isdir(d):
            continue
        files = sorted(f for f in os.listdir(d)
                       if f.lower().endswith(('.jpg', '.jpeg', '.png'))
                       and os.path.getsize(os.path.join(d, f)) > 3000)
        files = files[:50]
        done = 0
        for fn in files:
            try:
                im = Image.open(os.path.join(d, fn)).convert('RGB')
                side = min(im.size)
                if side < 100:
                    continue
                im = im.crop(((im.width-side)//2, (im.height-side)//2,
                              (im.width+side)//2, (im.height+side)//2))
                im = im.resize((SRC, SRC), Image.LANCZOS)
                a = np.asarray(im, dtype=np.float64)

                means = cell_mean(im)
                labs = lab_of_blocks(means.reshape(-1, 3))
                dd = ((labs[:, None, :] - PAL_LAB[None, :, :]) ** 2).sum(axis=2)
                b_old = dd.argmin(axis=1).reshape(GRID, GRID)

                lum = float((a @ np.array([0.299, 0.587, 0.114])).mean())
                buckets = int(np.unique(quantize(a)).size)
                gate = (buckets >= 64 and lum < 125)
                if gate:
                    gain = min(1.35, 125.0 / max(1.0, lum))
                    b_new = vote_chart(im, LUT_D2, PAL_DARK, gain, 1.10)
                else:
                    b_new = vote_chart(im, LUT_D2, PAL_DARK)

                co, cc, fo, do = stats(b_old)
                cn, c2, fn2, dn = stats(b_new)
                agree = float((b_old == b_new).mean() * 100)
                rows.append(dict(cat=cat, file=fn, gate=int(gate),
                                 old_colors=co, new_colors=cn,
                                 old_comp=cc, new_comp=c2,
                                 old_frag=fo, new_frag=fn2,
                                 old_dark=do, new_dark=dn,
                                 agree_pct=round(agree, 1)))
                done += 1
                if done in (1, 2) or (cat == 'random' and done in (1, 2, 3)):
                    key = key_for(cat, fn)
                    sheet(im, b_old, b_new, key).save(
                        os.path.join(SHEETS, key + '.png'))
            except Exception as e:
                print('ERR', cat, fn, repr(e)[:90], flush=True)
            if done % 25 == 0 and done:
                print('progress', cat, done, flush=True)
        print('DONE', cat, done, flush=True)

    with open(os.path.join(OUT, 'bench_results.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    print()
    print('%-10s %5s %9s %9s %9s %9s %9s %8s' %
          ('cat', 'n', 'old_col', 'new_col', 'old_frag', 'new_frag', 'agree%', 'gate%'))
    summ = []
    for cat in cats:
        rs = [x for x in rows if x['cat'] == cat]
        if not rs:
            continue
        n = len(rs)
        m = lambda k: sum(x[k] for x in rs) / n
        g = 100.0 * sum(x['gate'] for x in rs) / n
        print('%-10s %5d %9.1f %9.1f %9.1f %9.1f %9.1f %8.0f' %
              (cat, n, m('old_colors'), m('new_colors'), m('old_frag'),
               m('new_frag'), m('agree_pct'), g))
        summ.append(dict(cat=cat, n=n, old_colors=m('old_colors'),
                         new_colors=m('new_colors'), old_frag=m('old_frag'),
                         new_frag=m('new_frag'), agree_pct=m('agree_pct'), gate_pct=g))
    with open(os.path.join(OUT, 'summary_by_category.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=list(summ[0].keys()))
        w.writeheader()
        w.writerows(summ)
    print('ALL-DONE rows=%d' % len(rows), flush=True)

if __name__ == '__main__':
    main()

