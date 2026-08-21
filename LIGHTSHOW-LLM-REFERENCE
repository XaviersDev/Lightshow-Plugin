# LightShow — Full Reference for LLMs

Machine-facing specification of the LightShow particle DSL (Minecraft Spigot/Paper 1.16.5+).
Everything in this document is verified against the plugin source. If you are a model generating
LightShow commands, this file is your complete source of truth — do not invent syntax outside it.

---

> LightShow is also a **library**: other plugins can build the same shows from Java through
> `ru.lightshow.api`. If the task is "write a plugin that uses LightShow", read
> `LIGHTSHOW-API.md` instead — it uses the same parameters and formulas documented here.

## 0. How to use this file

Load it as system context. A request from a human is turned into a list of shell-style commands
that the plugin executes. Your job: pick geometry, stage it in time, and stay inside the
performance budget described in §10. The budget is not advice — exceeding it drops server TPS
and player ping, and is the single most common failure.

---

## 1. Mental model

A **show** is a list of **layers** rendered together at one anchor point in the world.

```
layer → point cloud (local coords) → per-layer timeline & transform → show transform → emission
```

* A layer produces a fixed set of **points** in local coordinates (`x` right, `y` up, `z` forward/away).
* Points are recomputed every tick only if the layer is animated (formula uses `T`, `p` or `rand`),
  otherwise they are computed once and cached.
* Each point is sent to the client as **one particle packet**. There is no batching except `count:`.
* A particle is a *fire-and-forget* client object: once sent, the server cannot move, recolor or
  delete it. It lives its own lifetime and then vanishes. **This is the root of every rule below.**

Two ways to make something move:

| you want | you use |
|---|---|
| a **shape** to move, scale or rotate as a whole | animate the **layer**: `ox: oy: oz: zoom: rotx: roty: rotz:` (formulas of `T`), or put `T` inside the formula |
| **individual particles** to fly (meteors, rain, beams, sparks) | give them **velocity**: `vx: vy: vz:` or `motion:` + `mspeed:` |

Mixing these up is the second most common failure. A falling Tetris piece is a *layer* moving.
A meteor is a *particle* flying.

---

## 2. Commands

Everything is executed as chat/console commands. When a human types them the Minecraft chat limit
is **256 characters**; when a plugin or MCP tool dispatches them there is no limit, but staying
under ~240 keeps them copy-pasteable.

| command | meaning |
|---|---|
| `/pshow new <name> <geometry> [params] [\| geometry2 @ params2 ...]` | create/overwrite a math or pixel preset |
| `/pshow text <name> <words...> [params]` | create a **text preset** (can be combined) |
| `/pshow image <name> <url> [w: h: alpha:]` or `/pimage ...` | download a picture into a coloured preset |
| `/pshow draw <name>` | open the 9×4 frame-by-frame drawing GUI |
| `/pshow set <name> <params>` | merge default params into an existing preset |
| `/pshow play <a>[+<b>+<c>] [params]` | run one or several presets as ONE show |
| `/ptext <words...> [params]` | show text immediately — creates **no** preset, cannot be combined |
| `/pshow list [all\|math\|draw\|text\|image]`, `/pshow info <name>`, `/pshow del <name>` | manage presets |
| `/pshow running`, `/pstop [me\|all\|<id>]` | inspect and stop running shows |
| `/pshow ambient add <id> <preset[+preset]> [params]`, `... del <id>`, `... list` | permanent shows saved to disk, restarted on server boot |
| `/pshow running` | also prints the particle transport (NMS / ProtocolLib / Bukkit) |
| `/pshow reload`, `/pshow fonts`, `/pshow particles`, `/pshow colors`, `/pshow funcs`, `/pshow examples` | utilities |
| `/pshow ai <request>` | ask the built-in model; `run/show/fix/del <n>` follow up |

Permissions: `lightshow.use` (play), `lightshow.edit` (create), `lightshow.ai`, `lightshow.admin`.

---

## 3. Presets and layers

A preset is a named, saved recipe. Four types:

* `math` — one or more layers, each a formula or a pixel picture
* `text` — a rendered string
* `draw` — frames from the GUI (animated pixel art)
* `image` — a downloaded picture with its own colours

**Layer syntax inside `/pshow new`:**

```
/pshow new NAME  <geometry1> @ <layer params 1>  |  <geometry2> @ <layer params 2>
```

* `|` separates layers.
* `@` separates a layer's geometry from that layer's own params.
* Params written **before** `@` (or with no `@` at all) become the preset's **default params**.
* Layers of one preset share the preset defaults; anything after `@` overrides them for that layer.

**Combining presets:** `/pshow play a+b+c` builds one show out of all their layers.

> Scope rule for combos: **show-level** params (§6.1) are taken from the **first** preset plus the
> `play` command line. **Layer-level** params are taken from each preset's own defaults plus the
> command line. Put `dur:`, `dist:`, `view:` etc. on the `play` line to avoid surprises.

---

## 4. Formula language

Assignments separated by `;`. The layer must define at least one of `x`, `y`, `z` (missing ones are 0).

```
let r=4+sin(T*3); x=r*cos(t); y=r*sin(t); z=0
```

### Variables

| name | meaning |
|---|---|
| `t` | main parameter, swept over the `t:` range (default `0..2pi`) |
| `u` | second parameter, for `mode:surface` / `mode:fill` / `mode:tube`, swept over `u:` (default `0..1`) |
| `T` | **seconds since the show started** — the animation clock |
| `p` | progress `0..1` over the show duration |
| `i` | index of the point, `n` | number of points |

Constants: `pi tau e phi`.

### Functions

```
sin cos tan asin acos atan atan2 sinh cosh tanh
sqrt cbrt abs sign floor ceil round frac exp ln log
min max pow hypot mod clamp lerp mix step
smooth smoothstep ease saw tri sq pulse noise rand if
deg rad rectx recty cellx celly step4
```

Notable ones:

* `smooth(x)` — clamps to `0..1` then eases. **The standard ramp is `smooth((T-A)/B)`**: 0 before
  second `A`, rises over `B` seconds, then stays 1 forever. Combine with `lerp(from,to,ramp)`.
* `lerp(a,b,k)` / `mix(a,b,k)` — linear blend.
* `noise(x)` — smooth pseudo-random in `-1..1`. `noise(i*.13)` gives a stable random value **per point**;
  add the cycle variable to vary it per emission.
* `if(cond,a,b)` with comparisons `< > <= >= == !=` — the way to **cut one shape out of another**.
* `step4(k)` — snaps smoothly through 0,1,2,3… (an eye looking in four directions).
* `rectx(t,w,h)` / `recty(t,w,h)` — walk the perimeter of a **rectangle** with half-width `w` and
  half-height `h` as `t` goes `0..2pi`. **Frames and borders must use this, never cos/sin.**
* `cellx(i,cols)` / `celly(i,cols)` — grid coordinates from a point index.
* `rand()` — new random each evaluation; marks the layer as animated (expensive), prefer `noise(i*…)`.

### Hard syntax rules

1. `x`, `y`, `z` are **coordinates** — never use them as `let` names.
2. A `let` name **shadows** constants, so never name a variable `e`, `pi`, `tau`, `phi`.
3. Reserved variable names you also cannot redefine: `t u i n T p`.
4. **No spaces inside a parameter value.** `ox:-4-lerp(0,5,smooth((T-3)/1))` is fine;
   `ox: -4 - lerp(...)` breaks the command into pieces.
5. Powers behave normally: `cos(t)^3` is `(cos t)³`.
6. Ranges accept `pi` and simple products: `t:0..2pi`, `t:-pi..pi`, `t:0..25`.

---

## 5. Geometry sources

### 5.1 `mode:` for formula layers

| mode | what it draws | cost |
|---|---|---|
| `curve` (default) | a line swept by `t` | `steps + 1` |
| `tube` | a pipe of `radius:` around that line with `sides:` faces | `(steps+1) × sides` |
| `surface` | a 2-parameter surface using `t` and `u` | `(steps+1) × (usteps+1)` |
| `fill` | like surface but `u` scales the shape from the centre outwards (filled disc, filled star) | same |

### 5.2 Pixel layers — `pix:`

Instead of a formula, a layer can be a **pixel picture**. This is the only correct way to draw
squares, bricks, tetrominoes, icons and pixel logos.

```
pix:0110/1111        rows separated by "/", 1 = block, 0 = empty
```

`px:` sets the size of one block in world blocks (default `0.5`).

Tetromino set: `pix:1111`, `pix:11/11`, `pix:010/111`, `pix:100/111`, `pix:001/111`,
`pix:011/110`, `pix:110/011`, vertical bar `pix:1/1/1/1`.

### 5.3 Text, images, drawings

Handled by their own commands (§9, §2). All of them accept the same layer params
(`ox oy zoom rot* from to in out particle color refresh …`).

---

## 6. Parameters

Written as `key:value`, separated by spaces. **S** = show-level (one per show), **L** = layer-level.

### 6.1 Placement and lifetime (S)

| key | default | meaning |
|---|---|---|
| `dist:` | 6 | blocks in front of the player's eyes |
| `at:` | — | absolute or relative coords: `at:0,80,0`, `at:~,~5,~` |
| `world:` | player's | world name, for `at:` / ambient |
| `anchor:` | `world` | `world` = fixed, `player` = follows the owner |
| `face:` | `player` | orientation: `player north south east west up down auto` |
| `offset:` | `0,0,0` | static shift of the whole show |
| `size:` | 1 | global scale multiplier |
| `spin:` | 0 | degrees per second, one number (Y axis) or `x,y,z` |
| `dur:` | `10s` | `20s`, `2m`, `600t`, `inf` |
| `loop:` | false | restart when `dur` is reached |
| `who:` | `all` | `me` = only the creator sees it |

### 6.2 Timeline (L) — see §7

| key | default | meaning |
|---|---|---|
| `from:` | 0 | when this layer appears |
| `to:` | show `dur` | when it disappears |
| `every:` / `for:` | 0 | repeat the layer in cycles: `every:2s for:0.6s` |
| `in:` / `out:` | `none` | entrance / exit animation |
| `int:` / `outt:` | `10t` | their durations |
| `ox: oy: oz:` | 0 | layer offset, **formulas of `T`** |
| `zoom:` | 1 | layer scale, formula of `T` |
| `rotx: roty: rotz:` | 0 | layer rotation in degrees, formulas of `T` |

`in:` values: `fly fade type wipe rise drop explode scale spiral none`
`out:` values: `fly fade scatter fall dissolve wipe implode shrink none`

`in:fly` spawns the particles `flyd:` blocks further away and lets them fly to their place using
vanilla velocity, arriving exactly as they expire. Each point departs at its own moment.

### 6.3 Geometry (L)

| key | default | meaning |
|---|---|---|
| `mode:` | `curve` | `curve tube surface fill` |
| `steps:` | 260 | points along `t` |
| `usteps:` | 20 | points along `u` |
| `sides:` | 14 | faces of a tube |
| `radius:` | 1.0 | tube radius (`1.5` → a 3-block-wide walkable corridor) |
| `t:` / `u:` | `0..2pi` / `0..1` | parameter ranges |
| `px:` | 0.5 / 0.25 / 0.4 / 0.15 | block size of one pixel: `pix:` / text / drawing / image |

### 6.4 Appearance (L)

| key | default | meaning |
|---|---|---|
| `particle:` | `end_rod` | see §6.7 |
| `color:` | `white` | only affects `dust`, `spell_color`, `note`. Named, `#RRGGBB`, `rainbow`, `rainbow:2`, `gradient:#a-#b`, `pulse:#a-#b` |
| `psize:` | 1 | dust pixel size |
| `sound:` `svol:` `spitch:` | — | sound played when the layer appears and on every repeat |

### 6.5 Motion and emitters (L)

| key | default | meaning |
|---|---|---|
| `motion:` | `none` | `out in up down flow spin to_player from_player look random vec:0,1,0` |
| `mspeed:` | 0.05 (1.0 if `vx/vy/vz` set) | speed multiplier |
| `vx: vy: vz:` | — | explicit velocity in **world** axes, blocks per tick, formulas of `i`, `u` (= emission cycle number) and `T` |
| `trail:` / `tgap:` | 0 / 0.5 | particles drawn behind the head along the velocity, and their spacing |
| `jitter:` | 0 | random reposition of the spawn point on every emission |
| `chance:` | 1 | probability the point fires on its turn |
| `count:` / `spread:` | 0 / 0.3 | pack several particles into ONE packet (clouds, smoke); incompatible with a velocity |
| `lift:` | 0 | small upward velocity to counter end_rod's slight gravity |

### 6.6 Performance (S unless noted)

| key | default | meaning |
|---|---|---|
| `refresh:` **(L)** | 12 static / 3 animated | ticks between re-sending each point |
| `cull:` | 56 | do not send points further than this from any viewer (`0` = no culling) |
| `view:` | 96 | radius in which players receive the show at all |
| `max:` | 1200 | hard cap of particles per tick for this show |
| `density:` | 1 | fraction of points actually drawn |

### 6.7 Particles

```
end_rod dust flame soul_fire soul smoke big_smoke campfire signal crit magic_crit enchant
portal reverse_portal spark note happy angry heart totem dragon cloud spell spell_color witch
slime snow ash white_ash crimson warped nautilus dolphin bubble splash lava drip_lava drip_water
honey obsidian_tear sneeze damage sweep flash squid_ink composter town_aura
```

Named colours: `white black gray red dark_red orange gold yellow lime green aqua cyan blue
dark_blue purple magenta pink mint ice lava soul`.

**`end_rod` is the base of everything.** It is bright, white, cheap and reads well at distance.
`dust` is the only fully RGB particle but costs more per packet and per frame — use it for small
coloured accents (≤ ~150 points per layer), never for large structures.

---

## 7. Timeline and scenes

Any request that describes an **order of events** ("first… then… after 3 seconds…") must be built
with `from:` / `to:` and `T`-based ramps. Nothing may appear all at once.

```
from:3s                                  layer starts at second 3
to:9s                                    layer ends at second 9
oy:12-lerp(0,12,smooth(T/1.2))           falls from 12 blocks up into place over 1.2 s
ox:-5-lerp(0,4,smooth((T-3)/1))          waits at -5, then slides 4 blocks left at second 3
zoom:lerp(0.25,1.9,smooth((T-6)/1.5))    grows from 25% to 190% between second 6 and 7.5
rotz:T*60                                spins 60°/s in the screen plane
every:2s for:0.6s                        flashes for 0.6 s once every 2 s, with its own in:/out:
```

`smooth()` clamps, so a movement **stops and stays** at its end value — that is how a falling
object lands and remains. `dur:` on the `play` line must cover the whole scenario.

Layers whose `ox/oy/oz/zoom/rot*` depend on `T` automatically drop to `refresh:3` so they do not smear.

---

## 8. Emitters — rain, meteors, sparks, fireworks

A static point that spits a particle straight down is a flickering column hanging in mid-air. Wrong.
A real emitter is: **few spawn points + rare firing + own velocity + a trail**.

* A particle covers about `|v| × 35` blocks over its life (friction 0.98, ~60 ticks), so `vy:-0.6`
  falls about 21 blocks.
* `u` inside `vx/vy/vz` is the emission cycle counter — use `noise(i*.9+u)` so every shot differs.
* `refresh:` is the firing period of each spawn point; the plugin spreads shots across ticks itself.

Verified meteor shower — 36 spawn points, ≈4 packets/tick, ≈220 live particles:

```
/pshow new stars let a=noise(i*.13)*70;let c=noise(i*.71+9)*70;x=a;y=34+noise(i*.37+3)*10;z=c @ steps:36 refresh:45 jitter:14 chance:0.55 vx:0.42+0.2*noise(i*.9+u) vy:-0.62 vz:0.18*noise(i*.5+u) trail:7 tgap:0.45
/pshow play stars face:north cull:0 view:128 dur:inf
```

---

## 9. Text

```
/pshow text <name> <words...> [params]      saved preset, combinable
/ptext <words...> [params]                  instant, not a preset
```

* `\n` = line break. `_` becomes a space.
* `font:` — built-in bitmap fonts `pixel` (8×16, compact, Cyrillic, best for particles), `bold`, `thin`,
  plus any system font. **Spaces in a font name must be written as `_`**: `font:DejaVu_Sans`.
* `lgap:` — real distance between lines in font pixels (`0` tight, `3` normal, `8` airy). Each line
  is trimmed separately, so this is exact.
* `spacing:` — pixels between letters. `align:` — `left center right`. `px:` — block size of one letter pixel.
* `outline:true` — draw only the border of the glyphs; worth it for `bold`/images, pointless for `pixel`.
* Glyphs available in the built-in fonts: `♥ ★ ☆ ✦ ● ■ → ← ↑ ↓ №` plus full Latin and Cyrillic.

---

## 10. Performance model — mandatory

A particle cannot be deleted or moved after it is sent. It simply lives ~60 ticks. Therefore:

```
live particles on the client = points × 60 ÷ refresh
packets per tick             = points ÷ refresh
```

900 points at `refresh:2` is 27 000 live particles and 9 000 packets per second **per viewer** —
that is what kills FPS and times out pings.

| layer kind | refresh |
|---|---|
| static geometry, nothing moves | **12 – 20** |
| shape animated by `T`, `spin:`, or moving `ox/oy/zoom` | **3 – 5** |
| emitter (meteors, sparks): firing period | **20 – 60** |

Budget targets for one show: **≤ 1200 points**, **≤ 300 packets/tick**. Also:

* `steps:` — a smooth circle needs 120–200, not 800. `tube`: keep `sides ≤ 12`.
  `surface`: `steps ≤ 40` and `usteps ≤ 20`.
* Anything longer than ~40 blocks (corridors, helixes, DNA) must set `cull:40`.
* `count:4 spread:0.5` gives four particles for the price of one packet — the only real batching.
* The plugin enforces a safety net: a layer above 400 packets/tick (150 for `dust`) has its
  `refresh` raised automatically, and the server-wide cap is `max-particles-per-tick` in `config.yml`
  (default 8000, shared between running shows).
* Players must have **Video Settings → Particles: All**, otherwise the client itself drops particles.

---

## 11. Verified recipes

**Rectangular frame** (never use cos/sin for this):

```
x=rectx(t,3.2,5.6);y=recty(t,3.2,5.6)          steps:230 refresh:14
```

**Crescent moon** — circle R=6 minus circle r=5 offset by 2.2; the two arcs meet exactly:

```
let th=0.927+t*1.41;let ph=-1.287+(t-pi)*0.8194;x=if(t<pi,6*cos(th),2.2+5*cos(ph));y=if(t<pi,6*sin(th),5*sin(ph))
```

**Eye outline** — a parabolic lens with sharp corners (a lemniscate is a figure-8, not an eye):

```
x=9*sin(t);y=3.2*cos(t)*abs(cos(t))
```

**Pupil looking in four directions**:

```
let a=step4(T*.45)*pi/2;let o=4*cos(a);let q=.45*sin(a);x=1.85*cos(t)+o;y=1.85*sin(t)+q
```

**Blink** applied to every layer of a face/eye:

```
let b=1-.85*max(0,1-abs(frac(T*.13)*40-1));   ... y=<expr>*b
```

**Walkable spiral corridor, 3 blocks wide**:

```
x=9*cos(t);y=1.4*t;z=9*sin(t)     mode:tube radius:1.5 sides:10 t:0..25 steps:400 refresh:20 cull:40
```

**Falling Tetris piece that lands and stays**:

```
pix:010/111 @ px:0.6 particle:dust color:#B040FF ox:-2.1 oy:6.8-lerp(0,11.1,smooth((T-5.4)/1)) from:108t
```

**Two names sliding apart with a heart growing between them**:

```
/pshow text sc_a NameOne px:0.25 ox:-5-lerp(0,4,smooth((T-3)/1)) in:fade int:15t
/pshow text sc_b NameTwo px:0.25 oy:12-lerp(0,12,smooth(T/1.2)) ox:5+lerp(0,4,smooth((T-3)/1)) from:10t in:fly int:25t
/pshow new sc_h let s=0.3;x=s*16*sin(t)^3;y=s*(13*cos(t)-5*cos(2*t)-2*cos(3*t)-cos(4*t)) @ steps:200 from:4s in:scale int:20t zoom:lerp(0.25,1.9,smooth((T-6)/1.5))
/pshow play sc_a+sc_b+sc_h dist:14 dur:25s
```

**Filled disc / star**: any closed curve plus `mode:fill u:0..1 usteps:8`.

**Scattered stars in a huge volume**:

```
let a=noise(i*.137)*70;let b=noise(i*.371+9)*26+14;let c=noise(i*.713+21)*70;x=a;y=b;z=c
steps:200 refresh:20 cull:0 view:160 particle:dust color:rainbow psize:1.6 motion:up mspeed:0.01
```

---

## 12. Anti-patterns

| wrong | right |
|---|---|
| drawing a frame or border with `cos/sin` | `rectx(t,w,h)` / `recty(t,w,h)` |
| building squares or tetrominoes from formulas | `pix:0110/1111` |
| a falling object made with `motion:down` on a static point | animate the layer with `oy:…smooth(…)` |
| a meteor made of one particle with no trail | `vx/vy/vz` + `trail:` + `jitter:` + `chance:` |
| `refresh:1..2` on a static shape | `refresh:12..20` |
| a big shape out of `dust` | `end_rod` for the shape, `dust` only for small accents |
| `steps:800` "for smoothness" | 120–260 is already smooth |
| everything appearing at second 0 | stage it with `from:` / `to:` |
| `/ptext` inside a `/pshow play` combo | `/pshow text <name> …` then use `<name>` |
| a preset name in `play` that no command created | every name must come from a `new`/`text`/`image` command |
| naming a variable `e`, `x`, `y`, `z` | any other name |
| spaces inside a param value | write it without spaces |

---

## 13. Pre-flight checklist

Before returning commands, verify:

1. Every preset used in `play` is created by a command above it.
2. No command exceeds ~240 characters if a human will type it.
3. Total points ≤ ~1200; every layer's `refresh` matches its kind (§10).
4. `dur:` covers the last `from:` plus its animation.
5. `dust` layers are small; the structure itself is `end_rod`.
6. Long structures have `cull:40`.
7. No spaces inside param values; no reserved names as `let` variables.
8. Sequenced requests actually use `from:`/`to:` and `smooth((T-A)/B)` ramps.

---

## 14. Output format for generators

When producing several alternatives (the format the built-in `/pshow ai` parses):

```
VARIANT: <short latin id>
DESC: <one sentence describing what the player will see>
CMD: <command 1>
CMD: <command 2>
PLAY: /pshow play <presets joined with +> [params]
END
```

Prefix every preset name with the variant id so names never collide (`moon1_disk`, `moon1_glow`).
Copy nicknames and words from the user exactly, character by character, keeping capitalisation.
