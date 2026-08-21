package ru.lightshow.api;

import org.bukkit.Particle;

/**
 * Настройка одного слоя. Все методы возвращают этот же объект, кроме and().
 * Любой параметр из документации можно задать напрямую через param("ключ", значение).
 */
public interface LayerBuilder {

    // ---- чем рисуем ----
    LayerBuilder particle(Particle particle);
    LayerBuilder particle(String alias);
    /** Спецификация цвета: "aqua", "#00FFAA", "rainbow", "gradient:#a-#b", "pulse:#a-#b". */
    LayerBuilder color(String spec);
    LayerBuilder color(int rgb);
    LayerBuilder particleSize(double psize);

    // ---- геометрия ----
    LayerBuilder mode(String mode);              // curve | tube | surface | fill
    LayerBuilder steps(int steps);
    LayerBuilder usteps(int usteps);
    LayerBuilder sides(int sides);
    LayerBuilder radius(double radius);
    LayerBuilder range(double from, double to);  // диапазон t
    LayerBuilder urange(double from, double to);
    LayerBuilder pixelSize(double blocks);       // размер пикселя для pix/текста/картинки

    // ---- таймлайн ----
    LayerBuilder from(int ticks);
    LayerBuilder to(int ticks);
    LayerBuilder every(int periodTicks, int windowTicks);
    LayerBuilder in(String animation, int ticks);
    LayerBuilder out(String animation, int ticks);

    // ---- трансформ слоя (строки — это формулы от T) ----
    LayerBuilder offset(double x, double y, double z);
    LayerBuilder offset(String x, String y, String z);
    LayerBuilder zoom(double zoom);
    LayerBuilder zoom(String formula);
    LayerBuilder rotation(String rx, String ry, String rz);

    // ---- движение частиц ----
    LayerBuilder motion(String motion);          // out|in|up|down|flow|spin|to_player|...
    LayerBuilder speed(double mspeed);
    LayerBuilder velocity(String vx, String vy, String vz);
    LayerBuilder trail(int count, double gap);
    LayerBuilder jitter(double blocks);
    LayerBuilder chance(double probability);
    LayerBuilder batch(int count, double spread); // несколько частиц одним пакетом
    LayerBuilder lift(double lift);

    // ---- прочее ----
    LayerBuilder refresh(int ticks);
    LayerBuilder sound(String sound, float volume, float pitch);
    LayerBuilder font(String font);
    LayerBuilder align(String align);
    LayerBuilder spacing(int pixels);
    LayerBuilder lineGap(int pixels);
    LayerBuilder outline(boolean outline);
    LayerBuilder fps(int ticksPerFrame);
    LayerBuilder pingpong(boolean pingpong);

    LayerBuilder param(String key, Object value);
    /** Строка вида "steps:200 color:aqua refresh:8". */
    LayerBuilder params(String spec);

    /** Оценка числа точек этого слоя без запуска. */
    int estimatePoints();

    /** Вернуться к настройке шоу. */
    ShowBuilder and();
}
