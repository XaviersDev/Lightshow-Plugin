package ru.lightshow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Компилятор математических выражений в дерево (AST).
 *
 * Отличия от старого парсера:
 *  - ИСПРАВЛЕН приоритет степени: cos(t)^3 = (cos t)^3, а не cos(t^3).
 *    Именно из-за этого раньше работал только знак бесконечности, а астроида/роза/сердце превращались в кашу.
 *  - Компиляция один раз, потом миллионы быстрых eval() без повторного разбора строки.
 *  - Переменные: t, u, i, n, T (секунды с начала), p (прогресс 0..1) + свои через let.
 *  - Сравнения и if() — можно делать логику прямо в формуле.
 */
public final class Expr {

    public interface Node { double ev(Ctx c); }

    /** Контекст вычисления: слоты переменных. */
    public static final class Ctx {
        public double[] v;
        private long seed = 0x9E3779B97F4A7C15L;
        public Ctx(int size) { v = new double[Math.max(size, 8)]; }
        public void fit(int size) { if (v.length < size) v = new double[size]; }
        public double rnd() {
            seed ^= seed << 13; seed ^= seed >>> 7; seed ^= seed << 17;
            return ((seed >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
        }
    }

    /** Таблица символов: имя переменной -> индекс слота. */
    public static final class Scope {
        public final LinkedHashMap<String, Integer> slots = new LinkedHashMap<String, Integer>();
        public Scope() { for (String s : BASE) slots.put(s, slots.size()); }
        public int alloc(String n) {
            Integer i = slots.get(n);
            if (i != null) return i;
            int k = slots.size(); slots.put(n, k); return k;
        }
        public Integer get(String n) { return slots.get(n); }
        public int size() { return slots.size(); }
    }

    public static final String[] BASE = { "t", "u", "i", "n", "T", "p" };
    public static final int S_T = 0, S_U = 1, S_I = 2, S_N = 3, S_TIME = 4, S_PROG = 5;

    /** Функция, добавленная сторонним плагином. */
    public interface Fn { double apply(double[] args); }

    private static final java.util.HashMap<String, Object[]> CUSTOM = new java.util.HashMap<String, Object[]>();

    /** arity < 0 — любое число аргументов. */
    public static void registerFunction(String name, int arity, Fn fn) {
        CUSTOM.put(name.toLowerCase(java.util.Locale.ROOT), new Object[] { arity, fn });
    }
    public static boolean hasFunction(String name) { return CUSTOM.containsKey(name.toLowerCase(java.util.Locale.ROOT)); }
    public static void unregisterFunction(String name) { CUSTOM.remove(name.toLowerCase(java.util.Locale.ROOT)); }
    public static java.util.Set<String> customFunctions() { return CUSTOM.keySet(); }

    public static final class ParseError extends RuntimeException {
        public final int pos;
        public ParseError(String msg, int pos) { super(msg); this.pos = pos; }
    }

    /** Результат компиляции. animated = формула зависит от времени (T/p/rand). */
    public static final class Compiled {
        public final Node node; public final boolean animated;
        Compiled(Node n, boolean a) { node = n; animated = a; }
        public double ev(Ctx c) { return node.ev(c); }
    }

    public static Compiled compile(String src, Scope sc) {
        Parser p = new Parser(src, sc);
        Node n = p.parseAll();
        return new Compiled(n, p.animated);
    }

    /** Быстрая проверка формулы без запуска шоу. */
    public static String validate(String src, Scope sc) {
        try { compile(src, sc); return null; }
        catch (ParseError e) { return e.getMessage() + " (позиция " + (e.pos + 1) + ")"; }
        catch (Exception e) { return "не удалось разобрать выражение"; }
    }

    // ------------------------------------------------------------------ парсер

    private static final class Parser {
        private final String s; private final Scope sc;
        private int pos = 0; boolean animated = false;

        Parser(String s, Scope sc) { this.s = s; this.sc = sc; }

        Node parseAll() {
            Node n = cmp();
            ws();
            if (pos < s.length()) throw new ParseError("лишний символ '" + s.charAt(pos) + "'", pos);
            return n;
        }

        private void ws() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

        private boolean eat(char c) { ws(); if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; } return false; }

        private boolean eat2(String op) {
            ws();
            if (s.startsWith(op, pos)) { pos += op.length(); return true; }
            return false;
        }

        /** Сравнения — дают 1.0 или 0.0. */
        Node cmp() {
            Node a = expr();
            ws();
            if (eat2("<=")) { Node b = expr(); return c -> a.ev(c) <= b.ev(c) ? 1 : 0; }
            if (eat2(">=")) { Node b = expr(); return c -> a.ev(c) >= b.ev(c) ? 1 : 0; }
            if (eat2("==")) { Node b = expr(); return c -> a.ev(c) == b.ev(c) ? 1 : 0; }
            if (eat2("!=")) { Node b = expr(); return c -> a.ev(c) != b.ev(c) ? 1 : 0; }
            if (eat2("<"))  { Node b = expr(); return c -> a.ev(c) <  b.ev(c) ? 1 : 0; }
            if (eat2(">"))  { Node b = expr(); return c -> a.ev(c) >  b.ev(c) ? 1 : 0; }
            return a;
        }

        Node expr() {
            Node x = term();
            for (;;) {
                if (eat('+')) { final Node a = x, b = term(); x = c -> a.ev(c) + b.ev(c); }
                else if (eat('-')) { final Node a = x, b = term(); x = c -> a.ev(c) - b.ev(c); }
                else return x;
            }
        }

        Node term() {
            Node x = unary();
            for (;;) {
                if (eat('*')) { final Node a = x, b = unary(); x = c -> a.ev(c) * b.ev(c); }
                else if (eat('/')) { final Node a = x, b = unary(); x = c -> { double d = b.ev(c); return d == 0 ? 0 : a.ev(c) / d; }; }
                else if (eat('%')) { final Node a = x, b = unary(); x = c -> { double d = b.ev(c); return d == 0 ? 0 : a.ev(c) % d; }; }
                else return x;
            }
        }

        Node unary() {
            ws();
            if (eat('-')) { final Node a = unary(); return c -> -a.ev(c); }
            if (eat('+')) return unary();
            return power();
        }

        /** ВАЖНО: степень применяется к уже готовому атому (включая вызов функции). */
        Node power() {
            Node base = atom();
            ws();
            if (eat('^')) { final Node b = base, e = unary(); return c -> Math.pow(b.ev(c), e.ev(c)); }
            return base;
        }

        Node atom() {
            ws();
            if (pos >= s.length()) throw new ParseError("выражение оборвано", pos);
            char ch = s.charAt(pos);

            if (ch == '(') { pos++; Node n = cmp(); if (!eat(')')) throw new ParseError("нет закрывающей скобки", pos); return n; }

            if ((ch >= '0' && ch <= '9') || ch == '.') {
                int st = pos;
                while (pos < s.length() && ((s.charAt(pos) >= '0' && s.charAt(pos) <= '9') || s.charAt(pos) == '.')) pos++;
                final double d;
                try { d = Double.parseDouble(s.substring(st, pos)); }
                catch (NumberFormatException e) { throw new ParseError("плохое число '" + s.substring(st, pos) + "'", st); }
                return c -> d;
            }

            if (Character.isLetter(ch) || ch == '_') {
                int st = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
                String name = s.substring(st, pos);
                ws();
                if (pos < s.length() && s.charAt(pos) == '(') { pos++; return func(name, st); }
                return varOrConst(name, st);
            }
            throw new ParseError("непонятный символ '" + ch + "'", pos);
        }

        private Node varOrConst(String name, int st) {
            String low = name.toLowerCase();
            Integer own = sc.get(name);          // свои let-переменные важнее констант
            if (own == null) {
                if (low.equals("pi")) return c -> Math.PI;
                if (low.equals("tau")) return c -> Math.PI * 2;
                if (low.equals("e")) return c -> Math.E;
                if (low.equals("phi")) return c -> 1.6180339887498949;
            }
            Integer slot = own;
            if (slot == null) {
                if (low.equals("x") || low.equals("y") || low.equals("z"))
                    throw new ParseError("'" + name + "' — это координата, её нельзя использовать как переменную (назови let-переменную иначе)", st);
                throw new ParseError("неизвестная переменная '" + name + "' (объяви её через let " + name + "=...)", st);
            }
            if (slot == S_TIME || slot == S_PROG) animated = true;
            final int k = slot;
            return c -> c.v[k];
        }

        private Node[] args(String fn, int st) {
            java.util.ArrayList<Node> list = new java.util.ArrayList<Node>();
            ws();
            if (eat(')')) return new Node[0];
            for (;;) {
                list.add(cmp());
                if (eat(',')) continue;
                if (eat(')')) break;
                throw new ParseError("ожидалась ',' или ')' в функции " + fn + "()", pos);
            }
            return list.toArray(new Node[0]);
        }

        private void need(String fn, Node[] a, int n, int st) {
            if (a.length != n) throw new ParseError("функция " + fn + "() ждёт " + n + " аргумент(а), а получила " + a.length, st);
        }

        private Node func(String rawName, int st) {
            String f = rawName.toLowerCase();
            final Node[] a = args(f, st);
            switch (f) {
                case "sin": need(f,a,1,st); return c -> Math.sin(a[0].ev(c));
                case "cos": need(f,a,1,st); return c -> Math.cos(a[0].ev(c));
                case "tan": need(f,a,1,st); return c -> Math.tan(a[0].ev(c));
                case "asin": need(f,a,1,st); return c -> Math.asin(clamp(a[0].ev(c), -1, 1));
                case "acos": need(f,a,1,st); return c -> Math.acos(clamp(a[0].ev(c), -1, 1));
                case "atan": need(f,a,1,st); return c -> Math.atan(a[0].ev(c));
                case "atan2": need(f,a,2,st); return c -> Math.atan2(a[0].ev(c), a[1].ev(c));
                case "sinh": need(f,a,1,st); return c -> Math.sinh(a[0].ev(c));
                case "cosh": need(f,a,1,st); return c -> Math.cosh(a[0].ev(c));
                case "tanh": need(f,a,1,st); return c -> Math.tanh(a[0].ev(c));
                case "sqrt": need(f,a,1,st); return c -> Math.sqrt(Math.max(0, a[0].ev(c)));
                case "cbrt": need(f,a,1,st); return c -> Math.cbrt(a[0].ev(c));
                case "abs": need(f,a,1,st); return c -> Math.abs(a[0].ev(c));
                case "sign": need(f,a,1,st); return c -> Math.signum(a[0].ev(c));
                case "floor": need(f,a,1,st); return c -> Math.floor(a[0].ev(c));
                case "ceil": need(f,a,1,st); return c -> Math.ceil(a[0].ev(c));
                case "round": need(f,a,1,st); return c -> (double) Math.round(a[0].ev(c));
                case "frac": need(f,a,1,st); return c -> { double x = a[0].ev(c); return x - Math.floor(x); };
                case "exp": need(f,a,1,st); return c -> Math.exp(a[0].ev(c));
                case "ln": need(f,a,1,st); return c -> Math.log(Math.max(1e-9, a[0].ev(c)));
                case "log": need(f,a,1,st); return c -> Math.log10(Math.max(1e-9, a[0].ev(c)));
                case "min": need(f,a,2,st); return c -> Math.min(a[0].ev(c), a[1].ev(c));
                case "max": need(f,a,2,st); return c -> Math.max(a[0].ev(c), a[1].ev(c));
                case "pow": need(f,a,2,st); return c -> Math.pow(a[0].ev(c), a[1].ev(c));
                case "hypot": need(f,a,2,st); return c -> Math.hypot(a[0].ev(c), a[1].ev(c));
                case "mod": need(f,a,2,st); return c -> { double m = a[1].ev(c); return m == 0 ? 0 : ((a[0].ev(c) % m) + m) % m; };
                case "clamp": need(f,a,3,st); return c -> clamp(a[0].ev(c), a[1].ev(c), a[2].ev(c));
                case "lerp": case "mix": need(f,a,3,st); return c -> { double k = a[2].ev(c); return a[0].ev(c) * (1 - k) + a[1].ev(c) * k; };
                case "step": need(f,a,2,st); return c -> a[1].ev(c) < a[0].ev(c) ? 0 : 1;
                case "smooth": case "smoothstep": need(f,a,1,st); return c -> { double x = clamp(a[0].ev(c), 0, 1); return x * x * (3 - 2 * x); };
                case "ease": need(f,a,1,st); return c -> { double x = clamp(a[0].ev(c), 0, 1); return x < 0.5 ? 2*x*x : 1 - Math.pow(-2*x+2, 2)/2; };
                case "saw": need(f,a,1,st); return c -> { double x = a[0].ev(c); return 2 * (x - Math.floor(x + 0.5)); };
                case "tri": need(f,a,1,st); return c -> { double x = a[0].ev(c); return 2 * Math.abs(2 * (x - Math.floor(x + 0.5))) - 1; };
                case "sq": need(f,a,1,st); return c -> Math.sin(a[0].ev(c)) >= 0 ? 1 : -1;
                case "pulse": need(f,a,2,st); return c -> { double x = a[0].ev(c); x -= Math.floor(x); return x < a[1].ev(c) ? 1 : 0; };
                case "noise": need(f,a,1,st); return c -> noise(a[0].ev(c));
                case "if": need(f,a,3,st); return c -> a[0].ev(c) != 0 ? a[1].ev(c) : a[2].ev(c);
                case "rand": animated = true; if (a.length != 0) need(f,a,0,st); return c -> c.rnd();
                case "deg": need(f,a,1,st); return c -> Math.toRadians(a[0].ev(c));
                case "rad": need(f,a,1,st); return c -> Math.toDegrees(a[0].ev(c));
                /** step4(k) — плавно щёлкает 0,1,2,3... удобно для "зрачок смотрит в 4 стороны". */
                /** rectx/recty(t,w,h) — периметр ПРЯМОУГОЛЬНИКА (полуширина w, полувысота h) по t от 0 до 2pi. */
                case "rectx": need(f,a,3,st); return c -> rectPt(a[0].ev(c), a[1].ev(c), a[2].ev(c), true);
                case "recty": need(f,a,3,st); return c -> rectPt(a[0].ev(c), a[1].ev(c), a[2].ev(c), false);
                /** cellx/celly(i,cols) — координаты клетки в сетке по номеру точки. */
                case "cellx": need(f,a,2,st); return c -> { double n2 = Math.max(1, Math.floor(a[1].ev(c))); return Math.floor(a[0].ev(c)) % n2; };
                case "celly": need(f,a,2,st); return c -> { double n2 = Math.max(1, Math.floor(a[1].ev(c))); return Math.floor(Math.floor(a[0].ev(c)) / n2); };
                case "step4": need(f,a,1,st); return c -> {
                    double k = a[0].ev(c); double base = Math.floor(k);
                    double fr = clamp((k - base) * 3.0 - 1.6, 0, 1);
                    return base + fr * fr * (3 - 2 * fr);
                };
                default: {
                    Object[] custom = CUSTOM.get(f);
                    if (custom != null) {
                        int arity = (Integer) custom[0];
                        if (arity >= 0 && a.length != arity) need(f, a, arity, st);
                        final Fn fn = (Fn) custom[1];
                        final Node[] args = a;
                        return c -> {
                            double[] vals = new double[args.length];
                            for (int k = 0; k < args.length; k++) vals[k] = args[k].ev(c);
                            return fn.apply(vals);
                        };
                    }
                    throw new ParseError("нет такой функции: " + rawName + "()", st);
                }
            }
        }
    }

    static double clamp(double v, double a, double b) { return v < a ? a : (v > b ? b : v); }

    /** Точка на периметре прямоугольника: t 0..2pi обходит его равномерно по длине. */
    static double rectPt(double t, double w, double h, boolean xAxis) {
        w = Math.abs(w); h = Math.abs(h);
        double per = 4 * w + 4 * h;
        if (per < 1e-9) return 0;
        double s = (((t / (Math.PI * 2)) % 1.0) + 1.0) % 1.0 * per;
        if (s < 2 * h) return xAxis ? w : (-h + s);
        s -= 2 * h;
        if (s < 2 * w) return xAxis ? (w - s) : h;
        s -= 2 * w;
        if (s < 2 * h) return xAxis ? -w : (h - s);
        s -= 2 * h;
        return xAxis ? (-w + s) : -h;
    }

    static double noise(double x) {
        int i = (int) Math.floor(x);
        double f = x - i, u = f * f * (3 - 2 * f);
        return h(i) * (1 - u) + h(i + 1) * u;
    }
    private static double h(int n) {
        n = (n << 13) ^ n;
        return 1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0;
    }

    /** Разбор строки вида "let r=2+sin(T); x=r*cos(t); y=r*sin(t)". */
    public static Map<String, String> splitAssignments(String src) {
        LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
        int depth = 0, start = 0;
        for (int i = 0; i <= src.length(); i++) {
            char c = i < src.length() ? src.charAt(i) : ';';
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if ((c == ';' || c == '\n') && depth == 0) {
                String part = src.substring(start, i).trim();
                start = i + 1;
                if (part.isEmpty()) continue;
                int eq = part.indexOf('=');
                if (eq <= 0) { out.put("!" + out.size(), part); continue; }
                String k = part.substring(0, eq).trim();
                if (k.toLowerCase().startsWith("let ")) k = k.substring(4).trim();
                out.put(k, part.substring(eq + 1).trim());
            }
        }
        return out;
    }
}
