# -*- coding: utf-8 -*-
"""JP_face 输入约定探针:尝试常见布局/归一化,用输出统计判断哪个是正确接法。只读不写。"""
import sys

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import cv2
import numpy as np

import sanc_common as C
import pipe_a as P

s = C.sess('jp_face')
i = s.get_inputs()[0]
print('input :', i.name, i.shape, i.type, flush=True)
print('output:', s.get_outputs()[0].shape, flush=True)

img = C.load_rgb(P.find_img('face000.jpg'))
bgr8 = cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
faces = C.detect_faces(bgr8)
al, _ = C.align_face(img, faces[0])

norm = {
    'm127': (al / 127.5 - 1).astype(np.float32),
    'n255': (al / 255.0).astype(np.float32),
}
for layout in ('NHWC', 'NCHW'):
    for nk, x in norm.items():
        a = x[None] if layout == 'NHWC' else x.transpose(2, 0, 1)[None]
        a = np.ascontiguousarray(a)
        try:
            y = np.asarray(s.run(None, {i.name: a})[0])
        except Exception as e:
            print('%-5s %-4s -> ERROR %s' % (layout, nk, str(e)[:80]), flush=True)
            continue
        if y.max() <= 2.0:  # tanh 类输出
            y01 = (y + 1) * 127.5
        else:
            y01 = y * 255.0 if y.max() <= 1.001 else y
        print('%-5s %-4s -> shape %-18s mean %6.1f std %6.1f range [%6.1f, %6.1f]'
              % (layout, nk, str(y.shape), y01.mean(), y01.std(), y01.min(), y01.max()),
              flush=True)
