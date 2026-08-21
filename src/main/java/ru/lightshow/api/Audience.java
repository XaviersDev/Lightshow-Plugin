package ru.lightshow.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Predicate;

/**
 * Кто именно получает пакеты частиц.
 * Плагин шлёт частицы адресно каждому игроку, поэтому шоу может быть видно
 * буквально одному человеку и невидимо всем остальным.
 */
public interface Audience {

    List<Player> viewers(Location center, double viewRadius);

    /** Все игроки мира в радиусе view. Поведение по умолчанию. */
    static Audience everyone() {
        return (center, r) -> {
            List<Player> out = new ArrayList<Player>();
            if (center.getWorld() == null) return out;
            double r2 = r * r;
            for (Player p : center.getWorld().getPlayers())
                if (p.getLocation().distanceSquared(center) <= r2) out.add(p);
            return out;
        };
    }

    /** Только эти игроки, независимо от расстояния. */
    static Audience of(Player... players) {
        final List<UUID> ids = new ArrayList<UUID>();
        for (Player p : players) if (p != null) ids.add(p.getUniqueId());
        return fromIds(ids);
    }

    static Audience of(Collection<? extends Player> players) {
        final List<UUID> ids = new ArrayList<UUID>();
        for (Player p : players) if (p != null) ids.add(p.getUniqueId());
        return fromIds(ids);
    }

    static Audience fromIds(Collection<UUID> ids) {
        final List<UUID> copy = new ArrayList<UUID>(ids);
        return (center, r) -> {
            List<Player> out = new ArrayList<Player>();
            for (UUID id : copy) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) out.add(p);
            }
            return out;
        };
    }

    /** Все в своём радиусе, независимо от view: шоу. */
    static Audience within(double radius) {
        final double r2 = radius * radius;
        return (center, ignored) -> {
            List<Player> out = new ArrayList<Player>();
            if (center.getWorld() == null) return out;
            for (Player p : center.getWorld().getPlayers())
                if (p.getLocation().distanceSquared(center) <= r2) out.add(p);
            return out;
        };
    }

    /** Произвольное условие: права, команда, режим игры, регион и т.д. */
    static Audience filter(Predicate<Player> test) {
        return (center, r) -> {
            List<Player> out = new ArrayList<Player>();
            if (center.getWorld() == null) return out;
            double r2 = r * r;
            for (Player p : center.getWorld().getPlayers())
                if (p.getLocation().distanceSquared(center) <= r2 && test.test(p)) out.add(p);
            return out;
        };
    }

    static Audience permission(String node) { return filter(p -> p.hasPermission(node)); }

    /** Объединение двух аудиторий без дублей. */
    default Audience plus(Audience other) {
        return (center, r) -> {
            LinkedHashSet<Player> set = new LinkedHashSet<Player>(viewers(center, r));
            set.addAll(other.viewers(center, r));
            return new ArrayList<Player>(set);
        };
    }

    /** Исключить игроков по условию. */
    default Audience without(Predicate<Player> test) {
        return (center, r) -> {
            List<Player> out = new ArrayList<Player>();
            for (Player p : viewers(center, r)) if (!test.test(p)) out.add(p);
            return out;
        };
    }
}
