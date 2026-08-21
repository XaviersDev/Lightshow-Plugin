package ru.lightshow.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Достать API. Плагин регистрирует себя в ServicesManager при включении. */
public final class LightShowProvider {

    private LightShowProvider() {}

    private static LightShowAPI cached;

    /** null, если LightShow не установлен или ещё не включился. */
    public static LightShowAPI get() {
        if (cached != null) return cached;
        RegisteredServiceProvider<LightShowAPI> rsp =
                Bukkit.getServicesManager().getRegistration(LightShowAPI.class);
        return cached = (rsp == null ? null : rsp.getProvider());
    }

    /** Бросает исключение, если плагина нет — удобно в onEnable при depend. */
    public static LightShowAPI require() {
        LightShowAPI api = get();
        if (api == null) throw new IllegalStateException("LightShow не найден: добавь depend: [LightShow] в plugin.yml");
        return api;
    }

    public static boolean isAvailable() { return get() != null; }
}
