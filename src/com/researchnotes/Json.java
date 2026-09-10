package com.researchnotes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个零依赖的最小 JSON 解析器 / 序列化器。
 * 解析结果使用 Map<String,Object> / List<Object> / String / Double / Boolean / null 表示。
 */
public final class Json {

    private Json() {
    }

    // ---------------- 解析 ----------------

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (!p.eof()) {
            throw p.error("JSON 结束后仍有多余字符");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("期望 JSON 对象 {}，实际为: " + typeName(v));
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return i >= s.length();
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + "（位置 " + i + "）");
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        char peek() {
            if (eof()) {
                throw error("意外到达 JSON 结尾");
            }
            return s.charAt(i);
        }

        boolean consume(char c) {
            if (!eof() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        void expect(char c) {
            if (!consume(c)) {
                throw error("期望字符 '" + c + "'，实际为 '" + (eof() ? "<EOF>" : peek()) + "'");
            }
        }

        Object readValue() {
            skipWs();
            char c = peek();
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                case 'f':
                    return readBoolean();
                case 'n':
                    return readNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("无法识别的 JSON 值，起始字符 '" + c + "'");
            }
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWs();
            if (consume('}')) {
                return map;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw error("对象键必须是字符串");
                }
                String key = readString();
                skipWs();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWs();
                if (consume(',')) {
                    continue;
                }
                expect('}');
                return map;
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<Object>();
            skipWs();
            if (consume(']')) {
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWs();
                if (consume(',')) {
                    continue;
                }
                expect(']');
                return list;
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) {
                    throw error("字符串未闭合");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (eof()) {
                        throw error("转义符后内容缺失");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
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
                            if (i + 4 > s.length()) {
                                throw error("\\u 转义不完整");
                            }
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException nfe) {
                                throw error("非法的 \\u 转义: " + hex);
                            }
                            break;
                        default:
                            throw error("非法的转义字符 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Boolean readBoolean() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw error("非法的布尔字面量");
        }

        Object readNull() {
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw error("非法的 null 字面量");
        }

        Object readNumber() {
            int start = i;
            if (peek() == '-') {
                i++;
            }
            readDigits();
            boolean isDouble = false;
            if (!eof() && s.charAt(i) == '.') {
                isDouble = true;
                i++;
                readDigits();
            }
            if (!eof() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                isDouble = true;
                i++;
                if (!eof() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                readDigits();
            }
            String num = s.substring(start, i);
            if (isDouble) {
                return Double.valueOf(Double.parseDouble(num));
            }
            try {
                return Long.valueOf(Long.parseLong(num));
            } catch (NumberFormatException nfe) {
                return Double.valueOf(Double.parseDouble(num));
            }
        }

        void readDigits() {
            if (eof() || !Character.isDigit(s.charAt(i))) {
                throw error("数字格式错误");
            }
            while (!eof() && Character.isDigit(s.charAt(i))) {
                i++;
            }
        }
    }

    // ---------------- 序列化 ----------------

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value.toString());
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Map) {
            writeMap(sb, (Map<?, ?>) value, indent);
        } else if (value instanceof Iterable) {
            writeList(sb, (Iterable<?>) value, indent);
        } else if (value.getClass().isArray()) {
            writeList(sb, arrayToList(value), indent);
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static List<Object> arrayToList(Object array) {
        List<Object> list = new ArrayList<Object>();
        int n = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < n; i++) {
            list.add(java.lang.reflect.Array.get(array, i));
        }
        return list;
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            pad(sb, indent + 1);
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(": ");
            write(sb, entry.getValue(), indent + 1);
        }
        sb.append('\n');
        pad(sb, indent);
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, Iterable<?> list, int indent) {
        boolean first = true;
        boolean sawOne = false;
        for (Object item : list) {
            if (first) {
                sb.append("[\n");
                first = false;
            }
            if (sawOne) {
                sb.append(",\n");
            }
            sawOne = true;
            pad(sb, indent + 1);
            write(sb, item, indent + 1);
        }
        if (!first) {
            sb.append('\n');
            pad(sb, indent);
            sb.append(']');
        } else {
            sb.append("[]");
        }
    }

    private static void pad(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
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

    private static String typeName(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Map) {
            return "object";
        }
        if (v instanceof List) {
            return "array";
        }
        if (v instanceof String) {
            return "string";
        }
        if (v instanceof Boolean) {
            return "boolean";
        }
        return "number";
    }
}
