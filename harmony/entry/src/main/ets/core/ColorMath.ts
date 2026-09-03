// 移植自 ColorMath.java

/**
 * 颜色计算工具:sRGB <-> CIELAB 转换、感知色距、亮度/对比度/饱和度调节。
 * 用 Lab 空间做最近色匹配,和人眼判断更一致。
 *
 * Java→TS 差异处理:安卓版 adjust/darken 内部是 float 运算再 (int) 截断,
 * 这里用 Math.fround 逐运算模拟 float32(float 存储处取一次 fround),
 * 保证截断结果与 Java 完全一致;其余均为 double 运算,TS number 直接对应。
 */

/** sRGB (0xRRGGBB) 转 CIELAB,返回 [L, a, b] */
export function rgbToLab(rgb: number): number[] {
  let r: number = ((rgb >> 16) & 0xFF) / 255.0;
  let g: number = ((rgb >> 8) & 0xFF) / 255.0;
  let b: number = (rgb & 0xFF) / 255.0;

  r = (r > 0.04045) ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
  g = (g > 0.04045) ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
  b = (b > 0.04045) ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

  const x: number = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047;
  const y: number = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / 1.00000;
  const z: number = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883;

  const fx: number = f(x);
  const fy: number = f(y);
  const fz: number = f(z);
  const out: number[] = [
    116.0 * fy - 16.0,
    500.0 * (fx - fy),
    200.0 * (fy - fz)
  ];
  return out;
}

function f(t: number): number {
  return (t > 0.008856) ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
}

/** CIELAB 转 sRGB(0xRRGGBB),与 rgbToLab 互为反函数 */
export function labToRgb(l: number, a: number, b: number): number {
  const fy: number = (l + 16.0) / 116.0;
  const fx: number = fy + a / 500.0;
  const fz: number = fy - b / 200.0;
  const x: number = finv(fx) * 0.95047;
  const y: number = finv(fy) * 1.00000;
  const z: number = finv(fz) * 1.08883;
  const rl: number = x * 3.2404542 + y * -1.5371385 + z * -0.4985314;
  const gl: number = x * -0.9692660 + y * 1.8760108 + z * 0.0415560;
  const bl: number = x * 0.0556434 + y * -0.2040259 + z * 1.0572252;
  const r: number = gamma(rl);
  const g: number = gamma(gl);
  const bb: number = gamma(bl);
  return ((r << 16) | (g << 8) | bb) >>> 0;
}

function finv(t: number): number {
  const t3: number = t * t * t;
  return t3 > 0.008856 ? t3 : (t - 16.0 / 116.0) / 7.787;
}

function gamma(v: number): number {
  if (v <= 0) {
    return 0;
  }
  if (v >= 1) {
    return 255;
  }
  const s: number = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
  const r: number = Math.round(s * 255.0);
  return r < 0 ? 0 : (r > 255 ? 255 : r);
}

/** Lab 欧氏距离平方 */
export function labDist2(a: number[], b: number[]): number {
  const dl: number = a[0] - b[0];
  const da: number = a[1] - b[1];
  const db: number = a[2] - b[2];
  return dl * dl + da * da + db * db;
}

/**
 * 画面调节。
 *
 * @param argb      原始像素(带 alpha)
 * @param bright    -100..100
 * @param contrast  -100..100
 * @param sat       -100..100
 */
export function adjustColor(argb: number, bright: number, contrast: number, sat: number): number {
  const a: number = (argb >>> 24) & 0xFF;
  if (a === 0) {
    return argb;
  }
  let r: number = (argb >> 16) & 0xFF;
  let g: number = (argb >> 8) & 0xFF;
  let b: number = argb & 0xFF;

  if (bright !== 0) {
    // Java int 运算:bright * 128 / 100(整除截断)
    const d: number = Math.trunc((bright * 128) / 100);
    r += d;
    g += d;
    b += d;
  }
  if (contrast !== 0) {
    // float k = (100f + contrast) / (100f - contrast)
    const k: number = Math.fround(Math.fround(100 + contrast) / Math.fround(100 - contrast));
    // (int)((r - 128) * k + 128):float 乘加后向零截断
    r = Math.trunc(Math.fround(Math.fround((r - 128) * k) + 128));
    g = Math.trunc(Math.fround(Math.fround((g - 128) * k) + 128));
    b = Math.trunc(Math.fround(Math.fround((b - 128) * k) + 128));
  }
  if (sat !== 0) {
    // float s = 1f + sat / 100f
    const s: number = Math.fround(1 + Math.fround(sat / Math.fround(100)));
    // float luma = 0.299f * r + 0.587f * g + 0.114f * b(逐运算 float32)
    const kr: number = Math.fround(0.299);
    const kg: number = Math.fround(0.587);
    const kb: number = Math.fround(0.114);
    const cr: number = Math.fround(kr * r);
    const cg: number = Math.fround(kg * g);
    const cb: number = Math.fround(kb * b);
    const luma: number = Math.fround(Math.fround(cr + cg) + cb);
    // (int)(luma + (r - luma) * s)
    r = Math.trunc(Math.fround(luma + Math.fround(Math.fround(Math.fround(r - luma) * s))));
    g = Math.trunc(Math.fround(luma + Math.fround(Math.fround(Math.fround(g - luma) * s))));
    b = Math.trunc(Math.fround(luma + Math.fround(Math.fround(Math.fround(b - luma) * s))));
  }
  return ((((a << 24) >>> 0) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b)) >>> 0);
}

function clamp(v: number): number {
  return v < 0 ? 0 : (v > 255 ? 255 : v);
}

/** 相对亮度 0..255 */
export function luminance(rgb: number): number {
  return Math.trunc(0.299 * ((rgb >> 16) & 0xFF)
    + 0.587 * ((rgb >> 8) & 0xFF)
    + 0.114 * (rgb & 0xFF));
}

/** 图上写字用黑色还是白色 */
export function textColorOn(rgb: number): number {
  return luminance(rgb) > 160 ? 0xFF1B1B1B : 0xFFFFFFFF;
}

/** 变暗,factor 0..1(安卓入参为 float,这里先转 float32 再参与运算) */
export function darken(rgb: number, factor: number): number {
  const kf: number = Math.fround(factor);
  const r: number = Math.trunc(Math.fround(((rgb >> 16) & 0xFF) * kf));
  const g: number = Math.trunc(Math.fround(((rgb >> 8) & 0xFF) * kf));
  const b: number = Math.trunc(Math.fround((rgb & 0xFF) * kf));
  return ((r << 16) | (g << 8) | b) >>> 0;
}
