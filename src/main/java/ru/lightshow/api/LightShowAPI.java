package ru.lightshow.api;

import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Точка входа для сторонних плагинов.
 *
 * <pre>{@code
 * LightShowAPI api = LightShowProvider.get();
 * ShowHandle h = api.show()
 *         .formula("x=4*cos(t);y=4*sin(t)").particle("end_rod").refresh(15).and()
 *         .near(player, 6).duration(20 * 20).onlyFor(player)
 *         .start();
 * }</pre>
 *
 * Все методы вызываются из главного потока сервера.
 */
public interface LightShowAPI {

    /** Пустое шоу, к которому добавляются слои. */
    ShowBuilder show();

    /** Шоу из сохранённого пресета, включая комбинации "a+b+c". */
    ShowBuilder fromPreset(String presetName);

    /** Короткий путь: пресет + строка параметров + место + зрители. */
    ShowHandle play(String presetName, String params, Location at, Audience audience);

    List<ShowHandle> active();
    ShowHandle byId(int id);
    int stopAll();
    int stopOwnedBy(UUID owner);

    PresetRegistry presets();
    FunctionRegistry functions();
    TextRenderer text();

    /** Свой псевдоним частицы: alias можно писать в particle: и в командах. */
    void registerParticleAlias(String alias, Particle particle);
    Set<String> particleAliases();
    Set<String> colorNames();

    /** Как пакеты уходят игрокам. */
    Transport transport();
    /** Общий лимит частиц на сервер за тик. */
    int particleBudget();
    void setParticleBudget(int perTick);
    /** Сколько частиц ушло в прошлый тик суммарно. */
    long currentLoad();

    String version();

    enum Transport { NMS, PROTOCOLLIB, BUKKIT }

    /** Управление сохранёнными пресетами. */
    interface PresetRegistry {
        Set<String> names();
        List<String> namesOfType(String type);   // math | draw | text | image
        boolean has(String name);
        String describe(String name);
        void delete(String name);
        /** Записать presets.yml на диск. */
        void save();
        /** Открыть пресет как билдер, чтобы дополнить или изменить. */
        ShowBuilder open(String name);
    }

    /** Свои функции для языка формул. Доступны сразу во всех формулах и командах. */
    interface FunctionRegistry {
        /** arity < 0 — любое количество аргументов. */
        void register(String name, int arity, Function fn);
        void unregister(String name);
        boolean has(String name);
        Set<String> custom();

        interface Function { double apply(double[] args); }
    }

    /** Рендер текста в пиксели — полезно и вне частиц. */
    interface TextRenderer {
        PixelArt render(String text, String font, String align, int spacing, int lineGap);
        List<String> fonts();
    }

    /** Монохромная картинка. */
    interface PixelArt {
        int width();
        int height();
        boolean get(int x, int y);
        int count();
        PixelArt outline();
        /** Строки вида "0110" — их принимает ShowBuilder.pixels(). */
        List<String> rows();
    }
}
