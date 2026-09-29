# -*- coding: utf-8 -*-
"""符号化管线一次性调试:落盘 person 掩膜 / 58 网格标签图 / 区域决策,定位颜色错乱。"""
import sys

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import cv2
import numpy as np

import sanc_common as C
import pipe_a
import pipe_symbol as S

C.idle_priority()

img = C.load_rgb(pipe_a.find_img('me_girl.jpg'))
h, w = img.shape[:2]
bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
faces = C.detect_faces(bgr8)
person = C.u2net_person(img)
print('person: shape', None if person is None else person.shape,
      'cov>0.5', None if person is None else round(float((person > 0.5).mean()), 3),
      'min', None if person is None else round(float(person.min()), 3),
      'max', None if person is None else round(float(person.max()), 3), flush=True)
C.save_rgb('dbg_person.png', np.stack([(person if person is not None else np.zeros((h, w))) * 255] * 3, -1))

face = sorted(faces, key=lambda f: f[2] * f[3], reverse=True)[0]
aligned, minv = C.align_face(img, face)
cls = C.face_parse_classes(aligned)
print('cls uniq:', sorted(set(cls.ravel().tolist())), flush=True)
cls_full = cv2.warpAffine(cls.astype(np.uint8), minv, (w, h),
                          flags=cv2.INTER_NEAREST, borderMode=cv2.BORDER_CONSTANT)
# 类别配色可视化
COLS = {0: (0, 0, 0), 1: (255, 128, 128), 2: (128, 0, 0), 3: (0, 255, 0), 4: (0, 128, 0),
        5: (0, 255, 255), 6: (128, 128, 0), 10: (255, 0, 255), 11: (0, 0, 255),
        12: (255, 255, 0), 13: (128, 128, 0), 14: (0, 128, 255), 17: (255, 255, 255)}
vis = np.zeros((h, w, 3), np.uint8)
for k, c in COLS.items():
    vis[cls_full == k] = c
C.save_rgb('dbg_cls.png', vis)

s = 58
cells = cv2.resize(np.clip(img, 0, 255).astype(np.uint8), (s, s),
                   interpolation=cv2.INTER_AREA).astype(np.float32)


def frac(m):
    return cv2.resize(m.astype(np.float32), (s, s), interpolation=cv2.INTER_AREA)


person_cell = frac(person) > 0.5
print('person_cell frac:', round(float(person_cell.mean()), 3), flush=True)
lab = np.full((s, s), -1, np.int32)
un = person_cell.copy()
for keys, v, thr in (((4, 5), 3, 0.15), ((2, 3), 4, 0.20), ((12, 13), 5, 0.20),
                     ((11,), 6, 0.25), ((1,), 0, 0.40), ((14, 15), 0, 0.40),
                     ((17,), 1, 0.40)):
    take = (frac(np.isin(cls_full, keys)) > thr) & un
    lab[take] = v
    un &= ~take
lab[un] = 2
names = {-1: 'bg', 0: 'skin', 1: 'hair', 2: 'clothes', 3: 'eye', 4: 'brow', 5: 'lip', 6: 'mouth'}
for v in (-1, 0, 1, 2, 3, 4, 5, 6):
    n = int((lab == v).sum())
    if n:
        med = np.median(cells[lab == v], axis=0).astype(int)
        print('lab %-7s n=%3d medianRGB=%s' % (names[v], n, list(med)), flush=True)
LV = {-1: (255, 255, 255), 0: (255, 200, 160), 1: (40, 40, 40), 2: (160, 160, 160),
      3: (0, 0, 255), 4: (0, 128, 255), 5: (255, 0, 255), 6: (0, 255, 255)}
grid = np.zeros((s, s, 3), np.uint8)
for v, c in LV.items():
    grid[lab == v] = c
C.save_rgb('dbg_lab58.png', cv2.resize(grid, (s * 9, s * 9), interpolation=cv2.INTER_NEAREST))
print('BLACK_I=%d WHITE_I=%d BROWN_I=%d' % (S.BLACK_I, S.WHITE_I, S.BROWN_I), flush=True)
print('done', flush=True)
