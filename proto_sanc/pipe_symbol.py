# -*- coding: utf-8 -*-
"""符号化图纸模式原型:不做照片量化。
ParseNet 分区填充 + 特征点豆(眼黑/眉棕/唇取原色) + U²-Net 抠人 + 轮廓描边豆 + 背景留白。
目标形态 = 官方像素拼豆图纸:大色块、个位数色、简单线条。"""
import os
import sys
import time

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import cv2
import numpy as np

import sanc_common as C
import pipe_a
from bead_palette_data import PALETTE_90

PAL = np.asarray(PALETTE_90, np.float32)
_PALLAB = None


def _pal_lab():
    global _PALLAB
    if _PALLAB is None:
        _PALLAB = C.to_lab(PAL)
    return _PALLAB


def nearest_pal(rgb):
    v = np.asarray(rgb, np.float32).reshape(1, 1, 3)
    lv = C.to_lab(v)  # 输入也是 RGB,必须先转 Lab(与色板同空间)
    d = ((lv - _pal_lab().reshape(1, -1, 3)) ** 2).sum(-1)[0]
    return int(d.argmin())


BLACK_I = nearest_pal((20, 20, 20))
DARK_I = nearest_pal((74, 74, 74))
BROWN_I = nearest_pal((62, 39, 35))
WHITE_I = nearest_pal((255, 255, 255))


def _hex_idx(hx):
    v = np.array([int(hx[0:2], 16), int(hx[2:4], 16), int(hx[4:6], 16)], np.float32)
    return int(((PAL - v) ** 2).sum(-1).argmin())


# 动漫安全子集:肤色只允许暖肤豆,发色只允许黑/棕/灰豆(防蓝黑头发吸到蓝豆)
SKIN_SUB = [_hex_idx(h) for h in ('FADCC8', 'F5CBA0', 'F2CD9A', 'E3D5AE', 'C8A17B',
                                  'E8A575', 'C3B091', 'CB6843', 'DDBAC2')]
HAIR_SUB = [_hex_idx(h) for h in ('141414', '2B2B2B', '2F211A', '3E2723', '6E2C1B',
                                  '8B4226', '4A4A4A', '757575', 'A9713F', 'A29A8C',
                                  '607D8B', 'C4825A', 'D2A24C')]


def nearest_in(sub, rgb):
    v = np.asarray(rgb, np.float32).reshape(1, 1, 3)
    lv = C.to_lab(v)
    d = ((lv - _pal_lab().reshape(1, -1, 3)) ** 2).sum(-1)[0][sub]
    return int(sub[int(d.argmin())])


def _lab1(rgb_med):
    return C.to_lab(np.asarray(rgb_med, np.float32).reshape(1, 1, 3)).reshape(3)


def region_bead(cells, sel, sub=None):
    """区域众数色(中位数)→最近豆索引。sub 给定时只在子集内选。空区返回 None。"""
    if not sel.any():
        return None
    med = np.median(cells[sel], axis=0)
    return nearest_in(sub, med) if sub else nearest_pal(med)


def symbolize(art, person, cls_full, s):
    """返回 (豆索引网格 s×s, 用到的豆索引列表)。"""
    cells = cv2.resize(np.clip(art, 0, 255).astype(np.uint8), (s, s),
                       interpolation=cv2.INTER_AREA).astype(np.float32)

    def frac(m):
        return cv2.resize(m.astype(np.float32), (s, s), interpolation=cv2.INTER_AREA)

    person_cell = frac(person) > 0.5
    lab = np.full((s, s), -1, np.int32)  # -1bg 0肤 1发 2衣 3眼 4眉 5唇 6嘴
    if cls_full is not None:
        # 本仓库 parsenet 实测类别表:0bg 1肤 2鼻 3镜 4左眼 5右眼 6/7眉 8/9耳
        # 10口 11/12唇 13发 14帽 15耳饰 16颈饰 17颈 18衣
        un = person_cell.copy()
        for keys, v, thr in (((4, 5), 3, 0.15), ((6, 7), 4, 0.20), ((11, 12), 5, 0.20),
                             ((10,), 6, 0.25),
                             ((1, 2, 3, 8, 9, 16, 17), 0, 0.40),
                             ((13, 14), 1, 0.40)):
            take = (frac(np.isin(cls_full, keys)) > thr) & un
            lab[take] = v
            un &= ~take
        lab[un] = 2
    else:
        lab[person_cell] = 2

    # 裸露皮肤(肩/臂)被 ParseNet 漏标为衣服:衣服聚类簇接近肤色中位数时归还肤色
    cl = lab == 2
    if cl.sum() >= 6 and (lab == 0).any():
        skin_l = _lab1(np.median(cells[lab == 0], axis=0))
        pts_rgb = cells[cl]
        pts = C.to_lab(pts_rgb).reshape(-1, 3).astype(np.float32)
        crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 16, 0.5)
        _, lbl, cen = cv2.kmeans(pts, 2, None, crit, 1, cv2.KMEANS_PP_CENTERS)
        lbl = lbl[:, 0]
        cl_flat = np.flatnonzero(cl)
        for i in range(2):
            sel = lbl == i
            if sel.any() and ((_lab1(np.median(pts_rgb[sel], axis=0)) - skin_l) ** 2).sum() < 400:
                lab.reshape(-1)[cl_flat[sel]] = 0

    idx = np.full((s, s), WHITE_I, np.int32)
    skin_i = region_bead(cells, lab == 0, SKIN_SUB)
    hair_i = region_bead(cells, lab == 1, HAIR_SUB)
    for v, bi in ((0, skin_i), (1, hair_i)):
        if bi is not None:
            idx[lab == v] = bi
    cl = lab == 2
    if cl.any():
        if cl.sum() >= 6:
            pts = C.to_lab(cells[cl]).reshape(-1, 3).astype(np.float32)
            crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 16, 0.5)
            _, lbl, cen = cv2.kmeans(pts, 2, None, crit, 1, cv2.KMEANS_PP_CENTERS)
            beads = [nearest_pal(cen[i]) for i in range(2)]
            idx[cl] = np.asarray(beads, np.int32)[lbl[:, 0]]
        else:
            bi = region_bead(cells, cl)
            if bi is not None:
                idx[cl] = bi
    # 特征豆:眼睛纯黑、眉/嘴深棕、唇取原色(素颜→粉)
    lip_i = region_bead(cells, lab == 5)
    idx[lab == 3] = BLACK_I
    idx[lab == 4] = BROWN_I
    idx[lab == 6] = BROWN_I
    if lip_i is not None:
        idx[lab == 5] = lip_i
    # 轮廓描边:剪影边缘一圈黑豆(眼睛除外)
    pb = (lab != -1).astype(np.uint8)
    edge = (pb & ~cv2.erode(pb, np.ones((3, 3), np.uint8))).astype(bool)
    idx[edge & (lab != 3)] = BLACK_I
    # 孤立豆清理(特征豆除外)
    for yy in range(s):
        for xx in range(s):
            if lab[yy, xx] in (3, 4, 5, 6):
                continue
            c = idx[yy, xx]
            nbs = [idx[yy + dy, xx + dx]
                   for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))
                   if 0 <= yy + dy < s and 0 <= xx + dx < s]
            if nbs and len(set(nbs)) == 1 and nbs[0] != c:
                idx[yy, xx] = nbs[0]
    return idx, sorted(set(idx.ravel().tolist()))


def render_flat(idx, s, px):
    return cv2.resize(PAL[idx].reshape(s, s, 3).astype(np.uint8),
                      (s * px, s * px), interpolation=cv2.INTER_NEAREST)


def render_beads(idx, s, cell):
    canvas = np.full((s * cell, s * cell, 3), 245, np.uint8)
    pal8 = PAL.astype(np.uint8)
    r = max(2, int(cell * 0.46))
    for yy in range(s):
        cy = yy * cell + cell // 2
        for xx in range(s):
            cx = xx * cell + cell // 2
            col = tuple(int(v) for v in pal8[idx[yy, xx]])[::-1]
            cv2.circle(canvas, (cx, cy), r, col, -1, cv2.LINE_AA)
    return canvas


def prep(name):
    path = pipe_a.find_img(name)
    if path is None:
        return None
    img = C.load_rgb(path)
    bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
    faces = C.detect_faces(bgr8)
    person = C.u2net_person(img)
    if person is None:
        person = np.zeros(img.shape[:2], np.float32)
        if faces:
            x, y, w, h = [float(v) for v in faces[0][:4]]
            cx, cy = x + w / 2, y + h / 2
            x0 = max(0, int(cx - w * 1.4))
            x1 = min(img.shape[1], int(cx + w * 1.4))
            y0 = max(0, int(cy - h * 1.2))
            person[y0:img.shape[0], x0:x1] = 1.0
        else:
            person[:] = 1.0
    print('[sym] person cov %.2f' % float((person > 0.5).mean()), flush=True)
    cls_full = None
    if faces:
        faces.sort(key=lambda f: f[2] * f[3], reverse=True)
        aligned, minv = C.align_face(img, faces[0])
        if aligned is not None:
            cls = C.face_parse_classes(aligned)
            if cls is not None:
                cls_full = cv2.warpAffine(cls.astype(np.uint8), minv,
                                          (img.shape[1], img.shape[0]),
                                          flags=cv2.INTER_NEAREST,
                                          borderMode=cv2.BORDER_CONSTANT)
    stem = os.path.splitext(name)[0]
    art = None
    for tag in ('%s_A3_wbc_jpface' % stem, '%s_A2b_jpface_face' % stem):
        p = os.path.join(C.OUT, tag + '.png')
        if os.path.isfile(p):
            a = C.load_rgb(p)
            if a is not None:
                art = a
                break
    return img, art, person, cls_full, stem


def process(name):
    r = prep(name)
    if r is None:
        print('[sym] %s not found' % name, flush=True)
        return
    img, art, person, cls_full, stem = r
    t0 = time.perf_counter()
    panels = [(img, 'Original photo')]
    for src_name, src in (('photo', img), ('art', art if art is not None else img)):
        for s in (58, 29):
            idx, used = symbolize(src, person, cls_full, s)
            px = max(1, 560 // s)
            flat = render_flat(idx, s, px)
            beads = render_beads(idx, s, px)
            C.save_rgb('sym_%s_%s_s%d_flat.png' % (stem, src_name, s), flat)
            C.save_rgb('sym_%s_%s_s%d_beads.png' % (stem, src_name, s), beads)
            hexes = ['%02X%02X%02X' % tuple(int(v) for v in PAL[i]) for i in used]
            print('[sym] %s %s s%d colors=%d %s' % (stem, src_name, s, len(used), hexes),
                  flush=True)
            if s == 58:
                panels.append((flat, 'Symbol %s 58x58 (%d colors)' % (src_name, len(used))))
                panels.append((beads, 'Symbol %s beads' % src_name))
    print('[sym] %s total %.2fs' % (stem, time.perf_counter() - t0), flush=True)
    C.sheet('sheet_sym_%s.png' % stem, panels)


if __name__ == '__main__':
    C.idle_priority()
    for n in ('me_girl.jpg', 'face000.jpg'):
        try:
            process(n)
        except Exception:
            import traceback
            print('[sym] ERR', traceback.format_exc(limit=4), flush=True)
