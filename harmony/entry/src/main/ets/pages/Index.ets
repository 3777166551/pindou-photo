/**
 * 首页 —— 移植自安卓 activity_main.xml + MainActivity.java
 * 柔焦粉彩玻璃拟态:底 #F6F3EE + 粉彩柔光斑;磨砂白卡;薄荷渐变主按钮。
 * 阶段 1:「从相册选照片」PhotoViewPicker 选图后进编辑页;「模板库」「拼豆知识」跳页;
 * 其余工具卡片(拍一张照片/去水印/识别图纸/文字生成/空白画布/我的项目)Toast「阶段 2 开放」。
 */
import { router, promptAction } from '@kit.ArkUI';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { EditorParams } from './Editor';

/** 工具卡片数据(照抄安卓卡片文案) */
class ToolItem {
  emoji: string = '';
  title: string = '';
  desc: string = '';
  /** 阶段 1 可用的卡片动作;'phase2' = Toast 阶段 2 开放 */
  action: string = 'phase2';

  constructor(emoji: string, title: string, desc: string, action: string) {
    this.emoji = emoji;
    this.title = title;
    this.desc = desc;
    this.action = action;
  }
}

/** 阶段 2 卡片列表(去水印单独一行,识别图纸+模板库/文字生成+空白画布成行,知识单独一行) */
const ROW_ONE: ToolItem[] = [new ToolItem('🩹', '去水印', '框住水印一键清除', 'phase2')];
const ROW_TWO: ToolItem[] = [
  new ToolItem('📷', '识别图纸', '现成图纸变同款', 'phase2'),
  new ToolItem('🧩', '模板库', '460+ 图案离线拼', 'templates')
];
const ROW_THREE: ToolItem[] = [
  new ToolItem('🔤', '文字生成', '名字变挂件图纸', 'phase2'),
  new ToolItem('✏️', '空白画布', '从零手绘图纸', 'phase2')
];
const ROW_FOUR: ToolItem[] = [new ToolItem('📖', '拼豆知识', '新手入门不迷路', 'knowledge')];

function toast(msg: string): void {
  promptAction.showToast({ message: msg });
}

@Entry
@Component
struct Index {

  /** 相册选照片:PhotoViewPicker 选图 -> router.pushUrl 编辑页(传图片 URI) */
  private async pickFromGallery(): Promise<void> {
    try {
      const opts: photoAccessHelper.PhotoSelectOptions = new photoAccessHelper.PhotoSelectOptions();
      opts.MIMEType = photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE;
      opts.maxSelectNumber = 1;
      const picker: photoAccessHelper.PhotoViewPicker = new photoAccessHelper.PhotoViewPicker();
      const res: photoAccessHelper.PhotoSelectResult = await picker.select(opts);
      if (res.photoUris.length > 0) {
        const prm: EditorParams = { photoUri: res.photoUris[0] };
        router.pushUrl({ url: 'pages/Editor', params: prm });
      }
    } catch (e) {
      toast('没有可用的相册应用');
    }
  }

  private handleAction(action: string): void {
    if (action === 'templates') {
      router.pushUrl({ url: 'pages/Templates' });
      return;
    }
    if (action === 'knowledge') {
      router.pushUrl({ url: 'pages/Knowledge' });
      return;
    }
    toast('阶段 2 开放');
  }

  /** 工具卡图标:emoji 放 52vp 薄荷→浅蓝渐变圆盘(同安卓 ToolCardIcon/bg_icon_pastel) */
  @Builder
  ToolDisc(emoji: string) {
    Column() {
      Text(emoji)
        .fontSize(24)
    }
    .width(52)
    .height(52)
    .borderRadius(26)
    .linearGradient({
      angle: 315,
      colors: [['#DDF3EA', 0], ['#DDEBFA', 1]]
    })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
  }

  /** 2 列工具小卡(同安卓 ToolCard 样式:磨砂白圆角 + 图标盘 + 标题 + 描述) */
  @Builder
  ToolCardRow(items: ToolItem[]) {
    Row({ space: 12 }) {
      ForEach(items, (item: ToolItem) => {
        Column() {
          this.ToolDisc(item.emoji)
          Text(item.title)
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .margin({ top: 6 })
          Text(item.desc)
            .fontSize(10)
            .fontColor('#8A8F98')
            .margin({ top: 2 })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .padding({ top: 14, bottom: 12, left: 8, right: 8 })
        .backgroundColor('#F2FFFFFF')
        .borderRadius(22)
        .border({ width: 1, color: '#66FFFFFF' })
        .shadow({ radius: 8, color: '#14000000', offsetY: 2 })
        .onClick(() => {
          this.handleAction(item.action);
        })
      }, (item: ToolItem) => item.title)
    }
    .width('100%')
  }

  /** 区块标题(同安卓:加粗标题 + 右侧细分隔线) */
  @Builder
  SectionTitle(title: string) {
    Row() {
      Text(title)
        .fontSize(15)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1F2430')
      Divider()
        .color('#ECEFF3')
        .strokeWidth(1.5)
        .layoutWeight(1)
        .margin({ left: 10 })
    }
    .width('100%')
    .margin({ top: 22, bottom: 10 })
    .alignItems(VerticalAlign.Center)
  }

  build() {
    Stack() {
      // ---- 柔焦粉彩底:大半径低透明度圆形光斑 ----
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor('#F6F3EE')
      Circle({ width: 300, height: 300 })
        .fill('#E3F5EC')
        .opacity(0.9)
        .blur(60)
        .position({ x: '-12%', y: '-6%' })
      Circle({ width: 320, height: 320 })
        .fill('#FFE9D9')
        .opacity(0.85)
        .blur(60)
        .position({ x: '60%', y: '-10%' })
      Circle({ width: 340, height: 340 })
        .fill('#E7E1FA')
        .opacity(0.8)
        .blur(70)
        .position({ x: '-14%', y: '58%' })
      Circle({ width: 300, height: 300 })
        .fill('#DDEBFA')
        .opacity(0.9)
        .blur(60)
        .position({ x: '64%', y: '64%' })
      Circle({ width: 220, height: 220 })
        .fill('#E3F5EC')
        .opacity(0.7)
        .blur(50)
        .position({ x: '34%', y: '38%' })

      // ---- 内容 ----
      Scroll() {
        Column() {
          Column().height(28).width('100%')

          // 大标题 + 渐变小条 + tagline
          Text('照片变拼豆')
            .fontSize(30)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .letterSpacing(0.6)
          Row()
            .width(132)
            .height(6)
            .borderRadius(3)
            .linearGradient({
              angle: 0,
              colors: [['#3ECF96', 0], ['#7C6CF0', 1]]
            })
            .margin({ top: 2 })
          Text('把手机里的照片,一键变成拼豆图纸 ✨')
            .fontSize(13)
            .fontColor('#8A8F98')
            .margin({ top: 8 })

          // 主卡片:核心流程(粉彩渐变英雄卡)
          Column() {
            Row() {
              Text('📷')
                .fontSize(34)
              Column() {
                Text('开始一张新图纸')
                  .fontSize(19)
                  .fontWeight(FontWeight.Bold)
                  .fontColor('#1F2430')
                Text('自动匹配豆色 · 生成可打印图纸')
                  .fontSize(11)
                  .fontColor('#6B7280')
                  .margin({ top: 2 })
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)
              .margin({ left: 10 })
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)

            // 从相册选照片:主按钮(薄荷渐变药丸)
            Text('从相册选照片')
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
              .fontColor('#FFFFFF')
              .textAlign(TextAlign.Center)
              .width('100%')
              .height(52)
              .borderRadius(26)
              .linearGradient({
                angle: 180,
                colors: [['#3ECF96', 0], ['#17A673', 1]]
              })
              .shadow({ radius: 10, color: '#333ECF96', offsetY: 4 })
              .margin({ top: 14 })
              .onClick(() => {
                this.pickFromGallery();
              })

            // 拍一张照片:次按钮(白底细描边药丸)
            Text('拍一张照片')
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
              .fontColor('#1F2430')
              .textAlign(TextAlign.Center)
              .width('100%')
              .height(46)
              .borderRadius(23)
              .backgroundColor('#F2FFFFFF')
              .border({ width: 1, color: '#D9DEE5' })
              .margin({ top: 10 })
              .onClick(() => {
                toast('阶段 2 开放');
              })
          }
          .width('100%')
          .padding(18)
          .margin({ top: 20 })
          .borderRadius(24)
          .linearGradient({
            angle: 135,
            colors: [['#F5FFFFFF', 0], ['#EDFBF5', 0.55], ['#EEF2FE', 1]]
          })
          .border({ width: 1, color: '#66FFFFFF' })
          .shadow({ radius: 16, color: '#1AE8EBF0', offsetY: 6 })

          // 🧰 好用的小工具
          this.SectionTitle('🧰 好用的小工具')
          this.ToolCardRow(ROW_ONE)
          Column().height(12)
          this.ToolCardRow(ROW_TWO)
          Column().height(12)
          this.ToolCardRow(ROW_THREE)
          Column().height(12)
          this.ToolCardRow(ROW_FOUR)

          // 🗂 继续拼(我的项目宽卡)
          this.SectionTitle('🗂 继续拼')
          Row() {
            this.ToolDisc('🗂')
            Column() {
              Text('我的项目')
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor('#1F2430')
              Text('存档随时接着拼,豆仓进度都在')
                .fontSize(10)
                .fontColor('#8A8F98')
                .margin({ top: 2 })
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
            .margin({ left: 12 })
          }
          .width('100%')
          .alignItems(VerticalAlign.Center)
          .padding({ top: 14, bottom: 14, left: 8, right: 8 })
          .backgroundColor('#F2FFFFFF')
          .borderRadius(22)
          .border({ width: 1, color: '#66FFFFFF' })
          .shadow({ radius: 8, color: '#14000000', offsetY: 2 })
          .onClick(() => {
            toast('阶段 2 开放');
          })

          Text('· 拼出你的快乐 ·')
            .fontSize(12)
            .fontColor('#8A8F98')
            .margin({ top: 22 })

          Column().height(24).width('100%')
        }
        .width('100%')
        .padding({ left: 18, right: 18 })
      }
      .scrollBar(BarState.Off)
      .width('100%')
      .height('100%')
      .align(Alignment.Top)
      .edgeEffect(EdgeEffect.Spring)
    }
    .width('100%')
    .height('100%')
  }
}
