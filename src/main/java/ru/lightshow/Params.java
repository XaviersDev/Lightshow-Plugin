package ru.lightshow;

import java.util.*;

/**
 * Параметры вида ключ:значение.
 * Такой формат выбран специально ради нормального табкомплита:
 * плагин всегда знает, какой ключ ты пишешь, и подсказывает готовые ЗНАЧЕНИЯ,
 * а не ники игроков.
 */
public final class Params {

    public static final class Meta {
        public final String desc; public final String[] vals;
        Meta(String d, String... v) { desc = d; vals = v; }
    }

    public static final LinkedHashMap<String, Meta> KEYS = new LinkedHashMap<String, Meta>();

    private static void k(String name, String desc, String... vals) { KEYS.put(name, new Meta(desc, vals)); }

    static {
        k("dist", "на сколько блоков перед тобой поставить шоу", "3", "5", "8", "12", "20", "35");
        k("at", "точные координаты (x,y,z или ~,~,~)", "~,~,~", "~,~5,~", "0,80,0");
        k("dur", "сколько живёт шоу", "5s", "10s", "30s", "1m", "5m", "inf");
        k("loop", "повторять по кругу", "true", "false");
        k("size", "общий масштаб фигуры", "0.5", "1", "1.5", "2", "3", "5", "8");
        k("steps", "точек вдоль кривой (густота)", "80", "160", "260", "400", "800", "1500");
        k("mode", "как рисуем формулу", "curve", "tube", "surface", "fill");
        k("radius", "радиус трубы для mode:tube", "0.5", "1", "1.5", "2", "3");
        k("sides", "граней у трубы", "6", "8", "12", "16", "24", "32");
        k("t", "диапазон параметра t", "0..6.28", "0..12.56", "-3.14..3.14", "0..25");
        k("u", "диапазон параметра u (surface/fill)", "0..1", "0..3.14", "0..6.28");
        k("usteps", "шагов по u", "8", "16", "24", "40");
        k("particle", "тип частиц", "end_rod", "dust", "flame", "soul_fire", "soul", "spark", "enchant");
        k("color", "цвет (для dust/spell_color/note)", "white", "aqua", "#00FFAA", "rainbow", "rainbow:2", "gradient:#ff00ff-#00ffff", "pulse:aqua-purple");
        k("psize", "размер пикселя dust-частицы", "0.5", "0.75", "1", "1.5", "2.5");
        k("motion", "куда летят частицы (работает на count=0)", "none", "out", "in", "up", "down", "flow", "spin", "to_player", "from_player", "look", "random", "vec:0,1,0");
        k("mspeed", "скорость этого полёта", "0.01", "0.05", "0.1", "0.25", "0.5", "1");
        k("lift", "подъём против гравитации end_rod", "0", "0.005", "0.01", "0.02");
        k("refresh", "раз в сколько тиков обновлять точку (больше = дешевле)", "2", "3", "5", "8", "12");
        k("cull", "не слать точки дальше стольких блоков от игрока (0 = слать всё)", "24", "40", "56", "80", "0");
        k("flyd", "с какого расстояния прилетают частицы у in:fly", "8", "14", "20", "30");
        k("density", "доля точек, которую реально рисуем", "0.25", "0.5", "0.75", "1");
        k("in", "анимация появления", "letters", "typeletters", "popletters", "fly", "none", "fade", "type", "wipe", "rise", "drop", "explode", "scale", "spiral");
        k("out", "анимация исчезновения", "letters", "fly", "none", "fade", "scatter", "fall", "dissolve", "wipe", "implode", "shrink");
        k("int", "длительность появления", "5t", "10t", "20t", "1s", "2s", "3s");
        k("outt", "длительность исчезновения", "5t", "10t", "20t", "1s", "2s", "3s");
        k("spin", "вращение градусов/сек (одно число или x,y,z)", "0", "15", "45", "90", "0,45,0", "20,0,30");
        k("face", "куда повёрнута фигура", "loc", "player", "north", "south", "east", "west", "up", "down", "auto");
        k("anchor", "привязка", "world", "player");
        k("offset", "сдвиг относительно центра", "0,0,0", "0,2,0", "0,-1,0");
        k("view", "радиус видимости шоу в блоках", "32", "64", "96", "128", "256");
        k("max", "жёсткий лимит частиц за тик у этого шоу", "300", "600", "1200", "2500");
        k("font", "шрифт для текста", "pixel", "bold", "thin");
        k("align", "выравнивание текста", "left", "center", "right");
        k("spacing", "расстояние между буквами (пикселей)", "0", "1", "2", "3");
        k("px", "размер одного пикселя текста/рисунка в блоках", "0.1", "0.15", "0.25", "0.4", "0.6");
        k("fps", "тиков на кадр нарисованной анимации", "1", "2", "4", "8", "20");
        k("pingpong", "проигрывать кадры туда-обратно", "true", "false");
        k("who", "кто видит", "all", "me");
        k("from", "СЦЕНЫ: когда этот слой появляется", "0", "1s", "3s", "5s", "60t");
        k("to", "СЦЕНЫ: когда этот слой исчезает", "5s", "10s", "20s");
        k("ox", "смещение слоя по X, можно формулой от T", "3", "-4", "-4-lerp(0,5,smooth((T-3)/1))");
        k("oy", "смещение слоя по Y, можно формулой от T", "2", "-1", "lerp(0,4,smooth((T-2)/1))");
        k("oz", "смещение слоя по Z, можно формулой от T", "0", "2", "-3");
        k("zoom", "масштаб слоя, можно формулой от T", "1", "0.5", "lerp(0.2,1,smooth((T-5)/1.5))");
        k("rotx", "наклон слоя вокруг X, градусы, можно формулой", "0", "45", "T*30");
        k("roty", "поворот слоя вокруг Y, градусы, можно формулой", "0", "90", "T*40");
        k("rotz", "поворот слоя в плоскости экрана, градусы", "0", "15", "T*60", "10*sin(T*2)");
        k("lgap", "расстояние между строками текста в пикселях", "0", "1", "3", "6", "10");
        k("outline", "только контур букв/рисунка (втрое меньше частиц)", "true", "false");
        k("sound", "звук в момент появления слоя", "entity.ender_dragon.growl", "block.beacon.activate", "entity.player.levelup", "block.note_block.chime");
        k("svol", "громкость звука", "0.5", "1", "2");
        k("spitch", "высота звука", "0.5", "1", "1.5", "2");
        k("vx", "скорость точки по X (мировые оси), формула от i/T", "0.4", "0.3*noise(i*.7)", "0");
        k("vy", "скорость точки по Y: минус = падает", "-0.7", "-0.4", "0.2");
        k("vz", "скорость точки по Z", "0.2", "0.15*noise(i*.5)", "0");
        k("trail", "сколько частиц тянется хвостом позади", "0", "3", "6", "10");
        k("tgap", "расстояние между частицами хвоста", "0.25", "0.4", "0.6", "1");
        k("jitter", "случайный сдвиг точки при каждом вылете", "0", "1", "3", "8");
        k("chance", "вероятность, что точка выстрелит в свою очередь", "0.15", "0.3", "0.6", "1");
        k("count", "частиц в одном пакете (облачко)", "0", "2", "4", "8");
        k("spread", "радиус разброса для count", "0.15", "0.3", "0.6", "1.5");
        k("burst", "зажигать весь слой разом, а не по частям", "true", "false");
        k("drift", "фигура летит целиком, движение считает клиент", "0,0.05,0", "0.1,0,0", "0,0,-0.08");
        k("wave", "волна по буквам: амплитуда,скорость", "0.3,6", "0.5,4", "0.2,10");
        k("every", "повторять слой каждые…", "1s", "2s", "5s");
        k("for", "сколько длится каждый повтор", "0.5s", "1s", "2s");
        k("world", "мир (для ambient/at)", "world", "world_nether", "world_the_end");
        k("w", "ширина картинки в пикселях", "32", "48", "64", "96", "128");
        k("h", "высота картинки в пикселях", "32", "48", "64", "96", "128");
        k("alpha", "порог прозрачности картинки", "0.2", "0.5", "0.8");
    }

    private final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();

    public Params() {}
    public Params(Map<String, String> src) { if (src != null) map.putAll(src); }

    /** Разбирает аргументы вида key:value начиная с индекса from. Возвращает неопознанные куски. */
    public List<String> parse(String[] args, int from) {
        List<String> rest = new ArrayList<String>();
        for (int i = from; i < args.length; i++) {
            String a = args[i];
            int c = a.indexOf(':');
            if (c > 0 && KEYS.containsKey(a.substring(0, c).toLowerCase(Locale.ROOT)))
                map.put(a.substring(0, c).toLowerCase(Locale.ROOT), a.substring(c + 1));
            else rest.add(a);
        }
        return rest;
    }

    public Params merge(Params other) {
        Params p = new Params(this.map);
        if (other != null) for (Map.Entry<String, String> e : other.map.entrySet()) p.map.put(e.getKey(), e.getValue());
        return p;
    }
    public Params under(Params defaults) { return defaults == null ? this : defaults.merge(this); }

    public Map<String, String> raw() { return map; }
    public boolean has(String k) { return map.containsKey(k); }
    public void set(String k, String v) { map.put(k, v); }

    public String str(String k, String def) { String v = map.get(k); return v == null || v.isEmpty() ? def : v; }
    public String lower(String k, String def) { return str(k, def).toLowerCase(Locale.ROOT); }

    public double num(String k, double def) {
        String v = map.get(k);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); } catch (Exception e) { return def; }
    }

    public int integer(String k, int def) { return (int) Math.round(num(k, def)); }

    public boolean bool(String k, boolean def) {
        String v = map.get(k);
        if (v == null) return def;
        v = v.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("yes") || v.equals("1") || v.equals("on") || v.equals("да");
    }

    /** "30s", "600t", "2m", "inf" → тики (-1 = бесконечно). */
    public int ticks(String k, int def) {
        String v = map.get(k);
        if (v == null) return def;
        v = v.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("inf") || v.equals("-1") || v.equals("forever")) return -1;
        try {
            if (v.endsWith("ms")) return (int) (Double.parseDouble(v.substring(0, v.length() - 2)) / 50.0);
            if (v.endsWith("t")) return (int) Double.parseDouble(v.substring(0, v.length() - 1));
            if (v.endsWith("s")) return (int) (Double.parseDouble(v.substring(0, v.length() - 1)) * 20);
            if (v.endsWith("m")) return (int) (Double.parseDouble(v.substring(0, v.length() - 1)) * 1200);
            return (int) (Double.parseDouble(v) * 20);
        } catch (Exception e) { return def; }
    }

    /** "0..6.28" → {0, 6.28} */
    public double[] range(String k, double a, double b) {
        String v = map.get(k);
        if (v == null) return new double[] { a, b };
        try {
            // "2pi" должно означать 2*pi, а не "2" склеенное с числом
            String s = v.replaceAll("(?i)(\\d(?:\\.\\d+)?)\\s*pi", "$1*" + Math.PI)
                        .replaceAll("(?i)pi", String.valueOf(Math.PI));
            int mul = s.indexOf('*');
            if (mul > 0) {   // разворачиваем простые произведения вида 2*3.1415
                StringBuilder sb = new StringBuilder();
                for (String part : s.split("\\.\\.")) {
                    if (sb.length() > 0) sb.append("..");
                    double acc = 1; boolean ok = true;
                    for (String f : part.trim().split("\\*")) {
                        try { acc *= Double.parseDouble(f.trim()); } catch (Exception e) { ok = false; break; }
                    }
                    sb.append(ok ? String.valueOf(acc) : part.trim());
                }
                s = sb.toString();
            }
            int i = s.indexOf("..");
            if (i < 0) return new double[] { 0, Double.parseDouble(s.trim()) };
            return new double[] { Double.parseDouble(s.substring(0, i).trim()), Double.parseDouble(s.substring(i + 2).trim()) };
        } catch (Exception e) { return new double[] { a, b }; }
    }

    /** "1,2,3" → вектор. */
    public double[] vec(String k, double x, double y, double z) {
        String v = map.get(k);
        if (v == null) return new double[] { x, y, z };
        String[] pr = v.split("[,;/ ]+");
        double[] out = new double[] { x, y, z };
        for (int i = 0; i < Math.min(3, pr.length); i++) {
            try { out[i] = Double.parseDouble(pr[i].trim()); } catch (Exception ignored) {}
        }
        return out;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public static Params deserialize(String s) {
        Params p = new Params();
        if (s == null || s.trim().isEmpty()) return p;
        p.parse(s.trim().split("\\s+"), 0);
        return p;
    }
}
