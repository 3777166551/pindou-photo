# -*- coding: utf-8 -*-
"""V12 对照表生成:从 judge12 列(judge_results.csv)汇总 OLD/V11/V12 三管线"""
import csv

rows = list(csv.DictReader(open('bench_out/judge_results.csv', encoding='utf-8')))
G = ('cartoons', 'lineart', 'pixelart')
P = ('landscape', 'portrait', 'food', 'random')
md = ['# V12 试验:贴色调救援 + 相似色孤格吸附 (2026-09-21)', '',
      '背景: 用户复核堆反馈"纯色图 OLD 好、复杂图 NEW 好"。归因=线条救援把柔和线画重',
      '(lineart dark 2.5x)+纯色区噪声翻点。V12 两修正针对此,OLD/V11/V12 同跑 347 张。', '',
      '| 类 | dark比 1.0/V11/V12 | 碎豆v2 旧/V11/V12 | 用色 旧/V11/V12 | ΔE 旧/V11/V12 | 无主豆 V11/V12 | 特征格 | V12 更差/更优 |',
      '|---|---|---|---|---|---|---|---|']
print('%-10s dark比 1.0/V11/V12   f2 旧/V11/V12     col 旧/V11/V12    de 旧/V11/V12      ol V11/V12  feat  w/b' % ('cat',))
for cat in G + P:
    rs = [r for r in rows if r['cat'] == cat]
    n = len(rs)
    f = lambda k: sum(float(r[k]) for r in rs) / n
    dr1 = sum(int(r['new_dark']) for r in rs) / max(1, sum(int(r['old_dark']) for r in rs))
    dr2 = sum(int(r['v12_dark']) for r in rs) / max(1, sum(int(r['old_dark']) for r in rs))
    nw = sum(1 for r in rs if r['verdict12'] == 'worse')
    nb = sum(1 for r in rs if r['verdict12'] == 'better')
    print('%-10s 1.0/%.2f/%.2f   %.1f/%.1f/%.1f  %.1f/%.1f/%.1f  %.2f/%.2f/%.2f  %d/%d  %.1f  %d/%d'
          % (cat, dr1, dr2,
             f('old_frag2'), f('new_frag2'), f('v12_frag2'),
             f('old_colors'), f('new_colors'), f('v12_colors'),
             f('de_old'), f('de_new'), f('de_v12'),
             f('ol_new'), f('ol_v12'), f('nfeat12'), nw, nb))
    md.append('| %s | 1.0/%.2f/%.2f | %.1f/%.1f/%.1f | %.1f/%.1f/%.1f | %.2f/%.2f/%.2f | %.1f/%.1f | %.1f | %d/%d |'
              % (cat, dr1, dr2,
                 f('old_frag2'), f('new_frag2'), f('v12_frag2'),
                 f('old_colors'), f('new_colors'), f('v12_colors'),
                 f('de_old'), f('de_new'), f('de_v12'),
                 f('ol_new'), f('ol_v12'), f('nfeat12'), nw, nb))
md += ['', '样张(SRC|V11|V12): bench_out/worse/ + sample/, 选取=V12 自动更差/复核堆/每类随机 2 张。',
       '', '读数要点: dark 比向 1.0 回归=柔和线恢复; 碎豆v2 较 V11 下降=锯齿/翻点减少;',
       'ΔE 与用色不应恶化; 无主豆(扣特征格)应保持 0。',
       '', '注: selfcheck 软线场景设计有误(线核 0x333 纯深灰,贴色调理应更深),真实柔和线看 dark 比。']
with open('bench_out/judge12_report.md', 'w', encoding='utf-8') as f:
    f.write('\n'.join(md) + '\n')
print('md written')
