# 拼豆图纸分享格式 (pindou-pattern) v1

这是 PindouPhoto 定义的**开放图纸交换格式**:自含色板、不依赖任何特定 APP、
纯 JSON 可人工阅读。任何工具都可以按本文档导出/导入,欢迎其他拼豆软件支持。

文件扩展名建议 `.json`,MIME 类型 `application/json`。

## 顶层结构

```json
{
  "format": "pindou-pattern",
  "version": 1,
  "app": "PindouPhoto",
  "name": "小猫挂件",
  "savedAt": 1725400000000,
  "cols": 58,
  "rows": 58,
  "round": false,
  "colors": [
    { "code": 1, "name": "白色", "rgb": 16777215 },
    { "code": 2, "name": "黑色", "rgb": 0 }
  ],
  "cells": [-1, 120, 0, 35, 1, 40, -1, 25],
  "note": "",
  "source": {
    "app": "PindouPhoto",
    "version": "2.31",
    "url": "https://github.com/3777166551/pindou-photo"
  }
}
```

## 字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `format` | string | ✔ | 固定 `"pindou-pattern"` |
| `version` | int | ✔ | 格式版本,当前为 `1` |
| `app` | string | | 生成文件的应用名 |
| `name` | string | | 图纸名(展示用) |
| `savedAt` | long | | 保存时间戳(毫秒) |
| `cols` / `rows` | int | ✔ | 网格宽高(4~400) |
| `round` | bool | | 是否圆形板:`true` 时内切圆以外视为板外 |
| `colors` | array | ✔ | **自含色板**,按用量从多到少排序 |
| `colors[].code` | int | | 该色在原品牌里的色号(整数,0 = 无) |
| `colors[].name` | string | | 颜色名(展示用) |
| `colors[].rgb` | int | | 0xRRGGBB 的十进制值 |
| `cells` | array | ✔ | **行程编码(RLE)**,见下 |
| `note` | string | | 作者备注 |
| `source` | object | | 出处信息(可选,方便溯源) |

## cells 行程编码

`cells` 是一维数组,按行优先(从上到下、从左到右)描述 `cols × rows` 个格子,
每两个元素为一组:`[值, 重复次数]`。

- `值 = -1` 表示空格(不摆豆)
- `值 = k`(k ≥ 0)表示该格使用 `colors[k]` 的颜色

例:`[-1, 120, 0, 35, 1, 40, -1, 25]` 表示:120 个空格、35 格 `colors[0]`、
40 格 `colors[1]`、25 个空格;所有组长度之和必须恰好等于 `cols × rows`。

## 设计约定

1. **自含**:颜色表随文件携带,不依赖导入方安装某种色板;导入方可以原样使用,
   也可以把 `rgb` 映射到自己的色板体系。
2. **紧凑**:RLE 让典型图纸的文件保持在几十 KB;
3. **向前兼容**:未知字段应忽略;`version` 大于导入方支持的版本时,
   导入方应明确拒绝而不是猜测。
4. **透明/空格**:圆形板、异形板、抠图留空都用 `-1` 表示。
