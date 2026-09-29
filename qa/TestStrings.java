import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 三语 strings.xml 一致性测试(纯文件解析,无 Android):
 * ①三套语言键集合完全一致(缺键/多键都算失败——历史上出过"EN/JA 帮助
 *   正文残留旧名"、导入菜单硬编码乱码,这类漂移编译器不报,只有测得出);
 * ②占位符参数集合一致(%1$d 这类,防某语言少参数运行时崩);
 * ③不允许的残留:占位串里出现 "?? "(GBK 乱码特征)。
 */
public class TestStrings {

    private static int passed = 0, failed = 0;

    private static final Pattern STRING_TAG = Pattern.compile(
            "<string name=\"([^\"]+)\">(.*)</string>\\s*$");
    /** 位置占位符(%1$d/%2$s/%3$.0f)与特殊 %,d;%% 字面量忽略 */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "%(?:([1-9]\\d*)\\$)?[,.\\d]*[dsf]");

    public static void main(String[] args) throws Exception {
        Map<String, String> zh = load("app/src/main/res/values/strings.xml");
        Map<String, String> en = load("app/src/main/res/values-en/strings.xml");
        Map<String, String> ja = load("app/src/main/res/values-ja/strings.xml");

        check("解析到足量键(zh>500)", zh.size() > 500);
        check("解析到足量键(en>500)", en.size() > 500);
        check("解析到足量键(ja>500)", ja.size() > 500);

        diffKeys("en 缺键", zh, en);
        diffKeys("en 多键", en, zh);
        diffKeys("ja 缺键", zh, ja);
        diffKeys("ja 多键", ja, zh);

        // 占位符一致性:每个键在三语里的参数集合必须相同
        List<String> mismatch = new ArrayList<>();
        for (Map.Entry<String, String> e : zh.entrySet()) {
            String k = e.getKey();
            String ev = en.get(k), jv = ja.get(k);
            if (ev == null || jv == null) continue;   // 键集差异已单独报
            if (!args(e.getValue()).equals(args(ev))
                    || !args(e.getValue()).equals(args(jv))) {
                mismatch.add(k);
            }
        }
        check("占位符三语一致(差异: " + mismatch + ")", mismatch.isEmpty());

        // GBK 乱码特征:任何语言不得含 "?? "
        List<String> junk = new ArrayList<>();
        for (Map.Entry<String, String> e : zh.entrySet()) {
            if (e.getValue().contains("?? ")) junk.add("zh:" + e.getKey());
        }
        for (Map.Entry<String, String> e : en.entrySet()) {
            if (e.getValue().contains("?? ")) junk.add("en:" + e.getKey());
        }
        for (Map.Entry<String, String> e : ja.entrySet()) {
            if (e.getValue().contains("?? ")) junk.add("ja:" + e.getKey());
        }
        check("无 ?? 乱码残留(" + junk + ")", junk.isEmpty());

        System.out.println("TestStrings: " + passed + " passed, " + failed
                + " failed");
        if (failed > 0) System.exit(1);
    }

    /** 从 a 中找出 b 没有的键(限制条数防刷屏) */
    private static void diffKeys(String label, Map<String, String> a,
                                 Map<String, String> b) {
        List<String> missing = new ArrayList<>();
        for (String k : a.keySet()) {
            if (!b.containsKey(k)) missing.add(k);
        }
        String show = missing.size() > 12
                ? missing.subList(0, 12) + "...+" + (missing.size() - 12)
                : missing.toString();
        check(label + "(" + missing.size() + "): " + show, missing.isEmpty());
    }

    /**
     * 提取参数「位号+种类」集合(排序后比较):位置写法与出现顺序都是
     * 各语言的合法自由度(英日语序不同会把 %2$ 提到 %1$ 前面,运行时
     * 正确);必须一致的是"每个位号被同种参数消费"。
     */
    private static List<String> args(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(s);
        int seq = 0;
        while (m.find()) {
            String body = m.group().substring(1);   // 去掉 %
            int dollar = body.indexOf('$');
            String pos = dollar >= 0 ? body.substring(0, dollar)
                    : String.valueOf(++seq);
            out.add(pos + body.substring(body.length() - 1));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static Map<String, String> load(String path) throws Exception {
        Map<String, String> out = new HashMap<>();
        File f = new File(path);
        if (!f.exists()) throw new IllegalStateException("missing " + path);
        String xml = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                StandardCharsets.UTF_8);
        for (String line : xml.split("\n")) {
            Matcher m = STRING_TAG.matcher(line.trim());
            if (m.matches()) {
                out.put(m.group(1), m.group(2));
            }
        }
        return out;
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
