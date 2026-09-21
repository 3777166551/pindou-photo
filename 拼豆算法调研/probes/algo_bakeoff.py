# -*- coding: utf-8 -*-
# 后台测试台:六类拼豆人常见题材 × 十种管线组合 × 拼豆人习惯指标,自动排名
# 运行: E:\crawl4ai\venv\Scripts\python.exe algo_bakeoff.py
# 无 UI、无真机,纯像素级模拟;色板/色彩转换与 App 源码逐行对齐
import math, random
from collections import Counter

# ================= 基础:色板与色彩(与 ColorMath.java/BeadPalettes.java 对齐) =================
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
def lin_srgb(v):
    v=max(0.0,min(1.0,v))
    s=12.92*v if v<=0.0031308 else 1.055*v**(1/2.4)-0.055
    return max(0,min(255,round(s*255)))
def rgb_lab(rgb):
    r,g,b=[srgb_lin(rgb[i]) for i in range(3)]
    x=(r*0.4124564+g*0.3575761+b*0.1804375)/0.95047
    y=(r*0.2126729+g*0.7151522+b*0.0721750)/1.0
    z=(r*0.0193339+g*0.1191920+b*0.9503041)/1.08883
    def f(t): return t**(1/3) if t>0.008856 else 7.787*t+16.0/116.0
    return (116*f(y)-16, 500*(f(x)-f(y)), 200*(f(y)-f(z)))
PLAB=[rgb_lab(c) for c in PAL]
WHITE_BEAD=max(range(len(PAL)),key=lambda i:PLAB[i][0])
BLACK_BEAD=min(range(len(PAL)),key=lambda i:PLAB[i][0])

def de2000(lab1,lab2):
    """CIEDE2000,与 ColorMath.java:76-130 逐行对齐"""
    L1,a1,b1=lab1; L2,a2,b2=lab2
    c1=math.hypot(a1,b1); c2=math.hypot(a2,b2)
    cbar=(c1+c2)*0.5
    c7=cbar**7
    g=0.5*(1-math.sqrt(c7/(c7+6103515625.0)))
    a1p=(1+g)*a1; a2p=(1+g)*a2
    c1p=math.hypot(a1p,b1); c2p=math.hypot(a2p,b2)
    h1p=0 if (a1p==0 and b1==0) else math.degrees(math.atan2(b1,a1p))%360
    h2p=0 if (a2p==0 and b2==0) else math.degrees(math.atan2(b2,a2p))%360
    dl=L2-L1; dc=c2p-c1p
    if c1p*c2p==0: dh=0.0
    else:
        dh=h2p-h1p
        if dh>180: dh-=360
        elif dh<-180: dh+=360
    dH=2*math.sqrt(c1p*c2p)*math.sin(math.radians(dh)/2)
    lbar=(L1+L2)*0.5; cbarp=(c1p+c2p)*0.5
    if c1p*c2p==0: hbar=h1p+h2p
    elif abs(h1p-h2p)<=180: hbar=(h1p+h2p)*0.5
    elif h1p+h2p<360: hbar=(h1p+h2p+360)*0.5
    else: hbar=(h1p+h2p-360)*0.5
    t=(1-0.17*math.cos(math.radians(hbar-30))+0.24*math.cos(math.radians(2*hbar))
       +0.32*math.cos(math.radians(3*hbar+6))-0.20*math.cos(math.radians(4*hbar-63)))
    dtheta=30*math.exp(-((hbar-275)/25)**2)
    cp7=cbarp**7
    rc=2*math.sqrt(cp7/(cp7+6103515625.0))
    sl=1+0.015*(lbar-50)**2/math.sqrt(20+(lbar-50)**2)
    sc=1+0.045*cbarp
    sh=1+0.015*cbarp*t
    rt=-math.sin(math.radians(2*dtheta))*rc
    tl=dl/sl; tc=dc/sc; th=dH/sh
    return math.sqrt(tl*tl+tc*tc+th*th+rt*tc*th)

# 匹配器:6bit 量化缓存(与业界做法一致,±2/通道误差可忽略)
def make_matcher(metric):
    cache={}
    def nearest(rgb):
        key=(rgb[0]>>2,rgb[1]>>2,rgb[2]>>2)
        v=cache.get(key)
        if v is not None: return v
        lab=rgb_lab(rgb)
        if metric=='de2000':
            best,bd=0,1e18
            for i,p in enumerate(PLAB):
                d=de2000(lab,p)
                if d<bd: bd,best=d,i
        else:
            best,bd=0,1e18
            for i,p in enumerate(PLAB):
                d=(lab[0]-p[0])**2+(lab[1]-p[1])**2+(lab[2]-p[2])**2
                if d<bd: bd,best=d,i
        cache[key]=best
        return best
    return nearest

# ================= 管线变体 =================
GW=GH=58

def cell_bounds(y,x,sw,sh):
    sy0=y*sh//GH; sy1=max(sy0+1,-(-(y+1)*sh//GH)); sy1=min(sy1,sh)
    sx0=x*sw//GW; sx1=max(sx0+1,-(-(x+1)*sw//GW)); sx1=min(sx1,sw)
    return sy0,sy1,sx0,sx1

def resample_mean_srgb(px,sw,sh):
    out=[]
    for y in range(GH):
        for x in range(GW):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh)
            r=g=b=0; n=0
            for yy in range(sy0,sy1):
                row=yy*sw
                for xx in range(sx0,sx1):
                    c=px[row+xx]; r+=c[0]; g+=c[1]; b+=c[2]; n+=1
            out.append((round(r/n),round(g/n),round(b/n)))
    return out

def resample_mean_lin(px,sw,sh):
    out=[]
    for y in range(GH):
        for x in range(GW):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh)
            r=g=b=0; n=0
            for yy in range(sy0,sy1):
                row=yy*sw
                for xx in range(sx0,sx1):
                    c=px[row+xx]
                    r+=srgb_lin(c[0]); g+=srgb_lin(c[1]); b+=srgb_lin(c[2]); n+=1
            out.append((lin_srgb(r/n),lin_srgb(g/n),lin_srgb(b/n)))
    return out

def resample_dominant(px,sw,sh):
    out=[]
    for y in range(GH):
        for x in range(GW):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh)
            cnt=Counter(); tot={}
            for yy in range(sy0,sy1):
                row=yy*sw
                for xx in range(sx0,sx1):
                    c=px[row+xx]
                    bk=(c[0]>>4,c[1]>>4,c[2]>>4)
                    cnt[bk]+=1
                    t=tot.get(bk,(0,0,0)); tot[bk]=(t[0]+c[0],t[1]+c[1],t[2]+c[2])
            bk=max(cnt,key=cnt.get); t=tot[bk]; n=cnt[bk]
            out.append((round(t[0]/n),round(t[1]/n),round(t[2]/n)))
    return out

def resample_vote(px,sw,sh,nearest):
    out=[]
    for y in range(GH):
        for x in range(GW):
            sy0,sy1,sx0,sx1=cell_bounds(y,x,sw,sh)
            votes=Counter()
            for yy in range(sy0,sy1):
                row=yy*sw
                for xx in range(sx0,sx1):
                    votes[nearest(px[row+xx])]+=1
            out.append(votes.most_common(1)[0][0])   # 直接是豆下标
    return out

def apply_anchors(beads, means):
    """黑白锚点:格平均色 L*>97 锚最白豆, L*<3 锚最黑豆(mean/dominant 路径用)"""
    for i,(mean,bead) in enumerate(zip(means,beads)):
        L=rgb_lab(mean)[0]
        if L>=97: beads[i]=WHITE_BEAD
        elif L<=3: beads[i]=BLACK_BEAD
    return beads

def classify_graphic(px,sw,sh):
    """图形 vs 照片 二分类:4bit 色桶数(采样步长3)。
       图形=色桶少(<64),照片=色桶多。边缘占比在硬边像素画/平滑渐变照上都不可靠,弃用"""
    buckets=set()
    for y in range(0,sh,3):
        for x in range(0,sw,3):
            c=px[y*sw+x]; buckets.add((c[0]>>4,c[1]>>4,c[2]>>4))
    return len(buckets)

def apply_enhance_if_photo(px,sw,sh):
    """照片类且欠曝/发灰时温和提亮(只提亮+轻增饱和);图形类原样返回"""
    nb=classify_graphic(px,sw,sh)
    if nb<64:                  # 图形图案:色桶少,原色神圣不可侵犯
        return px,False
    n=len(px)
    ml=sum(0.299*c[0]+0.587*c[1]+0.114*c[2] for c in px)/n
    if ml>=125:                # 亮度正常不干预
        return px,False
    gain=max(1.0,min(1.35,145.0/max(1.0,ml)))
    out=[]
    for c in px:
        r=max(0,min(255,round(c[0]*gain)))
        g=max(0,min(255,round(c[1]*gain)))
        b=max(0,min(255,round(c[2]*gain)))
        m=0.299*r+0.587*g+0.114*b
        s=1.10
        out.append((max(0,min(255,round(m+(r-m)*s))),
                    max(0,min(255,round(m+(g-m)*s))),
                    max(0,min(255,round(m+(b-m)*s)))))
    return out,True

def apply_enhance(px,sw,sh):
    """温和版"一键美化":全图亮度增益拉向目标均值 + 饱和度×1.10(模拟常见美化预设)"""
    n=len(px)
    ml=sum(0.299*c[0]+0.587*c[1]+0.114*c[2] for c in px)/n
    gain=max(1.0,min(1.35,145.0/max(1.0,ml)))   # 只提亮不压暗(真实"美化"语义)
    out=[]
    for c in px:
        r=max(0,min(255,round(c[0]*gain)))
        g=max(0,min(255,round(c[1]*gain)))
        b=max(0,min(255,round(c[2]*gain)))
        m=0.299*r+0.587*g+0.114*b
        s=1.10
        out.append((max(0,min(255,round(m+(r-m)*s))),
                    max(0,min(255,round(m+(g-m)*s))),
                    max(0,min(255,round(m+(b-m)*s)))))
    return out

def nearest_lab(l,a,b):
    best,bd=0,1e18
    for i,p in enumerate(PLAB):
        d=(l-p[0])**2+(a-p[1])**2+(b-p[2])**2
        if d<bd: bd,best=d,i
    return best

def fs_dither_lab(beads, means):
    """Lab 空间 FS 抖动(与 App 实现同构,网格层面)"""
    cur=[0.0]*(GW*3); nxt=[0.0]*(GW*3)
    out=list(beads)
    for y in range(GH):
        for x in range(GW):
            i=y*GW+x
            lab=rgb_lab(means[i])
            l=max(0.0,min(100.0,lab[0]+cur[x*3]))
            a=lab[1]+cur[x*3+1]; b=lab[2]+cur[x*3+2]
            bi=nearest_lab(l,a,b)
            out[i]=bi
            bl=PLAB[bi]
            el=l-bl[0]; ea=a-bl[1]; eb=b-bl[2]
            if x+1<GW:
                cur[(x+1)*3]+=el*7/16; cur[(x+1)*3+1]+=ea*7/16; cur[(x+1)*3+2]+=eb*7/16
            if y+1<GH:
                if x>0:
                    nxt[(x-1)*3]+=el*3/16; nxt[(x-1)*3+1]+=ea*3/16; nxt[(x-1)*3+2]+=eb*3/16
                nxt[x*3]+=el*5/16; nxt[x*3+1]+=ea*5/16; nxt[x*3+2]+=eb*5/16
                if x+1<GW:
                    nxt[(x+1)*3]+=el*1/16; nxt[(x+1)*3+1]+=ea*1/16; nxt[(x+1)*3+2]+=eb*1/16
        cur,nxt=nxt,cur
        for j in range(len(nxt)): nxt[j]=0.0
    return out

nearest_full=None  # 由 run_pipeline 注入(无缓存的网格级匹配)

def run_pipeline(px,sw,sh,cfg):
    """cfg: resample=mean_srgb/mean_lin/dominant/vote; metric=labE/de2000;
       anchors/enhance/dither=bool"""
    nearest=make_matcher(cfg['metric'])
    global nearest_full
    nearest_full=nearest
    if cfg.get('enhance'): px=apply_enhance(px,sw,sh)
    if cfg.get('enhance_photo'):
        px,_=apply_enhance_if_photo(px,sw,sh)
    if cfg['resample']=='vote':
        beads=resample_vote(px,sw,sh,nearest)
        return beads, None
    if cfg['resample']=='mean_srgb': means=resample_mean_srgb(px,sw,sh)
    elif cfg['resample']=='mean_lin': means=resample_mean_lin(px,sw,sh)
    else: means=resample_dominant(px,sw,sh)
    beads=[nearest(m) for m in means]
    if cfg.get('anchors'): beads=apply_anchors(beads,means)
    if cfg.get('dither'): beads=fs_dither_lab(beads,means)
    return beads, means

# ================= 题材(拼豆社区六类高频输入) =================
def add_disc(px,sw,sh,cx,cy,r,color):
    x0,x1=max(0,int(cx-r-2)),min(sw,int(cx+r+3))
    y0,y1=max(0,int(cy-r-2)),min(sh,int(cy+r+3))
    for y in range(y0,y1):
        for x in range(x0,x1):
            d=math.hypot(x-cx,y-cy)
            if d<=r-1: a=1.0
            elif d<=r+1: a=max(0.0,min(1.0,(r+1-d)/2))
            else: continue
            i=y*sw+x
            c=px[i]
            px[i]=tuple(round(a*color[k]+(1-a)*c[k]) for k in range(3))

def add_rect(px,sw,sh,x0,y0,x1,y1,color,aa=1.0):
    for y in range(max(0,y0-1),min(sh,y1+1)):
        for x in range(max(0,x0-1),min(sw,x1+1)):
            if x0<=x<x1 and y0<=y<y1:
                px[y*sw+x]=color

def cellrect(x0,y0,x1,y1):
    """源图坐标(480 基准)→格子矩形,缩边 1 格避开边界混合"""
    k=480/GW
    return (int(x0/k)+1,int(y0/k)+1,int(x1/k)-1,int(y1/k)-1)

def scene_cartoon():
    sw=sh=480
    px=[(255,255,255)]*(sw*sh)
    add_disc(px,sw,sh,240,250,170,(227,36,43))    # 红色主体
    add_disc(px,sw,sh,240,320,80,(247,224,30))    # 黄肚皮
    add_disc(px,sw,sh,185,185,16,(20,20,20))      # 左眼
    add_disc(px,sw,sh,295,185,16,(20,20,20))      # 右眼
    regions={'红身内部':(cellrect(150,220,330,290),(227,36,43)),
             '白底角落':(cellrect(10,410,120,470),(255,255,255)),
             '黄肚皮':(cellrect(210,305,270,335),(247,224,30))}
    mains=[(255,255,255),(227,36,43),(247,224,30),(20,20,20)]
    return px,sw,sh,dict(name='卡通(红体黄肚黑眼)',mains=mains,regions=regions)

def scene_outline():
    sw=sh=480
    px=[(255,255,255)]*(sw*sh)
    add_rect(px,sw,sh,120,120,360,360,(227,36,43))
    add_rect(px,sw,sh,96,96,384,384,(20,20,20))   # 黑描边先画,被红块盖内部 → 留 24px 边框
    add_rect(px,sw,sh,120,120,360,360,(227,36,43))
    regions={'红内部':(cellrect(150,150,330,330),(227,36,43)),
             '白底':(cellrect(10,10,80,70),(255,255,255))}
    mains=[(255,255,255),(227,36,43),(20,20,20)]
    return px,sw,sh,dict(name='简笔画(黑描边红块)',mains=mains,regions=regions)

def scene_pixelart():
    sw=sh=512; blk=16
    cols=[(227,36,43),(247,224,30),(66,165,245),(255,255,255),(20,20,20)]
    px=[(0,0,0)]*(sw*sh)
    for by in range(sw//blk):
        for bx in range(sw//blk):
            c=cols[(bx+by)%len(cols)]
            for y in range(by*blk,(by+1)*blk):
                for x in range(bx*blk,(bx+1)*blk):
                    px[y*sw+x]=c
    # 像素画的"原色保真":每格应等于其覆盖最多的源色块的豆
    k=512/GW
    exact=[]
    for y in range(GH):
        for x in range(GW):
            cx=int((x+0.5)*k)//blk; cy=int((y+0.5)*k)//blk
            exact.append(cols[(cx+cy)%len(cols)])
    return px,sw,sh,dict(name='像素画(5色马赛克,错位网格)',mains=None,regions=None,exact=exact)

def scene_landscape():
    sw=sh=480
    random.seed(5)
    px=[(0,0,0)]*(sw*sh)
    for y in range(sh):
        t=y/sh
        if t<0.62:
            u=t/0.62
            r=round(120+(255-120)*u); g=round(170+(255-170)*u); b=round(235+(255-235)*u)
        else:
            u=(t-0.62)/0.38
            r=round(76+20*u); g=round(140-40*u); b=round(60+10*u)
        for x in range(sw):
            c=(r,g,b)
            if t>=0.62:
                c=tuple(max(0,min(255,v+random.randint(-8,8))) for v in c)
            px[y*sw+x]=c
    add_disc(px,sw,sh,360,110,55,(247,224,30))
    return px,sw,sh,dict(name='风景照(渐变天+草地噪声)',mains=None,regions=None)

def scene_portrait():
    sw=sh=480
    px=[(255,255,255)]*(sw*sh)
    add_rect(px,sw,sh,110,60,370,250,(109,76,65))      # 头发
    add_disc(px,sw,sh,240,260,120,(242,205,168))       # 脸
    add_disc(px,sw,sh,196,232,13,(43,43,43))           # 左眼
    add_disc(px,sw,sh,284,232,13,(43,43,43))           # 右眼
    add_rect(px,sw,sh,215,296,265,320,(156,28,28))     # 嘴
    # 五官格:眼睛/嘴的内部格应配对"深色豆/深红豆"
    k=480/GW
    def cells_of(cx,cy,r):
        out=[]
        for y in range(GH):
            for x in range(GW):
                if math.hypot((x+0.5)*k-cx,(y+0.5)*k-cy)<=r-k*0.3: out.append(y*GW+x)
        return out
    features=[]
    features+= [(i,(43,43,43)) for i in cells_of(196,232,13)]
    features+= [(i,(43,43,43)) for i in cells_of(284,232,13)]
    for y in range(GH):
        for x in range(GW):
            if 216 <= (x+0.5)*k < 264 and 298 <= (y+0.5)*k < 318:
                features.append((y*GW+x,(156,28,28)))
    regions={'脸颊':(cellrect(180,260,220,290),(242,205,168))}
    return px,sw,sh,dict(name='人像(肤色脸+发+五官)',mains=None,regions=regions,features=features)

def scene_text():
    sw=sh=480
    px=[(255,255,255)]*(sw*sh)
    add_rect(px,sw,sh,60,120,420,150,(30,90,168))    # 粗条 30px
    add_rect(px,sw,sh,60,220,420,240,(30,90,168))    # 中条 20px
    add_rect(px,sw,sh,60,310,420,318,(30,90,168))    # 细条 8px
    k=480/GW
    def bar_cells(x0,y0,x1,y1):
        out=[]
        for y in range(GH):
            for x in range(GW):
                if x0+(y0 and 0) <= (x+0.5)*k < x1 and y0+1.5 <= (y+0.5)*k < y1-1.5:
                    out.append(y*GW+x)
        return out
    features=[]
    features+=[(i,(30,90,168)) for i in bar_cells(70,122,410,148)]
    features+=[(i,(30,90,168)) for i in bar_cells(70,222,410,238)]
    features+=[(i,(30,90,168)) for i in bar_cells(70,311.5,410,316.5)]
    regions={'白底':(cellrect(20,400,200,460),(255,255,255))}
    mains=[(255,255,255),(30,90,168)]
    return px,sw,sh,dict(name='字牌(三条横杠logo)',mains=mains,regions=regions,features=features)

# ================= 指标 =================
def components(indices_set):
    seen=set(); comps=[]
    for s in indices_set:
        if s in seen: continue
        stack=[s]; seen.add(s); comp=[]
        while stack:
            i=stack.pop(); comp.append(i)
            x,y=i%GW,i//GW
            for nx,ny in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
                j=ny*GW+nx
                if 0<=nx<GW and 0<=ny<GH and j in indices_set and j not in seen:
                    seen.add(j); stack.append(j)
        comps.append(comp)
    return comps

def components_of_color(beads):
    """同色 4-连通区域总数(越小=越好拼)"""
    bycolor={}
    for i,b in enumerate(beads):
        bycolor.setdefault(b,[]).append(i)
    total=0
    for _,idx in bycolor.items():
        total+=len(components(set(idx)))
    return total

def evaluate(beads,means,px,sw,sh,info,ref):
    m={}
    n=len(beads)
    m['用色']=len(set(beads))
    # 保真/亮度:统一参照 = 源图 sRGB 盒平均(所有管线同一把尺)
    tot=dl=0.0
    for i in range(n):
        bl=rgb_lab(PAL[beads[i]]); ml=rgb_lab(ref[i])
        tot+=math.sqrt(sum((bl[k]-ml[k])**2 for k in range(3)))
        dl+=bl[0]-ml[0]
    m['ΔE']=tot/n; m['L*偏差']=dl/n
    # 杂色
    if info.get('mains'):
        mains={nearest_full(c) for c in info['mains']}
        m['杂色']=sum(1 for b in beads if b not in mains)
    # 平坦区碎豆(区域内非主色豆的≤2格碎片数 与 总数)
    speck_cells=0; speck_comps=0; region_cells=0
    if info.get('regions'):
        for name,(rect,exp) in info['regions'].items():
            x0,y0,x1,y1=rect
            idx=[y*GW+x for y in range(max(0,y0),min(GH,y1)) for x in range(max(0,x0),min(GW,x1))]
            region_cells+=len(idx)
            eb=nearest_full(exp)
            bad={i for i in idx if beads[i]!=eb}
            for comp in components(bad):
                speck_comps+=1
                if len(comp)<=2: speck_cells+=len(comp)
        m['碎豆']=speck_cells
    # 像素画原色保真
    if info.get('exact'):
        ok=sum(1 for i,e in enumerate(info['exact']) if beads[i]==nearest_full(e))
        m['原色保真']=ok/n
    # 细节保留(五官/字条)
    if info.get('features'):
        ok=sum(1 for i,e in info['features'] if beads[i]==nearest_full(e))
        m['细节']=ok/len(info['features'])
    # 连通区域数(越小越好拼)
    m['连通区']=components_of_color(beads)
    return m

# 指标权重:拼豆人习惯假设(买豆成本/拼装体验/成品观感)
# ΔE/L*偏差 只对照片类题材计分(有渐变与纹理,平均色调有意义);
# 平坦类题材有真值(主色/原色/五官),幻影混合色本身就是要消灭的对象,
# 若用"混合平均色"当参照会反过来奖励杂色管线,故不计分仅打印参考。
WEIGHTS={'杂色':0.26,'碎豆':0.16,'用色':0.12,'原色保真':0.14,'细节':0.16,'连通区':0.0}
PHOTO_WEIGHTS={'用色':0.15,'ΔE':0.45,'L*偏差':0.15,'连通区':0.0}
def norm(name,v):
    if name=='杂色': return v/GW/GH
    if name=='碎豆': return v/300
    if name=='用色': return v/25
    if name=='ΔE': return v/20
    if name=='L*偏差': return abs(v)/8
    if name=='原色保真': return 1-v
    if name=='细节': return 1-v
    if name=='连通区': return v/700
    return 0.0

def score(m,photo):
    weights=PHOTO_WEIGHTS if photo else WEIGHTS
    tot=0.0; wsum=0.0
    for k,w in weights.items():
        if k in m:
            tot+=w*norm(k,m[k]); wsum+=w
    return tot/wsum*100 if wsum else 0.0

# ================= 主流程 =================
VARIANTS=[
    ('V1 现状(盒平均sRGB+Lab欧氏)',        dict(resample='mean_srgb',metric='labE')),
    ('V2 盒平均sRGB+CIEDE2000',           dict(resample='mean_srgb',metric='de2000')),
    ('V3 盒平均线性光+Lab欧氏',            dict(resample='mean_lin', metric='labE')),
    ('V4 盒平均线性光+CIEDE2000',          dict(resample='mean_lin', metric='de2000')),
    ('V5 先配后投(Lab欧氏)',               dict(resample='vote',    metric='labE')),
    ('V6 先配后投+CIEDE2000',              dict(resample='vote',    metric='de2000')),
    ('V7 全局众数(dominant)',              dict(resample='dominant',metric='labE')),
    ('V8 盒平均线性光+CIEDE2000+黑白锚点',  dict(resample='mean_lin',metric='de2000',anchors=True)),
    ('V9 线性光+CIEDE2000+一键增强',        dict(resample='mean_lin',metric='de2000',enhance=True)),
    ('V10 现状+FS抖动(对照)',              dict(resample='mean_srgb',metric='labE',dither=True)),
    ('V11 先配后投+CIEDE2000+自动提亮(仅照片)', dict(resample='vote',metric='de2000',enhance_photo=True)),
]
def scene_dull_photo():
    """欠曝室内感照片:暖暗背景+暗肤色圆+小物,整体 luma≈80,轻噪声"""
    sw=sh=480
    random.seed(17)
    px=[(0,0,0)]*(sw*sh)
    for y in range(sh):
        for x in range(sw):
            t=(x/sw+y/sh)/2
            r=round(55+40*t); g=round(45+30*t); b=round(40+22*t)   # 暗暖背景
            d=math.hypot(x/sw-0.52,y/sh-0.5)
            if d<0.24:
                a=max(0.0,min(1.0,(0.24-d)/0.05))
                r=round(r+(150-r)*a); g=round(g+(120-r)*a*0.8+30*a); b=round(b+(105-b)*a)
            c=tuple(max(0,min(255,v+random.randint(-8,8))) for v in (r,g,b))
            px[y*sw+x]=c
    add_disc(px,sw,sh,110,120,26,(120,90,60))
    return px,sw,sh,dict(name='欠曝照片(暗暖室内)',mains=None,regions=None)

SCENES=[scene_cartoon,scene_outline,scene_pixelart,scene_landscape,scene_portrait,scene_text,scene_dull_photo]

def main():
    import sys, time
    t0=time.time()
    all_scores={v[0]:[] for v in VARIANTS}
    table={}
    PHOTO={'风景照(渐变天+草地噪声)','人像(肤色脸+发+五官)','欠曝照片(暗暖室内)'}
    for sf in SCENES:
        px,sw,sh,info=sf()
        nb=classify_graphic(px,sw,sh)
        ref=resample_mean_srgb(px,sw,sh)     # 统一保真参照
        photo=info['name'] in PHOTO
        cls="图形" if nb<64 else "照片"
        print("="*72)
        print("题材:%s | 分类器: 色桶%d → 判定[%s] | 计分:%s"%(
            info['name'],nb,cls,"照片类,ΔE计分" if photo else "平坦类,ΔE仅参考"))
        print("="*72)
        hdr="%-26s %4s %4s %6s %7s %5s %6s %5s %5s %6s"%(
            "管线","用色","杂色","碎豆","原色保","细节","ΔE","L*","连通","得分")
        print(hdr)
        for name,cfg in VARIANTS:
            beads,means=run_pipeline(px,sw,sh,cfg)
            m=evaluate(beads,means,px,sw,sh,info,ref)
            s=score(m,photo)
            table[(info['name'],name)]=(m,s)
            all_scores[name].append(s)
            print("%-26s %4d %4s %6s %6s %5s %6.2f %+5.1f %5d %6.1f"%(
                name,m.get('用色',0),m.get('杂色','-'),m.get('碎豆','-'),
                ("%.0f%%"%(m['原色保真']*100)) if '原色保真' in m else '-',
                ("%.0f%%"%(m['细节']*100)) if '细节' in m else '-',
                m['ΔE'],m['L*偏差'],m.get('连通区',0),s))
    print(); print("="*72); print("总排名(六题材平均得分,0=最好 100=最差):"); print("="*72)
    rank=sorted(all_scores.items(),key=lambda kv:sum(kv[1])/len(kv[1]))
    for i,(name,ss) in enumerate(rank,1):
        print("%2d. %-30s 平均得分 %5.1f"%(i,name,sum(ss)/len(ss)))
    print("耗时 %.0fs"%(time.time()-t0))

if __name__=='__main__':
    main()
