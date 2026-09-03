# Java → ArkTS 移植规范(两个移植代理必须共同遵守)

源:安卓工程 `F:\delete\PDAPP\app\src\main\java\com\pindou\app\` 下的纯 Java 文件。
目标:`F:\delete\PDAPP\harmony\entry\src\main\ets\`。
原则:**逐文件 1:1 移植,不改算法、不改数值、不改分支**;所有"魔法数"原样保留。

## ArkTS 语法红线(NEXT 严格模式)

1. **禁止 any/unknown**:一切变量、参数、返回值显式类型;
2. **对象字面量必须带类型**:不允许裸 `{a:1}`,需要 `class` 或 `interface`;
3. **禁止结构化子类型**:回调/接口用显式 interface;
4. 数值类型统一 `number`;像素数组用 `Uint32Array`(ARGB,同 Java int 位布局);
5. 不支持静态初始化块:静态表用 `const` 模块级常量或懒加载函数;
6. 字符串拼接/`String.format` → 模板串或自写 `formatNumber`;
7. `HashMap` → `Map`;`ArrayList` → `Array`;`Arrays.sort` → `Array.sort(比较器)`;
8. 异常:throw new Error('中文消息'),调用方 try/catch;
9. 不用反射/序列化框架:JSON 用 `JSON.parse/stringify`(对应 org.json 的用法手工改写)。

## 核心数据布局(与安卓完全一致)

- 像素:`Uint32Array`,ARGB:alpha<<24 | r<<16 | g<<8 | b(注意 JS 位运算 32 位有符号,
  提取通道用 `(p >>> 16) & 0xFF` 形式,组装 alpha 用 `(a << 24) >>> 0` 再或);
- 图纸格:palette 下标 number,-1 = 空格;
- 模板字符串:`"key:W:H:cells"`,cells 中 '.'=留空,其余字符 chr(35 + 48 色下标),
  即 `String.fromCharCode(35 + idx)`;解析用 `charCodeAt(i) - 35`。

## core 层 API 面(UI 层按此调用,不得偏离)

```ts
// ColorMath.ts
export function rgbToLab(rgb: number): number[]          // [L,a,b]
export function labToRgb(l: number, a: number, b: number): number
export function labDist2(a: number[], b: number[]): number
export function adjustColor(argb: number, bright: number, contrast: number, sat: number): number
export function luminance(rgb: number): number
export function textColorOn(rgb: number): number          // 0xFF1B1B1B | 0xFFFFFFFF
export function darken(rgb: number, factor: number): number

// BeadColor.ts
export class BeadColor {
  code: number; name: string; rgb: number; tag: string
  constructor(code: number, name: string, rgb: number, tag: string)
  displayCode(): string
  fullLabel(): string
  hasOfficialCode(): boolean
}
export function getPalette(tierIdx: number): BeadColor[]   // 0..3 = 24/48/90/120 通用色
export const PALETTE_NAMES: string[]                       // ['24 色经典', ...]

// BrandCharts.ts
export function brandChart(brandIdx: number): BeadColor[]  // Artkal/漫德/Perler/Hama
export function brandTagOf(rgb: number): string            // 近似色号对照

// PatternEngine.ts
export const STYLE_REALISTIC = 0
export const STYLE_ABSTRACT = 1
export class Options { cols; rows; dither; brightness; contrast; saturation;
  style; brickSize; abstractUsePalette; abstractColors; abstractSnapToBeads;
  bgRemove; bgTolerance; roundBoard; maxColors }            // 默认值同安卓
export class UsedColor { index; color: BeadColor; symbol: string; count: number }
export class BeadPattern {
  cols: number; rows: number; palette: BeadColor[]; cells: Uint8Array  // 用 number[] 亦可
  counts: number[]; usedColors: UsedColor[]; totalBeads: number; emptyCount: number; round: boolean
  cellAt(x: number, y: number): number
  outsideShape(x: number, y: number): boolean
  boardsNeeded(): number
}
export function generate(sourcePx: Uint32Array, w: number, h: number,
  palette: BeadColor[], o: Options): BeadPattern
export function symbolFor(paletteIndex: number): string
export function applyPatch(base: BeadPattern, edits: Map<number, number>): BeadPattern
// 说明:generate 内部含去背景(bgRemove=true 走 Segmenter.ts 纯算法版,
// 对应安卓 PatternEngine 的颜色统计兜底,跳过 ML);maxColors 贪心合并同安卓。

// WatermarkRemover.ts
export function removeWatermark(argb: Uint32Array, w: number, h: number,
  x0: number, y0: number, x1: number, y1: number): void

// GridScanner.ts
export class Grid { cols; rows; ox; oy; pitchX; pitchY: number }
export function detectGrid(px: Uint32Array, w: number, h: number,
  x0: number, y0: number, x1: number, y1: number): Grid | null
export function sampleGrid(px: Uint32Array, w: number, h: number,
  g: Grid, outDims: number[]): Uint32Array   // 含边缘背景裁剪

// Segmenter.ts(纯算法去背景,对应安卓 SubjectSegmenter 语义)
export function findBackgroundMask(px: Uint32Array, w: number, h: number,
  tolerance: number): Uint8Array      // 1 = 背景

// TemplateData.ts
export class Cat { name: string; items: Tpl[] }
export class Tpl { name: string; grid: number[][]; suggestedSize: number }
export function allCategories(palette: BeadColor[]): Cat[]   // 🐾动物/💬情绪/🏰地牢

// BeadInventory.ts(Preferences 持久化,键 'inv_' + rgb 的 6 位 hex)
export async function invGet(ctx: common.UIAbilityContext, rgb: number): Promise<number>  // -1 未登记
export async function invSet(ctx: common.UIAbilityContext, rgb: number, count: number): Promise<void>

// ProjectStore.ts(files 目录 JSON,结构同安卓版)
export async function saveProject(ctx: common.UIAbilityContext, json: string, name: string): Promise<void>
export async function listProjects(ctx: common.UIAbilityContext): Promise<ProjectMeta[]>
```

## UI 层约定(pages/components)

- 视觉规范 = 安卓 v2.25 柔焦粉彩:底 `#F6F3EE` + 粉彩柔光斑(用 stack + 带透明度的
  大圆实现);卡片白色 92% 不透明、圆角 24;textMain #1F2430;textSub #8A8F98;
  主操作薄荷渐变 `linearGradient(180deg, #3ECF96, #17A673)`;圆角 30 的按钮;
  图标 emoji 放 52vp 的薄荷→浅蓝渐变圆盘(#DDF3EA→#DDEBFA);
- 首页卡片:开始一张新图纸(相册)/去水印/识别图纸/模板库/文字生成/空白画布/我的项目;
- 编辑页 = 顶部(返回/标题/撤销重做/菜单)+ 预览画布 Tab(效果图/图纸/豆单)+
  参数区(尺寸 chips、色板、限色、写实/抽象、去背景+容差、去水印、拼豆模式、限色数、变换);
- PatternCanvas:ArkUI Canvas 绘制(格子/网格线/29 分板线/符号/坐标/圆板蒙版),
  支持双指缩放(PinchGesture)+单指拖(PanGesture)+双击复位+点格回调+画笔模式;
- 拼豆模式:一次突出一种色、点格标记完成(描薄荷框)、屏幕常亮
  (`window.setWindowKeepScreenOn`)。

## 移植完成定义

- 所有导出函数/类与上方 API 面一致;
- 无 any、无未类型化字面量、无对 android.* 的残留引用;
- 每个文件头注释注明"移植自 <安卓文件名>"。
