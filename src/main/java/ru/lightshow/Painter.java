package ru.lightshow;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Всё, что касается самих частиц.
 *
 * Главные вещи, которых не хватало раньше:
 *  1) count = 0. Тогда клиент рождает РОВНО одну частицу, а offset(dx,dy,dz) превращается
 *     в ВЕКТОР СКОРОСТИ, умноженный на speed. Это и есть тот самый "полёт" end_rod,
 *     про который ты писал. При count = 1 (как было) offset — это случайный разброс,
 *     а speed — случайная скорость, отсюда и каша.
 *  2) longDistance (force). Без него клиент сам вырезает все частицы дальше 32 блоков.
 *     Поэтому шоу в небе выглядело "рваным" и пропадающим.
 *  3) Пакеты шлём только тем, кто реально рядом (view:), а не всему миру.
 */
public final class Painter {

    private Painter() {}

    // ------------------------------------------------------------- реестр частиц

    private static final LinkedHashMap<String, Particle> TYPES = new LinkedHashMap<String, Particle>();

    private static void reg(String alias, String enumName) {
        try { TYPES.put(alias, Particle.valueOf(enumName)); } catch (Throwable ignored) {}
    }

    static {
        reg("end_rod", "END_ROD");
        reg("dust", "REDSTONE");
        reg("flame", "FLAME");
        reg("soul_fire", "SOUL_FIRE_FLAME");
        reg("soul", "SOUL");
        reg("smoke", "SMOKE_NORMAL");
        reg("big_smoke", "SMOKE_LARGE");
        reg("campfire", "CAMPFIRE_COSY_SMOKE");
        reg("signal", "CAMPFIRE_SIGNAL_SMOKE");
        reg("crit", "CRIT");
        reg("magic_crit", "CRIT_MAGIC");
        reg("enchant", "ENCHANTMENT_TABLE");
        reg("portal", "PORTAL");
        reg("reverse_portal", "REVERSE_PORTAL");
        reg("spark", "FIREWORKS_SPARK");
        reg("note", "NOTE");
        reg("happy", "VILLAGER_HAPPY");
        reg("angry", "VILLAGER_ANGRY");
        reg("heart", "HEART");
        reg("totem", "TOTEM");
        reg("dragon", "DRAGON_BREATH");
        reg("cloud", "CLOUD");
        reg("spell", "SPELL_INSTANT");
        reg("spell_color", "SPELL_MOB");
        reg("witch", "SPELL_WITCH");
        reg("slime", "SLIME");
        reg("snow", "SNOW_SHOVEL");
        reg("ash", "ASH");
        reg("white_ash", "WHITE_ASH");
        reg("crimson", "CRIMSON_SPORE");
        reg("warped", "WARPED_SPORE");
        reg("nautilus", "NAUTILUS");
        reg("dolphin", "DOLPHIN");
        reg("bubble", "WATER_BUBBLE");
        reg("splash", "WATER_SPLASH");
        reg("lava", "LAVA");
        reg("drip_lava", "FALLING_LAVA");
        reg("drip_water", "FALLING_WATER");
        reg("honey", "FALLING_HONEY");
        reg("obsidian_tear", "FALLING_OBSIDIAN_TEAR");
        reg("sneeze", "SNEEZE");
        reg("damage", "DAMAGE_INDICATOR");
        reg("sweep", "SWEEP_ATTACK");
        reg("flash", "FLASH");
        reg("squid_ink", "SQUID_INK");
        reg("composter", "COMPOSTER");
        reg("town_aura", "TOWN_AURA");
    }

    public static Set<String> typeNames() { return TYPES.keySet(); }

    /** Свой псевдоним частицы от стороннего плагина. */
    public static void registerAlias(String alias, Particle particle) {
        if (alias != null && particle != null) TYPES.put(alias.toLowerCase(Locale.ROOT), particle);
    }

    public static Particle type(String name, Particle def) {
        if (name == null) return def;
        Particle p = TYPES.get(name.toLowerCase(Locale.ROOT));
        if (p != null) return p;
        try { return Particle.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Throwable t) { return def; }
    }

    /** Частицы, которые умеют менять цвет напрямую. */
    public static boolean isDust(Particle p) { return p != null && p.name().equals("REDSTONE"); }
    public static boolean isSpellColor(Particle p) { return p != null && p.name().equals("SPELL_MOB"); }
    public static boolean isNote(Particle p) { return p != null && p.name().equals("NOTE"); }

    // ------------------------------------------------------------- цвета

    /** Описание цвета: solid / rainbow / gradient / pulse. */
    public static final class Col {
        int mode; int a, b; double speed = 1;

        public static Col parse(String spec) {
            Col c = new Col();
            if (spec == null || spec.isEmpty()) { c.mode = 0; c.a = 0xFFFFFF; return c; }
            String s = spec.trim().toLowerCase(Locale.ROOT);
            if (s.startsWith("rainbow")) {
                c.mode = 1;
                int i = s.indexOf(':');
                if (i > 0) c.speed = parseD(s.substring(i + 1), 1);
                return c;
            }
            if (s.startsWith("gradient:") || s.startsWith("grad:") || s.startsWith("pulse:")) {
                c.mode = s.startsWith("pulse:") ? 3 : 2;
                String rest = s.substring(s.indexOf(':') + 1);
                String[] pr = rest.split("-|>|:");
                c.a = Painter.rgb(pr.length > 0 ? pr[0] : "white");
                c.b = Painter.rgb(pr.length > 1 ? pr[1] : "black");
                if (pr.length > 2) c.speed = parseD(pr[2], 1);
                return c;
            }
            c.mode = 0; c.a = Painter.rgb(s); return c;
        }

        public int rgb(double f, double time) {
            switch (mode) {
                case 1: return hsv((f * 0.9 + time * 0.15 * speed) % 1.0, 1, 1);
                case 2: return mix(a, b, f);
                case 3: return mix(a, b, 0.5 + 0.5 * Math.sin(time * speed * 2));
                default: return a;
            }
        }
        public boolean animated() { return mode == 1 || mode == 3; }
    }

    private static final Map<String, Integer> NAMED = new LinkedHashMap<String, Integer>();
    static {
        NAMED.put("white", 0xFFFFFF); NAMED.put("black", 0x0A0A0A); NAMED.put("gray", 0x888888);
        NAMED.put("red", 0xFF2020); NAMED.put("dark_red", 0x8B0000); NAMED.put("orange", 0xFF8000);
        NAMED.put("gold", 0xFFD000); NAMED.put("yellow", 0xFFFF40); NAMED.put("lime", 0x80FF00);
        NAMED.put("green", 0x20C020); NAMED.put("aqua", 0x40FFF0); NAMED.put("cyan", 0x00FFFF);
        NAMED.put("blue", 0x2040FF); NAMED.put("dark_blue", 0x0000A0); NAMED.put("purple", 0xA020F0);
        NAMED.put("magenta", 0xFF00FF); NAMED.put("pink", 0xFF80C0); NAMED.put("mint", 0x80FFC0);
        NAMED.put("ice", 0xB0F0FF); NAMED.put("lava", 0xFF4500); NAMED.put("soul", 0x30D0D0);
    }
    public static Set<String> colorNames() { return NAMED.keySet(); }

    public static int rgb(String s) {
        if (s == null) return 0xFFFFFF;
        s = s.trim().toLowerCase(Locale.ROOT);
        Integer n = NAMED.get(s);
        if (n != null) return n;
        if (s.startsWith("#")) s = s.substring(1);
        try { return (int) (Long.parseLong(s, 16) & 0xFFFFFF); } catch (Exception e) { return 0xFFFFFF; }
    }

    static int mix(int a, int b, double f) {
        f = Expr.clamp(f, 0, 1);
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    static int hsv(double h, double s, double v) {
        h = (h % 1.0 + 1.0) % 1.0;
        int i = (int) (h * 6); double f = h * 6 - i;
        double p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        double r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break; case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break; case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break; default: r = v; g = p; b = q;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static double parseD(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    // ------------------------------------------------------------- отправка

    /** Каким способом пакеты уходят игрокам. */
    public enum Transport { NMS, PROTOCOLLIB, BUKKIT }

    private static Transport mode = Transport.BUKKIT;
    private static boolean nmsReady = false;
    private static Constructor<?> packetCtor;
    private static Method craftToNms, getHandle, sendPacket;
    private static Field connField;

    public static Transport transport() { return mode; }

    /** preferred: NMS / PROTOCOLLIB / BUKKIT / null = авто. */
    public static void init(String preferred) {
        boolean wantNms = preferred == null || preferred.equalsIgnoreCase("auto") || preferred.equalsIgnoreCase("nms");
        boolean wantPl = preferred == null || preferred.equalsIgnoreCase("auto") || preferred.equalsIgnoreCase("protocollib");
        if (wantNms) { initNms(); if (nmsReady) { mode = Transport.NMS; return; } }
        if (wantPl && initProtocolLib()) { mode = Transport.PROTOCOLLIB; return; }
        if (!wantNms && !nmsReady) { initNms(); if (nmsReady) { mode = Transport.NMS; return; } }
        mode = Transport.BUKKIT;
    }

    // ---- ProtocolLib ----
    private static Object plManager;
    private static Object plPacketType;
    private static Method plCreate, plSend, plWrapParticle;
    private static Method plGetDoubles, plGetFloat, plGetIntegers, plGetBooleans, plGetNewParticles, plWrite;

    private static boolean initProtocolLib() {
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return false;
            Class<?> lib = Class.forName("com.comphenix.protocol.ProtocolLibrary");
            plManager = lib.getMethod("getProtocolManager").invoke(null);
            Class<?> typeCls = Class.forName("com.comphenix.protocol.PacketType");
            Class<?> serverCls = Class.forName("com.comphenix.protocol.PacketType$Play$Server");
            plPacketType = serverCls.getField("WORLD_PARTICLES").get(null);
            Class<?> containerCls = Class.forName("com.comphenix.protocol.events.PacketContainer");
            plCreate = plManager.getClass().getMethod("createPacket", typeCls);
            plSend = plManager.getClass().getMethod("sendServerPacket", Player.class, containerCls);
            plGetDoubles = containerCls.getMethod("getDoubles");
            plGetFloat = containerCls.getMethod("getFloat");
            plGetIntegers = containerCls.getMethod("getIntegers");
            plGetBooleans = containerCls.getMethod("getBooleans");
            plGetNewParticles = containerCls.getMethod("getNewParticles");
            plWrite = Class.forName("com.comphenix.protocol.reflect.StructureModifier")
                    .getMethod("write", int.class, Object.class);
            plWrapParticle = Class.forName("com.comphenix.protocol.wrappers.WrappedParticle")
                    .getMethod("fromBukkit", Particle.class, Object.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean sendViaProtocolLib(List<Player> viewers, Particle particle, Object data,
                                              double x, double y, double z,
                                              double dx, double dy, double dz, double speed, int count) {
        try {
            Object pc = plCreate.invoke(plManager, plPacketType);
            Object d = plGetDoubles.invoke(pc);
            plWrite.invoke(d, 0, x); plWrite.invoke(d, 1, y); plWrite.invoke(d, 2, z);
            Object f = plGetFloat.invoke(pc);
            plWrite.invoke(f, 0, (float) dx); plWrite.invoke(f, 1, (float) dy);
            plWrite.invoke(f, 2, (float) dz); plWrite.invoke(f, 3, (float) speed);
            plWrite.invoke(plGetIntegers.invoke(pc), 0, count);
            plWrite.invoke(plGetBooleans.invoke(pc), 0, Boolean.TRUE);
            plWrite.invoke(plGetNewParticles.invoke(pc), 0, plWrapParticle.invoke(null, particle, data));
            for (int i = 0; i < viewers.size(); i++) plSend.invoke(plManager, viewers.get(i), pc);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void initNms() {
        try {
            String pkg = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            String ver = pkg.substring(pkg.lastIndexOf('.') + 1);
            Class<?> craftParticle = Class.forName("org.bukkit.craftbukkit." + ver + ".CraftParticle");
            Class<?> paramCls = Class.forName("net.minecraft.server." + ver + ".ParticleParam");
            Class<?> packetCls = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutWorldParticles");
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + ver + ".entity.CraftPlayer");
            craftToNms = craftParticle.getMethod("toNMS", Particle.class, Object.class);
            packetCtor = packetCls.getConstructor(paramCls, boolean.class,
                    double.class, double.class, double.class,
                    float.class, float.class, float.class, float.class, int.class);
            getHandle = craftPlayer.getMethod("getHandle");
            Class<?> entityPlayer = getHandle.getReturnType();
            connField = entityPlayer.getField("playerConnection");
            for (Method m : connField.getType().getMethods()) {
                if (m.getName().equals("sendPacket") && m.getParameterCount() == 1) { sendPacket = m; break; }
            }
            craftToNms.setAccessible(true); getHandle.setAccessible(true);
            connField.setAccessible(true); sendPacket.setAccessible(true);
            nmsReady = craftToNms != null && packetCtor != null && sendPacket != null;
        } catch (Throwable t) {
            nmsReady = false;
        }
    }

    public static boolean nmsAvailable() { return nmsReady; }

    private static final java.util.WeakHashMap<Player, Object> CONN = new java.util.WeakHashMap<Player, Object>();

    private static Object conn(Player p) throws Exception {
        Object c = CONN.get(p);
        if (c == null) { c = connField.get(getHandle.invoke(p)); CONN.put(p, c); }
        return c;
    }

    public static void forgetPlayer(Player p) { CONN.remove(p); }

    /**
     * Отправить одну частицу списку игроков.
     * count = 0 → одна частица, (dx,dy,dz) * speed = её начальная скорость.
     */
    public static void send(List<Player> viewers, World world, Particle particle, Object data,
                            double x, double y, double z,
                            double dx, double dy, double dz, double speed, int count) {
        if (viewers.isEmpty()) return;
        if (mode == Transport.PROTOCOLLIB) {
            if (sendViaProtocolLib(viewers, particle, data, x, y, z, dx, dy, dz, speed, count)) return;
            mode = Transport.BUKKIT;
        }
        if (nmsReady) {
            try {
                Object param = craftToNms.invoke(null, particle, data);
                Object packet = packetCtor.newInstance(param, true, x, y, z,
                        (float) dx, (float) dy, (float) dz, (float) speed, count);
                for (int i = 0; i < viewers.size(); i++) sendPacket.invoke(conn(viewers.get(i)), packet);
                return;
            } catch (Throwable t) {
                nmsReady = false;                       // один раз упало — дальше идём обычным путём
                if (initProtocolLib()) mode = Transport.PROTOCOLLIB; else mode = Transport.BUKKIT;
            }
        }
        // Запасной путь через Bukkit API (force = true, иначе клиент режет всё дальше 32 блоков)
        world.spawnParticle(particle, x, y, z, count, dx, dy, dz, speed, data, true);
    }

    public static Object dustData(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255), size);
    }
}
