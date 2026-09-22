# -*- coding: utf-8 -*-
"""v1 判决失真归因:分解 judge_results.csv"""
import csv, statistics

rows = list(csv.DictReader(open('bench_out/judge_results.csv', encoding='utf-8')))

print('=== photo dE by gate ===')
for cat in ('landscape', 'portrait', 'food', 'random'):
    for g in ('0', '1'):
        rs = [r for r in rows if r['cat'] == cat and r['gate'] == g]
        if rs:
            do = sum(float(r['de_old']) for r in rs) / len(rs)
            dn = sum(float(r['de_new']) for r in rs) / len(rs)
            print('%-10s gate=%s n=%2d de_old=%.2f de_new=%.2f delta=%+.2f'
                  % (cat, g, len(rs), do, dn, dn - do))

print('=== graphic worse reasons ===')
for cat in ('cartoons', 'lineart', 'pixelart'):
    fu = ll = 0
    tot = 0
    for r in rows:
        if r['cat'] == cat and r['verdict'] == 'worse':
            of, nf = int(r['old_frag']), int(r['new_frag'])
            od, nd = int(r['old_dark']), int(r['new_dark'])
            tot += 1
            if nf > of if of else nf > 0:
                fu += 1
            if od >= 10 and nd < od * 0.85:
                ll += 1
    print('%-10s worse=%d frag_up=%d line_lost=%d' % (cat, tot, fu, ll))

print('=== dark ratio distribution (old_dark>=10) ===')
for cat in ('cartoons', 'lineart', 'pixelart'):
    rs = [r for r in rows if r['cat'] == cat and int(r['old_dark']) >= 10]
    rr = sorted(int(r['new_dark']) / int(r['old_dark']) for r in rs)
    if rr:
        print('%-10s n=%d min=%.2f p25=%.2f med=%.2f p75=%.2f max=%.2f'
              % (cat, len(rr), rr[0], rr[len(rr) // 4], statistics.median(rr),
                 rr[3 * len(rr) // 4], rr[-1]))

print('=== sample worst photo de_delta (gated) ===')
rs = sorted((r for r in rows if r['cat'] in ('landscape', 'portrait', 'food', 'random')
             and r['gate'] == '1'), key=lambda r: float(r['de_delta']))
for r in rs[:5]:
    print('%-10s %-20s de %s -> %s (%+.1f) lum_dev %s -> %s'
          % (r['cat'], r['file'][:20], r['de_old'], r['de_new'],
             float(r['de_delta']), r['lum_dev_old'], r['lum_dev_new']))
rs = sorted((r for r in rows if r['cat'] in ('landscape', 'portrait', 'food', 'random')
             and r['gate'] == '0'), key=lambda r: float(r['de_delta']))
print('-- non-gated worst --')
for r in rs[:5]:
    print('%-10s %-20s de %s -> %s (%+.1f)'
          % (r['cat'], r['file'][:20], r['de_old'], r['de_new'], float(r['de_delta'])))
