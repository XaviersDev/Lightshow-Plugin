# LightShow API — reference for plugin developers

LightShow is a particle-show engine for Spigot/Paper **1.16.5+**. It ships as a normal plugin with
commands, and at the same time exposes a full Java API so other plugins can build, run and control
shows programmatically. Everything the commands can do, the API can do.

* Package: `ru.lightshow.api`
* Entry point: `LightShowProvider.get()`
* Registered as a Bukkit **service** (`Bukkit.getServicesManager()`), so it works with any loader order.
* The string DSL used inside formulas and parameters is documented separately in
  `LIGHTSHOW-LLM-REFERENCE.md`. The API accepts exactly the same keys and formulas.

---

## 1. Depending on LightShow

`plugin.yml` of your plugin:

```yaml
depend: [LightShow]        # or softdepend if the integration is optional
```

Maven (install the jar into your local repo, or use jitpack):

```xml
<dependency>
    <groupId>ru.lightshow</groupId>
    <artifactId>LightShow</artifactId>
    <version>2.2.0</version>
    <scope>provided</scope>
</dependency>
```

Gradle:

```groovy
compileOnly 'ru.lightshow:LightShow:2.2.0'
```

**Never shade LightShow into your jar.** It must stay a single runtime instance, otherwise you get
two engines, two schedulers and two service registrations.

---

## 2. Getting the API

```java
import ru.lightshow.api.*;

@Override
public void onEnable() {
    LightShowAPI lights = LightShowProvider.require();   // throws if LightShow is missing
    // or:
    if (LightShowProvider.isAvailable()) { ... }         // for soft integration
}
```

`LightShowProvider.get()` returns `null` when the plugin is absent or not yet enabled.
The result is cached, so calling it per event is cheap.

**Threading:** every API call must happen on the main server thread. Building shows off-thread is
not supported; do your own async work first, then hop back with `runTask`.

---

## 3. Mental model in 60 seconds

A **show** is a set of **layers** rendered at one anchor point.
A layer turns into a cloud of **points**; every point is sent to a client as **one particle packet**.
A particle is fire-and-forget: once sent, the server cannot move, recolor or delete it — it lives
about 60 ticks and disappears.

```
live particles per viewer = points × 60 ÷ refresh
packets per tick          = points ÷ refresh
```

Two different kinds of motion:

| you want | you use |
|---|---|
| the **shape** to move / scale / rotate | animate the layer: `.offset("...")`, `.zoom("...")`, `.rotation(...)` (formulas of `T`), or put `T` in the formula |
| **individual particles** to fly | `.velocity(vx, vy, vz)` or `.motion(...).speed(...)` |

Refresh defaults are chosen automatically: static layer → 12, animated or moving layer → 3.
Override only if you know why.

---

## 4. Quick start

```java
LightShowAPI lights = LightShowProvider.require();

// a ring of light in front of the player for 10 seconds, visible only to him
ShowHandle handle = lights.show()
        .formula("x=4*cos(t);y=4*sin(t)")
            .particle("end_rod")
            .steps(160)
            .refresh(15)
            .and()
        .near(player, 6)
        .duration(20 * 10)
        .onlyFor(player)
        .start();
```

Run a preset that already exists (including combinations):

```java
lights.play("demon_eye", "dist:26 dur:inf", null, Audience.within(64));
```

Save what you built so admins can reuse it from commands:

```java
lights.show().formula("x=5*cos(4*t)*cos(t);y=5*cos(4*t)*sin(t)").steps(400).and()
      .params("dur:20s dist:8")
      .saveAs("my_rose");
// now available as: /pshow play my_rose
```

---

## 5. `ShowBuilder`

Every method returns the builder, so calls chain. Layer methods return a `LayerBuilder`; call
`.and()` to come back.

### 5.1 Adding layers

| method | layer kind |
|---|---|
| `formula(String)` | math formula, e.g. `"let r=4+sin(T*3);x=r*cos(t);y=r*sin(t)"` |
| `pixels(String rows)` | pixel shape, `"0110/1111"` (the `pix:` prefix is optional) |
| `pixels(boolean[][] grid)` | the same from a grid |
| `text(String)` | rendered text, built-in or system font |
| `image(List<String> rows)` | coloured picture, rows of `"RRGGBB,-,RRGGBB"` |
| `frames(List<List<String>> frames)` | frame-by-frame pixel animation |
| `preset(String name)` | pull in every layer of a saved preset (returns `ShowBuilder`, not a layer) |

A show may mix all of them freely.

### 5.2 Placement

| method | meaning |
|---|---|
| `at(Location)` | exact world position |
| `near(Player, double distance)` | in front of the player's eyes |
| `attachTo(Player)` | the show follows the player (`anchor:player`) |
| `face(String)` | orientation: `player north south east west up down` |
| `offset(x, y, z)` | static shift |
| `scale(double)` | global size multiplier |
| `spin(x, y, z)` | degrees per second around each axis |

If neither `at()` nor `near()` is given, the builder falls back to the `dist:` parameter and the
owner player; with no owner at all you must call `at()`.

### 5.3 Time, audience, budget

| method | meaning |
|---|---|
| `duration(int ticks)` | `-1` for infinite |
| `loop(boolean)` | restart when the duration ends |
| `audience(Audience)` | see §6 |
| `onlyFor(Player...)` | shortcut for `Audience.of(...)` |
| `viewDistance(double)` | radius in which players receive the show at all |
| `cull(double)` | drop points further than this from every viewer (long structures!) |
| `maxParticlesPerTick(int)` | hard cap for this show |
| `density(double)` | fraction of points actually drawn |

### 5.4 Misc

| method | meaning |
|---|---|
| `owner(Player)` | who "owns" it — used by `/pstop`, `anchor:player`, `motion:to_player` |
| `label(String)` | shows up in `/pshow running` |
| `param(String key, Object value)` | any parameter from the DSL, escape hatch |
| `params(String spec)` | `"dur:20s dist:8 color:aqua"` |
| `onEnd(Consumer<ShowHandle>)` | main-thread callback when it finishes |
| `estimatePoints()` | dry run: how many points will be produced |
| `validate()` | compile all formulas, returns `null` or an error message |
| `start()` | returns `ShowHandle`, or `null` if a `ShowStartEvent` listener cancelled it |
| `saveAs(String)` | persist as a preset in `presets.yml` |

---

## 6. `LayerBuilder`

### 6.1 Appearance

```java
.particle(Particle.END_ROD)   .particle("end_rod")
.color("#00FFAA")             .color(0x00FFAA)     .color("rainbow")
.particleSize(1.2)
```

`color` only affects `dust`, `spell_color` and `note`. Colour specs: named (`aqua`, `gold`, …),
`#RRGGBB`, `rainbow`, `rainbow:2`, `gradient:#a-#b`, `pulse:#a-#b`.

### 6.2 Geometry

```java
.mode("tube").radius(1.5).sides(10)     // curve | tube | surface | fill
.steps(200).usteps(16)
.range(0, 25).urange(0, Math.PI)
.pixelSize(0.6)                          // block size of one pixel for pix/text/image
```

### 6.3 Timeline (scenes)

```java
.from(60).to(200)                        // ticks, relative to show start
.every(40, 12)                           // repeat: fire for 12 ticks every 40
.in("fly", 25).out("fade", 15)
```

`in` values: `fly fade type wipe rise drop explode scale spiral none`
`out` values: `fly fade scatter fall dissolve wipe implode shrink none`

### 6.4 Layer transform — strings are formulas of `T` (seconds)

```java
.offset(-5, 0, 0)                                   // constant
.offset("-5-lerp(0,4,smooth((T-3)/1))", null, null) // waits, then slides at second 3
.zoom("lerp(0.25,1.9,smooth((T-6)/1.5))")
.rotation(null, null, "T*60")                       // spin in the screen plane
```

The standard ramp is `smooth((T-A)/B)`: 0 before second A, rises over B seconds, then stays 1.
Because it clamps, a movement stops and **stays** at its end value.

### 6.4b Whole-figure flight and letter animation

```java
.burst(true)                       // the layer lights up in one frame
.drift(0.12, 0, -0.3)              // the figure flies, blocks per tick, world axes
.param("driftt", "40t")            // for how long; afterwards it holds its final place
.wave(0.35, 5)                     // a wave running through the letters
.in("letters", 26).out("letters", 18)
```

`drift` moves the spawn point and every living particle by the same law (friction 0.91, whole-life
path `v · 11.07` blocks), so nothing is left behind and no extra particles are created. A flight
cannot outlive a particle: keep it under ~55 ticks and let `driftt` freeze it there. Text layers are split per glyph, which is what makes
`letters`, `typeletters` and `popletters` possible.

### 6.5 Particle motion and emitters

```java
.motion("to_player").speed(0.4)
.velocity("0.42+0.2*noise(i*.9+u)", "-0.62", "0.18*noise(i*.5+u)")   // world axes, blocks/tick
.trail(7, 0.45)          // 7 particles behind the head, 0.45 blocks apart
.jitter(14)              // spawn point moves randomly on every emission
.chance(0.55)            // it only fires 55% of its turns
.batch(4, 0.5)           // 4 particles in ONE packet (clouds/smoke); no velocity with this
.lift(0.01)
```

Inside velocity formulas `i` is the spawn point index, `u` is the emission cycle counter and `T` is
time. A particle covers roughly `|v| × 35` blocks over its life.

### 6.6 Text specifics

```java
.font("DejaVu_Sans")   // spaces become underscores; built-ins: pixel, bold, thin
.align("center").spacing(1).lineGap(2).outline(false)
```

`\n` inside the text is a line break; `lineGap` is the real distance between the ink of the lines.
Built-in fonts cover Latin, Cyrillic and `♥ ★ ☆ ✦ ● ■ → ← ↑ ↓ №`.

### 6.7 Other

```java
.refresh(15)
.sound("block.beacon.activate", 1f, 1.2f)   // plays when the layer appears and on every repeat
.fps(4).pingpong(true)                       // frame animations
.param("cull", 40)
.estimatePoints()
```

---

## 7. Per-player visibility — `Audience`

This is the reason most plugins integrate LightShow. Particles are sent **per player**, so a show can
be visible to exactly one person standing in a crowd.

```java
Audience.everyone()                 // everyone in the world within viewDistance (default)
Audience.of(player)                 // exactly these players, any distance
Audience.of(collectionOfPlayers)
Audience.fromIds(uuidCollection)
Audience.within(30)                 // everyone within 30 blocks, ignoring viewDistance
Audience.filter(p -> p.getGameMode() == GameMode.SURVIVAL)
Audience.permission("vip.shows")
```

They compose:

```java
Audience aud = Audience.permission("event.viewer")
        .plus(Audience.of(host))
        .without(p -> vanished.contains(p.getUniqueId()));
```

Custom implementations are welcome — the interface is one method:

```java
Audience team = (center, viewRadius) -> myTeam.getOnlineMembers();
```

Change it while the show is running:

```java
handle.setAudience(Audience.of(newPlayer));
```

> **Transport caveat.** True per-player rendering needs packet-level sending. LightShow picks the
> backend automatically: direct NMS packets, otherwise ProtocolLib (add it as a softdepend of your
> server, LightShow will find it), otherwise the plain Bukkit API. In the **Bukkit** fallback the
> engine cannot address individual players and nearby people will also see the particles.
> Check with `lights.transport()` and refuse to run private shows if it returns `Transport.BUKKIT`.

---

## 8. `ShowHandle` — control while it runs

```java
ShowHandle h = builder.start();
if (h == null) return;                      // a listener cancelled ShowStartEvent

h.id(); h.label(); h.owner();
h.isAlive(); h.stop();
h.setPaused(true);                          // freezes the timeline and all animations
h.move(player.getLocation().add(0, 3, 0));  // teleport the show
h.setDurationTicks(-1);                     // extend to infinite, or cut it short
h.setAudience(Audience.of(other));
h.points();                                 // points in the current frame
h.lastParticleCount();                      // particles actually sent last tick
h.elapsedTicks(); h.durationTicks();
h.onEnd(done -> player.sendMessage("done"));
```

Global control:

```java
lights.active();                 // List<ShowHandle>
lights.byId(7);
lights.stopOwnedBy(player.getUniqueId());
lights.stopAll();
```

---

## 9. Events

```java
@EventHandler
public void onShowStart(ShowStartEvent e) {
    ShowHandle show = e.getShow();
    if (regionIsProtected(show.location())) e.setCancelled(true);
}

@EventHandler
public void onShowEnd(ShowEndEvent e) {
    getLogger().info("show " + e.getShow().label() + " finished");
}
```

`ShowStartEvent` is cancellable and fires for **every** show, including ones started by commands,
by the AI generator and by ambient auto-start. That makes it the right place for region, permission
or anti-lag policies.

---

## 10. Presets

```java
LightShowAPI.PresetRegistry presets = lights.presets();

presets.names();                       // all saved presets
presets.namesOfType("math");           // math | draw | text | image
presets.has("demon_eye");
presets.describe("demon_eye");
presets.open("demon_eye")              // -> ShowBuilder, extend it
        .params("dist:20 dur:30s")
        .onlyFor(player)
        .start();
presets.delete("old_thing");
presets.save();                        // flush presets.yml
```

Presets created via `saveAs()` are ordinary presets: they appear in `/pshow list`, work in
`/pshow play a+b`, and can be attached to permanent ambient shows.

---

## 11. Extending the formula language

Your plugin can add functions that become available everywhere — in the API, in commands typed by
admins, and in whatever the AI generator writes.

```java
LightShowAPI.FunctionRegistry fn = lights.functions();

fn.register("hp", 0, args -> player.getHealth() / player.getMaxHealth());
fn.register("beat", 1, args -> myMusicEngine.envelopeAt(args[0]));
fn.register("biggest", -1, args -> {          // -1 = any number of arguments
    double m = Double.NEGATIVE_INFINITY;
    for (double v : args) m = Math.max(m, v);
    return m;
});
```

```java
lights.show().formula("let r=3+2*beat(T);x=r*cos(t);y=r*sin(t)").and().near(player, 6).start();
```

Register in `onEnable`, `unregister` in `onDisable`. Functions are global, so prefix names if you
expect collisions.

---

## 12. Text and pixel art outside particles

The bitmap renderer is exposed, so you can reuse it for maps, holograms, item lore art, anything:

```java
LightShowAPI.PixelArt art = lights.text().render("ПОБЕДА", "bold", "center", 1, 2);
art.width(); art.height(); art.get(x, y); art.count();
List<String> rows = art.outline().rows();     // "0110" strings

lights.show().pixels(rows.get(0)).and()...    // rows are accepted back by pixels()
lights.text().fonts();                        // built-ins + system fonts
```

---

## 13. Particles, colours, transport, budget

```java
lights.registerParticleAlias("myfire", Particle.SOUL_FIRE_FLAME);
lights.particleAliases();                     // every alias usable in particle:
lights.colorNames();

lights.transport();                           // NMS | PROTOCOLLIB | BUKKIT
lights.particleBudget();                      // server-wide particles per tick
lights.setParticleBudget(12000);
lights.currentLoad();                         // particles sent last tick
lights.version();
```

The budget is shared between all running shows. When the sum of what they want exceeds it, every
show is thinned proportionally instead of dropping TPS. Per-layer safety nets also apply: a layer
asking for more than 400 packets/tick (150 for `dust`) gets its `refresh` raised automatically.

---

## 14. Worked examples

### A private objective marker above a quest NPC

```java
ShowHandle marker = lights.show()
        .formula("let k=1+.15*sin(T*4);x=k*cos(t)*0.6;y=k*sin(t)*0.6")
            .particle("end_rod").steps(60).refresh(4).and()
        .formula("x=0;y=1.2+0.15*sin(T*2)")
            .particle("dust").color("#FFD000").particleSize(1.4).steps(1).refresh(8).and()
        .at(npc.getLocation().add(0, 2.4, 0))
        .duration(-1)
        .onlyFor(questPlayer)
        .label("quest-marker")
        .start();

// later
marker.stop();
```

### A countdown that only the arena sees

```java
for (int i = 3; i >= 1; i--) {
    final int n = i;
    Bukkit.getScheduler().runTaskLater(this, () ->
        lights.show().text(String.valueOf(n))
                .pixelSize(0.4).in("scale", 6).out("implode", 6).and()
            .at(arenaCenter.clone().add(0, 6, 0))
            .face("north")
            .duration(18)
            .audience(Audience.within(40))
            .start(), (3 - i) * 20L);
}
```

### A guiding path drawn only for the player following it

```java
ShowHandle path = lights.show()
        .formula("x=0;y=0.2;z=t")
            .particle("end_rod").range(0, 12).steps(40)
            .motion("flow").speed(0.08).refresh(6).and()
        .at(start).face("north")
        .duration(-1)
        .onlyFor(player)
        .cull(40)
        .start();

// keep it under the player's feet as he walks
Bukkit.getScheduler().runTaskTimer(this, () -> {
    if (!path.isAlive()) return;
    path.move(player.getLocation());
}, 1L, 5L);
```

### Team-coloured shapes, one show per team

```java
for (Team team : teams) {
    lights.show()
            .formula("x=6*cos(t);y=6*sin(t)")
                .particle("dust").color(team.rgb()).steps(140).refresh(15).and()
            .at(team.base())
            .duration(-1)
            .audience(Audience.of(team.online()))
            .label("base-" + team.name())
            .start();
}
```

### Reacting to a beat, driven by your own audio engine

```java
lights.functions().register("kick", 0, a -> audio.currentKickEnvelope());

lights.show()
        .formula("let r=5+3*kick();x=r*cos(t);y=r*sin(t)")
            .particle("end_rod").steps(180).refresh(3).and()
        .at(stage).duration(20 * 180).viewDistance(96)
        .start();
```

---

## 15. Parameter appendix

Anything reachable through `param(key, value)` / `params(spec)`. **S** = show-level, **L** = layer-level.

**Placement (S):** `dist at world anchor face offset size spin`
**Time (S):** `dur loop` — **(L):** `from to every for in out int outt`
**Geometry (L):** `mode steps usteps sides radius t u px`
**Look (L):** `particle color psize sound svol spitch burst wave`
**Motion (L):** `motion mspeed vx vy vz trail tgap jitter chance count spread lift drift`
**Transform (L):** `ox oy oz zoom rotx roty rotz`
**Text (L):** `font align spacing lgap outline`
**Frames (L):** `fps pingpong`
**Performance:** `refresh` (L), `cull view max density` (S)
**Audience (S):** `who` — prefer `audience()` from the API instead

Full semantics, defaults and the formula language: `LIGHTSHOW-LLM-REFERENCE.md`.

---

## 16. Compatibility notes

* Minecraft 1.16.5+, Java 8+.
* The API package `ru.lightshow.api` is stable; `ru.lightshow.*` internals are not — do not import
  `Show`, `Params`, `Presets` or `Painter` directly, they will change.
* `ShowHandle` is returned by value; keep the reference, do not look shows up by index.
* If `LightShowProvider.get()` returns `null` at your `onEnable`, either add `depend: [LightShow]`
  or defer the lookup to the first use.
