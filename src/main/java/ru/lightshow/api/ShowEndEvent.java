package ru.lightshow.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Вызывается, когда шоу завершилось или было остановлено. */
public final class ShowEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ShowHandle show;

    public ShowEndEvent(ShowHandle show) { this.show = show; }

    public ShowHandle getShow() { return show; }

    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
