package ru.lightshow.api;

import org.bukkit.Location;

import java.util.UUID;
import java.util.function.Consumer;

/** Ручка запущенного шоу: управляй им, пока оно идёт. */
public interface ShowHandle {

    int id();
    String label();
    UUID owner();

    boolean isAlive();
    void stop();

    boolean isPaused();
    /** Пауза замораживает время шоу: анимации и таймлайн стоят на месте. */
    void setPaused(boolean paused);

    Location location();
    /** Перенести шоу в другую точку прямо во время показа. */
    void move(Location location);

    /** Точек в текущем кадре. */
    int points();
    /** Сколько частиц реально отправлено в прошлый тик. */
    long lastParticleCount();

    int elapsedTicks();
    int durationTicks();
    /** -1 = бесконечно. Можно продлить или укоротить на ходу. */
    void setDurationTicks(int ticks);

    Audience audience();
    /** Сменить зрителей на лету. */
    void setAudience(Audience audience);

    /** Вызовется в главном потоке, когда шоу закончится или будет остановлено. */
    void onEnd(Consumer<ShowHandle> callback);
}
