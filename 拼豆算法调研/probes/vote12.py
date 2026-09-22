# -*- coding: utf-8 -*-
"""V12 投票管线(纯计算,无文件 IO):
①贴色调救援: 触发救援时选"格内深豆像素均色"的最近豆(全色板),不再选深豆众数
  ——硬黑线不变,柔和浅线恢复柔和;
②相似色孤格吸附: 无同色 4 邻的孤格,若存在 ΔE<ABSORB_DE 的邻居豆则改为最多数相似邻居
  ——吸收纯色区噪声翻点,虚线黑点(与白底 ΔE 大)不受影响。
vote_chart12 返回 (豆格, 特征格掩码): 特征格=rescue/absorb 作用过的格,
ownerless 度量应将其记为合法(颜色有源,非凭空捏造)。
"""
import math

import numpy as np

from algo_bakeoff import rgb_lab

ABSORB_DE = 12.0


def vote_chart12(img, lut, pal_lab, pal_dark, grid, src, b, lo=10, hi=45,
                 gate_gain=1.0, gate_sat=1.0):
    """V12 = 多数票 + 贴色调救援 + 相似色孤格吸附。
    img: PIL RGB 图(已按管线门控语义传入); grid=58, src=464, b=8"""
    a = np.asarray(img, dtype=np.float64)
    if gate_gain > 1.0:
        a = np.clip(a * gate_gain, 0, 255)
        gray = a @ np.array([0.299, 0.587, 0.114])
        a = np.clip(gray[..., None] + (a - gray[..., None]) * gate_sat, 0, 255)
    idx = lut[quantize(a)]
    blocks = idx.reshape(grid, b, grid, b)
    means = a.reshape(grid, b, grid, b, 3).mean(axis=(1, 3))
    n_pal = len(pal_lab)
    out = np.zeros((grid, grid), dtype=np.int64)
    feature = np.zeros((grid, grid), dtype=bool)
    for gy in range(grid):
        for gx in range(grid):
            blk = blocks[gy, :, gx, :].ravel()
            out[gy, gx] = int(np.bincount(blk, minlength=n_pal).argmax())
            if blk.size < 8:
                continue
            mr, mg, mb = means[gy, gx]
            lab_m = rgb_lab((int(mr), int(mg), int(mb)))
            chroma = math.sqrt(lab_m[1] ** 2 + lab_m[2] ** 2)
            dark_sel = pal_dark[blk]
            dk = dark_sel.sum() * 100
            if lab_m[0] > 60 and chroma < 20 and dk >= blk.size * lo and dk <= blk.size * hi:
                cellrgb = a[gy * b:(gy + 1) * b, gx * b:(gx + 1) * b].reshape(-1, 3)
                rgbm = cellrgb[dark_sel].mean(axis=0)
                lab_t = rgb_lab((int(rgbm[0]), int(rgbm[1]), int(rgbm[2])))
                out[gy, gx] = int(((pal_lab - lab_t) ** 2).sum(axis=1).argmin())
                feature[gy, gx] = True
    # 相似色孤格吸附(单趟,基于原格判定,防止级联)
    g2 = out.copy()
    for gy in range(grid):
        for gx in range(grid):
            c = out[gy, gx]
            nbs = [out[y, x] for y in range(max(0, gy - 1), min(grid, gy + 2))
                   for x in range(max(0, gx - 1), min(grid, gx + 2))
                   if (y, x) != (gy, gx)]
            if any(n == c for n in nbs):
                continue
            cands = [n for n in nbs
                     if math.sqrt(((pal_lab[n] - pal_lab[c]) ** 2).sum()) < ABSORB_DE]
            if cands:
                g2[gy, gx] = max(set(cands), key=cands.count)
                feature[gy, gx] = True
    return g2, feature


def quantize(a):
    a64 = a.astype(np.int64)
    return (a64[..., 0] >> 4 << 8) | (a64[..., 1] >> 4 << 4) | (a64[..., 2] >> 4)
