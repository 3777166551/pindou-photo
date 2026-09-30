# -*- coding: utf-8 -*-
"""
纸娃娃模式 · 部件库生成器 v2(日漫向,2026-09-29)
================================================
v2 变更(全部指向"日漫 Q 版"目标审美):
  1. 瞳色渐层(p 深 / t 中 / u 浅)替代 v1 单色瞳 —— 日漫眼核心特征;
  2. 上睫四形态: flat 平 / arc 拱 / upturn 外挑 / droop 外垂;
  3. 双高光(大左上 + 小右下)可选 —— 日漫标志性;
  4. 眼尾睫毛尖可选;
  5. 日漫发型 v2: 呆毛 / 空气刘海 / 公主切 / 丸子头 / 双丸子 / 长直
     (v1 的 bangs/zigzag/side/short/twintails 保留,共 10 款);
  6. 整脸 v2 修复 v1 三 bug:
     - 圆胖脸形(手工宽度表,不再纯椭圆),眼区/腮区保证有脸;
     - 部件只贴在肤色格上(与脸求交),悬空豆不可能出现;
     - 右眼使用镜像矩阵 —— 眼尾挑/睫毛方向永远朝外,双眼对称。
产出(固定字面量路径,不进 APK):
  proto_sanc/out/parts_eyes_v2.png   眼型 72 候选(4 睫形 x3 瞳 x3 高光 x2 睫毛)
  proto_sanc/out/parts_faces_v2.png  发型 10 款 + 整脸组合 8 例
运行: python parts_gen.py (纯 Pillow, 零依赖零网络)
"""
from PIL import Image, ImageDraw

OUT_EYES = r"F:\delete\PDAPP\proto_sanc\out\parts_eyes_v2.png"
OUT_FACES = r"F:\delete\PDAPP\proto_sanc\out\parts_faces_v2.png"

PAL = {
    'o': (42, 39, 53),      # 墨线(描边/睫)
    'w': (252, 252, 252),   # 眼白
    'p': (58, 42, 30),      # 瞳·深
    't': (94, 66, 46),      # 瞳·中
    'u': (138, 106, 80),    # 瞳·浅
    'g': (255, 255, 255),   # 高光
    's': (245, 211, 180),   # 肤
    'r': (242, 166, 160),   # 腮红
    'm': (226, 128, 120),   # 唇
    'H': (107, 74, 50),     # 发(示例棕;入库时照片取色替换)
    '.': None,
}
CELL = 16


def draw_grid(grid, cell=CELL):
    rows = len(grid)
    cols = len(grid[0])
    pad = 4
    img = Image.new("RGBA", (cols * cell + pad * 2, rows * cell + pad * 2),
                    (250, 247, 253, 255))
    d = ImageDraw.Draw(img)
    for y in range(rows):
        for x in range(cols):
            rgb = PAL.get(grid[y][x])
            if rgb is None:
                continue
            cx = pad + x * cell + cell / 2
            cy = pad + y * cell + cell / 2
            r = cell * 0.44
            d.ellipse([cx - r, cy - r, cx + r, cy + r],
                      fill=rgb + (255,), outline=(30, 27, 40, 110), width=1)
            hr = cell * 0.10
            d.ellipse([cx - r * 0.45 - hr, cy - r * 0.45 - hr,
                       cx - r * 0.45 + hr, cy - r * 0.45 + hr],
                      fill=(255, 255, 255, 100))
    return img


def mirror(grid):
    return [row[::-1] for row in grid]


# ---------------- 眼型 v2(7x5) ----------------

def eye_grid(brow, pupil, hi, lash):
    g = [['.'] * 7 for _ in range(5)]
    for y in (1, 2, 3):
        for x in (1, 2, 3, 4, 5):
            g[y][x] = 'w'
    # 上睫四形态(外端在右侧;右眼用镜像)
    if brow == 'flat':
        for x in (1, 2, 3, 4, 5):
            g[0][x] = 'o'
    elif brow == 'arc':
        for x in (2, 3, 4):
            g[0][x] = 'o'
        g[1][1] = 'o'
        g[1][5] = 'o'
    elif brow == 'up':      # 外眼角上挑
        for x in (3, 4, 5):
            g[0][x] = 'o'
        g[1][1] = 'o'
        g[1][2] = 'o'
    else:                   # droop 外眼角下垂
        for x in (1, 2, 3):
            g[0][x] = 'o'
        g[1][4] = 'o'
        g[1][5] = 'o'
    # 瞳(渐层)
    if pupil == 'dot':
        g[2][3] = 'p'
    elif pupil == 'grad':       # 2x2 横渐层
        g[2][3] = 'p'
        g[2][4] = 'p'
        g[3][3] = 't'
        g[3][4] = 'u'
    else:                       # wide 3 宽渐层
        g[2][2] = 'p'
        g[2][3] = 'p'
        g[2][4] = 't'
        g[3][2] = 't'
        g[3][3] = 'u'
        g[3][4] = 'u'
    # 高光
    if hi == 'tl':
        g[1][2] = 'g'
    elif hi == 'dual':
        g[1][2] = 'g'
        g[3][4] = 'g'   # 小高光叠瞳右下
    # 眼尾睫毛尖(外上端)
    if lash:
        g[0][6] = 'o'
    return g


def enum_eyes():
    out = []
    for brow in ('flat', 'arc', 'up', 'droop'):
        for pupil in ('dot', 'grad', 'wide'):
            for hi in ('tl', 'dual', 'none'):
                for lash in (0, 1):
                    out.append((brow, pupil, hi, lash))
    return out


# ---------------- 发型 v2(14x7, 画在脸上层;垂条允许盖脸侧) ----------------

def _base_lids(g):
    """y5 鬓角 + y6 两角(多数刘海款共用的收边)"""
    for x in (0, 1, 2, 11, 12, 13):
        g[5][x] = 'H'
    g[6][0] = 'H'
    g[6][13] = 'H'


def hair_bangs():
    g = [['.'] * 14 for _ in range(15)]
    for y in range(5):
        for x in range(14):
            g[y][x] = 'H'
    _base_lids(g)
    return g


def hair_airy():
    g = [['.'] * 14 for _ in range(15)]
    for y in (0, 1, 3, 4):
        for x in range(14):
            g[y][x] = 'H'
    for x in range(2, 12):          # 中段挖空气隙
        g[2][x] = 'H' if x % 3 != 2 else '.'
    _base_lids(g)
    return g


def hair_ahoge():
    g = hair_bangs()
    for x in range(5, 10):          # 头顶中段挖出呆毛根
        g[0][x] = '.'
    g[0][6] = 'H'
    g[0][7] = 'H'
    return g


def hair_hime():
    g = [['.'] * 14 for _ in range(15)]
    for y in range(3):              # 平顶
        for x in range(14):
            g[y][x] = 'H'
    for y in range(3, 10):          # 中分露额 + 两颊垂条到 y9(姬发)
        for x in range(14):
            if x <= 4 or x >= 9 or x in (0, 1, 12, 13):
                g[y][x] = 'H'
    return g


def hair_bun():
    g = [['.'] * 14 for _ in range(15)]
    for y in (0, 1):
        for x in (5, 6, 7, 8):      # 顶部圆髻
            g[y][x] = 'H'
    for y in (2, 3, 4):
        for x in range(14):
            g[y][x] = 'H'
    _base_lids(g)
    return g


def hair_twinbun():
    g = [['.'] * 14 for _ in range(15)]
    for y in (0, 1):
        for x in (2, 3, 4, 9, 10, 11):
            g[y][x] = 'H'
    for y in (2, 3, 4):
        for x in range(14):
            g[y][x] = 'H'
    _base_lids(g)
    return g


def hair_straight():
    g = [['.'] * 14 for _ in range(15)]
    for y in range(10):             # 长直垂到 y9
        for x in range(14):
            g[y][x] = 'H'
    for y in range(4, 10):          # 额前露脸
        for x in range(3, 11):
            g[y][x] = '.'
    return g


def hair_zigzag():
    g = [['.'] * 14 for _ in range(15)]
    for x in range(14):
        g[0][x] = 'H'
        g[1][x] = 'H' if x % 2 == 0 else '.'
        g[2][x] = 'H' if x % 2 == 1 else '.'
    for y in (3, 4):
        for x in range(14):
            g[y][x] = 'H'
    _base_lids(g)
    return g


def hair_side():
    g = [['.'] * 14 for _ in range(15)]
    for y in range(5):
        for x in range(14):
            if x <= 9 - y // 2:
                g[y][x] = 'H'
    _base_lids(g)
    return g


def hair_short():
    g = [['.'] * 14 for _ in range(15)]
    for y in range(3):
        for x in range(14):
            g[y][x] = 'H'
    for y in (3, 4, 5, 6):
        for x in (0, 1, 12, 13):
            g[y][x] = 'H'
    return g


def hair_twintails():
    g = [['.'] * 14 for _ in range(15)]
    for y in range(4):
        for x in range(14):
            g[y][x] = 'H'
    for y in range(3, 10):                  # 垂条延到脸侧 y9(v3)
        for x in (0, 1, 12, 13):
            g[y][x] = 'H'
    return g


HAIRS = [('bangs', hair_bangs), ('ahoge', hair_ahoge), ('airy', hair_airy),
         ('hime', hair_hime), ('bun', hair_bun), ('twinbun', hair_twinbun),
         ('straight', hair_straight), ('zigzag', hair_zigzag),
         ('side', hair_side), ('short', hair_short),
         ('twintails', hair_twintails)]


# ---------------- 嘴(沿用 v1) ----------------

def mouth_smile():
    return [['.ooo.'], ['o...o']]


def mouth_open():
    return [['ooo'], ['ooo']]


def mouth_cat():
    return [['o.o.o'], ['.ooo.']]


def mouth_line():
    return [['ooo']]


MOUTHS = [('smile', mouth_smile), ('open', mouth_open),
          ('cat', mouth_cat), ('line', mouth_line)]


# ---------------- 脸模板 v2(14x15) ----------------

# 每行肤色 x 范围(闭区间): 圆胖脸,手工宽度表
FACE_ROWS = {
    4: (3, 10), 5: (2, 11), 6: (1, 12), 7: (1, 12),
    8: (1, 12), 9: (1, 12), 10: (1, 12), 11: (1, 12),
    12: (2, 11), 13: (3, 10), 14: (5, 8),
}
EYE_TOP = 6      # 眼矩阵 5 行贴 y6..10
BLUSH_Y = 11
MOUTH_TOP = 12


def face_grid_v2(hair_name, eye_params, mouth_name, blush=True):
    g = [['.'] * 14 for _ in range(15)]
    # 1) 脸
    for y, (x0, x1) in FACE_ROWS.items():
        for x in range(x0, x1 + 1):
            g[y][x] = 's'
    # 2) 发(覆盖脸侧/额,公主切垂条由此生效)
    hg = dict(HAIRS)[hair_name]()
    for y in range(min(15, len(hg))):
        for x in range(14):
            if hg[y][x] != '.':
                g[y][x] = 'H'
    # 3) 眼(只贴肤色格;左眼原矩阵,右眼镜像)
    eg = eye_grid(*eye_params)
    for side, off in (('L', 0), ('R', 7)):
        m = eg if side == 'L' else mirror(eg)
        for y in range(5):
            for x in range(7):
                ch = m[y][x]
                if ch != '.' and g[EYE_TOP + y][off + x] == 's':
                    g[EYE_TOP + y][off + x] = ch
    # 4) 腮红(贴肤色格)
    if blush:
        for x in (2, 3, 10, 11):
            if g[BLUSH_Y][x] == 's':
                g[BLUSH_Y][x] = 'r'
    # 5) 嘴(贴肤色格,居中)
    mg = dict(MOUTHS)[mouth_name]()
    mw = len(mg[0])
    x0 = (14 - mw) // 2
    for y in range(len(mg)):
        for x in range(mw):
            if mg[y][x] != '.' and 0 <= MOUTH_TOP + y < 15:
                if g[MOUTH_TOP + y][x0 + x] == 's':
                    g[MOUTH_TOP + y][x0 + x] = 'm'
    return g


# ---------------- sheet ----------------

def build_eyes_sheet():
    eyes = enum_eyes()
    per_row = 12
    tile_w = 7 * CELL + 14
    tile_h = 5 * CELL + 26
    rows = (len(eyes) + per_row - 1) // per_row
    img = Image.new("RGBA", (per_row * tile_w + 16, rows * tile_h + 52),
                    (250, 247, 253, 255))
    d = ImageDraw.Draw(img)
    d.text((12, 8), "Eye candidates v2  E00-E71  "
                    "(brow flat/arc/up/droop x pupil dot/grad/wide x hi tl/dual/none x lash)",
           fill=(60, 54, 80))
    for i, params in enumerate(eyes):
        r, c = divmod(i, per_row)
        ox = 8 + c * tile_w
        oy = 32 + r * tile_h
        img.paste(draw_grid(eye_grid(*params)), (ox, oy))
        d.text((ox + 2, oy + 5 * CELL + 4), "E%02d %s/%s/%s%s" % (
            i, params[0][:2], params[1][:2], params[2][:2],
            'L' if params[3] else ''), fill=(80, 74, 95))
    img.convert("RGB").save(OUT_EYES)
    return len(eyes)


def build_faces_sheet():
    combos = [
        ('ahoge', ('up', 'grad', 'dual', 1), 'smile'),
        ('airy', ('arc', 'grad', 'tl', 1), 'cat'),
        ('hime', ('flat', 'wide', 'dual', 0), 'line'),
        ('bun', ('droop', 'dot', 'tl', 0), 'smile'),
        ('twinbun', ('up', 'grad', 'dual', 1), 'open'),
        ('straight', ('arc', 'wide', 'tl', 1), 'line'),
        ('bangs', ('flat', 'grad', 'tl', 0), 'smile'),
        ('twintails', ('droop', 'grad', 'dual', 1), 'cat'),
    ]
    tile_w = 14 * CELL + 14
    tile_h = 15 * CELL + 28
    hair_tile = 14 * CELL + 12
    img = Image.new("RGBA", (max(len(combos) * tile_w,
                                 6 * hair_tile) + 16, 800),
                    (250, 247, 253, 255))
    d = ImageDraw.Draw(img)
    d.text((12, 8), "Face v2: 11 hairstyles (top) / 8 assemblies (bottom) "
                    "-- parts only attach on skin cells; right eye mirrored",
           fill=(60, 54, 80))
    # 发型行(10+ 款, 6 列 x 2 行)
    for i, (name, fn) in enumerate(HAIRS):
        r, c = divmod(i, 6)
        ox = 8 + c * hair_tile
        oy = 36 + r * (7 * CELL + 26)
        img.paste(draw_grid(fn()), (ox, oy))
        d.text((ox + 2, oy + 7 * CELL + 4), name, fill=(80, 74, 95))
    # 整脸行
    fy = 36 + 2 * (7 * CELL + 26) + 20
    for i, (h, e, m) in enumerate(combos):
        ox = 8 + i * tile_w
        img.paste(draw_grid(face_grid_v2(h, e, m)), (ox, fy))
        d.text((ox + 2, fy + 15 * CELL + 4), "%s %s/%s %s" % (
            h, e[0], e[1], m), fill=(80, 74, 95))
    img.convert("RGB").save(OUT_FACES)
    return len(combos)


if __name__ == '__main__':
    print("eyes v2:", build_eyes_sheet(), "->", OUT_EYES)
    print("faces v2:", build_faces_sheet(), "->", OUT_FACES)
