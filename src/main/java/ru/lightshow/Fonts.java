package ru.lightshow;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Шрифты для частичного текста.
 *
 * Встроенные растровые шрифты (pixel / bold / thin) лежат в jar и работают ВСЕГДА,
 * даже если на сервере вообще нет системных шрифтов (частый случай в docker).
 * Кириллица поддерживается из коробки.
 * Дополнительно можно взять любой системный шрифт: font:Arial, font:Impact и т.д.
 */
public final class Fonts {

    /** Готовая монохромная картинка текста. */
    public static final class Bitmap {
        public final int w, h; public final boolean[] px;
        Bitmap(int w, int h) { this.w = Math.max(1, w); this.h = Math.max(1, h); px = new boolean[this.w * this.h]; }
        public boolean get(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h && px[y * w + x]; }
        void set(int x, int y) { if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = true; }
        public int count() { int n = 0; for (boolean b : px) if (b) n++; return n; }
    }

    /** Одна буква со своим местом в строке. */
    public static final class Glyph {
        public final Bitmap bitmap;
        public final int x, y;
        Glyph(Bitmap bitmap, int x, int y) { this.bitmap = bitmap; this.x = x; this.y = y; }
    }

    /**
     * Тот же текст, но разложенный по буквам: каждая со своей картинкой и координатой.
     * Нужен, чтобы буквы можно было анимировать поодиночке, а не всей строкой сразу.
     */
    public static List<Glyph> glyphs(String text, String font, String align, int spacing, int lineGap) {
        if (text == null) text = "";
        text = text.replace("\\n", "\n").replace("%n", "\n");
        if (font != null) font = font.replace('_', ' ').trim();
        String key = font == null ? "pixel" : font.toLowerCase(Locale.ROOT);
        PixelFont pf = BUILTIN.get(key);
        if (pf == null) for (String sf : systemFonts) if (sf.equalsIgnoreCase(font)) { font = sf; break; }

        String[] lines = text.split("\n", -1);
        List<List<Glyph>> perLine = new ArrayList<List<Glyph>>();
        List<Integer> widths = new ArrayList<Integer>();
        List<Integer> heights = new ArrayList<Integer>();

        for (String line : lines) {
            List<Glyph> row = new ArrayList<Glyph>();
            int cursor = 0, tallest = 1;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == ' ') { cursor += 3 + spacing; continue; }
                Bitmap bm = pf != null ? renderLinePixel(String.valueOf(ch), pf, spacing)
                    : renderAwt(String.valueOf(ch), font);
                if (bm == null) bm = renderLinePixel(String.valueOf(ch), BUILTIN.get("pixel"), spacing);
                row.add(new Glyph(bm, cursor, 0));
                cursor += bm.w + spacing;
                tallest = Math.max(tallest, bm.h);
            }
            perLine.add(row);
            widths.add(Math.max(1, cursor - spacing));
            heights.add(tallest);
        }

        int maxWidth = 1;
        for (int w : widths) maxWidth = Math.max(maxWidth, w);

        List<Glyph> out = new ArrayList<Glyph>();
        int baseY = 0;
        for (int li = 0; li < perLine.size(); li++) {
            int shift = 0;
            if ("center".equals(align)) shift = (maxWidth - widths.get(li)) / 2;
            else if ("right".equals(align)) shift = maxWidth - widths.get(li);
            for (Glyph glyph : perLine.get(li)) {
                out.add(new Glyph(glyph.bitmap, glyph.x + shift, baseY));
            }
            baseY += heights.get(li) + lineGap;
        }
        return out;
    }

    /** Встроенный растровый шрифт. */
    static final class PixelFont {
        int rows, bpr, cols;
        final Map<Character, int[]> glyphs = new HashMap<Character, int[]>();
        final Map<Character, int[]> ink = new HashMap<Character, int[]>(); // {left,right}

        int[] rowsOf(char c) {
            int[] g = glyphs.get(c);
            if (g == null) g = glyphs.get('?');
            return g;
        }

        int[] inkOf(char c) {
            int[] i = ink.get(c);
            if (i != null) return i;
            int[] g = rowsOf(c);
            int l = cols, r = -1;
            if (g != null) for (int y = 0; y < rows; y++)
                for (int x = 0; x < cols; x++)
                    if ((g[y] & (1 << (cols - 1 - x))) != 0) { if (x < l) l = x; if (x > r) r = x; }
            int[] res = new int[] { l, r };
            ink.put(c, res);
            return res;
        }
    }

    private static final Map<String, PixelFont> BUILTIN = new LinkedHashMap<String, PixelFont>();
    private static List<String> systemFonts = new ArrayList<String>();

    public static void load(java.util.logging.Logger log, ClassLoader cl) {
        String[] names = { "pixel", "bold", "thin" };
        for (String n : names) {
            try (InputStream in = cl.getResourceAsStream("font_" + n + ".dat")) {
                if (in == null) continue;
                BUILTIN.put(n, read(in));
            } catch (Exception e) {
                log.warning("Не удалось загрузить встроенный шрифт " + n + ": " + e.getMessage());
            }
        }
        try {
            System.setProperty("java.awt.headless", "true");
            String[] fam = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            systemFonts = new ArrayList<String>(Arrays.asList(fam));
            Collections.sort(systemFonts);
        } catch (Throwable t) {
            systemFonts = new ArrayList<String>();
        }
    }

    private static PixelFont read(InputStream raw) throws Exception {
        DataInputStream in = new DataInputStream(raw);
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'P' || magic[1] != 'F' || magic[2] != 'N' || magic[3] != 'T') throw new Exception("bad magic");
        in.readByte();
        PixelFont f = new PixelFont();
        f.rows = in.readUnsignedByte();
        f.bpr = in.readUnsignedByte();
        f.cols = f.bpr * 8;
        int rangeCount = in.readUnsignedShort();
        int[][] ranges = new int[rangeCount][2];
        for (int i = 0; i < rangeCount; i++) { ranges[i][0] = in.readUnsignedShort(); ranges[i][1] = in.readUnsignedShort(); }
        for (int[] r : ranges) {
            for (int c = r[0]; c <= r[1]; c++) {
                int[] g = new int[f.rows];
                for (int y = 0; y < f.rows; y++) {
                    int v = 0;
                    for (int b = 0; b < f.bpr; b++) v = (v << 8) | in.readUnsignedByte();
                    g[y] = v;
                }
                f.glyphs.put((char) c, g);
            }
        }
        return f;
    }

    /** Для табкомплита: пробелы заменены на '_', иначе имя рвётся на два аргумента. */
    public static List<String> names() {
        List<String> out = new ArrayList<String>(BUILTIN.keySet());
        for (String s : systemFonts) out.add(s.replace(' ', '_'));
        return out;
    }

    public static boolean isBuiltin(String name) { return name != null && BUILTIN.containsKey(name.toLowerCase(Locale.ROOT)); }

    /**
     * Растеризуем текст. \n — перенос строки.
     * align: left / center / right.
     */
    public static Bitmap render(String text, String font, String align, int spacing) {
        return render(text, font, align, spacing, 3);
    }

    /**
     * lineGap — расстояние между строками В ПИКСЕЛЯХ шрифта (может быть 0 или отрицательным).
     * Каждая строка обрезается по своим чернилам отдельно, поэтому интервал реально управляемый,
     * а не «высота клетки глифа», как было раньше.
     */
    public static Bitmap render(String text, String font, String align, int spacing, int lineGap) {
        if (text == null) text = "";
        text = text.replace("\\n", "\n").replace("%n", "\n");
        if (font != null) font = font.replace('_', ' ').trim();
        String key = font == null ? "pixel" : font.toLowerCase(Locale.ROOT);
        PixelFont pf = BUILTIN.get(key);
        if (pf == null) {
            for (String sf : systemFonts) if (sf.equalsIgnoreCase(font)) { font = sf; break; }
        }

        String[] lines = text.split("\n", -1);
        List<Bitmap> parts = new ArrayList<Bitmap>();
        for (String line : lines) {
            Bitmap b = null;
            if (pf != null) b = renderLinePixel(line, pf, spacing);
            else b = renderAwt(line, font);
            if (b == null) b = renderLinePixel(line, BUILTIN.get("pixel"), spacing);
            parts.add(b);
        }
        return stack(parts, align, lineGap);
    }

    private static Bitmap stack(List<Bitmap> parts, String align, int lineGap) {
        int maxW = 1, totalH = 0;
        for (int i = 0; i < parts.size(); i++) {
            maxW = Math.max(maxW, parts.get(i).w);
            totalH += parts.get(i).h;
            if (i > 0) totalH += lineGap;
        }
        Bitmap out = new Bitmap(maxW, Math.max(1, totalH));
        int y0 = 0;
        for (int i = 0; i < parts.size(); i++) {
            Bitmap b = parts.get(i);
            int x0 = 0;
            if ("center".equals(align)) x0 = (maxW - b.w) / 2;
            else if ("right".equals(align)) x0 = maxW - b.w;
            for (int y = 0; y < b.h; y++)
                for (int x = 0; x < b.w; x++)
                    if (b.get(x, y)) out.set(x0 + x, y0 + y);
            y0 += b.h + lineGap;
        }
        return out;
    }

    /** Оставляет только контур букв — в 2-3 раза меньше частиц и похоже на неон. */
    public static Bitmap outline(Bitmap b) {
        Bitmap out = new Bitmap(b.w, b.h);
        for (int y = 0; y < b.h; y++)
            for (int x = 0; x < b.w; x++)
                if (b.get(x, y) && !(b.get(x - 1, y) && b.get(x + 1, y) && b.get(x, y - 1) && b.get(x, y + 1)))
                    out.set(x, y);
        return out;
    }

    private static Bitmap renderLinePixel(String line, PixelFont f, int spacing) {
        if (f == null) return new Bitmap(1, 4);
        if (line.isEmpty()) return new Bitmap(1, Math.max(1, f.rows / 2));
        int w = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') { w += 3 + spacing; continue; }
            int[] ink = f.inkOf(c);
            w += (ink[1] < ink[0] ? 3 : ink[1] - ink[0] + 1) + spacing;
        }
        Bitmap bm = new Bitmap(Math.max(1, w - spacing), f.rows);
        int cx = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') { cx += 3 + spacing; continue; }
            int[] ink = f.inkOf(c);
            if (ink[1] < ink[0]) { cx += 3 + spacing; continue; }
            int[] g = f.rowsOf(c);
            if (g != null) for (int y = 0; y < f.rows; y++)
                for (int x = ink[0]; x <= ink[1]; x++)
                    if ((g[y] & (1 << (f.cols - 1 - x))) != 0) bm.set(cx + x - ink[0], y);
            cx += (ink[1] - ink[0] + 1) + spacing;
        }
        return trim(bm);
    }

    private static Bitmap renderAwt(String line, String font) {
        try {
            if (line.isEmpty()) return new Bitmap(1, 8);
            int px = 28;
            java.awt.Font f = new java.awt.Font(font, java.awt.Font.BOLD, px);
            java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D pg = probe.createGraphics();
            pg.setFont(f);
            java.awt.FontMetrics fm = pg.getFontMetrics();
            int w = Math.max(1, fm.stringWidth(line)) + 4, h = fm.getHeight() + 4;
            pg.dispose();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setFont(f);
            g.setColor(java.awt.Color.WHITE);
            g.drawString(line, 2, 2 + fm.getAscent());
            g.dispose();
            Bitmap bm = new Bitmap(w, h);
            int lit = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    if (((argb >>> 24) > 100) && (((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255)) > 200) { bm.set(x, y); lit++; }
                }
            return lit == 0 ? null : trim(bm);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap trim(Bitmap b) {
        int minX = b.w, maxX = -1, minY = b.h, maxY = -1;
        for (int y = 0; y < b.h; y++) for (int x = 0; x < b.w; x++) if (b.get(x, y)) {
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
        }
        if (maxX < 0) return b;
        Bitmap out = new Bitmap(maxX - minX + 1, maxY - minY + 1);
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) if (b.get(x, y)) out.set(x - minX, y - minY);
        return out;
    }
}
