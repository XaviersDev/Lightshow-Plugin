package ru.lightshow;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public final class Commands implements CommandExecutor, TabCompleter {

    private static final String[] SUBS = { "play", "list", "info", "new", "set", "del", "draw", "text", "image",
            "ambient", "stop", "running", "reload", "fonts", "particles", "colors", "funcs", "examples", "ai", "help" };

    private final LightShow plugin;

    public Commands(LightShow plugin) { this.plugin = plugin; }

    private static String P = LightShow.PX;

    // ================================================================ выполнение

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("pstop")) return stop(sender, args);
        if (name.equals("phelp")) { help(sender, args.length > 0 ? args[0] : "1"); return true; }
        if (name.equals("ptext")) return text(sender, args, true);
        if (name.equals("pimage")) return image(sender, args);

        if (args.length == 0) { help(sender, "1"); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help")) { help(sender, args.length > 1 ? args[1] : "1"); return true; }
        if (sub.equals("play") || sub.equals("start")) return play(sender, args);
        if (sub.equals("list")) return list(sender, args);
        if (sub.equals("info")) return info(sender, args);
        if (sub.equals("new")) return create(sender, args);
        if (sub.equals("set")) return setDefaults(sender, args);
        if (sub.equals("del") || sub.equals("delete")) return delete(sender, args);
        if (sub.equals("draw")) return draw(sender, args);
        if (sub.equals("text")) return text(sender, args, false);
        if (sub.equals("image")) return image(sender, args);
        if (sub.equals("ambient")) return ambient(sender, args);
        if (sub.equals("ai")) return ai(sender, args);
        if (sub.equals("stop")) return stop(sender, Arrays.copyOfRange(args, 1, args.length));
        if (sub.equals("running")) return running(sender);
        if (sub.equals("reload")) return reload(sender);
        if (sub.equals("fonts")) { columns(sender, "Шрифты", Fonts.names(), "font:"); return true; }
        if (sub.equals("particles")) { columns(sender, "Частицы", new ArrayList<String>(Painter.typeNames()), "particle:"); return true; }
        if (sub.equals("colors")) { columns(sender, "Цвета", new ArrayList<String>(Painter.colorNames()), "color:"); return true; }
        if (sub.equals("funcs")) { funcs(sender); return true; }
        if (sub.equals("examples")) { examples(sender); return true; }

        // /pshow <имя_пресета> ... — короткая форма
        if (plugin.presets().has(sub)) {
            String[] shifted = new String[args.length + 1];
            shifted[0] = "play";
            System.arraycopy(args, 0, shifted, 1, args.length);
            return play(sender, shifted);
        }

        sender.sendMessage(P + "§cНеизвестная команда. §7/pshow help");
        return true;
    }

    // ---------------------------------------------------------------- play

    private boolean play(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Только для игроков."); return true; }
        Player p = (Player) sender;
        if (!p.hasPermission("lightshow.use")) { p.sendMessage(P + "§cНет прав."); return true; }
        if (args.length < 2) { p.sendMessage(P + "§7/pshow play <пресет> [ключ:значение ...]"); return true; }

        Params par = new Params();
        par.parse(args, 2);
        String raw = args[1];

        try {
            List<Show.Layer> layers = new ArrayList<Show.Layer>();
            List<String> used = new ArrayList<String>();
            for (String pn : raw.split("\\+")) {
                Presets.Preset pr = plugin.presets().get(pn);
                if (pr == null) { p.sendMessage(P + "§cНет пресета §f" + pn + "§c. §7/pshow list"); return true; }
                layers.addAll(Presets.build(pr, par.under(pr.defaults)));
                used.add(pr.name);
            }
            Presets.Preset first = plugin.presets().get(raw.split("\\+")[0]);
            Params merged = par.under(first.defaults);
            Show s = plugin.manager().startLayers(p, layers, merged, String.join("+", used));
            if (s == null) { p.sendMessage(P + "§cЗапуск отменён другим плагином."); return true; }
            p.sendMessage(P + "Запущено §d" + String.join("+", used) + " §8[id " + s.id + "] §7точек: §f" + s.points()
                    + " §7• длительность: §f" + (s.duration() < 0 ? "∞" : fmt(s.duration() / 20.0) + "с"));
        } catch (Expr.ParseError e) {
            p.sendMessage(P + "§cФормула: §f" + e.getMessage());
        } catch (Throwable e) {
            p.sendMessage(P + "§cНе получилось: §f" + e);
        }
        return true;
    }

    // ---------------------------------------------------------------- list / info

    private boolean list(CommandSender s, String[] args) {
        String filter = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        List<Presets.Preset> items = new ArrayList<Presets.Preset>();
        for (Presets.Preset p : plugin.presets().all())
            if (filter.equals("all") || p.type.equals(filter)) items.add(p);
        s.sendMessage("");
        s.sendMessage(P + "Пресеты §7(" + items.size() + ")§f:");
        if (items.isEmpty()) s.sendMessage("§8 » §7пусто. Создай: §f/pshow new <имя> <формула>");
        for (Presets.Preset p : items) {
            String icon = p.type.equals("math") ? "§bƒ" : p.type.equals("draw") ? "§e✎" : p.type.equals("text") ? "§aT" : "§d▣";
            s.sendMessage("§8 » " + icon + " §f" + p.name + " §8· §7" + p.describe()
                    + (p.author.isEmpty() ? "" : " §8· §7" + p.author));
        }
        s.sendMessage("§8 » §7Подробно: §f/pshow info <имя>   §8|  §7Запуск: §f/pshow play <имя>");
        s.sendMessage("");
        return true;
    }

    private boolean info(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage(P + "§7/pshow info <пресет>"); return true; }
        Presets.Preset p = plugin.presets().get(args[1]);
        if (p == null) { s.sendMessage(P + "§cНет такого пресета."); return true; }
        s.sendMessage("");
        s.sendMessage(P + "§d" + p.name + " §8(" + p.type + ")");
        if (!p.author.isEmpty()) s.sendMessage("§8 » §7автор: §f" + p.author);
        for (String l : p.layers) s.sendMessage("§8 » §bƒ §f" + l);
        if (!p.text.isEmpty()) s.sendMessage("§8 » §aтекст: §f" + p.text);
        if (!p.frames.isEmpty()) s.sendMessage("§8 » §eкадров: §f" + p.frames.size());
        String d = p.defaults.serialize();
        s.sendMessage("§8 » §7параметры по умолчанию: §f" + (d.isEmpty() ? "нет" : d));
        s.sendMessage("");
        return true;
    }

    // ---------------------------------------------------------------- new / set / del

    private boolean create(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lightshow.edit")) { sender.sendMessage(P + "§cНет прав."); return true; }
        if (args.length < 3) {
            sender.sendMessage(P + "§7/pshow new <имя> <формула> [| формула2 ...] [ключ:значение]");
            sender.sendMessage("§8 » §7Пример: §f/pshow new roza x=4*cos(4*t)*cos(t);y=4*cos(4*t)*sin(t)");
            return true;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        String tail = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (tail.trim().isEmpty()) { sender.sendMessage(P + "§cФормула пустая."); return true; }

        Presets.Preset p = new Presets.Preset();
        p.name = name; p.type = "math";
        p.author = sender instanceof Player ? sender.getName() : "console";
        Params par = new Params();

        // всё после '@' внутри куска — параметры ЭТОГО слоя, остальные key:value — общие
        for (String chunk : tail.split("\\|")) {
            int at = chunk.indexOf('@');
            String geoPart = at >= 0 ? chunk.substring(0, at) : chunk;
            String layerParams = at >= 0 ? chunk.substring(at + 1).trim() : "";
            List<String> rest = par.parse(geoPart.trim().split("\\s+"), 0);
            String geo = String.join(" ", rest).trim();
            if (geo.isEmpty()) continue;
            try { if (!geo.toLowerCase(Locale.ROOT).startsWith("pix:")) Geo.MathGeo.compile(geo); }
            catch (Expr.ParseError e) {
                sender.sendMessage(P + "§cОшибка в слое §f" + (p.layers.size() + 1) + "§c: " + e.getMessage());
                return true;
            }
            p.layers.add(layerParams.isEmpty() ? geo : geo + " @ " + layerParams);
        }
        p.defaults = par;
        if (p.layers.isEmpty()) { sender.sendMessage(P + "§cФормула пустая."); return true; }
        plugin.presets().put(p);
        plugin.presets().save();
        sender.sendMessage(P + "Пресет §d" + name + " §fсохранён. Слоёв: §a" + p.layers.size());
        sender.sendMessage("§8 » §7Запуск: §f/pshow play " + name + " dist:8 dur:30s");
        return true;
    }

    private boolean setDefaults(CommandSender s, String[] args) {
        if (!s.hasPermission("lightshow.edit")) { s.sendMessage(P + "§cНет прав."); return true; }
        if (args.length < 3) { s.sendMessage(P + "§7/pshow set <пресет> <ключ:значение ...>"); return true; }
        Presets.Preset p = plugin.presets().get(args[1]);
        if (p == null) { s.sendMessage(P + "§cНет такого пресета."); return true; }
        Params add = new Params();
        add.parse(args, 2);
        p.defaults = p.defaults.merge(add);
        plugin.presets().save();
        s.sendMessage(P + "Параметры §d" + p.name + "§f: §7" + p.defaults.serialize());
        return true;
    }

    private boolean delete(CommandSender s, String[] args) {
        if (!s.hasPermission("lightshow.edit")) { s.sendMessage(P + "§cНет прав."); return true; }
        if (args.length < 2) { s.sendMessage(P + "§7/pshow del <пресет>"); return true; }
        if (!plugin.presets().has(args[1])) { s.sendMessage(P + "§cНет такого пресета."); return true; }
        plugin.presets().remove(args[1]);
        plugin.presets().save();
        s.sendMessage(P + "Удалён §d" + args[1]);
        return true;
    }

    // ---------------------------------------------------------------- draw / text / image

    private boolean draw(CommandSender s, String[] args) {
        if (!(s instanceof Player)) return true;
        if (!s.hasPermission("lightshow.edit")) { s.sendMessage(P + "§cНет прав."); return true; }
        if (args.length < 2) { s.sendMessage(P + "§7/pshow draw <имя>"); return true; }
        plugin.gui().open((Player) s, args[1].toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean text(CommandSender sender, String[] args, boolean instant) {
        if (!(sender instanceof Player)) { sender.sendMessage("Только для игроков."); return true; }
        Player p = (Player) sender;
        if (!p.hasPermission("lightshow.use")) { p.sendMessage(P + "§cНет прав."); return true; }

        Params par = new Params();
        int from = instant ? 0 : 2;
        if (!instant && args.length < 3) {
            p.sendMessage(P + "§7/pshow text <имя> <текст...> [ключ:значение]");
            return true;
        }
        List<String> rest = par.parse(args, from);
        String body = String.join(" ", rest).replace('_', ' ');
        if (body.trim().isEmpty()) {
            p.sendMessage(P + "§7/ptext <текст> [size:2 font:bold color:rainbow in:type out:fade dur:20s]");
            return true;
        }

        Presets.Preset pr = new Presets.Preset();
        pr.type = "text";
        pr.text = body;
        pr.author = p.getName();

        if (instant) {
            pr.name = "~text";
            if (!par.has("dur")) par.set("dur", "15s");
            if (!par.has("dist")) par.set("dist", "8");
            if (!par.has("in")) par.set("in", "type");
            if (!par.has("out")) par.set("out", "fade");
            try {
                Show s = plugin.manager().start(p, pr, par, "text");
                if (s == null) { p.sendMessage(P + "§cЗапуск отменён другим плагином."); return true; }
                p.sendMessage(P + "Текст показан §8[id " + s.id + "] §7точек: §f" + s.points());
            } catch (Throwable e) { p.sendMessage(P + "§cОшибка: " + e); }
            return true;
        }

        pr.name = args[1].toLowerCase(Locale.ROOT);
        pr.defaults = par;
        plugin.presets().put(pr);
        plugin.presets().save();
        p.sendMessage(P + "Текстовый пресет §d" + pr.name + " §fсохранён.");
        p.sendMessage("§8 » §7Запуск: §f/pshow play " + pr.name);
        return true;
    }

    private boolean image(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        final Player p = (Player) sender;
        if (!p.hasPermission("lightshow.edit")) { p.sendMessage(P + "§cНет прав."); return true; }
        int base = args.length > 0 && args[0].equalsIgnoreCase("image") ? 1 : 0;
        if (args.length < base + 2) {
            p.sendMessage(P + "§7/pimage <имя> <прямая-ссылка-на-png> [w:64 h:64 px:0.15]");
            return true;
        }
        final String name = args[base].toLowerCase(Locale.ROOT);
        final String url = args[base + 1];
        final Params par = new Params();
        par.parse(args, base + 2);
        final int w = Math.max(4, Math.min(160, par.integer("w", 64)));
        final int h = Math.max(4, Math.min(160, par.integer("h", 64)));
        p.sendMessage(P + "Качаю картинку…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final List<String> rows = ImageLoader.load(url, w, h, par.num("alpha", 0.5));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Presets.Preset pr = new Presets.Preset();
                    pr.name = name; pr.type = "image"; pr.rows = rows; pr.author = p.getName();
                    pr.defaults = par;
                    if (!pr.defaults.has("px")) pr.defaults.set("px", "0.15");
                    if (!pr.defaults.has("particle")) pr.defaults.set("particle", "dust");
                    plugin.presets().put(pr);
                    plugin.presets().save();
                    p.sendMessage(P + "Картинка §d" + name + " §fготова §7(" + rows.size() + " строк)");
                    p.sendMessage("§8 » §f/pshow play " + name + " dist:10 dur:30s");
                });
            } catch (Throwable e) {
                Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(P + "§cНе смог загрузить: §f" + e.getMessage()));
            }
        });
        return true;
    }

    // ---------------------------------------------------------------- ии

    private boolean ai(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Только для игроков."); return true; }
        Player p = (Player) sender;
        if (!p.hasPermission("lightshow.ai")) { p.sendMessage(P + "§cНет прав на ИИ."); return true; }
        if (args.length < 2) {
            p.sendMessage("");
            p.sendMessage(P + "ИИ собирает шоу по описанию:");
            p.sendMessage("§8 » §f/pshow ai §7красивая луна в форме отрезанного бублика, вокруг падают метеориты");
            p.sendMessage("§8 » §f/pshow ai run 1 §8| §fshow 1 §8| §ffix 1 <что не так> §8| §fdel 1");
            p.sendMessage("§8 » §7Он знает про end_rod, refresh и лимиты — считает нагрузку до запуска.");
            p.sendMessage("");
            return true;
        }
        String act = args[1].toLowerCase(Locale.ROOT);
        boolean isAct = act.equals("run") || act.equals("show") || act.equals("del") || act.equals("fix");
        if (isAct && args.length >= 3) {
            int idx;
            try { idx = Integer.parseInt(args[2]); }
            catch (NumberFormatException e) { p.sendMessage(P + "§cНомер варианта: 1, 2 или 3."); return true; }
            if (act.equals("run")) plugin.ai().run(p, idx);
            else if (act.equals("show")) plugin.ai().show(p, idx);
            else if (act.equals("del")) plugin.ai().delete(p, idx);
            else {
                if (args.length < 4) { p.sendMessage(P + "§7/pshow ai fix <номер> <что поправить>"); return true; }
                plugin.ai().fix(p, idx, String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            }
            return true;
        }
        plugin.ai().ask(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)), null);
        return true;
    }

    // ---------------------------------------------------------------- ambient

    private boolean ambient(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lightshow.admin")) { sender.sendMessage(P + "§cНужны права администратора."); return true; }
        String act = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (act.equals("list")) {
            sender.sendMessage(P + "Постоянные шоу §7(" + plugin.manager().ambients().size() + ")");
            for (Manager.Ambient a : plugin.manager().ambients())
                sender.sendMessage("§8 » §f" + a.id + " §7= §b" + a.preset + " §8@ §7" + a.world + " "
                        + (int) a.x + " " + (int) a.y + " " + (int) a.z + " §8" + a.params);
            if (plugin.manager().ambients().isEmpty())
                sender.sendMessage("§8 » §7нет. Добавить: §f/pshow ambient add <id> <пресет> [параметры]");
            return true;
        }
        if (act.equals("add")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Только для игроков."); return true; }
            if (args.length < 4) { sender.sendMessage(P + "§7/pshow ambient add <id> <пресет> [ключ:значение]"); return true; }
            Player p = (Player) sender;
            for (String pn : args[3].split("\\+"))
                if (!plugin.presets().has(pn)) { sender.sendMessage(P + "§cНет пресета " + pn); return true; }
            Params par = new Params();
            par.parse(args, 4);
            if (!par.has("dur")) par.set("dur", "inf");
            if (!par.has("view")) par.set("view", "64");
            Location loc = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(par.num("dist", 6)));
            plugin.manager().addAmbient(args[2], args[3], loc, par);
            plugin.manager().restartAmbients();
            sender.sendMessage(P + "Ambient §d" + args[2] + " §fпоставлен здесь навсегда.");
            return true;
        }
        if (act.equals("del") || act.equals("remove")) {
            if (args.length < 3) { sender.sendMessage(P + "§7/pshow ambient del <id>"); return true; }
            sender.sendMessage(P + (plugin.manager().delAmbient(args[2]) ? "Удалено." : "§cНе найдено."));
            return true;
        }
        sender.sendMessage(P + "§7/pshow ambient <add|del|list>");
        return true;
    }

    // ---------------------------------------------------------------- прочее

    private boolean stop(CommandSender sender, String[] args) {
        String what = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "me";
        if (what.equals("all")) {
            if (!sender.hasPermission("lightshow.admin")) { sender.sendMessage(P + "§cНет прав на stop all."); return true; }
            int n = plugin.manager().stopAll();
            plugin.manager().startAmbients();
            sender.sendMessage(P + "Остановлено §c" + n + "§f шоу.");
            return true;
        }
        try {
            int id = Integer.parseInt(what);
            sender.sendMessage(P + (plugin.manager().stopId(id) ? "Шоу " + id + " остановлено." : "§cНет шоу с id " + id));
            return true;
        } catch (NumberFormatException ignored) {}
        if (!(sender instanceof Player)) { sender.sendMessage(P + "Используй /pstop all"); return true; }
        int n = plugin.manager().stopOf(((Player) sender).getUniqueId());
        sender.sendMessage(P + "Остановлено твоих шоу: §c" + n);
        return true;
    }

    private boolean running(CommandSender s) {
        List<Show> list = plugin.manager().shows();
        s.sendMessage(P + "Идёт шоу: §f" + list.size() + " §7• частиц/тик: §f" + plugin.manager().lastLoad()
                + " §7• транспорт: §f" + Painter.transport() + " §7• API: §f" + plugin.getDescription().getVersion());
        for (Show sh : list) {
            String left = sh.duration() < 0 ? "∞" : fmt(Math.max(0, sh.duration() - sh.elapsed()) / 20.0) + "с";
            s.sendMessage("§8 » §7id §f" + sh.id + " §8· §d" + sh.label + " §8· §7точек §f" + sh.points()
                    + " §8· §7осталось §f" + left);
        }
        return true;
    }

    private boolean reload(CommandSender s) {
        if (!s.hasPermission("lightshow.admin")) { s.sendMessage(P + "§cНет прав."); return true; }
        plugin.reloadAll();
        s.sendMessage(P + "Перезагружено. Пресетов: §a" + plugin.presets().names().size());
        return true;
    }

    private void columns(CommandSender s, String title, List<String> items, String prefix) {
        s.sendMessage("");
        s.sendMessage(P + title + " §7(" + items.size() + ")");
        StringBuilder sb = new StringBuilder("§8 » §7");
        int c = 0;
        for (String i : items) {
            sb.append("§f").append(i).append("§8, ");
            if (++c % 6 == 0) { s.sendMessage(sb.toString()); sb = new StringBuilder("§8 » §7"); }
        }
        if (c % 6 != 0) s.sendMessage(sb.toString());
        s.sendMessage("§8 » §7Использование: §f" + prefix + "<значение>");
        s.sendMessage("");
    }

    private void funcs(CommandSender s) {
        s.sendMessage("");
        s.sendMessage(P + "Что можно писать в формуле");
        s.sendMessage("§8 » §7Переменные: §ft §8— параметр, §fu §8— второй параметр, §fT §8— секунды с начала,");
        s.sendMessage("§8   §fp §8— прогресс 0..1, §fi§8/§fn §8— номер точки и их количество");
        s.sendMessage("§8 » §7Функции: §fsin cos tan asin acos atan atan2 sinh cosh tanh sqrt cbrt abs sign");
        s.sendMessage("§8   §ffloor ceil round frac exp ln log min max pow hypot mod clamp lerp step");
        s.sendMessage("§8   §fsmooth ease saw tri sq pulse noise rand if step4");
        s.sendMessage("§8 » §7Константы: §fpi tau e phi");
        s.sendMessage("§8 » §7Свои переменные: §flet r=2+sin(T); x=r*cos(t); y=r*sin(t)");
        s.sendMessage("§8 » §7Сравнения: §fif(t<pi, 1, -1)");
        s.sendMessage("");
    }

    private void examples(CommandSender s) {
        s.sendMessage("");
        s.sendMessage(P + "Готовые формулы §7(копируй и меняй)");
        s.sendMessage("§8 » §7Роза: §f/pshow new roza x=5*cos(4*t)*cos(t);y=5*cos(4*t)*sin(t)");
        s.sendMessage("§8 » §7Сердце: §f/pshow new serdce x=0.5*16*sin(t)^3;y=0.5*(13*cos(t)-5*cos(2*t)-2*cos(3*t)-cos(4*t))");
        s.sendMessage("§8 » §7Астроида: §f/pshow new astroida x=4*cos(t)^3;y=4*sin(t)^3");
        s.sendMessage("§8 » §7Сфера: §f/pshow new sfera x=5*sin(u)*cos(t);y=5*cos(u);z=5*sin(u)*sin(t) mode:surface u:0..3.14");
        s.sendMessage("§8 » §7Тор: §f/pshow new tor x=(4+1.5*cos(u))*cos(t);y=1.5*sin(u);z=(4+1.5*cos(u))*sin(t) mode:surface u:0..6.28");
        s.sendMessage("§8 » §7Спиральный коридор 3 блока: §f/pshow new koridor x=9*cos(t);y=1.4*t;z=9*sin(t) mode:tube radius:1.5 t:0..25 steps:600 sides:22");
        s.sendMessage("§8 » §7Пульс во времени: §f/pshow new puls let r=4+sin(T*3); x=r*cos(t);y=r*sin(t)");
        s.sendMessage("§8 » §7Луч в игрока: §f/pshow play koltso motion:to_player mspeed:0.3");
        s.sendMessage("");
    }

    private void help(CommandSender s, String page) {
        int pg = 1;
        try { pg = Integer.parseInt(page); } catch (Exception ignored) {}
        s.sendMessage("");
        s.sendMessage("§d✦ §5LightShow §8v" + plugin.getDescription().getVersion() + " §8— страница " + pg + "/3");
        if (pg == 1) {
            s.sendMessage("§8 » §f/pshow play <пресет> [ключ:значение] §8— запустить");
            s.sendMessage("§8 » §f/pshow list §8— все пресеты   §f/pshow info <имя>");
            s.sendMessage("§8 » §f/pshow new <имя> <формула> §8— своя фигура");
            s.sendMessage("§8 » §f/pshow draw <имя> §8— нарисовать в GUI (с кадрами!)");
            s.sendMessage("§8 » §f/ptext <текст> [size:2 font:bold] §8— текст частицами");
            s.sendMessage("§8 » §f/pimage <имя> <ссылка> §8— картинка частицами");
            s.sendMessage("§8 » §f/pshow ai <описание> §8— ИИ соберёт шоу за тебя");
            s.sendMessage("§8 » §f/pstop §8— убрать свои,  §f/pstop all §8— убрать всё");
            s.sendMessage("§8 » §7Дальше: §f/pshow help 2");
        } else if (pg == 2) {
            s.sendMessage("§7Главные параметры (пиши через пробел, ключ:значение):");
            int c = 0;
            for (Map.Entry<String, Params.Meta> e : Params.KEYS.entrySet()) {
                if (c >= 22) break;
                s.sendMessage("§8 » §b" + e.getKey() + ":§f" + (e.getValue().vals.length > 0 ? e.getValue().vals[0] : "")
                        + " §8— §7" + e.getValue().desc);
                c++;
            }
            s.sendMessage("§8 » §7Дальше: §f/pshow help 3");
        } else {
            s.sendMessage("§7Ещё параметры и подсказки:");
            int c = 0;
            for (Map.Entry<String, Params.Meta> e : Params.KEYS.entrySet()) {
                if (c++ < 22) continue;
                s.sendMessage("§8 » §b" + e.getKey() + ":§f" + (e.getValue().vals.length > 0 ? e.getValue().vals[0] : "")
                        + " §8— §7" + e.getValue().desc);
            }
            s.sendMessage("§8 » §f/pshow funcs §8— что можно в формулах");
            s.sendMessage("§8 » §f/pshow examples §8— готовые формулы");
            s.sendMessage("§8 » §f/pshow particles §8| §f/pshow colors §8| §f/pshow fonts");
        }
        s.sendMessage("");
    }

    private static String fmt(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    // ================================================================ табкомплит

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String cur = args.length == 0 ? "" : args[args.length - 1];

        if (name.equals("pstop")) {
            if (args.length == 1) { add(out, cur, "all", "me"); for (Show s : plugin.manager().shows()) out.add(String.valueOf(s.id)); }
            return filter(out, cur);
        }
        if (name.equals("phelp")) { if (args.length == 1) add(out, cur, "1", "2", "3"); return filter(out, cur); }

        if (name.equals("ptext")) {
            if (args.length == 1) { add(out, cur, "Привет", "HELLO", "GG_WP"); return filter(out, cur); }
            return paramComplete(cur);
        }
        if (name.equals("pimage")) {
            if (args.length == 1) return filter(hint("<имя>"), cur);
            if (args.length == 2) return filter(hint("https://…png"), cur);
            return paramComplete(cur);
        }

        if (args.length == 1) {
            add(out, cur, SUBS);
            out.addAll(plugin.presets().names());
            return filter(out, cur);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (sub.equals("play") || sub.equals("info") || sub.equals("del") || sub.equals("set"))
                return filter(new ArrayList<String>(plugin.presets().names()), cur);
            if (sub.equals("list")) return filter(Arrays.asList("all", "math", "draw", "text", "image"), cur);
            if (sub.equals("draw")) return filter(plugin.presets().namesOfType("draw"), cur);
            if (sub.equals("ambient")) return filter(Arrays.asList("add", "del", "list"), cur);
            if (sub.equals("ai")) return filter(Arrays.asList("run", "show", "fix", "del", "<опиши_что_хочешь>"), cur);
            if (sub.equals("stop")) { List<String> l = new ArrayList<String>(Arrays.asList("all", "me")); for (Show s : plugin.manager().shows()) l.add(String.valueOf(s.id)); return filter(l, cur); }
            if (sub.equals("new") || sub.equals("text")) return filter(hint("<имя>"), cur);
            if (sub.equals("help")) return filter(Arrays.asList("1", "2", "3"), cur);
            return out;
        }

        if (sub.equals("ai")) {
            if (args.length == 3) return filter(Arrays.asList("1", "2", "3"), cur);
            return new ArrayList<String>();
        }

        if (sub.equals("ambient")) {
            if (args.length == 3 && args[1].equalsIgnoreCase("add")) return filter(hint("<id>"), cur);
            if (args.length == 3 && (args[1].equalsIgnoreCase("del"))) {
                List<String> ids = new ArrayList<String>();
                for (Manager.Ambient a : plugin.manager().ambients()) ids.add(a.id);
                return filter(ids, cur);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("add")) return filter(new ArrayList<String>(plugin.presets().names()), cur);
            return paramComplete(cur);
        }

        if (sub.equals("new") && args.length == 3) {
            return filter(Arrays.asList("x=4*cos(t)^3;y=4*sin(t)^3",
                    "x=5*cos(4*t)*cos(t);y=5*cos(4*t)*sin(t)",
                    "x=cos(t)*6;y=sin(2*t)*2",
                    "let r=4+sin(T*3);x=r*cos(t);y=r*sin(t)"), cur);
        }

        return paramComplete(cur);
    }

    /** Подсказки ключ:значение — то, ради чего вообще нужен этот формат. */
    private List<String> paramComplete(String cur) {
        List<String> out = new ArrayList<String>();
        int c = cur.indexOf(':');
        if (c < 0) {
            for (Map.Entry<String, Params.Meta> e : Params.KEYS.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith(cur.toLowerCase(Locale.ROOT))) continue;
                String first = e.getValue().vals.length > 0 ? e.getValue().vals[0] : "";
                out.add(k + ":" + first);
            }
            return out;
        }
        String key = cur.substring(0, c).toLowerCase(Locale.ROOT);
        String val = cur.substring(c + 1).toLowerCase(Locale.ROOT);
        List<String> vals = new ArrayList<String>();
        Params.Meta m = Params.KEYS.get(key);
        if (key.equals("particle")) vals.addAll(Painter.typeNames());
        else if (key.equals("color")) { vals.addAll(Painter.colorNames()); vals.addAll(Arrays.asList("rainbow", "rainbow:2", "gradient:#ff00ff-#00ffff", "pulse:aqua-purple", "#00FFAA")); }
        else if (key.equals("font")) vals.addAll(Fonts.names());
        else if (key.equals("world")) { for (org.bukkit.World w : Bukkit.getWorlds()) vals.add(w.getName()); }
        else if (m != null) vals.addAll(Arrays.asList(m.vals));
        for (String v : vals) if (v.toLowerCase(Locale.ROOT).startsWith(val)) out.add(key + ":" + v);
        return out;
    }

    private static List<String> hint(String h) { return new ArrayList<String>(Collections.singletonList(h)); }

    private static void add(List<String> out, String cur, String... items) { out.addAll(Arrays.asList(items)); }

    private static List<String> filter(List<String> src, String cur) {
        List<String> out = new ArrayList<String>();
        String c = cur.toLowerCase(Locale.ROOT);
        for (String s : src) if (s.toLowerCase(Locale.ROOT).startsWith(c)) out.add(s);
        return out;
    }
}
