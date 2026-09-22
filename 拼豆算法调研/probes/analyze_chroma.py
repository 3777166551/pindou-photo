# -*- coding: utf-8 -*-
"""色度离群指标分布检查:实锤缺陷(sepia 扫描)能否与 4bit 量化噪声分开"""
import csv, statistics

rows = list(csv.DictReader(open('bench_out/judge_results.csv', encoding='utf-8')))
for cat in ('cartoons', 'lineart', 'pixelart', 'landscape', 'random'):
    rs = sorted(int(r['colout_new']) for r in rows if r['cat'] == cat)
    n = len(rs)
    print('%-10s colout_new p50=%4d p90=%4d max=%4d | colout_old p50=%3d max=%3d'
          % (cat, rs[n // 2], rs[int(n * .9)], rs[-1],
             sorted(int(r['colout_old']) for r in rows if r['cat'] == cat)[n // 2],
             max(int(r['colout_old']) for r in rows if r['cat'] == cat)))
# 目检实锤的 sepia 雕版扫描
for r in rows:
    if r['file'] in ('lineart_040.jpg', 'lineart_021.jpg'):
        print('eyeballed %s colout_new=%s (verdict=%s)' % (r['file'], r['colout_new'], r['verdict']))
