/**
 * 移植自安卓 view/PatternView.java
 * 图纸/效果图预览画布:支持双指缩放(1~12 倍)、单指拖动、双击复位、点格回调。
 * 图纸模式:色格 + 网格线 + 29 格拼板分隔线 + 符号 + 坐标 + 圆板蒙版 + 空格叉;
 * 效果图模式:仿真拼豆圆豆 + 拼板底板;拼豆模式:非当前色蒙纸色 + 已完成格描薄荷边。
 */
import {
  BeadPattern,
  symbolFor
} from '../core/PatternEngine';
import {
  textColorOn,
  darken
} from '../core/ColorMath';

/** 效果图模式(仿真圆豆) */
export const MODE_EFFECT: number = 0;
/** 图纸模式(色格 + 网格) */
export const MODE_PATTERN: number = 1;

const MIN_ZOOM: number = 1.0;
const MAX_ZOOM: number = 12.0;
/** 拖动手势触发距离(vp) */
const PAN_DISTANCE: number = 5;
/** 双击判定间隔(ms) */
const DOUBLE_TAP_MS: number = 300;
/** 单击延迟(等双击判定,ms) */
const SINGLE_TAP_DELAY_MS: number = 280;
/** 双击判定距离(vp) */
const DOUBLE_TAP_SLOP: number = 24;
/** 画布四周留白(vp) */
const FIT_PAD: number = 10;

/**
 * 点格回调(图纸模式下单击某格返回其坐标;与安卓 OnCellTapListener 同义)
 */
export interface CellTapListener {
  onCellTap: (cellX: number, cellY: number) => void;
}

/**
 * 图纸持有对象:父页面创建并持有,BeadPattern 生成后赋值,
 * 再自增 patternVersion 触发画布重绘(避免对 core 类加装饰器)。
 */
export class PatternHolder {
  pattern: BeadPattern | null = null;
  /** 拼豆模式已完成的格子(y * cols + x) */
  doneSet: Set<number> = new Set<number>();
}

/** ARGB 数字 -> CSS rgba 字符串(canvas 绘制用) */
export function cssColor(argb: number): string {
  const a: number = (argb >>> 24) & 0xFF;
  const r: number = (argb >>> 16) & 0xFF;
  const g: number = (argb >>> 8) & 0xFF;
  const b: number = argb & 0xFF;
  return 'rgba(' + r.toString() + ',' + g.toString() + ',' + b.toString() + ',' + (a / 255).toFixed(3) + ')';
}

/** RGB 数字 -> #RRGGBB 字符串(组件背景用) */
export function hexColor(rgb: number): string {
  return '#' + (rgb & 0xFFFFFF).toString(16).padStart(6, '0');
}

@Component
export struct PatternCanvas {
  /** 图纸数据持有对象(父页面创建,同一实例直读) */
  holder: PatternHolder = new PatternHolder();
  /** 点格回调 */
  onCellTap?: CellTapListener;

  @Prop @Watch('onPropChanged') mode: number = MODE_EFFECT;
  @Prop @Watch('onPropChanged') showSymbols: boolean = true;
  @Prop @Watch('onPropChanged') showGrid: boolean = true;
  @Prop @Watch('onPropChanged') assistOn: boolean = false;
  @Prop @Watch('onPropChanged') assistFocus: number = -1;
  /** 父页面在替换 holder.pattern 后自增 */
  @Prop @Watch('onPropChanged') patternVersion: number = 0;
  /** 父页面在改动 doneSet 后自增 */
  @Prop @Watch('onPropChanged') doneVersion: number = 0;

  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));
  @State canvasW: number = 0;
  @State canvasH: number = 0;

  // 视口状态(非 UI 绑定,绘制时直读)
  private zoom: number = 1.0;
  private offX: number = 0;
  private offY: number = 0;
  private lastMode: number = MODE_EFFECT;
  private lastCols: number = -1;
  private lastRows: number = -1;

  // 手势中间量
  private zoomStart: number = 1.0;
  private panBaseX: number = 0;
  private panBaseY: number = 0;
  private lastTapTime: number = 0;
  private lastTapX: number = 0;
  private lastTapY: number = 0;
  private tapTimer: number = -1;

  aboutToAppear(): void {
    this.lastMode = this.mode;
  }

  aboutToDisappear(): void {
    if (this.tapTimer >= 0) {
      clearTimeout(this.tapTimer);
      this.tapTimer = -1;
    }
  }

  private onPropChanged(): void {
    this.repaint();
  }

  /** 画幅正好放进控件时每格的尺寸(同安卓 fitCell) */
  private fitCell(): number {
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null || p.cols === 0 || p.rows === 0) {
      return 0;
    }
    if (this.canvasW <= 0 || this.canvasH <= 0) {
      return 0;
    }
    return Math.min((this.canvasW - 2 * FIT_PAD) / p.cols, (this.canvasH - 2 * FIT_PAD) / p.rows);
  }

  private marginRatio(): number {
    return this.mode === MODE_EFFECT ? 0.7 : 0;
  }

  /** 模式/画幅变化时复位缩放(同安卓 setPattern/setMode) */
  private syncResetViewport(): void {
    const p: BeadPattern | null = this.holder.pattern;
    if (this.mode !== this.lastMode) {
      this.zoom = 1;
      this.offX = 0;
      this.offY = 0;
      this.lastMode = this.mode;
    }
    if (p !== null && (p.cols !== this.lastCols || p.rows !== this.lastRows)) {
      this.zoom = 1;
      this.offX = 0;
      this.offY = 0;
      this.lastCols = p.cols;
      this.lastRows = p.rows;
    }
  }

  /**
   * 屏幕坐标 -> 图纸格坐标;不在范围内或非图纸模式返回 null。
   * 与绘制使用同一套 clamp 后的偏移,保证指哪是哪(同安卓 cellAt)。
   */
  cellAt(vx: number, vy: number): number[] | null {
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null || p.cols === 0 || this.mode !== MODE_PATTERN) {
      return null;
    }
    const fit: number = this.fitCell();
    const cell: number = fit * this.zoom;
    if (cell <= 0) {
      return null;
    }
    const cols: number = p.cols;
    const rows: number = p.rows;
    const cw: number = cols * cell + 2 * this.marginRatio() * cell;
    const ch: number = rows * cell + 2 * this.marginRatio() * cell;
    const w: number = this.canvasW;
    const h: number = this.canvasH;
    let ox: number = this.offX;
    let oy: number = this.offY;
    if (cw <= w) {
      ox = (w - cw) / 2;
    } else {
      ox = Math.max(Math.min(this.offX, 0), w - cw);
    }
    if (ch <= h) {
      oy = (h - ch) / 2;
    } else {
      oy = Math.max(Math.min(this.offY, 0), h - ch);
    }
    const m: number = this.marginRatio() * cell;
    const gx: number = Math.floor((vx - ox - m) / cell);
    const gy: number = Math.floor((vy - oy - m) / cell);
    if (gx < 0 || gy < 0 || gx >= cols || gy >= rows) {
      return null;
    }
    return [gx, gy];
  }

  private resetZoom(): void {
    this.zoom = 1;
    this.offX = 0;
    this.offY = 0;
    this.repaint();
  }

  private handleClick(x: number, y: number): void {
    const now: number = Date.now();
    const near: boolean = Math.abs(x - this.lastTapX) < DOUBLE_TAP_SLOP &&
      Math.abs(y - this.lastTapY) < DOUBLE_TAP_SLOP;
    if (now - this.lastTapTime < DOUBLE_TAP_MS && near) {
      // 双击:复位缩放,吞掉已排队的单击
      this.lastTapTime = 0;
      if (this.tapTimer >= 0) {
        clearTimeout(this.tapTimer);
        this.tapTimer = -1;
      }
      this.resetZoom();
      return;
    }
    this.lastTapTime = now;
    this.lastTapX = x;
    this.lastTapY = y;
    if (this.tapTimer >= 0) {
      clearTimeout(this.tapTimer);
    }
    this.tapTimer = setTimeout(() => {
      this.tapTimer = -1;
      this.fireSingleTap(x, y);
    }, SINGLE_TAP_DELAY_MS);
  }

  private fireSingleTap(vx: number, vy: number): void {
    if (this.onCellTap === undefined) {
      return;
    }
    const c: number[] | null = this.cellAt(vx, vy);
    if (c === null) {
      return;
    }
    this.onCellTap.onCellTap(c[0], c[1]);
  }

  repaint(): void {
    this.syncResetViewport();
    const w: number = this.canvasW;
    const h: number = this.canvasH;
    const p: BeadPattern | null = this.holder.pattern;
    this.ctx.clearRect(0, 0, w, h);
    if (p === null || p.cols === 0 || w <= 0 || h <= 0) {
      return;
    }
    const fit: number = this.fitCell();
    if (fit <= 0) {
      return;
    }
    const cell: number = fit * this.zoom;
    const m: number = this.marginRatio() * cell;
    const cw: number = p.cols * cell + 2 * m;
    const ch: number = p.rows * cell + 2 * m;

    // 居中 / 限制拖动范围(同安卓 onDraw)
    if (cw <= w) {
      this.offX = (w - cw) / 2;
    } else {
      this.offX = Math.max(Math.min(this.offX, 0), w - cw);
    }
    if (ch <= h) {
      this.offY = (h - ch) / 2;
    } else {
      this.offY = Math.max(Math.min(this.offY, 0), h - ch);
    }

    this.ctx.save();
    this.ctx.translate(this.offX + m, this.offY + m);
    if (this.mode === MODE_EFFECT) {
      this.drawEffect(p, cell, m);
    } else {
      this.drawPatternGrid(p, cell);
    }
    this.ctx.restore();
  }

  /** 圆内竖直线段:过 (x, y0~y1 范围) 画弦;不在圆内就不画(同安卓 chordV) */
  private chordV(x: number, min: number, max: number, cx: number, cy: number, r: number): void {
    const dx: number = x - cx;
    const h2: number = r * r - dx * dx;
    if (h2 <= 0) {
      return;
    }
    const half: number = Math.sqrt(h2);
    this.ctx.beginPath();
    this.ctx.moveTo(x, Math.max(min, cy - half));
    this.ctx.lineTo(x, Math.min(max, cy + half));
    this.ctx.stroke();
  }

  /** 圆内水平线段(同上,横向版) */
  private chordH(y: number, min: number, max: number, cx: number, cy: number, r: number): void {
    const dy: number = y - cy;
    const h2: number = r * r - dy * dy;
    if (h2 <= 0) {
      return;
    }
    const half: number = Math.sqrt(h2);
    this.ctx.beginPath();
    this.ctx.moveTo(Math.max(min, cx - half), y);
    this.ctx.lineTo(Math.min(max, cx + half), y);
    this.ctx.stroke();
  }

  private pathRoundRect(x: number, y: number, w: number, h: number, r: number): void {
    const rr: number = Math.max(0, Math.min(r, Math.min(w, h) / 2));
    this.ctx.beginPath();
    this.ctx.moveTo(x + rr, y);
    this.ctx.lineTo(x + w - rr, y);
    this.ctx.quadraticCurveTo(x + w, y, x + w, y + rr);
    this.ctx.lineTo(x + w, y + h - rr);
    this.ctx.quadraticCurveTo(x + w, y + h, x + w - rr, y + h);
    this.ctx.lineTo(x + rr, y + h);
    this.ctx.quadraticCurveTo(x, y + h, x, y + h - rr);
    this.ctx.lineTo(x, y + rr);
    this.ctx.quadraticCurveTo(x, y, x + rr, y);
  }

  /** 效果图:仿真的拼豆圆豆 + 拼板底板(圆形板画圆盘)(同安卓 drawEffect) */
  private drawEffect(p: BeadPattern, cell: number, m: number): void {
    const cols: number = p.cols;
    const rows: number = p.rows;

    this.ctx.fillStyle = cssColor(0xFFEFEAE3);
    if (p.round) {
      const r: number = cols * cell / 2;
      this.ctx.beginPath();
      this.ctx.arc(cols * cell / 2, rows * cell / 2, r + m * 0.9, 0, Math.PI * 2);
      this.ctx.fill();
    } else {
      const corner: number = Math.max(6, m * 0.8);
      this.pathRoundRect(-m, -m, cols * cell + m, rows * cell + m, corner);
      this.ctx.fill();
    }

    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        if (p.outsideShape(x, y)) {
          continue;
        }
        const idx: number = p.cellAt(x, y);
        const cx: number = (x + 0.5) * cell;
        const cy: number = (y + 0.5) * cell;
        if (idx < 0) {
          // 空格画底板凸点
          this.ctx.fillStyle = cssColor(0xFFD8D2C9);
          this.ctx.beginPath();
          this.ctx.arc(cx, cy, cell * 0.15, 0, Math.PI * 2);
          this.ctx.fill();
          continue;
        }
        const rgb: number = p.palette[idx].rgb;
        // 豆体
        this.ctx.fillStyle = cssColor(0xFF000000 | rgb);
        this.ctx.beginPath();
        this.ctx.arc(cx, cy, cell * 0.46, 0, Math.PI * 2);
        this.ctx.fill();
        // 深色描边环
        const ringW: number = Math.max(1, cell * 0.06);
        this.ctx.strokeStyle = cssColor(0xFF000000 | darken(rgb, 0.72));
        this.ctx.lineWidth = ringW;
        this.ctx.beginPath();
        this.ctx.arc(cx, cy, cell * 0.46 - ringW * 0.5, 0, Math.PI * 2);
        this.ctx.stroke();
        // 高光
        if (cell > 16) {
          this.ctx.fillStyle = cssColor(0x46FFFFFF);
          this.ctx.beginPath();
          this.ctx.arc(cx - cell * 0.14, cy - cell * 0.16, cell * 0.11, 0, Math.PI * 2);
          this.ctx.fill();
        }
      }
    }
  }

  /** 图纸:格子 + 网格线 + 29 格拼板分隔线 + 符号 + 坐标(圆形板画圆)(同安卓 drawPatternGrid) */
  private drawPatternGrid(p: BeadPattern, cell: number): void {
    const cols: number = p.cols;
    const rows: number = p.rows;
    const w: number = cols * cell;
    const h: number = rows * cell;
    const round: boolean = p.round;
    const halfW: number = Math.min(w, h) / 2;

    // 白底(圆形板为圆面)
    this.ctx.fillStyle = cssColor(0xFFFFFFFF);
    if (round) {
      this.ctx.beginPath();
      this.ctx.arc(w / 2, h / 2, halfW, 0, Math.PI * 2);
      this.ctx.fill();
    } else {
      this.ctx.fillRect(-1, -1, w + 2, h + 2);
    }

    // 颜色格子
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        if (round && p.outsideShape(x, y)) {
          continue;
        }
        const idx: number = p.cellAt(x, y);
        if (idx < 0) {
          continue;
        }
        this.ctx.fillStyle = cssColor(0xFF000000 | p.palette[idx].rgb);
        this.ctx.fillRect(x * cell, y * cell, cell, cell);
      }
    }

    // 空格画小叉(板外格不画)
    if (p.emptyCount > 0) {
      this.ctx.strokeStyle = cssColor(0xFFCFCFCF);
      this.ctx.lineWidth = Math.max(1, cell * 0.06);
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          if (p.outsideShape(x, y)) {
            continue;
          }
          if (p.cellAt(x, y) >= 0) {
            continue;
          }
          const x0: number = x * cell + cell * 0.3;
          const y0: number = y * cell + cell * 0.3;
          const x1: number = (x + 1) * cell - cell * 0.3;
          const y1: number = (y + 1) * cell - cell * 0.3;
          this.ctx.beginPath();
          this.ctx.moveTo(x0, y0);
          this.ctx.lineTo(x1, y1);
          this.ctx.stroke();
          this.ctx.beginPath();
          this.ctx.moveTo(x1, y0);
          this.ctx.lineTo(x0, y1);
          this.ctx.stroke();
        }
      }
    }

    // 细网格线(圆形板只画弦段);开关开就画,不再按缩放自动隐藏
    if (this.showGrid) {
      this.ctx.strokeStyle = cssColor(0x33888888);
      this.ctx.lineWidth = 1;
      for (let x = 1; x < cols; x++) {
        if (round) {
          this.chordV(x * cell, 0, h, w / 2, h / 2, halfW);
        } else {
          this.ctx.beginPath();
          this.ctx.moveTo(x * cell, 0);
          this.ctx.lineTo(x * cell, h);
          this.ctx.stroke();
        }
      }
      for (let y = 1; y < rows; y++) {
        if (round) {
          this.chordH(y * cell, 0, w, w / 2, h / 2, halfW);
        } else {
          this.ctx.beginPath();
          this.ctx.moveTo(0, y * cell);
          this.ctx.lineTo(w, y * cell);
          this.ctx.stroke();
        }
      }
    }

    // 每 29 格一条拼板分隔线
    this.ctx.strokeStyle = cssColor(0xFF9A9086);
    this.ctx.lineWidth = Math.max(2, cell * 0.1);
    for (let x = 29; x < cols; x += 29) {
      if (round) {
        this.chordV(x * cell, 0, h, w / 2, h / 2, halfW);
      } else {
        this.ctx.beginPath();
        this.ctx.moveTo(x * cell, 0);
        this.ctx.lineTo(x * cell, h);
        this.ctx.stroke();
      }
    }
    for (let y = 29; y < rows; y += 29) {
      if (round) {
        this.chordH(y * cell, 0, w, w / 2, h / 2, halfW);
      } else {
        this.ctx.beginPath();
        this.ctx.moveTo(0, y * cell);
        this.ctx.lineTo(w, y * cell);
        this.ctx.stroke();
      }
    }

    // 外框
    this.ctx.strokeStyle = cssColor(0xFF6E655C);
    this.ctx.lineWidth = 2;
    if (round) {
      this.ctx.beginPath();
      this.ctx.arc(w / 2, h / 2, halfW - 1, 0, Math.PI * 2);
      this.ctx.stroke();
    } else {
      this.ctx.strokeRect(0, 0, w, h);
    }

    // 符号(开关开就画;缩得太小时字会很小,但不至于"开关失灵")
    if (this.showSymbols) {
      this.ctx.font = 'bold ' + Math.max(2, cell * 0.42).toFixed(1) + 'px sans-serif';
      this.ctx.textAlign = 'center';
      this.ctx.textBaseline = 'middle';
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          const idx: number = p.cellAt(x, y);
          if (idx < 0) {
            continue;
          }
          const rgb: number = p.palette[idx].rgb;
          this.ctx.fillStyle = cssColor(textColorOn(rgb));
          this.ctx.fillText(symbolFor(idx), (x + 0.5) * cell, (y + 0.5) * cell);
        }
      }
    }

    // 坐标编号
    if (cell >= 16) {
      const step: number = cell >= 22 ? 1 : 5;
      this.ctx.fillStyle = cssColor(0xFF9A938C);
      this.ctx.font = Math.max(2, cell * 0.3).toFixed(1) + 'px sans-serif';
      this.ctx.textBaseline = 'middle';
      this.ctx.textAlign = 'center';
      for (let x = 0; x < cols; x += step) {
        this.ctx.fillText(String(x + 1), (x + 0.5) * cell, -cell * 0.45);
      }
      this.ctx.textAlign = 'right';
      for (let y = 0; y < rows; y += step) {
        this.ctx.fillText(String(y + 1), -cell * 0.18, (y + 0.5) * cell);
      }
    }

    // 拼豆模式:非当前颜色蒙上纸色,已完成的格子描薄荷绿边
    if (this.assistOn) {
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          if (round && p.outsideShape(x, y)) {
            continue;
          }
          const idx: number = p.cellAt(x, y);
          if (idx < 0) {
            continue;
          }
          if (this.assistFocus >= 0 && idx !== this.assistFocus) {
            this.ctx.fillStyle = cssColor(0xE6FDF8EF);
            this.ctx.fillRect(x * cell, y * cell, cell, cell);
          }
          if (this.holder.doneSet.has(y * cols + x)) {
            const isFocus: boolean = this.assistFocus < 0 || idx === this.assistFocus;
            this.ctx.strokeStyle = cssColor(isFocus ? 0xFF2EC4B6 : 0x662EC4B6);
            this.ctx.lineWidth = Math.max(2, cell * 0.12);
            const inset: number = cell * 0.12;
            this.ctx.strokeRect(x * cell + inset, y * cell + inset,
              cell - 2 * inset, cell - 2 * inset);
          }
        }
      }
    }
  }

  build() {
    Canvas(this.ctx)
      .width('100%')
      .height('100%')
      .onReady(() => {
        this.repaint();
      })
      .onAreaChange((_old: Area, now: Area) => {
        this.canvasW = Number(now.width);
        this.canvasH = Number(now.height);
        this.repaint();
      })
      .gesture(
        GestureGroup(GestureMode.Exclusive,
          PinchGesture({ fingers: 2 })
            .onActionStart((e: GestureEvent) => {
              this.zoomStart = this.zoom;
            })
            .onActionUpdate((e: GestureEvent) => {
              const fit: number = this.fitCell();
              if (fit <= 0) {
                return;
              }
              const nz: number = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoomStart * e.scale));
              const oldCell: number = fit * this.zoom;
              const newCell: number = fit * nz;
              const mo: number = this.marginRatio() * oldCell;
              const fx: number = e.pinchCenterX;
              const fy: number = e.pinchCenterY;
              const gx: number = (fx - this.offX - mo) / oldCell;
              const gy: number = (fy - this.offY - mo) / oldCell;
              const nm: number = this.marginRatio() * newCell;
              this.zoom = nz;
              this.offX = fx - nm - gx * newCell;
              this.offY = fy - nm - gy * newCell;
              this.repaint();
            }),
          PanGesture({ fingers: 1, distance: PAN_DISTANCE })
            .onActionStart((e: GestureEvent) => {
              this.panBaseX = this.offX;
              this.panBaseY = this.offY;
            })
            .onActionUpdate((e: GestureEvent) => {
              this.offX = this.panBaseX + e.offsetX;
              this.offY = this.panBaseY + e.offsetY;
              this.repaint();
            })
        )
      )
      .onClick((e: ClickEvent) => {
        this.handleClick(e.x, e.y);
      })
  }
}
