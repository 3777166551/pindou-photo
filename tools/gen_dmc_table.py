# 生成 app/src/main/java/com/pindou/app/export/DmcTable.java
# 数据源:Skytuhua/stitch-forge assets/dmc-source.json (MIT License)
# 本脚本"拥有"生成文件的全部内容;改数据先改数据源再重跑本脚本。
import json
import io

SRC = r'tools/_dmc_assets_dmc-source.json'
OUT = r'app/src/main/java/com/pindou/app/export/DmcTable.java'

d = json.load(io.open(SRC, encoding='utf-8-sig'))
rows = []
for e in d:
    code = str(e['floss']).strip()
    name = str(e['description']).strip().replace('"', "'")
    r, g, b = int(e['r']), int(e['g']), int(e['b'])
    rows.append((code, name, r, g, b))

lines = []
lines.append('package com.pindou.app.export;')
lines.append('')
lines.append('/**')
lines.append(' * DMC 绣线色号表(454 色),十字绣导出用。')
lines.append(' * 数据来源:Skytuhua/stitch-forge assets/dmc-source.json(MIT License,')
lines.append(' * Copyright (c) 2026 Skytuhua),本文件由 tools/gen_dmc_table.py 生成,')
lines.append(' * 勿手改;更新数据先换数据源再重跑生成脚本。色号与名称为事实数据,')
lines.append(' * RGB 为社区测色近似值,实物请以 DMC 官方色卡为准。')
lines.append(' */')
lines.append('public final class DmcTable {')
lines.append('')
lines.append('    public static final int COUNT = %d;' % len(rows))
lines.append('    /** 平行数组:色号 / 名称 / RGB */')
lines.append('    public static final String[] CODES = {')
buf = []
for i, (code, name, r, g, b) in enumerate(rows):
    buf.append('"%s"' % code)
    if len(buf) == 8:
        lines.append('            ' + ', '.join(buf) + ',')
        buf = []
if buf:
    lines.append('            ' + ', '.join(buf))
lines.append('    };')
lines.append('    public static final String[] NAMES = {')
buf = []
for code, name, r, g, b in rows:
    buf.append('"%s"' % name)
    if len(buf) == 4:
        lines.append('            ' + ', '.join(buf) + ',')
        buf = []
if buf:
    lines.append('            ' + ', '.join(buf))
lines.append('    };')
lines.append('    public static final int[] RGBS = {')
buf = []
for code, name, r, g, b in rows:
    buf.append('0xFF%02X%02X%02X' % (r, g, b))
    if len(buf) == 6:
        lines.append('            ' + ', '.join(buf) + ',')
        buf = []
if buf:
    lines.append('            ' + ', '.join(buf))
lines.append('    };')
lines.append('')
lines.append('    private DmcTable() {')
lines.append('    }')
lines.append('}')
io.open(OUT, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print('generated', OUT, len(rows), 'colors')
