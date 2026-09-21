# -*- coding: utf-8 -*-
# 欠曝照片门控测试:人像压暗到 45%,OLD 无提亮 vs NEW 自动提亮
from PIL import Image
im = Image.open('real_portrait.jpg').convert('RGB')
im = im.point(lambda v: int(v * 0.45))
im.save('real_portrait_dark.jpg')
print('OK dark')
