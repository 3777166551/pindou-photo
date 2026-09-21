# -*- coding: utf-8 -*-
# 三种抗杂色方案对比:简单图案(杂色数) vs 照片场景(保真度ΔE)（02 号文档四方案对比实验）
# 运行: E:\crawl4ai\venv\Scripts\python.exe pindou_fix_compare.py
import math, random
from collections import Counter

def rgb_to_lab(rgb):
    r, g, b = [(rgb[i] / 255.0) for i in range(3)]
    def fl(c): return c/12.92 if c <= 0.04045 else ((c+0.055)/1.055)**2.4
    r, g, b = fl(r), fl(g), fl(b)
    x=(r*0.4124564+g*0.3575761+b*0.1804375)/0.95047
    y=(r*0.2126729+g*0.7151522+b*0.0721750)/1.0
    z=(r*0.0193339+g*0.1191920+b*0.9503041)/1.08883
    def f(t): return t**(1/3) if t > 0.008856 else 7.787*t+16.0/116.0
    return (116*f(y)-16, 500*(f(x)-f(y)), 200*(f(y)-f(z)))

def srgb_to_lin(c):
    c/=255.0
    return c/12.92 if c <= 0.04045 else ((c+0.055)/1.055)**2.4
def lin_to_srgb(v):
    v=max(0.0,min(1.0,v))
    s=12.92*v if v <= 0.0031308 else 1.055*v**(1/2.4)-0.055
    return max(0,min(255,round(s*255)))

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
PLAB=[rgb_to_lab(c) for c in PAL]

_ncache={}
def nearest(lab):
    key=(round(lab[0],1),round(lab[1],1),round(lab[2],1))
    v=_ncache.get(key)
    if v is not None: return v
    best,bd=0,1e18
    for i,p in enumerate(PLAB):
        d=(lab[0]-p[0])**2+(lab[1]-p[1])**2+(lab[2]-p[2])**2
        if d<bd: bd,best=d,i
    _ncache[key]=best
    return best

def de(a,b):
    return math.sqrt((a[0]-b[0])**2+(a[1]-b[1])**2+(a[2]-b[2])**2)

def cell_bounds(y,x,sw,sh,dw,dh):
    sy0=y*sh//dh; sy1=max(sy0+1,-(-(y+1)*sh//dh)); sy1=min(sy1,sh)
    sx0=x*sw//dw; sx1=max(sx0+1,-(-(x+1)*sw//dw)); sx1=min(sx1,sw)
    return sy0,sy1,sx0,sx1

def gen_pixels_of(px,sy0,sy1,sx0,sx1):
    for yy in range(sy0,sy1):
        row=yy*SW
        for xx in range(sx0,sx1):
            yield px[row+xx]

# ---------- 方案实现 ----------
def run_current(px,sw,sh,dw,dh):
    """App现状: sRGB盒平均 -> Lab最近"""
    out=[]
    for y in range(dh):
        for x in range(dw):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh,dw,dh)
            r=g=b=0; n=0
            for c in gen_pixels_of(px,sy0,sy1,sx0,sx1):
                r+=c[0]; g+=c[1]; b+=c[2]; n+=1
            out.append((round(r/n),round(g/n),round(b/n)))
    return [PAL[nearest(rgb_to_lab(c))] for c in out]

def run_dominant(px,sw,sh,dw,dh):
    """众数采样(全部格子)"""
    out=[]
    for y in range(dh):
        for x in range(dw):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh,dw,dh)
            cnt=Counter(); tot={}
            for c in gen_pixels_of(px,sy0,sy1,sx0,sx1):
                bk=(c[0]>>4,c[1]>>4,c[2]>>4)
                cnt[bk]+=1
                t=tot.get(bk,(0,0,0)); tot[bk]=(t[0]+c[0],t[1]+c[1],t[2]+c[2])
            bk=max(cnt,key=cnt.get); t=tot[bk]; n=cnt[bk]
            out.append((round(t[0]/n),round(t[1]/n),round(t[2]/n)))
    return [PAL[nearest(rgb_to_lab(c))] for c in out]

def run_vote(px,sw,sh,dw,dh):
    """逐像素先匹配豆色,格内众数豆"""
    out=[]
    for y in range(dh):
        for x in range(dw):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh,dw,dh)
            cnt=Counter()
            for c in gen_pixels_of(px,sy0,sy1,sx0,sx1):
                cnt[nearest(rgb_to_lab(c))]+=1
            out.append(PAL[cnt.most_common(1)[0][0]])
    return out

def run_hybrid(px,sw,sh,dw,dh):
    """混合:每格算平均豆与投票豆;双峰(边界格)用投票,否则用平均。
       双峰判定:两个最大色桶合计>=70% 且两桶均值 ΔE>20"""
    out=[]
    for y in range(dh):
        for x in range(dw):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh,dw,dh)
            pix=list(gen_pixels_of(px,sy0,sy1,sx0,sx1))
            n=len(pix)
            r=sum(c[0] for c in pix)/n; g=sum(c[1] for c in pix)/n; b=sum(c[2] for c in pix)/n
            mean_bead=nearest(rgb_to_lab((round(r),round(g),round(b))))
            cnt=Counter(); tot={}
            votes=Counter()
            for c in pix:
                bk=(c[0]>>4,c[1]>>4,c[2]>>4)
                cnt[bk]+=1
                t=tot.get(bk,(0,0,0)); tot[bk]=(t[0]+c[0],t[1]+c[1],t[2]+c[2])
                votes[nearest(rgb_to_lab(c))]+=1
            top=cnt.most_common(2)
            use_vote=False
            if len(top)==2 and (top[0][1]+top[1][1])>=0.7*n:
                t0=tot[top[0][0]]; t1=tot[top[1][0]]
                m0=rgb_to_lab((round(t0[0]/top[0][1]),round(t0[1]/top[0][1]),round(t0[2]/top[0][1])))
                m1=rgb_to_lab((round(t1[0]/top[1][1]),round(t1[1]/top[1][1]),round(t1[2]/top[1][1])))
                if de(m0,m1)>20: use_vote=True
            if use_vote:
                out.append(PAL[votes.most_common(1)[0][0]])
            else:
                out.append(PAL[mean_bead])
    return out

# ---------- 场景 ----------
SW=SH=480; GW=GH=58
def px_index(x,y): return y*SW+x

def scene_flat(kind,noise=0,seed=3):
    random.seed(seed)
    px=[(255,255,255)]*(SW*SH)
    def inside(x,y):
        if kind=='circle': return (x-SW/2)**2+(y-SH/2)**2 <= (SW*0.36)**2
        if kind=='square': return SW*0.2<=x<=SW*0.8 and SH*0.2<=y<=SH*0.8
        return x>=SW*0.42
    for y in range(SH):
        for x in range(SW):
            if inside(x,y):
                hit=0
                for dy in range(4):
                    for dx in range(4):
                        if inside(x+dx/4-0.375,y+dy/4-0.375): hit+=1
                a=hit/16.0
                base=(227,36,43) if kind!='stripe' else (247,224,30)
                c=tuple(round(a*bb+(1-a)*255) for bb in base)
                if noise: c=tuple(max(0,min(255,v+random.randint(-noise,noise))) for v in c)
                px[px_index(x,y)]=c
    return px

def scene_gradient():
    """天空渐变 + 软边太阳:照片式平滑渐变"""
    px=[(0,0,0)]*(SW*SH)
    for y in range(SH):
        for x in range(SW):
            t=y/SH
            # 浅蓝->白 的垂直渐变
            r=round(140+(255-140)*t); g=round(180+(255-180)*t); b=round(235+(255-235)*t)
            # 太阳:中心(0.3,0.3) 软边
            d=math.hypot(x/SW-0.3,y/SH-0.3)
            if d<0.18:
                a=max(0.0,min(1.0,(0.18-d)/0.06))
                r=round(r+(255-r)*a); g=round(g+(244-g)*a); b=round(b+(180-b)*a)
            px[px_index(x,y)]=(r,g,b)
    return px

def scene_photo_like(seed=9):
    """模拟照片:多块平滑渐变区域 + 轻噪声 + 软边圆(人脸感肤色)"""
    random.seed(seed)
    px=[(0,0,0)]*(SW*SH)
    for y in range(SH):
        for x in range(SW):
            tx=x/SW; ty=y/SH
            # 背景:对角渐变 草绿->深绿
            t=(tx+ty)/2
            r=round(90+70*t); g=round(140+50*t); b=round(60+40*t)
            # 肤色软边圆
            d=math.hypot(tx-0.55,ty-0.45)
            if d<0.22:
                a=max(0.0,min(1.0,(0.22-d)/0.05))
                r=round(r+(242-r)*a); g=round(g+(205-g)*a); b=round(b+(168-b)*a)
            # 红色小旗
            if 0.12<tx<0.3 and 0.1<ty<0.28:
                a=1.0
                r=round(r+(227-r)*a); g=round(g+(36-g)*a); b=round(b+(43-b)*a)
            c=tuple(max(0,min(255,v+random.randint(-5,5))) for v in (r,g,b))
            px[px_index(x,y)]=c
    return px

def stray_count(cells, mains):
    ms={nearest(rgb_to_lab(c)) for c in mains}
    s=0
    for c in cells:
        if nearest(rgb_to_lab(c)) not in ms: s+=1
    return s

def fidelity(cells, px, sw, sh, dw, dh):
    """平均 ΔE(格真实平均色Lab vs 最终豆色Lab) + 亮度偏差"""
    tot=0.0; dl=0.0; n=0
    for y in range(dh):
        for x in range(dw):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh,dw,dh)
            r=g=b=0; k=0
            for c in gen_pixels_of(px,sy0,sy1,sx0,sx1):
                r+=c[0]; g+=c[1]; b+=c[2]; k+=1
            mean_lab=rgb_to_lab((round(r/k),round(g/k),round(b/k)))
            bead_lab=rgb_to_lab(cells[y*dw+x])
            tot+=de(mean_lab,bead_lab)
            dl+=bead_lab[0]-mean_lab[0]
            n+=1
    return tot/n, dl/n

print("="*62)
print("A. 简单图案 — 杂色豆数(越少越好)")
print("="*62)
scenes=[("红圆白底", scene_flat('circle'), [(227,36,43),(255,255,255)]),
        ("红方白底", scene_flat('square'), [(227,36,43),(255,255,255)]),
        ("黄条白底", scene_flat('stripe'), [(247,224,30),(255,255,255)]),
        ("红方+噪声", scene_flat('square',noise=6), [(227,36,43),(255,255,255)])]
for name,px,mains in scenes:
    row=[]
    for label,fn in [("现状",run_current),("自动众数",run_dominant),("混合",run_hybrid),("先配后投",run_vote)]:
        cells=fn(px,SW,SH,GW,GH)
        row.append("%s:%d"%(label,stray_count(cells,mains)))
    print("  %-8s  %s"%(name,"  ".join(row)))

print()
print("="*62)
print("B. 渐变/照片场景 — 保真度: 平均ΔE(越低越好) / L*偏差(越接近0越好) / 用色数")
print("="*62)
for name,px in [("天空渐变+太阳", scene_gradient()), ("照片感(渐变+肤色圆+旗)", scene_photo_like())]:
    for label,fn in [("现状",run_current),("自动众数",run_dominant),("混合",run_hybrid),("先配后投",run_vote)]:
        cells=fn(px,SW,SH,GW,GH)
        f,dl=fidelity(cells,px,SW,SH,GW,GH)
        kinds=len({nearest(rgb_to_lab(c)) for c in cells})
        print("  %-14s %-10s ΔE=%.2f  L*%+.2f  用色%d种"%(name,label,f,dl,kinds))
    print()
