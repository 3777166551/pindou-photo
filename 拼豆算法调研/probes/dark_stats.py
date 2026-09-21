# -*- coding: utf-8 -*-
import csv, os
p = os.path.join(r'F:\delete\PDAPP', '拼豆算法调研', 'probes', 'bench_out', 'bench_results.csv')
rows = list(csv.DictReader(open(p, encoding='utf-8')))
for cat in ['lineart', 'cartoons']:
    rs = [r for r in rows if r['cat'] == cat]
    n = len(rs)
    for k in ['old_dark', 'new_dark']:
        print(cat, k, round(sum(int(r[k]) for r in rs) / n, 1))
