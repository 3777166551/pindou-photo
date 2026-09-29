# -*- coding: utf-8 -*-
"""捏脸 v4:高细节脸部板。头部单独在「对齐空间」渲染成 44 豆宽:
ParseNet 各类(发/肤/眉/眼/鼻/唇)降采样为逐格多数类,再按动漫规范精描——
眼=白底椭圆+黑瞳+双高光,眉/嘴深棕,唇取原色,鼻影,腮红,黑描边。半身身体沿用 v3。"""
import os
import sys

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import cv2
import numpy as np

import sanc_common as C
import pipe_a
import pipe_symbol as PS
import pipe_avatar as PA

GW = 44
CLASSES = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18]
BOOST = {4: 1.8, 5: 1.8, 6: 1.6, 7: 1.6, 10: 1.5, 11: 1.5, 12: 1.5}  # 细部件降采样保活


def stylize(label, a, GH):
    g = np.full((GH, GW), PS.WHITE_I, np.int32)
    m = {0: None, 1: a['skin_i'], 2: a['skin_i'], 8: a['skin_i'], 9: a['skin_i'],
         17: a['skin_i'], 18: None, 13: a['hair_i'], 6: PS.BROWN_I, 7: PS.BROWN_I,
         10: PS.BROWN_I, 11: a['lip_i'], 12: a['lip_i'], 4: PS.BLACK_I, 5: PS.BLACK_I}
    hat = a['hat_i'] if a['hat_i'] is not None else a['hair_i']
    for yy in range(GH):
        for xx in range(GW):
            v = label[yy, xx]
            g[yy, xx] = hat if v == 14 else (m[v] if m[v] is not None else PS.WHITE_I)
    # 眼睛精描:动漫式=黑底椭圆+白高光(非白底黑圈),两眼尺寸统一
    em = ((label == 4) | (label == 5)).astype(np.uint8)
    n, _, stats, _ = cv2.connectedComponentsWithStats(em, 8)
    boxes = [(stats[i, 0], stats[i, 1], stats[i, 2], stats[i, 3])
             for i in range(1, n) if stats[i, 4] >= 2 and stats[i, 2] >= 2]
    if boxes:
        ew = max(6, int(np.median([b[2] for b in boxes])) + 2)
        eh = max(3, int(np.median([b[3] for b in boxes])) + 1)
        for (x, y, w, h) in boxes:
            cx, cy = x + w // 2, y + h // 2
            rx, ry = ew // 2, eh // 2
            for yy in range(cy - ry, cy + ry + 1):
                for xx in range(cx - rx, cx + rx + 1):
                    if 0 <= yy < GH and 0 <= xx < GW:
                        rel = ((xx - cx) / (rx + .4)) ** 2 + ((yy - cy) / (ry + .4)) ** 2
                        if rel <= 1.0:
                            g[yy, xx] = PS.BLACK_I
            hy_, hx_ = cy - max(0, ry - 1), cx - max(1, rx - 1)
            if 0 <= hy_ < GH and 0 <= hx_ < GW:
                g[hy_, hx_] = PS.WHITE_I
            if ry >= 2:
                hy2, hx2 = cy + 1, cx + max(1, rx - 1)
                if 0 <= hy2 < GH and 0 <= hx2 < GW and g[hy2, hx2] == PS.BLACK_I:
                    g[hy2, hx2] = PS.WHITE_I
    # 鼻影:鼻类底部一格深肤
    nm = label == 2
    if nm.sum() >= 2:
        ys, xs = np.where(nm)
        yy, xx = int(ys.max()), int(xs[np.argmax(ys)])
        dark_skin = PS.nearest_in(PS.SKIN_SUB, np.array(
            [PS.PAL[a['skin_i']][0] * .9, PS.PAL[a['skin_i']][1] * .88,
             PS.PAL[a['skin_i']][2] * .85], np.float32))
        g[yy, xx] = dark_skin
    # 腮红:嘴上 2 行、肤色行两端
    mys = np.where(label == 10)[0]
    if len(mys):
        ry_ = int(mys.mean()) - 3
        if 0 <= ry_ < GH:
            row = [xx for xx in range(GW) if label[ry_, xx] in (1, 2, 17)]
            if len(row) >= 6:
                g[ry_, row[0] + 1] = PA.PINK_I
                g[ry_, row[-1] - 1] = PA.PINK_I
    # 轮廓
    solid = g != PS.WHITE_I
    edge = np.zeros_like(solid)
    edge[1:-1, 1:-1] = solid[1:-1, 1:-1] & ~(solid[:-2, 1:-1] & solid[2:, 1:-1]
                                             & solid[1:-1, :-2] & solid[1:-1, 2:])
    g[edge] = PS.BLACK_I
    return g


def process(name):
    path = pipe_a.find_img(name)
    img = C.load_rgb(path)
    bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
    faces = C.detect_faces(bgr8)
    if not faces:
        print('[v4] %s no face' % name, flush=True)
        return
    face = sorted(faces, key=lambda f: f[2] * f[3], reverse=True)[0]
    aligned, minv = C.align_face(img, face)
    Mfwd = cv2.invertAffineTransform(minv)
    k_eff = float(np.hypot(Mfwd[0, 0], Mfwd[1, 0]))
    cls = C.face_parse_classes(aligned)
    if cls is None:
        print('[v4] %s parse fail' % name, flush=True)
        return
    content = np.isin(cls, [1, 2, 4, 5, 6, 7, 10, 11, 12, 13, 14])  # 不含颈(17),防下巴拉长
    ys, xs = np.where(content)
    mx = int(.06 * (xs.max() - xs.min()))
    my = int(.06 * (ys.max() - ys.min()))
    x0, x1 = max(0, xs.min() - mx), min(512, xs.max() + mx)
    y0, y1 = max(0, ys.min() - my), min(512, ys.max() + my)
    crop = cls[y0:y1, x0:x1]
    GH = max(40, min(60, int(round(GW * (y1 - y0) / (x1 - x0)))))
    # 逐类降采样多数投票
    stacks = []
    for c in CLASSES:
        m = cv2.resize((crop == c).astype(np.float32), (GW, GH),
                       interpolation=cv2.INTER_AREA) * BOOST.get(c, 1.0)
        stacks.append(m)
    st = np.stack(stacks, 0)
    ids = np.argmax(st, 0)
    cover = st.max(0)
    label = np.array(CLASSES, np.int32)[ids]
    label[cover < .28] = 0
    # 颜色
    person = C.u2net_person(img)
    if person is None:
        person = np.ones(img.shape[:2], np.float32)
    cls_full = cv2.warpAffine(cls.astype(np.uint8), minv, (img.shape[1], img.shape[0]),
                              flags=cv2.INTER_NEAREST, borderMode=cv2.BORDER_CONSTANT)
    a = PA.detect_attrs(img, person, cls_full, face)
    hp = aligned[cls == 13]  # 发色:取最暗 35% 的中位数(半透明刘海会提亮中位数)
    if len(hp) > 300:
        hl = C.to_lab(hp.reshape(-1, 1, 3)).reshape(-1, 3)[:, 0]
        a['hair_i'] = PS.nearest_in(PS.HAIR_SUB, np.median(hp[hl <= np.percentile(hl, 35)],
                                                          axis=0))
    g = stylize(label, a, GH)
    flat = cv2.resize(PS.PAL[g].reshape(GH, GW, 3).astype(np.uint8),
                      (GW * 11, GH * 11), interpolation=cv2.INTER_NEAREST)
    beads = PA.render(g, 11)
    acrop = aligned[y0:y1, x0:x1].astype(np.uint8)
    C.save_rgb('v4_%s_flat.png' % os.path.splitext(name)[0], flat)
    C.save_rgb('v4_%s_beads.png' % os.path.splitext(name)[0], beads)
    C.sheet('sheet_v4_%s.png' % os.path.splitext(name)[0], [
        (acrop, 'Aligned face'),
        (flat, 'Detailed head %dx%d (%d colors)' % (GW, GH, len(set(g.ravel().tolist())))),
        (beads, 'v4 beads'),
    ])
    print('[v4] %s done %dx%d colors=%d' % (name, GW, GH,
                                            len(set(g.ravel().tolist()))), flush=True)


if __name__ == '__main__':
    C.idle_priority()
    for n in ('me_girl.jpg', 'face000.jpg', 'user_girl.jpg'):
        try:
            process(n)
        except Exception:
            import traceback
            print('[v4] ERR', traceback.format_exc(limit=4), flush=True)
