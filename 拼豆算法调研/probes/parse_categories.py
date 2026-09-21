# -*- coding: utf-8 -*-
"""解析 Commons 类目 JSON -> 每类 50 张缩略图 URL 清单 + curl 下载脚本。
   只挑光栅图(jpg/png 缩略),排除 svg 原文件过小/音频等。"""
import json, os, random

HERE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'datasets')
CATS = ['cartoons', 'lineart', 'pixelart', 'landscape', 'portrait', 'food']

for cat in CATS:
    p = os.path.join(HERE, 'cat_%s.json' % cat)
    j = json.load(open(p, encoding='utf-8'))
    pages = j.get('query', {}).get('pages', {})
    urls = []
    for pid, page in pages.items():
        ii = page.get('imageinfo', [{}])[0]
        u = (ii.get('thumburl') or '').split('?')[0]   # 去 utm 追踪参数
        if u.lower().endswith(('.jpg', '.jpeg', '.png')):
            urls.append(u)
    urls = urls[:50]
    with open(os.path.join(HERE, 'urls_%s.txt' % cat), 'w') as f:
        f.write('\n'.join(urls))
    print(cat, len(urls))

# 50 张随机实拍(picsum 确定性 id,失败的下载时跳过)
random.seed(20260921)
ids = random.sample(range(1, 1084), 80)   # 多备 30 个容错
with open(os.path.join(HERE, 'urls_random.txt'), 'w') as f:
    for i in ids:
        f.write('https://picsum.photos/id/%d/600/600\n' % i)
print('random', 80)
