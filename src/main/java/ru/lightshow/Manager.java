package ru.lightshow;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.lightshow.api.Audience;
import ru.lightshow.api.ShowEndEvent;
import ru.lightshow.api.ShowStartEvent;

import java.io.File;
import java.util.*;

/** Запуск/остановка шоу + общий тикер с лимитом частиц на сервер. */
public final class Manager {

    private final LightShow plugin;
    private final List<Show> shows = new ArrayList<Show>();
    private int nextId = 1;
    private int globalBudget = 20000;
    private long emittedLastTick = 0;

    public Manager(LightShow plugin) { this.plugin = plugin; }

    public void setBudget(int b) { globalBudget = b; }
    public int budget() { return globalBudget; }
    public List<Show> shows() { return shows; }
    public long lastLoad() { return emittedLastTick; }

    public Show start(Player creator, Presets.Preset preset, Params params, String label) {
        Params merged = params.under(preset.defaults);
        List<Show.Layer> layers = Presets.build(preset, merged);
        Location loc = resolve(creator, merged);
        if (loc == null || loc.getWorld() == null) throw new Expr.ParseError("не понял, где ставить шоу (укажи at:x,y,z)", 0);
        return register(new Show(nextId++, label == null ? preset.name : label, creator, loc, layers, merged), null);
    }

    /** Общая точка входа: событие + добавление в список. null, если ShowStartEvent отменили. */
    private Show register(Show s, Audience audience) {
        if (audience != null) s.setAudience(audience);
        ShowStartEvent ev = new ShowStartEvent(s);
        try { Bukkit.getPluginManager().callEvent(ev); } catch (Throwable ignored) {}
        if (ev.isCancelled()) return null;
        shows.add(s);
        return s;
    }

    public Show startLayers(Player creator, List<Show.Layer> layers, Params merged, String label) {
        return startLayers(creator, layers, merged, label, null, null);
    }

    public Show startLayers(Player creator, List<Show.Layer> layers, Params merged, String label,
                            Location explicit, Audience audience) {
        Location loc = explicit != null ? explicit : resolve(creator, merged);
        if (loc == null || loc.getWorld() == null) throw new Expr.ParseError("не понял, где ставить шоу (укажи at:x,y,z)", 0);
        return register(new Show(nextId++, label, creator, loc, layers, merged), audience);
    }

    private Location resolve(Player p, Params par) {
        World w = null;
        if (par.has("world")) w = Bukkit.getWorld(par.str("world", ""));
        if (w == null && p != null) w = p.getWorld();
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        if (w == null) return null;

        if (par.lower("anchor", "world").equals("player") && p != null) return p.getLocation();

        if (par.has("at")) {
            String[] pr = par.str("at", "").split("[,;/ ]+");
            double[] base = p != null ? new double[] { p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ() } : new double[] { 0, 64, 0 };
            double[] xyz = new double[] { base[0], base[1], base[2] };
            for (int i = 0; i < Math.min(3, pr.length); i++) {
                String t = pr[i].trim();
                try {
                    if (t.startsWith("~")) xyz[i] = base[i] + (t.length() > 1 ? Double.parseDouble(t.substring(1)) : 0);
                    else xyz[i] = Double.parseDouble(t);
                } catch (Exception ignored) {}
            }
            return new Location(w, xyz[0], xyz[1], xyz[2]);
        }

        if (p == null) return new Location(w, 0, 80, 0);
        double dist = par.num("dist", 6);
        Location eye = p.getEyeLocation();
        return eye.clone().add(eye.getDirection().normalize().multiply(dist));
    }

    public void tickAll() {
        if (shows.isEmpty()) { emittedLastTick = 0; return; }
        long total = 0;
        int per = Math.max(200, globalBudget / Math.max(1, shows.size()));
        Iterator<Show> it = shows.iterator();
        while (it.hasNext()) {
            Show s = it.next();
            try {
                total += s.tick(per);
            } catch (Throwable t) {
                plugin.getLogger().warning("Шоу " + s.label + " упало: " + t);
                s.kill();
            }
            if (s.isDead()) { it.remove(); finish(s); }
        }
        emittedLastTick = total;
    }

    private void finish(Show s) {
        try { s.fireEnd(); } catch (Throwable ignored) {}
        try { Bukkit.getPluginManager().callEvent(new ShowEndEvent(s)); } catch (Throwable ignored) {}
    }

    public int stopAll() {
        int n = shows.size();
        for (Show s : shows) { s.kill(); finish(s); }
        shows.clear();
        return n;
    }

    public Show byId(int id) { for (Show s : shows) if (s.id == id) return s; return null; }

    public int stopOf(UUID owner) {
        int n = 0;
        Iterator<Show> it = shows.iterator();
        while (it.hasNext()) {
            Show s = it.next();
            if (owner.equals(s.owner)) { s.kill(); it.remove(); finish(s); n++; }
        }
        return n;
    }

    public boolean stopId(int id) {
        Iterator<Show> it = shows.iterator();
        while (it.hasNext()) {
            Show s = it.next();
            if (s.id == id) { s.kill(); it.remove(); finish(s); return true; }
        }
        return false;
    }

    // ------------------------------------------------------------- ambient

    public static final class Ambient {
        public String id, preset, world, params;
        public double x, y, z;
    }

    private final LinkedHashMap<String, Ambient> ambients = new LinkedHashMap<String, Ambient>();

    public Collection<Ambient> ambients() { return ambients.values(); }

    public void loadAmbients() {
        ambients.clear();
        File f = new File(plugin.getDataFolder(), "ambient.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("ambient");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            Ambient a = new Ambient();
            a.id = k;
            a.preset = root.getString(k + ".preset", "");
            a.world = root.getString(k + ".world", "world");
            a.x = root.getDouble(k + ".x"); a.y = root.getDouble(k + ".y"); a.z = root.getDouble(k + ".z");
            a.params = root.getString(k + ".params", "");
            ambients.put(k.toLowerCase(Locale.ROOT), a);
        }
    }

    public void saveAmbients() {
        YamlConfiguration y = new YamlConfiguration();
        for (Ambient a : ambients.values()) {
            String b = "ambient." + a.id + ".";
            y.set(b + "preset", a.preset); y.set(b + "world", a.world);
            y.set(b + "x", a.x); y.set(b + "y", a.y); y.set(b + "z", a.z);
            y.set(b + "params", a.params);
        }
        try { y.save(new File(plugin.getDataFolder(), "ambient.yml")); }
        catch (Exception e) { plugin.getLogger().warning("ambient.yml: " + e.getMessage()); }
    }

    public void addAmbient(String id, String preset, Location loc, Params p) {
        Ambient a = new Ambient();
        a.id = id; a.preset = preset; a.world = loc.getWorld().getName();
        a.x = loc.getX(); a.y = loc.getY(); a.z = loc.getZ();
        a.params = p.serialize();
        ambients.put(id.toLowerCase(Locale.ROOT), a);
        saveAmbients();
    }

    public boolean delAmbient(String id) {
        Ambient a = ambients.remove(id.toLowerCase(Locale.ROOT));
        if (a == null) return false;
        Iterator<Show> it = shows.iterator();
        while (it.hasNext()) {
            Show s = it.next();
            if (s.label.equals("ambient:" + a.id)) { s.kill(); it.remove(); finish(s); }
        }
        saveAmbients();
        return true;
    }

    public void restartAmbients() {
        Iterator<Show> it = shows.iterator();
        while (it.hasNext()) { Show s = it.next(); if (s.label.startsWith("ambient:")) { s.kill(); it.remove(); } }
        startAmbients();
    }

    public int startAmbients() {
        int n = 0;
        for (Ambient a : ambients.values()) {
            try {
                Params p = Params.deserialize(a.params);
                p.set("world", a.world);
                p.set("at", a.x + "," + a.y + "," + a.z);
                if (!p.has("dur")) p.set("dur", "inf");
                if (!p.has("face")) p.set("face", "north");
                Presets.Preset pr = null;
                List<Show.Layer> layers = new ArrayList<Show.Layer>();
                for (String pn : a.preset.split("\\+")) {
                    Presets.Preset one = plugin.presets().get(pn);
                    if (one == null) throw new IllegalStateException("нет пресета " + pn);
                    if (pr == null) pr = one;
                    layers.addAll(Presets.build(one, p.under(one.defaults)));
                }
                Params merged = p.under(pr.defaults);
                World w = Bukkit.getWorld(a.world);
                if (w == null) continue;
                shows.add(new Show(nextId++, "ambient:" + a.id, null, new Location(w, a.x, a.y, a.z), layers, merged));
                n++;
            } catch (Throwable t) {
                plugin.getLogger().warning("Ambient '" + a.id + "' не запустился: " + t.getMessage());
            }
        }
        return n;
    }
}
