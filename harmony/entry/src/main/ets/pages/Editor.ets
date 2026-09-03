/**
 * 编辑页 —— 移植自安卓 activity_editor.xml + EditorActivity.java
 * 顶部(返回/标题/撤销/重做/菜单)+ Tabs(效果图/图纸/豆豆清单)+
 * 参数区(画幅/色板/限色/写实抽象/画面调节/照片预处理/手动修格/拼豆模式/变换)。
 * 数据流:photoAccessHelper 所选图片读像素(Uint32Array ARGB)→ PatternEngine.generate
 * → PatternCanvas 展示;参数变更 120ms 防抖重新生成;去水印框选修复;拼豆模式逐色辅助。
 */
import { router, promptAction, window } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { image } from '@kit.ImageKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { BeadColor, getPalette, PALETTE_NAMES } from '../core/BeadColor';
import {
  BeadPattern,
  Options,
  STYLE_REALISTIC,
  STYLE_ABSTRACT,
  applyPatch,
  generate
} from '../core/PatternEngine';
import { labDist2, rgbToLab, textColorOn } from '../core/ColorMath';
import { brandTagOf } from '../core/BrandCharts';
import { invGet, invSet } from '../core/BeadInventory';
import { removeWatermark } from '../core/WatermarkRemover';
import { MODE_EFFECT, MODE_PATTERN, PatternCanvas, PatternHolder, CellTapListener, hexColor, cssColor } from '../components/PatternCanvas';
import { TemplatePayload } from './Templates';

/** router 传参(Index「从相册选照片」带来的图片 URI) */
export interface EditorParams {
  photoUri: string;
}

const MIN_SIZE: number = 8;
const MAX_SIZE: number = 160;
/** 抽象画主色数量档位(同安卓 ABSTRACT_CHOICES,默认下标 2 = 8 色) */
const ABSTRACT_CHOICES: number[] = [4, 6, 8, 10, 12, 16];
const BRICK_SIZES: number[] = [2, 3, 4, 6];
const BRICK_LABELS: string[] = ['轻 2×2', '中 3×3', '强 4×4', '超强 6×6'];
/** 色数上限档位,0 = 不限 */
const COLOR_LIMITS: number[] = [0, 12, 18, 26, 40];
const LIMIT_LABELS: string[] = ['不限', '12色', '18色', '26色', '40色'];
const SIZE_LABELS: string[] = ['29×29', '58×58', '87×87', '116×116'];
const MAX_UNDO: number = 40;
/** 每颗豆参考单价(元),默认同安卓 0.02 */
const BEAD_UNIT_PRICE: number = 0.02;
/** 照片读取上限边长(同安卓 ImageLoader 2048) */
const PHOTO_MAX_EDGE: number = 2048;
/** 生成防抖(ms,同安卓 scheduleRegen) */
const REGEN_DEBOUNCE_MS: number = 120;
/** 去水印对话框显示上限(同安卓 320/380) */
const WM_MAX_W: number = 320;
const WM_MAX_H: number = 380;
/** 色数选择:恢复自动匹配 */
const PICK_RESTORE: number = -2;

function toast(msg: string): void {
  promptAction.showToast({ message: msg });
}

function errText(e: Object | null | undefined): string {
  if (e instanceof Error) {
    return e.message;
  }
  return String(e);
}

/** 千分位整数(对应 String.format "%,d") */
function fmtInt(v: number): string {
  const n: number = Math.round(v);
  const neg: boolean = n < 0;
  const s: string = Math.abs(n).toString();
  let out: string = '';
  let c: number = 0;
  for (let i = s.length - 1; i >= 0; i--) {
    out = s.charAt(i) + out;
    c++;
    if (c % 3 === 0 && i > 0) {
      out = ',' + out;
    }
  }
  return (neg ? '-' : '') + out;
}

/** 带符号数值(滑杆标签) */
function fmtSigned(v: number): string {
  return (v > 0 ? '+' : '') + v;
}

/** 豆豆清单行渲染模型 */
class BeadRowVM {
  rgb: number = 0;
  symbol: string = '';
  symbolColor: number = 0xFF1B1B1B;
  name: string = '';
  brand: string = '';
  count: string = '';
  percent: string = '';
  pct: number = 0;
  inv: string = '库存未登记';
  invColor: string = '#8A8F98';
  sub: string = '';
  hasSub: boolean = false;
}

/** 点格选色行(含小节标题项) */
class ColorOptVM {
  label: string = '';
  rgb: number = 0xFFCCCCCC;
  bold: boolean = false;
  /** >=0 色板下标;PICK_RESTORE 恢复自动;-1 小节标题 */
  target: number = -1;
}

/** 豆仓登记行 */
class InvRowVM {
  rgb: number = 0;
  label: string = '';
  draft: string = '';
}

@Entry
@Component
struct Editor {
  // ---- 图纸数据(非响应式持有,经 patternVersion 驱动画布重绘) ----
  holder: PatternHolder = new PatternHolder();
  private rawPattern: BeadPattern | null = null;
  private editMap: Map<number, number> = new Map<number, number>();
  private undoStack: Array<Map<number, number>> = [];
  private redoStack: Array<Map<number, number>> = [];
  private beadDone: Set<number> = new Set<number>();
  private srcPx: Uint32Array | null = null;
  private srcW: number = 0;
  private srcH: number = 0;
  private origPx: Uint32Array | null = null;
  private genSeq: number = 0;
  private regenTimer: number = -1;
  private invMap: Map<number, number> = new Map<number, number>();
  private uiCtx: common.UIAbilityContext | null = null;
  private pickerCell: number = 0;

  // ---- 顶部 / 标签 ----
  @State activeTab: number = 0;
  @State canvasMode: number = MODE_EFFECT;
  @State undoAlpha: number = 0.35;
  @State redoAlpha: number = 0.35;
  private exportMenu: MenuElement[] = [];

  // ---- 生成参数 ----
  @State cols: number = 58;
  @State rows: number = 58;
  @State tierIdx: number = 2; // 默认 90 色
  @State dither: boolean = false;
  @State brightnessAdj: number = 0;
  @State contrastAdj: number = 0;
  @State saturation: number = 0;
  @State style: number = STYLE_REALISTIC;
  @State brickIdx: number = 1; // 默认 中度 3×3
  @State abstractUsePalette: boolean = true;
  @State abstractColors: number = 8;
  @State abstractSnap: boolean = true;
  @State bgRemove: boolean = false;
  @State bgTolerance: number = 45;
  @State roundBoard: boolean = false;
  @State maxColorsIdx: number = 0;
  @State boardHint: string = '';
  @State patternVersion: number = 0;
  @State doneVersion: number = 0;
  @State showSymbols: boolean = true;
  @State showGrid: boolean = true;
  @State loading: boolean = false;
  @State loadingText: string = '正在生成…';

  // ---- 手动修格 / 拼豆模式 ----
  @State editCell: boolean = false;
  @State editsCount: number = 0;
  @State beadAssist: boolean = false;
  @State assistFocus: number = -1;
  @State assistRgb: number = 0xFFCCCCCC;
  @State assistText: string = '先生成图纸';
  @State assistProgressText: string = '已完成 0 / 0 颗(0%)';

  // ---- 豆豆清单 ----
  @State summaryText: string = '';
  @State rowsVM: BeadRowVM[] = [];
  @State invShow: boolean = false;
  @State invTitle: string = '';
  @State invRows: InvRowVM[] = [];

  // ---- 点格选色 ----
  @State pickerShow: boolean = false;
  @State pickerHeader: string = '';
  @State pickerOptions: ColorOptVM[] = [];

  // ---- 去水印框选 ----
  @State wmShow: boolean = false;
  @State wmImage: image.PixelMap | null = null;
  @State wmDw: number = 0;
  @State wmDh: number = 0;
  @State wmHasEdited: boolean = false;
  private wmCtx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));
  private wmX0: number = -1;
  private wmY0: number = -1;
  private wmX1: number = -1;
  private wmY1: number = -1;

  private paletteOptions: SelectOption[] = [];
  private absOptions: SelectOption[] = [];

  aboutToAppear(): void {
    this.uiCtx = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.syncSizeUi();
    // 下拉框数据
    for (const n of PALETTE_NAMES) {
      const o: SelectOption = { value: n };
      this.paletteOptions.push(o);
    }
    for (const c of ABSTRACT_CHOICES) {
      const o: SelectOption = { value: c + ' 色' };
      this.absOptions.push(o);
    }
    // ⋮ 导出菜单(阶段 1 全部降级提示,文案照抄安卓)
    this.exportMenu = [
      { value: '保存图纸图片(可打印)', action: () => { toast('阶段 2 开放'); } },
      { value: '保存效果图', action: () => { toast('阶段 2 开放'); } },
      { value: '分享图纸', action: () => { toast('阶段 2 开放'); } },
      { value: '导出 PDF 文档', action: () => { toast('阶段 2 开放'); } },
      { value: '💾 保存项目存档', action: () => { toast('阶段 2 开放'); } }
    ];
    this.resolveEntry();
  }

  aboutToDisappear(): void {
    if (this.regenTimer >= 0) {
      clearTimeout(this.regenTimer);
      this.regenTimer = -1;
    }
    if (this.beadAssist) {
      this.setKeepScreenOn(false);
    }
  }

  // ---------------- 入口数据 ----------------

  private resolveEntry(): void {
    const ep: EditorParams | undefined = router.getParams() as EditorParams | undefined;
    let photoUri: string = '';
    if (ep !== undefined && ep !== null) {
      photoUri = ep.photoUri;
    }
    if (photoUri.length > 0) {
      this.loadPhoto(photoUri);
      return;
    }
    // 模板库路径:AppStorage 里的模板像素
    const tp: TemplatePayload | undefined = AppStorage.get<TemplatePayload>('tplPayload');
    if (tp !== undefined && tp.px.length > 0) {
      this.srcPx = tp.px;
      this.srcW = tp.w;
      this.srcH = tp.h;
      this.origPx = tp.px;
      if (tp.suggestedSize >= MIN_SIZE && tp.suggestedSize <= MAX_SIZE) {
        this.cols = tp.suggestedSize;
        this.rows = tp.suggestedSize;
        this.syncSizeUi();
      }
      this.regenerate();
      return;
    }
    toast('没有选择照片');
    router.back();
  }

  /** 读所选图片像素:RGBA 字节流转 ARGB Uint32Array(同安卓 int 位布局) */
  private async loadPhoto(uriStr: string): Promise<void> {
    this.showLoading(true);
    try {
      const f: fs.File = await fs.open(uriStr, fs.OpenMode.READ_ONLY);
      const src: image.ImageSource = image.createImageSource(f.fd);
      const info: image.ImageInfo = await src.getImageInfo();
      let dw: number = info.size.width;
      let dh: number = info.size.height;
      const mx: number = Math.max(dw, dh);
      if (mx > PHOTO_MAX_EDGE) {
        const s: number = PHOTO_MAX_EDGE / mx;
        dw = Math.max(1, Math.round(dw * s));
        dh = Math.max(1, Math.round(dh * s));
      }
      const opts: image.DecodingOptions = {
        desiredSize: { width: dw, height: dh },
        desiredPixelFormat: image.PixelMapFormat.RGBA_8888
      };
      const pm: image.PixelMap = await src.createPixelMap(opts);
      const pinfo: image.ImageInfo = await pm.getImageInfo();
      const w: number = pinfo.size.width;
      const h: number = pinfo.size.height;
      const buf: ArrayBuffer = new ArrayBuffer(w * h * 4);
      await pm.readPixelsToBuffer(buf);
      const bytes: Uint8Array = new Uint8Array(buf);
      const px: Uint32Array = new Uint32Array(w * h);
      for (let i = 0; i < w * h; i++) {
        const r: number = bytes[i * 4];
        const g: number = bytes[i * 4 + 1];
        const b: number = bytes[i * 4 + 2];
        const a: number = bytes[i * 4 + 3];
        px[i] = (((a << 24) >>> 0) | (r << 16) | (g << 8) | b) >>> 0;
      }
      await pm.release();
      await src.release();
      await fs.close(f);
      this.srcPx = px;
      this.srcW = w;
      this.srcH = h;
      this.origPx = px;
      this.showLoading(false);
      this.regenerate();
    } catch (e) {
      this.showLoading(false);
      toast('这张照片读取失败,换一张试试吧');
      router.back();
    }
  }

  private async setKeepScreenOn(on: boolean): Promise<void> {
    try {
      const ctx: common.UIAbilityContext | null = this.uiCtx;
      if (ctx === null) {
        return;
      }
      const win: window.Window = await window.getLastWindow(ctx);
      win.setWindowKeepScreenOn(on);
    } catch (e) {
      // 忽略:无窗口时保持常亮设置失败不影响功能
    }
  }

  private showLoading(show: boolean, text: string = ''): void {
    this.loading = show;
    if (text.length > 0) {
      this.loadingText = text;
    }
  }

  // ---------------- 标签页 ----------------

  private selectTab(which: number): void {
    this.activeTab = which;
    if (which < 2) {
      this.canvasMode = which === 0 ? MODE_EFFECT : MODE_PATTERN;
    }
  }

  // ---------------- 生成 ----------------

  private scheduleRegen(): void {
    if (this.srcPx === null) {
      return;
    }
    if (this.regenTimer >= 0) {
      clearTimeout(this.regenTimer);
    }
    this.regenTimer = setTimeout(() => {
      this.regenTimer = -1;
      this.regenerate();
    }, REGEN_DEBOUNCE_MS);
  }

  private regenerate(): void {
    if (this.srcPx === null) {
      return;
    }
    this.showLoading(true);
    const seq: number = ++this.genSeq;
    setTimeout(() => {
      if (seq !== this.genSeq || this.srcPx === null) {
        return;
      }
      const opt: Options = new Options();
      opt.cols = this.cols;
      opt.rows = this.rows;
      opt.dither = this.dither;
      opt.brightness = this.brightnessAdj;
      opt.contrast = this.contrastAdj;
      opt.saturation = this.saturation;
      opt.style = this.style;
      opt.brickSize = BRICK_SIZES[this.brickIdx];
      opt.abstractUsePalette = this.abstractUsePalette;
      opt.abstractColors = this.abstractColors;
      opt.abstractSnapToBeads = this.abstractSnap;
      opt.bgRemove = this.bgRemove;
      opt.bgTolerance = this.bgTolerance;
      opt.roundBoard = this.roundBoard;
      opt.maxColors = COLOR_LIMITS[this.maxColorsIdx];
      const np: BeadPattern = generate(this.srcPx, this.srcW, this.srcH, getPalette(this.tierIdx), opt);
      if (seq !== this.genSeq) {
        return;
      }
      const patched: BeadPattern = applyPatch(np, this.editMap);
      this.rawPattern = np;
      this.holder.pattern = patched;
      this.patternVersion++;
      // 重新生成后格子变了,完成度标记失效,清空重来(同安卓)
      this.beadDone.clear();
      this.doneVersion++;
      if (this.beadAssist) {
        this.assistFocus = patched.usedColors.length > 0 ? patched.usedColors[0].index : -1;
        this.updateAssistUi();
      }
      this.rebuildRowsAndSummary();
      this.refreshInventory();
      this.showLoading(false);
    }, 30);
  }

  /** 尺寸/色板等结构性变化:清修格 + 重新生成(同安卓 structureChanged) */
  private structureChanged(): void {
    this.syncSizeUi();
    this.invalidateEdits();
    this.scheduleRegen();
  }

  /** 手动修格基于格下标,尺寸/形状/风格/色板一变就整体失效 */
  private invalidateEdits(): void {
    if (this.editMap.size > 0) {
      this.editMap.clear();
    }
    this.undoStack.length = 0;
    this.redoStack.length = 0;
    this.updateUndoRedoButtons();
  }

  private syncSizeUi(): void {
    if (this.roundBoard) {
      this.boardHint = '圆形板直径 ' + this.cols + ' 格 · 成品约 ' +
        Math.round(this.cols * 0.5) + ' cm(标准 5mm 豆)';
    } else {
      const boards: number = Math.ceil(this.cols / 29.0) * Math.ceil(this.rows / 29.0);
      this.boardHint = '需要 29×29 拼板 ' + boards + ' 块 · 成品约 ' +
        Math.round(this.cols * 0.5) + ' × ' + Math.round(this.rows * 0.5) + ' cm(标准 5mm 豆)';
    }
  }

  private clampSize(v: number): number {
    return Math.max(MIN_SIZE, Math.min(MAX_SIZE, v));
  }

  private onSizeChip(v: number): void {
    this.cols = v;
    this.rows = v;
    this.structureChanged();
  }

  private onStep(plus: boolean, width: boolean): void {
    if (width) {
      this.cols = this.clampSize(this.cols + (plus ? 1 : -1));
    } else {
      this.rows = this.clampSize(this.rows + (plus ? 1 : -1));
    }
    this.structureChanged();
  }

  private setRound(round: boolean): void {
    if (this.roundBoard === round) {
      return;
    }
    this.roundBoard = round;
    if (round && this.rows !== this.cols) {
      this.cols = Math.min(this.cols, this.rows);
      this.rows = this.cols;
    }
    this.invalidateEdits();
    this.structureChanged();
  }

  private setStyle(s: number): void {
    const changed: boolean = this.style !== s;
    this.style = s;
    if (changed) {
      this.editMap.clear(); // 风格变了修格失效(同安卓)
      this.scheduleRegen();
    }
  }

  private resetSettings(): void {
    this.cols = 58;
    this.rows = 58;
    this.tierIdx = 2;
    this.dither = false;
    this.brightnessAdj = 0;
    this.contrastAdj = 0;
    this.saturation = 0;
    this.brickIdx = 1;
    this.abstractUsePalette = true;
    this.abstractColors = 8;
    this.abstractSnap = true;
    this.bgRemove = false;
    this.bgTolerance = 45;
    this.editCell = false;
    this.editMap.clear();
    this.updateEditsButton();
    this.roundBoard = false;
    this.syncSizeUi();
    this.style = STYLE_REALISTIC;
    this.structureChanged();
  }

  // ---------------- 撤销 / 重做(40 步,同安卓) ----------------

  private pushUndoState(): void {
    this.undoStack.push(new Map<number, number>(this.editMap));
    if (this.undoStack.length > MAX_UNDO) {
      this.undoStack.shift();
    }
    this.redoStack.length = 0;
    this.updateUndoRedoButtons();
  }

  private undoEdit(): void {
    if (this.undoStack.length === 0) {
      toast('没有可撤销的操作了');
      return;
    }
    this.redoStack.push(new Map<number, number>(this.editMap));
    this.editMap.clear();
    const last: Map<number, number> | undefined = this.undoStack.pop();
    if (last !== undefined) {
      last.forEach((v: number, k: number) => {
        this.editMap.set(k, v);
      });
    }
    this.applyEditsToUi();
    this.updateEditsButton();
    this.updateUndoRedoButtons();
  }

  private redoEdit(): void {
    if (this.redoStack.length === 0) {
      toast('没有可重做的操作了');
      return;
    }
    this.undoStack.push(new Map<number, number>(this.editMap));
    this.editMap.clear();
    const last: Map<number, number> | undefined = this.redoStack.pop();
    if (last !== undefined) {
      last.forEach((v: number, k: number) => {
        this.editMap.set(k, v);
      });
    }
    this.applyEditsToUi();
    this.updateEditsButton();
    this.updateUndoRedoButtons();
  }

  private updateUndoRedoButtons(): void {
    this.undoAlpha = this.undoStack.length === 0 ? 0.35 : 1;
    this.redoAlpha = this.redoStack.length === 0 ? 0.35 : 1;
  }

  /** 用引擎原始输出叠加手动覆盖,刷新展示与清单(同安卓 applyEditsToUi) */
  private applyEditsToUi(): void {
    if (this.rawPattern === null) {
      return;
    }
    this.holder.pattern = applyPatch(this.rawPattern, this.editMap);
    this.patternVersion++;
    this.rebuildRowsAndSummary();
  }

  private updateEditsButton(): void {
    this.editsCount = this.editMap.size;
  }

  private clearEdits(): void {
    if (this.editMap.size === 0) {
      return;
    }
    this.pushUndoState();
    this.editMap.clear();
    this.applyEditsToUi();
    this.updateEditsButton();
  }

  // ---------------- 点格回调(图纸页) ----------------

  private onCellTapFromCanvas(cellX: number, cellY: number): void {
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null) {
      return;
    }
    if (p.outsideShape(cellX, cellY)) {
      return; // 圆形板板外无格
    }
    if (this.beadAssist) {
      const idx: number = p.cellAt(cellX, cellY);
      if (idx < 0) {
        return;
      }
      const key: number = cellY * p.cols + cellX;
      if (!this.beadDone.delete(key)) {
        this.beadDone.add(key);
      }
      this.updateAssistUi();
      this.doneVersion++;
      return;
    }
    if (!this.editCell) {
      return;
    }
    this.showColorPicker(cellY * p.cols + cellX);
  }

  private rawPaletteName(idx: number): string {
    if (this.rawPattern === null || idx < 0 || idx >= this.rawPattern.palette.length) {
      return '';
    }
    return this.rawPattern.palette[idx].fullLabel();
  }

  /** 弹出选色:恢复自动匹配 / 已用颜色 / 全部色板(同安卓 showColorPicker) */
  private showColorPicker(cellIndex: number): void {
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null) {
      return;
    }
    this.pickerCell = cellIndex;
    this.pickerHeader = '当前格 ' + (cellIndex % p.cols + 1) + ',' + (Math.floor(cellIndex / p.cols) + 1);
    const opts: ColorOptVM[] = [];
    const curIdx: number = p.cellAt(cellIndex % p.cols, Math.floor(cellIndex / p.cols));
    const autoLabel: string = curIdx >= 0
      ? '自动匹配的是 ' + this.rawPaletteName(curIdx) : '该格当前为空';
    const restore: ColorOptVM = new ColorOptVM();
    restore.label = '↺ 恢复自动匹配' + (curIdx >= 0 ? '(' + autoLabel + ')' : '');
    restore.rgb = curIdx >= 0 ? p.palette[curIdx].rgb : 0xFFCCCCCC;
    restore.bold = true;
    restore.target = PICK_RESTORE;
    opts.push(restore);
    const hUsed: ColorOptVM = new ColorOptVM();
    hUsed.label = '已用颜色';
    opts.push(hUsed);
    for (const uc of p.usedColors) {
      const o: ColorOptVM = new ColorOptVM();
      o.label = uc.symbol + '  ' + uc.color.fullLabel();
      o.rgb = uc.color.rgb;
      o.target = uc.index;
      opts.push(o);
    }
    const hAll: ColorOptVM = new ColorOptVM();
    hAll.label = '全部色板';
    opts.push(hAll);
    for (let i = 0; i < p.palette.length; i++) {
      const o: ColorOptVM = new ColorOptVM();
      o.label = p.palette[i].fullLabel();
      o.rgb = p.palette[i].rgb;
      o.target = i;
      opts.push(o);
    }
    this.pickerOptions = opts;
    this.pickerShow = true;
  }

  private pickColor(target: number): void {
    const cellIndex: number = this.pickerCell;
    this.pickerShow = false;
    if (target === PICK_RESTORE) {
      this.editMap.delete(cellIndex);
    } else if (target >= 0) {
      this.editMap.set(cellIndex, target);
    }
    this.applyEditsToUi();
    this.updateEditsButton();
  }

  // ---------------- 拼豆模式(逐色辅助 + 完成度标记) ----------------

  private setBeadAssist(on: boolean): void {
    this.beadAssist = on;
    this.setKeepScreenOn(on);
    if (on) {
      const p: BeadPattern | null = this.holder.pattern;
      if (p !== null && p.usedColors.length > 0 && this.assistFocus < 0) {
        this.assistFocus = p.usedColors[0].index;
      }
      toast('已开启:图纸页点一下 = 标记已拼好,再点取消');
    }
    this.updateAssistUi();
    this.doneVersion++;
  }

  /** 切到下一种颜色(按用量从多到少循环,同安卓) */
  private cycleAssistColor(): void {
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null || p.usedColors.length === 0) {
      return;
    }
    let pos: number = -1;
    for (let i = 0; i < p.usedColors.length; i++) {
      if (p.usedColors[i].index === this.assistFocus) {
        pos = i;
        break;
      }
    }
    this.assistFocus = p.usedColors[(pos + 1) % p.usedColors.length].index;
    this.updateAssistUi();
    this.doneVersion++;
  }

  private updateAssistUi(): void {
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null) {
      return;
    }
    if (this.assistFocus < 0 || this.assistFocus >= p.palette.length) {
      this.assistText = '先生成图纸';
      return;
    }
    const c: BeadColor = p.palette[this.assistFocus];
    this.assistRgb = c.rgb;
    let order: number = 0;
    for (let i = 0; i < p.usedColors.length; i++) {
      if (p.usedColors[i].index === this.assistFocus) {
        order = i + 1;
        break;
      }
    }
    const total: number = p.counts[this.assistFocus];
    let done: number = 0;
    for (let y = 0; y < p.rows; y++) {
      for (let x = 0; x < p.cols; x++) {
        if (p.cellAt(x, y) === this.assistFocus && this.beadDone.has(y * p.cols + x)) {
          done++;
        }
      }
    }
    this.assistText = '颜色 ' + order + '/' + p.usedColors.length + ' · 本色 ' + total + ' 颗';
    const pct: number = p.totalBeads > 0 ? this.beadDone.size * 100.0 / p.totalBeads : 0;
    this.assistProgressText = '本色已完成 ' + done + ' / ' + total + ' 颗 · 总进度 ' +
      pct.toFixed(0) + '%(' + this.beadDone.size + '/' + p.totalBeads + ' 颗)';
  }

  // ---------------- 豆豆清单 ----------------

  /**
   * 缺豆替代建议:对每种"库存不够"的用量色,在当前色板里找
   * "库存富余量 ≥ 该色需求"且 Lab 色差最近的替代色(ΔE<18 才推荐)。(同安卓)
   */
  private computeSubstitutes(): Map<number, number> {
    const subs: Map<number, number> = new Map<number, number>();
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null || p.usedColors.length === 0) {
      return subs;
    }
    const pal: BeadColor[] = p.palette;
    const labs: number[][] = [];
    for (let i = 0; i < pal.length; i++) {
      labs.push(rgbToLab((0xFF000000 | pal[i].rgb) >>> 0));
    }
    for (const uc of p.usedColors) {
      const have: number | undefined = this.invMap.get(uc.color.rgb);
      if (have === undefined || have < 0 || have >= uc.count) {
        continue; // 只管缺豆的
      }
      let best: number = -1;
      let bestDe: number = Number.MAX_VALUE;
      for (let j = 0; j < pal.length; j++) {
        if (j === uc.index) {
          continue;
        }
        const inv: number | undefined = this.invMap.get(pal[j].rgb);
        if (inv === undefined || inv < 0) {
          continue;
        }
        const spare: number = inv - p.counts[j]; // 排除替代色自身图纸用量
        if (spare < uc.count) {
          continue;
        }
        const de: number = labDist2(labs[uc.index], labs[j]);
        if (de < bestDe) {
          bestDe = de;
          best = j;
        }
      }
      if (best >= 0 && bestDe < 18 * 18) {
        subs.set(uc.index, best);
      }
    }
    return subs;
  }

  private rebuildRowsAndSummary(): void {
    const p: BeadPattern | null = this.holder.pattern;
    if (p === null) {
      return;
    }
    const subs: Map<number, number> = this.computeSubstitutes();
    const list: BeadRowVM[] = [];
    let registered: number = 0;
    let enough: number = 0;
    let shortage: number = 0;
    for (const uc of p.usedColors) {
      const vm: BeadRowVM = new BeadRowVM();
      vm.rgb = uc.color.rgb;
      vm.symbol = uc.symbol;
      vm.symbolColor = textColorOn(uc.color.rgb);
      vm.name = uc.color.fullLabel();
      if (uc.color.hasOfficialCode()) {
        vm.brand = '官方色号,可直接照单';
      } else {
        const bt: string = brandTagOf(uc.color.rgb);
        vm.brand = bt.length === 0 ? '暂无对照' : '≈ ' + bt;
      }
      vm.count = fmtInt(uc.count) + ' 颗';
      const pct: number = p.totalBeads > 0 ? uc.count * 100.0 / p.totalBeads : 0;
      vm.percent = pct.toFixed(1) + '%';
      vm.pct = pct;
      const have: number | undefined = this.invMap.get(uc.color.rgb);
      if (have === undefined || have < 0) {
        vm.inv = '库存未登记';
        vm.invColor = '#8A8F98';
      } else if (have >= uc.count) {
        registered++;
        enough++;
        vm.inv = '✔ 库存够·余 ' + (have - uc.count);
        vm.invColor = '#22B57F';
      } else {
        registered++;
        shortage += uc.count - have;
        vm.inv = '缺 ' + (uc.count - have) + ' 颗';
        vm.invColor = '#F0654E';
      }
      const subIdx: number | undefined = subs.get(uc.index);
      if (subIdx !== undefined && subIdx < p.palette.length) {
        vm.sub = '💡 可用「' + p.palette[subIdx].fullLabel() + '」代替';
        vm.hasSub = true;
      }
      list.push(vm);
    }
    this.rowsVM = list;

    // 汇总头部(文案照抄安卓 updateSummary)
    let sb: string = '总用豆:' + fmtInt(p.totalBeads) + ' 颗\n';
    sb += '颜色:' + p.usedColors.length + ' 种 · 29×29 拼板:' + p.boardsNeeded() + ' 块\n';
    sb += '成品约:' + Math.round(p.cols * 0.5) + ' × ' + Math.round(p.rows * 0.5) + ' cm(标准 5mm 豆)';
    if (p.emptyCount > 0) {
      sb += '\n空格:' + fmtInt(p.emptyCount) + ' 格(不放置)';
    }
    sb += '\n约重 ' + fmtInt(Math.round(p.totalBeads * 0.024)) + ' g · 参考成本 ¥' +
      (p.totalBeads * BEAD_UNIT_PRICE).toFixed(2) + '(点此改单价)';
    if (registered > 0) {
      sb += '\n🎒 豆仓:' + enough + '/' + p.usedColors.length + ' 色够用 · 还需补 ' + fmtInt(shortage) + ' 颗';
    }
    this.summaryText = sb;
  }

  /** 读当前色板全部库存(Preferences 持久化在 core BeadInventory) */
  private async refreshInventory(): Promise<void> {
    const ctx: common.UIAbilityContext | null = this.uiCtx;
    if (ctx === null) {
      return;
    }
    const pal: BeadColor[] = getPalette(this.tierIdx);
    try {
      const vals: number[] = await Promise.all(pal.map((c: BeadColor): Promise<number> =>
        invGet(ctx, c.rgb)));
      for (let i = 0; i < pal.length; i++) {
        this.invMap.set(pal[i].rgb, vals[i]);
      }
      this.rebuildRowsAndSummary();
    } catch (e) {
      // 库存读取失败按未登记处理
    }
  }

  private async showInventoryDialog(): Promise<void> {
    const ctx: common.UIAbilityContext | null = this.uiCtx;
    if (ctx === null) {
      return;
    }
    const pal: BeadColor[] = getPalette(this.tierIdx);
    try {
      const vals: number[] = await Promise.all(pal.map((c: BeadColor): Promise<number> =>
        invGet(ctx, c.rgb)));
      const rows: InvRowVM[] = [];
      for (let i = 0; i < pal.length; i++) {
        const r: InvRowVM = new InvRowVM();
        r.rgb = pal[i].rgb;
        r.label = pal[i].fullLabel();
        r.draft = vals[i] < 0 ? '' : String(vals[i]);
        rows.push(r);
      }
      this.invRows = rows;
      this.invTitle = '🎒 豆仓库存(当前色板 ' + pal.length + ' 色)';
      this.invShow = true;
    } catch (e) {
      toast('库存读取失败');
    }
  }

  private async saveInventory(): Promise<void> {
    const ctx: common.UIAbilityContext | null = this.uiCtx;
    if (ctx === null) {
      return;
    }
    const tasks: Array<Promise<void>> = [];
    for (const r of this.invRows) {
      const t: string = r.draft.trim();
      if (t.length === 0) {
        continue;
      }
      const v: number = parseInt(t, 10);
      if (Number.isNaN(v) || v < 0) {
        continue;
      }
      tasks.push(invSet(ctx, r.rgb, v));
    }
    await Promise.all(tasks);
    this.invShow = false;
    await this.refreshInventory();
    toast('豆仓已更新,豆单已标注缺口');
  }

  // ---------------- 去水印(框选修复) ----------------

  private async showWatermarkDialog(): Promise<void> {
    if (this.srcPx === null) {
      toast('还没有照片');
      return;
    }
    const w: number = this.srcW;
    const h: number = this.srcH;
    const scale: number = Math.min(WM_MAX_W / w, WM_MAX_H / h);
    this.wmDw = Math.max(1, Math.round(w * scale));
    this.wmDh = Math.max(1, Math.round(h * scale));
    this.wmX0 = -1;
    this.wmY0 = -1;
    this.wmX1 = -1;
    this.wmY1 = -1;
    try {
      // srcPx 内存字节序为 B,G,R,A(BGRA),与 BGRA_8888 一致
      const initOpts: image.InitializationOptions = {
        size: { width: w, height: h },
        pixelFormat: image.PixelMapFormat.BGRA_8888
      };
      this.wmImage = await image.createPixelMap((this.srcPx as Uint32Array).buffer as ArrayBuffer, initOpts);
      this.wmShow = true;
    } catch (e) {
      toast('图片读取失败:' + errText(e));
    }
  }

  private wmDraw(): void {
    this.wmCtx.clearRect(0, 0, this.wmDw, this.wmDh);
    if (this.wmX0 < 0 || this.wmY0 < 0) {
      return;
    }
    const left: number = Math.min(this.wmX0, this.wmX1);
    const top: number = Math.min(this.wmY0, this.wmY1);
    const rw: number = Math.abs(this.wmX1 - this.wmX0);
    const rh: number = Math.abs(this.wmY1 - this.wmY0);
    this.wmCtx.fillStyle = cssColor(0x3322B57F);
    this.wmCtx.fillRect(left, top, rw, rh);
    this.wmCtx.strokeStyle = cssColor(0xFF22B57F);
    this.wmCtx.lineWidth = 2;
    this.wmCtx.strokeRect(left, top, rw, rh);
  }

  private wmPanStart(e: GestureEvent): void {
    if (e.fingerList.length > 0) {
      this.wmX0 = e.fingerList[0].localX;
      this.wmY0 = e.fingerList[0].localY;
    } else {
      this.wmX0 = 0;
      this.wmY0 = 0;
    }
    this.wmX1 = this.wmX0;
    this.wmY1 = this.wmY0;
    this.wmDraw();
  }

  private wmPanUpdate(e: GestureEvent): void {
    if (this.wmX0 < 0) {
      return;
    }
    this.wmX1 = this.wmX0 + e.offsetX;
    this.wmY1 = this.wmY0 + e.offsetY;
    this.wmDraw();
  }

  private applyWatermarkFix(): void {
    if (this.srcPx === null) {
      return;
    }
    const left: number = Math.min(this.wmX0, this.wmX1);
    const top: number = Math.min(this.wmY0, this.wmY1);
    const rw: number = Math.abs(this.wmX1 - this.wmX0);
    const rh: number = Math.abs(this.wmY1 - this.wmY0);
    if (this.wmX0 < 0 || rw < 8 || rh < 8) {
      toast('先在图上拖一个框盖住水印');
      return;
    }
    // 视口坐标 -> 像素坐标,外扩 2px 盖住水印抗锯齿边(同安卓)
    const bx0: number = Math.max(0, Math.round(left * this.srcW / this.wmDw) - 2);
    const by0: number = Math.max(0, Math.round(top * this.srcH / this.wmDh) - 2);
    const bx1: number = Math.min(this.srcW - 1, Math.round((left + rw) * this.srcW / this.wmDw) + 2);
    const by1: number = Math.min(this.srcH - 1, Math.round((top + rh) * this.srcH / this.wmDh) + 2);
    this.showLoading(true, '正在修复水印…');
    const seq: number = ++this.genSeq;
    setTimeout(() => {
      if (seq !== this.genSeq || this.srcPx === null) {
        return;
      }
      try {
        const px: Uint32Array = this.srcPx.slice();
        removeWatermark(px, this.srcW, this.srcH, bx0, by0, bx1, by1);
        this.srcPx = px;
        this.wmHasEdited = true;
        this.wmShow = false;
        this.showLoading(false);
        this.regenerate();
        toast('已修复,还有水印可再框一次');
      } catch (e) {
        this.showLoading(false);
        toast('修复失败:' + errText(e));
      }
    }, 30);
  }

  /** 恢复为原始照片(去水印等改动后可一键还原) */
  private restoreOriginal(): void {
    if (this.origPx === null || this.srcPx === this.origPx) {
      toast('当前已是原始照片');
      return;
    }
    this.srcPx = this.origPx;
    this.wmHasEdited = false;
    this.regenerate();
  }

  // ---------------- 照片变换(镜像/旋转,手动修格跟着几何映射) ----------------

  private transformSource(kind: number): void {
    if (this.srcPx === null) {
      return;
    }
    const w: number = this.srcW;
    const h: number = this.srcH;
    const px: Uint32Array = this.srcPx;
    const out: Uint32Array = new Uint32Array(w * h);
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const v: number = px[y * w + x];
        if (kind === 0) {
          out[y * w + (w - 1 - x)] = v; // 左右镜像
        } else if (kind === 1) {
          out[(h - 1 - y) * w + x] = v; // 上下镜像
        } else {
          out[x * h + (h - 1 - y)] = v; // 右转 90°(新宽 = h)
        }
      }
    }
    const remap: Map<number, number> = this.remapEdits(kind, w, h);
    this.editMap.clear();
    remap.forEach((v: number, k: number) => {
      this.editMap.set(k, v);
    });
    // 变换后旧快照坐标全部失效,撤销历史作废(同安卓)
    this.undoStack.length = 0;
    this.redoStack.length = 0;
    this.updateUndoRedoButtons();
    if (kind === 2) {
      const t: number = this.cols;
      this.cols = this.rows;
      this.rows = t;
    }
    this.srcPx = out;
    this.syncSizeUi();
    this.scheduleRegen();
    this.updateEditsButton();
  }

  /** 把手动修格覆盖按几何变换映到新坐标;旋转返回的键按新宽度编码(同安卓) */
  private remapEdits(kind: number, w: number, h: number): Map<number, number> {
    const out: Map<number, number> = new Map<number, number>();
    this.editMap.forEach((value: number, key: number) => {
      const x: number = key % w;
      const y: number = Math.floor(key / w);
      let nx: number = x;
      let ny: number = y;
      if (kind === 0) {
        nx = w - 1 - x;
      } else if (kind === 1) {
        ny = h - 1 - y;
      } else {
        nx = h - 1 - y;
        ny = x;
      }
      const newW: number = kind === 2 ? h : w;
      out.set(ny * newW + nx, value);
    });
    return out;
  }

  // ---------------- 通用小控件 ----------------

  /** 区块标题(圆点 + 加粗) */
  @Builder
  SecTitle(title: string) {
    Row() {
      Circle({ width: 10, height: 10 }).fill('#22B57F')
      Text(title)
        .fontSize(13)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1F2430')
        .margin({ left: 7 })
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }

  /** 药丸 chip(选中 = 薄荷实心白字;等分占满一行,同安卓 weight=1) */
  @Builder
  Chip(label: string, selected: boolean, fontSizeNum: number, heightNum: number, onTap: () => void) {
    Text(label)
      .fontSize(fontSizeNum)
      .fontWeight(selected ? FontWeight.Bold : FontWeight.Normal)
      .fontColor(selected ? '#FFFFFF' : '#8A8F98')
      .textAlign(TextAlign.Center)
      .maxLines(1)
      .height(heightNum)
      .layoutWeight(1)
      .borderRadius(heightNum / 2)
      .backgroundColor(selected ? '#22B57F' : '#F2FFFFFF')
      .border({ width: 1, color: selected ? '#22B57F' : '#D9DEE5' })
      .onClick(() => onTap())
  }

  /** 全宽 chip 按钮 */
  @Builder
  FullChip(label: string, fontSizeNum: number, heightNum: number, onTap: () => void) {
    Text(label)
      .fontSize(fontSizeNum)
      .fontColor('#8A8F98')
      .textAlign(TextAlign.Center)
      .width('100%')
      .height(heightNum)
      .borderRadius(heightNum / 2)
      .backgroundColor('#F2FFFFFF')
      .border({ width: 1, color: '#D9DEE5' })
      .shadow({ radius: 4, color: '#14000000', offsetY: 1 })
      .onClick(() => onTap())
  }

  /** 宽高步进小按钮 */
  @Builder
  StepBtn(sym: string, onTap: () => void) {
    Text(sym)
      .fontSize(16)
      .fontWeight(FontWeight.Bold)
      .fontColor('#8A8F98')
      .textAlign(TextAlign.Center)
      .width(38)
      .height(32)
      .borderRadius(16)
      .backgroundColor('#F2FFFFFF')
      .border({ width: 1, color: '#D9DEE5' })
      .onClick(() => onTap())
  }

  /** 开关行:标题 + 描述 + Toggle */
  @Builder
  SwitchRow(title: string, desc: string, isOn: boolean, onChange: (v: boolean) => void) {
    Row() {
      Column() {
        Text(title)
          .fontSize(14)
          .fontColor('#1F2430')
        if (desc.length > 0) {
          Text(desc)
            .fontSize(11)
            .fontColor('#8A8F98')
            .margin({ top: 2 })
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      Toggle({ type: ToggleType.Switch, isOn: isOn })
        .selectedColor('#22B57F')
        .onChange((v: boolean) => onChange(v))
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .margin({ top: 12 })
  }

  /** 滑杆行:标签 + 数值 + Slider */
  @Builder
  SliderRow(title: string, valueText: string, value: number, maxNum: number, onChange: (v: number) => void) {
    Row() {
      Text(title)
        .fontSize(14)
        .fontColor('#1F2430')
        .layoutWeight(1)
      Text(valueText)
        .fontSize(13)
        .fontWeight(FontWeight.Bold)
        .fontColor('#22B57F')
        .width(40)
        .textAlign(TextAlign.End)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    Slider({ value: value, min: 0, max: maxNum, step: 1 })
      .width('100%')
      .trackColor('#ECEFF3')
      .selectedColor('#22B57F')
      .onChange((v: number) => onChange(Math.round(v)))
  }

  // ---------------- 豆豆清单子视图 ----------------

  @Builder
  BeadRowCard(vm: BeadRowVM) {
    Row() {
      Stack() {
        Circle({ width: 42, height: 42 }).fill(hexColor(vm.rgb))
        Text(vm.symbol)
          .fontSize(15)
          .fontWeight(FontWeight.Bold)
          .fontColor(hexColor(vm.symbolColor))
      }
      .width(42)
      .height(42)

      Column() {
        Text(vm.name)
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1F2430')
          .maxLines(1)
        Text(vm.brand)
          .fontSize(10)
          .fontColor('#8A8F98')
          .maxLines(1)
          .margin({ top: 2 })
        if (vm.hasSub) {
          Text(vm.sub)
            .fontSize(10)
            .fontWeight(FontWeight.Bold)
            .fontColor('#2EA8A0')
            .maxLines(2)
            .margin({ top: 2 })
        }
        Stack({ alignContent: Alignment.Start }) {
          Row()
            .width('100%')
            .height(5)
            .backgroundColor('#ECEFF3')
            .borderRadius(2.5)
          Row()
            .width(vm.pct + '%')
            .height(5)
            .backgroundColor(hexColor(vm.rgb))
            .borderRadius(2.5)
        }
        .width('100%')
        .margin({ top: 6 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })

      Column() {
        Text(vm.count)
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1F2430')
        Text(vm.percent)
          .fontSize(11)
          .fontColor('#8A8F98')
          .margin({ top: 2 })
        Text(vm.inv)
          .fontSize(10)
          .fontWeight(FontWeight.Bold)
          .fontColor(vm.invColor)
          .margin({ top: 2 })
      }
      .width(78)
      .alignItems(HorizontalAlign.End)
      .margin({ left: 10 })
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
    .padding(10)
    .margin({ top: 7 })
    .backgroundColor('#F2FFFFFF')
    .borderRadius(24)
    .border({ width: 1, color: '#66FFFFFF' })
    .shadow({ radius: 8, color: '#1AE8EBF0', offsetY: 2 })
  }

  @Builder
  BeadListHeader() {
    Column() {
      Text('用量统计')
        .fontSize(15)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1F2430')
      Text(this.summaryText)
        .fontSize(13)
        .fontColor('#8A8F98')
        .lineHeight(20)
        .width('100%')
        .margin({ top: 6 })
        .onClick(() => toast('阶段 2 开放'))
      Text('🎒 豆仓库存(登记手头豆子,自动算缺口)')
        .fontSize(12)
        .fontWeight(FontWeight.Bold)
        .fontColor('#8A8F98')
        .textAlign(TextAlign.Center)
        .width('100%')
        .height(38)
        .borderRadius(19)
        .backgroundColor('#F2FFFFFF')
        .border({ width: 1, color: '#D9DEE5' })
        .margin({ top: 10 })
        .onClick(() => {
          this.showInventoryDialog();
        })
      Text('💡 建议每种颜色多备 10% 左右的用量\n🏷️ 品牌色号为近似对照,下单前请核对官方色卡')
        .fontSize(12)
        .fontColor('#B26A00')
        .width('100%')
        .margin({ top: 8 })
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
    .padding(16)
    .backgroundColor('#F2FFFFFF')
    .borderRadius(24)
    .border({ width: 1, color: '#66FFFFFF' })
    .shadow({ radius: 8, color: '#1AE8EBF0', offsetY: 2 })
  }

  // ---------------- 页面 ----------------

  build() {
    Stack() {
      // 柔焦粉彩底
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor('#F6F3EE')
      Circle({ width: 320, height: 320 })
        .fill('#E3F5EC')
        .opacity(0.7)
        .blur(60)
        .position({ x: '-14%', y: '-8%' })
      Circle({ width: 340, height: 340 })
        .fill('#FFE9D9')
        .opacity(0.6)
        .blur(70)
        .position({ x: '62%', y: '-12%' })
      Circle({ width: 340, height: 340 })
        .fill('#E7E1FA')
        .opacity(0.6)
        .blur(70)
        .position({ x: '-16%', y: '60%' })
      Circle({ width: 300, height: 300 })
        .fill('#DDEBFA')
        .opacity(0.7)
        .blur(60)
        .position({ x: '66%', y: '64%' })

      Column() {
        // ---- 渐变顶栏 ----
        Row() {
          Text('‹')
            .fontSize(30)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .padding({ left: 16, right: 8 })
            .onClick(() => {
              router.back();
            })
          Text('拼豆编辑')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .layoutWeight(1)
          Text('↺')
            .fontSize(22)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .opacity(this.undoAlpha)
            .padding({ left: 10, right: 6 })
            .onClick(() => {
              this.undoEdit();
            })
          Text('↪')
            .fontSize(22)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .opacity(this.redoAlpha)
            .padding({ left: 6, right: 8 })
            .onClick(() => {
              this.redoEdit();
            })
          Text('⋮')
            .fontSize(26)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .padding({ left: 12, right: 18 })
            .bindMenu(this.exportMenu)
        }
        .width('100%')
        .height(58)
        .backgroundColor('#B3FFFFFF')
        .alignItems(VerticalAlign.Center)
        .shadow({ radius: 8, color: '#1A000000', offsetY: 2 })

        // ---- 分段式标签 ----
        Row() {
          this.TabChip('效果图', 0)
          this.TabChip('图纸', 1)
          this.TabChip('豆豆清单', 2)
        }
        .width('100%')
        .height(42)
        .margin({ left: 14, right: 14, top: 12 })
        .padding(4)
        .backgroundColor('#F2FFFFFF')
        .borderRadius(21)
        .border({ width: 1, color: '#ECEFF3' })
        .alignItems(VerticalAlign.Center)

        // ---- 预览画布(浮起面板) ----
        Stack() {
          PatternCanvas({
            holder: this.holder,
            mode: this.canvasMode,
            showSymbols: this.showSymbols,
            showGrid: this.showGrid,
            assistOn: this.beadAssist,
            assistFocus: this.assistFocus,
            patternVersion: this.patternVersion,
            doneVersion: this.doneVersion,
            onCellTap: {
              onCellTap: (cellX: number, cellY: number): void => {
                this.onCellTapFromCanvas(cellX, cellY);
              }
            } as CellTapListener
          })
        }
        .width('100%')
        .layoutWeight(1)
        .margin({ left: 10, right: 10, top: 10 })
        .backgroundColor('#F2FFFFFF')
        .borderRadius(20)
        .border({ width: 1.5, color: '#ECEFF3' })
        .shadow({ radius: 12, color: '#1AE8EBF0', offsetY: 4 })
        .clip(true)
        .visibility(this.activeTab === 2 ? Visibility.None : Visibility.Visible)

        // ---- 豆豆清单 ----
        Column() {
          Scroll() {
            Column() {
              this.BeadListHeader()
              ForEach(this.rowsVM, (vm: BeadRowVM) => {
                this.BeadRowCard(vm)
              }, (vm: BeadRowVM, idx: number) => idx.toString() + '_' + vm.name)
            }
            .width('100%')
            .padding({ left: 10, right: 10, bottom: 14, top: 0 })
          }
          .width('100%')
          .height('100%')
          .scrollBar(BarState.Off)
          .align(Alignment.Top)
        }
        .width('100%')
        .layoutWeight(1)
        .visibility(this.activeTab === 2 ? Visibility.Visible : Visibility.None)

        // ---- 参数设置区 ----
        Scroll() {
          Column() {
            this.BuildControls()
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 4, bottom: 20 })
        }
        .width('100%')
        .layoutWeight(1.15)
        .scrollBar(BarState.Off)
        .align(Alignment.Top)
        .visibility(this.activeTab === 2 ? Visibility.None : Visibility.Visible)
      }
      .width('100%')
      .height('100%')

      // ---- 全屏加载提示 ----
      if (this.loading) {
        Column() {
          LoadingProgress()
            .width(40)
            .height(40)
            .color('#FFFFFF')
          Text(this.loadingText)
            .fontSize(13)
            .fontColor('#FFFFFF')
            .margin({ top: 8 })
        }
        .padding(18)
        .backgroundColor('#99000000')
        .borderRadius(20)
      }

      // ---- 点格选色对话框 ----
      if (this.pickerShow) {
        Column() {
          Column() {
            Text('选择这一格的豆色')
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
              .fontColor('#1F2430')
            Text(this.pickerHeader)
              .fontSize(12)
              .fontWeight(FontWeight.Bold)
              .fontColor('#8A8F98')
              .margin({ top: 4 })
            Scroll() {
              Column() {
                ForEach(this.pickerOptions, (o: ColorOptVM) => {
                  if (o.target === -1) {
                    Text(o.label)
                      .fontSize(12)
                      .fontWeight(FontWeight.Bold)
                      .fontColor('#1F2430')
                      .width('100%')
                      .margin({ top: 10, bottom: 2 })
                  } else {
                    Row() {
                      Circle({ width: 24, height: 24 }).fill(hexColor(o.rgb))
                      Text(o.label)
                        .fontSize(14)
                        .fontWeight(o.bold ? FontWeight.Bold : FontWeight.Normal)
                        .fontColor('#1F2430')
                        .layoutWeight(1)
                        .margin({ left: 8 })
                        .maxLines(2)
                    }
                    .width('100%')
                    .alignItems(VerticalAlign.Center)
                    .padding({ top: 5, bottom: 5 })
                    .onClick(() => {
                      this.pickColor(o.target);
                    })
                  }
                }, (o: ColorOptVM, idx: number) => idx.toString() + o.label)
              }
              .width('100%')
              .alignItems(HorizontalAlign.Start)
            }
            .layoutWeight(1)
            .align(Alignment.Top)
            .margin({ top: 8 })
            Text('取消')
              .fontSize(14)
              .fontWeight(FontWeight.Bold)
              .fontColor('#8A8F98')
              .textAlign(TextAlign.Center)
              .width('100%')
              .height(40)
              .borderRadius(20)
              .backgroundColor('#F2FFFFFF')
              .border({ width: 1, color: '#D9DEE5' })
              .margin({ top: 12 })
              .onClick(() => {
                this.pickerShow = false;
              })
          }
          .padding(16)
          .backgroundColor('#FFFFFF')
          .borderRadius(24)
          .width('86%')
          .height('72%')
        }
        .width('100%')
        .height('100%')
        .backgroundColor('#66000000')
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }

      // ---- 豆仓库存对话框 ----
      if (this.invShow) {
        Column() {
          Column() {
            Text(this.invTitle)
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
              .fontColor('#1F2430')
            List() {
              ForEach(this.invRows, (r: InvRowVM, idx: number) => {
                ListItem() {
                  Row() {
                    Circle({ width: 26, height: 26 }).fill(hexColor(r.rgb))
                    Text(r.label)
                      .fontSize(13)
                      .fontColor('#1F2430')
                      .layoutWeight(1)
                      .margin({ left: 10 })
                      .maxLines(1)
                    TextInput({ text: r.draft, placeholder: '0' })
                      .width(88)
                      .height(36)
                      .fontSize(13)
                      .type(InputType.Number)
                      .onChange((v: string) => {
                        r.draft = v;
                      })
                  }
                  .width('100%')
                  .alignItems(VerticalAlign.Center)
                  .padding({ top: 5, bottom: 5, left: 10, right: 10 })
                }
              }, (r: InvRowVM, idx: number) => idx.toString())
            }
            .layoutWeight(1)
            .divider({ strokeWidth: 0.5, color: '#ECEFF3', startMargin: 10, endMargin: 10 })
            .margin({ top: 8 })
            Row() {
              Text('取消')
                .fontSize(14)
                .fontColor('#8A8F98')
                .textAlign(TextAlign.Center)
                .layoutWeight(1)
                .height(40)
                .borderRadius(20)
                .backgroundColor('#F2FFFFFF')
                .border({ width: 1, color: '#D9DEE5' })
                .onClick(() => {
                  this.invShow = false;
                })
              Text('保存')
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor('#FFFFFF')
                .textAlign(TextAlign.Center)
                .layoutWeight(1)
                .height(40)
                .borderRadius(20)
                .linearGradient({ angle: 180, colors: [['#3ECF96', 0], ['#17A673', 1]] })
                .margin({ left: 10 })
                .onClick(() => {
                  this.saveInventory();
                })
            }
            .width('100%')
            .margin({ top: 12 })
            .alignItems(VerticalAlign.Center)
          }
          .padding(16)
          .backgroundColor('#FFFFFF')
          .borderRadius(24)
          .width('86%')
          .height('76%')
        }
        .width('100%')
        .height('100%')
        .backgroundColor('#66000000')
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }

      // ---- 去水印全屏对话框 ----
      if (this.wmShow) {
        Column() {
          Column() {
            Text('🩹 去水印')
              .fontSize(17)
              .fontWeight(FontWeight.Bold)
              .fontColor('#1F2430')
              .width('100%')
            Text('用来清除别的 APP 出图自带的水印/角标。\n用法:在图上拖一个框,把水印完整盖住、框紧贴水印,点「修复」;\n不要圈进大块背景。图上有多个水印时,修完一次再框下一处即可。')
              .fontSize(12)
              .fontColor('#8A8F98')
              .lineHeight(18)
              .width('100%')
              .margin({ top: 6 })
            Stack() {
              if (this.wmImage !== null) {
                Image(this.wmImage)
                  .width(this.wmDw)
                  .height(this.wmDh)
                  .objectFit(ImageFit.Fill)
              }
              Canvas(this.wmCtx)
                .width(this.wmDw)
                .height(this.wmDh)
                .onReady(() => {
                  this.wmDraw();
                })
                .gesture(
                  PanGesture({ fingers: 1, distance: 3 })
                    .onActionStart((e: GestureEvent) => {
                      this.wmPanStart(e);
                    })
                    .onActionUpdate((e: GestureEvent) => {
                      this.wmPanUpdate(e);
                    })
                )
            }
            .alignContent(Alignment.TopStart)
            .margin({ top: 12 })

            Row() {
              Text('取消')
                .fontSize(14)
                .fontColor('#8A8F98')
                .textAlign(TextAlign.Center)
                .layoutWeight(1)
                .height(40)
                .borderRadius(20)
                .backgroundColor('#F2FFFFFF')
                .border({ width: 1, color: '#D9DEE5' })
                .onClick(() => {
                  this.wmShow = false;
                })
              Text('修复')
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor('#FFFFFF')
                .textAlign(TextAlign.Center)
                .layoutWeight(1)
                .height(40)
                .borderRadius(20)
                .linearGradient({ angle: 180, colors: [['#3ECF96', 0], ['#17A673', 1]] })
                .margin({ left: 10 })
                .onClick(() => {
                  this.applyWatermarkFix();
                })
            }
            .width('100%')
            .margin({ top: 14 })
            .alignItems(VerticalAlign.Center)
          }
          .padding(16)
          .backgroundColor('#FFFFFF')
          .borderRadius(24)
          .width('90%')
          .constraintSize({ maxHeight: '88%' })
        }
        .width('100%')
        .height('100%')
        .backgroundColor('#66000000')
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }
    }
    .width('100%')
    .height('100%')
  }

  /** Tab 胶囊(选中 = 实心薄荷胶囊) */
  @Builder
  TabChip(label: string, which: number) {
    Text(label)
      .fontSize(13)
      .fontWeight(FontWeight.Bold)
      .fontColor(this.activeTab === which ? '#FFFFFF' : '#8A8F98')
      .textAlign(TextAlign.Center)
      .height(34)
      .layoutWeight(1)
      .margin({ right: which < 2 ? 4 : 0 })
      .backgroundColor(this.activeTab === which ? '#22B57F' : '#00000000')
      .borderRadius(17)
      .onClick(() => {
        this.selectTab(which);
      })
  }

  /** 参数区内容(独立 Builder 保持 build 清爽) */
  @Builder
  BuildControls() {
    // 画幅尺寸
    Column() {
      this.SecTitle('画幅尺寸(格数)')
      Text(this.boardHint)
        .fontSize(11)
        .fontColor('#8A8F98')
        .margin({ top: 3 })
      Row({ space: 7 }) {
        this.Chip('▭ 方形板', !this.roundBoard, 13, 34, (): void => this.setRound(false))
        this.Chip('⬤ 圆形板', this.roundBoard, 13, 34, (): void => this.setRound(true))
      }
      .width('100%')
      .margin({ top: 8 })
      .alignItems(VerticalAlign.Center)

      Row({ space: 7 }) {
        ForEach(SIZE_LABELS, (label: string, idx: number) => {
          this.Chip(label, this.cols === (idx + 1) * 29 && this.rows === (idx + 1) * 29, 12, 36,
            (): void => this.onSizeChip((idx + 1) * 29))
        }, (label: string) => label)
      }
      .width('100%')
      .margin({ top: 10 })
      .alignItems(VerticalAlign.Center)

      if (!this.roundBoard) {
        Row() {
          Text('宽')
            .fontSize(13)
            .fontColor('#8A8F98')
          this.StepBtn('−', (): void => this.onStep(false, true))
          Text(this.cols.toString())
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .width(44)
            .textAlign(TextAlign.Center)
          this.StepBtn('+', (): void => this.onStep(true, true))
          Column().layoutWeight(1)
          Text('高')
            .fontSize(13)
            .fontColor('#8A8F98')
          this.StepBtn('−', (): void => this.onStep(false, false))
          Text(this.rows.toString())
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .width(44)
            .textAlign(TextAlign.Center)
          this.StepBtn('+', (): void => this.onStep(true, false))
        }
        .width('100%')
        .margin({ top: 10 })
        .alignItems(VerticalAlign.Center)
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)

    // 色板与图纸风格
    Column() {
      this.SecTitle('色板与图纸风格')
      Column() {
        Row() {
          Text('豆豆色板')
            .fontSize(14)
            .fontColor('#1F2430')
          Select(this.paletteOptions)
            .selected(this.tierIdx)
            .value(this.paletteOptions.length > this.tierIdx ? PALETTE_NAMES[this.tierIdx] : '')
            .font({ size: 14 })
            .layoutWeight(1)
            .margin({ left: 12 })
            .height(42)
            .onSelect((index: number) => {
              this.tierIdx = index;
              this.editMap.clear(); // 色板体系变了,修格下标失效
              this.structureChanged();
              this.refreshInventory();
            })
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)

        Row({ space: 5 }) {
          ForEach(LIMIT_LABELS, (label: string, idx: number) => {
            this.Chip(label, this.maxColorsIdx === idx, 12, 34, (): void => {
              if (this.maxColorsIdx !== idx) {
                this.maxColorsIdx = idx;
                this.scheduleRegen();
              }
            })
          }, (label: string) => label)
        }
        .width('100%')
        .margin({ top: 10 })
        .alignItems(VerticalAlign.Center)

        Text('色数上限:自动合并最接近的颜色,少买几种豆(写实模式生效)')
          .fontSize(10)
          .fontColor('#8A8F98')
          .margin({ top: 4 })

        Row({ space: 7 }) {
          this.Chip('写实', this.style === STYLE_REALISTIC, 13, 34,
            (): void => this.setStyle(STYLE_REALISTIC))
          this.Chip('抽象', this.style === STYLE_ABSTRACT, 13, 34,
            (): void => this.setStyle(STYLE_ABSTRACT))
        }
        .width('100%')
        .margin({ top: 12 })
        .alignItems(VerticalAlign.Center)

        // 抽象画参数(暖色子面板)
        if (this.style === STYLE_ABSTRACT) {
          Column() {
            Text('抽象程度(砖块大小)')
              .fontSize(14)
              .fontColor('#1F2430')
            Text('一小片像素合并成同一种颜色,越大越像积木')
              .fontSize(11)
              .fontColor('#8A8F98')
              .margin({ top: 2 })
            Row({ space: 6 }) {
              ForEach(BRICK_LABELS, (label: string, idx: number) => {
                this.Chip(label, this.brickIdx === idx, 12, 34, (): void => {
                  this.brickIdx = idx;
                  this.scheduleRegen();
                })
              }, (label: string) => label)
            }
            .width('100%')
            .margin({ top: 8 })
            .alignItems(VerticalAlign.Center)

            this.SwitchRow('限定主色调(海报感)', '关闭后使用整套拼豆色板,色彩更自然',
              this.abstractUsePalette, (v: boolean): void => {
                this.abstractUsePalette = v;
                this.scheduleRegen();
              })
            if (this.abstractUsePalette) {
              Row() {
                Column() {
                  Text('提取主色数量')
                    .fontSize(14)
                    .fontColor('#1F2430')
                  Text('自动抓取照片里最主要的几种颜色')
                    .fontSize(11)
                    .fontColor('#8A8F98')
                    .margin({ top: 2 })
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Start)
                Select(this.absOptions)
                  .selected(this.indexOfAbstractColors())
                  .font({ size: 14 })
                  .width(96)
                  .height(42)
                  .onSelect((index: number) => {
                    this.abstractColors = ABSTRACT_CHOICES[index];
                    this.scheduleRegen();
                  })
              }
              .width('100%')
              .alignItems(VerticalAlign.Center)
              .margin({ top: 12 })

              this.SwitchRow('颜色吸附到拼豆色板', '开启后可直接按清单买豆;关闭则用照片原色',
                this.abstractSnap, (v: boolean): void => {
                  this.abstractSnap = v;
                  this.scheduleRegen();
                })
            }
          }
          .width('100%')
          .padding(12)
          .margin({ top: 12 })
          .backgroundColor('#F1F4F6')
          .borderRadius(16)
          .alignItems(HorizontalAlign.Start)
        }

        this.SwitchRow('抖动(更接近照片)', '颜色过渡更自然,但图纸更复杂',
          this.dither, (v: boolean): void => {
            this.dither = v;
            this.scheduleRegen();
          })
        this.SwitchRow('图纸显示符号', '', this.showSymbols, (v: boolean): void => {
          this.showSymbols = v;
        })
        this.SwitchRow('显示网格线', '', this.showGrid, (v: boolean): void => {
          this.showGrid = v;
        })
      }
      .width('100%')
      .padding(14)
      .margin({ top: 8 })
      .backgroundColor('#F2FFFFFF')
      .borderRadius(24)
      .border({ width: 1, color: '#66FFFFFF' })
      .shadow({ radius: 10, color: '#1AE8EBF0', offsetY: 3 })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .margin({ top: 18 })
    .alignItems(HorizontalAlign.Start)

    // 照片预处理
    Column() {
      this.SecTitle('照片预处理')
      Column() {
        this.SwitchRow('一键去背景', '适合纯色背景照片,抠掉后自动留空格',
          this.bgRemove, (v: boolean): void => {
            this.bgRemove = v;
            this.scheduleRegen();
          })
        if (this.bgRemove) {
          Column() {
            this.SliderRow('容差(抠得多少)', this.bgTolerance + '%', this.bgTolerance, 100,
              (v: number): void => {
                this.bgTolerance = v;
                this.scheduleRegen();
              })
            Text('抠不干净往右调,把人物吃掉了往回调;背景有渐变也能顺着抠掉')
              .fontSize(10)
              .fontColor('#8A8F98')
              .margin({ top: 2 })
          }
          .width('100%')
          .margin({ top: 10 })
          .alignItems(HorizontalAlign.Start)
        }

        this.FullChip('🩹 去水印(框选水印,自动修复)', 11, 34, (): void => {
          this.showWatermarkDialog();
        })
        if (this.wmHasEdited) {
          this.FullChip('还原原始照片', 13, 40, (): void => {
            this.restoreOriginal();
          })
        }

        Row({ space: 6 }) {
          this.Chip('⇋ 左右镜像', false, 11, 34, (): void => this.transformSource(0))
          this.Chip('↕ 上下镜像', false, 11, 34, (): void => this.transformSource(1))
          this.Chip('⟳ 右转90°', false, 11, 34, (): void => this.transformSource(2))
        }
        .width('100%')
        .margin({ top: 12 })
        .alignItems(VerticalAlign.Center)

        Text('镜像/旋转后图纸会重新生成,手动修过的格子也会跟着转')
          .fontSize(10)
          .fontColor('#8A8F98')
          .margin({ top: 6 })
      }
      .width('100%')
      .padding(14)
      .margin({ top: 8 })
      .backgroundColor('#F2FFFFFF')
      .borderRadius(24)
      .border({ width: 1, color: '#66FFFFFF' })
      .shadow({ radius: 10, color: '#1AE8EBF0', offsetY: 3 })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .margin({ top: 18 })
    .alignItems(HorizontalAlign.Start)

    // 手动修格 / 拼豆模式
    Column() {
      this.SecTitle('手动修格(图纸页点格子换色)')
      Column() {
        this.SwitchRow('开启点格修改', '在「图纸」标签页单击任意格子弹出选色',
          this.editCell, (v: boolean): void => {
            this.editCell = v;
            if (v && this.beadAssist) {
              this.setBeadAssist(false);
            }
            toast(v ? '已开启:去「图纸」页点任意格子换色' : '已关闭点格修改');
          })

        this.SwitchRow('🧩 拼豆模式(逐色辅助)',
          '一次只突出一种颜色,点格子记完成度,拼的时候不看串行;开启后屏幕保持常亮',
          this.beadAssist, (v: boolean): void => {
            if (v && this.editCell) {
              this.editCell = false;
            }
            this.setBeadAssist(v);
          })

        if (this.beadAssist) {
          Row() {
            Circle({ width: 22, height: 22 }).fill(hexColor(this.assistRgb))
            Text(this.assistText)
              .fontSize(13)
              .fontColor('#1F2430')
              .layoutWeight(1)
              .margin({ left: 8 })
              .maxLines(1)
            Text('下一个颜色')
              .fontSize(12)
              .fontColor('#8A8F98')
              .height(34)
              .padding({ left: 12, right: 12 })
              .backgroundColor('#F2FFFFFF')
              .borderRadius(17)
              .border({ width: 1, color: '#D9DEE5' })
              .onClick(() => {
                this.cycleAssistColor();
              })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          .padding(10)
          .margin({ top: 10 })
          .backgroundColor('#F1F4F6')
          .borderRadius(16)

          Text(this.assistProgressText)
            .fontSize(11)
            .fontColor('#8A8F98')
            .width('100%')
            .margin({ top: 4 })
        }

        if (this.editsCount > 0) {
          this.FullChip('清除全部手动修改', 13, 40, (): void => {
            this.clearEdits();
          })
        }
      }
      .width('100%')
      .padding(14)
      .margin({ top: 8 })
      .backgroundColor('#F2FFFFFF')
      .borderRadius(24)
      .border({ width: 1, color: '#66FFFFFF' })
      .shadow({ radius: 10, color: '#1AE8EBF0', offsetY: 3 })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .margin({ top: 16 })
    .alignItems(HorizontalAlign.Start)

    // 画面调节
    Column() {
      this.SecTitle('画面调节')
      Column() {
        this.SliderRow('亮度', fmtSigned(this.brightnessAdj), this.brightnessAdj + 100, 200,
          (v: number): void => {
            this.brightnessAdj = v - 100;
            this.scheduleRegen();
          })
        this.SliderRow('对比度', fmtSigned(this.contrastAdj), this.contrastAdj + 100, 200,
          (v: number): void => {
            this.contrastAdj = v - 100;
            this.scheduleRegen();
          })
        this.SliderRow('饱和度', fmtSigned(this.saturation), this.saturation + 100, 200,
          (v: number): void => {
            this.saturation = v - 100;
            this.scheduleRegen();
          })
      }
      .width('100%')
      .padding(14)
      .margin({ top: 8 })
      .backgroundColor('#F2FFFFFF')
      .borderRadius(24)
      .border({ width: 1, color: '#66FFFFFF' })
      .shadow({ radius: 10, color: '#1AE8EBF0', offsetY: 3 })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .margin({ top: 18 })
    .alignItems(HorizontalAlign.Start)

    // 底部操作
    Row({ space: 8 }) {
      Text('恢复默认')
        .fontSize(14)
        .fontColor('#8A8F98')
        .textAlign(TextAlign.Center)
        .layoutWeight(1)
        .height(46)
        .borderRadius(23)
        .backgroundColor('#F2FFFFFF')
        .border({ width: 1, color: '#D9DEE5' })
        .onClick(() => {
          this.resetSettings();
        })
      Text('换个照片')
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor('#22B57F')
        .textAlign(TextAlign.Center)
        .layoutWeight(1)
        .height(46)
        .borderRadius(23)
        .backgroundColor('#F2FFFFFF')
        .border({ width: 1, color: '#D9DEE5' })
        .shadow({ radius: 8, color: '#1AE8EBF0', offsetY: 2 })
        .onClick(() => {
          router.back();
        })
    }
    .width('100%')
    .margin({ top: 18 })
    .alignItems(VerticalAlign.Center)
  }

  /** 抽象主色数量档位下标(默认 8 色) */
  private indexOfAbstractColors(): number {
    let idx: number = 2;
    for (let i = 0; i < ABSTRACT_CHOICES.length; i++) {
      if (ABSTRACT_CHOICES[i] === this.abstractColors) {
        idx = i;
      }
    }
    return idx;
  }
}
