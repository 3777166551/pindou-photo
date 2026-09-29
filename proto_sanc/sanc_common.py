# -*- coding: utf-8 -*-
"""proto_sanc 共用件:模型加载/推理封装/色彩工具。仅 PC 原型验证用,不是 APP 代码。
写出仅允许 proto_sanc/out;豆色板用 bead_palette_data 字面量,不做任何跨目录文件读取。"""
import ctypes
import os

import cv2
import numpy as np
import onnxruntime as ort

from bead_palette_data import PALETTE_90

REPO = r'F:\delete\PDAPP'
ROOT = os.path.join(REPO, 'proto_sanc')
OUT = os.path.join(ROOT, 'out')
ML = os.path.join(REPO, 'tools', 'bgtest', 'ml')

_REAL_OUT = os.path.realpath(OUT)


def safe_out(name):
    """out/ 下的相对文件名;拒绝一切路径分隔与跳转,防路径逃逸。"""
    if os.path.sep in name or '/' in name or '..' in name:
        raise ValueError('bad out name: %r' % name)
    rp = os.path.realpath(os.path.join(OUT, name))
    if not rp.startswith(_REAL_OUT):
        raise ValueError('write outside OUT is not allowed')
    return rp


def idle_priority():
    """空闲优先级:前台干活优先,本进程让路。"""
    try:
        ctypes.windll.kernel32.SetPriorityClass(
            ctypes.windll.kernel32.GetCurrentProcess(), 0x00000040)
    except Exception:
        pass


_CAND = {
    'ghibli':  [os.path.join(REPO, 'app', 'src', 'main', 'assets', 'animeganv3_ghibli.onnx'),
                os.path.join(ML, 'animegan3_ghibli.onnx')],
    'shinkai': [os.path.join(ML, 'animegan3_shinkai.onnx')],
    'jp_face': [os.path.join(ML, 'dl', 'AnimeGANv3_JP_face.onnx')],
    'wbc':     [os.path.join(ML, 'dl', 'wbc_pinto', 'white_box_cartoonization_1x3x720x720.onnx')],
    'lines':   [os.path.join(ML, 'dl', 'informative_drawings.onnx')],
    'yunet':   [os.path.join(ML, 'yunet.onnx')],
}

_SESS = {}


def sess(key):
    p = None
    for c in _CAND.get(key, []):
        if os.path.isfile(c):
            p = c
            break
    if p is None:
        return None
    if key not in _SESS:
        _SESS[key] = ort.InferenceSession(p, providers=['CPUExecutionProvider'])
    return _SESS[key]


def has(key):
    return sess(key) is not None


def _run(s, x):
    return s.run(None, {s.get_inputs()[0].name: x})[0]


def pad32(h, w):
    H = max(32, (h + 15) // 32 * 32)
    W = max(32, (w + 15) // 32 * 32)
    return H, W


def animegan(img_rgb, key):
    """AnimeGANv3 系:NHWC [1,H,W,3], RGB/127.5-1, 32 对齐, 出图 (x+1)*127.5。"""
    s = sess(key)
    if s is None:
        return None
    h, w = img_rgb.shape[:2]
    H, W = pad32(h, w)
    x = cv2.resize(img_rgb, (W, H), interpolation=cv2.INTER_AREA) if (H, W) != (h, w) else img_rgb
    x = (x[None] / 127.5 - 1).astype(np.float32)
    y = _run(s, x)[0]
    y = np.clip((y + 1) * 127.5, 0, 255)
    if y.shape[:2] != (h, w):
        y = cv2.resize(y, (w, h), interpolation=cv2.INTER_LINEAR)
    return y.astype(np.float32)


def wbc720(img_rgb):
    """WBC(PINTO):实测输入 NCHW [1,3,720,720](调研文档勘误), tanh 输出需 clamp。"""
    s = sess('wbc')
    if s is None:
        return None
    h, w = img_rgb.shape[:2]
    x = cv2.resize(img_rgb, (720, 720), interpolation=cv2.INTER_AREA)
    x = (x.transpose(2, 0, 1)[None] / 127.5 - 1).astype(np.float32)
    y = np.asarray(_run(s, x))
    if y.ndim == 4:
        y = y[0]
    if y.ndim == 3 and y.shape[0] == 3:
        y = y.transpose(1, 2, 0)
    y = np.clip((y + 1) * 127.5, 0, 255)
    return cv2.resize(y, (w, h), interpolation=cv2.INTER_LINEAR).astype(np.float32)


def lines_ink(img_rgb):
    """Informative Drawings 线稿:NCHW /255 动态尺寸。返回 0..1,1=墨线。"""
    s = sess('lines')
    if s is None:
        return None
    h, w = img_rgb.shape[:2]
    H, W = pad32(h, w)
    x = cv2.resize(img_rgb, (W, H), interpolation=cv2.INTER_AREA) if (H, W) != (h, w) else img_rgb
    x = (x.transpose(2, 0, 1)[None] / 255.0).astype(np.float32)
    y = np.asarray(_run(s, x))
    if y.ndim == 4:
        y = y[0]
    if y.ndim == 3 and 1 in (y.shape[0], y.shape[-1]):
        y = y[..., 0] if y.shape[-1] == 1 else y[0]
    y = y.astype(np.float32)
    if y.shape[:2] != (h, w):
        y = cv2.resize(y, (w, h))
    ink = 1.0 - y / 255.0 if y.max() > 1.5 else 1.0 - y
    if ink.mean() > 0.5:
        ink = 1.0 - ink  # 极性自检:白底黑线应为少数墨
    return np.clip(ink, 0, 1)


def detect_faces(img_bgr, score_thr=0.6):
    p = _CAND['yunet'][0]
    if not os.path.isfile(p):
        return []
    det = cv2.FaceDetectorYN.create(p, '', (img_bgr.shape[1], img_bgr.shape[0]),
                                    score_thr, 0.3, 5000)
    ok, faces = det.detect(img_bgr)
    if faces is None:
        return []
    return [f for f in faces if f[14] >= score_thr]


_TEMPL = np.array([[38.29, 51.69], [73.53, 51.50], [56.02, 71.74],
                   [41.55, 92.37], [70.73, 92.20]], np.float32)


def align_face(img_rgb, face, size=512):
    """5 点相似变换到 size 对齐脸。返回 (aligned, Minv) 或 (None, None)。"""
    lm = np.asarray(face[4:14], np.float32).reshape(5, 2)
    k = size / 112.0 * 0.92
    dst = _TEMPL * k
    dst[:, 0] += (size - 112 * k) / 2
    dst[:, 1] += (size - 112 * k) / 2 - 0.06 * size
    M, _ = cv2.estimateAffinePartial2D(lm, dst)
    if M is None:
        return None, None
    aligned = cv2.warpAffine(img_rgb, M, (size, size), flags=cv2.INTER_LINEAR,
                             borderMode=cv2.BORDER_REFLECT)
    return aligned, cv2.invertAffineTransform(M)


def feather_mask(shape, face):
    """贴回用羽化椭圆掩膜(兜底;优先用 ParseNet 语义掩膜)。0..1,1=贴风格脸。"""
    H, W = shape[:2]
    x, y, w, h = [float(v) for v in face[:4]]
    m = np.zeros((H, W), np.float32)
    cx, cy = x + w * 0.5, y + h * 0.50
    cv2.ellipse(m, (int(cx), int(cy)), (int(w * 0.60), int(h * 0.80)), 0, 0, 360, 1.0, -1)
    m = cv2.GaussianBlur(m, (0, 0), max(w, h) * 0.05)
    return np.clip(m, 0, 1)[..., None]


_CAND['parsenet'] = [os.path.join(ML, 'parsing_parsenet_int8.onnx'),
                     os.path.join(ML, 'parsing_parsenet.onnx')]
_CAND['u2net'] = [os.path.join(REPO, 'app', 'src', 'main', 'assets', 'u2netp.onnx'),
                  os.path.join(ML, 'u2netp.onnx')]
_IMNET_MEAN = np.array([0.485, 0.456, 0.406], np.float32)
_IMNET_STD = np.array([0.229, 0.224, 0.225], np.float32)
# face-parsing.PyTorch 19 类:取脸核心+颈,排除眼镜(6)/耳饰(9)/衣(16)/发(17)/帽(18)
_FACE_CLASSES = np.array([1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14, 15])


def face_parse_classes(aligned_rgb):
    """ParseNet 19 类逐像素标签(512 对齐脸上算)。失败 None。"""
    try:
        s = sess('parsenet')
        if s is None:
            return None
        x = (aligned_rgb.astype(np.float32) / 255.0 - _IMNET_MEAN) / _IMNET_STD
        x = np.ascontiguousarray(x.transpose(2, 0, 1))[None]
        y = np.asarray(s.run(None, {s.get_inputs()[0].name: x})[0], np.float32)
        if y.ndim == 4:
            y = y[0]
        if y.shape[0] != 19 and y.shape[-1] == 19:
            y = y.transpose(2, 0, 1)
        if y.ndim != 3 or y.shape[0] != 19:
            return None
        return y.argmax(0)
    except Exception:
        return None


def face_parse_mask(aligned_rgb):
    """ParseNet 人脸语义掩膜。返回 0..1 [512,512,1];失败 None。"""
    cls = face_parse_classes(aligned_rgb)
    if cls is None:
        return None
    try:
        m = np.isin(cls, _FACE_CLASSES).astype(np.float32)
        m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
        m = cv2.GaussianBlur(m, (0, 0), 5)
        return np.clip(m, 0, 1)[..., None]
    except Exception:
        return None


def u2net_person(img_rgb):
    """U²-NetP 人物剪影(320 输入,ImageNet 归一化)。返回 0..1 全图;失败 None。"""
    try:
        s = sess('u2net')
        if s is None:
            return None
        h, w = img_rgb.shape[:2]
        x = cv2.resize(np.clip(img_rgb, 0, 255).astype(np.uint8), (320, 320),
                       interpolation=cv2.INTER_AREA)
        x = (x.astype(np.float32) / 255.0 - _IMNET_MEAN) / _IMNET_STD
        x = np.ascontiguousarray(x.transpose(2, 0, 1))[None]
        y = np.asarray(s.run(None, {s.get_inputs()[0].name: x})[0], np.float32)
        if y.ndim == 4:
            y = y[0, 0]
        elif y.ndim == 3:
            y = y[0]
        m = y.astype(np.float32)
        if m.max() > 1.5:  # logits 兜底
            m = 1.0 / (1.0 + np.exp(-m))
        lo, hi = float(m.min()), float(m.max())
        if hi > lo:
            m = (m - lo) / (hi - lo)
        t, _ = cv2.threshold((np.clip(m, 0, 1) * 255).astype(np.uint8), 0, 255,
                             cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        mask = (m > t / 255.0).astype(np.float32)
        cov = float(mask.mean())
        if cov > 0.92 or cov < 0.02:  # 分割失效(全人/全空),交调用方兜底
            return None
        mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((7, 7), np.uint8))
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
        return cv2.resize(mask, (w, h), interpolation=cv2.INTER_LINEAR)
    except Exception:
        return None


def _lab8(img):
    """RGB(0..255)→Lab(float32),统一走 8-bit 转换(float 直转会被 OpenCV 0..1 假定钳坏)。"""
    return cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8),
                        cv2.COLOR_RGB2LAB).astype(np.float32)


def reinhard_lab(src_rgb, ref_rgb, alpha):
    """部分 Reinhard 色彩迁移(Lab 逐通道)。alpha=1 完全对齐 ref,0 不动。"""
    s = _lab8(src_rgb)
    r = _lab8(ref_rgb)
    out = np.empty_like(s)
    for c in range(3):
        ms, ss = float(s[..., c].mean()), float(s[..., c].std()) + 1e-6
        mr, sr = float(r[..., c].mean()), float(r[..., c].std()) + 1e-6
        out[..., c] = (s[..., c] - ms) * (1 + alpha * (sr / ss - 1)) + ms + alpha * (mr - ms)
    u8 = np.clip(out, 0, 255).astype(np.uint8)
    return cv2.cvtColor(u8, cv2.COLOR_LAB2RGB).astype(np.float32)


def match_rgb_mean_std(src, ref):
    """现行 APP 的 deblue:RGB 逐通道均值/方差拉回。"""
    out = src.astype(np.float32).copy()
    for c in range(3):
        ms, ss = float(src[..., c].mean()), float(src[..., c].std()) + 1e-6
        mr, sr = float(ref[..., c].mean()), float(ref[..., c].std()) + 1e-6
        out[..., c] = (src[..., c] - ms) * (sr / ss) + mr
    return np.clip(out, 0, 255)


def load_rgb(path, long_edge=640):
    bgr = cv2.imread(path, cv2.IMREAD_COLOR)
    if bgr is None:
        return None
    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float32)
    h, w = rgb.shape[:2]
    sc = long_edge / max(h, w)
    if sc < 1:
        rgb = cv2.resize(rgb, (max(1, int(w * sc)), max(1, int(h * sc))),
                         interpolation=cv2.INTER_AREA)
    H, W = pad32(*rgb.shape[:2])
    if (H, W) != rgb.shape[:2]:
        rgb = cv2.resize(rgb, (W, H), interpolation=cv2.INTER_AREA)
    return rgb


def save_rgb(name, img):
    path = safe_out(name)
    os.makedirs(OUT, exist_ok=True)
    cv2.imwrite(path, cv2.cvtColor(np.clip(img, 0, 255).astype(np.uint8), cv2.COLOR_RGB2BGR))


_FONT = cv2.FONT_HERSHEY_SIMPLEX


def labeled(img_rgb, text, height=440):
    im = cv2.resize(np.clip(img_rgb, 0, 255).astype(np.uint8),
                    (max(1, int(img_rgb.shape[1] * height / img_rgb.shape[0])), height),
                    interpolation=cv2.INTER_AREA)
    bar = np.full((30, im.shape[1], 3), 24, np.uint8)
    cv2.putText(bar, text, (8, 21), _FONT, 0.55, (240, 240, 240), 1, cv2.LINE_AA)
    return np.vstack([bar, im])


def sheet(out_name, panels):
    panels = [labeled(p, t) for p, t in panels]
    h = max(p.shape[0] for p in panels)
    panels = [np.vstack([p, np.full((h - p.shape[0], p.shape[1], 3), 24, np.uint8)])
              for p in panels]
    gap = np.full((h, 8, 3), 24, np.uint8)
    row = []
    for i, p in enumerate(panels):
        if i:
            row.append(gap)
        row.append(p)
    save_rgb(out_name, np.hstack(row))


def to_lab(rgb):
    """RGB(0..255)→Lab。统一走 8-bit 路径:OpenCV float 路径假定 0..1,0..255 会被钳坏。"""
    a = np.asarray(rgb)
    flat = a.ndim == 2 and a.shape[1] == 3
    if flat:
        a = a.reshape(1, -1, 3)
    a = np.clip(a, 0, 255).astype(np.uint8)
    lab = cv2.cvtColor(a, cv2.COLOR_RGB2LAB).astype(np.float32)
    return lab.reshape(-1, 3) if flat else lab


def nearest_bead(cells_rgb, pal_rgb):
    """逐格最近豆色(Lab 欧氏,同 APP 主路径)。返回 (索引, 最近距离)。"""
    cl = to_lab(cells_rgb).reshape(-1, 1, 3)
    pl = to_lab(np.asarray(pal_rgb, np.float32)).reshape(1, -1, 3)
    d = ((cl - pl) ** 2).sum(-1)
    idx = d.argmin(1)
    return idx, np.sqrt(d.min(1))
