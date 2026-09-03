// 移植自 GridScanner.java

/**
 * 拍照识别拼豆图纸:从照片里估出网格间距与行列数,并按格采样颜色。
 * 纯算法零依赖(输入输出都是 ARGB 像素数组)。
 *
 * 原理:
 * 1. 用户先框住图纸区域(透视/倾斜由用户尽量摆正,框选只做粗定位);
 * 2. 对框内做亮度图,按列/行求梯度投影(网格线处颜色突变,投影出现尖峰);
 * 3. 对投影做自相关,峰最集中的 lag 就是网格间距 pitch;相位由峰位置取平均;
 * 4. 采样:每格取中心 5×5 邻域的中位均值颜色。
 */

/** 检测结果:pitchX/pitchY 为格距(像素),ox/oy 为第一格中心 */
export class Grid {
  cols: number;
  rows: number;
  ox: number;
  oy: number;
  pitchX: number;
  pitchY: number;

  constructor(cols: number, rows: number, ox: number, oy: number, pitchX: number, pitchY: number) {
    this.cols = cols;
    this.rows = rows;
    this.ox = ox;
    this.oy = oy;
    this.pitchX = pitchX;
    this.pitchY = pitchY;
  }
}

/**
 * @param argb      整图像素
 * @param w h       整图尺寸
 * @param x0 y0 x1 y1 图纸区域(含端点)
 * @return 网格参数;检测不到规则网格时返回 null(调用方退回手工指定)
 */
export function detectGrid(argb: Uint32Array, w: number, h: number,
                           x0in: number, y0in: number, x1in: number, y1in: number): Grid | null {
  let x0: number = Math.max(1, x0in);
  let y0: number = Math.max(1, y0in);
  let x1: number = Math.min(w - 2, x1in);
  let y1: number = Math.min(h - 2, y1in);
  const cw: number = x1 - x0 + 1;
  const ch: number = y1 - y0 + 1;
  if (cw < 24 || ch < 24) {
    return null;
  }

  const lum: number[] = luminance(argb, w, h);

  // 列投影:每个 x 上所有 y 的水平梯度绝对值之和(竖直网格线处现峰)
  const colProj: number[] = new Array<number>(cw).fill(0);
  for (let x = x0; x <= x1; x++) {
    let s = 0;
    for (let y = y0; y <= y1; y++) {
      s += Math.abs(lum[y * w + x] - lum[y * w + x - 1]);
    }
    colProj[x - x0] = s;
  }
  // 行投影
  const rowProj: number[] = new Array<number>(ch).fill(0);
  for (let y = y0; y <= y1; y++) {
    let s = 0;
    for (let x = x0; x <= x1; x++) {
      s += Math.abs(lum[y * w + x] - lum[(y - 1) * w + x]);
    }
    rowProj[y - y0] = s;
  }

  let pitchX: number = bestPitch(colProj);
  let pitchY: number = bestPitch(rowProj);
  // 拼豆网格横竖格距相同:一个方向失手(文档标题区干扰等)就借用另一个
  if (pitchX <= 0 && pitchY <= 0) {
    return null;
  }
  if (pitchX <= 0) {
    pitchX = pitchY;
  }
  if (pitchY <= 0) {
    pitchY = pitchX;
  }

  // 格数:做半格偏置——框选通常会比图纸多框一点边,
  // 直接 round 会多出一格;先扣掉半个 pitch 再取整
  const cols: number = Math.round((cw - pitchX * 0.5) / pitchX);
  const rows: number = Math.round((ch - pitchY * 0.5) / pitchY);
  // 拼豆图纸实际常见 3~200 格,过滤离谱结果
  if (cols < 3 || rows < 3 || cols > 200 || rows > 200) {
    return null;
  }
  if (Math.abs(cw - cols * pitchX) > pitchX * 0.75
    || Math.abs(ch - rows * pitchY) > pitchY * 0.75) {
    return null;   // 尾部剩大半格以上,说明 pitch 估计不可靠
  }

  const ox: number = x0 + firstCenter(colProj, pitchX, cols);
  const oy: number = y0 + firstCenter(rowProj, pitchY, rows);
  return new Grid(cols, rows, ox, oy, pitchX, pitchY);
}

/**
 * 自相关找周期:对投影去掉均值后,与自身平移 lag 卷积,
 * 在合法 lag 范围内取归一化相关系数最大的 lag。
 */
function bestPitch(p: number[]): number {
  const n: number = p.length;
  let mean = 0;
  for (let i = 0; i < n; i++) {
    mean += p[i];
  }
  mean /= n;
  const c: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    c[i] = p[i] - mean;
  }

  const minLag: number = Math.max(4, Math.floor(n / 200));
  const maxLag: number = Math.floor(n / 4);
  if (maxLag <= minLag) {
    return -1;
  }

  // 平滑投影(照片噪点会产生 1~3px 的伪峰)
  const sm: number = Math.max(2, Math.floor(n / 150));
  const s: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    let acc = 0;
    let cnt = 0;
    for (let k = -sm; k <= sm; k++) {
      const j: number = i + k;
      if (j >= 0 && j < n) {
        acc += c[j];
        cnt++;
      }
    }
    s[i] = acc / cnt;
  }
  let sd = 0;
  for (let i = 0; i < n; i++) {
    sd += s[i] * s[i];
  }
  sd = Math.sqrt(sd / Math.max(1, n));
  const prominence: number = sd * 0.6;
  const minGap: number = minLag;

  // 候选峰:局部极大 + 显著性(>0.6σ)
  const cand: number[][] = [];
  for (let i = 2; i < n - 2; i++) {
    if (s[i] > s[i - 1] && s[i] >= s[i + 1] && s[i] > s[i - 2]
      && s[i] > s[i + 2] && s[i] > prominence) {
      cand.push([i, Math.round(s[i])]);
    }
  }
  // 非极大抑制:按高度降序,minLag 内只留最高峰
  cand.sort((a: number[], b: number[]): number => b[1] - a[1]);
  const peaks: number[] = [];
  for (let ci = 0; ci < cand.length; ci++) {
    const cd: number[] = cand[ci];
    let near = false;
    for (let pi = 0; pi < peaks.length; pi++) {
      if (Math.abs(peaks[pi] - cd[0]) < minGap) {
        near = true;
        break;
      }
    }
    if (!near) {
      peaks.push(cd[0]);
    }
  }
  peaks.sort((a: number, b: number): number => a - b);

  // 主估计:峰间距中位数(自相关在 2x/3x 周期的 lag 上同样相关,
  // 会掉进"倍频陷阱";峰间距对它免疫)
  if (peaks.length >= 3) {
    const gaps: number[] = [];
    for (let i = 1; i < peaks.length; i++) {
      gaps.push(peaks[i] - peaks[i - 1]);
    }
    const med: number = median(gaps);
    if (med >= minLag && med <= maxLag) {
      // 一致性门:至少 60% 的 gap 是中位数的整数倍(±18%),
      // 否则说明峰来自随机纹理(如实物照片)而非规则网格
      let consistent = 0;
      for (let gi = 0; gi < gaps.length; gi++) {
        const k: number = gaps[gi] / med;
        if (Math.abs(k - Math.round(k)) < 0.18) {
          consistent++;
        }
      }
      if (consistent >= Math.max(3, Math.trunc(Math.fround(gaps.length * Math.fround(0.6))))) {
        // 漏检峰会产生 2x/3x 的 gap:按中位比值折算回基频
        const units: number[] = [];
        for (let gi = 0; gi < gaps.length; gi++) {
          const k: number = Math.max(1, Math.round(gaps[gi] / med));
          units.push(gaps[gi] / k);
        }
        return median(units);
      }
    }
  }

  // 兜底:自相关(峰太少时)
  let best = -1;
  let bestLag: number = -1;
  for (let lag = minLag; lag <= maxLag; lag++) {
    let dot = 0;
    let e0 = 0;
    let e1 = 0;
    for (let i = 0; i + lag < n; i++) {
      dot += c[i] * c[i + lag];
      e0 += c[i] * c[i];
      e1 += c[i + lag] * c[i + lag];
    }
    if (e0 <= 0 || e1 <= 0) {
      continue;
    }
    const r: number = dot / Math.sqrt(e0 * e1);
    if (r > best) {
      best = r;
      bestLag = lag;
    }
  }
  if (bestLag < 0 || best < 0.15) {
    return -1;   // 相关性太弱 = 没有规则网格
  }

  // 细化:峰位置对 bestLag 的余数中位
  const phases: number[] = [];
  for (let pi = 0; pi < peaks.length; pi++) {
    const k: number = peaks[pi] / bestLag;
    if (Math.abs(k - Math.round(k)) < 0.3) {
      phases.push(peaks[pi] - Math.round(k) * bestLag);
    }
  }
  if (phases.length < 2) {
    return bestLag;
  }
  const phase: number = median(phases);
  return bestLag + phase / Math.max(1, Math.round(n / bestLag));
}

function median(v: number[]): number {
  const s: number[] = v.slice();
  s.sort((a: number, b: number): number => a - b);
  return s[Math.floor(s.length / 2)];
}

/** 第一格中心:网格线(或色块边界)永远在格边界上,故 = 首个显著峰 + 半个 pitch */
function firstCenter(p: number[], pitch: number, cols: number): number {
  const n: number = p.length;
  let mean = 0;
  for (let i = 0; i < n; i++) {
    mean += p[i];
  }
  mean /= n;
  for (let i = 1; i < n - 1; i++) {
    if (p[i] > mean && p[i] >= p[i - 1] && p[i] >= p[i + 1]) {
      const c: number = i + pitch * 0.5;
      return Math.max(pitch * 0.5, Math.min(n - pitch * 0.5, c));
    }
  }
  return pitch * 0.5;
}

/**
 * 按网格采样(含边缘背景裁剪):返回裁剪后的网格(行优先),
 * 实际 cols/rows 写入 outDims[0]/outDims[1]。
 * 框选常会把图纸外的空白/网站底色框进来,形成"整行/列都是背景"的假格子。
 * 背景色 = 采样结果最外圈出现最多的颜色;某条边 ≥90% 是它、且它占全图 ≥30%
 * 时才裁该边(避免误裁纯色边框的图纸);每边最多裁 35%。
 */
export function sampleGrid(argb: Uint32Array, w: number, h: number,
                           g: Grid, outDims: number[]): Uint32Array {
  const cols: number = g.cols;
  const rows: number = g.rows;
  const raw: Uint32Array = new Uint32Array(cols * rows);
  for (let j = 0; j < rows; j++) {
    for (let i = 0; i < cols; i++) {
      const cx: number = g.ox + i * g.pitchX;
      const cy: number = g.oy + j * g.pitchY;
      raw[j * cols + i] = sampleCell(argb, w, h, cx, cy,
        Math.min(g.pitchX, g.pitchY) * 0.22);
    }
  }
  return trimBackground(raw, cols, rows, outDims);
}

/** 边缘背景裁剪:实际裁剪后的 cols/rows 写入 outColsRows(传 null 则不写) */
export function trimBackground(cells: Uint32Array, cols: number, rows: number,
                               outColsRows: number[] | null = null): Uint32Array {
  // 量化到 4bit/通道,抗噪
  const n: number = cols * rows;
  const q: Uint32Array = new Uint32Array(n);
  for (let i = 0; i < n; i++) {
    const p: number = cells[i];
    q[i] = ((((p >> 16) & 0xF0) << 12) | (((p >> 8) & 0xF0) << 8)
      | ((p & 0xF0) << 4)) >>> 0;
  }
  // 边界主色 = 四条边并集里出现最多的量化色
  const cnt: Map<number, number> = new Map<number, number>();
  for (let x = 0; x < cols; x++) {
    addCount(cnt, q[x]);
    addCount(cnt, q[(rows - 1) * cols + x]);
  }
  for (let y = 0; y < rows; y++) {
    addCount(cnt, q[y * cols]);
    addCount(cnt, q[y * cols + cols - 1]);
  }
  let bg: number = 0;
  let bgMax: number = 0;
  cnt.forEach((value: number, key: number): void => {
    if (value > bgMax) {
      bgMax = value;
      bg = key;
    }
  });
  let bgTotal = 0;
  for (let i = 0; i < n; i++) {
    if (q[i] === bg) {
      bgTotal++;
    }
  }
  if (bgTotal < n * 0.3) {
    if (outColsRows !== null) {
      outColsRows[0] = cols;
      outColsRows[1] = rows;
    }
    return cells;
  }

  let x0: number = 0;
  let x1: number = cols - 1;
  let y0: number = 0;
  let y1: number = rows - 1;
  // 安卓为 (int)(cols * 0.35f):float 乘后向零截断
  const maxCutX: number = Math.trunc(Math.fround(cols * Math.fround(0.35)));
  const maxCutY: number = Math.trunc(Math.fround(rows * Math.fround(0.35)));
  // 左
  while (x0 < x1 && x0 < maxCutX && edgeCol(q, cols, rows, x0, bg, 0.9)) {
    x0++;
  }
  while (x1 > x0 && (cols - 1 - x1) < maxCutX && edgeCol(q, cols, rows, x1, bg, 0.9)) {
    x1--;
  }
  while (y0 < y1 && y0 < maxCutY && edgeRow(q, cols, rows, y0, bg, 0.9)) {
    y0++;
  }
  while (y1 > y0 && (rows - 1 - y1) < maxCutY && edgeRow(q, cols, rows, y1, bg, 0.9)) {
    y1--;
  }
  if (x0 === 0 && y0 === 0 && x1 === cols - 1 && y1 === rows - 1) {
    if (outColsRows !== null) {
      outColsRows[0] = cols;
      outColsRows[1] = rows;
    }
    return cells;
  }
  const nw: number = x1 - x0 + 1;
  const nh: number = y1 - y0 + 1;
  if (nw < 3 || nh < 3) {
    if (outColsRows !== null) {
      outColsRows[0] = cols;
      outColsRows[1] = rows;
    }
    return cells;
  }
  const out: Uint32Array = new Uint32Array(nw * nh);
  for (let y = 0; y < nh; y++) {
    out.set(cells.subarray((y0 + y) * cols + x0, (y0 + y) * cols + x0 + nw), y * nw);
  }
  if (outColsRows !== null) {
    outColsRows[0] = nw;
    outColsRows[1] = nh;
  }
  return out;
}

function addCount(m: Map<number, number>, k: number): void {
  const v: number | undefined = m.get(k);
  m.set(k, v === undefined ? 1 : v + 1);
}

function edgeCol(q: Uint32Array, cols: number, rows: number, x: number, bg: number, ratio: number): boolean {
  let hit = 0;
  for (let y = 0; y < rows; y++) {
    if (q[y * cols + x] === bg) {
      hit++;
    }
  }
  return hit >= rows * ratio;
}

function edgeRow(q: Uint32Array, cols: number, rows: number, y: number, bg: number, ratio: number): boolean {
  let hit = 0;
  for (let x = 0; x < cols; x++) {
    if (q[y * cols + x] === bg) {
      hit++;
    }
  }
  return hit >= cols * ratio;
}

function sampleCell(argb: Uint32Array, w: number, h: number, cx: number, cy: number, r: number): number {
  const x0: number = Math.max(0, Math.round(cx - r));
  const y0: number = Math.max(0, Math.round(cy - r));
  const x1: number = Math.min(w - 1, Math.round(cx + r));
  const y1: number = Math.min(h - 1, Math.round(cy + r));
  let sr = 0;
  let sg = 0;
  let sb = 0;
  let n = 0;
  for (let y = y0; y <= y1; y++) {
    for (let x = x0; x <= x1; x++) {
      const p: number = argb[y * w + x];
      sr += (p >> 16) & 0xFF;
      sg += (p >> 8) & 0xFF;
      sb += p & 0xFF;
      n++;
    }
  }
  if (n === 0) {
    return 0;
  }
  return (0xFF000000 | (Math.floor(sr / n) << 16)
    | (Math.floor(sg / n) << 8) | Math.floor(sb / n)) >>> 0;
}

function luminance(argb: Uint32Array, w: number, h: number): number[] {
  const l: number[] = new Array<number>(w * h).fill(0);
  for (let i = 0; i < l.length; i++) {
    const p: number = argb[i];
    l[i] = 0.299 * ((p >> 16) & 0xFF)
      + 0.587 * ((p >> 8) & 0xFF)
      + 0.114 * (p & 0xFF);
  }
  return l;
}
