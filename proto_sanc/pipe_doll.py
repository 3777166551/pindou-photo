# -*- coding: utf-8 -*-
"""
纸娃娃模式 · 照片→豆偶闭环原型(2026-09-29)
==========================================
数据流: 照片 → PSY.prep(U²-Net 人区 + ParseNet 全图分区) → YuNet 最大脸
       → AV.detect_attrs 取色(发/肤/唇/衣, 90 色板下标)
       → PG.face_grid_v2 部件模板换色(瞳色由发色派生渐层, 腮红由肤派生)
       → 部件全自动选择(attrs 驱动: 双马尾/长发/刘海/微笑 → 发型与嘴)
       → 三联图(照片 / 平涂 / 豆板) 落 proto_sanc/out/doll_<stem>.png
复用: pipe_symbol.prep / pipe_avatar.detect_attrs / parts_gen 部件库 v1
运行: python pipe_doll.py me_girl.jpg [more.jpg ...]  (CPU, 零下载)
"""
import os
import sys

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import numpy as np
from PIL import Image

import sanc_common as C
import pipe_symbol as PSY          # prep / palette utils
import pipe_avatar as AV           # detect_attrs
import parts_gen as PG             # 部件库(眼72/发11/嘴4 + face_grid_v2)
from bead_palette_data import PALETTE_90

BODY_ROWS = 8                      # 简身: 领+肩 8 行(半身照形态)


def _clamp(v):
    return tuple(int(max(0, min(255, round(x)))) for x in v)


def derive_palette(a):
    """attrs(色板下标) → parts_gen 色槽 RGB"""
    hair = np.array(PALETTE_90[a['hair_i']], np.float32)
    skin = np.array(PALETTE_90[a['skin_i']], np.float32)
    lip = np.array(PALETTE_90[a['lip_i']], np.float32)
    # 瞳 = 发色同系渐层(日漫惯例: 深发色→深瞳)
    p = hair * 0.42
    t = hair * 0.68
    u = hair * 0.95
    # 腮红 = 肤向红偏
    blush = skin * 0.55 + np.array((238, 120, 120), np.float32) * 0.45
    return {
        'H': _clamp(hair), 's': _clamp(skin),
        'p': _clamp(p), 't': _clamp(t), 'u': _clamp(u),
        'r': _clamp(blush), 'm': _clamp(lip),
    }


def pick_parts(a):
    """attrs → (发型, 眼, 嘴)。全自动规则 v1: 简单可解释。"""
    if a.get('twin'):
        hair = 'twintails'
    elif a.get('long'):
        hair = 'straight'
    elif a.get('bangs'):
        hair = 'ahoge'
    else:
        hair = 'short'
    eye = ('up', 'grad', 'dual', 1)          # 日漫元气眼(推荐款)
    mouth = 'smile' if a.get('smile') else 'line'
    return hair, eye, mouth


def body_grid(a, top_colors):
    """简身 7 行(14 宽): 脖(肤,1 行) + 水手领(衣色/点缀色) + 肩。y 从 15 起。"""
    shirt = _clamp(PALETTE_90[a['shirt_i']])
    accent = _clamp(PALETTE_90[a['accent_i']])
    s = top_colors['s']
    g = [['.'] * 14 for _ in range(BODY_ROWS)]
    for x in (5, 6, 7, 8):                    # 脖(1 行,v3 收短)
        g[0][x] = 'N'
    # 肩/衣: y1..6, 逐行加宽
    for y in range(1, BODY_ROWS):
        half = min(6, 1 + (y - 1) * 2)
        for x in range(7 - half, 7 + half):
            g[y][x] = 'C'
    # 水手领 V: 从脖向下外扩(y1..3)
    for i, y in enumerate((1, 2, 3)):
        for x in (6 - i, 7 + i):
            g[y][x] = 'A'
    # 领结(V 领下缘两颗,收紧到 x5/x8 内侧)
    g[3][5] = 'A'
    g[3][8] = 'A'
    return g, {'N': s, 'C': shirt, 'A': accent}


def render_doll(a):
    """attrs → (flat_grid_image, beads_grid_image) 上=大头 下=简身"""
    colors = derive_palette(a)
    hair, eye, mouth = pick_parts(a)
    head = PG.face_grid_v2(hair, eye, mouth)
    body, body_pal = body_grid(a, colors)
    full = head + body
    PG.PAL.update(colors)
    PG.PAL.update(body_pal)
    flat = PG.draw_grid(full, cell=16)
    beads = PG.draw_grid(full, cell=16).resize(
        (14 * 34 + 8, (15 + BODY_ROWS) * 34 + 8), Image.NEAREST)
    return flat, beads, (hair, eye, mouth)


def photo_panel(img, h=460):
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    w = round(im.width * h / im.height)
    return im.resize((w, h))


def main(names):
    for name in names:
        r = PSY.prep(name)
        if r is None:
            print('[doll] %s missing' % name, flush=True)
            continue
        img, _art, person, cls_full, stem = r
        import cv2
        bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8),
                            cv2.COLOR_RGB2BGR)
        faces = C.detect_faces(bgr8)
        if not faces:
            print('[doll] %s no face' % name, flush=True)
            continue
        if person is None:
            person = np.ones(img.shape[:2], np.float32)
        a = AV.detect_attrs(img, person, cls_full if cls_full is not None
                            else np.zeros(img.shape[:2], np.uint8), faces[0])
        flat, beads, parts = render_doll(a)
        # 三联: 照片 | 平涂 | 豆板
        ph = photo_panel(img)
        gap = 12
        H = max(ph.height, flat.height, beads.height)
        W = ph.width + flat.width + beads.width + gap * 4
        sheet = Image.new("RGB", (W, H + 30), (250, 247, 253))
        sheet.paste(ph, (gap, 10))
        sheet.paste(flat.convert("RGB"), (ph.width + gap * 2, 10))
        sheet.paste(beads.convert("RGB"),
                    (ph.width + flat.width + gap * 3, 10))
        out = r'F:\delete\PDAPP\proto_sanc\out\doll_%s.png' % stem
        sheet.save(out)
        print('[doll] %s parts=%s hair_i=%s skin_i=%s -> %s' % (
            stem, parts, a['hair_i'], a['skin_i'], out), flush=True)


if __name__ == '__main__':
    C.idle_priority()
    args = sys.argv[1:] or ['me_girl.jpg']
    main(args)
