# -*- coding: utf-8 -*-
"""总控:空闲优先级 → 路线A → 路线B → 汇总 report.md。"""
import sys
import time

sys.path.insert(0, r'F:\delete\PDAPP\proto_sanc')

import sanc_common as C  # noqa: E402


def write_report(a_res, b_res, total_s):
    import io
    lines = []
    w = lines.append
    w('# proto_sanc 原型运行报告')
    w('')
    w('- 运行时间: %s,总耗时 %.1fs,进程空闲优先级(不抢前台 CPU)' % (
        time.strftime('%Y-%m-%d %H:%M:%S'), total_s))
    w('- 豆色板: 90 色档(HEX_T1+T2+T3,与 APP 默认一致)')
    w('- 模型: 全部复用 tools/bgtest 已下载权重,无新增下载')
    w('')
    w('## 路线A(风格化管线)')
    for r in a_res:
        w('- `%s` %s' % (r.get('name'), r))
    w('')
    w('产出: out/sheet_a_*.png(逐图横向对比: Original / Current APP v2.58 / A 各变体)')
    w('')
    w('## 路线B(图纸量化)')
    for r in b_res:
        w('- `%s` %s' % (r.get('name'), r))
    w('')
    w('产出: out/sheet_b_*.png(风格化输入 / 现行量化 / 动漫模式 / 豆子渲染)')
    w('')
    w('## 指标说明')
    w('- meandE: 格子色与最近豆色的 Lab 距离均值,越低越保真(但动漫模式主动牺牲它换观感)')
    w('- colors: 用到的独立豆色数;动漫模式限 58 板 18 色 / 87 板 26 色')
    w('- dark_bead_pct: 描边深色豆占比')
    body = '\n'.join(lines)
    with open(C.safe_out('report.md'), 'w', encoding='utf-8') as f:
        f.write(body)
    print('[report] written, %d chars' % len(body), flush=True)


def main():
    C.idle_priority()
    t0 = time.perf_counter()
    import pipe_a
    import pipe_b
    a_res = pipe_a.main()
    b_res = pipe_b.main()
    write_report(a_res, b_res, time.perf_counter() - t0)
    print('ALL DONE', flush=True)


if __name__ == '__main__':
    main()
