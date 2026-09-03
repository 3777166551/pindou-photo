// 移植自 SubjectSegmenter.java

/**
 * 主体识别 v4:找出画面中的"主角",返回前景掩码。
 *
 * 思路(相比 FT 显著性更稳):背景一定连着画面边界,
 * 所以先用边界环聚类得到"背景参考色",再把每个像素的显著度定义为
 * 与最近背景参考色的 Lab 距离——离背景越远越像主体。
 * 这样浅色主体(白兔/白衣)只要与背景有色差就能整块保住,
 * 不像"与全图均值比距离"那样只留下高对比小色块。
 *
 * 流程:边界聚类 → 距离显著性(模糊去噪) → Otsu 阈值 × 松紧系数
 *   → 闭运算补描边缺口 → 连通域筛选 → 孔洞填充 → 膨胀安全边。
 * 配套的机制在 PatternEngine 里:主体占比异常时(满幅特写/无主体)
 * 直接不抠,不会硬伤图。
 *
 * 已验证的负优化(后台 32 图回归测试,勿再加回):
 *  - FT 显著性(与全图均值比距离):浅色大主体被挖成碎片;
 *  - 边界簇占比门槛提到 7%:主体压边的小簇被排除反而引发误抠;
 *  - 四角窗口背景规则:合法背景条带被排除,整体变保守且特写误抠;
 *  - 行/列边缘局部参考 + 测地线生长:列参考把底部渐变背景引回
 *    显著度(兔子 s87 回归),生长在背景簇含主体色时永远失效。
 */
import { rgbToLab } from './ColorMath';

/** 边界聚类:与已有簇中心 ΔE 小于该值并入同簇 */
const CLUSTER_JOIN_DE: number = 16.0;
/** 边界最多聚出的簇数 */
const MAX_BORDER_CLUSTERS: number = 8;
/** 占比 <4% 的边界簇不算背景色(主体压边时会被排除)。
 *  已知局限(纯颜色统计方法的天花板,参数调整已证明无效):
 *  ① 主体触碰边界且占比大(马蹄/大衣)时会污染背景参考;
 *  ② 满幅微距(脸占满画面)可能误判;
 *  ③ 黑白照片主体灰度夹在背景灰度之间时可能被吃。
 *  这些场景靠容差滑杆 + 画笔橡皮修正,质变需上 ML 抠图模型。 */
const MIN_BORDER_FRACTION: number = 0.04;

/** 上一次 findSubject 的分离度 = Otsu 类间方差 / 总方差(0~1)。
 *  实测对"满幅特写无背景"的区分度不足,暂未参与判定,仅供调试观察。 */
export let lastSeparation: number = 0;

/**
 * 找出主体,返回前景掩码(布尔数组,true = 主体),同安卓 findSubject。
 * @param thresholdFactor Otsu 阈值系数:1.0=标准;>1 主体圈得更小
 *                        (背景抠得更多);<1 保得更多
 */
export function findSubject(px: Uint32Array, w: number, h: number, thresholdFactor: number): boolean[] {
  const n: number = w * h;
  let fg: boolean[] = new Array<boolean>(n).fill(false);
  if (n < 16 || w < 5 || h < 5) {
    return fg;
  }

  // ---- ① Lab 缓存 + 模糊去噪 ----
  const L: number[] = new Array<number>(n).fill(0);
  const A: number[] = new Array<number>(n).fill(0);
  const B: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    const lab: number[] = rgbToLab(px[i]);
    L[i] = lab[0];
    A[i] = lab[1];
    B[i] = lab[2];
  }
  const Lb: number[] = box3(box3(L, w, h), w, h);
  const Ab: number[] = box3(box3(A, w, h), w, h);
  const Bb: number[] = box3(box3(B, w, h), w, h);

  // ---- ② 背景参考色:边界环贪心聚类 ----
  const centers: number[][] = [];
  let ring = 0;
  for (let i = 0; i < n; i++) {
    const border: boolean = (i < w) || (i >= n - w) || (i % w === 0) || (i % w === w - 1);
    if (!border) {
      continue;
    }
    ring++;
    const p: number[] = [L[i], A[i], B[i], 1];
    let joined = false;
    for (let ci = 0; ci < centers.length; ci++) {
      const c: number[] = centers[ci];
      if (dist(p, c) <= CLUSTER_JOIN_DE) {
        const k: number = c[3];
        c[0] = (c[0] * k + p[0]) / (k + 1);
        c[1] = (c[1] * k + p[1]) / (k + 1);
        c[2] = (c[2] * k + p[2]) / (k + 1);
        c[3] = k + 1;
        joined = true;
        break;
      }
    }
    if (!joined && centers.length < MAX_BORDER_CLUSTERS) {
      centers.push(p);
    }
  }
  const minCount: number = Math.max(4, ring * MIN_BORDER_FRACTION);
  for (let j = centers.length - 1; j >= 0; j--) {
    if (centers[j][3] < minCount) {
      centers.splice(j, 1);
    }
  }
  if (centers.length === 0) {
    return fg;   // 边缘全是花色,不敢定义背景
  }

  // ---- ③ 显著性 = 与最近背景色的距离,Otsu 二值化 ----
  const sal: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    let best: number = Number.MAX_VALUE;
    for (let ci = 0; ci < centers.length; ci++) {
      const c: number[] = centers[ci];
      const dl: number = Lb[i] - c[0];
      const da: number = Ab[i] - c[1];
      const db: number = Bb[i] - c[2];
      const d: number = dl * dl + da * da + db * db;
      if (d < best) {
        best = d;
      }
    }
    sal[i] = Math.fround(Math.sqrt(best));   // 安卓为 (float) Math.sqrt
  }
  const th: number = Math.fround(otsu(sal) * thresholdFactor);   // 安卓为 float 乘
  for (let i = 0; i < n; i++) {
    fg[i] = sal[i] >= th;
  }
  lastSeparation = separation(sal);

  // ---- ④ 闭运算:先胀后缩,补上描边的小缺口,让轮廓能圈住内部 ----
  fg = dilate1(fg, w, h);
  fg = erode1(fg, w, h);

  // ---- ⑤ 连通域筛选 ----
  keepMainComponents(fg, w, h);

  // ---- ⑥ 孔洞填充:描边圈内哪怕颜色和背景一样也归主体 ----
  fillHoles(fg, w, h);

  // ---- ⑦ 膨胀安全边 ----
  return dilate1(fg, w, h);
}

/**
 * 纯算法去背景掩码(移植规范 API 面):1 = 背景,0 = 主体/前景。
 * tolerance 即安卓 SubjectSegmenter.findSubject 的 thresholdFactor
 * (Otsu 阈值系数,由 PatternEngine从容差百分比换算后传入)。
 */
export function findBackgroundMask(px: Uint32Array, w: number, h: number, tolerance: number): Uint8Array {
  const fg: boolean[] = findSubject(px, w, h, tolerance);
  const out: Uint8Array = new Uint8Array(w * h);
  for (let i = 0; i < out.length; i++) {
    out[i] = fg[i] ? 0 : 1;
  }
  return out;
}

/** 3×3 盒滤波(边界钳制取样),两遍叠加近似高斯模糊 */
function box3(src: number[], w: number, h: number): number[] {
  const out: number[] = new Array<number>(src.length).fill(0);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let sum = 0;
      for (let dy = -1; dy <= 1; dy++) {
        const yy: number = Math.max(0, Math.min(h - 1, y + dy));
        for (let dx = -1; dx <= 1; dx++) {
          const xx: number = Math.max(0, Math.min(w - 1, x + dx));
          sum += src[yy * w + xx];
        }
      }
      out[y * w + x] = sum / 9;
    }
  }
  return out;
}

/** 类间方差 / 总方差:衡量显著度分布有没有清晰的"背景/主体"两层 */
function separation(v: number[]): number {
  let mean = 0;
  for (let i = 0; i < v.length; i++) {
    mean += v[i];
  }
  mean /= v.length;
  let varAll = 0;
  for (let i = 0; i < v.length; i++) {
    const d: number = v[i] - mean;
    varAll += d * d;
  }
  varAll /= v.length;
  if (varAll < 1e-9) {
    return 0;
  }
  // 用与 otsu 相同的 256 桶重算类间方差,避免二次扫描原数组
  let min: number = Number.MAX_VALUE;
  let max: number = -Number.MAX_VALUE;
  for (let i = 0; i < v.length; i++) {
    if (v[i] < min) {
      min = v[i];
    }
    if (v[i] > max) {
      max = v[i];
    }
  }
  const range: number = max - min;
  if (range <= 1e-6) {
    return 0;
  }
  const hist: number[] = new Array<number>(256).fill(0);
  for (let i = 0; i < v.length; i++) {
    let bkt: number = Math.trunc(((v[i] - min) / range) * 255);
    if (bkt < 0) {
      bkt = 0;
    }
    if (bkt > 255) {
      bkt = 255;
    }
    hist[bkt]++;
  }
  let sumAll = 0;
  for (let t = 0; t < 256; t++) {
    sumAll += t * hist[t];
  }
  let sumB = 0;
  let wB = 0;
  let bestVar = 0;
  for (let t = 0; t < 256; t++) {
    wB += hist[t];
    if (wB === 0) {
      continue;
    }
    const wF: number = v.length - wB;
    if (wF === 0) {
      break;
    }
    sumB += t * hist[t];
    const mB: number = sumB / wB;
    const mF: number = (sumAll - sumB) / wF;
    const varBetween: number = wB * wF * (mB - mF) * (mB - mF);
    if (varBetween > bestVar) {
      bestVar = varBetween;
    }
  }
  // bestVar 是桶坐标下未归一化的 wB*wF*(mB-mF)^2,
  // 除以 n^2 得到真正的类间方差,再换回原值域与总方差相除
  const varBetweenReal: number = (bestVar / (v.length * v.length)) * Math.pow(range / 255.0, 2);
  const ratio: number = varBetweenReal / varAll;
  return Math.min(1.0, ratio);
}

/** 经典 Otsu:256 桶直方图,类间方差最大的切点 */
function otsu(v: number[]): number {
  let min: number = Number.MAX_VALUE;
  let max: number = -Number.MAX_VALUE;
  for (let i = 0; i < v.length; i++) {
    if (v[i] < min) {
      min = v[i];
    }
    if (v[i] > max) {
      max = v[i];
    }
  }
  const range: number = max - min;
  if (range <= 1e-6) {
    return max;
  }

  const hist: number[] = new Array<number>(256).fill(0);
  for (let i = 0; i < v.length; i++) {
    let bkt: number = Math.trunc(((v[i] - min) / range) * 255);
    if (bkt < 0) {
      bkt = 0;
    }
    if (bkt > 255) {
      bkt = 255;
    }
    hist[bkt]++;
  }
  const total: number = v.length;
  let sumAll = 0;
  for (let t = 0; t < 256; t++) {
    sumAll += t * hist[t];
  }
  let sumB = 0;
  let wB = 0;
  let bestVar = -1;
  let bestT: number = 128;
  for (let t = 0; t < 256; t++) {
    wB += hist[t];
    if (wB === 0) {
      continue;
    }
    const wF: number = total - wB;
    if (wF === 0) {
      break;
    }
    sumB += t * hist[t];
    const mB: number = sumB / wB;
    const mF: number = (sumAll - sumB) / wF;
    const varBetween: number = wB * wF * (mB - mF) * (mB - mF);
    if (varBetween > bestVar) {
      bestVar = varBetween;
      bestT = t;
    }
  }
  return min + range * bestT / 255;
}

/** 连通域(4 邻接):保留"含画面中心的块"和面积 ≥ 最大块 22% 的块 */
function keepMainComponents(fg: boolean[], w: number, h: number): void {
  const n: number = w * h;
  const comp: number[] = new Array<number>(n).fill(0);
  const sizes: number[] = [];
  const queue: number[] = [];
  let head = 0;
  for (let seed = 0; seed < n; seed++) {
    if (!fg[seed] || comp[seed] !== 0) {
      continue;
    }
    const id: number = sizes.length + 1;
    let count = 0;
    comp[seed] = id;
    queue.push(seed);
    while (head < queue.length) {
      const i: number = queue[head];
      head++;
      count++;
      const x: number = i % w;
      const y: number = Math.floor(i / w);
      if (x > 0 && fg[i - 1] && comp[i - 1] === 0) {
        comp[i - 1] = id;
        queue.push(i - 1);
      }
      if (x < w - 1 && fg[i + 1] && comp[i + 1] === 0) {
        comp[i + 1] = id;
        queue.push(i + 1);
      }
      if (y > 0 && fg[i - w] && comp[i - w] === 0) {
        comp[i - w] = id;
        queue.push(i - w);
      }
      if (y < h - 1 && fg[i + w] && comp[i + w] === 0) {
        comp[i + w] = id;
        queue.push(i + w);
      }
    }
    sizes.push(count);
  }
  if (sizes.length === 0) {
    return;
  }

  let maxSize = 0;
  for (let i = 0; i < sizes.length; i++) {
    if (sizes[i] > maxSize) {
      maxSize = sizes[i];
    }
  }
  const minKeep: number = Math.max(3, Math.trunc((maxSize * 22) / 100));   // Java 整除
  const centerComp: number = comp[Math.floor(h / 2) * w + Math.floor(w / 2)];
  const keep: boolean[] = new Array<boolean>(sizes.length + 1).fill(false);
  for (let id = 1; id <= sizes.length; id++) {
    keep[id] = (id === centerComp) || (sizes[id - 1] >= minKeep);
  }
  for (let i = 0; i < n; i++) {
    fg[i] = fg[i] && keep[comp[i]];
  }
}

/** 孔洞填充:从边界泛洪背景,泛不到的封闭"洞"并入主体 */
function fillHoles(fg: boolean[], w: number, h: number): void {
  const n: number = w * h;
  const outside: boolean[] = new Array<boolean>(n).fill(false);
  const queue: number[] = [];
  let head = 0;
  for (let x = 0; x < w; x++) {
    trySeed(fg, outside, queue, x, 0, w);
    trySeed(fg, outside, queue, x, h - 1, w);
  }
  for (let y = 0; y < h; y++) {
    trySeed(fg, outside, queue, 0, y, w);
    trySeed(fg, outside, queue, w - 1, y, w);
  }
  while (head < queue.length) {
    const i: number = queue[head];
    head++;
    const x: number = i % w;
    const y: number = Math.floor(i / w);
    trySpread(fg, outside, queue, x - 1, y, w, h);
    trySpread(fg, outside, queue, x + 1, y, w, h);
    trySpread(fg, outside, queue, x, y - 1, w, h);
    trySpread(fg, outside, queue, x, y + 1, w, h);
  }
  for (let i = 0; i < n; i++) {
    if (!fg[i] && !outside[i]) {
      fg[i] = true;
    }
  }
}

function trySeed(fg: boolean[], outside: boolean[], queue: number[], x: number, y: number, w: number): void {
  const i: number = y * w + x;
  if (!fg[i] && !outside[i]) {
    outside[i] = true;
    queue.push(i);
  }
}

function trySpread(fg: boolean[], outside: boolean[], queue: number[],
                   x: number, y: number, w: number, h: number): void {
  if (x < 0 || x >= w || y < 0 || y >= h) {
    return;
  }
  const i: number = y * w + x;
  if (!fg[i] && !outside[i]) {
    outside[i] = true;
    queue.push(i);
  }
}

function dilate1(fg: boolean[], w: number, h: number): boolean[] {
  const out: boolean[] = new Array<boolean>(fg.length).fill(false);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i: number = y * w + x;
      if (fg[i]
        || (x > 0 && fg[i - 1]) || (x < w - 1 && fg[i + 1])
        || (y > 0 && fg[i - w]) || (y < h - 1 && fg[i + w])) {
        out[i] = true;
      }
    }
  }
  return out;
}

function erode1(fg: boolean[], w: number, h: number): boolean[] {
  const out: boolean[] = new Array<boolean>(fg.length).fill(false);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i: number = y * w + x;
      const solid: boolean = fg[i]
        && (x === 0 || fg[i - 1]) && (x === w - 1 || fg[i + 1])
        && (y === 0 || fg[i - w]) && (y === h - 1 || fg[i + w]);
      out[i] = solid;
    }
  }
  return out;
}

function dist(a: number[], b: number[]): number {
  const dl: number = a[0] - b[0];
  const da: number = a[1] - b[1];
  const db: number = a[2] - b[2];
  return Math.sqrt(dl * dl + da * da + db * db);
}
