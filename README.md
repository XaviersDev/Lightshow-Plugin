<div align="center">

# LightShow

**Particle show engine for Minecraft** — formulas, scenes, text, emitters, a drawing GUI, an AI generator and a full Java API.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5%2B-brightgreen)](https://www.spigotmc.org/)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://adoptium.net/)
[![Release](https://img.shields.io/github/v/release/XaviersDev/Lightshow-Plugin?label=download)](https://github.com/XaviersDev/Lightshow-Plugin/releases)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[![](https://jitpack.io/v/XaviersDev/Lightshow-Plugin.svg)](https://jitpack.io/#XaviersDev/Lightshow-Plugin)

[English](#english) · [Русский](#русский)

</div>

---

## English

### Install

Drop the jar into `plugins/` and restart. ProtocolLib is optional — it is picked up automatically if present.

### Try it

```
/pshow list                          everything that ships with the plugin
/pshow play demon_eye                a blinking eye whose pupil looks around
/ptext HELLO in:fly int:30t          letters fly in from the distance
/pshow draw mylogo                   frame-by-frame pixel drawing GUI
/pshow ai a moon with meteors        the AI writes the commands for you
```

Build your own from a formula:

```
/pshow new rose x=5*cos(4*t)*cos(t);y=5*cos(4*t)*sin(t) steps:400 dur:20s
```

### Features

* Math formulas with `let` variables, 45+ functions and a time variable `T` for animation
* Curves, tubes, surfaces, fills, pixel shapes, text, images and frame animations
* **Scenes** — every layer has its own timeline, entrance, exit, movement and scaling
* **Emitters** — meteors, rain and sparks with real velocity, trails, jitter and randomness
* Text in built-in bitmap fonts (Latin + Cyrillic) or any system font
* Permanent ambient shows that survive restarts
* Built-in AI generator that writes commands from a plain description and validates them first
* An honest performance model with per-layer and server-wide safety limits

### Documentation

| | |
|---|---|
| **[Command & DSL reference](LIGHTSHOW-LLM-REFERENCE.md)** | Complete specification of the language. Written for LLMs — feed it to an MCP server or paste it into a system prompt and the model will write correct shows. |
| **[Java API reference](LIGHTSHOW-API.md)** | For plugin developers: builders, per-player audiences, runtime control, events, custom formula functions. |
| **[Полное руководство (RU)](GUIDE-RU.md)** | Detailed Russian guide: parameters, performance, recipes. |

Raw link for MCP / prompt loading:

```
https://raw.githubusercontent.com/XaviersDev/Lightshow-Plugin/main/LIGHTSHOW-LLM-REFERENCE.md
```

### Use it from your plugin

```java
LightShowAPI lights = LightShowProvider.require();

lights.show()
        .formula("x=4*cos(t);y=4*sin(t)").particle("end_rod").refresh(15).and()
        .near(player, 6)
        .duration(20 * 10)
        .onlyFor(player)          // visible to this player only
        .start();
```

```yaml
depend: [LightShow]
```

### Build

```
mvn clean package        # → target/LightShow-2.1.0.jar
```

---

## Русский

### Установка

Кинь jar в `plugins/` и перезапусти сервер. ProtocolLib не обязателен — подхватится сам, если стоит.

### Попробовать

```
/pshow list                          всё, что идёт в комплекте
/pshow play demon_eye                моргающий глаз, зрачок смотрит по сторонам
/ptext ПРИВЕТ in:fly int:30t         буквы прилетают издалека
/pshow draw mylogo                   покадровая рисовалка в GUI
/pshow ai луна и метеориты           ИИ сам напишет команды
```

Своя фигура одной командой:

```
/pshow new roza x=5*cos(4*t)*cos(t);y=5*cos(4*t)*sin(t) steps:400 dur:20s
```

### Возможности

* Язык формул с переменными `let`, 45+ функциями и временем `T` для анимации
* Кривые, трубы, поверхности, заливки, пиксельные фигуры, текст, картинки и покадровые анимации
* **Сцены** — у каждого слоя своё время жизни, вход, выход, движение и масштаб
* **Эмиттеры** — метеоры, дождь и искры с настоящей скоростью, хвостами и разбросом
* Текст встроенными растровыми шрифтами (латиница + кириллица) или любым системным
* Постоянные ambient-шоу, которые переживают рестарт
* Встроенный ИИ-генератор: пишет команды по описанию и проверяет их до запуска
* Честная модель нагрузки и предохранители — на слой и на весь сервер

### Документация

| | |
|---|---|
| **[Команды и язык](LIGHTSHOW-LLM-REFERENCE.md)** | Полная спецификация. Написана под нейросети — залей в MCP-сервер или вставь в системный промпт, и модель начнёт писать рабочие шоу. |
| **[Java API](LIGHTSHOW-API.md)** | Для разработчиков плагинов: билдеры, показ отдельным игрокам, управление на лету, события, свои функции для формул. |
| **[Полное руководство](GUIDE-RU.md)** | Подробно: все параметры, производительность, готовые рецепты. |

Прямая ссылка для MCP и промптов:

```
https://raw.githubusercontent.com/XaviersDev/Lightshow-Plugin/main/LIGHTSHOW-LLM-REFERENCE.md
```

### Использовать в своём плагине

```java
LightShowAPI lights = LightShowProvider.require();

lights.show()
        .formula("x=4*cos(t);y=4*sin(t)").particle("end_rod").refresh(15).and()
        .near(player, 6)
        .duration(20 * 10)
        .onlyFor(player)          // увидит только этот игрок
        .start();
```

```yaml
depend: [LightShow]
```

### Сборка

```
mvn clean package        # → target/LightShow-2.1.0.jar
```

---

<div align="center">
MIT · <a href="https://github.com/XaviersDev/Lightshow-Plugin/issues">Issues</a> · <a href="https://github.com/XaviersDev/Lightshow-Plugin/releases">Releases</a>
</div>
