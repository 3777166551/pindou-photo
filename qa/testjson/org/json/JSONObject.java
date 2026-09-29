package org.json;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * qa 桌面测试专用迷你 org.json(第 2/3 个文件:JSONObject)。
 * 递归下降解析 + 紧凑序列化;键保持插入序(LinkedHashMap)。
 * 数字:整型且不溢出存 Long,否则 Double;布尔/字符串/null 原样。
 * 序列化转义:引号/反斜杠/控制字符(<0x20);非 ASCII 原样输出
 * (落盘走 UTF-8,与真 org.json + 应用管道的行为一致)。
 */
public class JSONObject {

    private final Map<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {
    }

    /** @throws JSONException 输入不是合法 JSON 对象(与真 org.json 同语义) */
    public JSONObject(String source) throws JSONException {
        this(new Parser(source));
    }

    private JSONObject(Parser p) throws JSONException {
        p.skipWs();
        p.expect('{');
        p.skipWs();
        if (p.peek() == '}') {
            p.advance();
            return;
        }
        while (true) {
            p.skipWs();
            if (p.next() != '"') throw p.bad("expected key string");
            String key = p.parseString();   // parseString 自己消费成对引号
            p.skipWs();
            p.expect(':');
            p.skipWs();
            map.put(key, p.parseValue());
            p.skipWs();
            char c = p.next();
            if (c == '}') return;
            if (c != ',') throw p.bad("expected ',' or '}'");
        }
    }

    public JSONObject put(String name, Object value) {
        map.put(name, value == null ? JSONObject.NULL : value);
        return this;
    }

    public JSONObject put(String name, int value) {
        return put(name, (Object) (long) value);
    }

    public JSONObject put(String name, long value) {
        return put(name, (Object) (Long) value);
    }

    public JSONObject put(String name, boolean value) {
        return put(name, (Object) (Boolean) value);
    }

    public JSONObject put(String name, double value) {
        return put(name, (Object) (Double) value);
    }

    public boolean has(String name) {
        return map.containsKey(name);
    }

    /** 键的插入序迭代(真 org.json 是无序;测试只依赖可遍历) */
    public Iterator<String> keys() {
        return map.keySet().iterator();
    }

    public Object opt(String name) {
        Object v = map.get(name);
        return v == NULL ? null : v;
    }

    public String optString(String name, String fallback) {
        Object v = opt(name);
        return v instanceof String ? (String) v : fallback;
    }

    public String optString(String name) {
        return optString(name, "");
    }

    public int optInt(String name, int fallback) {
        Object v = opt(name);
        if (v instanceof Long) return ((Long) v).intValue();
        if (v instanceof Double) return (int) Math.round((Double) v);
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public int optInt(String name) {
        return optInt(name, 0);
    }

    public long optLong(String name, long fallback) {
        Object v = opt(name);
        if (v instanceof Long) return (Long) v;
        if (v instanceof Double) return Math.round((Double) v);
        return fallback;
    }

    public long optLong(String name) {
        return optLong(name, 0L);
    }

    public boolean optBoolean(String name, boolean fallback) {
        Object v = opt(name);
        if (v instanceof Boolean) return (Boolean) v;
        return fallback;
    }

    public boolean optBoolean(String name) {
        return optBoolean(name, false);
    }

    public double optDouble(String name, double fallback) {
        Object v = opt(name);
        if (v instanceof Double) return (Double) v;
        if (v instanceof Long) return ((Long) v).doubleValue();
        return fallback;
    }

    public JSONObject optJSONObject(String name) {
        Object v = opt(name);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public JSONArray optJSONArray(String name) {
        Object v = opt(name);
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    public String getString(String name) throws JSONException {
        Object v = opt(name);
        if (v instanceof String) return (String) v;
        throw new JSONException("not a string: " + name);
    }

    public int getInt(String name) throws JSONException {
        Object v = opt(name);
        if (v instanceof Long) return ((Long) v).intValue();
        throw new JSONException("not an int: " + name);
    }

    public JSONObject getJSONObject(String name) throws JSONException {
        Object v = opt(name);
        if (v instanceof JSONObject) return (JSONObject) v;
        throw new JSONException("not an object: " + name);
    }

    public JSONArray getJSONArray(String name) throws JSONException {
        Object v = opt(name);
        if (v instanceof JSONArray) return (JSONArray) v;
        throw new JSONException("not an array: " + name);
    }

    /** 紧凑序列化(键按插入序;字符串按 JSON 规则转义) */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            Parser.quote(sb, e.getKey());
            sb.append(':').append(Parser.value(e.getValue()));
        }
        return sb.append('}').toString();
    }

    /** org.json 的 null 占位(put(name,null) 与"没有该键"区分) */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    // ---------------- 解析器(仅包内使用) ----------------

    static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        JSONException bad(String msg) {
            return new JSONException(msg + " at " + i);
        }

        char peek() throws JSONException {
            if (i >= s.length()) throw bad("unexpected end");
            return s.charAt(i);
        }

        void advance() {
            i++;
        }

        char next() throws JSONException {
            char c = peek();
            advance();
            return c;
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else return;
            }
        }

        void expect(char c) throws JSONException {
            if (next() != c) throw bad("expected '" + c + "'");
        }

        Object parseValue() throws JSONException {
            skipWs();
            char c = peek();
            if (c == '{') return new JSONObject(this);
            if (c == '[') return new JSONArray(this);
            if (c == '"') {
                advance();          // parseString 约定:开引号已被消费
                return parseString();
            }
            if (c == 't') {
                lit("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                lit("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                lit("null");
                return NULL;
            }
            return parseNumber();
        }

        private void lit(String word) throws JSONException {
            if (!s.regionMatches(i, word, 0, word.length())) throw bad("bad literal");
            i += word.length();
        }

        private Object parseNumber() throws JSONException {
            int start = i;
            if (peek() == '-') advance();
            boolean isDouble = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    i++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isDouble = true;
                    i++;
                } else {
                    break;
                }
            }
            if (i == start) throw bad("bad number");
            String num = s.substring(start, i);
            try {
                if (!isDouble) return Long.valueOf(num);
                return Double.valueOf(num);
            } catch (NumberFormatException e) {
                throw bad("bad number");
            }
        }

        String parseString() throws JSONException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"':
                        case '\\':
                        case '/':
                            sb.append(e);
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (i + 4 > s.length()) throw bad("bad \\u");
                            sb.append((char) Integer.parseInt(
                                    s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default:
                            throw bad("bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        /** 序列化字符串字面量(含转义) */
        static void quote(StringBuilder sb, String v) {
            sb.append('"');
            for (int k = 0; k < v.length(); k++) {
                char c = v.charAt(k);
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
        }

        static String value(Object v) {
            if (v == null || v == NULL) return "null";
            if (v instanceof String) {
                StringBuilder sb = new StringBuilder();
                quote(sb, (String) v);
                return sb.toString();
            }
            if (v instanceof Double) {
                double d = (Double) v;
                return d == Math.rint(d) && !Double.isInfinite(d)
                        && Math.abs(d) < 1e15 ? String.valueOf((long) d)
                        : String.valueOf(d);
            }
            return v.toString();
        }
    }
}
