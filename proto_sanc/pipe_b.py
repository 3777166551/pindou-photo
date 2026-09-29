# -*- coding: utf-8 -*-
"""路线B原型:「二次元图纸模式」vs 现行量化,同豆色板/同板数。
现行=整图缩网格+逐格最近豆;动漫模式=双边平滑+中心加权取色+限色 kmeans+关抖动+墨线豆描边。"""
import os
import time

import cv2
import numpy as np

import sanc_common as C
from bead_palette_data import PALETTE_90

SIZES = [58, 87]
INK_CELL_THR = 0.30
MAXC = {58: 18, 87: 26}   # 限色数(实验参数)

PAL = np.asarray(PALETTE_90, np.float32)
DARK_IDX = int(((PAL - np.array([18, 18, 18], np.float32)) ** 2).sum(-1).argmin())


def _kmeans_lab(pts, k):
    """Lab 点聚类到 k 个代表色。原型用 kmeans,APP 内可用既有 mergeToMaxColors。"""
    k = min(k, len(np.unique(pts.round(2), axis=0)) or k)
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 0.5)
    _, labels, centers = cv2.kmeans(pts.astype(np.float32), k, None, crit, 1,
                                    cv2.KMEANS_PP_CENTERS)
    return centers, labels.reshape(-1)


def quantize_current(art, s):
    """现行引擎主路径复现:AREA 缩到网格 → 最近豆。"""
    cells = cv2.resize(art, (s, s), interpolation=cv2.INTER_AREA)
    idx, dist = C.nearest_bead(cells, PAL)
    return cells, idx, float(dist.mean())


def quantize_anime(art, s, ink):
    """动漫图纸模式:平滑 + 中心加权取色 + 限色 + 描边豆。"""
    sm = art
    for _ in range(2):
        sm = cv2.bilateralFilter(np.clip(sm, 0, 255).astype(np.uint8), 9, 60, 60)
    sm = sm.astype(np.float32)
    # 中心加权取色:裁掉边缘 22% 再缩,避免格子里混进邻格背景(对比感知降采样的简化版)
    h, w = sm.shape[:2]
    my, mx = int(h * 0.22), int(w * 0.22)
    core = sm[my:h - my, mx:w - mx]
    cells = cv2.resize(core, (s, s), interpolation=cv2.INTER_AREA)
    pts = C.to_lab(cells).reshape(-1, 3)          # 全程 Lab 直通
    pal_lab = C.to_lab(PAL)
    centers, labels = _kmeans_lab(pts, MAXC[s])
    bidx = ((centers.reshape(-1, 1, 3) - pal_lab.reshape(1, -1, 3)) ** 2).sum(-1).argmin(1)
    idx = bidx[labels]
    dist = np.sqrt(((pts - pal_lab[bidx[labels]]) ** 2).sum(-1))
    if ink is not None:
        ink_cells = cv2.resize(ink, (s, s), interpolation=cv2.INTER_AREA).reshape(-1)
        idx = np.where(ink_cells > INK_CELL_THR, DARK_IDX, idx)
    return cells, idx.astype(int), float(dist.mean())


def render_flat(idx, s, cell_px):
    g = PAL[idx].reshape(s, s, 3).astype(np.uint8)
    return cv2.resize(g, (s * cell_px, s * cell_px), interpolation=cv2.INTER_NEAREST)


def render_circles(idx, s, cell=14):
    canvas = np.full((s * cell, s * cell, 3), 40, np.uint8)
    r = int(cell * 0.44)
    pal8 = PAL.astype(np.uint8)
    for yy in range(s):
        cy = yy * cell + cell // 2
        for xx in range(s):
            cx = xx * cell + cell // 2
            col = tuple(int(v) for v in pal8[idx[yy * s + xx]])[::-1]
            cv2.circle(canvas, (cx, cy), r, col, -1, cv2.LINE_AA)
    return canvas


def pick_art(stem, orig):
    # 优先未叠线变体:动漫模式的墨线由描边豆映射承担,避免双重描边
    for tag in ('%s_A2_ghibli_face' % stem, '%s_A2_ghibli_face_line' % stem,
                '%s_A1_shinkai' % stem, '%s_A1_shinkai_line' % stem):
        p = os.path.join(C.OUT, tag + '.png')
        if os.path.isfile(p):
            img = C.load_rgb(p)
            if img is not None:
                return img, tag
    return orig, 'original'


def process(name):
    stem = os.path.splitext(name)[0]
    src_path = None
    for d in (C.ROOT,
              os.path.join(C.REPO, 'tools', 'bgtest', 'people'),
              os.path.join(C.REPO, 'tools', 'bgtest', 'images')):
        p = os.path.join(d, name)
        if os.path.isfile(p):
            src_path = p
            break
    if src_path is None:
        return {'name': name, 'error': 'not found'}
    orig = C.load_rgb(src_path)
    art, art_tag = pick_art(stem, orig)
    ink = C.lines_ink(art)
    out = {'name': name, 'art': art_tag}

    for s in SIZES:
        t0 = time.perf_counter()
        _, idx_cur, d_cur = quantize_current(art, s)
        _, idx_anm, d_anm = quantize_anime(art, s, ink)
        el = round(time.perf_counter() - t0, 2)
        u_cur, u_anm = len(set(idx_cur.tolist())), len(set(idx_anm.tolist()))
        dark_pct = float((idx_anm == DARK_IDX).mean() * 100)
        out['s%d' % s] = {'meandE_cur': round(d_cur, 1), 'meandE_anm': round(d_anm, 1),
                          'colors_cur': u_cur, 'colors_anm': u_anm,
                          'dark_bead_pct': round(dark_pct, 1), 'sec': el}
        cell = 560 // s
        cur_big = render_flat(idx_cur, s, cell)
        anm_big = render_flat(idx_anm, s, cell)
        anm_cir = render_circles(idx_anm, s, cell)
        C.save_rgb('b_%s_s%d_cur.png' % (stem, s), cur_big)
        C.save_rgb('b_%s_s%d_anime.png' % (stem, s), anm_big)
        C.save_rgb('b_%s_s%d_anime_circles.png' % (stem, s), anm_cir)
        C.sheet('sheet_b_%s_s%d.png' % (stem, s), [
            (art, 'Stylized input (%s)' % art_tag),
            (cur_big, 'B: current quantize'),
            (anm_big, 'B: anime mode (limit %d colors)' % MAXC[s]),
            (anm_cir, 'B: anime mode beads'),
        ])
    return out


def main():
    results = []
    for name in ['face000.jpg', 'face003.jpg', 'user_girl.jpg', 'home.jpg']:
        try:
            r = process(name)
        except Exception:
            import traceback
            r = {'name': name, 'error': traceback.format_exc(limit=3)}
        print('[pipe_b] %s' % r, flush=True)
        results.append(r)
    return results


if __name__ == '__main__':
    C.idle_priority()
    main()
