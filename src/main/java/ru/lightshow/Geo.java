package ru.lightshow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Генераторы геометрии: превращают формулу/рисунок/текст в массив точек. */
public final class Geo {

    /** Буфер точек. Массивы, а не объекты — чтобы не мусорить в GC каждый тик. */
    public static final class Buf {
        public double[] x = new double[1024], y = new double[1024], z = new double[1024];
        public double[] tx = new double[1024], ty = new double[1024], tz = new double[1024]; // касательная
        public double[] f = new double[1024];      // 0..1 вдоль фигуры (градиент)
        public float[] seed = new float[1024];     // стабильный рандом на точку
        public int[] layer = new int[1024];
        public int[] rgb = new int[1024];          // -1 = взять цвет из слоя
        public int[] grp = new int[1024];          // номер буквы или другой группы
        public int groups = 1;
        public int size = 0;

        public void clear() { size = 0; groups = 1; group = 0; }

        private void grow() {
            int n = x.length * 2;
            x = java.util.Arrays.copyOf(x, n); y = java.util.Arrays.copyOf(y, n); z = java.util.Arrays.copyOf(z, n);
            tx = java.util.Arrays.copyOf(tx, n); ty = java.util.Arrays.copyOf(ty, n); tz = java.util.Arrays.copyOf(tz, n);
            f = java.util.Arrays.copyOf(f, n); seed = java.util.Arrays.copyOf(seed, n);
            layer = java.util.Arrays.copyOf(layer, n); rgb = java.util.Arrays.copyOf(rgb, n);
            grp = java.util.Arrays.copyOf(grp, n);
        }

        public void add(double px, double py, double pz, double dx, double dy, double dz, double frac, int lay) {
            addC(px, py, pz, dx, dy, dz, frac, lay, -1);
        }

        public int group = 0;

        public void addC(double px, double py, double pz, double dx, double dy, double dz, double frac, int lay, int color) {
            if (size >= x.length) grow();
            rgb[size] = color;
            grp[size] = group;
            x[size] = px; y[size] = py; z[size] = pz;
            tx[size] = dx; ty[size] = dy; tz[size] = dz;
            f[size] = frac; layer[size] = lay;
            int h = size * 0x9E3779B9;
            h ^= h >>> 15; h *= 0x85EBCA6B; h ^= h >>> 13;
            seed[size] = ((h >>> 8) & 0xFFFF) / 65535f;
            size++;
        }
    }

    public interface Source {
        void build(Buf out, int layerIndex, double T, double p);
        boolean animated();
        int estimate();
    }

    // ------------------------------------------------------------- математика

    public static final class MathGeo implements Source {
        public String mode = "curve";
        public double tFrom = 0, tTo = Math.PI * 2, uFrom = 0, uTo = 1;
        public int steps = 260, usteps = 24, sides = 16;
        public double radius = 1.0;
        private final Expr.Scope scope = new Expr.Scope();
        private final List<int[]> letSlots = new ArrayList<int[]>();
        private final List<Expr.Compiled> letExpr = new ArrayList<Expr.Compiled>();
        private Expr.Compiled ex, ey, ez;
        private boolean anim = false;
        private final Expr.Ctx ctx = new Expr.Ctx(16);
        private double[] cx = new double[0], cy = new double[0], cz = new double[0];

        /** src: "let r=2+sin(T); x=r*cos(t); y=r*sin(t); z=0" */
        public static MathGeo compile(String src) {
            MathGeo g = new MathGeo();
            Map<String, String> parts = Expr.splitAssignments(src);
            String sx = "0", sy = "0", sz = "0";
            for (Map.Entry<String, String> e : parts.entrySet()) {
                String k = e.getKey().trim();
                String lk = k.toLowerCase();
                if (lk.equals("x")) { sx = e.getValue(); continue; }
                if (lk.equals("y")) { sy = e.getValue(); continue; }
                if (lk.equals("z")) { sz = e.getValue(); continue; }
                if (k.startsWith("!")) throw new Expr.ParseError("часть '" + e.getValue() + "' без имени (нужно x= / y= / z= / имя=)", 0);
                Expr.Compiled c = Expr.compile(e.getValue(), g.scope);
                int slot = g.scope.alloc(k);
                g.letSlots.add(new int[] { slot });
                g.letExpr.add(c);
                g.anim |= c.animated;
            }
            g.ex = Expr.compile(sx, g.scope);
            g.ey = Expr.compile(sy, g.scope);
            g.ez = Expr.compile(sz, g.scope);
            g.anim |= g.ex.animated || g.ey.animated || g.ez.animated;
            return g;
        }

        public boolean animated() { return anim; }

        public int estimate() {
            if (mode.equals("tube")) return (steps + 1) * sides;
            if (mode.equals("surface") || mode.equals("fill")) return (steps + 1) * (usteps + 1);
            return steps + 1;
        }

        private void evalPoint(double t, double u, double T, double p, int i, int n, double[] out) {
            ctx.fit(scope.size());
            ctx.v[Expr.S_T] = t; ctx.v[Expr.S_U] = u; ctx.v[Expr.S_I] = i;
            ctx.v[Expr.S_N] = n; ctx.v[Expr.S_TIME] = T; ctx.v[Expr.S_PROG] = p;
            for (int k = 0; k < letExpr.size(); k++) ctx.v[letSlots.get(k)[0]] = letExpr.get(k).ev(ctx);
            out[0] = ex.ev(ctx); out[1] = ey.ev(ctx); out[2] = ez.ev(ctx);
        }

        public void build(Buf out, int lay, double T, double p) {
            double[] tmp = new double[3];
            int n = Math.max(2, steps);

            if (mode.equals("surface") || mode.equals("fill")) {
                int un = Math.max(1, usteps);
                for (int i = 0; i <= n; i++) {
                    double t = tFrom + (tTo - tFrom) * i / n;
                    for (int j = 0; j <= un; j++) {
                        double u = uFrom + (uTo - uFrom) * j / un;
                        evalPoint(t, u, T, p, i, n, tmp);
                        double sx = tmp[0], sy = tmp[1], sz = tmp[2];
                        if (mode.equals("fill")) { sx *= u; sy *= u; sz *= u; }
                        out.add(sx, sy, sz, 0, 0, 0, (double) i / n, lay);
                    }
                }
                return;
            }

            // Сначала считаем сам путь, затем — касательные (нужны для tube и motion:flow)
            if (cx.length < n + 1) { cx = new double[n + 1]; cy = new double[n + 1]; cz = new double[n + 1]; }
            for (int i = 0; i <= n; i++) {
                double t = tFrom + (tTo - tFrom) * i / n;
                evalPoint(t, 0, T, p, i, n, tmp);
                cx[i] = tmp[0]; cy[i] = tmp[1]; cz[i] = tmp[2];
            }

            boolean tube = mode.equals("tube");
            for (int i = 0; i <= n; i++) {
                int a = Math.max(0, i - 1), b = Math.min(n, i + 1);
                double dx = cx[b] - cx[a], dy = cy[b] - cy[a], dz = cz[b] - cz[a];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-9) { dx = 0; dy = 0; dz = 1; } else { dx /= len; dy /= len; dz /= len; }
                double frac = (double) i / n;

                if (!tube) { out.add(cx[i], cy[i], cz[i], dx, dy, dz, frac, lay); continue; }

                // ортонормированный базис вокруг касательной
                double ux = 0, uy = 1, uz = 0;
                if (Math.abs(dy) > 0.95) { ux = 1; uy = 0; uz = 0; }
                double n1x = dy * uz - dz * uy, n1y = dz * ux - dx * uz, n1z = dx * uy - dy * ux;
                double l1 = Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
                if (l1 < 1e-9) { n1x = 1; n1y = 0; n1z = 0; l1 = 1; }
                n1x /= l1; n1y /= l1; n1z /= l1;
                double n2x = dy * n1z - dz * n1y, n2y = dz * n1x - dx * n1z, n2z = dx * n1y - dy * n1x;
                for (int j = 0; j < sides; j++) {
                    double ang = Math.PI * 2 * j / sides;
                    double ca = Math.cos(ang) * radius, sa = Math.sin(ang) * radius;
                    out.add(cx[i] + n1x * ca + n2x * sa, cy[i] + n1y * ca + n2y * sa, cz[i] + n1z * ca + n2z * sa,
                            dx, dy, dz, frac, lay);
                }
            }
        }
    }

    // ------------------------------------------------------------- растр (текст / рисунок)

    public static final class BitmapGeo implements Source {
        public final List<Fonts.Bitmap> frames = new ArrayList<Fonts.Bitmap>();
        public double px = 0.25;      // размер пикселя в блоках
        public int frameTicks = 4;    // сколько тиков держится кадр
        public boolean pingpong = false;
        private int cached = -1;

        public BitmapGeo(List<Fonts.Bitmap> f) { frames.addAll(f); }
        public BitmapGeo(Fonts.Bitmap f) { frames.add(f); }

        public boolean animated() { return frames.size() > 1; }

        public int estimate() { return frames.isEmpty() ? 0 : frames.get(0).count(); }

        public void build(Buf out, int lay, double T, double p) {
            if (frames.isEmpty()) return;
            int idx = 0;
            if (frames.size() > 1) {
                int step = (int) (T * 20.0 / Math.max(1, frameTicks));
                if (pingpong) {
                    int period = frames.size() * 2 - 2;
                    int k = period <= 0 ? 0 : step % period;
                    idx = k < frames.size() ? k : period - k;
                } else idx = step % frames.size();
            }
            cached = idx;
            Fonts.Bitmap bm = frames.get(idx);
            double w = bm.w, h = bm.h;
            for (int y = 0; y < bm.h; y++)
                for (int x = 0; x < bm.w; x++)
                    if (bm.get(x, y))
                        out.add((x - (w - 1) / 2.0) * px, ((h - 1) / 2.0 - y) * px, 0, 0, 0, 0, w <= 1 ? 0 : x / (w - 1), lay);
        }

        public int currentFrame() { return cached; }
    }

    // ------------------------------------------------------------- картинка (со своими цветами)

    public static final class ImageGeo implements Source {
        public final int w, h;
        public final int[] col;   // -1 = прозрачно
        public double px = 0.15;

        public ImageGeo(List<String> rows) {
            int height = rows.size(), width = 1;
            String[][] cells = new String[height][];
            for (int y = 0; y < height; y++) { cells[y] = rows.get(y).split(","); width = Math.max(width, cells[y].length); }
            w = width; h = height;
            col = new int[w * h];
            java.util.Arrays.fill(col, -1);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < cells[y].length; x++) {
                    String c = cells[y][x];
                    if (c.isEmpty() || c.charAt(0) == '-') continue;
                    try { col[y * w + x] = (int) (Long.parseLong(c.trim(), 16) & 0xFFFFFF); } catch (Exception ignored) {}
                }
        }

        public boolean animated() { return false; }

        public int estimate() { int n = 0; for (int c : col) if (c >= 0) n++; return n; }

        public void build(Buf out, int lay, double T, double p) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = col[y * w + x];
                    if (c < 0) continue;
                    out.addC((x - (w - 1) / 2.0) * px, ((h - 1) / 2.0 - y) * px, 0, 0, 0, 0,
                            w <= 1 ? 0 : (double) x / (w - 1), lay, c);
                }
        }
    }

    // ------------------------------------------------------------- текст по буквам

    public static final class TextGeo implements Source {
        private final List<Fonts.Glyph> glyphs;
        private final double width, height;
        public double px = 0.25;

        public TextGeo(List<Fonts.Glyph> glyphs) {
            this.glyphs = glyphs;
            double w = 1, h = 1;
            for (Fonts.Glyph g : glyphs) {
                w = Math.max(w, g.x + g.bitmap.w);
                h = Math.max(h, g.y + g.bitmap.h);
            }
            this.width = w;
            this.height = h;
        }

        public boolean animated() { return false; }

        public int estimate() {
            int n = 0;
            for (Fonts.Glyph g : glyphs) n += g.bitmap.count();
            return n;
        }

        public int letters() { return glyphs.size(); }

        public void build(Buf out, int lay, double T, double p) {
            out.groups = Math.max(out.groups, Math.max(1, glyphs.size()));
            for (int gi = 0; gi < glyphs.size(); gi++) {
                Fonts.Glyph glyph = glyphs.get(gi);
                out.group = gi;
                for (int y = 0; y < glyph.bitmap.h; y++) {
                    for (int x = 0; x < glyph.bitmap.w; x++) {
                        if (!glyph.bitmap.get(x, y)) continue;
                        double wx = (glyph.x + x - (width - 1) / 2.0) * px;
                        double wy = ((height - 1) / 2.0 - (glyph.y + y)) * px;
                        out.add(wx, wy, 0, 0, 0, 0,
                            glyphs.size() <= 1 ? 0 : (double) gi / (glyphs.size() - 1), lay);
                    }
                }
            }
            out.group = 0;
        }
    }
}
