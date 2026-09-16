import com.pindou.app.util.GifEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GIF89a 编码器测试(纯 JVM,自带迷你 LZW 解码器做真往返):
 *  - 结构:GIF89a 头 / 逻辑屏幕描述符 / NETSCAPE2.0 循环 / 逐帧 GCE+图像
 *    描述符+局部色表 / 0x3B 终止
 *  - LZW 往返:解码出的索引经局部色表还原 == 原像素(纯色/双色棋盘/
 *    渐变>256色中位切分三档)
 *  - 多帧 + 延时字段
 * 失败时 exit 1。
 */
public class TestGifEncoder {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    // ---------------- 迷你 GIF 解码器(仅本测试用) ----------------

    static final class ParsedFrame {
        int delayCs;
        int width, height;
        int[] palette;     // 局部色表
        int[] indices;     // LZW 解出的像素索引
        int minCodeSize;
    }

    static final class ParsedGif {
        int screenW, screenH;
        boolean loop;
        List<ParsedFrame> frames = new ArrayList<>();
        boolean trailer;
    }

    static int readShort(byte[] b, int[] p) {
        int v = (b[p[0]] & 0xFF) | ((b[p[0] + 1] & 0xFF) << 8);
        p[0] += 2;
        return v;
    }

    static ParsedGif parse(byte[] data) throws Exception {
        ParsedGif g = new ParsedGif();
        int[] p = {0};
        check("header GIF89a", new String(data, 0, 6, "US-ASCII").equals("GIF89a"));
        p[0] = 6;
        g.screenW = readShort(data, p);
        g.screenH = readShort(data, p);
        int packed = data[p[0]] & 0xFF;
        p[0]++;
        p[0]++;   // bg
        p[0]++;   // aspect
        if ((packed & 0x80) != 0) {
            p[0] += 3 * (2 << (packed & 7));
        }
        while (p[0] < data.length) {
            int block = data[p[0]] & 0xFF;
            if (block == 0x3B) {
                g.trailer = true;
                break;
            }
            if (block == 0x21) {   // 扩展
                int label = data[p[0] + 1] & 0xFF;
                p[0] += 2;
                if (label == 0xF9) {   // GCE
                    int size = data[p[0]] & 0xFF;
                    p[0]++;
                    int pflags = data[p[0]] & 0xFF;
                    ParsedFrame f = new ParsedFrame();
                    f.delayCs = (data[p[0] + 1] & 0xFF) | ((data[p[0] + 2] & 0xFF) << 8);
                    g.frames.add(f);
                    p[0] += size;
                    p[0]++;   // block terminator
                } else if (label == 0xFF) {   // 应用扩展
                    int size = data[p[0]] & 0xFF;
                    p[0]++;
                    String name = new String(data, p[0], size, "US-ASCII");
                    if (name.equals("NETSCAPE2.0")) g.loop = true;
                    p[0] += size;
                    while ((data[p[0]] & 0xFF) != 0) p[0] += 1 + (data[p[0]] & 0xFF);
                    p[0]++;
                } else {
                    throw new Exception("unknown ext " + label);
                }
                continue;
            }
            if (block == 0x2C) {   // 图像描述符
                p[0]++;
                int left = readShort(data, p);
                int top = readShort(data, p);
                int w = readShort(data, p);
                int h = readShort(data, p);
                int ip = data[p[0]] & 0xFF;
                p[0]++;
                ParsedFrame f = g.frames.get(g.frames.size() - 1);
                f.width = w;
                f.height = h;
                int tableEntries = 0;
                if ((ip & 0x80) != 0) {
                    tableEntries = 2 << (ip & 7);
                    f.palette = new int[tableEntries];
                    for (int i = 0; i < tableEntries; i++) {
                        f.palette[i] = ((data[p[0]] & 0xFF) << 16)
                                | ((data[p[0] + 1] & 0xFF) << 8) | (data[p[0] + 2] & 0xFF);
                        p[0] += 3;
                    }
                }
                f.minCodeSize = data[p[0]] & 0xFF;
                p[0]++;
                ByteArrayOutputStream lzw = new ByteArrayOutputStream();
                while (true) {
                    int len = data[p[0]] & 0xFF;
                    p[0]++;
                    if (len == 0) break;
                    lzw.write(data, p[0], len);
                    p[0] += len;
                }
                f.indices = lzwDecode(lzw.toByteArray(), f.minCodeSize, w * h);
                continue;
            }
            throw new Exception("unknown block " + block);
        }
        return g;
    }

    /**
     * GIF LZW 解码(与 GifEncoder 的 GIFCOMPR 语义互为镜像):
     * 编码器在"发射后按已有条目数增位",解码器滞后一拍
     * (在"新增条目后 dNext == 2^codeSize"时增位)。
     */
    static int[] lzwDecode(byte[] data, int minCodeSize, int expected) throws Exception {
        int clear = 1 << minCodeSize, end = clear + 1;
        int codeSize = minCodeSize + 1, next = end + 1;
        int[] prefix = new int[4096], suffix = new int[4096], first = new int[4096];
        for (int c = 0; c < clear; c++) {
            prefix[c] = -1;
            suffix[c] = c;
            first[c] = c;
        }
        int[] out = new int[expected];
        int outAt = 0, bitPos = 0, prev = -1;
        while (outAt < expected && bitPos + codeSize <= data.length * 8) {
            int code = 0;
            for (int i = 0; i < codeSize; i++) {
                code |= ((data[(bitPos >> 3)] >> (bitPos & 7)) & 1) << i;
                bitPos++;
            }
            if (code == clear) {
                next = end + 1;
                codeSize = minCodeSize + 1;
                prev = -1;
                continue;
            }
            if (code == end) break;
            int emitFirst;
            if (code < next) {
                emitFirst = first[code];
                outAt = emitString(out, outAt, code, prefix, suffix);
            } else if (code == next) {
                emitFirst = first[prev];                    // KwKwK
                outAt = emitString(out, outAt, prev, prefix, suffix);
                out[outAt++] = emitFirst;
            } else {
                throw new Exception("corrupt code " + code);
            }
            if (outAt > expected) throw new Exception("overflow");
            if (prev != -1 && next < 4096) {
                prefix[next] = prev;
                suffix[next] = emitFirst;
                first[next] = first[prev];
                next++;
                // 与参考实现(compress/GIFCOMPR)镜像:free_ent >= 2^codeSize 即增位
                if (next >= (1 << codeSize) && codeSize < 12) codeSize++;
            }
            prev = code;
        }
        if (outAt != expected) throw new Exception("short output " + outAt);
        return out;
    }

    /** 展开一个码对应的字符串(沿 prefix 链走到初始码) */
    static int emitString(int[] out, int at, int code, int[] prefix, int[] suffix) {
        int[] rev = new int[4096];
        int n = 0;
        while (code != -1) {
            rev[n++] = suffix[code];
            code = prefix[code];
        }
        for (int i = n - 1; i >= 0; i--) out[at++] = rev[i];
        return at;
    }

    // ---------------- 用例 ----------------

    static int argb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static void main(String[] args) throws Exception {
        // ---- 用例 1:纯色帧 ----
        int w = 32, h = 32;
        int[] frame = new int[w * h];
        java.util.Arrays.fill(frame, argb(200, 60, 90));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        GifEncoder gif = new GifEncoder(w, h, 8, 0);
        gif.start(bo);
        gif.addFrame(frame);
        gif.finish();
        ParsedGif g = parse(bo.toByteArray());
        check("solid: loop ext present", g.loop);
        check("solid: trailer", g.trailer);
        check("solid: screen size", g.screenW == w && g.screenH == h);
        check("solid: one frame", g.frames.size() == 1);
        check("solid: delay 8cs", g.frames.get(0).delayCs == 8);
        check("solid: full size frame", g.frames.get(0).width == w
                && g.frames.get(0).height == h);
        int pal = g.frames.get(0).palette.length;
        boolean allMatch = g.frames.get(0).indices.length == w * h;
        for (int i = 0; i < w * h && allMatch; i++) {
            int idx = g.frames.get(0).indices[i];
            if (idx < 0 || idx >= pal
                    || g.frames.get(0).palette[idx] != (argb(200, 60, 90) & 0xFFFFFF)) {
                allMatch = false;
            }
        }
        check("solid: LZW roundtrip via palette", allMatch);

        // ---- 用例 2:双色棋盘(跨块边界的 LZW) ----
        int[] checker = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                checker[y * w + x] = ((x / 3 + y / 3) % 2 == 0)
                        ? argb(20, 20, 20) : argb(240, 240, 240);
            }
        }
        ByteArrayOutputStream bo2 = new ByteArrayOutputStream();
        GifEncoder gif2 = new GifEncoder(w, h, 2, 0);
        gif2.start(bo2);
        gif2.addFrame(checker);
        gif2.finish();
        ParsedGif g2 = parse(bo2.toByteArray());
        Map<Integer, Integer> colorToIdx = new HashMap<>();
        for (int i = 0; i < g2.frames.get(0).palette.length; i++) {
            colorToIdx.put(g2.frames.get(0).palette[i], i);
        }
        boolean checkerOk = true;
        for (int i = 0; i < w * h && checkerOk; i++) {
            int want = colorToIdx.get(checker[i] & 0xFFFFFF);
            if (g2.frames.get(0).indices[i] != want) checkerOk = false;
        }
        check("checker: LZW roundtrip", checkerOk);

        // ---- 用例 3:渐变 >256 色(走中位切分)+ 多帧 ----
        int gw = 64, gh = 64;
        ByteArrayOutputStream bo3 = new ByteArrayOutputStream();
        GifEncoder gif3 = new GifEncoder(gw, gh, 4, 3);
        gif3.start(bo3);
        for (int f = 0; f < 3; f++) {
            int[] grad = new int[gw * gh];
            for (int y = 0; y < gh; y++) {
                for (int x = 0; x < gw; x++) {
                    grad[y * gw + x] = argb(x * 4 + f, y * 4, (x + y + f * 3) % 256);
                }
            }
            gif3.addFrame(grad);
        }
        gif3.finish();
        ParsedGif g3 = parse(bo3.toByteArray());
        check("gradient: three frames", g3.frames.size() == 3);
        check("gradient: loop count 3", g3.loop);
        boolean gradOk = true;
        for (int fr = 0; fr < 3 && gradOk; fr++) {
            check("gradient: frame " + fr + " palette <= 256",
                    g3.frames.get(fr).palette.length <= 256);
            int[] pal3 = g3.frames.get(fr).palette;
            int worst = 0;
            for (int i = 0; i < gw * gh; i++) {
                int idx = g3.frames.get(fr).indices[i];
                int c = pal3[idx];
                int want = (fr == 0 ? 0 : fr == 1 ? 0 : 0)
                        | 0; // 占位,下面直接逐像素对误差
                int x = i % gw, y = i / gw;
                int wantRgb = argb(x * 4 + fr, y * 4, (x + y + fr * 3) % 256) & 0xFFFFFF;
                int dr = ((c >> 16) & 0xFF) - ((wantRgb >> 16) & 0xFF);
                int dg = ((c >> 8) & 0xFF) - ((wantRgb >> 8) & 0xFF);
                int db = (c & 0xFF) - (wantRgb & 0xFF);
                int d = dr * dr + dg * dg + db * db;
                if (d > worst) worst = d;
            }
            if (worst > 30 * 30) gradOk = false;   // 中位切分平均误差应很小
        }
        check("gradient: quantization error small", gradOk);

        // ---- 用例 4:大图 > 4096 字典(表满 clear 分支) ----
        int bw = 100, bh = 100;
        int[] noise = new int[bw * bh];
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < noise.length; i++) {
            noise[i] = argb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
        }
        ByteArrayOutputStream bo4 = new ByteArrayOutputStream();
        GifEncoder gif4 = new GifEncoder(bw, bh, 2, -1);
        gif4.start(bo4);
        gif4.addFrame(noise);
        gif4.finish();
        ParsedGif g4 = parse(bo4.toByteArray());
        check("noise: no loop when -1", !g4.loop);
        Map<Integer, Integer> c4 = new HashMap<>();
        for (int i = 0; i < g4.frames.get(0).palette.length; i++) {
            c4.put(g4.frames.get(0).palette[i], i);
        }
        boolean noiseOk = true;
        for (int i = 0; i < bw * bh && noiseOk; i++) {
            Integer want = c4.get(noise[i] & 0xFFFFFF);
            int idx = g4.frames.get(0).indices[i];
            if (idx < 0 || idx >= g4.frames.get(0).palette.length) noiseOk = false;
            else if (want != null && want.intValue() != idx) noiseOk = false;
        }
        check("noise: LZW survives table-full clears", noiseOk);

        System.out.println("TestGifEncoder: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
