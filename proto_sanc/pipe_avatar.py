# -*- coding: utf-8 -*-
"""捏脸模式原型 v2:全自动属性检测 + Q 版部件拼装。
布局按 Q 版铁律:头占画面 2/3、3 豆高大眼+白高光、头身相连、锯齿刘海。
侧发/长发检测改用 U²-Net 人区 + 发色 Lab 匹配(不再依赖 ParseNet 的脸区限制)。"""
import os
import sys

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import cv2
import numpy as np

import sanc_common as C
import pipe_a
import pipe_symbol as PS

GW, GH = 29, 32
WHITE_I = PS.WHITE_I
BLACK_I = PS.BLACK_I
BROWN_I = PS.BROWN_I
DARK_I = PS.DARK_I
PINK_I = PS.nearest_pal((244, 143, 177))


def _frac(mask, x0, y0, x1, y1):
    h, w = mask.shape
    x0 = max(0, int(x0)); y0 = max(0, int(y0))
    x1 = min(w, int(x1)); y1 = min(h, int(y1))
    if x1 <= x0 or y1 <= y0:
        return 0.0
    return float(mask[y0:y1, x0:x1].mean())


def detect_attrs(img, person, cls_full, face):
    h, w = img.shape[:2]
    x, y, fw, fh = [float(v) for v in face[:4]]
    a = {}
    hair = cls_full == 13
    a['bangs'] = _frac(hair, x + .15 * fw, y + .02 * fh, x + .85 * fw, y + .30 * fh) > .25
    # 侧发/双马尾:脸颊两侧的人区像素且颜色接近发色(ParseNet 管不到脸外)
    hs = hair.sum() > 300
    hair_med = np.median(img[hair], axis=0) if hs else np.array((62, 39, 35), np.float32)
    hair_lab = PS._lab1(hair_med)

    def hair_like(reg_mask):
        if reg_mask.sum() < 50:
            return 0.0
        cols = img[reg_mask]
        d = ((C.to_lab(cols).reshape(-1, 3) - hair_lab.reshape(1, 3)) ** 2).sum(-1)
        return float((d < 2500).mean())

    lbox = np.zeros((h, w), bool)
    lbox[max(0, int(y + .5 * fh)):min(h, int(y + 1.7 * fh)),
         max(0, int(x - .55 * fw)):min(w, int(x + .05 * fw))] = True
    rbox = np.zeros((h, w), bool)
    rbox[max(0, int(y + .5 * fh)):min(h, int(y + 1.7 * fh)),
         min(w, int(x + .95 * fw)):min(w, int(x + 1.55 * fw))] = True
    lbox &= person > .5
    rbox &= person > .5
    left, right = hair_like(lbox), hair_like(rbox)
    a['side'] = left > .5 and right > .5
    bbox = np.zeros((h, w), bool)
    bbox[max(0, int(y + 1.2 * fh)):min(h, int(y + 2.4 * fh)),
         max(0, int(x - .8 * fw)):min(w, int(x + 1.8 * fw))] = True
    bbox &= person > .5
    a['long'] = hair_like(bbox) > .4 or hair.sum() > .6 * fw * fh
    # 长发兜底: B 箱被浅色衣物稀释时比率不足(me_girl 实测 0.314, 马尾
    # 垂在白衣肩前), 但发量/脸框面积是发型无关的强信号, 披肩/马尾显著
    a['twin'] = a['side'] and a['long']
    a['hat'] = _frac(cls_full == 14, x - .1 * fw, y - .4 * fh, x + 1.1 * fw, y + .4 * fh) > .30
    a['glasses'] = float((cls_full == 3).sum()) > .004 * w * h
    a['smile'] = _frac(cls_full == 10, x + .2 * fw, y + .5 * fh, x + .8 * fw, y + 1.0 * fh) > .012
    sk = cls_full == 1
    a['skin_i'] = PS.nearest_in(PS.SKIN_SUB, np.median(img[sk], axis=0) if sk.sum() > 100
                                else (229, 193, 177))
    a['hair_i'] = PS.nearest_in(PS.HAIR_SUB, hair_med)
    ht = (cls_full == 14).sum() > 300
    a['hat_i'] = PS.nearest_pal(np.median(img[cls_full == 14], axis=0)) if ht else None
    lp = ((cls_full == 11) | (cls_full == 12)).sum() > 60
    a['lip_i'] = PS.nearest_pal(np.median(img[(cls_full == 11) | (cls_full == 12)], axis=0)) \
        if lp else PINK_I
    chest = np.zeros((h, w), bool)
    chest[max(0, int(y + 1.25 * fh)):min(h, int(y + 2.3 * fh)),
          max(0, int(x - .7 * fw)):min(w, int(x + 1.7 * fw))] = True
    chest &= person > .5
    if chest.sum() > 200:
        cols_rgb = img[chest]
        pts = C.to_lab(cols_rgb).reshape(-1, 3).astype(np.float32)
        crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 16, 0.5)
        _, lbl, cen = cv2.kmeans(pts, 2, None, crit, 1, cv2.KMEANS_PP_CENTERS)
        lum = cen[:, 0]
        shirt, accent = (0, 1) if lum[0] >= lum[1] else (1, 0)
        a['shirt_i'] = PS.nearest_pal(np.median(cols_rgb[lbl[:, 0] == shirt], axis=0))
        a['accent_i'] = PS.nearest_pal(np.median(cols_rgb[lbl[:, 0] == accent], axis=0))
    else:
        a['shirt_i'] = PS.nearest_pal((245, 240, 225))
        a['accent_i'] = DARK_I
    return a


def draw(a):
    g = np.full((GH, GW), WHITE_I, np.int32)

    def head_e(xx, yy):
        return ((xx - 14.5) / 10.0) ** 2 + ((yy - 11.0) / 9.5) ** 2 <= 1.0

    def face_e(xx, yy):
        return ((xx - 14.5) / 7.5) ** 2 + ((yy - 13.0) / 7.5) ** 2 <= 1.0

    for yy in range(GH):
        for xx in range(GW):
            if not head_e(xx, yy):
                continue
            if yy <= 9 or not face_e(xx, yy):
                g[yy, xx] = a['hat_i'] if a['hat_i'] is not None else a['hair_i']
            elif yy in (10, 11) and (xx % 3 != 1):  # 锯齿刘海
                g[yy, xx] = a['hat_i'] if a['hat_i'] is not None else a['hair_i']
            else:
                g[yy, xx] = a['skin_i']
    # 帽檐
    if a['hat_i'] is not None:
        for xx in range(6, 24):
            if head_e(xx, 10):
                g[10, xx] = a['accent_i']
    # 双马尾(两侧,渐细)
    if a['twin']:
        for yy in range(10, 27):
            t = (yy - 10) // 6
            for xx in range(2 + t, 7 - t):
                g[yy, xx] = a['hair_i']
                g[yy, 28 - xx] = a['hair_i']
    elif a['long']:
        for yy in range(10, 25):
            for xx in range(4, 7):
                g[yy, xx] = a['hair_i']
                g[yy, 28 - xx] = a['hair_i']
    elif a['side']:
        for yy in range(10, 16):
            for xx in range(5, 7):
                g[yy, xx] = a['hair_i']
                g[yy, 28 - xx] = a['hair_i']
    # 眼:3 豆高 2 豆宽 + 左上白高光
    for ex in (10, 18):
        g[12:15, ex:ex + 2] = BLACK_I
        g[12, ex] = WHITE_I
    # 眼镜
    if a['glasses']:
        for ex in (9, 10, 11, 12, 17, 18, 19, 20):
            g[11, ex] = DARK_I
        for ex in (13, 14, 15, 16):
            g[11, ex] = DARK_I
    # 腮红
    g[16, 8] = PINK_I
    g[16, 21] = PINK_I
    # 嘴
    if a['smile']:
        g[17, 13] = a['lip_i']
        g[17, 14] = a['lip_i']
        g[17, 15] = a['lip_i']
    else:
        g[17, 14] = a['lip_i']
    # 身体(与头相连):肩领行 + 梯形衣身
    for xx in range(8, 22):
        g[21, xx] = a['accent_i']
        g[22, xx] = a['accent_i'] if abs(xx - 14) > 3 else a['shirt_i']
    for yy in range(23, GH):
        half = 6 + min((yy - 23) // 2, 3)
        for xx in range(14 - half, 15 + half):
            g[yy, xx] = a['shirt_i']
    g[23, 14] = a['accent_i']
    g[24, 14] = a['accent_i']
    # 轮廓:非白区边界一圈黑
    solid = g != WHITE_I
    edge = np.zeros_like(solid)
    edge[1:-1, 1:-1] = solid[1:-1, 1:-1] & ~(solid[:-2, 1:-1] & solid[2:, 1:-1]
                                             & solid[1:-1, :-2] & solid[1:-1, 2:])
    g[edge] = BLACK_I
    return g


def render(g, cell):
    h, w = g.shape
    canvas = np.full((h * cell, w * cell, 3), 245, np.uint8)
    pal8 = PS.PAL.astype(np.uint8)
    r = max(2, int(cell * 0.46))
    for yy in range(h):
        for xx in range(w):
            col = tuple(int(v) for v in pal8[g[yy, xx]])[::-1]
            cv2.circle(canvas, (xx * cell + cell // 2, yy * cell + cell // 2), r, col, -1,
                       cv2.LINE_AA)
    return canvas


def render_flat(g, px):
    h, w = g.shape
    return cv2.resize(PS.PAL[g].reshape(h, w, 3).astype(np.uint8), (w * px, h * px),
                      interpolation=cv2.INTER_NEAREST)


def process(name):
    r = PS.prep(name)
    if r is None:
        print('[av] %s missing' % name, flush=True)
        return
    img, _art, person, cls_full, stem = r
    bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
    faces = C.detect_faces(bgr8)
    if not faces:
        print('[av] %s no face' % name, flush=True)
        return
    if person is None:
        person = np.ones(img.shape[:2], np.float32)
    a = detect_attrs(img, person, cls_full if cls_full is not None else
                     np.zeros(img.shape[:2], np.uint8), faces[0])
    a_show = {k: (v if isinstance(v, bool) else int(v)) if isinstance(v, (bool, np.integer))
              else v for k, v in a.items()}
    print('[av] %s attrs=%s' % (name, a_show), flush=True)
    g = draw(a)
    flat = render_flat(g, 14)
    beads = render(g, 14)
    C.save_rgb('av_%s_flat.png' % stem, flat)
    C.save_rgb('av_%s_beads.png' % stem, beads)
    C.sheet('sheet_av_%s.png' % stem, [
        (img, 'Photo'),
        (flat, 'Avatar auto (%d colors)' % len(set(g.ravel().tolist()))),
        (beads, 'Avatar beads 29x32'),
    ])
    print('[av] %s done colors=%d' % (stem, len(set(g.ravel().tolist()))), flush=True)


if __name__ == '__main__':
    C.idle_priority()
    for n in ('me_girl.jpg', 'face000.jpg', 'user_girl.jpg'):
        try:
            process(n)
        except Exception:
            import traceback
            print('[av] ERR', traceback.format_exc(limit=4), flush=True)
