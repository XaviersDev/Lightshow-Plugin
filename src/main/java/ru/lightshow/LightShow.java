package ru.lightshow;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.lightshow.api.LightShowAPI;
import ru.lightshow.impl.ApiImpl;

public final class LightShow extends JavaPlugin {

    public static final String PX = "§d✦ §5LightShow §8» §f";

    private Presets presets;
    private Manager manager;
    private CanvasGUI gui;
    private AI ai;
    private LightShowAPI api;

    @Override
    public void onLoad() {
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Fonts.load(getLogger(), getClassLoader());
        Painter.init(getConfig().getString("transport", "auto"));

        presets = new Presets(this);
        presets.load();

        manager = new Manager(this);
        manager.setBudget(getConfig().getInt("max-particles-per-tick", 8000));
        manager.loadAmbients();

        api = new ApiImpl(this);
        Bukkit.getServicesManager().register(LightShowAPI.class, api, this, ServicePriority.Normal);

        ai = new AI(this);
        gui = new CanvasGUI(this);
        Bukkit.getPluginManager().registerEvents(gui, this);

        Commands cmd = new Commands(this);
        for (String c : new String[] { "pshow", "ptext", "pimage", "pstop", "phelp" }) {
            PluginCommand pc = getCommand(c);
            if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> manager.tickAll(), 1L, 1L);

        int amb = manager.startAmbients();
        getLogger().info("Загружено пресетов: " + presets.names().size()
                + ", постоянных шоу: " + amb
                + ", транспорт частиц: " + Painter.transport());
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.stopAll();
        Bukkit.getServicesManager().unregisterAll(this);
    }

    public void reloadAll() {
        reloadConfig();
        manager.stopAll();
        presets.load();
        manager.setBudget(getConfig().getInt("max-particles-per-tick", 8000));
        manager.loadAmbients();
        manager.startAmbients();
    }

    public Presets presets() { return presets; }
    public Manager manager() { return manager; }
    public CanvasGUI gui() { return gui; }
    public AI ai() { return ai; }
    public LightShowAPI api() { return api; }
}
