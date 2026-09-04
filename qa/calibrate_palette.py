# -*- coding: utf-8 -*-
# 色板校准工具:用 beadcolors (MIT, https://github.com/maxcleme/beadcolors)
# gen/v3 数据核对 BeadBrandCharts.java 里四个品牌色号表的 RGB 值。
# 只替换 HEX,保留代码与名称;不带 --apply 只输出对比报告。
# 用法: python qa/calibrate_palette.py [--apply]
import io, re, os, sys, math

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
JAVA = os.path.join(REPO, 'app', 'src', 'main', 'java', 'com', 'pindou', 'app',
                    'bead', 'BeadBrandCharts.java')
CSV = {
    'DATA_ARTKAL_S': 'artkal_s.csv',
    'DATA_MARD': 'mard.csv',
    'DATA_PERLER': 'perler.csv',
    'DATA_HAMA': 'hama.csv',
}

def lab(rgb):
    r, g, b = [(rgb >> s & 0xFF) / 255.0 for s in (16, 8, 0)]
    def f(t):
        return t / 12.92 if t <= 0.04045 else ((t + 0.055) / 1.055) ** 2.4
    x = (f(r) * 0.4124564 + f(g) * 0.3575761 + f(b) * 0.1804375) / 0.95047
    y = (f(r) * 0.2126729 + f(g) * 0.7151522 + f(b) * 0.0721750)
    z = (f(r) * 0.0193339 + f(g) * 0.1191920 + f(b) * 0.9503041) / 1.08883
    def fi(t):
        return t ** (1 / 3.0) if t > 0.008856 else 7.787 * t + 16.0 / 116.0
    fx, fy, fz = fi(x), fi(y), fi(z)
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))

def d2(a, b):
    return (a[0]-b[0])**2 + (a[1]-b[1])**2 + (a[2]-b[2])**2

def load_csv(path):
    out = {}
    for ln in io.open(path, encoding='utf-8').read().splitlines():
        parts = ln.split(',')
        if len(parts) < 6:
            continue
        code = parts[0].strip().upper()
        try:
            rgb = (int(parts[3]) << 16) | (int(parts[4]) << 8) | int(parts[5])
        except ValueError:
            continue
        out[code] = (rgb, parts[1].strip())
    return out

def main(apply=False):
    src = io.open(JAVA, encoding='utf-8').read()
    report, changed_total = [], 0
    for arr, csvf in CSV.items():
        m = re.search(r'private static final String\[\] ' + arr + r' = \{(.*?)\n    \};',
                      src, re.S)
        if not m:
            report.append('!! array not found: ' + arr)
            continue
        body = m.group(1)
        entries = re.findall(r'"([^"]+)"', body)
        bc = load_csv(os.path.join(HERE, 'palette_data', csvf))
        matched = missing = 0
        diffs, new_entries = [], []
        for e in entries:
            parts = e.split('|')
            code = parts[0].strip().upper()
            old_rgb = int(parts[2], 16)
            if code in bc:
                matched += 1
                new_rgb, bc_name = bc[code]
                if new_rgb != old_rgb:
                    d = math.sqrt(d2(lab(old_rgb), lab(new_rgb)))
                    diffs.append((code, parts[1], old_rgb, new_rgb, d))
                    parts[2] = '%06X' % new_rgb
                    changed_total += 1
            else:
                missing += 1
            new_entries.append('|'.join(parts))
        new_body = body
        for old_e, new_e in zip(entries, new_entries):
            if old_e != new_e:
                new_body = new_body.replace('"' + old_e + '"', '"' + new_e + '"', 1)
        src = src[:m.start(1)] + new_body + src[m.end(1):]
        diffs.sort(key=lambda t: -t[4])
        report.append('== %s: %d 条 | 匹配 %d | CSV缺号 %d | 改色 %d' %
                      (arr, len(entries), matched, missing, len(diffs)))
        for code, name, old, new, d in diffs[:8]:
            report.append('   %s %-12s %06X -> %06X  ΔE=%.1f' %
                          (code, name[:12], old, new, d))
        if len(diffs) > 8:
            report.append('   ... 还有 %d 处' % (len(diffs) - 8))

    print('\n'.join(report))
    print('TOTAL CHANGED:', changed_total)
    if apply and changed_total:
        io.open(JAVA, 'w', encoding='utf-8', newline='').write(src)
        print('WRITTEN')

if __name__ == '__main__':
    main('--apply' in sys.argv)
