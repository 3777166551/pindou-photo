/**
 * 模板库 —— 移植自安卓 MainActivity.showTemplateGallery / showCategoryGrid
 * 两级导航:分类卡页(2 列,emoji + 名 + 数量)→ 缩略图网格(4 列,64vp 数据渲染)→
 * 点击 router 到编辑页并经 AppStorage 载入该模板像素。
 */
import { router } from '@kit.ArkUI';
import { Cat, Tpl, allCategories } from '../core/TemplateData';
import { BeadColor, getPalette } from '../core/BeadColor';
import { hexColor } from '../components/PatternCanvas';

/**
 * 模板像素载荷(经 AppStorage 传给编辑页;模板数据小,直接给像素等价物)。
 * grid 里 -1 = 留空格(透明像素),其余为 48 色通用色板的下标。
 */
export class TemplatePayload {
  px: Uint32Array = new Uint32Array(0);
  w: number = 0;
  h: number = 0;
  suggestedSize: number = 0;
}

const APPSTORAGE_KEY: string = 'tplPayload';
/** 缩略图边长(vp,对应安卓 64dp) */
const THUMB_SIZE: number = 64;

/** 把模板 grid 渲染成 ARGB 像素(留空格 = 全 0 透明,同安卓 Templates.build 的透明底) */
function tplToPixels(tpl: Tpl, pal: BeadColor[]): TemplatePayload {
  let w: number = 0;
  for (const row of tpl.grid) {
    if (row.length > w) {
      w = row.length;
    }
  }
  const h: number = tpl.grid.length;
  const px: Uint32Array = new Uint32Array(w * h);
  for (let y = 0; y < h; y++) {
    const row: number[] = tpl.grid[y];
    for (let x = 0; x < row.length; x++) {
      const v: number = row[x];
      if (v < 0 || v >= pal.length) {
        continue; // 留空格(透明)
      }
      px[y * w + x] = (0xFF000000 | pal[v].rgb) >>> 0;
    }
  }
  const out: TemplatePayload = new TemplatePayload();
  out.px = px;
  out.w = w;
  out.h = h;
  out.suggestedSize = tpl.suggestedSize;
  return out;
}

/** 分类名拆出 emoji(同安卓:首个空格前为图标) */
function catIcon(name: string): string {
  const sp: number = name.indexOf(' ');
  return sp > 0 ? name.substring(0, sp) : '🧩';
}

function catName(name: string): string {
  const sp: number = name.indexOf(' ');
  return sp > 0 ? name.substring(sp + 1) : name;
}

/** 缩略图单元:64vp Canvas 数据渲染(对应安卓 Templates.buildThumb) */
@Component
struct ThumbCell {
  tpl: Tpl | null = null;
  private ctx: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));

  private draw(): void {
    const t: Tpl | null = this.tpl;
    if (t === null) {
      return;
    }
    const ctx: CanvasRenderingContext2D = this.ctx;
    ctx.clearRect(0, 0, THUMB_SIZE, THUMB_SIZE);
    let cols: number = 0;
    for (const row of t.grid) {
      if (row.length > cols) {
        cols = row.length;
      }
    }
    const rows: number = t.grid.length;
    if (cols <= 0 || rows <= 0) {
      return;
    }
    const pal: BeadColor[] = getPalette(1); // 48 色通用色板(模板数据即按它标注)
    const cell: number = THUMB_SIZE / Math.max(cols, rows);
    const ox: number = (THUMB_SIZE - cols * cell) / 2;
    const oy: number = (THUMB_SIZE - rows * cell) / 2;
    for (let y = 0; y < rows; y++) {
      const row: number[] = t.grid[y];
      for (let x = 0; x < row.length; x++) {
        const v: number = row[x];
        if (v < 0 || v >= pal.length) {
          continue;
        }
        ctx.fillStyle = hexColor(pal[v].rgb);
        ctx.fillRect(ox + x * cell, oy + y * cell, Math.max(1, cell - 0.5), Math.max(1, cell - 0.5));
      }
    }
  }

  build() {
    Column() {
      Canvas(this.ctx)
        .width(THUMB_SIZE)
        .height(THUMB_SIZE)
        .onReady(() => {
          this.draw();
        })
      Text(this.tpl !== null ? this.tpl.name : '')
        .fontSize(10)
        .fontColor('#444444')
        .maxLines(1)
        .margin({ top: 2 })
    }
    .alignItems(HorizontalAlign.Center)
  }
}

@Entry
@Component
struct Templates {
  /** 0 = 分类卡页,1 = 缩略图网格页 */
  @State level: number = 0;
  @State cats: Cat[] = [];
  @State selCat: Cat | null = null;

  aboutToAppear(): void {
    this.cats = allCategories(getPalette(1)); // 48 色通用色板
  }

  private openTemplate(tpl: Tpl): void {
    const payload: TemplatePayload = tplToPixels(tpl, getPalette(1));
    AppStorage.setOrCreate<TemplatePayload>(APPSTORAGE_KEY, payload);
    router.pushUrl({ url: 'pages/Editor' });
  }

  /** 顶栏(返回 ‹ / 标题),二级页返回改为回到分类页 */
  @Builder
  TopBar() {
    Row() {
      Text('‹')
        .fontSize(30)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1F2430')
        .padding({ left: 16, right: 8 })
        .onClick(() => {
          if (this.level === 1) {
            this.level = 0;
            this.selCat = null;
          } else {
            router.back();
          }
        })
      Text(this.level === 0 ? '图案模板库 🧩' : catName(this.selCat !== null ? this.selCat.name : ''))
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1F2430')
        .layoutWeight(1)
    }
    .width('100%')
    .height(58)
    .backgroundColor('#B3FFFFFF')
    .alignItems(VerticalAlign.Center)
    .padding({ right: 18 })
  }

  /** 二级页顶部的「⬅ 分类」返回 chip(对应安卓对话框的 Positive 按钮) */
  @Builder
  BackToCatChip() {
    Text('⬅ 分类')
      .fontSize(13)
      .fontColor('#8A8F98')
      .height(34)
      .padding({ left: 14, right: 14 })
      .backgroundColor('#F2FFFFFF')
      .borderRadius(16)
      .border({ width: 1, color: '#D9DEE5' })
      .margin({ left: 18, top: 12 })
      .onClick(() => {
        this.level = 0;
        this.selCat = null;
      })
  }

  /** 分类卡:emoji + 名 + 数量(2 列) */
  @Builder
  CategoryCell(cat: Cat) {
    Column() {
      Text(catIcon(cat.name))
        .fontSize(26)
      Text(catName(cat.name))
        .fontSize(14)
        .fontColor('#1F2430')
        .margin({ top: 4 })
      Text(cat.items.length + ' 个图案')
        .fontSize(11)
        .fontColor('#8A8F98')
        .margin({ top: 2 })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
    .padding(10)
    .backgroundColor('#F2FFFFFF')
    .borderRadius(22)
    .border({ width: 1, color: '#66FFFFFF' })
    .shadow({ radius: 8, color: '#14000000', offsetY: 2 })
    .onClick(() => {
      this.selCat = cat;
      this.level = 1;
    })
  }

  build() {
    Stack() {
      // 柔焦粉彩底
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor('#F6F3EE')
      Circle({ width: 320, height: 320 })
        .fill('#E3F5EC')
        .opacity(0.9)
        .blur(60)
        .position({ x: '-12%', y: '-6%' })
      Circle({ width: 340, height: 340 })
        .fill('#E7E1FA')
        .opacity(0.8)
        .blur(70)
        .position({ x: '-14%', y: '58%' })
      Circle({ width: 300, height: 300 })
        .fill('#FFE9D9')
        .opacity(0.8)
        .blur(60)
        .position({ x: '62%', y: '-10%' })

      Column() {
        this.TopBar()
        if (this.level === 0) {
          Grid() {
            ForEach(this.cats, (cat: Cat) => {
              GridItem() {
                this.CategoryCell(cat)
              }
            }, (cat: Cat) => cat.name)
          }
          .columnsTemplate('1fr 1fr')
          .columnsGap(12)
          .rowsGap(12)
          .width('100%')
          .layoutWeight(1)
          .padding(18)
          .scrollBar(BarState.Off)
        } else {
          this.BackToCatChip()
          Grid() {
            ForEach(this.selCat !== null ? this.selCat.items : [], (tpl: Tpl) => {
              GridItem() {
                Column() {
                  ThumbCell({ tpl: tpl })
                }
                .padding(6)
                .backgroundColor('#F2FFFFFF')
                .borderRadius(16)
                .border({ width: 1, color: '#66FFFFFF' })
                .onClick(() => {
                  this.openTemplate(tpl);
                })
              }
            }, (tpl: Tpl) => tpl.name)
          }
          .columnsTemplate('1fr 1fr 1fr 1fr')
          .columnsGap(10)
          .rowsGap(10)
          .width('100%')
          .layoutWeight(1)
          .padding(18)
          .scrollBar(BarState.Off)
        }
      }
      .width('100%')
      .height('100%')
    }
    .width('100%')
    .height('100%')
  }
}
