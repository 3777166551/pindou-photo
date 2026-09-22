# -*- coding: utf-8 -*-
"""重生成人工复核样张(此前误删):自动更差(2) + 色相离群 Top12 + 碎豆v2增幅 Top10。
key_for(cat,fn) 截断规则: (cat+'_'+sha256)[:40],与 auto_judge 落盘名一致。"""
import os, sys, csv, hashlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from batch_compare import lut_de2000
from auto_judge import (safe_join, DS, WORSE_DIR, load_image, gen_two_beads)

OUT = os.path.join(HERE, 'bench_out')
rows = list(csv.DictReader(open(os.path.join(OUT, 'judge_results.csv'),
                                encoding='utf-8')))


def key_of(r):
    return (r['cat'] + '_' + hashlib.sha256(r['file'].encode()).hexdigest())[:40]


graphics = [r for r in rows if r['cat'] in ('cartoons', 'lineart', 'pixelart')]
keep = {}
for r in rows:
    if r['verdict'] == 'worse':
        keep[key_of(r)] = r
for r in sorted(graphics, key=lambda r: -int(r['colout_new']))[:12]:
    keep.setdefault(key_of(r), r)
for r in sorted(graphics, key=lambda r: -(int(r['new_frag2']) - int(r['old_frag2']))):
    if len(keep) >= 2 + 12 + 10:
        break
    keep.setdefault(key_of(r), r)

print('building LUT...', flush=True)
lut = lut_de2000()
for key, r in keep.items():
    path = os.path.join(WORSE_DIR, key + '.png')
    if os.path.exists(path):
        continue
    im = load_image(safe_join(DS, r['cat']), r['file'])
    if im is None:
        print('skip', r['file'])
        continue
    b_old, b_new = gen_two_beads(im, lut)
    from batch_compare import sheet
    sheet(im, b_old, b_new, r['cat'] + '/' + r['file'][:28]).save(path)
    print('regen', r['cat'], r['file'], flush=True)
print('DONE total=%d' % len(keep), flush=True)
