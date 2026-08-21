package ru.lightshow.impl;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import ru.lightshow.*;
import ru.lightshow.api.*;

import java.util.*;
import java.util.function.Consumer;

/** Реализация публичного API поверх того же движка, что крутит команды. */
public final class ApiImpl implements LightShowAPI {

    private final LightShow plugin;

    public ApiImpl(LightShow plugin) { this.plugin = plugin; }

    public ShowBuilder show() { return new Builder(); }

    public ShowBuilder fromPreset(String name) {
        Builder b = new Builder();
        for (String part : name.split("\\+")) b.preset(part);
        return b;
    }

    public ShowHandle play(String presetName, String params, Location at, Audience audience) {
        ShowBuilder b = fromPreset(presetName);
        if (params != null && !params.isEmpty()) b.params(params);
        if (at != null) b.at(at);
        if (audience != null) b.audience(audience);
        return b.start();
    }

    public List<ShowHandle> active() { return new ArrayList<ShowHandle>(plugin.manager().shows()); }

    public ShowHandle byId(int id) { return plugin.manager().byId(id); }

    public int stopAll() { return plugin.manager().stopAll(); }

    public int stopOwnedBy(UUID owner) { return plugin.manager().stopOf(owner); }

    public PresetRegistry presets() { return presetRegistry; }

    public FunctionRegistry functions() { return functionRegistry; }

    public TextRenderer text() { return textRenderer; }

    public void registerParticleAlias(String alias, Particle particle) { Painter.registerAlias(alias, particle); }

    public Set<String> particleAliases() { return Painter.typeNames(); }

    public Set<String> colorNames() { return Painter.colorNames(); }

    public Transport transport() { return Transport.valueOf(Painter.transport().name()); }

    public int particleBudget() { return plugin.manager().budget(); }

    public void setParticleBudget(int perTick) { plugin.manager().setBudget(perTick); }

    public long currentLoad() { return plugin.manager().lastLoad(); }

    public String version() { return plugin.getDescription().getVersion(); }

    // ================================================================= реестры

    private final PresetRegistry presetRegistry = new PresetRegistry() {
        public Set<String> names() { return plugin.presets().names(); }
        public List<String> namesOfType(String type) { return plugin.presets().namesOfType(type); }
        public boolean has(String name) { return plugin.presets().has(name); }
        public String describe(String name) {
            Presets.Preset p = plugin.presets().get(name);
            return p == null ? null : p.describe();
        }
        public void delete(String name) { plugin.presets().remove(name); plugin.presets().save(); }
        public void save() { plugin.presets().save(); }
        public ShowBuilder open(String name) { return fromPreset(name); }
    };

    private final FunctionRegistry functionRegistry = new FunctionRegistry() {
        public void register(String name, int arity, final Function fn) {
            Expr.registerFunction(name, arity, new Expr.Fn() {
                public double apply(double[] args) { return fn.apply(args); }
            });
        }
        public void unregister(String name) { Expr.unregisterFunction(name); }
        public boolean has(String name) { return Expr.hasFunction(name); }
        public Set<String> custom() { return Expr.customFunctions(); }
    };

    private final TextRenderer textRenderer = new TextRenderer() {
        public PixelArt render(String text, String font, String align, int spacing, int lineGap) {
            return new Art(Fonts.render(text, font == null ? "pixel" : font,
                    align == null ? "center" : align, spacing, lineGap));
        }
        public List<String> fonts() { return Fonts.names(); }
    };

    static final class Art implements PixelArt {
        private final Fonts.Bitmap bm;
        Art(Fonts.Bitmap bm) { this.bm = bm; }
        public int width() { return bm.w; }
        public int height() { return bm.h; }
        public boolean get(int x, int y) { return bm.get(x, y); }
        public int count() { return bm.count(); }
        public PixelArt outline() { return new Art(Fonts.outline(bm)); }
        public List<String> rows() {
            List<String> out = new ArrayList<String>(bm.h);
            for (int y = 0; y < bm.h; y++) {
                StringBuilder sb = new StringBuilder(bm.w);
                for (int x = 0; x < bm.w; x++) sb.append(bm.get(x, y) ? '1' : '0');
                out.add(sb.toString());
            }
            return out;
        }
    }

    // ================================================================= билдеры

    private static final int MATH = 0, TEXT = 1, IMAGE = 2, FRAMES = 3, PRESET = 4;

    final class LB implements LayerBuilder {
        final Builder parent;
        final int kind;
        final String geo;
        final List<String> imageRows;
        final List<List<String>> frames;
        final Params p = new Params();

        LB(Builder parent, int kind, String geo, List<String> imageRows, List<List<String>> frames) {
            this.parent = parent; this.kind = kind; this.geo = geo;
            this.imageRows = imageRows; this.frames = frames;
        }

        public LayerBuilder particle(Particle particle) { return param("particle", particle.name()); }
        public LayerBuilder particle(String alias) { return param("particle", alias); }
        public LayerBuilder color(String spec) { return param("color", spec); }
        public LayerBuilder color(int rgb) { return param("color", String.format("#%06X", rgb & 0xFFFFFF)); }
        public LayerBuilder particleSize(double psize) { return param("psize", psize); }

        public LayerBuilder mode(String mode) { return param("mode", mode); }
        public LayerBuilder steps(int steps) { return param("steps", steps); }
        public LayerBuilder usteps(int usteps) { return param("usteps", usteps); }
        public LayerBuilder sides(int sides) { return param("sides", sides); }
        public LayerBuilder radius(double radius) { return param("radius", radius); }
        public LayerBuilder range(double from, double to) { return param("t", from + ".." + to); }
        public LayerBuilder urange(double from, double to) { return param("u", from + ".." + to); }
        public LayerBuilder pixelSize(double blocks) { return param("px", blocks); }

        public LayerBuilder from(int ticks) { return param("from", ticks + "t"); }
        public LayerBuilder to(int ticks) { return param("to", ticks + "t"); }
        public LayerBuilder every(int period, int window) { return param("every", period + "t").param("for", window + "t"); }
        public LayerBuilder in(String animation, int ticks) { return param("in", animation).param("int", ticks + "t"); }
        public LayerBuilder out(String animation, int ticks) { return param("out", animation).param("outt", ticks + "t"); }

        public LayerBuilder offset(double x, double y, double z) { return offset(str(x), str(y), str(z)); }
        public LayerBuilder offset(String x, String y, String z) {
            if (x != null) param("ox", x);
            if (y != null) param("oy", y);
            if (z != null) param("oz", z);
            return this;
        }
        public LayerBuilder zoom(double zoom) { return param("zoom", zoom); }
        public LayerBuilder zoom(String formula) { return param("zoom", formula); }
        public LayerBuilder rotation(String rx, String ry, String rz) {
            if (rx != null) param("rotx", rx);
            if (ry != null) param("roty", ry);
            if (rz != null) param("rotz", rz);
            return this;
        }

        public LayerBuilder motion(String motion) { return param("motion", motion); }
        public LayerBuilder speed(double mspeed) { return param("mspeed", mspeed); }
        public LayerBuilder velocity(String vx, String vy, String vz) {
            if (vx != null) param("vx", vx);
            if (vy != null) param("vy", vy);
            if (vz != null) param("vz", vz);
            return this;
        }
        public LayerBuilder trail(int count, double gap) { return param("trail", count).param("tgap", gap); }
        public LayerBuilder jitter(double blocks) { return param("jitter", blocks); }
        public LayerBuilder chance(double probability) { return param("chance", probability); }
        public LayerBuilder batch(int count, double spread) { return param("count", count).param("spread", spread); }
        public LayerBuilder lift(double lift) { return param("lift", lift); }

        public LayerBuilder refresh(int ticks) { return param("refresh", ticks); }
        public LayerBuilder sound(String sound, float volume, float pitch) {
            return param("sound", sound).param("svol", volume).param("spitch", pitch);
        }
        public LayerBuilder font(String font) { return param("font", font == null ? null : font.replace(' ', '_')); }
        public LayerBuilder align(String align) { return param("align", align); }
        public LayerBuilder spacing(int pixels) { return param("spacing", pixels); }
        public LayerBuilder lineGap(int pixels) { return param("lgap", pixels); }
        public LayerBuilder outline(boolean outline) { return param("outline", outline); }
        public LayerBuilder fps(int ticksPerFrame) { return param("fps", ticksPerFrame); }
        public LayerBuilder pingpong(boolean pingpong) { return param("pingpong", pingpong); }

        public LayerBuilder param(String key, Object value) {
            if (value != null) p.set(key.toLowerCase(Locale.ROOT), str(value));
            return this;
        }
        public LayerBuilder params(String spec) {
            for (Map.Entry<String, String> e : Params.deserialize(spec).raw().entrySet()) p.set(e.getKey(), e.getValue());
            return this;
        }

        public int estimatePoints() {
            int n = 0;
            for (Show.Layer l : parent.buildLayer(this)) n += l.src.estimate();
            return n;
        }

        public ShowBuilder and() { return parent; }
    }

    private static String str(Object v) {
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }

    final class Builder implements ShowBuilder {
        final List<LB> layers = new ArrayList<LB>();
        final Params showParams = new Params();
        Location loc;
        Player ownerPlayer;
        String label = "api";
        Audience aud;
        final List<Consumer<ShowHandle>> callbacks = new ArrayList<Consumer<ShowHandle>>();

        private LB add(LB lb) { layers.add(lb); return lb; }

        public LayerBuilder formula(String formula) { return add(new LB(this, MATH, formula, null, null)); }

        public LayerBuilder pixels(String rows) {
            String g = rows.trim();
            if (!g.toLowerCase(Locale.ROOT).startsWith("pix:")) g = "pix:" + g;
            return add(new LB(this, MATH, g, null, null));
        }

        public LayerBuilder pixels(boolean[][] grid) {
            StringBuilder sb = new StringBuilder("pix:");
            for (int y = 0; y < grid.length; y++) {
                if (y > 0) sb.append('/');
                for (int x = 0; x < grid[y].length; x++) sb.append(grid[y][x] ? '1' : '0');
            }
            return add(new LB(this, MATH, sb.toString(), null, null));
        }

        public LayerBuilder text(String text) { return add(new LB(this, TEXT, text, null, null)); }

        public LayerBuilder image(List<String> rows) { return add(new LB(this, IMAGE, null, rows, null)); }

        public LayerBuilder frames(List<List<String>> frames) { return add(new LB(this, FRAMES, null, null, frames)); }

        public ShowBuilder preset(String name) { add(new LB(this, PRESET, name, null, null)); return this; }

        public ShowBuilder at(Location location) { this.loc = location; return this; }

        public ShowBuilder near(Player player, double distance) {
            this.ownerPlayer = player;
            return param("dist", distance);
        }

        public ShowBuilder attachTo(Player player) {
            this.ownerPlayer = player;
            return param("anchor", "player");
        }

        public ShowBuilder face(String face) { return param("face", face); }
        public ShowBuilder offset(double x, double y, double z) { return param("offset", x + "," + y + "," + z); }
        public ShowBuilder scale(double size) { return param("size", size); }
        public ShowBuilder spin(double x, double y, double z) { return param("spin", x + "," + y + "," + z); }
        public ShowBuilder duration(int ticks) { return param("dur", ticks < 0 ? "inf" : ticks + "t"); }
        public ShowBuilder loop(boolean loop) { return param("loop", loop); }

        public ShowBuilder audience(Audience audience) { this.aud = audience; return this; }

        public ShowBuilder onlyFor(Player... players) {
            this.aud = Audience.of(players);
            if (players.length > 0 && ownerPlayer == null) ownerPlayer = players[0];
            return this;
        }

        public ShowBuilder viewDistance(double blocks) { return param("view", blocks); }
        public ShowBuilder cull(double blocks) { return param("cull", blocks); }
        public ShowBuilder maxParticlesPerTick(int max) { return param("max", max); }
        public ShowBuilder density(double fraction) { return param("density", fraction); }
        public ShowBuilder owner(Player player) { this.ownerPlayer = player; return this; }
        public ShowBuilder label(String label) { this.label = label; return this; }

        public ShowBuilder param(String key, Object value) {
            if (value != null) showParams.set(key.toLowerCase(Locale.ROOT), str(value));
            return this;
        }

        public ShowBuilder params(String spec) {
            for (Map.Entry<String, String> e : Params.deserialize(spec).raw().entrySet()) showParams.set(e.getKey(), e.getValue());
            return this;
        }

        public ShowBuilder onEnd(Consumer<ShowHandle> callback) { if (callback != null) callbacks.add(callback); return this; }

        /** Собирает Show.Layer через тот же код, что и пресеты, — включая все страховки. */
        List<Show.Layer> buildLayer(LB lb) {
            Params merged = lb.p.under(showParams);
            Presets.Preset tmp = new Presets.Preset();
            tmp.name = "api";
            switch (lb.kind) {
                case TEXT: tmp.type = "text"; tmp.text = lb.geo; break;
                case IMAGE: tmp.type = "image"; tmp.rows = lb.imageRows == null ? new ArrayList<String>() : lb.imageRows; break;
                case FRAMES: tmp.type = "draw"; tmp.frames = lb.frames == null ? new ArrayList<List<String>>() : lb.frames; break;
                case PRESET: {
                    Presets.Preset src = plugin.presets().get(lb.geo);
                    if (src == null) throw new IllegalArgumentException("нет пресета: " + lb.geo);
                    return Presets.build(src, merged.under(src.defaults));
                }
                default: tmp.type = "math"; tmp.layers = Collections.singletonList(lb.geo);
            }
            return Presets.build(tmp, merged);
        }

        List<Show.Layer> buildAll() {
            List<Show.Layer> out = new ArrayList<Show.Layer>();
            for (LB lb : layers) out.addAll(buildLayer(lb));
            if (out.isEmpty()) throw new IllegalStateException("в шоу нет ни одного слоя");
            return out;
        }

        public int estimatePoints() {
            int n = 0;
            try { for (Show.Layer l : buildAll()) n += l.src.estimate(); } catch (Throwable ignored) {}
            return n;
        }

        public String validate() {
            try { buildAll(); return null; }
            catch (Expr.ParseError e) { return e.getMessage(); }
            catch (Throwable t) { return t.getMessage() == null ? t.toString() : t.getMessage(); }
        }

        public ShowHandle start() {
            List<Show.Layer> built = buildAll();
            Show s = plugin.manager().startLayers(ownerPlayer, built, showParams, label, loc, aud);
            if (s == null) return null;
            for (Consumer<ShowHandle> c : callbacks) s.onEnd(c);
            return s;
        }

        public void saveAs(String presetName) {
            Presets.Preset p = new Presets.Preset();
            p.name = presetName.toLowerCase(Locale.ROOT);
            p.author = "api";
            p.defaults = showParams;
            boolean allMath = true;
            for (LB lb : layers) if (lb.kind != MATH) { allMath = false; break; }
            if (allMath) {
                p.type = "math";
                for (LB lb : layers) {
                    String extra = lb.p.serialize();
                    p.layers.add(extra.isEmpty() ? lb.geo : lb.geo + " @ " + extra);
                }
            } else if (layers.size() == 1) {
                LB lb = layers.get(0);
                p.defaults = lb.p.under(showParams);
                switch (lb.kind) {
                    case TEXT: p.type = "text"; p.text = lb.geo; break;
                    case IMAGE: p.type = "image"; p.rows = lb.imageRows; break;
                    case FRAMES: p.type = "draw"; p.frames = lb.frames; break;
                    default: throw new IllegalStateException("этот слой нельзя сохранить пресетом");
                }
            } else {
                throw new IllegalStateException("смешанные слои нельзя сохранить одним пресетом — сохрани их по отдельности");
            }
            plugin.presets().put(p);
            plugin.presets().save();
        }
    }
}
