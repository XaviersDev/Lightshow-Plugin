package ru.lightshow;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/** Хранилище пресетов (formula / рисунок / текст) в presets.yml. */
public final class Presets {

    public static final class Preset {
        public String name;
        public String type = "math";               // math | draw | text | image
        public List<String> layers = new ArrayList<String>();     // math: "формула @ параметры слоя"
        public List<List<String>> frames = new ArrayList<List<String>>(); // draw: кадры из строк "010110..."
        public String text = "";                   // text
        public List<String> rows = new ArrayList<String>();        // image: строки "RRGGBB,-,..." 
        public String author = "";
        public boolean builtin = false;
        public Params defaults = new Params();

        public String describe() {
            if (type.equals("math")) return "формул: " + layers.size();
            if (type.equals("draw")) return "кадров: " + frames.size();
            if (type.equals("text")) return "\"" + (text.length() > 18 ? text.substring(0, 18) + "…" : text) + "\"";
            if (type.equals("image")) return "картинка " + (rows.isEmpty() ? 0 : rows.get(0).split(",").length) + "x" + rows.size();
            return type;
        }
    }

    private final LightShow plugin;
    private final File file;
    private final LinkedHashMap<String, Preset> map = new LinkedHashMap<String, Preset>();
    private int lastRev = 1;

    public Presets(LightShow plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "presets.yml");
    }

    public Collection<Preset> all() { return map.values(); }
    public Preset get(String name) { return name == null ? null : map.get(name.toLowerCase(Locale.ROOT)); }
    public boolean has(String name) { return get(name) != null; }
    public void put(Preset p) { map.put(p.name.toLowerCase(Locale.ROOT), p); }
    public void remove(String name) { map.remove(name.toLowerCase(Locale.ROOT)); }
    public Set<String> names() { return map.keySet(); }

    public List<String> namesOfType(String type) {
        List<String> out = new ArrayList<String>();
        for (Preset p : map.values()) if (type == null || type.equals("all") || p.type.equals(type)) out.add(p.name);
        return out;
    }

    public void load() {
        map.clear();
        if (!file.exists()) plugin.saveResource("presets.yml", false);
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        readInto(user, false);

        // Встроенные пресеты обновляются вместе с jar, но пользовательские не трогаем.
        YamlConfiguration bundled = null;
        try (java.io.InputStream in = plugin.getResource("presets.yml")) {
            if (in != null) bundled = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, "UTF-8"));
        } catch (Exception ignored) {}
        if (bundled != null) {
            int userRev = user.getInt("rev", 0), rev = bundled.getInt("rev", 1);
            LinkedHashMap<String, Preset> mine = new LinkedHashMap<String, Preset>(map);
            map.clear();
            readInto(bundled, true);
            LinkedHashMap<String, Preset> fresh = new LinkedHashMap<String, Preset>(map);
            map.clear();
            map.putAll(mine);
            int added = 0, updated = 0;
            for (Map.Entry<String, Preset> e : fresh.entrySet()) {
                Preset old = map.get(e.getKey());
                if (old == null) { map.put(e.getKey(), e.getValue()); added++; }
                else if (old.builtin && userRev < rev) { map.put(e.getKey(), e.getValue()); updated++; }
            }
            if (added > 0 || updated > 0) {
                save(rev);
                plugin.getLogger().info("Пресеты: добавлено " + added + ", обновлено " + updated + " (rev " + rev + ")");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void readInto(YamlConfiguration y, boolean builtin) {
        ConfigurationSection root = y.getConfigurationSection("presets");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Preset p = new Preset();
            p.name = key;
            p.type = s.getString("type", "math");
            p.text = s.getString("text", "");
            p.author = s.getString("author", "");
            p.defaults = Params.deserialize(s.getString("params", ""));
            p.builtin = s.getBoolean("builtin", builtin);
            p.layers = s.getStringList("layers");
            p.rows = s.getStringList("rows");
            List<?> fr = s.getList("frames");
            if (fr != null) for (Object o : fr) {
                if (o instanceof List) {
                    List<String> rows = new ArrayList<String>();
                    for (Object r : (List<Object>) o) rows.add(String.valueOf(r));
                    p.frames.add(rows);
                }
            }
            map.put(key.toLowerCase(Locale.ROOT), p);
        }
    }

    public void save() { save(-1); }

    public void save(int rev) {
        YamlConfiguration y = new YamlConfiguration();
        if (rev < 0) rev = lastRev;
        lastRev = rev;
        y.set("rev", rev);
        for (Preset p : map.values()) {
            String base = "presets." + p.name + ".";
            y.set(base + "type", p.type);
            if (!p.layers.isEmpty()) y.set(base + "layers", p.layers);
            if (!p.frames.isEmpty()) y.set(base + "frames", p.frames);
            if (!p.text.isEmpty()) y.set(base + "text", p.text);
            if (!p.rows.isEmpty()) y.set(base + "rows", p.rows);
            if (!p.author.isEmpty()) y.set(base + "author", p.author);
            if (p.builtin) y.set(base + "builtin", true);
            String d = p.defaults.serialize();
            if (!d.isEmpty()) y.set(base + "params", d);
        }
        try { y.save(file); } catch (Exception e) { plugin.getLogger().warning("Не смог сохранить presets.yml: " + e.getMessage()); }
    }

    // ------------------------------------------------------------- сборка слоёв

    /** Превращает пресет + параметры в готовые слои шоу. Бросает ParseError с понятным текстом. */
    public static List<Show.Layer> build(Preset preset, Params p) {
        List<Show.Layer> out = new ArrayList<Show.Layer>();
        if (preset.type.equals("math")) {
            for (String raw : preset.layers) {
                String geoSrc = raw, extra = "";
                int at = raw.indexOf('@');
                if (at >= 0) { geoSrc = raw.substring(0, at); extra = raw.substring(at + 1); }
                Params lp = p.merge(Params.deserialize(extra));

                // Слой-картинка: pix:0110/1111  — готовые блочные фигуры без всякой математики
                String trimmed = geoSrc.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("pix:")) {
                    List<String> rows = new ArrayList<String>(Arrays.asList(trimmed.substring(4).split("/")));
                    Geo.BitmapGeo bg = new Geo.BitmapGeo(fromRows(rows));
                    bg.px = lp.num("px", 0.5);
                    out.add(style(bg, lp));
                    continue;
                }

                Geo.MathGeo g = Geo.MathGeo.compile(geoSrc);
                g.mode = lp.lower("mode", "curve");
                g.steps = Math.max(2, Math.min(20000, lp.integer("steps", 260)));
                g.usteps = Math.max(1, Math.min(400, lp.integer("usteps", 20)));
                g.sides = Math.max(3, Math.min(64, lp.integer("sides", 14)));
                g.radius = lp.num("radius", 1.0);
                double[] tr = lp.range("t", 0, Math.PI * 2);
                g.tFrom = tr[0]; g.tTo = tr[1];
                double[] ur = lp.range("u", 0, 1);
                g.uFrom = ur[0]; g.uTo = ur[1];
                out.add(style(g, lp));
            }
        } else if (preset.type.equals("draw")) {
            List<Fonts.Bitmap> frames = new ArrayList<Fonts.Bitmap>();
            for (List<String> rows : preset.frames) frames.add(fromRows(rows));
            if (frames.isEmpty()) frames.add(new Fonts.Bitmap(1, 1));
            Geo.BitmapGeo g = new Geo.BitmapGeo(frames);
            g.px = p.num("px", 0.4);
            g.frameTicks = Math.max(1, p.integer("fps", 4));
            g.pingpong = p.bool("pingpong", false);
            out.add(style(g, p));
        } else if (preset.type.equals("image")) {
            Geo.ImageGeo g = new Geo.ImageGeo(preset.rows);
            g.px = p.num("px", 0.15);
            out.add(style(g, p));
        } else if (preset.type.equals("text")) {
            // По буквам, чтобы каждую можно было двигать отдельно
            Geo.TextGeo g = new Geo.TextGeo(Fonts.glyphs(preset.text, p.str("font", "pixel"),
                p.lower("align", "center"), p.integer("spacing", 1), p.integer("lgap", 3)));
            g.px = p.num("px", 0.25);
            out.add(style(g, p));
        }
        if (out.isEmpty()) throw new Expr.ParseError("в пресете нет ни одного слоя", 0);
        return out;
    }

    public static Show.Layer style(Geo.Source src, Params p) {
        Show.Layer l = new Show.Layer();
        l.src = src;
        l.particle = Painter.type(p.lower("particle", "end_rod"), Particle.END_ROD);
        l.col = Painter.Col.parse(p.str("color", "white"));
        l.psize = (float) p.num("psize", 1);
        l.motion = p.lower("motion", "none");
        l.mspeed = p.num("mspeed", 0.05);
        l.lift = p.num("lift", 0);
        // Статичную геометрию можно обновлять редко: частица end_rod живёт ~60 тиков.
        // Живых частиц у клиента = точки * 60 / refresh — вот где раньше умирал FPS.
        // таймлайн слоя: from/to, своя анимация входа-выхода, формулы смещения и масштаба
        l.from = Math.max(0, p.ticks("from", 0));
        int showDur = p.ticks("dur", 200);
        l.to = p.has("to") ? p.ticks("to", -1) : showDur;
        l.in = p.lower("in", "none");
        l.out = p.lower("out", "none");
        l.inT = p.ticks("int", 10);
        l.outT = p.ticks("outt", 10);
        l.ox = expr(p.str("ox", null));
        l.oy = expr(p.str("oy", null));
        l.oz = expr(p.str("oz", null));
        l.zoom = expr(p.str("zoom", null));
        l.rotx = expr(p.str("rotx", null));
        l.roty = expr(p.str("roty", null));
        l.rotz = expr(p.str("rotz", null));
        l.sound = p.str("sound", null);
        l.svol = (float) p.num("svol", 1);
        l.spitch = (float) p.num("spitch", 1);
        l.vx = expr(p.str("vx", null));
        l.vy = expr(p.str("vy", null));
        l.vz = expr(p.str("vz", null));
        boolean ownVel = l.vx != null || l.vy != null || l.vz != null;
        if (ownVel && !p.has("mspeed")) l.mspeed = 1.0;
        l.trail = Math.max(0, Math.min(24, p.integer("trail", 0)));
        l.tgap = p.num("tgap", 0.5);
        l.jitter = Math.max(0, p.num("jitter", 0));
        l.chance = Math.max(0.01, Math.min(1, p.num("chance", 1)));
        l.pcount = Math.max(0, Math.min(64, p.integer("count", 0)));
        l.spread = Math.max(0, p.num("spread", 0.3));
        // Слой, который ЕЗДИТ или крутится по формуле, обязан обновляться часто,
        // иначе за ним тянется шлейф из старых частиц (они живут ~60 тиков).
        boolean moving = anim(l.ox) || anim(l.oy) || anim(l.oz) || anim(l.zoom)
                || anim(l.rotx) || anim(l.roty) || anim(l.rotz);
        l.refresh = Math.max(1, Math.min(60, p.integer("refresh", (src.animated() || moving) ? 3 : 12)));
        // страховка: ни один слой не имеет права засыпать клиента пакетами
        int est = 0;
        try { est = src.estimate(); } catch (Throwable ignored) {}
        if (est > 0) {
            boolean dust = Painter.isDust(l.particle);
            if (dust && est > 400) l.refresh = Math.max(l.refresh, 18);
            int cap = dust ? 150 : 400;
            if (est / l.refresh > cap) l.refresh = Math.min(60, (int) Math.ceil((double) est / cap));
        }
        l.colorAnimated = l.col.animated();

        l.burst = p.bool("burst", false);
        double[] drift = p.vec("drift", 0, 0, 0);
        l.driftX = drift[0]; l.driftY = drift[1]; l.driftZ = drift[2];
        l.hasDrift = p.has("drift") && (drift[0] != 0 || drift[1] != 0 || drift[2] != 0);
        l.driftTicks = Math.max(0, p.ticks("driftt", 0));
        double[] wave = p.vec("wave", 0, 6, 0);
        l.waveAmp = wave[0]; l.waveSpeed = wave[1] == 0 ? 6 : wave[1];
        l.every = Math.max(0, p.ticks("every", 0));
        l.forT = Math.max(0, p.ticks("for", 0));
        return l;
    }

    private static boolean anim(Expr.Compiled c) { return c != null && c.animated; }

    private static Expr.Compiled expr(String src) {
        if (src == null || src.trim().isEmpty()) return null;
        return Expr.compile(src, new Expr.Scope());
    }

    public static Fonts.Bitmap fromRows(List<String> rows) {
        int h = rows.size(), w = 1;
        for (String r : rows) w = Math.max(w, r.length());
        Fonts.Bitmap bm = new Fonts.Bitmap(w, h);
        for (int y = 0; y < h; y++) {
            String r = rows.get(y);
            for (int x = 0; x < r.length(); x++) if (r.charAt(x) != '0' && r.charAt(x) != '.' && r.charAt(x) != ' ') bm.set(x, y);
        }
        return bm;
    }
}
