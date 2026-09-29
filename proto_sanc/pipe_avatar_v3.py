# -*- coding: utf-8 -*-
"""捏脸模式 v3:Q 版头(设计件) + 照片剪影身体(真姿势,零新增模型)。
身体逐格按颜色归类到 {肤色/发色/衣主色/领色/黑} 候选豆——裸臂、水手领、马尾、腿、鞋自动出现。"""
import os
import sys

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import cv2
import numpy as np

import sanc_common as C
import pipe_a
import pipe_symbol as PS
import pipe_avatar as PA


def compose(img, person, cls_full, face, a):
    h, w = img.shape[:2]
    ys, xs = np.where(person > .5)
    x0b, x1b = int(xs.min()), int(xs.max())
    y0b, y1b = int(ys.min()), int(ys.max())
    bw, bh = x1b - x0b + 1, y1b - y0b + 1
    GW = 29
    scale = GW / bw
    GH = max(30, min(56, int(round(bh * scale))))

    def cell_rgb(xx, yy):
        px0 = x0b + int(xx * bw / GW)
        px1 = max(px0 + 1, x0b + int((xx + 1) * bw / GW))
        py0 = y0b + int(yy * bh / GH)
        py1 = max(py0 + 1, y0b + int((yy + 1) * bh / GH))
        px1 = min(w, px1); py1 = min(h, py1)
        reg = (person[py0:py1, px0:px1] > .5)
        if reg.sum() < 2:
            return None
        return np.median(img[py0:py1, px0:px1][reg], axis=0)

    cands = [a['skin_i'], a['hair_i'], a['shirt_i'], a['accent_i'], PS.BLACK_I]
    labs = [PS._lab1(PS.PAL[i]) for i in cands]
    g = np.full((GH, GW), PS.WHITE_I, np.int32)
    for yy in range(GH):
        for xx in range(GW):
            rgb = cell_rgb(xx, yy)
            if rgb is None:
                continue
            lv = PS._lab1(rgb)
            d = [int(((lv - l) ** 2).sum()) for l in labs]
            g[yy, xx] = cands[int(np.argmin(d))]

    # Q 版头(参数化 v2 设计):脸宽映射头尺寸
    fx, fy, fw, fh = [float(v) for v in face[:4]]
    cx = (fx + fw / 2 - x0b) * scale
    cy = (fy + fh * .48 - y0b) * scale
    s = max(.55, min(1.0, fw * scale / 17.0))
    rx, ry = 10 * s, 9.5 * s
    frx = 7.5 * s

    def head_e(xx, yy):
        return ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1

    def face_e(xx, yy):
        return ((xx - cx) / frx) ** 2 + ((yy - cy) / frx) ** 2 <= 1

    hair_i = a['hat_i'] if a['hat_i'] is not None else a['hair_i']
    top_i = a['hat_i'] if a['hat_i'] is not None else a['hair_i']
    bang0, bang1 = int(cy - 1 * s), int(cy)
    for yy in range(max(0, int(cy - ry) - 1), min(GH, int(cy + ry) + 2)):
        for xx in range(max(0, int(cx - rx) - 1), min(GW, int(cx + rx) + 2)):
            if not head_e(xx, yy):
                continue
            if yy <= bang1 and (yy - (cy - ry) <= 9 * s or not face_e(xx, yy)
                                or (yy >= bang0 and (xx % 3 != 1))):
                g[yy, xx] = top_i
            else:
                g[yy, xx] = a['skin_i']
    eye_y0 = int(cy + 1 * s)
    eye_h = 3 if s >= .7 else 2
    for sgn in (-1, 1):
        ex0 = int(cx + sgn * 4.5 * s) - (1 if sgn > 0 else 0)
        g[eye_y0:eye_y0 + eye_h, ex0:ex0 + 2] = PS.BLACK_I
        g[eye_y0, ex0] = PS.WHITE_I
    if a['glasses']:
        for ex in range(int(cx - 5.5 * s), int(cx + 5.5 * s) + 1):
            g[eye_y0 - 1, ex] = PS.DARK_I
    by = int(cy + 5 * s)
    g[by, int(cx - 6.5 * s)] = PA.PINK_I
    g[by, int(cx + 6.5 * s)] = PA.PINK_I
    my = int(cy + 6.5 * s)
    if a['smile']:
        g[my, int(cx - 1.5 * s)] = a['lip_i']
        g[my, int(cx)] = a['lip_i']
        g[my, int(cx + 1.5 * s)] = a['lip_i']
    else:
        g[my, int(cx)] = a['lip_i']

    # 清理:孤立豆归入多数邻色(2 轮)
    for _ in range(2):
        for yy in range(GH):
            for xx in range(GW):
                c = g[yy, xx]
                nbs = []
                for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    y2, x2 = yy + dy, xx + dx
                    if 0 <= y2 < GH and 0 <= x2 < GW:
                        nbs.append(g[y2, x2])
                if nbs and len(set(nbs)) == 1 and nbs[0] != c:
                    g[yy, xx] = nbs[0]
    # 轮廓
    solid = g != PS.WHITE_I
    edge = np.zeros_like(solid)
    edge[1:-1, 1:-1] = solid[1:-1, 1:-1] & ~(solid[:-2, 1:-1] & solid[2:, 1:-1]
                                             & solid[1:-1, :-2] & solid[1:-1, 2:])
    g[edge] = PS.BLACK_I
    return g


def process(name):
    r = PS.prep(name)
    if r is None:
        print('[v3] %s missing' % name, flush=True)
        return
    img, _art, person, cls_full, stem = r
    bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
    faces = C.detect_faces(bgr8)
    if not faces:
        print('[v3] %s no face' % name, flush=True)
        return
    if person is None:
        person = np.ones(img.shape[:2], np.float32)
    a = PA.detect_attrs(img, person, cls_full if cls_full is not None else
                        np.zeros(img.shape[:2], np.uint8), faces[0])
    g = compose(img, person, cls_full, faces[0], a)
    flat = cv2.resize(PS.PAL[g].reshape(g.shape[0], g.shape[1], 3).astype(np.uint8),
                      (g.shape[1] * 12, g.shape[0] * 12), interpolation=cv2.INTER_NEAREST)
    beads = PA.render(g, 12)
    C.save_rgb('v3_%s_flat.png' % stem, flat)
    C.save_rgb('v3_%s_beads.png' % stem, beads)
    C.sheet('sheet_v3_%s.png' % stem, [
        (img, 'Photo'),
        (flat, 'Avatar v3 (%dx%d, %d colors)' % (g.shape[1], g.shape[0],
                                                 len(set(g.ravel().tolist())))),
        (beads, 'v3 beads'),
    ])
    print('[v3] %s done grid=%dx%d colors=%d' % (stem, g.shape[1], g.shape[0],
                                                 len(set(g.ravel().tolist()))), flush=True)


if __name__ == '__main__':
    C.idle_priority()
    for n in ('me_girl.jpg', 'face000.jpg', 'messi.jpg'):
        try:
            process(n)
        except Exception:
            import traceback
            print('[v3] ERR', traceback.format_exc(limit=4), flush=True)
