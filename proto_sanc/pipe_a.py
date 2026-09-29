# -*- coding: utf-8 -*-
"""路线A原型:现行APP效果复现 vs 恢复强管线(对齐+人像模型+贴回+Reinhard+线稿叠层)。
输出 out/{name}_*.png 与 out/sheet_a_{name}.png。"""
import os
import time

import cv2
import numpy as np

import sanc_common as C

PEOPLE_DIR = os.path.join(C.REPO, 'tools', 'bgtest', 'people')
IMAGES_DIR = os.path.join(C.REPO, 'tools', 'bgtest', 'images')
TESTS = ['face000.jpg', 'face003.jpg', 'user_girl.jpg', 'home.jpg']

INK_W = 0.45      # 线稿叠加强度
REIN_A = 0.6      # JP_face 贴脸后的 Reinhard 强度


def find_img(name):
    for d in (C.ROOT, PEOPLE_DIR, IMAGES_DIR):
        p = os.path.join(d, name)
        if os.path.isfile(p):
            return p
    return None


def process(name):
    p = find_img(name)
    if p is None:
        return {'name': name, 'error': 'image not found'}
    img = C.load_rgb(p)
    if img is None:
        return {'name': name, 'error': 'unreadable'}
    H, W = img.shape[:2]
    stem = os.path.splitext(name)[0]
    t = {'name': name, 'size': '%dx%d' % (W, H)}
    bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)

    # ---- 现行 APP 复现:整图 Ghibli → deblue → 65% 混合 ----
    t0 = time.perf_counter()
    g = C.animegan(img, 'ghibli')
    cur = np.clip(img * 0.35 + C.match_rgb_mean_std(g, img) * 0.65, 0, 255)
    t['ghibli_whole_s'] = round(time.perf_counter() - t0, 2)

    ink = C.lines_ink(img)
    ink3 = None if ink is None else ink[..., None]
    panels = [(img, 'Original'), (cur, 'Current APP v2.58')]

    faces = C.detect_faces(bgr8)
    if faces:
        faces.sort(key=lambda f: f[2] * f[3], reverse=True)
        face = faces[0]
        aligned, minv = C.align_face(img, face)
        t['faces'] = len(faces)
        if aligned is not None:
            base = g  # 全强度 Ghibli 整图作基底,背景连贯

            # 贴回掩膜:优先 ParseNet 语义掩膜(对齐空间算,逆映射回原图),椭圆兜底
            Mfwd = cv2.invertAffineTransform(minv)  # 原图坐标→对齐坐标
            fm = C.face_parse_mask(aligned)
            if fm is not None:
                m = cv2.warpAffine(fm[..., 0], minv, (W, H),
                                   borderMode=cv2.BORDER_CONSTANT)[..., None]
                m = np.clip(m, 0, 1)
            else:
                m = C.feather_mask(img.shape, face)

            # A2:对齐后 Ghibli 贴回
            t0 = time.perf_counter()
            fg = C.animegan(aligned, 'ghibli')
            warped = cv2.warpAffine(fg, minv, (W, H), borderMode=cv2.BORDER_CONSTANT)
            a2 = base * (1 - m) + warped * m
            t['face_ghibli_s'] = round(time.perf_counter() - t0, 2)

            # A2b:JP_face 贴脸 + Reinhard(防漂白,调研文档验证过必须)
            a2b = None
            if C.has('jp_face'):
                fj = C.animegan(aligned, 'jp_face')
                ref = cv2.warpAffine(base, Mfwd, (512, 512), borderMode=cv2.BORDER_CONSTANT)
                fjr = C.reinhard_lab(fj, ref, REIN_A)
                warped_j = cv2.warpAffine(fjr, minv, (W, H), borderMode=cv2.BORDER_REFLECT)
                a2b = base * (1 - m) + warped_j * m

            # A3:WBC 平涂底图 + JP_face 贴脸(调研文档第4按钮方案)
            a3 = None
            if C.has('wbc') and a2b is not None:
                t0 = time.perf_counter()
                flat = C.wbc720(img)
                ref2 = cv2.warpAffine(flat, Mfwd, (512, 512), borderMode=cv2.BORDER_CONSTANT)
                fjr2 = C.reinhard_lab(C.animegan(aligned, 'jp_face'), ref2, REIN_A)
                warped_f = cv2.warpAffine(fjr2, minv, (W, H), borderMode=cv2.BORDER_REFLECT)
                a3 = flat * (1 - m) + warped_f * m
                t['wbc_s'] = round(time.perf_counter() - t0, 2)

            for tag, im in (('A2_ghibli_face', a2), ('A2b_jpface_face', a2b),
                            ('A3_wbc_jpface', a3)):
                if im is not None:
                    C.save_rgb('%s_%s.png' % (stem, tag), im)

            a2_line = a2 if ink3 is None else a2 * (1 - INK_W * ink3)
            C.save_rgb('%s_A2_ghibli_face_line.png' % stem, a2_line)

            panels.append((a2, 'A: Ghibli face-align'))
            if a2b is not None:
                panels.append((a2b, 'A: JP_face face-align'))
            if a3 is not None:
                panels.append((a3, 'A: WBC flat + JP_face'))
            panels.append((a2_line, 'A + ink overlay'))
    else:
        t['faces'] = 0
        # 场景:全强度 Shinkai(文档默认风景模型)
        s = C.animegan(img, 'shinkai')
        a1 = s
        a1_line = a1 if ink3 is None else a1 * (1 - INK_W * ink3)
        C.save_rgb('%s_A1_shinkai.png' % stem, a1)
        C.save_rgb('%s_A1_shinkai_line.png' % stem, a1_line)
        panels.append((a1, 'A: Shinkai full'))
        panels.append((a1_line, 'A + ink overlay'))

    C.sheet('sheet_a_%s.png' % stem, panels)
    return t


def main():
    results = []
    for name in TESTS:
        try:
            r = process(name)
        except Exception:
            import traceback
            r = {'name': name, 'error': traceback.format_exc(limit=3)}
        print('[pipe_a] %s' % r, flush=True)
        results.append(r)
    return results


if __name__ == '__main__':
    C.idle_priority()
    main()
