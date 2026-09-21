# -*- coding: utf-8 -*-
# 真实照片对比:现状(V1) vs 先配后投+门控(V11),58x58 默认档
# 用法: E:\crawl4ai\venv\Scripts\python.exe real_photo_compare.py <img1> <img2> ...
# 输出: real_compare\<名>_cmp.png  (SOURCE | OLD | NEW 三联,标注用色数)
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from algo_bakeoff import run_pipeline, PAL
from PIL import Image, ImageDraw

def bead_rgb(b):
    return tuple(PAL[b]) if isinstance(b, int) else tuple(b[:3])

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'real_compare')
os.makedirs(OUT, exist_ok=True)
GRID = 58
CELL = 10          # 渲染每格边长(px)
PLATE = (243, 239, 249)   # 合同 surface 冷紫
CFG_OLD = dict(resample='mean_srgb', metric='labE')
CFG_NEW = dict(resample='vote', metric='de2000', enhance_photo=True)

def chart(beads, cols):
    img = Image.new('RGB', (cols*CELL, cols*CELL), PLATE)
    d = ImageDraw.Draw(img)
    r = CELL*0.42
    for i, b in enumerate(beads):
        cx = (i % cols)*CELL + CELL/2
        cy = (i // cols)*CELL + CELL/2
        d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=bead_rgb(b))
    return img

def strip(img, label, note):
    w, h = img.size
    out = Image.new('RGB', (w, h+34), (255, 255, 255))
    out.paste(img, (0, 34))
    d = ImageDraw.Draw(out)
    d.text((8, 8), label + '  ' + note, fill=(29, 27, 32))
    return out

for path in sys.argv[1:]:
    name = os.path.splitext(os.path.basename(path))[0]
    im = Image.open(path).convert('RGB')
    side = min(im.size)
    im = im.crop(((im.width-side)//2, (im.height-side)//2,
                  (im.width+side)//2, (im.height+side)//2))
    im = im.resize((464, 464), Image.LANCZOS)   # 464/58 = 8px per cell source
    px = list(im.getdata())
    sw = sh = 464

    beads_old, _ = run_pipeline(list(px), sw, sh, CFG_OLD)
    beads_new, _ = run_pipeline(list(px), sw, sh, CFG_NEW)

    sheet = Image.new('RGB', (GRID*CELL*3 + 40, GRID*CELL + 34*3), (250, 250, 252))
    sheet.paste(strip(im.resize((GRID*CELL, GRID*CELL)), 'SOURCE', ''), (0, 34))
    sheet.paste(strip(chart(beads_old, GRID), 'OLD',
                      'colors=%d' % len(set(beads_old))), (GRID*CELL+20, 34))
    sheet.paste(strip(chart(beads_new, GRID), 'NEW',
                      'colors=%d' % len(set(beads_new))), (GRID*CELL*2+40, 34))
    out_path = os.path.join(OUT, name + '_cmp.png')
    sheet.save(out_path)
    print('OK', out_path, 'old_colors=%d new_colors=%d' %
          (len(set(beads_old)), len(set(beads_new))))
