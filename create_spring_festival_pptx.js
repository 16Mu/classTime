const PptxGenJS = require('pptxgenjs');

const pres = new PptxGenJS();

// 设置演示文稿主题色
pres.layout = 'LAYOUT_16x9';

// 颜色方案 - 春节主题 (红色、金色、米白色)
const colors = {
  red: 'C41E3A',
  gold: 'FFD700',
  darkRed: '8B0000',
  cream: 'FFF8DC',
  black: '1A1A1A'
};

// 第 1 页：封面
let slide1 = pres.addSlide();
slide1.background = { color: colors.darkRed };

slide1.addShape(pres.shapes.RECTANGLE, { 
  x: 0, y: 0, w: '100%', h: '100%',
  fill: { color: colors.darkRed }
});

slide1.addShape(pres.shapes.RECTANGLE, {
  x: 0.3, y: 0.3, w: 9.4, h: 5,
  fill: { color: 'transparent' },
  line: { color: colors.gold, width: 2 }
});

slide1.addText('春节', {
  x: 2, y: 1.5, w: 5.2, h: 1.5,
  fontSize: 54,
  color: colors.gold,
  bold: true,
  align: 'center',
  fontFace: 'Microsoft YaHei'
});

slide1.addText('中国农历新年', {
  x: 2, y: 2.8, w: 5.2, h: 0.8,
  fontSize: 24,
  color: colors.cream,
  align: 'center',
  fontFace: 'Microsoft YaHei'
});

slide1.addText('Spring Festival', {
  x: 2, y: 3.5, w: 5.2, h: 0.6,
  fontSize: 18,
  color: colors.gold,
  align: 'center',
  italic: true
});

// 第 2 页：什么是春节
let slide2 = pres.addSlide();
slide2.background = { color: colors.cream };

slide2.addText('什么是春节？', {
  x: 0.5, y: 0.3, w: 8.2, h: 0.6,
  fontSize: 36,
  color: colors.darkRed,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide2.addShape(pres.shapes.LINE, {
  x: 0.5, y: 0.9, w: 2, h: 0,
  line: { color: colors.red, width: 3 }
});

slide2.addText('春节是中国最重要的传统节日', {
  x: 0.7, y: 1.3, w: 8, h: 0.4,
  fontSize: 18,
  color: colors.black,
  fontFace: 'Microsoft YaHei',
  bullet: true
});

slide2.addText('农历正月初一，通常在公历 1 月底至 2 月中旬', {
  x: 0.7, y: 1.8, w: 8, h: 0.4,
  fontSize: 18,
  color: colors.black,
  fontFace: 'Microsoft YaHei',
  bullet: true
});

slide2.addText('已有 4000 多年历史', {
  x: 0.7, y: 2.3, w: 8, h: 0.4,
  fontSize: 18,
  color: colors.black,
  fontFace: 'Microsoft YaHei',
  bullet: true
});

slide2.addText('象征着新的开始、家庭团聚和美好祝愿', {
  x: 0.7, y: 2.8, w: 8, h: 0.4,
  fontSize: 18,
  color: colors.black,
  fontFace: 'Microsoft YaHei',
  bullet: true
});

// 第 3 页：春节的起源
let slide3 = pres.addSlide();
slide3.background = { color: colors.cream };

slide3.addText('春节的起源与传说', {
  x: 0.5, y: 0.3, w: 8.2, h: 0.6,
  fontSize: 36,
  color: colors.darkRed,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide3.addShape(pres.shapes.LINE, {
  x: 0.5, y: 0.9, w: 2, h: 0,
  line: { color: colors.red, width: 3 }
});

slide3.addText('年兽的传说', {
  x: 0.7, y: 1.3, w: 3.5, h: 0.4,
  fontSize: 22,
  color: colors.red,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide3.addText('相传古代有一只叫"年"的怪兽，每到除夕就出来伤害人畜。后来人们发现年兽害怕红色、火光和响声，于是贴红对联、放鞭炮驱赶年兽。', {
  x: 0.7, y: 1.7, w: 8, h: 0.8,
  fontSize: 16,
  color: colors.black,
  fontFace: 'Microsoft YaHei',
  align: 'left'
});

slide3.addText('历史演变', {
  x: 0.7, y: 2.7, w: 3.5, h: 0.4,
  fontSize: 22,
  color: colors.red,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide3.addText('起源于殷商时期的岁末祭祀活动，汉武帝时期正式确定正月初一为岁首，至今已有两千多年历史。', {
  x: 0.7, y: 3.1, w: 8, h: 0.6,
  fontSize: 16,
  color: colors.black,
  fontFace: 'Microsoft YaHei',
  align: 'left'
});

// 第 4 页：春节习俗 - 准备
let slide4 = pres.addSlide();
slide4.background = { color: colors.cream };

slide4.addText('春节习俗 - 节前准备', {
  x: 0.5, y: 0.3, w: 8.2, h: 0.6,
  fontSize: 36,
  color: colors.darkRed,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide4.addShape(pres.shapes.LINE, {
  x: 0.5, y: 0.9, w: 2, h: 0,
  line: { color: colors.red, width: 3 }
});

const preparations = [
  { title: '扫尘', desc: '腊月二十四，扫房子，寓意除旧迎新' },
  { title: '办年货', desc: '采购食品、新衣、礼品等' },
  { title: '贴春联', desc: '红色对联表达美好祝愿' },
  { title: '贴福字', desc: '倒贴福字，寓意"福到"' }
];

preparations.forEach((item, index) => {
  const col = index % 2;
  const row = Math.floor(index / 2);
  const x = col === 0 ? 0.7 : 4.7;
  const y = 1.3 + row * 1.3;
  
  slide4.addText(item.title, {
    x: x, y: y, w: 3.5, h: 0.4,
    fontSize: 20,
    color: colors.red,
    bold: true,
    fontFace: 'Microsoft YaHei'
  });
  
  slide4.addText(item.desc, {
    x: x, y: y + 0.4, w: 3.5, h: 0.5,
    fontSize: 15,
    color: colors.black,
    fontFace: 'Microsoft YaHei'
  });
});

// 第 5 页：除夕之夜
let slide5 = pres.addSlide();
slide5.background = { color: colors.cream };

slide5.addText('除夕之夜', {
  x: 0.5, y: 0.3, w: 8.2, h: 0.6,
  fontSize: 36,
  color: colors.darkRed,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide5.addShape(pres.shapes.LINE, {
  x: 0.5, y: 0.9, w: 2, h: 0,
  line: { color: colors.red, width: 3 }
});

const newYearEve = [
  { title: '团圆饭', desc: '全家人围坐一起吃年夜饭，象征团圆美满' },
  { title: '守岁', desc: '熬夜迎接新年，寓意辞旧迎新' },
  { title: '发红包', desc: '长辈给晚辈压岁钱，祝福平安健康' },
  { title: '看春晚', desc: '观看春节联欢晚会，共度欢乐时光' }
];

newYearEve.forEach((item, index) => {
  slide5.addText(item.title, {
    x: 0.7, y: 1.3 + index * 1.1, w: 2, h: 0.4,
    fontSize: 20,
    color: colors.red,
    bold: true,
    fontFace: 'Microsoft YaHei'
  });
  
  slide5.addText(item.desc, {
    x: 2.5, y: 1.3 + index * 1.1, w: 6.2, h: 0.5,
    fontSize: 16,
    color: colors.black,
    fontFace: 'Microsoft YaHei'
  });
});

// 第 6 页：春节美食
let slide6 = pres.addSlide();
slide6.background = { color: colors.cream };

slide6.addText('春节传统美食', {
  x: 0.5, y: 0.3, w: 8.2, h: 0.6,
  fontSize: 36,
  color: colors.darkRed,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide6.addShape(pres.shapes.LINE, {
  x: 0.5, y: 0.9, w: 2, h: 0,
  line: { color: colors.red, width: 3 }
});

const foods = [
  { name: '饺子', meaning: '形如元宝，寓意招财进宝' },
  { name: '鱼', meaning: '年年有余的象征' },
  { name: '年糕', meaning: '年年高升' },
  { name: '汤圆', meaning: '团团圆圆' },
  { name: '春卷', meaning: '迎接春天' },
  { name: '长寿面', meaning: '健康长寿' }
];

foods.forEach((item, index) => {
  const col = index % 2;
  const row = Math.floor(index / 2);
  const x = col === 0 ? 0.7 : 4.7;
  const y = 1.3 + row * 1.2;
  
  slide6.addText(item.name, {
    x: x, y: y, w: 3.5, h: 0.4,
    fontSize: 22,
    color: colors.red,
    bold: true,
    fontFace: 'Microsoft YaHei'
  });
  
  slide6.addText(item.meaning, {
    x: x, y: y + 0.4, w: 3.5, h: 0.4,
    fontSize: 15,
    color: colors.black,
    fontFace: 'Microsoft YaHei'
  });
});

// 第 7 页：春节活动
let slide7 = pres.addSlide();
slide7.background = { color: colors.cream };

slide7.addText('春节期间的活动', {
  x: 0.5, y: 0.3, w: 8.2, h: 0.6,
  fontSize: 36,
  color: colors.darkRed,
  bold: true,
  fontFace: 'Microsoft YaHei'
});

slide7.addShape(pres.shapes.LINE, {
  x: 0.5, y: 0.9, w: 2, h: 0,
  line: { color: colors.red, width: 3 }
});

const activities = [
  '拜年 - 走亲访友，互送祝福',
  '舞龙舞狮 - 祈求风调雨顺',
  '逛庙会 - 体验传统民俗文化',
  '放鞭炮 - 驱邪避灾，增添喜庆',
  '赏花灯 - 元宵节赏灯猜谜'
];

activities.forEach((item, index) => {
  slide7.addText(item, {
    x: 0.7, y: 1.3 + index * 0.8, w: 8, h: 0.5,
    fontSize: 18,
    color: colors.black,
    fontFace: 'Microsoft YaHei',
    bullet: true
  });
});

// 第 8 页：春节祝福语
let slide8 = pres.addSlide();
slide8.background = { color: colors.darkRed };

slide8.addShape(pres.shapes.RECTANGLE, {
  x: 0, y: 0, w: '100%', h: '100%',
  fill: { color: colors.darkRed }
});

slide8.addText('春节祝福语', {
  x: 2, y: 0.8, w: 5.2, h: 0.6,
  fontSize: 32,
  color: colors.gold,
  bold: true,
  align: 'center',
  fontFace: 'Microsoft YaHei'
});

slide8.addShape(pres.shapes.LINE, {
  x: 3.6, y: 1.4, w: 2, h: 0,
  line: { color: colors.gold, width: 2 }
});

const blessings = [
  '新年快乐',
  '恭喜发财',
  '万事如意',
  '身体健康',
  '阖家幸福',
  '年年有余'
];

blessings.forEach((item, index) => {
  const col = index % 2;
  const row = Math.floor(index / 2);
  const x = col === 0 ? 2 : 4.7;
  const y = 1.8 + row * 1;
  
  slide8.addText(item, {
    x: x, y: y, w: 2.5, h: 0.6,
    fontSize: 24,
    color: colors.cream,
    bold: true,
    align: 'center',
    fontFace: 'Microsoft YaHei'
  });
});

// 第 9 页：结束页
let slide9 = pres.addSlide();
slide9.background = { color: colors.cream };

slide9.addText('谢谢观看', {
  x: 2, y: 2, w: 5.2, h: 0.8,
  fontSize: 44,
  color: colors.darkRed,
  bold: true,
  align: 'center',
  fontFace: 'Microsoft YaHei'
});

slide9.addText('恭喜发财 大吉大利', {
  x: 2, y: 2.9, w: 5.2, h: 0.5,
  fontSize: 20,
  color: colors.red,
  align: 'center',
  fontFace: 'Microsoft YaHei'
});

// 保存演示文稿
pres.writeFile({ fileName: '春节.pptx' })
  .then(fileName => {
    console.log('PPTX 文件创建成功:', fileName);
  })
  .catch(err => {
    console.error('创建 PPTX 文件失败:', err);
  });
