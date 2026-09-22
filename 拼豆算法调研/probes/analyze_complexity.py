# -*- coding: utf-8 -*-
"""验证用户观察"纯色图 OLD 好、复杂图 NEW 好":
按图像复杂度(旧用色数/色桶数)分桶,看碎豆v2 增幅与 dark 增幅的分布"""
import csv

rows = list(csv.DictReader(open('bench_out/judge_results.csv', encoding='utf-8')))
print('%-10s %-14s %3s %8s %8s %8s %8s' %
      ('cat', '复杂度桶(旧用色)', 'n', 'dFrag2', 'dDark%', 'dCol', ' verdict w/b'))
for cat in ('cartoons', 'lineart', 'pixelart'):
    rs = [r for r in rows if r['cat'] == cat]
    # 按旧用色数分三桶:简单(<8) / 中(8~20) / 复杂(>20)
    for lo, hi, tag in ((0, 8, '简单<8'), (8, 20, '中8-20'), (20, 999, '复杂>20')):
        b = [r for r in rs if lo <= int(r['old_colors']) < hi]
        if not b:
            continue
        n = len(b)
        df2 = sum(int(r['new_frag2']) - int(r['old_frag2']) for r in b) / n
        ddk = (sum(int(r['new_dark']) for r in b) /
               max(1, sum(int(r['old_dark']) for r in b)))
        dc = sum(int(r['new_colors']) - int(r['old_colors']) for r in b) / n
        w = sum(1 for r in b if r['verdict'] == 'worse')
        bt = sum(1 for r in b if r['verdict'] == 'better')
        print('%-10s %-14s %3d %8.1f %8.2f %8.1f   %d/%d' %
              (cat, tag, n, df2, ddk, dc, w, bt))
