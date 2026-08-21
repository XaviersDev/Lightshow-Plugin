package ru.lightshow.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Вызывается до запуска шоу. Можно отменить. */
public final class ShowStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ShowHandle show;
    private boolean cancelled;

    public ShowStartEvent(ShowHandle show) { this.show = show; }

    public ShowHandle getShow() { return show; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
