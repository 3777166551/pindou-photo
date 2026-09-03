// 移植自 PatternEngine.java + BeadPattern.java + PatternPatch.java
// (去背景走 Segmenter.ts 纯算法版,按移植规范跳过 U2NetP/ML 一切代码)
import { adjustColor, labDist2, labToRgb, rgbToLab } from './ColorMath';
import { BeadColor } from './BeadColor';
import { findSubject } from './Segmenter';

/**
 * 核心:把一张照片变成拼豆图纸。
 *
 * 两种风格:
 *  - 写实(STYLE_REALISTIC):逐像素匹配固定拼豆色板,尽量贴合照片
 *  - 抽象(STYLE_ABSTRACT):乐高积木风。先把图像缩小到"砖块"粒度
 *    (每块 = brickSize × brickSize 颗豆,双线性缩放即完成邻域平均),
 *    一整块共用一颗豆的颜色;抽象程度由块大小控制。
 *    可再用 k 均值把全图限定到少数几种主色(海报感),并可吸附到真实豆色。
 *
 * 全程在 CIELAB 空间做最近色匹配,符合人眼感知。
 */

export const STYLE_REALISTIC: number = 0;
export const STYLE_ABSTRACT: number = 1;

const SYMBOLS: string =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

/** 最近一次 subjectMask 的决策:"V4"=颜色统计兜底 / "NONE"=守卫拒绝不抠
 *  (安卓还有 "ML"/"+R" 等取值;本移植按规范跳过 ML,仅保留颜色统计路径) */
export let lastMaskSource: string = '?';

/** 生成参数(默认值同安卓) */
export class Options {
  cols: number = 58;
  rows: number = 58;
  dither: boolean = false;
  brightness: number = 0;
  contrast: number = 0;
  saturation: number = 0;
  style: number = STYLE_REALISTIC;
  /** 抽象模式:砖块边长(几颗豆见方),2 轻 / 3 中 / 4 强 / 6 超强 */
  brickSize: number = 3;
  /** 抽象模式:是否限定主色调(k 均值提色);关闭则用整套拼豆色板 */
  abstractUsePalette: boolean = true;
  /** 抽象模式:提取的主色数量(4~16) */
  abstractColors: number = 8;
  /** 抽象模式:主色是否吸附到最近的拼豆豆色 */
  abstractSnapToBeads: boolean = true;
  /** 是否自动抠图去背景 */
  bgRemove: boolean = false;
  /** 去背景容差 0~100,映射到 Lab 距离阈值约 14~58 */
  bgTolerance: number = 55;
  /** 圆形拼板:内切圆以外的格子全部置空 */
  roundBoard: boolean = false;
  /** 降色数:限制最终使用的颜色种数,0 = 不限制(贪心合并最相近的色) */
  maxColors: number = 0;
}

/** 按用量从多到少排序后的已用颜色 */
export class UsedColor {
  index: number;
  color: BeadColor;
  symbol: string;
  count: number;

  constructor(index: number, color: BeadColor, symbol: string, count: number) {
    this.index = index;
    this.color = color;
    this.symbol = symbol;
    this.count = count;
  }
}

/** 生成结果:像素化 + 颜色匹配后的拼豆图纸数据 */
export class BeadPattern {
  cols: number;
  rows: number;
  /** 当前使用的色板(完整列表,含未用到的颜色) */
  palette: BeadColor[];
  /** 每格对应 palette 的下标,-1 表示空格(不放置;圆形板板外也是 -1) */
  cells: number[];
  /** 每种颜色(按 palette 下标)的使用数量 */
  counts: number[];
  /** 实际用到的颜色,按用量从多到少排序 */
  usedColors: UsedColor[];
  totalBeads: number;
  emptyCount: number;
  /** 圆形拼板:内切圆以外的格子全部视为板外,不存在 */
  round: boolean;

  constructor(cols: number, rows: number, palette: BeadColor[], cells: number[],
              counts: number[], usedColors: UsedColor[], totalBeads: number,
              emptyCount: number, round: boolean = false) {
    this.cols = cols;
    this.rows = rows;
    this.palette = palette;
    this.cells = cells;
    this.counts = counts;
    this.usedColors = usedColors;
    this.totalBeads = totalBeads;
    this.emptyCount = emptyCount;
    this.round = round;
  }

  cellAt(x: number, y: number): number {
    return this.cells[y * this.cols + x];
  }

  /** 该格是否在有效拼豆区域外(圆形板的内切圆以外) */
  outsideShape(x: number, y: number): boolean {
    return this.round && BeadPattern.isOutsideRound(this.cols, this.rows, x, y);
  }

  /** 圆形板判定:格中心到画布中心距离 > 内切圆半径即板外 */
  static isOutsideRound(cols: number, rows: number, x: number, y: number): boolean {
    const r: number = Math.min(cols, rows) / 2.0;
    const dx: number = x + 0.5 - cols / 2.0;
    const dy: number = y + 0.5 - rows / 2.0;
    return dx * dx + dy * dy > r * r;
  }

  boardsNeeded(): number {
    return Math.ceil(this.cols / 29.0) * Math.ceil(this.rows / 29.0);
  }
}

/** 按用量从多到少排序(同安卓 BeadPattern.sortByCountDesc) */
export function sortByCountDesc(list: UsedColor[]): void {
  list.sort((a: UsedColor, b: UsedColor): number => b.count - a.count);
}

/**
 * 生成图纸。sourcePx 为已解码的不透明 ARGB 像素(安卓版入参是 Bitmap;
 * 居中裁剪/超长边压缩在这里用纯像素完成,1024 限长缩放用引擎自带的双线性)。
 */
export function generate(sourcePx: Uint32Array, w: number, h: number,
                         beadPalette: BeadColor[], o: Options): BeadPattern {
  const cols: number = Math.max(4, Math.min(200, o.cols));
  const rows: number = Math.max(4, Math.min(200, o.rows));

  const abs: boolean = o.style === STYLE_ABSTRACT;
  const b: number = abs ? Math.max(1, Math.min(8, o.brickSize)) : 1;
  const gw: number = Math.floor((cols + b - 1) / b);   // 工作网格宽(块数)
  const gh: number = Math.floor((rows + b - 1) / b);

  // 1. 居中裁剪到画幅比例
  const target: number = cols / rows;
  const sw: number = w;
  const sh: number = h;
  const cur: number = sw / sh;
  let cw: number;
  let ch: number;
  if (cur > target) {
    ch = sh;
    cw = Math.max(1, Math.round(sh * target));
  } else {
    cw = sw;
    ch = Math.max(1, Math.round(sw / target));
  }
  // Bitmap.createBitmap 居中裁剪的纯像素等价
  let cropped: Uint32Array;
  if (cw === sw && ch === sh) {
    cropped = sourcePx.slice();
  } else {
    cropped = new Uint32Array(cw * ch);
    const cx0: number = Math.floor((sw - cw) / 2);
    const cy0: number = Math.floor((sh - ch) / 2);
    for (let y = 0; y < ch; y++) {
      cropped.set(sourcePx.subarray((cy0 + y) * sw + cx0, (cy0 + y) * sw + cx0 + cw), y * cw);
    }
  }

  // 2. 取像素:过大的源图先压到长边 1024(两步缩放保证质量,避免超大数组;
  //    安卓用 Bitmap.createScaledBitmap(filter=true),此处用引擎自带双线性)
  let pw: number;
  let ph: number;
  let srcPx: Uint32Array;
  {
    const longSide: number = Math.max(cw, ch);
    if (longSide > 1024) {
      const s: number = 1024 / longSide;
      const tw: number = Math.max(1, Math.round(cw * s));
      const th: number = Math.max(1, Math.round(ch * s));
      srcPx = resampleBilinear(cropped, cw, ch, tw, th);
      pw = tw;
      ph = th;
    } else {
      pw = cw;
      ph = ch;
      srcPx = cropped;
    }
  }

  // 2.5 工作网格像素:盒式面积平均降采样(块内所有源像素都参与,
  //     不像双线性大比例缩小只采零星几个点);开去背景时先在高分辨率上
  //     求掩码,再按覆盖率逐格加权平均--背景色不混进主体边缘
  let px: Uint32Array;
  if (o.bgRemove) {
    px = gridWithBackground(srcPx, pw, ph, gw, gh, o.bgTolerance);
  } else {
    px = boxResample(srcPx, pw, ph, gw, gh);
  }

  // 3. 画面调节
  if (o.brightness !== 0 || o.contrast !== 0 || o.saturation !== 0) {
    for (let i = 0; i < px.length; i++) {
      px[i] = adjustColor(px[i], o.brightness, o.contrast, o.saturation);
    }
  }

  // 4. 确定生效色板
  let palette: BeadColor[];
  if (!abs) {
    palette = beadPalette;
  } else if (o.abstractUsePalette) {
    palette = buildAbstractPalette(px, beadPalette, o.abstractColors, o.abstractSnapToBeads);
  } else {
    palette = beadPalette;
  }
  const n: number = palette.length;

  // 5. 工作网格逐块匹配最近豆色(可选 FS 抖动在块层面扩散)
  const workCells: number[] = new Array<number>(gw * gh).fill(0);
  const counts: number[] = new Array<number>(Math.max(1, n)).fill(0);
  if (n > 0) {
    const labs: number[][] = new Array<number[]>(n);
    for (let i = 0; i < n; i++) {
      labs[i] = rgbToLab(palette[i].rgb);
    }

    if (o.dither) {
      let curRow: number[] = new Array<number>(gw * 3).fill(0);
      let nextRow: number[] = new Array<number>(gw * 3).fill(0);
      for (let y = 0; y < gh; y++) {
        for (let x = 0; x < gw; x++) {
          const p: number = px[y * gw + x];
          if (((p >>> 24) & 0xFF) < 128) {
            workCells[y * gw + x] = -1;
            continue;
          }
          const lab: number[] = rgbToLab(p);
          const l: number = clampL(lab[0] + curRow[x * 3]);
          const a: number = lab[1] + curRow[x * 3 + 1];
          const bl: number = lab[2] + curRow[x * 3 + 2];
          const idx: number = nearest(labs, l, a, bl);
          workCells[y * gw + x] = idx;

          const el: number = l - labs[idx][0];
          const ea: number = a - labs[idx][1];
          const eb: number = bl - labs[idx][2];
          if (x + 1 < gw) {
            addErr(curRow, x + 1, el, ea, eb, 7.0 / 16);
          }
          if (y + 1 < gh) {
            if (x > 0) {
              addErr(nextRow, x - 1, el, ea, eb, 3.0 / 16);
            }
            addErr(nextRow, x, el, ea, eb, 5.0 / 16);
            if (x + 1 < gw) {
              addErr(nextRow, x + 1, el, ea, eb, 1.0 / 16);
            }
          }
        }
        const t: number[] = curRow;
        curRow = nextRow;
        nextRow = t;
        nextRow.fill(0);
      }
    } else {
      for (let i = 0; i < px.length; i++) {
        const p: number = px[i];
        if (((p >>> 24) & 0xFF) < 128) {
          workCells[i] = -1;
        } else {
          const lab: number[] = rgbToLab(p);
          workCells[i] = nearest(labs, lab[0], lab[1], lab[2]);
        }
      }
    }
  } else {
    workCells.fill(-1);
  }

  // 6. 展开成最终画幅(一块填满 b×b 颗豆),并统计用量
  const cells: number[] = new Array<number>(cols * rows).fill(-1);
  expandBricks(workCells, gw, gh, cells, cols, rows, b);

  // 6.5 圆形板:内切圆以外的格子视为板外
  if (o.roundBoard) {
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        if (BeadPattern.isOutsideRound(cols, rows, x, y)) {
          cells[y * cols + x] = -1;
        }
      }
    }
  }

  let empty = 0;
  counts.fill(0);
  for (let i = 0; i < cells.length; i++) {
    const c: number = cells[i];
    if (c < 0) {
      empty++;
    } else {
      counts[c]++;
    }
  }

  // 6.8 降色数:合并最相近的颜色,直到不超过上限(写实模式的"限 N 色")
  if (o.maxColors > 0) {
    mergeToMaxColors(cells, counts, n, palette, o.maxColors);
  }

  // 7. 统计
  let total = 0;
  const used: UsedColor[] = [];
  for (let i = 0; i < n; i++) {
    if (counts[i] > 0) {
      used.push(new UsedColor(i, palette[i], symbolFor(i), counts[i]));
      total += counts[i];
    }
  }
  sortByCountDesc(used);

  return new BeadPattern(cols, rows, palette, cells, counts, used, total, empty, o.roundBoard);
}

/**
 * 把粗网格按 b×b 一块展开成细网格;最后一行/列不满一块时钳制到边界。
 */
export function expandBricks(coarse: number[], gw: number, gh: number,
                             fine: number[], fw: number, fh: number, b: number): void {
  for (let y = 0; y < fh; y++) {
    const cy: number = Math.min(Math.floor(y / b), gh - 1);
    const rowBase: number = y * fw;
    const srcBase: number = cy * gw;
    for (let x = 0; x < fw; x++) {
      fine[rowBase + x] = coarse[srcBase + Math.min(Math.floor(x / b), gw - 1)];
    }
  }
}

/**
 * 降色数:反复把 Lab ΔE 最近的两色合并(用量小的并入用量大的),
 * 重定向所有格子,直到使用的颜色种数不超过 max。合并保留色的 Lab
 * 按用量加权平均,后续合并以混合后的代表色计算距离。
 */
function mergeToMaxColors(cells: number[], counts: number[], n: number,
                          palette: BeadColor[], max: number): void {
  let used = 0;
  for (let i = 0; i < n; i++) {
    if (counts[i] > 0) {
      used++;
    }
  }
  if (used <= max) {
    return;
  }

  const lab: (number[] | null)[] = new Array<number[] | null>(n).fill(null);
  const weight: number[] = new Array<number>(n).fill(0);      // 合并后的加权总量(用于平均 Lab)
  const accLab: (number[] | null)[] = new Array<number[] | null>(n).fill(null);
  const map: number[] = new Array<number>(n).fill(0);         // k -> 合并后代表色
  for (let i = 0; i < n; i++) {
    map[i] = i;
    if (counts[i] > 0) {
      const lb: number[] = rgbToLab(0xFF000000 | palette[i].rgb);
      lab[i] = lb;
      accLab[i] = [lb[0] * counts[i], lb[1] * counts[i], lb[2] * counts[i]];
      weight[i] = counts[i];
    }
  }

  while (used > max) {
    let bi: number = -1;
    let bj: number = -1;
    let best: number = Number.MAX_VALUE;
    for (let i = 0; i < n; i++) {
      if (counts[i] <= 0) {
        continue;
      }
      const li: number[] | null = lab[i];
      if (li === null) {
        continue;
      }
      for (let j = i + 1; j < n; j++) {
        if (counts[j] <= 0) {
          continue;
        }
        const lj: number[] | null = lab[j];
        if (lj === null) {
          continue;
        }
        const de: number = labDist2(li, lj);
        if (de < best) {
          best = de;
          bi = i;
          bj = j;
        }
      }
    }
    const from: number = counts[bi] <= counts[bj] ? bi : bj;
    const to: number = from === bi ? bj : bi;
    for (let k = 0; k < n; k++) {
      if (map[k] === from) {
        map[k] = to;
      }
    }
    counts[to] += counts[from];
    counts[from] = 0;
    // 代表色 = 加权平均 Lab(注意:同安卓,先除旧 weight 再累加 weight)
    const aTo: number[] | null = accLab[to];
    const aFrom: number[] | null = accLab[from];
    const lTo: number[] | null = lab[to];
    if (aTo !== null && aFrom !== null && lTo !== null) {
      for (let c = 0; c < 3; c++) {
        aTo[c] += aFrom[c];
        lTo[c] = aTo[c] / weight[to];
      }
    }
    weight[to] += weight[from];
    accLab[from] = null;
    used--;
  }

  for (let i = 0; i < cells.length; i++) {
    if (cells[i] >= 0) {
      cells[i] = map[cells[i]];
    }
  }
}

// ---- 去背景 v2 的调参常量 ----
/** 边界聚类:与已有簇中心 ΔE 小于该值并入同簇 */
const CLUSTER_JOIN_DE: number = 16.0;
/** 边界最多聚出的簇数(防止把五彩边缘碎成几十簇) */
const MAX_BORDER_CLUSTERS: number = 8;
/** 局部连续判据相对全局容差的比例(更严,只用来顺着渐变走) */
const LOCAL_RATIO: number = 0.55;
/** 连续"纯局部"跳变的最长链(云朵/渐变这类软边缘大块区域靠它吃掉) */
const LOCAL_HOP_LIMIT: number = 30;

/** 去背景工作图目标长边:网格 × S,尽量贴近模型的 320 分辨率 */
const MASK_WORK_SIDE: number = 320;
/** 掩码可信区间:前景占比 8%~88%,超出视为满幅特写/无主体,放弃抠图 */
const FG_MIN_RATIO: number = 0.08;
const FG_MAX_RATIO: number = 0.88;
/** 细结构救援:救援区域与所有背景簇中心的 Lab 距离必须超过该值
 *  (确保救回的是"前景色"部件,排除渐变/阴影的泛洪渗漏) */
const RESCUE_MIN_DE: number = 16;
/** 细结构救援:救援面积超过画幅该比例判定泛洪失控,放弃 */
const RESCUE_MAX_RATIO: number = 0.30;
/** 细结构救援:小于画幅该比例的孤立碎点视为噪声丢弃 */
const RESCUE_MIN_BLOB: number = 0.0005;

/**
 * 一键去背景 v6:先在高分辨率工作图上求前景掩码,再按"覆盖率"决定
 * 每个拼豆格子去留,格子颜色只平均掩码内的像素。
 * 安卓版在此之上有 U2NetP ML 概率图优先路径与交叉校验,本移植按规范
 * 整体跳过 ML,固定使用颜色统计 v4(Segmenter.ts)——与安卓在
 * mlProvider 为空时的行为一致。掩码不可信时退回普通盒式采样。
 *
 * @param srcPx 已按画幅比例裁剪好的源图像素(不透明 ARGB)
 */
export function gridWithBackground(srcPx: Uint32Array, sw: number, sh: number,
                                   gw: number, gh: number, tolerancePct: number): Uint32Array {
  let s: number = Math.max(1, Math.min(12, Math.round(MASK_WORK_SIDE / Math.max(gw, gh))));
  while (s > 1 && (gw * s > sw || gh * s > sh)) {
    s--;
  }
  const mw: number = gw * s;
  const mh: number = gh * s;

  const work: Uint32Array = boxResample(srcPx, sw, sh, mw, mh);
  const fg: boolean[] | null = subjectMask(work, mw, mh, tolerancePct);
  if (fg === null) {
    return boxResample(srcPx, sw, sh, gw, gh);
  }
  return maskCoverageDownscale(work, mw, mh, fg, gw, gh, s);
}

/**
 * 颜色统计 v4 掩码 + 占比守卫(安卓 subjectMask 去掉 ML 分支后的 1:1 版)。
 * 前景占比异常(满幅特写/无主体)返回 null。
 */
function subjectMask(work: Uint32Array, mw: number, mh: number, tolerancePct: number): boolean[] | null {
  // 颜色统计 v4(安卓中它常驻计算,既是兜底也做 ML 交叉校验)
  const factor: number =
    Math.fround(Math.fround(0.70) + Math.fround(clampPct(tolerancePct) * Math.fround(0.0065)));
  const v4: boolean[] = findSubject(work, mw, mh, factor);
  const v4Ratio: number = trueRatio(v4);
  const v4ok: boolean = v4Ratio >= FG_MIN_RATIO && v4Ratio <= FG_MAX_RATIO;

  let base: boolean[];
  if (v4ok) {
    base = v4;
    lastMaskSource = 'V4';
  } else {
    lastMaskSource = 'NONE';
    return null;
  }
  const merged: boolean[] = rescueDetachedForeground(work, mw, mh, base, tolerancePct);
  if (merged === base) {
    return base;
  }
  const mergedRatio: number = trueRatio(merged);
  if (mergedRatio < FG_MIN_RATIO || mergedRatio > FG_MAX_RATIO) {
    return base;
  }
  lastMaskSource += '+R';
  return merged;
}

function trueRatio(mask: boolean[]): number {
  let c = 0;
  for (let i = 0; i < mask.length; i++) {
    if (mask[i]) {
      c++;
    }
  }
  return c / mask.length;
}

/**
 * 细结构救援:对"与主体分离的细小部件"(太阳光芒、漂浮装饰、孤立的
 * 耳朵/尾巴尖)用边界泛洪"与背景同色的连通域"把它们留下。
 * 做法:边界背景聚类 → 双门槛泛洪(全局容差 + 局部连续 + Sobel
 * 边缘门)得背景连通域 B;候选救援 = 泛洪前景之外 ∧ 掩码之外;仅当候选
 * 与所有背景簇中心的 Lab 距离都超过 RESCUE_MIN_DE(真是"前景色",排除
 * 渐变/阴影渗漏)且总面积不超过画幅 RESCUE_MAX_RATIO 时并入。
 * 不适用时原样返回 fg。
 */
function rescueDetachedForeground(work: Uint32Array, mw: number, mh: number,
                                  fg: boolean[], tolerancePct: number): boolean[] {
  const centers: number[][] = borderClusterCenters(work, mw, mh);
  if (centers.length === 0) {
    return fg;
  }
  const bgFlood: boolean[] = floodBackgroundRegion(work, mw, mh, centers, tolerancePct);

  const n: number = mw * mh;
  const add: boolean[] = new Array<boolean>(n).fill(false);
  let addCount = 0;
  for (let i = 0; i < n; i++) {
    if (fg[i] || bgFlood[i]) {
      continue;
    }
    const lab: number[] = rgbToLab(work[i]);
    let best: number = Number.MAX_VALUE;
    for (let ci = 0; ci < centers.length; ci++) {
      const c: number[] = centers[ci];
      const dl: number = lab[0] - c[0];
      const da: number = lab[1] - c[1];
      const db: number = lab[2] - c[2];
      const d: number = Math.sqrt(dl * dl + da * da + db * db);
      if (d < best) {
        best = d;
      }
    }
    if (best > RESCUE_MIN_DE) {
      add[i] = true;
      addCount++;
    }
  }
  if (addCount === 0 || addCount > n * RESCUE_MAX_RATIO) {
    return fg;
  }

  const minSize: number = Math.trunc(Math.max(4, n * RESCUE_MIN_BLOB));
  let rescued: boolean[] = dropTinyComponents(add, mw, mh, minSize);
  rescued = dilate1(rescued, mw, mh);

  const out: boolean[] = fg.slice();
  let changed = false;
  for (let i = 0; i < n; i++) {
    if (rescued[i] && !out[i]) {
      out[i] = true;
      changed = true;
    }
  }
  return changed ? out : fg;
}

/** 边界环背景聚类(与 Segmenter 同参数):返回 {L,a,b,count} 列表 */
function borderClusterCenters(px: Uint32Array, w: number, h: number): number[][] {
  const n: number = w * h;
  const centers: number[][] = [];
  let ring = 0;
  for (let i = 0; i < n; i++) {
    const border: boolean = (i < w) || (i >= n - w) || (i % w === 0) || (i % w === w - 1);
    if (!border) {
      continue;
    }
    ring++;
    const lab: number[] = rgbToLab(px[i]);
    const p: number[] = [lab[0], lab[1], lab[2], 1];
    let joined = false;
    for (let ci = 0; ci < centers.length; ci++) {
      const c: number[] = centers[ci];
      const dl: number = p[0] - c[0];
      const da: number = p[1] - c[1];
      const db: number = p[2] - c[2];
      if (Math.sqrt(dl * dl + da * da + db * db) <= CLUSTER_JOIN_DE) {
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
  const minCount: number = Math.max(4, ring * 0.04);
  for (let j = centers.length - 1; j >= 0; j--) {
    if (centers[j][3] < minCount) {
      centers.splice(j, 1);
    }
  }
  return centers;
}

/**
 * 边界种子泛洪(v2 算法,仅作细结构救援用):全局容差门 + 局部连续门
 * (限跳)+ Sobel 边缘门,返回"与边界背景同色的连通域"。
 */
function floodBackgroundRegion(px: Uint32Array, w: number, h: number,
                               centers: number[][], tolerancePct: number): boolean[] {
  const n: number = w * h;
  // float tol = 14f + clampPct(tolerancePct) * 44f / 100f
  const tol: number = Math.fround(14 + Math.fround(Math.fround(clampPct(tolerancePct) * Math.fround(44)) / 100));
  const localTol: number = Math.fround(tol * Math.fround(LOCAL_RATIO));

  const L: number[] = new Array<number>(n).fill(0);
  const A: number[] = new Array<number>(n).fill(0);
  const B: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    const lab: number[] = rgbToLab(px[i]);
    L[i] = lab[0];
    A[i] = lab[1];
    B[i] = lab[2];
  }
  const lum: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    const p: number = px[i];
    lum[i] = 0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF);
  }
  const mag: number[] = new Array<number>(n).fill(0);
  let magSum = 0;
  for (let y = 1; y < h - 1; y++) {
    for (let x = 1; x < w - 1; x++) {
      const i: number = y * w + x;
      const tl: number = lum[i - w - 1];
      const tc: number = lum[i - w];
      const tr: number = lum[i - w + 1];
      const ml: number = lum[i - 1];
      const mr: number = lum[i + 1];
      const bl: number = lum[i + w - 1];
      const bc: number = lum[i + w];
      const br: number = lum[i + w + 1];
      const gx: number = -tl - 2 * ml - bl + tr + 2 * mr + br;
      const gy: number = -tl - 2 * tc - tr + bl + 2 * bc + br;
      const mg: number = Math.sqrt(gx * gx + gy * gy);
      mag[i] = mg;
      magSum += mg;
    }
  }
  // 阈值 = max(22, 平均梯度的 2.2 倍):平整插画/照片取下限,
  // 纹理杂乱的照片自动放宽,只挡真正的轮廓线
  const edgeGate: number = Math.fround(Math.max(22.0, (magSum / n) * 2.2));

  const bg: boolean[] = new Array<boolean>(n).fill(false);
  const hops: number[] = new Array<number>(n).fill(0);
  const queue: number[] = [];
  let head = 0;
  for (let i = 0; i < n; i++) {
    const border: boolean = (i < w) || (i >= n - w) || (i % w === 0) || (i % w === w - 1);
    if (!border) {
      continue;
    }
    for (let ci = 0; ci < centers.length; ci++) {
      const c: number[] = centers[ci];
      const dl: number = L[i] - c[0];
      const da: number = A[i] - c[1];
      const db: number = B[i] - c[2];
      if (dl * dl + da * da + db * db <= tol * tol) {
        bg[i] = true;
        hops[i] = 0;
        queue.push(i);
        break;
      }
    }
  }
  while (head < queue.length) {
    const j: number = queue[head];
    head++;
    const x: number = j % w;
    const y: number = Math.floor(j / w);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x - 1, y, w, h, j, tol, localTol);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x + 1, y, w, h, j, tol, localTol);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x, y - 1, w, h, j, tol, localTol);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x, y + 1, w, h, j, tol, localTol);
  }
  return bg;
}

/** 丢弃小于 minSize 的 4 连通碎点(救援区域去噪) */
function dropTinyComponents(m: boolean[], w: number, h: number, minSize: number): boolean[] {
  const n: number = w * h;
  const out: boolean[] = new Array<boolean>(n).fill(false);
  const seen: boolean[] = new Array<boolean>(n).fill(false);
  const queue: number[] = [];
  let head = 0;
  const blob: number[] = [];
  for (let seed = 0; seed < n; seed++) {
    if (!m[seed] || seen[seed]) {
      continue;
    }
    seen[seed] = true;
    queue.push(seed);
    blob.length = 0;
    while (head < queue.length) {
      const i: number = queue[head];
      head++;
      blob.push(i);
      const x: number = i % w;
      const y: number = Math.floor(i / w);
      if (x > 0 && m[i - 1] && !seen[i - 1]) {
        seen[i - 1] = true;
        queue.push(i - 1);
      }
      if (x < w - 1 && m[i + 1] && !seen[i + 1]) {
        seen[i + 1] = true;
        queue.push(i + 1);
      }
      if (y > 0 && m[i - w] && !seen[i - w]) {
        seen[i - w] = true;
        queue.push(i - w);
      }
      if (y < h - 1 && m[i + w] && !seen[i + w]) {
        seen[i + w] = true;
        queue.push(i + w);
      }
    }
    if (blob.length >= minSize) {
      for (let bi = 0; bi < blob.length; bi++) {
        out[blob[bi]] = true;
      }
    }
  }
  return out;
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

/** 逐格统计掩码覆盖率:≥50% 保留;保留格颜色 = 格内掩码内像素的平均 */
function maskCoverageDownscale(work: Uint32Array, mw: number, mh: number,
                               fg: boolean[], gw: number, gh: number, s: number): Uint32Array {
  const out: Uint32Array = new Uint32Array(gw * gh);
  const bg: boolean[] = new Array<boolean>(gw * gh).fill(false);
  const cell: number = s * s;
  for (let gy = 0; gy < gh; gy++) {
    for (let gx = 0; gx < gw; gx++) {
      let inside = 0;
      let sr = 0;
      let sg = 0;
      let sb = 0;
      for (let y = 0; y < s; y++) {
        const row: number = (gy * s + y) * mw + gx * s;
        for (let x = 0; x < s; x++) {
          const i: number = row + x;
          if (fg[i]) {
            inside++;
            const c: number = work[i];
            sr += (c >> 16) & 0xFF;
            sg += (c >> 8) & 0xFF;
            sb += c & 0xFF;
          }
        }
      }
      const k: number = gy * gw + gx;
      if (inside * 2 >= cell) {
        out[k] = (0xFF000000 | (Math.floor(sr / inside) << 16)
          | (Math.floor(sg / inside) << 8) | Math.floor(sb / inside)) >>> 0;
      } else {
        out[k] = 0;
        bg[k] = true;
      }
    }
  }
  fillPinholes(out, bg, gw, gh);
  return out;
}

function clampPct(v: number): number {
  return v < 0 ? 0 : (v > 100 ? 100 : v);
}

/**
 * 盒式面积平均重采样(RGB,忽略 alpha)。
 * 每个目标像素取源图对应矩形内全部像素的平均--大比例缩小时
 * 所有源像素都参与,不像双线性只零星采样,边缘颜色不会被漏掉。
 * 目标比源大时退化为双线性。
 */
export function boxResample(src: Uint32Array, sw: number, sh: number, dw: number, dh: number): Uint32Array {
  if (dw === sw && dh === sh) {
    return src.slice();
  }
  if (dw > sw || dh > sh) {
    return resampleBilinear(src, sw, sh, dw, dh);
  }
  const out: Uint32Array = new Uint32Array(dw * dh);
  for (let y = 0; y < dh; y++) {
    const sy0: number = Math.floor(y * sh / dh);
    let sy1: number = Math.max(sy0 + 1, ceilDiv((y + 1) * sh, dh));
    sy1 = Math.min(sy1, sh);
    for (let x = 0; x < dw; x++) {
      const sx0: number = Math.floor(x * sw / dw);
      let sx1: number = Math.max(sx0 + 1, ceilDiv((x + 1) * sw, dw));
      sx1 = Math.min(sx1, sw);
      let r = 0;
      let g = 0;
      let b = 0;
      let cnt = 0;
      for (let yy = sy0; yy < sy1; yy++) {
        const row: number = yy * sw;
        for (let xx = sx0; xx < sx1; xx++) {
          const c: number = src[row + xx];
          r += (c >> 16) & 0xFF;
          g += (c >> 8) & 0xFF;
          b += c & 0xFF;
          cnt++;
        }
      }
      out[y * dw + x] = (0xFF000000
        | (Math.round(r / cnt) << 16)
        | (Math.round(g / cnt) << 8)
        | Math.round(b / cnt)) >>> 0;
    }
  }
  return out;
}

/** 双线性重采样(RGB,忽略 alpha) */
export function resampleBilinear(src: Uint32Array, sw: number, sh: number, dw: number, dh: number): Uint32Array {
  if (dw === sw && dh === sh) {
    return src.slice();
  }
  const out: Uint32Array = new Uint32Array(dw * dh);
  for (let y = 0; y < dh; y++) {
    const fy: number = (dh === 1) ? 0 : (y + 0.5) * sh / dh - 0.5;
    let y0: number = Math.floor(fy);
    let ty: number = fy - y0;
    if (y0 < 0) {
      y0 = 0;
      ty = 0;
    }
    if (y0 >= sh - 1) {
      y0 = sh - 1;
      ty = 0;
    }
    const y1: number = Math.min(sh - 1, y0 + 1);
    for (let x = 0; x < dw; x++) {
      const fx: number = (dw === 1) ? 0 : (x + 0.5) * sw / dw - 0.5;
      let x0: number = Math.floor(fx);
      let tx: number = fx - x0;
      if (x0 < 0) {
        x0 = 0;
        tx = 0;
      }
      if (x0 >= sw - 1) {
        x0 = sw - 1;
        tx = 0;
      }
      const x1: number = Math.min(sw - 1, x0 + 1);
      const c00: number = src[y0 * sw + x0];
      const c10: number = src[y0 * sw + x1];
      const c01: number = src[y1 * sw + x0];
      const c11: number = src[y1 * sw + x1];
      const r: number = bilinear1((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF,
        (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, tx, ty);
      const g: number = bilinear1((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF,
        (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, tx, ty);
      const b: number = bilinear1(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, tx, ty);
      out[y * dw + x] = (0xFF000000 | (r << 16) | (g << 8) | b) >>> 0;
    }
  }
  return out;
}

function bilinear1(v00: number, v10: number, v01: number, v11: number, tx: number, ty: number): number {
  const top: number = v00 + (v10 - v00) * tx;
  const bot: number = v01 + (v11 - v01) * tx;
  const v: number = Math.round(top + (bot - top) * ty);
  return v < 0 ? 0 : (v > 255 ? 255 : v);
}

function ceilDiv(a: number, b: number): number {
  return Math.floor((a + b - 1) / b);
}

/**
 * 旧版算法(原 removeBackground):多色边界聚类 + 双门槛区域生长 + 边缘门槛。
 * 现仅作为 v3 主体识别失败时的兜底(当前生成流程未调用,按 1:1 移植保留)。
 */
function removeBackgroundByColorFlood(px: Uint32Array, w: number, h: number, tolerancePct: number): void {
  const n: number = w * h;
  if (n === 0 || w < 3 || h < 3) {
    return;
  }
  const tol: number = 14 + Math.max(0, Math.min(100, tolerancePct)) * 44 / 100;

  const L: number[] = new Array<number>(n).fill(0);
  const A: number[] = new Array<number>(n).fill(0);
  const B: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    const lab: number[] = rgbToLab(px[i]);
    L[i] = lab[0];
    A[i] = lab[1];
    B[i] = lab[2];
  }

  // ---- ① 边界环聚类(贪心,中心均值在线更新)。centers: {L,a,b,count} ----
  const centers: number[][] = [];
  let ringCount = 0;
  for (let i = 0; i < n; i++) {
    const border: boolean = (i < w) || (i >= n - w) || (i % w === 0) || (i % w === w - 1);
    if (!border) {
      continue;
    }
    ringCount++;
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
  if (centers.length === 0) {
    return;
  }

  // 占比 >=4% 的簇才算真背景色(主题压到边上的小簇直接忽略)
  const minCount: number = Math.max(4, ringCount * 0.04);
  for (let j = centers.length - 1; j >= 0; j--) {
    if (centers[j][3] < minCount) {
      centers.splice(j, 1);
    }
  }
  if (centers.length === 0) {
    return;   // 边缘全是花色的复杂照片,不硬抠
  }

  const localTol: number = tol * LOCAL_RATIO;

  // ---- ③ 边缘门槛:Sobel 梯度图 + 自适应阈值 ----
  const lum: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    const p: number = px[i];
    lum[i] = 0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF);
  }
  const mag: number[] = new Array<number>(n).fill(0);
  let magSum = 0;
  for (let y = 1; y < h - 1; y++) {
    for (let x = 1; x < w - 1; x++) {
      const i: number = y * w + x;
      const tl: number = lum[i - w - 1];
      const tc: number = lum[i - w];
      const tr: number = lum[i - w + 1];
      const ml: number = lum[i - 1];
      const mr: number = lum[i + 1];
      const bl: number = lum[i + w - 1];
      const bc: number = lum[i + w];
      const br: number = lum[i + w + 1];
      const gx: number = -tl - 2 * ml - bl + tr + 2 * mr + br;
      const gy: number = -tl - 2 * tc - tr + bl + 2 * bc + br;
      const mg: number = Math.sqrt(gx * gx + gy * gy);
      mag[i] = mg;
      magSum += mg;
    }
  }
  // 阈值 = max(22, 平均梯度的 2.2 倍):平整插画/照片取下限,
  // 纹理杂乱的照片自动放宽,只挡真正的轮廓线
  const edgeGate: number = Math.max(22.0, (magSum / n) * 2.2);

  // ---- ② 种子 = 属于背景簇的边界格,BFS 生长 ----
  const bg: boolean[] = new Array<boolean>(n).fill(false);
  const hops: number[] = new Array<number>(n).fill(0);   // 距上次"全局确认"连跳了几步纯局部扩散
  const queue: number[] = [];
  let head = 0;
  for (let i = 0; i < n; i++) {
    const border: boolean = (i < w) || (i >= n - w) || (i % w === 0) || (i % w === w - 1);
    if (!border) {
      continue;
    }
    const p: number[] = [L[i], A[i], B[i], 0];
    for (let ci = 0; ci < centers.length; ci++) {
      if (dist(p, centers[ci]) <= tol) {
        bg[i] = true;
        hops[i] = 0;
        queue.push(i);
        break;
      }
    }
  }

  while (head < queue.length) {
    const j: number = queue[head];
    head++;
    const x: number = j % w;
    const y: number = Math.floor(j / w);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x - 1, y, w, h, j, tol, localTol);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x + 1, y, w, h, j, tol, localTol);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x, y - 1, w, h, j, tol, localTol);
    spread(bg, hops, queue, L, A, B, centers, mag, edgeGate, x, y + 1, w, h, j, tol, localTol);
  }

  // ---- ③ 面积安全阀 ----
  let removed = 0;
  for (let i = 0; i < n; i++) {
    if (bg[i]) {
      removed++;
    }
  }
  if (removed === 0 || removed > Math.trunc((n * 92) / 100)) {
    return;
  }

  for (let i = 0; i < n; i++) {
    if (bg[i]) {
      px[i] = (px[i] & 0x00FFFFFF) >>> 0;   // 只清 alpha,RGB 留着给补钉眼用
    }
  }

  // ---- ④ 补钉眼 ----
  fillPinholes(px, bg, w, h);
}

/**
 * BFS 单步扩展。三道门按顺序过:
 * 1) 边缘门:目标格梯度超阈值(轮廓线)→ 永不越过,保护浅色主体;
 * 2) 颜色门:与任一背景簇中心近似(不限步数)或与相邻已抠格近似
 *    (连续纯局部跳变限 LOCAL_HOP_LIMIT 步,用来吃掉云朵/渐变)。
 */
function spread(bg: boolean[], hops: number[], queue: number[],
                L: number[], A: number[], B: number[],
                centers: number[][],
                mag: number[], edgeGate: number,
                x: number, y: number, w: number, h: number, from: number,
                tol: number, localTol: number): void {
  if (x < 0 || x >= w || y < 0 || y >= h) {
    return;
  }
  const k: number = y * w + x;
  if (bg[k]) {
    return;
  }
  if (mag[k] > edgeGate) {
    return;   // 轮廓线/强边缘:泛洪止步
  }

  let nearAnyCenter = false;
  for (let ci = 0; ci < centers.length; ci++) {
    const c: number[] = centers[ci];
    const dl: number = L[k] - c[0];
    const da: number = A[k] - c[1];
    const db: number = B[k] - c[2];
    if (dl * dl + da * da + db * db <= tol * tol) {
      nearAnyCenter = true;
      break;
    }
  }
  const dnl: number = L[k] - L[from];
  const dna: number = A[k] - A[from];
  const dnb: number = B[k] - B[from];
  const nearNeighbor: boolean = dnl * dnl + dna * dna + dnb * dnb <= localTol * localTol;

  if (!nearAnyCenter && !nearNeighbor) {
    return;
  }
  if (!nearAnyCenter) {
    if (hops[from] + 1 > LOCAL_HOP_LIMIT) {
      return;
    }
    hops[k] = hops[from] + 1;
  } else {
    hops[k] = 0;
  }
  bg[k] = true;
  queue.push(k);
}

/**
 * 补钉眼:某个被抠空的格子若 8 个邻居全是实心格,且这些邻居的
 * 颜色基本一致(主体内部),则把该格恢复成邻居的主色。
 * 只做一轮,避免连锁把大面积透明区域误填。
 */
function fillPinholes(px: Uint32Array, bg: boolean[], w: number, h: number): void {
  for (let y = 1; y < h - 1; y++) {
    for (let x = 1; x < w - 1; x++) {
      const i: number = y * w + x;
      if (!bg[i]) {
        continue;
      }
      let solid = 0;
      let sumR = 0;
      let sumG = 0;
      let sumB = 0;
      let allSame = true;
      let baseR: number = -1;
      let baseG: number = -1;
      let baseB: number = -1;
      for (let dy = -1; dy <= 1 && allSame; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          if (dx === 0 && dy === 0) {
            continue;
          }
          const j: number = (y + dy) * w + (x + dx);
          if (bg[j]) {
            allSame = false;
            break;
          }
          solid++;
          const r: number = (px[j] >> 16) & 0xFF;
          const g: number = (px[j] >> 8) & 0xFF;
          const b: number = px[j] & 0xFF;
          if (baseR < 0) {
            baseR = r;
            baseG = g;
            baseB = b;
          } else if (Math.abs(r - baseR) > 40
            || Math.abs(g - baseG) > 40
            || Math.abs(b - baseB) > 40) {
            allSame = false;
            break;
          }
          sumR += r;
          sumG += g;
          sumB += b;
        }
      }
      if (solid === 8 && allSame) {
        px[i] = (0xFF000000 | (Math.floor(sumR / 8) << 16)
          | (Math.floor(sumG / 8) << 8) | Math.floor(sumB / 8)) >>> 0;
        bg[i] = false;
      }
    }
  }
}

/** 4 维距离(L,a,b + 占位),用于簇比较 */
function dist(a: number[], b: number[]): number {
  const dl: number = a[0] - b[0];
  const da: number = a[1] - b[1];
  const db: number = a[2] - b[2];
  return Math.sqrt(dl * dl + da * da + db * db);
}

/**
 * 抽象模式:从工作网格像素里提取主色构成色板。
 * snapToBeads=true 时主色吸附到最近的拼豆色(可直接照单买豆);
 * 否则直接使用照片原色。返回按用量多优先排序。
 */
export function buildAbstractPalette(px: Uint32Array, beadPalette: BeadColor[],
                                     wantColors: number, snapToBeads: boolean): BeadColor[] {
  const result: BeadColor[] = [];
  const list: number[][] = [];
  for (let i = 0; i < px.length; i++) {
    const p: number = px[i];
    if (((p >>> 24) & 0xFF) >= 128) {
      list.push(rgbToLab(p));
    }
  }
  if (list.length === 0) {
    return result;
  }
  const centers: number[][] = kmeansLab(list, wantColors);
  if (centers.length === 0) {
    return result;
  }

  if (snapToBeads) {
    const beadLabs: number[][] = new Array<number[]>(beadPalette.length);
    for (let i = 0; i < beadPalette.length; i++) {
      beadLabs[i] = rgbToLab(beadPalette[i].rgb);
    }
    const used: Set<number> = new Set<number>();
    for (let i = 0; i < centers.length; i++) {
      const c: number[] = centers[i];
      const bi: number = nearest(beadLabs, c[0], c[1], c[2]);
      if (!used.has(bi)) {
        used.add(bi);
        result.push(beadPalette[bi]);
      }
    }
  } else {
    for (let i = 0; i < centers.length; i++) {
      const c: number[] = centers[i];
      result.push(new BeadColor(i + 1, '主色' + (i + 1),
        labToRgb(c[0], c[1], c[2])));
    }
  }
  return result;
}

/**
 * k 均值聚类(Lab 空间)。按亮度分位初始化(确定性),最多 12 轮,
 * 合并距离过近(ΔE<3)的簇,返回 [L,a,b,像素数] 数组,按簇大小降序。
 */
export function kmeansLab(labs: number[][], wantK: number): number[][] {
  const n: number = labs.length;
  if (n === 0) {
    return [];
  }
  const k: number = Math.max(1, Math.min(wantK, n));

  const order: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    order[i] = i;
  }
  order.sort((a: number, c: number): number => {
    const x: number = labs[a][0];
    const y: number = labs[c][0];
    if (x < y) {
      return -1;
    }
    if (x > y) {
      return 1;
    }
    return 0;
  });
  const centers: number[][] = new Array<number[]>(k);
  for (let j = 0; j < k; j++) {
    const idx: number = order[Math.min(n - 1, Math.trunc((j + 0.5) * n / k))];
    centers[j] = [labs[idx][0], labs[idx][1], labs[idx][2]];
  }

  const assign: number[] = new Array<number>(n).fill(0);
  for (let iter = 0; iter < 12; iter++) {
    let shift = 0;
    const sumL: number[] = new Array<number>(k).fill(0);
    const sumA: number[] = new Array<number>(k).fill(0);
    const sumB: number[] = new Array<number>(k).fill(0);
    const cnt: number[] = new Array<number>(k).fill(0);
    for (let i = 0; i < n; i++) {
      const best: number = nearest(centers, labs[i][0], labs[i][1], labs[i][2]);
      assign[i] = best;
      sumL[best] += labs[i][0];
      sumA[best] += labs[i][1];
      sumB[best] += labs[i][2];
      cnt[best]++;
    }
    for (let j = 0; j < k; j++) {
      if (cnt[j] === 0) {
        continue;
      }
      const nl: number = sumL[j] / cnt[j];
      const na: number = sumA[j] / cnt[j];
      const nb: number = sumB[j] / cnt[j];
      const dl: number = nl - centers[j][0];
      const da: number = na - centers[j][1];
      const db: number = nb - centers[j][2];
      shift += dl * dl + da * da + db * db;
      centers[j][0] = nl;
      centers[j][1] = na;
      centers[j][2] = nb;
    }
    if (shift < 0.5) {
      break;
    }
  }

  const cnt: number[] = new Array<number>(k).fill(0);
  for (let i = 0; i < n; i++) {
    cnt[assign[i]]++;
  }
  const cOrder: number[] = new Array<number>(k).fill(0);
  for (let j = 0; j < k; j++) {
    cOrder[j] = j;
  }
  cOrder.sort((a: number, b: number): number => cnt[b] - cnt[a]);

  const merged: number[][] = [];
  for (let oi = 0; oi < cOrder.length; oi++) {
    const j: number = cOrder[oi];
    if (cnt[j] === 0) {
      continue;
    }
    const c: number[] = centers[j];
    let dup = false;
    for (let mi = 0; mi < merged.length; mi++) {
      const m: number[] = merged[mi];
      const dl: number = c[0] - m[0];
      const da: number = c[1] - m[1];
      const db: number = c[2] - m[2];
      if (dl * dl + da * da + db * db < 9.0) {
        dup = true;
        break;
      }
    }
    if (!dup) {
      merged.push([c[0], c[1], c[2], cnt[j]]);
    }
  }
  return merged;
}

function addErr(row: number[], x: number, el: number, ea: number, eb: number, wt: number): void {
  row[x * 3] += el * wt;
  row[x * 3 + 1] += ea * wt;
  row[x * 3 + 2] += eb * wt;
}

function clampL(v: number): number {
  return v < 0 ? 0 : (v > 100 ? 100 : v);
}

function nearest(labs: number[][], l: number, a: number, b: number): number {
  let best = 0;
  let bestD: number = Number.MAX_VALUE;
  for (let i = 0; i < labs.length; i++) {
    const dl: number = l - labs[i][0];
    const da: number = a - labs[i][1];
    const db: number = b - labs[i][2];
    const d: number = dl * dl + da * da + db * db;
    if (d < bestD) {
      bestD = d;
      best = i;
    }
  }
  return best;
}

/** 给色板下标分配一个图纸符号:先 A-Z a-z 0-9(62 字符),再 AA、AB… */
export function symbolFor(paletteIndex: number): string {
  if (paletteIndex < SYMBOLS.length) {
    return SYMBOLS.charAt(paletteIndex);
  }
  const i: number = paletteIndex - SYMBOLS.length;
  const c1: number = 65 + Math.floor(i / 26) % 26;   // 'A'
  const c2: number = 65 + i % 26;
  return String.fromCharCode(c1, c2);
}

/**
 * 手动修格(移植自 PatternPatch.apply):把"某格 -> 某色板下标"的覆盖
 * 关系套到图纸上,并重新统计用量。纯函数,不改原图纸对象。
 * (安卓版对 null 入参直接返回;ArkTS 非空类型下该分支由调用方保证。)
 */
export function applyPatch(base: BeadPattern, edits: Map<number, number>): BeadPattern {
  if (edits.size === 0) {
    return base;
  }

  const cells: number[] = base.cells.slice();
  const n: number = base.palette.length;
  const counts: number[] = new Array<number>(Math.max(1, n)).fill(0);
  let empty = 0;
  let total = 0;

  for (let i = 0; i < cells.length; i++) {
    const ov: number | undefined = edits.get(i);
    const c: number = (ov !== undefined) ? ov : cells[i];
    cells[i] = c;
    if (c < 0) {
      empty++;
    } else if (c < counts.length && c >= 0) {
      counts[c]++;
      total++;
    } else {
      // 非法覆盖值,当作空格处理
      cells[i] = -1;
      empty++;
    }
  }

  const used: UsedColor[] = [];
  for (let i = 0; i < n; i++) {
    if (counts[i] > 0) {
      used.push(new UsedColor(i, base.palette[i], symbolFor(i), counts[i]));
    }
  }
  sortByCountDesc(used);

  return new BeadPattern(base.cols, base.rows, base.palette, cells, counts,
    used, total, empty, base.round);
}
