# -*- coding: utf-8 -*-
"""只读+按 parse_categories 同款风格:提取 x_*.json 额外 URL"""
import json, os
d = os.path.join(r'F:\delete\PDAPP', '拼豆算法调研', 'probes', 'datasets')
NL = chr(10)
for cat in ['pixelart', 'lineart', 'landscape', 'portrait']:
    j = json.load(open(os.path.join(d, 'x_%s.json' % cat), encoding='utf-8'))
    pages = j.get('query', {}).get('pages', {})
    have = set()
    hp = os.path.join(d, 'urls_%s.txt' % cat)
    if os.path.exists(hp):
        have = set(l.strip() for l in open(hp, encoding='utf-8') if l.strip())
    urls = []
    for pid, page in pages.items():
        ii = page.get('imageinfo', [{}])[0]
        u = (ii.get('thumburl') or '').split('?')[0]
        if u and (u not in have):
            low = u.lower()
            if low.endswith('.jpg') or low.endswith('.jpeg') or low.endswith('.png'):
                urls.append(u)
    urls = urls[0:60]
    with open(os.path.join(d, 'x_urls_%s.txt' % cat), 'w', encoding='utf-8') as f:
        f.write(NL.join(urls))
    print(cat, len(urls))
