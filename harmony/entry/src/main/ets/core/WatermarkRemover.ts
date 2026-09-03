// 移植自 WatermarkRemover.java

/**
 * 选框水印修复(纯算法,零平台依赖)。
 * 两段式:
 *  1. 掩码检测:取选框外 3px 环带的中值色为"背景参考",框内与中值色差超阈值的
 *     像素视为水印笔画(再膨胀 3px 盖住抗锯齿边)。选框内容与周围一致(框错了/
 *     只有噪声)时直接不动,背景纹理、结构边缘全部原样保留;
 *  2. 填充:只把掩码像素视为未知,用周围真实像素做"洋葱剥皮"逐层内推,
 *     再雅可比松弛过渡。水印占满选框(>65%)或背景太花时退化为整框填充。
 * 拼豆图纸最终只取低频颜色(≤116×116 格),平滑填充转图纸后基本无痕。
 */
const DX: number[] = [1, -1, 0, 0, 1, 1, -1, -1];
const DY: number[] = [0, 0, 1, -1, 1, -1, 1, -1];
/** 与环带中值的最大通道差超过该值 => 疑似水印像素 */
const MASK_THRESHOLD: number = 28;
/** 环带宽度(px) */
const RING: number = 3;
/** 掩码膨胀半径(px),盖住水印抗锯齿过渡边 */
const DILATE: number = 3;

/**
 * @param argb        图像像素(原地修改)
 * @param w h         图像尺寸
 * @param x0 y0 x1 y1 修复框(像素坐标,含端点;自动钳制,最小 3×3)
 */
export function removeWatermark(argb: Uint32Array, w: number, h: number,
                                x0in: number, y0in: number, x1in: number, y1in: number): void {
  let x0: number = Math.max(1, x0in);
  let y0: number = Math.max(1, y0in);
  let x1: number = Math.min(w - 2, x1in);
  let y1: number = Math.min(h - 2, y1in);
  if (x1 - x0 < 2 || y1 - y0 < 2) {
    return;
  }
  const rw: number = x1 - x0 + 1;
  const rh: number = y1 - y0 + 1;
  const n: number = rw * rh;

  // ---- 1) 掩码:与环带中值色比对 ----
  const med: number[] = ringMedian(argb, w, h, x0, y0, x1, y1);
  const mask: boolean[] = new Array<boolean>(n).fill(false);
  let hits = 0;
  for (let y = 0; y < rh; y++) {
    for (let x = 0; x < rw; x++) {
      const p: number = argb[(y0 + y) * w + (x0 + x)];
      const dev: number = Math.max(Math.max(
        Math.abs(((p >> 16) & 0xFF) - med[0]),
        Math.abs(((p >> 8) & 0xFF) - med[1])),
        Math.abs((p & 0xFF) - med[2]));
      if (dev > MASK_THRESHOLD) {
        mask[y * rw + x] = true;
        hits++;
      }
    }
  }
  const frac: number = hits / n;
  if (frac < 0.02) {
    return;   // 选框内与周围一致:框错了或只有噪声,不动图
  }
  if (frac > 0.65) {
    mask.fill(true);   // 水印占满/背景太花:整框填充
  } else {
    dilate(mask, rw, rh, DILATE);
  }

  // ---- 2) 洋葱剥皮填充(待填区 = 掩码;未掩码像素保持原色) ----
  let unknownMask: boolean[] = mask.slice();
  let next: boolean[] = new Array<boolean>(n).fill(false);
  const fill: number[] = new Array<number>(n).fill(0);
  for (let y = 0; y < rh; y++) {
    for (let x = 0; x < rw; x++) {
      if (!mask[y * rw + x]) {
        fill[y * rw + x] = argb[(y0 + y) * w + (x0 + x)];
      }
    }
  }
  let filled = 0;
  while (filled < n) {
    for (let i = 0; i < n; i++) {
      next[i] = unknownMask[i];
    }
    let now = 0;
    for (let y = 0; y < rh; y++) {
      for (let x = 0; x < rw; x++) {
        const i: number = y * rw + x;
        if (!unknownMask[i]) {
          continue;
        }
        const gx: number = x0 + x;
        const gy: number = y0 + y;
        let sr = 0;
        let sg = 0;
        let sb = 0;
        let cnt = 0;
        for (let d = 0; d < 8; d++) {
          const nx: number = gx + DX[d];
          const ny: number = gy + DY[d];
          let cp: number;
          if (nx < x0 || nx > x1 || ny < y0 || ny > y1) {
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
              continue;
            }
            cp = argb[ny * w + nx];          // 选框外 = 真实像素
          } else {
            const j: number = (ny - y0) * rw + (nx - x0);
            if (unknownMask[j]) {
              continue;
            }
            cp = fill[j];                    // 已填充像素
          }
          sr += (cp >> 16) & 0xFF;
          sg += (cp >> 8) & 0xFF;
          sb += cp & 0xFF;
          cnt++;
        }
        if (cnt > 0) {
          fill[i] = (0xFF000000
            | (Math.floor(sr / cnt) << 16) | (Math.floor(sg / cnt) << 8) | Math.floor(sb / cnt)) >>> 0;
          next[i] = false;
          now++;
        }
      }
    }
    const t: boolean[] = unknownMask;
    unknownMask = next;
    next = t;
    if (now === 0) {
      break;
    }
    filled += now;
  }

  // ---- 3) 雅可比松弛(只动掩码像素,边界保持真实像素) ----
  const iters: number = Math.min(120, Math.max(40, Math.floor((rw + rh) / 2)));
  const bufA: number[] = new Array<number>(n).fill(0);
  const bufB: number[] = new Array<number>(n).fill(0);
  for (let i = 0; i < n; i++) {
    bufA[i] = fill[i];
  }
  for (let it = 0; it < iters; it++) {
    const src: number[] = (it & 1) === 0 ? bufA : bufB;
    const dst: number[] = (it & 1) === 0 ? bufB : bufA;
    for (let y = 0; y < rh; y++) {
      for (let x = 0; x < rw; x++) {
        const i: number = y * rw + x;
        if (!mask[i]) {
          dst[i] = src[i];
          continue;
        }
        const gx: number = x0 + x;
        const gy: number = y0 + y;
        let sr = 0;
        let sg = 0;
        let sb = 0;
        let cnt = 0;
        for (let d = 0; d < 4; d++) {
          const nx: number = gx + DX[d];
          const ny: number = gy + DY[d];
          let cp: number;
          if (nx < x0 || nx > x1 || ny < y0 || ny > y1) {
            cp = argb[ny * w + nx];
          } else {
            cp = src[(ny - y0) * rw + (nx - x0)];
          }
          sr += (cp >> 16) & 0xFF;
          sg += (cp >> 8) & 0xFF;
          sb += cp & 0xFF;
          cnt++;
        }
        dst[i] = (0xFF000000
          | (Math.floor(sr / cnt) << 16) | (Math.floor(sg / cnt) << 8) | Math.floor(sb / cnt)) >>> 0;
      }
    }
  }
  const result: number[] = (iters % 2 === 0) ? bufA : bufB;
  for (let y = 0; y < rh; y++) {
    for (let x = 0; x < rw; x++) {
      if (mask[y * rw + x]) {
        argb[(y0 + y) * w + (x0 + x)] = result[y * rw + x];
      }
    }
  }
}

/** 选框外 RING px 环带各通道中值(选框贴图边时用剩余侧) */
function ringMedian(argb: Uint32Array, w: number, h: number,
                    x0: number, y0: number, x1: number, y1: number): number[] {
  const rx0: number = Math.max(0, x0 - RING);
  const ry0: number = Math.max(0, y0 - RING);
  const rx1: number = Math.min(w - 1, x1 + RING);
  const ry1: number = Math.min(h - 1, y1 + RING);
  const rs: number[] = [];
  const gs: number[] = [];
  const bs: number[] = [];
  for (let y = ry0; y <= ry1; y++) {
    for (let x = rx0; x <= rx1; x++) {
      if (x >= x0 && x <= x1 && y >= y0 && y <= y1) {
        continue;
      }
      const p: number = argb[y * w + x];
      rs.push((p >> 16) & 0xFF);
      gs.push((p >> 8) & 0xFF);
      bs.push(p & 0xFF);
    }
  }
  if (rs.length === 0) {
    const fallback: number[] = [128, 128, 128];
    return fallback;
  }
  rs.sort((a: number, b: number): number => a - b);
  gs.sort((a: number, b: number): number => a - b);
  bs.sort((a: number, b: number): number => a - b);
  const m: number = Math.floor(rs.length / 2);
  const out: number[] = [rs[m], gs[m], bs[m]];
  return out;
}

/** 掩码膨胀 r 轮(8 邻域) */
function dilate(mask: boolean[], rw: number, rh: number, r: number): void {
  for (let round = 0; round < r; round++) {
    const src: Uint8Array = new Uint8Array(mask.length);
    for (let i = 0; i < mask.length; i++) {
      src[i] = mask[i] ? 1 : 0;
    }
    for (let y = 0; y < rh; y++) {
      for (let x = 0; x < rw; x++) {
        if (src[y * rw + x] === 1) {
          continue;
        }
        let hit = false;
        for (let d = 0; d < 8 && !hit; d++) {
          const nx: number = x + DX[d];
          const ny: number = y + DY[d];
          if (nx >= 0 && ny >= 0 && nx < rw && ny < rh
            && src[ny * rw + nx] === 1) {
            hit = true;
          }
        }
        mask[y * rw + x] = hit;
      }
    }
  }
}
