# -*- coding: utf-8 -*-
# 量化 PDAPP 拼豆管线的亮度/饱和度损失（01 号文档实验①②③④）
# 色板 = BeadPalettes.java T1+T2+T3（默认 90 色档）；色彩转换与 ColorMath.java 逐行对齐
# 运行: E:\crawl4ai\venv\Scripts\python.exe pindou_algo_probe.py
import math, random

# ---- 与 ColorMath.java 一致的 sRGB->Lab ----
def rgb_to_lab(rgb):
    r, g, b = [(rgb[i] / 255.0) for i in range(3)]
    def f_lin(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = f_lin(r), f_lin(g), f_lin(b)
    x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
    y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / 1.00000
    z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
    def f(t):
        return t ** (1/3) if t > 0.008856 else 7.787 * t + 16.0 / 116.0
    fx, fy, fz = f(x), f(y), f(z)
    return (116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))

def srgb_to_linear(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def linear_to_srgb(v):
    v = max(0.0, min(1.0, v))
    s = 12.92 * v if v <= 0.0031308 else 1.055 * v ** (1/2.4) - 0.055
    return max(0, min(255, round(s * 255)))

# ---- App 默认 90 色板 (T1+T2+T3, tierIdx=2) ----
T1 = [0xFFFFFF,0xF7F0DD,0xA8A8A8,0x4A4A4A,0x141414,0xE3242B,0x9C1C1C,0xE4007C,
      0xF48FB1,0xF57C00,0xF7E01E,0xF5A623,0x43A047,0x1B5E20,0x26A69A,0x42A5F5,
      0x1E5AA8,0x0D2C6B,0x7B3FA0,0x795548,0xC8A17B,0xF5CBA0,0x6D4C41,0x90CAF9]
T2 = [0xD9D9D9,0x757575,0x7B1113,0xA04A3A,0xFF7F6E,0xFFB6C1,0xE91E63,0xFFB74D,
      0xE65100,0xFFF59D,0xFBC02D,0xB8A233,0x7CB342,0x556B2F,0x00838F,0x4DD0E1,
      0x039BE5,0x1A237E,0x607D8B,0x512DA8,0xD1C4E9,0xA1887F,0xC3B091,0xFADCC8]
T3 = [0xEAE0C0,0x2B2B2B,0xA29A8C,0x6B7075,0xEA4A28,0x5C0E1E,0xDDBAC2,0xF2CD9A,
      0xE8A575,0x9E8B3A,0xFFBE0B,0xA9C24A,0x0E3620,0xA5DBC8,0x92A683,0x63E62E,
      0x13876A,0xD6E9F8,0x3949AB,0x2E7CFF,0x6A5ACD,0x452159,0xC0A5C9,0x3E2723,
      0x6E2C1B,0x8B4226,0xA9713F,0xD2A24C,0xC0C7CE,0xD4AF37,0xFF6F3C,0x0277BD,
      0x38623C,0xE3D5AE,0xCB6843,0xA9BFD4,0xD3ACAF,0x2F211A,0x00A651,0x6E7623,
      0xAD0A63,0xC4825A]
def to_rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)
PAL90 = [to_rgb(h) for h in (T1 + T2 + T3)]
PAL_LABS = [rgb_to_lab(c) for c in PAL90]

def nearest_euclid(lab):
    best, bd = 0, 1e18
    for i, p in enumerate(PAL_LABS):
        d = (lab[0]-p[0])**2 + (lab[1]-p[1])**2 + (lab[2]-p[2])**2
        if d < bd:
            bd, best = d, i
    return best

def delta_e00(lab1, lab2):
    # 简化:此处用 Lab 欧氏近似即可,重点不是精确 CIEDE2000
    return math.sqrt(sum((a-b)**2 for a, b in zip(lab1, lab2)))

# "照片般"的随机色:L* 30~92,色度 8~70(真实照片主体分布),色相随机
random.seed(7)
def photo_color():
    L = random.uniform(30, 92)
    C = random.uniform(8, 70)
    h = random.uniform(0, 360)
    a = C * math.cos(math.radians(h))
    b = C * math.sin(math.radians(h))
    return (L, a, b)

def lab_chroma(lab):
    return math.hypot(lab[1], lab[2])

# ---- 实验 1:色板匹配的亮度/色度拉低 ----
dL, dC, n = 0.0, 0.0, 0
worst = []
for _ in range(8000):
    src = photo_color()
    i = nearest_euclid(src)
    bead = PAL_LABS[i]
    dL += bead[0] - src[0]
    dC += lab_chroma(bead) - lab_chroma(src)
    n += 1
    if bead[0] - src[0] < -6:
        worst.append((round(src[0],1), round(bead[0],1)))
print("[实验1] 默认90色板 Lab欧氏匹配, N=%d" % n)
print("  平均 L* 变化: %+.2f (负=变暗)" % (dL/n))
print("  平均 C* 变化: %+.2f (负=变灰)" % (dC/n))
print("  L* 掉超6的样本占比: %.1f%%" % (100*len(worst)/n))

# 分亮度区间看
for lo, hi in [(30,50),(50,70),(70,92)]:
    dLs = []
    for _ in range(3000):
        L = random.uniform(lo, hi)
        C = random.uniform(8, 70)
        h = random.uniform(0, 360)
        src = (L, C*math.cos(math.radians(h)), C*math.sin(math.radians(h)))
        bead = PAL_LABS[nearest_euclid(src)]
        dLs.append(bead[0]-src[0])
    print("  L* %d~%d 段平均变暗: %+.2f" % (lo, hi, sum(dLs)/len(dLs)))

# ---- 实验 2:盒式均值在 sRGB 空间 vs 线性光空间的减亮 ----
# 模拟一个格子内两种颜色按比例混合(降采样的本质)
random.seed(11)
dL2 = 0.0; dC2 = 0.0; n2 = 0
for _ in range(8000):
    c1 = photo_color(); c2 = photo_color()
    # lab -> rgb(粗略反变换即可,只要两端一致)
    def lab_to_rgb_approx(lab, C=None):
        # 用数值法:在 a-b 平面从白点向外找 —— 太麻烦,直接在 RGB 空间随机采样替代
        return None
    # 直接用随机 RGB 对,更贴近真实像素
    r1 = tuple(random.randint(0,255) for _ in range(3))
    r2 = tuple(random.randint(0,255) for _ in range(3))
    frac = random.uniform(0.2, 0.8)
    # App: gamma(sRGB) 空间平均
    gam = tuple(round(frac*x + (1-frac)*y) for x, y in zip(r1, r2))
    # 正确:线性光平均
    l1 = [srgb_to_linear(x) for x in r1]
    l2 = [srgb_to_linear(x) for x in r2]
    lin = tuple(linear_to_srgb(frac*x + (1-frac)*y) for x, y in zip(l1, l2))
    Lg = rgb_to_lab(gam)[0]; Ll = rgb_to_lab(lin)[0]
    dL2 += Lg - Ll
    dC2 += lab_chroma(rgb_to_lab(gam)) - lab_chroma(rgb_to_lab(lin))
    n2 += 1
print("[实验2] 格内混色: sRGB空间均值 vs 线性光均值, N=%d" % n2)
print("  sRGB 均值相对线性均值: L* %+.2f (负=更暗), C* %+.2f (负=更灰)" % (dL2/n2, dC2/n2))

# ---- 实验 3:色板覆盖度 —— sRGB 随机色到最近豆色的距离 ----
ds = []
random.seed(23)
for _ in range(4000):
    src = photo_color()
    i = nearest_euclid(src)
    ds.append(delta_e00(src, PAL_LABS[i]))
ds.sort()
print("[实验3] 90色板覆盖度: 随机照片色到最近豆色的中位 ΔE=%.1f, P90=%.1f" %
      (ds[len(ds)//2], ds[int(len(ds)*0.9)]))

# ---- 实验 4:色板本身亮度分布 ----
Ls = sorted(l[0] for l in PAL_LABS)
Cs = [lab_chroma(l) for l in PAL_LABS]
print("[实验4] 90色板: 平均 L*=%.1f, 中位 L*=%.1f, 平均 C*=%.1f" %
      (sum(Ls)/len(Ls), Ls[len(Ls)//2], sum(Cs)/len(Cs)))
# 对照:随机照片色的平均 L*
random.seed(31)
srcs = [photo_color() for _ in range(8000)]
print("        随机照片色(模拟): 平均 L*=%.1f, 平均 C*=%.1f" %
      (sum(s[0] for s in srcs)/len(srcs), sum(lab_chroma(s) for s in srcs)/len(srcs)))
