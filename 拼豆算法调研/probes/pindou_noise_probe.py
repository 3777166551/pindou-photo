# -*- coding: utf-8 -*-
# 复现"简单图案出现杂色":红圆白底 -> App管线(box平均+Lab最近匹配) vs 备选方案（02 号文档复现实验）
# 运行: E:\crawl4ai\venv\Scripts\python.exe pindou_noise_probe.py
import math, random
from collections import Counter

def rgb_to_lab(rgb):
    r, g, b = [(rgb[i] / 255.0) for i in range(3)]
    def f_lin(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = f_lin(r), f_lin(g), f_lin(b)
    x = (r*0.4124564 + g*0.3575761 + b*0.1804375) / 0.95047
    y = (r*0.2126729 + g*0.7151522 + b*0.0721750) / 1.0
    z = (r*0.0193339 + g*0.1191920 + b*0.9503041) / 1.08883
    def f(t):
        return t ** (1/3) if t > 0.008856 else 7.787*t + 16.0/116.0
    return (116*f(y)-16, 500*(f(x)-f(y)), 200*(f(y)-f(z)))

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
def to_rgb(h): return ((h>>16)&255,(h>>8)&255,h&255)
PAL = [to_rgb(h) for h in (T1+T2+T3)]
PLAB = [rgb_to_lab(c) for c in PAL]

def nearest(lab):
    best, bd = 0, 1e18
    for i,p in enumerate(PLAB):
        d=(lab[0]-p[0])**2+(lab[1]-p[1])**2+(lab[2]-p[2])**2
        if d<bd: bd,best=d,i
    return best

def box_avg_srgb(px, sw, sh, dw, dh):
    """复刻 PatternEngine.boxResample(不透明图)"""
    out=[]
    for y in range(dh):
        sy0=y*sh//dh; sy1=max(sy0+1, -(-(y+1)*sh//dh)); sy1=min(sy1,sh)
        for x in range(dw):
            sx0=x*sw//dw; sx1=max(sx0+1, -(-(x+1)*sw//dw)); sx1=min(sx1,sw)
            r=g=b=0; n=0
            for yy in range(sy0,sy1):
                for xx in range(sx0,sx1):
                    c=px[yy*sw+xx]; r+=c[0]; g+=c[1]; b+=c[2]; n+=1
            out.append((round(r/n),round(g/n),round(b/n)))
    return out

def dominant_resample(px, sw, sh, dw, dh):
    """复刻 dominantResample(4bit量化桶众数)"""
    out=[]
    for y in range(dh):
        sy0=y*sh//dh; sy1=max(sy0+1, -(-(y+1)*sh//dh)); sy1=min(sy1,sh)
        for x in range(dw):
            sx0=x*sw//dw; sx1=max(sx0+1, -(-(x+1)*sw//dw)); sx1=min(sx1,sw)
            cnt={}; tot={}
            for yy in range(sy0,sy1):
                for xx in range(sx0,sx1):
                    c=px[yy*sw+xx]
                    bk=(c[0]>>4, c[1]>>4, c[2]>>4)
                    cnt[bk]=cnt.get(bk,0)+1
                    t=tot.get(bk,(0,0,0)); tot[bk]=(t[0]+c[0],t[1]+c[1],t[2]+c[2])
            bk=max(cnt,key=cnt.get)
            t=tot[bk]; n=cnt[bk]
            out.append((round(t[0]/n),round(t[1]/n),round(t[2]/n)))
    return out

def match_first_majority(px, sw, sh, dw, dh):
    """备选方案:逐像素先匹配豆色,格内取众数豆"""
    out=[]
    for y in range(dh):
        sy0=y*sh//dh; sy1=max(sy0+1, -(-(y+1)*sh//dh)); sy1=min(sy1,sh)
        for x in range(dw):
            sx0=x*sw//dw; sx1=max(sx0+1, -(-(x+1)*sw//dw)); sx1=min(sx1,sw)
            cnt=Counter()
            for yy in range(sy0,sy1):
                for xx in range(sx0,sx1):
                    cnt[nearest(rgb_to_lab(px[yy*sw+xx]))]+=1
            bi=cnt.most_common(1)[0][0]
            out.append(PAL[bi])
    return out

def make_scene(kind, sw=480, sh=480, noise=0, seed=3):
    """简单图案:红圆/红方块/黄蓝拼接,白底,带抗锯齿边缘"""
    random.seed(seed)
    px=[(255,255,255)]*(sw*sh)
    def inside(x,y):
        if kind=='circle':
            return (x-sw/2)**2+(y-sh/2)**2 <= (sw*0.36)**2
        if kind=='square':
            return sw*0.2<=x<=sw*0.8 and sh*0.2<=y<=sh*0.8
        if kind=='stripe':
            return x>=sw*0.42
    for y in range(sh):
        for x in range(sw):
            if inside(x,y):
                # 4x 超采样做抗锯齿
                hit=0
                for dy in range(4):
                    for dx in range(4):
                        if inside(x+dx/4-0.375, y+dy/4-0.375): hit+=1
                a=hit/16.0
                base=(227,36,43)  # 大红 0xE3242B
                if kind=='stripe': base=(247,224,30)  # 柠檬黄
                c=tuple(round(a*b0+(1-a)*255) for b0 in base)
                if noise:
                    c=tuple(max(0,min(255,v+random.randint(-noise,noise))) for v in c)
                px[y*sw+x]=c
    return px, sw, sh

def report(name, cells, gw, gh, expect_main_lab):
    used=Counter()
    stray=0
    for c in cells:
        i=nearest(rgb_to_lab(c)); used[i]+=1
    # 杂色定义:不属于两种主色(红0xE3242B/白0xFFFFFF 或 黄/白)的豆
    mains={nearest(rgb_to_lab(c)) for c in expect_main_lab}
    stray=sum(v for k,v in used.items() if k not in mains)
    kinds=len(used)
    top=used.most_common()
    names=[]
    for k,v in top[:6]:
        rgb=PAL[k]
        tag="主" if k in mains else "杂"
        names.append("#%02X%02X%02X×%d(%s)"%(rgb[0],rgb[1],rgb[2],v,tag))
    print("  %s: 用色%d种, 杂色豆%d颗 [%s]"%(name,kinds,stray,"  ".join(names)))

for kind, mains in [('circle',[0xE3242B,0xFFFFFF]),
                    ('square',[0xE3242B,0xFFFFFF]),
                    ('stripe',[0xF7E01E,0xFFFFFF])]:
    px,sw,sh=make_scene(kind)
    gw=gh=58
    exp=[to_rgb(h) for h in mains]
    print("[%s 白底 480->58格]"%kind)
    cells=box_avg_srgb(px,sw,sh,gw,gh)
    report("App现状(盒平均+Lab最近)", cells, gw, gh, exp)
    cells=dominant_resample(px,sw,sh,gw,gh)
    report("众数采样(dominant开)", cells, gw, gh, exp)
    cells=match_first_majority(px,sw,sh,gw,gh)
    report("先匹配后投票(备选)", cells, gw, gh, exp)

# 加 JPEG 噪声的平坦区:红方块,噪声±6
px,sw,sh=make_scene('square', noise=6)
print("[红方块+平坦区噪声±6 480->58格]")
cells=box_avg_srgb(px,sw,sh,58,58)
report("App现状", cells, 58, 58, [to_rgb(0xE3242B),to_rgb(0xFFFFFF)])
cells=dominant_resample(px,sw,sh,58,58)
report("众数采样", cells, 58, 58, [to_rgb(0xE3242B),to_rgb(0xFFFFFF)])
