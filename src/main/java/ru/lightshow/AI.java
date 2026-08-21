package ru.lightshow;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Генерация шоу нейросетью: /pshow ai <что хочешь>. */
public final class AI {

    private static final String API_URL = "https://fptools.onrender.com/api/ai";
    private static final String API_KEY = "fptoolsdim";

    /** Полное описание языка плагина — модель должна писать РАБОЧИЕ команды, а не фантазии. */
    private static final String SYSTEM = String.join("\n",
        "You generate particle-show commands for the Minecraft plugin LightShow (Spigot 1.16.5).",
        "Answer ONLY in the strict output format below. No markdown, no code fences, no extra text.",
        "",
        "== COMMANDS ==",
        "/pshow new <name> <formula> [key:value ...]   create a math preset (name: lowercase latin, short)",
        "  Several layers in one preset: separate with | . Per-layer params go after @ inside that layer.",
        "  Example: /pshow new ring x=4*cos(t);y=4*sin(t) @ steps:160 | x=2*cos(t);y=2*sin(t) @ steps:90",
        "/pshow set <name> <key:value ...>             set default params of a preset (dist, dur, view, in, ...)",
        "/pshow play <a>+<b>+<c> [key:value ...]       run several presets together as one show",
        "",
        "== FORMULA LANGUAGE ==",
        "Assignments separated by ';'. Coordinates: x (right), y (up), z (forward, away from the player).",
        "Own variables: let name=expr;  (name must NOT be x, y, z, t, u, i, n, T, p)",
        "Variables: t = curve parameter, u = second parameter (surface/fill/tube),",
        "  T = seconds since the show started (use it for animation), p = progress 0..1,",
        "  i = point index, n = number of points.",
        "Functions: sin cos tan asin acos atan atan2 sinh cosh tanh sqrt cbrt abs sign floor ceil round frac",
        "  exp ln log min max pow hypot mod clamp lerp step smooth ease saw tri sq pulse noise rand if step4",
        "  step4(k) snaps smoothly through 0,1,2,3 - perfect for 'look in 4 directions'.",
        "  noise(i*0.13) gives a stable pseudo-random value per point - use it for scattered objects.",
        "Constants: pi tau e phi.  Power works normally: cos(t)^3 is (cos t)^3.",
        "",
        "== PARAMS ==",
        "mode: curve (default) | tube (radius:, sides:) | surface (needs u) | fill",
        "t:0..6.28  u:0..3.14   steps:<points along t>  usteps:<points along u>",
        "particle:  end_rod (DEFAULT, use it for almost everything) | dust | flame | soul_fire | soul | spark | enchant | crit | totem",
        "color:     only affects dust / spell_color. white aqua gold #FFAA00 rainbow gradient:#ff00ff-#00ffff",
        "psize:     dust pixel size 0.5..2",
        "motion:    none | out | in | up | down | flow | spin | to_player | from_player | look | random | vec:0,1,0",
        "mspeed:    speed of that motion, 0.01..0.6",
        "refresh:   how often each point is re-sent, PER LAYER",
        "in: / out: fly | fade | type | wipe | rise | drop | explode | scale | spiral   (out: also scatter fall dissolve implode)",
        "int: outt: duration of in/out, e.g. 25t or 2s.   flyd: distance for in:fly",
        "dist: how far in front of the player.  at:x,y,z   offset:0,5,0   face:player|north|up",
        "dur: 20s | inf   spin:0,30,0   view:96   cull:40   anchor:player   max:<particles per tick>",
        "",
        "== HARD RULES (breaking these lags the server, it is the main quality criterion) ==",
        "1. end_rod is the base of everything. Use dust ONLY for small colour accents:",
        "   a layer with dust must have at most ~150 points and refresh 15+. Never build big shapes out of dust.",
        "2. A particle lives ~60 ticks. Live particles = points * 60 / refresh.",
        "   Static layer (no T in the formula) -> refresh:12..20. Animated layer (uses T, or the show has spin:) -> refresh:3..5.",
        "3. Total points of one show <= 1200. Keep steps small: a smooth circle needs only 120-200 steps.",
        "   surface: steps*usteps is the cost - keep steps<=40 and usteps<=20.",
        "   tube: steps*sides - keep sides<=12.",
        "4. Anything longer than ~40 blocks must have cull:40.",
        "5. Every single command must be shorter than 240 characters.",
        "6. Scattered objects (meteors, stars, sparks) = ONE layer using noise(i*...) for positions,",
        "   not many presets. Example: let a=noise(i*.13)*40;let b=noise(i*.37+5)*20;x=a;y=b;z=noise(i*.71+9)*40",
        "7. Falling/flying things use motion: + mspeed:, not animation of every point.",
        "",
        "== SCENES AND TIMING - READ THIS, most requests are a SEQUENCE, not one picture ==",
        "Every layer/preset has its own timeline inside the show:",
        "  from:3s   this layer only appears 3 seconds after the show started",
        "  to:9s     this layer disappears at second 9",
        "  in: out: int: outt: are ALSO per layer, so each scene element gets its own entrance and exit.",
        "Layer position and size can be FORMULAS of T (seconds since the show started):",
        "  ox: oy: oz: = offset of the whole layer,   zoom: = scale of the whole layer.",
        "  Standard ramp: smooth((T-A)/B) is 0 before second A, rises during B seconds, then stays 1.",
        "  ox:-5-lerp(0,4,smooth((T-3)/1))        stays at -5, then slides 4 blocks left between T=3 and T=4",
        "  zoom:lerp(0.25,1.9,smooth((T-6)/1.5))  grows from 25% to 190% between second 6 and 7.5",
        "  oy:12-lerp(0,12,smooth(T/1.2))         drops from 12 blocks above into place during the first 1.2 s",
        "If the user describes an order of events (\"first ... then ... after 3 seconds ...\"), you MUST use from:/to:",
        "and ox/oy/zoom ramps. Never let everything appear at the same moment.",
        "dur: on the PLAY line must cover the WHOLE scenario, otherwise later layers never get their turn.",
        "Formulas and param values must contain no spaces.",
        "",
        "== TEXT ==",
        "/ptext <words> [params]          shows text INSTANTLY. It creates NO preset and CANNOT be used in /pshow play.",
        "/pshow text <name> <words> [...]  saves a TEXT preset named <name>. Only this form can be combined in /pshow play.",
        "So when the show must contain words, always use '/pshow text <name> ...' as a CMD and put <name> in the PLAY line.",
        "Text params: font:pixel|bold|thin (Cyrillic works), px:0.15..0.4 (block size of one letter pixel),",
        "  align:left|center|right, spacing:<pixels between letters>, size:, and the usual in:/out:.",
        "  Underscores in the words become spaces.",
        "MULTI-LINE: write \\n between the lines, and control the gap yourself with lgap:<pixels>.",
        "  lgap:0 = lines almost touching, lgap:3 = normal, lgap:8 = airy. It is NOT fixed, always set it if the user asks.",
        "  Example: /pshow text t1 ПЕРВАЯ_СТРОКА\\nВТОРАЯ_СТРОКА px:0.25 lgap:1 align:center",
        "  Each line is trimmed separately, so lgap is the real distance between the ink of the lines.",
        "outline:true draws only the border of the letters - worth it for font:bold and for images, not for font:pixel.",
        "These symbols exist in the built-in fonts and look great in text: heart, star, filled circle, four-point sparkle, arrows.",
        "EVERY preset name used in the PLAY line must be created by one of the CMD lines above it. Never invent names.",
        "Copy nicknames and words from the user EXACTLY, character by character, keeping their capitalisation.",
        "Never shorten, translate or 'fix' them: aitcHinson stays aitcHinson.",
        "",
        "== BLOCKS, RECTANGLES, PIXEL SHAPES (do not try to draw these with cos/sin) ==",
        "A layer can be a PIXEL PICTURE instead of a formula. Write it as the geometry of the layer:",
        "  pix:0110/1111    rows separated by /, 1 = block, 0 = empty. px: sets the block size in blocks.",
        "  Tetromino examples: pix:1111  pix:11/11  pix:010/111  pix:100/111  pix:001/111  pix:011/110  pix:110/011",
        "  This is the ONLY correct way to draw squares, bricks, tetris pieces, pixel logos and icons.",
        "RECTANGLES: rectx(t,w,h) and recty(t,w,h) walk the perimeter of a rectangle with half-width w and",
        "  half-height h as t goes 0..2pi. A frame is: x=rectx(t,3.2,5.6);y=recty(t,3.2,5.6) with steps:230.",
        "  NEVER use cos/sin for a frame or a border - that gives an oval, which is wrong.",
        "  cellx(i,cols) and celly(i,cols) give grid coordinates from the point index.",
        "MOVING A SHAPE (a falling piece, a sliding word) = animate the LAYER with oy:/ox:, never a particle velocity.",
        "  A piece that falls and STAYS: oy:6.8-lerp(0,12,smooth((T-2.7)/1)) plus from:54t so it is hidden before its turn.",
        "  smooth() clamps, so the piece stops exactly at the end and simply stays there for the rest of the show.",
        "  The plugin lowers refresh automatically for such layers, so they do not smear.",
        "",
        "== EMITTERS: meteors, rain, sparks, fireworks - NEVER fake these with static points ==",
        "A fixed point that spits a particle straight down looks like a flickering column hanging in the air.",
        "That is wrong. A real emitter = few spawn points + rare firing + its own velocity + a trail:",
        "  vx: vy: vz:   velocity of the particle in WORLD axes, in blocks per tick. Formulas of",
        "                i (index of the spawn point), u (= number of the firing cycle, changes on every shot) and T.",
        "                A particle covers about |v|*35 blocks during its life, so vy:-0.6 falls about 21 blocks.",
        "  trail:7 tgap:0.45   a 3-block streak behind the head. THIS is what turns a dot into a meteor.",
        "  jitter:14     the spawn point jumps randomly up to 14 blocks on every shot, so nothing hangs in place",
        "  chance:0.55   the point only fires 55% of its turns -> irregular natural rhythm",
        "  refresh:45    each spawn point fires once per 45 ticks; the plugin spreads the shots across ticks itself",
        "Make the direction slightly different per meteor with noise(i*..+u), never identical for all of them.",
        "VERIFIED METEOR SHOWER (36 spawn points, only ~4 packets per tick, 217 live particles):",
        "  /pshow new stars let a=noise(i*.13)*70;let c=noise(i*.71+9)*70;x=a;y=34+noise(i*.37+3)*10;z=c @ steps:36",
        "    refresh:45 jitter:14 chance:0.55 vx:0.42+0.2*noise(i*.9+u) vy:-0.62 vz:0.18*noise(i*.5+u) trail:7 tgap:0.45",
        "  (all of that on ONE line). Run it with face:north cull:0 view:128 so the whole sky is covered.",
        "Soft rain: small |v| and trail:2. Sparks from a point: vx/vz from noise(i*..)-0.5, vy:0.4, trail:3, jitter:0.",
        "count:4 spread:0.5 packs several particles into ONE packet - ideal for clouds, nebulas, smoke and fog,",
        "  since it is 4 particles for the price of one packet. It cannot be combined with vx/vy/vz.",
        "",
        "== MORE PER-LAYER POWER ==",
        "rotx: roty: rotz: = tilt/rotate THE LAYER in degrees, also formulas of T.",
        "  rotz:T*60 spins the layer in the screen plane, rotz:10*sin(T*2) makes it rock, roty:T*40 flips it in 3D.",
        "every: + for: = repeat the layer in cycles. every:2s for:0.6s means: flash for 0.6 s once every 2 s,",
        "  with its own in:/out: inside each flash. Use it for pulses, heartbeats, blinking rings.",
        "sound: plays a sound the moment the layer appears (and on every repeat):",
        "  sound:block.beacon.activate svol:1 spitch:1.2 . Good ones: entity.ender_dragon.growl,",
        "  block.beacon.activate, entity.player.levelup, block.note_block.chime, entity.firework_rocket.twinkle,",
        "  block.end_portal.spawn, entity.evoker.cast_spell. Put a sound on the key moments of a scene, not on every layer.",
        "",
        "== WHERE PARAMS GO ==",
        "Show-level params (dist at dur loop in out int outt flyd view cull spin face anchor offset max size)",
        "only take effect from the PLAY line or from the FIRST preset of the play command. Put them on PLAY.",
        "Layer-level params (steps usteps sides radius mode t u particle color psize motion mspeed lift refresh)",
        "go after @ inside each layer, or on the /pshow new line if the preset has one layer.",
        "",
        "== TECHNIQUES (verified, reuse the idea) ==",
        "Cutting one shape out of another = if(t<pi, curveA, curveB): first half of t draws arc A, second half arc B.",
        "Crescent moon (circle R=6 minus circle r=5 shifted by 2.2, the two arcs meet exactly):",
        "  let th=0.927+t*1.41;let ph=-1.287+(t-pi)*0.8194;x=if(t<pi,6*cos(th),2.2+5*cos(ph));y=if(t<pi,6*sin(th),5*sin(ph))",
        "Filled disc: x=5*cos(t);y=5*sin(t) with mode:fill u:0..1 usteps:8 (u scales from the centre outwards).",
        "Rain / meteors / falling things = FIXED spawn points plus motion, never animated positions:",
        "  let a=noise(i*.13)*40;x=a;y=25+noise(i*.37+5)*8;z=noise(i*.71+9)*40   steps:60 motion:down mspeed:0.5 refresh:4",
        "Scattered stars in a huge volume: same noise trick with refresh:20 and cull:0.",
        "Sparks near the player: anchor:player offset:0,1,0 with a tiny noise cloud, steps:25 refresh:8.",
        "Pulsing glow ring: let k=1+.3*frac(T*.5);x=6*k*cos(t);y=6*k*sin(t)   refresh:3",
        "Colour accents on a shape: a SEPARATE small dust layer with the same formula but steps:30 refresh:20.",
        "Text is a separate command: /ptext <words> size:2 in:fly int:30t flyd:18 dur:12s",
        "Worked SCENE - two names, one drops onto the other, at T=3 they slide apart, then a heart grows between them:",
        "  /pshow text sc_a NameOne px:0.25 ox:-5-lerp(0,4,smooth((T-3)/1)) in:fade int:15t",
        "  /pshow text sc_b NameTwo px:0.25 oy:12-lerp(0,12,smooth(T/1.2)) ox:5+lerp(0,4,smooth((T-3)/1)) from:10t in:fly int:25t",
        "  /pshow new sc_h let s=0.3;x=s*16*sin(t)^3;y=s*(13*cos(t)-5*cos(2*t)-2*cos(3*t)-cos(4*t)) @ steps:200 refresh:12 from:4s in:scale int:20t zoom:lerp(0.25,1.9,smooth((T-6)/1.5))",
        "  PLAY: /pshow play sc_a+sc_b+sc_h dist:14 dur:20s",
        "",
        "== OUTPUT FORMAT (exactly this, 3 variants) ==",
        "VARIANT: <short latin id>",
        "DESC: <one sentence in Russian describing what the player will see>",
        "CMD: <command 1>",
        "CMD: <command 2>",
        "PLAY: /pshow play <presets joined with +> [params]",
        "END",
        "",
        "Make the three variants genuinely different (different composition or mood), not three copies.",
        "Prefix every preset name with the variant id so names never collide, e.g. moon1_disk, moon1_glow.");

    public static final class Variant {
        public String id = "", desc = "";
        public final List<String> commands = new ArrayList<String>();
        public String play = "";
        public final List<String> presetNames = new ArrayList<String>();
        public int points = 0, perTick = 0;
        public final List<String> problems = new ArrayList<String>();
    }

    private final LightShow plugin;
    private final Map<UUID, List<Variant>> results = new HashMap<UUID, List<Variant>>();
    private final Map<UUID, String> lastPrompt = new HashMap<UUID, String>();
    private final Set<UUID> busy = new HashSet<UUID>();

    public AI(LightShow plugin) { this.plugin = plugin; }

    public List<Variant> resultsOf(Player p) { return results.get(p.getUniqueId()); }
    public String lastPromptOf(Player p) { return lastPrompt.get(p.getUniqueId()); }

    // ------------------------------------------------------------------ запрос

    public void ask(final Player p, final String request, final String extraContext) {
        if (busy.contains(p.getUniqueId())) { p.sendMessage(LightShow.PX + "§7Предыдущий запрос ещё думает…"); return; }
        busy.add(p.getUniqueId());
        lastPrompt.put(p.getUniqueId(), request);
        p.sendMessage(LightShow.PX + "§7Думаю над: §f" + request + " §8(до 45 сек)");

        final String prompt = extraContext == null || extraContext.isEmpty()
                ? request
                : "Previous attempt:\n" + extraContext + "\n\nUser wants this changed: " + request
                  + "\nReturn 3 improved variants in the same strict format.";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            public void run() {
                String err = null, body = null;
                try { body = post(prompt); }
                catch (Throwable t) { err = t.getMessage() == null ? t.toString() : t.getMessage(); }
                final String fErr = err, fBody = body;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    public void run() {
                        busy.remove(p.getUniqueId());
                        if (!p.isOnline()) return;
                        if (fErr != null || fBody == null) {
                            p.sendMessage(LightShow.PX + "§cИИ не ответил: §f" + fErr);
                            return;
                        }
                        List<Variant> vs = parse(fBody);
                        if (vs.isEmpty()) {
                            p.sendMessage(LightShow.PX + "§cИИ ответил не по формату. Попробуй сформулировать иначе.");
                            return;
                        }
                        for (Variant v : vs) validate(v);
                        results.put(p.getUniqueId(), vs);
                        print(p, vs);
                    }
                });
            }
        });
    }

    private static String post(String userPrompt) throws Exception {
        String payload = "{\"messages\":[{\"role\":\"system\",\"content\":" + json(SYSTEM)
                + "},{\"role\":\"user\",\"content\":" + json(userPrompt)
                + "}],\"modelName\":\"ChatGPT 4o\",\"currentPagePath\":\"/chatgpt-4o\"}";

        HttpURLConnection con = (HttpURLConnection) new URL(API_URL).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + API_KEY);
        con.setRequestProperty("User-Agent", "LightShow/2.1");
        con.setConnectTimeout(10000);
        con.setReadTimeout(45000);
        con.setDoOutput(true);
        OutputStream os = con.getOutputStream();
        os.write(payload.getBytes(StandardCharsets.UTF_8));
        os.close();

        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String text = read(in);
        if (code >= 500) throw new Exception("сервер ИИ временно недоступен (HTTP " + code + ")");
        if (code >= 400) throw new Exception("HTTP " + code + ": " + cut(text, 120));
        String resp = field(text, "response");
        if (resp == null) throw new Exception("пустой ответ от сервера");
        return resp;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String cut(String s, int n) { return s == null ? "" : (s.length() > n ? s.substring(0, n) : s); }

    /** Экранирование строки в JSON. */
    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Достаёт строковое поле верхнего уровня из JSON без внешних библиотек. */
    static String field(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;
        int i = json.indexOf(':', k + needle.length());
        if (i < 0) return null;
        while (i + 1 < json.length() && Character.isWhitespace(json.charAt(i + 1))) i++;
        if (i + 1 >= json.length() || json.charAt(i + 1) != '"') return null;
        StringBuilder sb = new StringBuilder();
        for (int j = i + 2; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(++j);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 'r': break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (j + 4 < json.length()) {
                            try { sb.append((char) Integer.parseInt(json.substring(j + 1, j + 5), 16)); } catch (Exception ignored) {}
                            j += 4;
                        }
                        break;
                    default: sb.append(n);
                }
            } else if (c == '"') return sb.toString();
            else sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ разбор

    private static List<Variant> parse(String body) {
        List<Variant> out = new ArrayList<Variant>();
        Variant cur = null;
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("```") || line.isEmpty()) continue;
            String up = line.toUpperCase(Locale.ROOT);
            if (up.startsWith("VARIANT:")) {
                if (cur != null && !cur.commands.isEmpty()) out.add(cur);
                cur = new Variant();
                cur.id = line.substring(8).trim().replaceAll("[^A-Za-z0-9_]", "");
                if (cur.id.isEmpty()) cur.id = "v" + (out.size() + 1);
            } else if (cur == null) continue;
            else if (up.startsWith("DESC:")) cur.desc = line.substring(5).trim();
            else if (up.startsWith("CMD:")) add(cur, line.substring(4).trim());
            else if (up.startsWith("PLAY:")) cur.play = norm(line.substring(5).trim());
            else if (up.startsWith("END")) { if (!cur.commands.isEmpty()) out.add(cur); cur = null; }
        }
        if (cur != null && !cur.commands.isEmpty()) out.add(cur);
        return out;
    }

    private static void add(Variant v, String cmd) {
        cmd = norm(cmd);
        if (cmd.isEmpty()) return;
        v.commands.add(cmd);
        String[] a = cmd.split("\\s+");
        if (a.length >= 3 && a[0].equalsIgnoreCase("/pshow")
                && (a[1].equalsIgnoreCase("new") || a[1].equalsIgnoreCase("text") || a[1].equalsIgnoreCase("image")))
            v.presetNames.add(a[2].toLowerCase(Locale.ROOT));
        if (a.length >= 3 && a[0].equalsIgnoreCase("/pimage")) v.presetNames.add(a[1].toLowerCase(Locale.ROOT));
    }

    private static String norm(String c) {
        c = c.trim();
        if (c.startsWith("`")) c = c.replace("`", "").trim();
        if (!c.isEmpty() && !c.startsWith("/")) c = "/" + c;
        return c;
    }

    // ------------------------------------------------------------------ проверка

    /** Компилируем формулы и считаем реальную цену ДО того, как это увидит сервер. */
    private void validate(Variant v) {
        v.points = 0; v.perTick = 0;
        for (String cmd : v.commands) {
            String[] a = cmd.split("\\s+");
            if (a.length < 3 || !a[0].equalsIgnoreCase("/pshow")) continue;
            if (a[1].equalsIgnoreCase("set")) continue;
            if (a[1].equalsIgnoreCase("text")) {          // текстовый пресет — считаем зажжённые пиксели
                try {
                    Params tp = new Params();
                    List<String> words = tp.parse(Arrays.copyOfRange(a, 3, a.length), 0);
                    Fonts.Bitmap bm = Fonts.render(String.join(" ", words).replace('_', ' '),
                            tp.str("font", "pixel"), tp.lower("align", "center"),
                            tp.integer("spacing", 1), tp.integer("lgap", 3));
                    if (tp.bool("outline", false)) bm = Fonts.outline(bm);
                    v.points += bm.count();
                    v.perTick += bm.count() / Math.max(1, tp.integer("refresh", 12));
                } catch (Throwable ignored) {}
                continue;
            }
            if (!a[1].equalsIgnoreCase("new")) continue;
            if (cmd.length() > 250) v.problems.add("команда длиннее лимита чата: " + a[2]);
            String tail = cmd.substring(cmd.indexOf(a[2]) + a[2].length());
            for (String chunk : tail.split("\\|")) {
                int at = chunk.indexOf('@');
                String geoPart = at >= 0 ? chunk.substring(0, at) : chunk;
                String lpS = at >= 0 ? chunk.substring(at + 1) : "";
                Params par = new Params();
                List<String> rest = par.parse(geoPart.trim().split("\\s+"), 0);
                Params lp = par.merge(Params.deserialize(lpS));
                String geo = String.join(" ", rest).trim();
                if (geo.isEmpty()) continue;
                try {
                    Geo.MathGeo g = Geo.MathGeo.compile(geo);
                    g.mode = lp.lower("mode", "curve");
                    g.steps = lp.integer("steps", 260);
                    g.usteps = lp.integer("usteps", 20);
                    g.sides = lp.integer("sides", 14);
                    int est = g.estimate();
                    int rf = Math.max(1, lp.integer("refresh", g.animated() ? 3 : 12));
                    v.points += est;
                    v.perTick += est / rf;
                } catch (Expr.ParseError e) {
                    v.problems.add("формула в §f" + a[2] + "§7: " + e.getMessage());
                } catch (Throwable t) {
                    v.problems.add("не разобрал слой в §f" + a[2]);
                }
            }
        }
        if (v.points > 2500) v.problems.add("тяжеловато: " + v.points + " точек");

        // каждый пресет из PLAY должен кем-то создаваться
        if (!v.play.isEmpty()) {
            String[] pa = v.play.split("\\s+");
            if (pa.length >= 3 && pa[1].equalsIgnoreCase("play")) {
                for (String name : pa[2].split("\\+")) {
                    String low = name.toLowerCase(Locale.ROOT);
                    boolean made = v.presetNames.contains(low);
                    boolean exists = plugin != null && plugin.presets().has(low);
                    if (!made && !exists) v.problems.add("в запуске указан несуществующий пресет §f" + name);
                }
            }
        }
    }

    // ------------------------------------------------------------------ вывод и запуск

    private void print(Player p, List<Variant> vs) {
        p.sendMessage("");
        p.sendMessage(LightShow.PX + "Готово, §f" + vs.size() + " §fварианта:");
        for (int i = 0; i < vs.size(); i++) {
            Variant v = vs.get(i);
            String health = v.problems.isEmpty() ? "§a✔" : "§e⚠";
            p.sendMessage("§8 " + (i + 1) + ") " + health + " §d" + v.id + " §8· §7" + v.desc);
            p.sendMessage("§8    §7точек §f" + v.points + " §8· §7пакетов/тик §f" + v.perTick
                    + " §8· §7команд §f" + v.commands.size());
            for (String pr : v.problems) p.sendMessage("§8    §e⚠ §7" + pr);
        }
        p.sendMessage("§8 » §f/pshow ai run 1 §8— создать и запустить");
        p.sendMessage("§8 » §f/pshow ai show 1 §8— посмотреть команды");
        p.sendMessage("§8 » §f/pshow ai fix 1 <что не так> §8— переделать");
        p.sendMessage("§8 » §f/pshow ai del 1 §8— удалить созданные пресеты");
        p.sendMessage("");
    }

    public void show(Player p, int idx) {
        Variant v = pick(p, idx);
        if (v == null) return;
        p.sendMessage("");
        p.sendMessage(LightShow.PX + "§d" + v.id + " §8· §7" + v.desc);
        for (String c : v.commands) p.sendMessage("§8 » §f" + c);
        if (!v.play.isEmpty()) p.sendMessage("§8 » §a" + v.play);
        p.sendMessage("");
    }

    public void run(Player p, int idx) {
        Variant v = pick(p, idx);
        if (v == null) return;
        int ok = 0, fail = 0;
        for (String c : v.commands) {
            String low = c.toLowerCase(Locale.ROOT);
            if (!(low.startsWith("/pshow") || low.startsWith("/ptext") || low.startsWith("/pimage"))) { fail++; continue; }
            try { if (Bukkit.dispatchCommand(p, c.substring(1))) ok++; else fail++; }
            catch (Throwable t) { fail++; }
        }
        p.sendMessage(LightShow.PX + "Выполнено команд: §a" + ok + (fail > 0 ? " §8(пропущено " + fail + ")" : ""));
        if (v.play.isEmpty()) return;

        // ИИ иногда вписывает в play пресет, который сам же не создал — выкидываем такие
        String play = v.play;
        String[] a = play.split("\\s+");
        if (a.length >= 3 && a[1].equalsIgnoreCase("play")) {
            List<String> good = new ArrayList<String>(), bad = new ArrayList<String>();
            for (String name : a[2].split("\\+")) {
                if (plugin != null && plugin.presets().has(name)) good.add(name); else bad.add(name);
            }
            if (!bad.isEmpty())
                p.sendMessage(LightShow.PX + "§e⚠ §7в запуске не нашлось: §f" + String.join(", ", bad));
            if (good.isEmpty()) { p.sendMessage(LightShow.PX + "§cЗапускать нечего."); return; }
            a[2] = String.join("+", good);
            play = String.join(" ", a);
        }
        try { Bukkit.dispatchCommand(p, play.substring(1)); }
        catch (Throwable t) { p.sendMessage(LightShow.PX + "§cЗапуск не удался: " + t.getMessage()); }
    }

    public void delete(Player p, int idx) {
        Variant v = pick(p, idx);
        if (v == null) return;
        int n = 0;
        for (String name : v.presetNames) {
            if (plugin.presets().has(name)) { plugin.presets().remove(name); n++; }
        }
        plugin.presets().save();
        plugin.manager().stopOf(p.getUniqueId());
        p.sendMessage(LightShow.PX + "Удалено пресетов: §c" + n);
    }

    public void fix(Player p, int idx, String what) {
        Variant v = pick(p, idx);
        if (v == null) return;
        StringBuilder ctx = new StringBuilder();
        for (String c : v.commands) ctx.append(c).append('\n');
        if (!v.play.isEmpty()) ctx.append(v.play).append('\n');
        ask(p, what, ctx.toString());
    }

    private Variant pick(Player p, int idx) {
        List<Variant> vs = results.get(p.getUniqueId());
        if (vs == null || vs.isEmpty()) { p.sendMessage(LightShow.PX + "§cСначала §f/pshow ai <что хочешь>"); return null; }
        if (idx < 1 || idx > vs.size()) { p.sendMessage(LightShow.PX + "§cВариант 1…" + vs.size()); return null; }
        return vs.get(idx - 1);
    }
}
