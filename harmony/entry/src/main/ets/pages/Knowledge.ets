/**
 * 拼豆知识页 —— 移植自安卓 activity_knowledge.xml + KnowledgeActivity.java
 * 顶部栏 + 7 个段落卡片(规格、模板、选购、熨烫、看图技巧等),文案 1:1 照抄安卓版。
 */
import { router } from '@kit.ArkUI';

/** 知识段落 */
class KSection {
  title: string = '';
  body: string = '';

  constructor(title: string, body: string) {
    this.title = title;
    this.body = body;
  }
}

/** 7 个段落(照抄安卓 KnowledgeActivity.SECTIONS) */
const SECTIONS: KSection[] = [
  new KSection('拼豆是什么?',
    '拼豆(Perler Beads / Fuse Beads,又叫熨斗豆、拼拼豆豆)是一种直径约 5 毫米、中间带孔的塑料小珠子。' +
    '把豆子按图纸插在带凸起的模板(拼板)上,摆好图案后垫上熨烫纸,用熨斗加热让豆子互相粘连,' +
    '冷却后就得到一块像素风的作品。常用来做钥匙扣、杯垫、挂饰、摆件和相框画。'),
  new KSection('豆子规格',
    '• 标准豆(5mm):最常见,颜色最多、价格便宜。每格约 0.5cm,一块 29×29 模板成品约 15×15cm。\n' +
    '• 迷你豆(2.6mm):更精细,适合细节多的小图,成品尺寸约为标准豆的一半,拼起来费眼力,建议配镊子。\n' +
    '注意:两种豆子孔距不同,不能混用在同一块模板上。'),
  new KSection('模板(拼板)常识',
    '最常用的是方形模板,标准豆方板一般是 29×29 孔。多块模板可以拼起来扩大画面:' +
    '58×58 需要 4 块,87×87 需要 9 块,116×116 需要 16 块。' +
    '另有圆形、六边形、爱心等异形板,适合做小挂饰。拼大图时建议先把多块板用边框扣固定好再下豆。'),
  new KSection('颜色与选购',
    '市面常见套装有 24、44、48、60、90、120 色等。颜色越多照片还原度越高:' +
    '24 色适合简单卡通和 emoji,48-60 色适合一般图案,90 色以上适合人像和风景。\n' +
    '品牌方面,进口有 Perler(美)、Hama(丹麦)、Artkal,国产有漫德、伊诺思等,色号体系各不相同。' +
    '本 APP 的颜色按常见色系整理,仅供参考,建议对照自己手上豆子的实物颜色。\n' +
    '小提示:黑色、深蓝、深棕和白色、肤色的用量通常最大,建议多备。'),
  new KSection('熨烫定型步骤',
    '① 作品拼完后,在豆子上垫一层熨烫纸(或烘焙纸),不要让熨斗直接接触豆子。\n' +
    '② 熨斗调到中温(约 120~150℃),关闭蒸汽。单手轻压、来回匀速移动,每面大约 10~20 秒。\n' +
    '③ 透过纸观察:豆孔略微变小、表面微微融化即可,烫太久会过度熔化变形。\n' +
    '④ 完全冷却后再揭纸;冷却时压一本厚书可以让作品更平整。\n' +
    '⑤ 翻面重复以上步骤,两面都烫过更结实;边缘容易脱落的可以垫纸再补烫一下。'),
  new KSection('看图拼豆技巧',
    '• 从图纸的一个角开始,一行一行拼,每拼完一行核对一遍,不容易错。\n' +
    '• 先放数量少的深色轮廓线,再填充大片浅色区域。\n' +
    '• 把豆子按颜色分装在小分格盒里,对照图纸上的符号取豆。\n' +
    '• 用镊子夹豆更省力,拼迷你豆基本必须用镊子。\n' +
    '• 拼错了不要硬拔,用另一颗豆子从模板背面把它顶出去。'),
  new KSection('用本 APP 出图的建议',
    '• 选图:主体清晰、背景简单的照片效果最好,比如卡通头像、宠物大头照、证件照。' +
    '背景杂乱的人像建议先把背景去掉,或把尺寸开到 87 以上。\n' +
    '• 尺寸:29×29 适合 emoji 和 Q 版;58×58 适合头像;87×87 以上适合半身像和风景。\n' +
    '• 色板:先默认用 90 色预览;如果某些颜色你手头没有,可以换小一点的色板,或拼的时候用相近色替代。\n' +
    '• 画面调节:照片变像素后颜色会显得"灰"一些,适当加饱和度(+10~+30)和对比度(+5~+20)会更接近原图。\n' +
    '• 抖动:开启后照片的明暗过渡更自然,但图纸会更复杂,新手建议关闭。')
];

@Entry
@Component
struct Knowledge {

  /** 段落卡片(标题薄荷绿加粗 + 正文暖灰) */
  @Builder
  SectionCard(sec: KSection) {
    Column() {
      Text(sec.title)
        .fontSize(17)
        .fontWeight(FontWeight.Bold)
        .fontColor('#22B57F')
        .width('100%')
      Text(sec.body)
        .fontSize(14)
        .fontColor('#4E4A46')
        .lineHeight(22)
        .width('100%')
        .margin({ top: 8 })
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
    .padding({ top: 16, bottom: 16, left: 18, right: 18 })
    .backgroundColor('#F2FFFFFF')
    .borderRadius(24)
    .border({ width: 1, color: '#66FFFFFF' })
    .shadow({ radius: 10, color: '#1AE8EBF0', offsetY: 3 })
    .margin({ bottom: 14 })
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
        .fill('#FFE9D9')
        .opacity(0.8)
        .blur(60)
        .position({ x: '60%', y: '-12%' })
      Circle({ width: 340, height: 340 })
        .fill('#DDEBFA')
        .opacity(0.85)
        .blur(70)
        .position({ x: '-14%', y: '58%' })
      Circle({ width: 300, height: 300 })
        .fill('#E7E1FA')
        .opacity(0.8)
        .blur(60)
        .position({ x: '64%', y: '66%' })

      Column() {
        // 顶栏(同安卓:‹ 返回 + 拼豆小知识)
        Row() {
          Text('‹')
            .fontSize(30)
            .fontWeight(FontWeight.Bold)
            .fontColor('#1F2430')
            .padding({ left: 16, right: 8 })
            .onClick(() => {
              router.back();
            })
          Text('拼豆小知识')
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

        Scroll() {
          Column() {
            ForEach(SECTIONS, (sec: KSection) => {
              this.SectionCard(sec)
            }, (sec: KSection) => sec.title)
          }
          .width('100%')
          .padding({ top: 16, left: 16, right: 16, bottom: 30 })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
        .align(Alignment.Top)
      }
      .width('100%')
      .height('100%')
    }
    .width('100%')
    .height('100%')
  }
}
