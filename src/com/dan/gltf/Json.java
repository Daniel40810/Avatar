package com.dan.gltf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-Leser des Laders (nur Lesen): Objekte werden zu {@link LinkedHashMap}, Felder zu
 * {@link ArrayList}, Zahlen zu {@link Double}. Eigene Kopie, damit das Package für sich steht.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw j.err("Text nach dem Ende");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o, String key) {
        Object v = ((Map<String, Object>) o).get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o, String key) {
        Object v = ((Map<String, Object>) o).get(key);
        return v instanceof List ? (List<Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static double num(Object o, String key, double fallback) {
        Object v = ((Map<String, Object>) o).get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    public static String str(Object o, String key) {
        Object v = ((Map<String, Object>) o).get(key);
        return v == null ? null : String.valueOf(v);
    }

    private Object value() {
        if (i >= s.length()) throw err("unerwartetes Ende");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': lit("true"); return Boolean.TRUE;
            case 'f': lit("false"); return Boolean.FALSE;
            case 'n': lit("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws(); expect(':'); ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("',' oder '}' erwartet");
        }
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<>();
        i++; ws();
        if (peek() == ']') { i++; return a; }
        while (true) {
            ws();
            a.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return a;
            if (c != ',') throw err("',' oder ']' erwartet");
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> b.append(e);
                }
            } else b.append(c);
        }
    }

    private Double number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err("Wert erwartet");
        return Double.valueOf(s.substring(st, i));
    }

    private void lit(String w) {
        if (!s.startsWith(w, i)) throw err(w + " erwartet");
        i += w.length();
    }

    private char peek() { return i < s.length() ? s.charAt(i) : 0; }

    private void expect(char c) {
        if (peek() != c) throw err("'" + c + "' erwartet");
        i++;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException err(String m) {
        return new IllegalArgumentException("JSON, Zeichen " + i + ": " + m);
    }
}
