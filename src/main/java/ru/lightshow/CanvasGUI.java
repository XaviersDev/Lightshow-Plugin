package ru.lightshow;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Холст 9x4 (36 слотов) + нижняя строка настроек.
 * Можно рисовать НЕСКОЛЬКО КАДРОВ — получится покадровая анимация.
 * Рисовать можно зажав ЛКМ и протянув мышь (drag), а не тыкать в каждый пиксель.
 */
public final class CanvasGUI implements Listener {

    public static final String TITLE = "§d✦ §8Холст §7— §f";
    private static final int W = 9, H = 4, CANVAS = W * H;

    private static final String[] PARTICLES = { "end_rod", "dust", "flame", "soul_fire", "soul", "spark", "enchant", "totem", "happy", "crit" };
    private static final String[] COLORS = { "white", "aqua", "cyan", "purple", "magenta", "pink", "red", "orange", "gold", "lime", "green", "blue", "rainbow", "gradient:#ff00ff-#00ffff" };
    private static final String[] PX = { "0.15", "0.2", "0.3", "0.4", "0.5", "0.75", "1.0" };
    private static final String[] MOTION = { "none", "out", "in", "up", "down", "to_player", "from_player", "flow", "spin", "random" };
    private static final String[] FPS = { "1", "2", "3", "4", "6", "10", "20" };

    public static final class Session {
        public String name;
        public final List<boolean[]> frames = new ArrayList<boolean[]>();
        public int cur = 0;
        public int particle = 0, color = 0, px = 3, motion = 0, fps = 3;
        Session(String n) { name = n; frames.add(new boolean[CANVAS]); }
    }

    private final LightShow plugin;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();

    public CanvasGUI(LightShow plugin) { this.plugin = plugin; }

    public void open(Player p, String name) {
        Presets.Preset old = plugin.presets().get(name);
        Session s = new Session(name);
        if (old != null && old.type.equals("draw") && !old.frames.isEmpty()) {
            s.frames.clear();
            for (List<String> rows : old.frames) {
                boolean[] f = new boolean[CANVAS];
                for (int y = 0; y < Math.min(H, rows.size()); y++) {
                    String r = rows.get(y);
                    for (int x = 0; x < Math.min(W, r.length()); x++) if (r.charAt(x) == '1') f[y * W + x] = true;
                }
                s.frames.add(f);
            }
            Params d = old.defaults;
            s.particle = idx(PARTICLES, d.lower("particle", "end_rod"));
            s.color = idx(COLORS, d.str("color", "white"));
            s.px = idx(PX, d.str("px", "0.4"));
            s.motion = idx(MOTION, d.lower("motion", "none"));
            s.fps = idx(FPS, d.str("fps", "4"));
        }
        sessions.put(p.getUniqueId(), s);
        Inventory inv = Bukkit.createInventory(null, 45, TITLE + name);
        render(inv, s);
        p.openInventory(inv);
    }

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equalsIgnoreCase(v)) return i;
        return 0;
    }

    private void render(Inventory inv, Session s) {
        boolean[] f = s.frames.get(s.cur);
        for (int i = 0; i < CANVAS; i++) inv.setItem(i, pixel(f[i]));
        inv.setItem(36, item(Material.ARROW, "§e◀ Предыдущий кадр", "§7Кадр §f" + (s.cur + 1) + "§7/§f" + s.frames.size()));
        inv.setItem(37, item(Material.ARROW, "§e▶ Следующий кадр", "§7Если это последний — §fсоздаст новый"));
        inv.setItem(38, item(Material.PAPER, "§bКадр " + (s.cur + 1) + " из " + s.frames.size(),
                "§8» §7ЛКМ — §fдублировать кадр", "§8» §7ПКМ — §fудалить кадр", "§8» §7Shift+ЛКМ — §fочистить"));
        inv.setItem(39, item(Material.BLAZE_ROD, "§6Частица: §f" + PARTICLES[s.particle], "§7ЛКМ/ПКМ — листать"));
        inv.setItem(40, item(Material.LIME_DYE, "§aЦвет: §f" + COLORS[s.color], "§7Работает на dust / spell_color", "§7ЛКМ/ПКМ — листать"));
        inv.setItem(41, item(Material.STICK, "§dРазмер пикселя: §f" + PX[s.px] + " бл.", "§7Ширина рисунка: §f" + fmt(9 * Double.parseDouble(PX[s.px])) + " блоков"));
        inv.setItem(42, item(Material.FEATHER, "§bПолёт частиц: §f" + MOTION[s.motion], "§7Куда летят частицы (end_rod)"));
        inv.setItem(43, item(Material.ENDER_EYE, "§5Предпросмотр", "§7Показать 6 секунд", "§7Скорость кадров: §f" + FPS[s.fps] + " тик/кадр", "§8» §7Shift — сменить скорость"));
        inv.setItem(44, item(Material.GREEN_CONCRETE, "§a✔ Сохранить", "§7Сохранит §f" + s.frames.size() + " §7кадр(ов)", "§8» §7Запуск: §f/pshow play " + s.name));
    }

    private static String fmt(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    private static ItemStack pixel(boolean on) {
        ItemStack it = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (on) {
            m.setDisplayName("§f✦");
            m.addEnchant(Enchantment.DURABILITY, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        } else {
            m.setDisplayName("§8");
        }
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore.length > 0) m.setLore(Arrays.asList(lore));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    // --------------------------------------------------------------- события

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getView().getTitle();
        if (!title.startsWith(TITLE)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) { p.closeInventory(); return; }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 45) return;
        Inventory inv = e.getView().getTopInventory();
        boolean shift = e.isShiftClick(), right = e.isRightClick();

        if (slot < CANVAS) {
            boolean[] f = s.frames.get(s.cur);
            f[slot] = !right;
            inv.setItem(slot, pixel(f[slot]));
            return;
        }
        switch (slot) {
            case 36: s.cur = (s.cur - 1 + s.frames.size()) % s.frames.size(); break;
            case 37:
                if (s.cur == s.frames.size() - 1) {
                    if (s.frames.size() >= 64) { p.sendMessage(LightShow.PX + "§cМаксимум 64 кадра."); return; }
                    s.frames.add(new boolean[CANVAS]);
                }
                s.cur++;
                break;
            case 38:
                if (shift) { s.frames.set(s.cur, new boolean[CANVAS]); }
                else if (right) {
                    if (s.frames.size() > 1) { s.frames.remove(s.cur); if (s.cur >= s.frames.size()) s.cur = s.frames.size() - 1; }
                } else {
                    if (s.frames.size() < 64) { s.frames.add(s.cur + 1, s.frames.get(s.cur).clone()); s.cur++; }
                }
                break;
            case 39: s.particle = cycle(s.particle, PARTICLES.length, right); break;
            case 40: s.color = cycle(s.color, COLORS.length, right); break;
            case 41: s.px = cycle(s.px, PX.length, right); break;
            case 42: s.motion = cycle(s.motion, MOTION.length, right); break;
            case 43:
                if (shift) { s.fps = cycle(s.fps, FPS.length, right); break; }
                preview(p, s);
                return;
            case 44:
                savePreset(p, s);
                p.closeInventory();
                return;
        }
        render(inv, s);
    }

    private static int cycle(int v, int n, boolean back) { return back ? (v - 1 + n) % n : (v + 1) % n; }

    /** Рисование протягиванием мыши. */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!e.getView().getTitle().startsWith(TITLE)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        boolean[] f = s.frames.get(s.cur);
        boolean erase = e.getType() == DragType.SINGLE && p.isSneaking();
        for (int slot : e.getRawSlots()) {
            if (slot < CANVAS) {
                f[slot] = !erase;
                e.getView().getTopInventory().setItem(slot, pixel(f[slot]));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().startsWith(TITLE)) return;
        // сессию не удаляем сразу: игрок мог закрыть случайно, вернём при повторном /pshow draw
    }

    private Params settingsOf(Session s) {
        Params pr = new Params();
        pr.set("particle", PARTICLES[s.particle]);
        pr.set("color", COLORS[s.color]);
        pr.set("px", PX[s.px]);
        pr.set("motion", MOTION[s.motion]);
        pr.set("fps", FPS[s.fps]);
        if (!MOTION[s.motion].equals("none")) pr.set("mspeed", "0.08");
        return pr;
    }

    private void preview(Player p, Session s) {
        Presets.Preset tmp = toPreset(s);
        Params pr = settingsOf(s);
        pr.set("dur", "6s"); pr.set("dist", "6"); pr.set("in", "fade"); pr.set("int", "10t");
        p.closeInventory();
        try {
            plugin.manager().start(p, tmp, pr, "preview:" + s.name);
            p.sendMessage(LightShow.PX + "Предпросмотр §d" + s.name + "§f. Открыть заново: §7/pshow draw " + s.name);
        } catch (Exception ex) {
            p.sendMessage(LightShow.PX + "§cОшибка предпросмотра: " + ex.getMessage());
        }
    }

    private Presets.Preset toPreset(Session s) {
        Presets.Preset pr = new Presets.Preset();
        pr.name = s.name;
        pr.type = "draw";
        for (boolean[] f : s.frames) {
            List<String> rows = new ArrayList<String>();
            for (int y = 0; y < H; y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < W; x++) sb.append(f[y * W + x] ? '1' : '0');
                rows.add(sb.toString());
            }
            pr.frames.add(rows);
        }
        pr.defaults = settingsOf(s);
        return pr;
    }

    private void savePreset(Player p, Session s) {
        Presets.Preset pr = toPreset(s);
        pr.author = p.getName();
        plugin.presets().put(pr);
        plugin.presets().save();
        p.sendMessage("");
        p.sendMessage(LightShow.PX + "Рисунок §d" + s.name + " §fсохранён (§a" + s.frames.size() + " §fкадр(ов)).");
        p.sendMessage("§8» §7Запуск: §f/pshow play " + s.name + " dist:6 dur:30s");
        p.sendMessage("§8» §7Крутится: §f/pshow play " + s.name + " spin:45 dur:inf");
        p.sendMessage("");
    }
}
