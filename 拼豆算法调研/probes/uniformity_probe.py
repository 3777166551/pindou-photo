# -*- coding: utf-8 -*-
# 压力测试:先配后投在"简单色/简单图案"下的颜色统一性
# 三种压力源:①JPEG块状噪声纯色 ②渐变白底 ③卡在两颗豆色正中间的源色
# 运行: E:\crawl4ai\venv\Scripts\python.exe uniformity_probe.py
import math, random
from collections import Counter

T1=[0xFFFFFF,0xF7F0DD,0xA8A8A8,0x4A4A4A,0x141414,0xE3242B,0x9C1C1C,0xE4007C,
    0xF48FB1,0xF57C00,0xF7E01E,0xF5A623,0x43A047,0x1B5E20,0x26A69A,0x42A5F5,
    0x1E5AA8,0x0D2C6B,0x7B3FA0,0x795548,0xC8A17B,0xF5CBA0,0x6D4C41,0x90CAF9]
T2=[0xD9D9D9,0x757575,0x7B1113,0xA04A3A,0xFF7F6E,0xFFB6C1,0xE91E63,0xFFB74D,
    0xE65100,0xFFF59D,0xFBC02D,0xB8A233,0x7CB342,0x556B2F,0x00838F,0x4DD0E1,
    0x039BE5,0x1A237E,0x607D8B,0x512DA8,0xD1C4E9,0xA1887F,0xC3B091,0xFADCC8]
T3=[0xEAE0C0,0x2B2B2B,0xA29A8C,0x6B7075,0xEA4A28,0x5C0E1E,0xDDBAC2,0xF2CD9A,
    0xE8A575,0x9E8B3A,0xFFBE0B,0xA9C24A,0x0E3620,0xA5DBC8,0x92A683,0x63E62E,
    0x13876A,0xD6E9F8,0x3949AB,0x2E7CFF,0x6A5ACD,0x452159,0xC0A5C9,0x3E2723,
    0x6E2C1B,0x8B4226,0xA9713F,0xD2A24C,0xC0C7CE,0xD4AF37,0xFF6F3C,0x0277BD,
    0x38623C,0xE3D5AE,0xCB6843,0xA9BFD4,0xD3ACAF,0x2F211A,0x00A651,0x6E7623,
    0xAD0A63,0xC4825A]
def to_rgb(h): return ((h>>16)&255,(h>>8)&255,h&255)
PAL=[to_rgb(h) for h in (T1+T2+T3)]
def srgb_lin(c):
    c/=255.0
    return c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4
def rgb_lab(rgb):
    r,g,b=[srgb_lin(rgb[i]) for i in range(3)]
    x=(r*0.4124564+g*0.3575761+b*0.1804375)/0.95047
    y=(r*0.2126729+g*0.7151522+b*0.0721750)/1.0
    z=(r*0.0193339+g*0.1191920+b*0.9503041)/1.08883
    def f(t): return t**(1/3) if t>0.008856 else 7.787*t+16.0/116.0
    return (116*f(y)-16, 500*(f(x)-f(y)), 200*(f(y)-f(z)))
PLAB=[rgb_lab(c) for c in PAL]
WHITE=max(range(len(PAL)),key=lambda i:PLAB[i][0])

_cache={}
def nearest(rgb):
    key=(rgb[0]>>2,rgb[1]>>2,rgb[2]>>2)
    v=_cache.get(key)
    if v is not None: return v
    lab=rgb_lab(rgb)
    best,bd=0,1e18
    for i,p in enumerate(PLAB):
        d=(lab[0]-p[0])**2+(lab[1]-p[1])**2+(lab[2]-p[2])**2
        if d<bd: bd,best=d,i
    _cache[key]=best
    return best

GW=GH=58
def cell_bounds(y,x,sw,sh):
    sy0=y*sh//GH; sy1=max(sy0+1,-(-(y+1)*sh//GH)); sy1=min(sy1,sh)
    sx0=x*sw//GW; sx1=max(sx0+1,-(-(x+1)*sw//GW)); sx1=min(sx1,sw)
    return sy0,sy1,sx0,sx1

def run_current(px,sw,sh):
    out=[]
    for y in range(GH):
        for x in range(GW):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh)
            r=g=b=0; n=0
            for yy in range(sy0,sy1):
                for xx in range(sx0,sx1):
                    c=px[yy*sw+xx]; r+=c[0]; g+=c[1]; b+=c[2]; n+=1
            out.append(nearest((round(r/n),round(g/n),round(b/n))))
    return out

def run_vote(px,sw,sh):
    out=[]
    for y in range(GH):
        for x in range(GW):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh)
            votes=Counter()
            for yy in range(sy0,sy1):
                for xx in range(sx0,sx1):
                    votes[nearest(px[yy*sw+xx])]+=1
            out.append(votes.most_common(1)[0][0])
    return out

def unify_isolated(beads):
    """一次性孤格吸附:某格与 8 邻里中 >=6 个不同且邻居同色 → 吸附(等价"杂色清理"对孤点的作用)"""
    out=list(beads)
    for y in range(GH):
        for x in range(GW):
            i=y*GW+x
            nb=[out[j] for dy in (-1,0,1) for dx in (-1,0,1)
                if (dx or dy) and 0<=x+dx<GW and 0<=y+dy<GH
                for j in [(y+dy)*GW+(x+dx)]]
            cnt=Counter(nb)
            maj,majn=cnt.most_common(1)[0]
            if out[i]!=maj and majn>=6:
                out[i]=maj
    return out

def region_report(name,beads,cells,label):
    cnt=Counter(beads[i] for i in cells)
    maj,majn=cnt.most_common(1)[0]
    mottle=sum(v for k,v in cnt.items() if k!=maj)
    colors=len(cnt)
    names=",".join("#%02X%02X%02X×%d"%(PAL[k][0],PAL[k][1],PAL[k][2],v) for k,v in cnt.most_common(4))
    print("    %-14s 区内用色%d 花格%d/%d [%s]"%(label,colors,mottle,len(cells),names))

def interior_cells(cx0,cy0,cx1,cy1):
    return [y*GW+x for y in range(cy0,cy1) for x in range(cx0,cx1)]

def jpeg_noise(px,sw,sh,region,delta,seed):
    """8×8 块状噪声模拟 JPEG 压缩:每块一个偏移"""
    random.seed(seed)
    out=list(px)
    x0,y0,x1,y1=region
    for by in range(y0,y1,8):
        for bx in range(x0,x1,8):
            off=tuple(random.randint(-delta,delta) for _ in range(3))
            for y in range(by,min(by+8,y1)):
                for x in range(bx,min(bx+8,x1)):
                    c=out[y*sw+x]
                    out[y*sw+x]=tuple(max(0,min(255,c[k]+off[k])) for k in range(3))
    return out

SW=SH=480
def base_scene():
    px=[(255,255,255)]*(SW*SH)
    # 红色圆盘 r=170
    for y in range(SH):
        for x in range(SW):
            if (x-240)**2+(y-250)**2<=170**2:
                px[y*SW+x]=(227,36,43)
    return px

# 格子坐标系里的红色内部(避开边缘混合带)与白色角落
RED_CELLS=interior_cells(8,12,50,44)
WHITE_CELLS=interior_cells(2,52,14,57)

def run_case(title, make_px, seed_note):
    print("  "+title)
    px=make_px()
    a=run_current(px,SW,SH); b=run_vote(px,SW,SH); c=unify_isolated(b)
    region_report("",a,RED_CELLS,"现状红区");   region_report("",a,WHITE_CELLS,"现状白区")
    region_report("",b,RED_CELLS,"投票红区");   region_report("",b,WHITE_CELLS,"投票白区")
    region_report("",c,RED_CELLS,"投票+清杂红区"); region_report("",c,WHITE_CELLS,"投票+清杂白区")
    # 全图用色
    print("    全图用色: 现状%d 投票%d 投票+清杂%d"%(
        len(set(a)),len(set(b)),len(set(c))))

print("="*66)
print("压力① 平坦红色区 + JPEG 块状噪声(±4/±8/±14),白底")
print("="*66)
for d in (4,8,14):
    run_case("噪声±%d"%d, lambda d=d: jpeg_noise(base_scene(),SW,SH,(30,30,450,450),d,7), "")

print()
print("="*66)
print("压力② 渐变白底(255→215 平滑明度渐变,无任何图案) —— 看白底会不会花")
print("="*66)
def make_grad():
    px=[(0,0,0)]*(SW*SH)
    for y in range(SH):
        v=255-int(40*(y/SH))
        for x in range(SW):
            px[y*SW+x]=(v,v,v)
    return px
px=make_grad()
a=run_current(px,SW,SH); b=run_vote(px,SW,SH)
ca=Counter(a); cb=Counter(b)
def nm(cnt):
    return " ".join("#%02X%02X%02X×%d"%(PAL[k][0],PAL[k][1],PAL[k][2],v) for k,v in cnt.most_common(5))
print("    现状白底用色%d [%s]"%(len(ca),nm(ca)))
print("    投票白底用色%d [%s]"%(len(cb),nm(cb)))

print()
print("="*66)
print("压力③ 源色卡在两颗豆正中间(大红E3242B ↔ 珊瑚红FF7F6E 的 Lab中点)")
print("="*66)
mid=tuple(round((PLAB[5][k]+PLAB[4][k])/2) for k in range(3))
# Lab中点反解近似:在两豆 RGB 间扫描找最近 Lab 中点的 RGB
best=None
for r in range(220,256):
    for g in range(50,130):
        for b_ in range(60,140):
            pass
# 避免暴力:直接取两豆RGB均值再微调
midrgb=tuple(round((PAL[5][k]+PAL[4][k])/2) for k in range(3))
print("    两豆: 大红#E3242B ↔ 珊瑚红#FF7F6E, 取RGB中点 #%02X%02X%02X (Lab距两豆各≈%.1f)"%(
    midrgb[0],midrgb[1],midrgb[2],
    math.sqrt(sum((rgb_lab(midrgb)[k]-PLAB[5][k])**2 for k in range(3)))))
def make_mid(d=0):
    px=[(255,255,255)]*(SW*SH)
    for y in range(SH):
        for x in range(SW):
            if (x-240)**2+(y-250)**2<=170**2:
                c=tuple(max(0,min(255,v+random.randint(-d,d))) for v in midrgb)
                px[y*SW+x]=c
    return px
random.seed(3)
px=make_mid()
a=run_current(px,SW,SH); b=run_vote(px,SW,SH)
region_report("",a,RED_CELLS,"现状红区"); region_report("",b,RED_CELLS,"投票红区")
print("    (两算法在此极端都会在两豆间摇摆——见下方'清杂'是否救平)")
c=unify_isolated(b)
region_report("",c,RED_CELLS,"投票+清杂红区")
print("    现状全图用色%d, 投票全图用色%d"%(len(set(a)),len(set(b))))
