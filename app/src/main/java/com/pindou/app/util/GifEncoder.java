package com.pindou.app.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java 的 GIF89a 动图编码器(生长动画导出用):逐帧局部调色板 +
 * LZW 压缩 + NETSCAPE2.0 循环扩展。无任何第三方依赖,零 Android 引用,
 * 桌面 JVM 可单测(qa/TestGifEncoder 里带了一个迷你解码器做往返校验)。
 * LZW 专利于 2004 年到期,GIF 编解码无许可限制。
 *
 * 用法(流式,内存只需一帧):
 *   GifEncoder gif = new GifEncoder(w, h, 0);   // 0 = 无限循环
 *   gif.start(out);
 *   for 每帧: gif.addFrame(argbPixels);          // w*h 个 0xAARRGGBB
 *   gif.finish();
 */
public final class GifEncoder {

    private final int width, height, delayCs, loopCount;
    private OutputStream out;
    private boolean started, finished;

    /** @param delayCs 每帧延时(百分之一秒);loopCount 0=无限循环,-1=不循环 */
    public GifEncoder(int width, int height, int delayCs, int loopCount) {
        this.width = width;
        this.height = height;
        this.delayCs = Math.max(2, delayCs);
        this.loopCount = loopCount;
    }

    /** 写文件头 + 逻辑屏幕描述符 + 循环扩展 */
    public void start(OutputStream out) throws IOException {
        this.out = out;
        // GIF89a 头
        out.write('G');
        out.write('I');
        out.write('F');
        out.write('8');
        out.write('9');
        out.write('a');
        // 逻辑屏幕描述符:全局色表只给 2 色占位,真调色板逐帧局部
        writeShort(width);
        writeShort(height);
        out.write(0x80 | 0x70);   // GCT=1, colorRes=7, 2^1=2 色
        out.write(0);
        out.write(0);
        writeRgb(0, 0, 0);        // 占位全局色表:黑
        writeRgb(255, 255, 255);  // 白
        if (loopCount >= 0) {
            // NETSCAPE2.0 循环扩展
            out.write(0x21);
            out.write(0xFF);
            out.write(11);
            out.write("NETSCAPE2.0".getBytes("US-ASCII"));
            out.write(3);
            out.write(1);
            writeShort(loopCount);
            out.write(0);
        }
        started = true;
    }

    /** 追加一帧(整帧覆盖,ARGB,alpha 被忽略按不透明处理) */
    public void addFrame(int[] argb) throws IOException {
        if (!started || finished) throw new IOException("gif not started/finished");
        if (argb.length != width * height) throw new IOException("bad frame size");

        // 1. 统计本帧不重复颜色(带像素计数,中位切分要用)
        Map<Integer, Integer> hist = new HashMap<>();
        for (int px : argb) {
            int c = px & 0xFFFFFF;
            Integer n0 = hist.get(c);
            hist.put(c, n0 == null ? 1 : n0 + 1);
        }
        List<Integer> distinct = new ArrayList<>(hist.keySet());

        // 2. 调色板 + 索引映射
        int[] palette;
        int[] indices = new int[argb.length];
        if (distinct.size() <= 256) {
            palette = new int[distinct.size()];
            for (int i = 0; i < distinct.size(); i++) {
                palette[i] = distinct.get(i);
                hist.put(palette[i], i);
            }
            for (int i = 0; i < argb.length; i++) {
                indices[i] = hist.get(argb[i] & 0xFFFFFF);
            }
        } else {
            palette = medianCut(hist, 256);
            Map<Integer, Integer> map = new HashMap<>();
            for (int i = 0; i < palette.length; i++) {
                map.put(palette[i], i);
            }
            for (int i = 0; i < argb.length; i++) {
                int c = argb[i] & 0xFFFFFF;
                Integer at = map.get(c);
                if (at == null) {
                    at = nearest(palette, c);
                    map.put(c, at);
                }
                indices[i] = at;
            }
        }

        // 3. 图形控制扩展(延时,无透明)
        out.write(0x21);
        out.write(0xF9);
        out.write(4);
        out.write(0x04);          // disposal=1(不动底)
        writeShort(delayCs);
        out.write(0);             // 无透明色
        out.write(0);

        // 4. 图像描述符 + 局部色表
        out.write(0x2C);
        writeShort(0);
        writeShort(0);
        writeShort(width);
        writeShort(height);
        int bits = minCodeSize(palette.length);
        int tableEntries = 2;
        while (tableEntries < palette.length) tableEntries <<= 1;
        int sizeField = 0;                            // GIF:色表项数 = 2^(sizeField+1)
        while ((1 << (sizeField + 1)) < tableEntries) sizeField++;
        out.write(0x80 | sizeField);                  // 局部色表 flag + size
        for (int i = 0; i < tableEntries; i++) {
            int c = i < palette.length ? palette[i] : 0;
            writeRgb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
        }
        out.write(bits);

        // 5. LZW 压缩 + 子块封装
        ByteArrayOutputStream lzw = new ByteArrayOutputStream();
        lzwCompress(indices, bits, lzw);
        byte[] data = lzw.toByteArray();
        int at = 0;
        while (at < data.length) {
            int chunk = Math.min(255, data.length - at);
            out.write(chunk);
            out.write(data, at, chunk);
            at += chunk;
        }
        out.write(0);             // 子块结束
    }

    /** 收尾:写 0x3B 终止符 */
    public void finish() throws IOException {
        if (finished) return;
        out.write(0x3B);
        out.flush();
        finished = true;
    }

    // ---------------- LZW ----------------

    private static void lzwCompress(int[] indices, int minCodeSize,
                                    OutputStream out) throws IOException {
        int clear = 1 << minCodeSize;
        int end = clear + 1;
        int codeSize = minCodeSize + 1;
        int next = end + 1;
        Map<Integer, Integer> dict = new HashMap<>();
        BitWriter bw = new BitWriter(out);

        // 码长增长时机(GIFCOMPR 语义,解码器滞后一拍镜像):
        // 发射当前码后,若"已有条目数 next"已超出当前码长可表示范围
        // (next > 2^codeSize - 1),则下一发开始用更长的码。
        bw.write(clear, codeSize);
        int prefix = indices[0];
        for (int i = 1; i < indices.length; i++) {
            int k = indices[i];
            Integer hit = dict.get((prefix << 8) | k);
            if (hit != null) {
                prefix = hit;
                continue;
            }
            bw.write(prefix, codeSize);
            if (next > (1 << codeSize) - 1 && codeSize < 12) {
                codeSize++;
            }
            if (next < 4096) {
                dict.put((prefix << 8) | k, next);
                next++;
            } else {
                // 表满:发射 clear 重来(与参考实现 cl_block 一致)
                bw.write(clear, codeSize);
                dict.clear();
                next = end + 1;
                codeSize = minCodeSize + 1;
            }
            prefix = k;
        }
        bw.write(prefix, codeSize);
        if (next > (1 << codeSize) - 1 && codeSize < 12) {
            codeSize++;
        }
        bw.write(end, codeSize);
        bw.flush();
    }

    /** GIF 位流:LSB 优先打包 */
    private static final class BitWriter {
        private final OutputStream out;
        private int bitBuf, bitCnt;

        BitWriter(OutputStream out) {
            this.out = out;
        }

        void write(int code, int codeSize) throws IOException {
            bitBuf |= code << bitCnt;
            bitCnt += codeSize;
            while (bitCnt >= 8) {
                out.write(bitBuf & 0xFF);
                bitBuf >>>= 8;
                bitCnt -= 8;
            }
        }

        void flush() throws IOException {
            if (bitCnt > 0) {
                out.write(bitBuf & 0xFF);
                bitBuf = 0;
                bitCnt = 0;
            }
        }
    }

    // ---------------- 调色板 ----------------

    /** LZW 最小码长:能表达 paletteSize-1 的位数,最小 2 */
    private static int minCodeSize(int paletteSize) {
        int bits = 2;
        while ((1 << bits) < paletteSize) bits++;
        return Math.min(8, bits);
    }

    /** 中位切分法:把不重复颜色压到 maxColors 个代表色 */
    static int[] medianCut(Map<Integer, Integer> hist, int maxColors) {
        List<long[]> boxes = new ArrayList<>();
        // box = {rMin,rMax,gMin,gMax,bMin,bMax,population}(population 仅本 box 内像素计权)
        List<Map<Integer, Integer>> boxColors = new ArrayList<>();
        boxColors.add(hist);
        boxes.add(bounds(hist));
        while (boxes.size() < maxColors) {
            // 挑"像素数 × 最长边"最大的 box 来切
            int pick = -1;
            long best = -1;
            for (int i = 0; i < boxes.size(); i++) {
                long[] b = boxes.get(i);
                long range = Math.max(b[1] - b[0], Math.max(b[3] - b[2], b[5] - b[4]));
                long score = b[6] * (range + 1);
                if (score > best) {
                    best = score;
                    pick = i;
                }
            }
            if (pick < 0 || best <= 0) break;
            Map<Integer, Integer> colors = boxColors.get(pick);
            if (colors.size() < 2) break;
            long[] b = boxes.get(pick);
            int channel;
            if (b[1] - b[0] >= b[3] - b[2] && b[1] - b[0] >= b[5] - b[4]) channel = 0;
            else if (b[3] - b[2] >= b[5] - b[4]) channel = 1;
            else channel = 2;

            // 按目标通道排序找中位切点
            List<Integer> keys = new ArrayList<>(colors.keySet());
            final int ch = channel;
            keys.sort(new java.util.Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return channelOf(a, ch) - channelOf(b, ch);
                }
            });
            long half = 0, total = boxes.get(pick)[6];
            int cut = 0;
            for (int i = 0; i < keys.size(); i++) {
                half += colors.get(keys.get(i));
                cut = i + 1;
                if (half >= total / 2 && cut < keys.size()) break;
            }
            Map<Integer, Integer> left = new HashMap<>();
            Map<Integer, Integer> right = new HashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                (i < cut ? left : right).put(keys.get(i), colors.get(keys.get(i)));
            }
            if (left.isEmpty() || right.isEmpty()) break;
            boxes.remove(pick);
            boxColors.remove(pick);
            boxes.add(bounds(left));
            boxColors.add(left);
            boxes.add(bounds(right));
            boxColors.add(right);
        }
        int[] out = new int[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            out[i] = avgColor(boxColors.get(i));
        }
        return out;
    }

    private static int channelOf(int rgb, int ch) {
        return ch == 0 ? (rgb >> 16) & 0xFF : ch == 1 ? (rgb >> 8) & 0xFF : rgb & 0xFF;
    }

    private static long[] bounds(Map<Integer, Integer> colors) {
        int rMin = 255, rMax = 0, gMin = 255, gMax = 0, bMin = 255, bMax = 0;
        long pop = 0;
        for (Map.Entry<Integer, Integer> e : colors.entrySet()) {
            int c = e.getKey();
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            if (r < rMin) rMin = r;
            if (r > rMax) rMax = r;
            if (g < gMin) gMin = g;
            if (g > gMax) gMax = g;
            if (b < bMin) bMin = b;
            if (b > bMax) bMax = b;
            pop += e.getValue();
        }
        return new long[]{rMin, rMax, gMin, gMax, bMin, bMax, pop};
    }

    private static int avgColor(Map<Integer, Integer> colors) {
        long r = 0, g = 0, b = 0, n = 0;
        for (Map.Entry<Integer, Integer> e : colors.entrySet()) {
            int c = e.getKey();
            long w = e.getValue();
            r += ((c >> 16) & 0xFF) * w;
            g += ((c >> 8) & 0xFF) * w;
            b += (c & 0xFF) * w;
            n += w;
        }
        if (n == 0) return 0;
        return (((int) (r / n)) << 16) | (((int) (g / n)) << 8) | (int) (b / n);
    }

    /** 找最近代表色(线性扫,<=256 项,只对"新出现"的颜色调用) */
    private static int nearest(int[] palette, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int c = palette[i];
            int dr = r - ((c >> 16) & 0xFF), dg = g - ((c >> 8) & 0xFF), db = b - (c & 0xFF);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ---------------- 小工具 ----------------

    private void writeShort(int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private void writeRgb(int r, int g, int b) throws IOException {
        out.write(r);
        out.write(g);
        out.write(b);
    }
}
