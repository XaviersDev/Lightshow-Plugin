package ru.lightshow.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Сборка шоу. Слои добавляются любым из методов formula/pixels/text/image/frames/preset,
 * каждый возвращает LayerBuilder для настройки именно этого слоя.
 */
public interface ShowBuilder {

    // ---- слои ----
    /** Математический слой: "let r=4+sin(T);x=r*cos(t);y=r*sin(t)". */
    LayerBuilder formula(String formula);
    /** Пиксельная фигура: строки через "/", например "0110/1111". */
    LayerBuilder pixels(String rows);
    LayerBuilder pixels(boolean[][] grid);
    /** Текст встроенным или системным шрифтом. */
    LayerBuilder text(String text);
    /** Цветная картинка: строки "RRGGBB,-,RRGGBB". */
    LayerBuilder image(List<String> rows);
    /** Покадровая анимация из строк "0110". */
    LayerBuilder frames(List<List<String>> frames);
    /** Добавить в это шоу все слои сохранённого пресета. */
    ShowBuilder preset(String name);

    // ---- размещение ----
    ShowBuilder at(Location location);
    /** Перед глазами игрока на заданном расстоянии. */
    ShowBuilder near(Player player, double distance);
    /** Шоу едет за игроком. */
    ShowBuilder attachTo(Player player);
    ShowBuilder face(String face);               // player|north|south|east|west|up|down
    ShowBuilder offset(double x, double y, double z);
    ShowBuilder scale(double size);
    ShowBuilder spin(double x, double y, double z);   // градусов в секунду

    // ---- время ----
    ShowBuilder duration(int ticks);             // -1 = бесконечно
    ShowBuilder loop(boolean loop);

    // ---- зрители и нагрузка ----
    ShowBuilder audience(Audience audience);
    ShowBuilder onlyFor(Player... players);
    ShowBuilder viewDistance(double blocks);
    ShowBuilder cull(double blocks);
    ShowBuilder maxParticlesPerTick(int max);
    ShowBuilder density(double fraction);

    // ---- служебное ----
    ShowBuilder owner(Player player);
    ShowBuilder label(String label);
    ShowBuilder param(String key, Object value);
    ShowBuilder params(String spec);
    ShowBuilder onEnd(Consumer<ShowHandle> callback);

    /** Сколько точек получится — без запуска. */
    int estimatePoints();
    /** Проверить формулы. null если всё в порядке, иначе текст ошибки. */
    String validate();

    /** Запустить. null, если ShowStartEvent отменили. */
    ShowHandle start();
    /** Сохранить как пресет, чтобы потом звать по имени или из команды. */
    void saveAs(String presetName);
}
