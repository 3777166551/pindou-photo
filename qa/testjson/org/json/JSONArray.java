package org.json;

import java.util.ArrayList;
import java.util.List;

/**
 * qa 桌面测试专用迷你 org.json(第 3/3 个文件:JSONArray)。
 * 与 JSONObject.Parser 共用递归下降解析;实现应用用到的面:
 * put / length / opt 系 / get 系 / toString。
 */
public class JSONArray {

    private final List<Object> list = new ArrayList<>();

    public JSONArray() {
    }

    /** @throws JSONException 输入不是合法 JSON 数组 */
    public JSONArray(String source) throws JSONException {
        this(new JSONObject.Parser(source));
    }

    JSONArray(JSONObject.Parser p) throws JSONException {
        p.skipWs();
        p.expect('[');
        p.skipWs();
        if (p.peek() == ']') {
            p.advance();
            return;
        }
        while (true) {
            p.skipWs();
            list.add(p.parseValue());
            p.skipWs();
            char c = p.next();
            if (c == ']') return;
            if (c != ',') throw p.bad("expected ',' or ']'");
        }
    }

    public JSONArray put(Object value) {
        list.add(value == null ? JSONObject.NULL : value);
        return this;
    }

    public JSONArray put(int value) {
        return put((Object) (long) value);
    }

    public JSONArray put(long value) {
        return put((Object) (Long) value);
    }

    public JSONArray put(boolean value) {
        return put((Object) (Boolean) value);
    }

    public int length() {
        return list.size();
    }

    public Object opt(int index) {
        if (index < 0 || index >= list.size()) return null;
        Object v = list.get(index);
        return v == JSONObject.NULL ? null : v;
    }

    public int optInt(int index, int fallback) {
        Object v = opt(index);
        return v instanceof Long ? ((Long) v).intValue() : fallback;
    }

    public int optInt(int index) {
        return optInt(index, 0);
    }

    public String optString(int index, String fallback) {
        Object v = opt(index);
        return v instanceof String ? (String) v : fallback;
    }

    public JSONObject optJSONObject(int index) {
        Object v = opt(index);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public JSONArray optJSONArray(int index) {
        Object v = opt(index);
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object v = opt(index);
        if (v instanceof JSONObject) return (JSONObject) v;
        throw new JSONException("not an object at " + index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(JSONObject.Parser.value(list.get(i)));
        }
        return sb.append(']').toString();
    }
}
