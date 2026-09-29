# -*- coding: utf-8 -*-
"""用户指定图的三转二实测:跑路线A全变体 + 路线B图纸量化。"""
import sys

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import sanc_common as C

C.idle_priority()

import pipe_a  # noqa: E402
import pipe_b  # noqa: E402

NAME = 'me_girl.jpg'
a = pipe_a.process(NAME)
print('[A]', a, flush=True)
b = pipe_b.process(NAME)
print('[B]', b, flush=True)
