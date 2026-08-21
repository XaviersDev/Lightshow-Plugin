package ru.lightshow;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import ru.lightshow.api.Audience;
import ru.lightshow.api.ShowHandle;

import java.util.*;
import java.util.function.Consumer;

/** Одно запущенное шоу. */
public final class Show implements ShowHandle {

    /** Слой = геометрия + свой стиль частиц. В одном шоу их может быть сколько угодно. */
    public static final class Layer {
        public Geo.Source src;
        public Particle particle = Particle.END_ROD;
        public Painter.Col col = Painter.Col.parse("white");
        public float psize = 1f;
        public String motion = "none";
        public double mspeed = 0.05, lift = 0;
        public int refresh = 12;      // раз в сколько тиков обновляется КАЖДАЯ точка этого слоя
        public boolean colorAnimated;
        // ---- собственный таймлайн слоя (сцены) ----
        public int from = 0, to = -1;            // когда слой живёт, в тиках от старта шоу
        public String in = "none", out = "none";
        public int inT = 10, outT = 10;
        public Expr.Compiled ox, oy, oz, zoom;   // формулы от T: смещение и масштаб слоя
        public Expr.Compiled rotx, roty, rotz;   // формулы от T: наклон слоя в градусах
        public String sound; public float svol = 1, spitch = 1;
        // ---- эмиттер: своя скорость на каждую точку, хвост, дрожание, разброс ----
        public Expr.Compiled vx, vy, vz;         // вектор скорости в МИРОВЫХ осях, формулы от i/T
        public int trail = 0; public double tgap = 0.5;
        public double jitter = 0, chance = 1;
        public int pcount = 0; public double spread = 0;
        public final Expr.Ctx vctx = new Expr.Ctx(8);
        public int every = 0, forT = 0;          // повтор слоя циклами
        public final Expr.Ctx ctx = new Expr.Ctx(8);
    }

    public final int id;
    public final String label;
    public final UUID owner;
    private final World world;
    private final Params par;
    private final List<Layer> layers;

    private double cx, cy, cz;
    private final double[] F = new double[3], R = new double[3], U = new double[3];
    private final double[] offset;
    private final String anchor, face;
    private final double[] spin;

    private int dur;
    private final int inT, outT, refresh, maxPerTick;
    private final boolean loop;
    private final String inAnim, outAnim;
    private final double scale, density, view, cull, flyDist;
    private final boolean onlyOwner;

    private final Geo.Buf buf = new Geo.Buf();
    private final boolean animated;
    private boolean built = false;
    private int[] layerFrom = new int[0], layerTo = new int[0];
    private double[] lMinX = new double[0], lMaxX = new double[0], lMinY = new double[0], lMaxY = new double[0], lRad = new double[0];
    private double minX, maxX, minY, maxY, radiusMax = 1;

    private int elapsed = 0;
    private boolean dead = false;
    private List<Player> viewers = new ArrayList<Player>();
    private final Map<Integer, Object> dustCache = new HashMap<Integer, Object>();
    public long lastEmitted = 0;
    private Audience audience;
    private boolean paused;
    private final List<Consumer<ShowHandle>> endCallbacks = new ArrayList<Consumer<ShowHandle>>();

    public Show(int id, String label, Player creator, Location center, List<Layer> layers, Params p) {
        this.id = id;
        this.label = label;
        this.owner = creator == null ? null : creator.getUniqueId();
        this.world = center.getWorld();
        this.layers = layers;
        this.par = p;

        this.dur = p.ticks("dur", 200);
        this.loop = p.bool("loop", false);
        this.inT = p.ticks("int", 10);
        this.outT = p.ticks("outt", 10);
        this.inAnim = p.lower("in", "none");
        this.outAnim = p.lower("out", "none");
        this.refresh = Math.max(1, p.integer("refresh", 3));
        this.scale = p.num("size", 1);
        this.density = Math.max(0.01, Math.min(1, p.num("density", 1)));
        this.view = p.num("view", 96);
        this.cull = p.num("cull", 56);
        this.flyDist = p.num("flyd", 14);
        this.maxPerTick = p.integer("max", 1200);
        this.anchor = p.lower("anchor", "world");
        this.face = p.lower("face", "player");
        this.onlyOwner = p.lower("who", "all").equals("me");
        this.offset = p.vec("offset", 0, 0, 0);
        double[] sp = p.vec("spin", 0, 0, 0);
        if (!p.has("spin")) sp = new double[] { 0, 0, 0 };
        else if (p.str("spin", "0").indexOf(',') < 0) sp = new double[] { 0, p.num("spin", 0), 0 };
        this.spin = sp;

        cx = center.getX(); cy = center.getY(); cz = center.getZ();
        setBasis(center, creator);

        boolean anim = false;
        for (Layer l : layers) anim |= l.src.animated();
        this.animated = anim;
        refreshViewers();
    }

    // --------------------------------------------------------------- базис

    private void setBasis(Location center, Player creator) {
        double dx, dy, dz;
        if (face.equals("player") || face.equals("auto")) {
            Location l = creator != null ? creator.getEyeLocation() : center;
            org.bukkit.util.Vector d = l.getDirection();
            dx = d.getX(); dy = d.getY(); dz = d.getZ();
        } else if (face.equals("north")) { dx = 0; dy = 0; dz = -1; }
        else if (face.equals("south")) { dx = 0; dy = 0; dz = 1; }
        else if (face.equals("east")) { dx = 1; dy = 0; dz = 0; }
        else if (face.equals("west")) { dx = -1; dy = 0; dz = 0; }
        else if (face.equals("up")) { dx = 0; dy = 1; dz = 0; }
        else if (face.equals("down")) { dx = 0; dy = -1; dz = 0; }
        else { dx = 0; dy = 0; dz = -1; }
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) { dx = 0; dy = 0; dz = -1; len = 1; }
        F[0] = dx / len; F[1] = dy / len; F[2] = dz / len;

        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(F[1]) > 0.98) { ux = 0; uy = 0; uz = 1; }
        // right = F x worldUp
        R[0] = F[1] * uz - F[2] * uy; R[1] = F[2] * ux - F[0] * uz; R[2] = F[0] * uy - F[1] * ux;
        double rl = Math.sqrt(R[0] * R[0] + R[1] * R[1] + R[2] * R[2]);
        if (rl < 1e-9) { R[0] = 1; R[1] = 0; R[2] = 0; rl = 1; }
        R[0] /= rl; R[1] /= rl; R[2] /= rl;
        // up = right x F
        U[0] = R[1] * F[2] - R[2] * F[1]; U[1] = R[2] * F[0] - R[0] * F[2]; U[2] = R[0] * F[1] - R[1] * F[0];
    }

    // --------------------------------------------------------------- зрители

    private void refreshViewers() {
        if (audience != null) {
            try { viewers = audience.viewers(new Location(world, cx, cy, cz), view); }
            catch (Throwable t) { viewers = new ArrayList<Player>(); }
            return;
        }
        List<Player> out = new ArrayList<Player>();
        if (onlyOwner) {
            Player p = owner == null ? null : Bukkit.getPlayer(owner);
            if (p != null && p.getWorld().equals(world)) out.add(p);
        } else {
            double v2 = view * view;
            for (Player p : world.getPlayers()) {
                double dx = p.getLocation().getX() - cx, dy = p.getLocation().getY() - cy, dz = p.getLocation().getZ() - cz;
                if (dx * dx + dy * dy + dz * dz <= v2) out.add(p);
            }
        }
        viewers = out;
    }

    // ------------------------------------------------------------- ShowHandle

    public int id() { return id; }
    public String label() { return label; }
    public UUID owner() { return owner; }
    public boolean isAlive() { return !dead; }
    public void stop() { dead = true; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean p) { this.paused = p; }
    public Location location() { return new Location(world, cx, cy, cz); }

    public void move(Location l) {
        if (l == null) return;
        cx = l.getX(); cy = l.getY(); cz = l.getZ();
        refreshViewers();
    }

    public long lastParticleCount() { return lastEmitted; }
    public int elapsedTicks() { return elapsed; }
    public int durationTicks() { return dur; }
    public void setDurationTicks(int ticks) { this.dur = ticks; }
    public Audience audience() { return audience; }
    public void setAudience(Audience a) { this.audience = a; refreshViewers(); }
    public void onEnd(Consumer<ShowHandle> cb) { if (cb != null) endCallbacks.add(cb); }

    /** Вызывается менеджером ровно один раз. */
    public void fireEnd() {
        for (Consumer<ShowHandle> c : endCallbacks) {
            try { c.accept(this); } catch (Throwable ignored) {}
        }
        endCallbacks.clear();
    }

    public boolean isDead() { return dead; }
    public void kill() { dead = true; }
    public int elapsed() { return elapsed; }
    public int duration() { return dur; }
    public int points() { return buf.size; }
    public Params params() { return par; }
    public Location center() { return new Location(world, cx, cy, cz); }

    // --------------------------------------------------------------- тик

    public int tick(int budget) {
        if (dead || paused) return 0;
        elapsed++;
        if (dur > 0 && elapsed > dur) {
            if (loop) elapsed = 1; else { dead = true; return 0; }
        }
        if (anchor.equals("player")) {
            Player p = owner == null ? null : Bukkit.getPlayer(owner);
            if (p == null || !p.getWorld().equals(world)) { dead = true; return 0; }
            Location l = p.getLocation();
            cx = l.getX() + offset[0]; cy = l.getY() + offset[1]; cz = l.getZ() + offset[2];
            if (face.equals("player") || face.equals("auto")) setBasis(l, p);
        }
        if (elapsed % 20 == 1) refreshViewers();
        if (viewers.isEmpty()) return 0;   // никто не смотрит — не тратим ни такта, ни трафика

        double T = elapsed / 20.0;
        double p01 = dur > 0 ? (double) elapsed / dur : (elapsed % 1200) / 1200.0;

        if (animated || !built) { rebuild(T, p01); built = true; }
        return emit(T, p01, budget);
    }

    private void rebuild(double T, double p01) {
        buf.clear();
        if (layerFrom.length < layers.size()) {
            int n = layers.size();
            layerFrom = new int[n]; layerTo = new int[n];
            lMinX = new double[n]; lMaxX = new double[n]; lMinY = new double[n]; lMaxY = new double[n]; lRad = new double[n];
        }
        for (int i = 0; i < layers.size(); i++) {
            layerFrom[i] = buf.size;
            try { layers.get(i).src.build(buf, i, T, p01); }
            catch (Throwable ignored) {}
            layerTo[i] = buf.size;
            double a1 = Double.MAX_VALUE, a2 = -Double.MAX_VALUE, b1 = Double.MAX_VALUE, b2 = -Double.MAX_VALUE, rr = 1;
            for (int k = layerFrom[i]; k < layerTo[i]; k++) {
                if (buf.x[k] < a1) a1 = buf.x[k];
                if (buf.x[k] > a2) a2 = buf.x[k];
                if (buf.y[k] < b1) b1 = buf.y[k];
                if (buf.y[k] > b2) b2 = buf.y[k];
                double r = Math.sqrt(buf.x[k] * buf.x[k] + buf.y[k] * buf.y[k] + buf.z[k] * buf.z[k]);
                if (r > rr) rr = r;
            }
            if (a2 < a1) { a1 = a2 = b1 = b2 = 0; }
            lMinX[i] = a1; lMaxX[i] = a2; lMinY[i] = b1; lMaxY[i] = b2; lRad[i] = rr;
        }
        minX = minY = Double.MAX_VALUE; maxX = maxY = -Double.MAX_VALUE; radiusMax = 1;
        for (int i = 0; i < buf.size; i++) {
            if (buf.x[i] < minX) minX = buf.x[i];
            if (buf.x[i] > maxX) maxX = buf.x[i];
            if (buf.y[i] < minY) minY = buf.y[i];
            if (buf.y[i] > maxY) maxY = buf.y[i];
            double r = Math.sqrt(buf.x[i] * buf.x[i] + buf.y[i] * buf.y[i] + buf.z[i] * buf.z[i]);
            if (r > radiusMax) radiusMax = r;
        }
        if (maxX < minX) { minX = maxX = minY = maxY = 0; }
    }

    // --------------------------------------------------------------- отрисовка

    private final double[] tmp = new double[3];
    private double[] vx, vy, vz;

    private int emit(double T, double p01, int budget) {
        int emitted = 0;

        double ax = Math.toRadians(spin[0] * T), ay = Math.toRadians(spin[1] * T), az = Math.toRadians(spin[2] * T);
        double cxr = Math.cos(ax), sxr = Math.sin(ax), cyr = Math.cos(ay), syr = Math.sin(ay), czr = Math.cos(az), szr = Math.sin(az);
        boolean rotate = spin[0] != 0 || spin[1] != 0 || spin[2] != 0;

        Player near = viewers.isEmpty() ? null : viewers.get(0);
        double nearBest = Double.MAX_VALUE;
        int nv = viewers.size();
        if (vx == null || vx.length < nv) { vx = new double[nv + 4]; vy = new double[nv + 4]; vz = new double[nv + 4]; }
        for (int k = 0; k < nv; k++) {
            Player pl = viewers.get(k);
            Location pl2 = pl.getLocation();
            vx[k] = pl2.getX(); vy[k] = pl2.getY() + 1.5; vz[k] = pl2.getZ();
            double ddx = vx[k] - cx, ddy = vy[k] - cy, ddz = vz[k] - cz;
            double d = ddx * ddx + ddy * ddy + ddz * ddz;
            if (d < nearBest) { nearBest = d; near = pl; }
        }
        final double cull2 = cull <= 0 ? Double.MAX_VALUE : cull * cull;
        // end_rod: трение 0.98, жизнь ~60 тиков => путь за жизнь = speed * 35 блоков.
        // Значит частица, рождённая в d блоках позади, долетит ровно до фигуры и там погаснет,
        // если дать ей speed = d/35. Раньше я брал d/inT — отсюда и перелёт мимо текста.
        final double LIFE_TRAVEL = 35.0;

        // общий лимит: если слои вместе просят больше, чем можно — равномерно разрежаем
        int allowed = maxPerTick;
        if (budget > 0) allowed = Math.min(allowed, budget);
        int planned = 0;
        for (int li = 0; li < layers.size(); li++) {
            int cnt = layerTo[li] - layerFrom[li];
            planned += cnt / Math.max(1, layers.get(li).refresh) + 1;
        }
        double thin = planned > allowed ? (double) planned / allowed : 1.0;

        for (int li = 0; li < layers.size(); li++) {
            Layer L = layers.get(li);
            int from = layerFrom[li], to = layerTo[li];
            if (to <= from) continue;

            // ---- собственный таймлайн слоя ----
            if (elapsed < L.from) continue;
            if (L.to > 0 && elapsed > L.to) continue;
            int since; double vanish = 0;
            if (L.every > 0) {                          // слой повторяется циклами
                int cyc = (elapsed - L.from) % L.every;
                int win = L.forT > 0 ? L.forT : L.every;
                if (cyc >= win) continue;
                since = cyc;
                if (L.outT > 0 && cyc > win - L.outT)
                    vanish = Expr.clamp((double) (cyc - (win - L.outT)) / L.outT, 0, 1);
            } else {
                since = elapsed - L.from;
                if (L.outT > 0 && L.to > 0 && elapsed > L.to - L.outT)
                    vanish = Expr.clamp((double) (elapsed - (L.to - L.outT)) / L.outT, 0, 1);
            }
            double appear = (L.inT > 0 && since < L.inT) ? (double) since / L.inT : 1.0;
            String inAnim = L.in, outAnim = L.out;

            if (since == 0 && L.sound != null && !L.sound.isEmpty()) {
                Location sl = new Location(world, cx, cy, cz);
                for (int k = 0; k < nv; k++) {
                    try { viewers.get(k).playSound(sl, L.sound, L.svol, L.spitch); } catch (Throwable ignored) {}
                }
            }

            // ---- смещение и масштаб слоя как формулы от T ----
            double lox = 0, loy = 0, loz = 0, zoom = 1, rax = 0, ray = 0, raz = 0;
            if (L.ox != null || L.oy != null || L.oz != null || L.zoom != null
                    || L.rotx != null || L.roty != null || L.rotz != null) {
                L.ctx.fit(8);
                L.ctx.v[Expr.S_TIME] = T; L.ctx.v[Expr.S_PROG] = p01;
                L.ctx.v[Expr.S_T] = 0; L.ctx.v[Expr.S_U] = 0; L.ctx.v[Expr.S_I] = 0; L.ctx.v[Expr.S_N] = 1;
                try {
                    if (L.ox != null) lox = L.ox.ev(L.ctx);
                    if (L.oy != null) loy = L.oy.ev(L.ctx);
                    if (L.oz != null) loz = L.oz.ev(L.ctx);
                    if (L.zoom != null) zoom = L.zoom.ev(L.ctx);
                    if (L.rotx != null) rax = Math.toRadians(L.rotx.ev(L.ctx));
                    if (L.roty != null) ray = Math.toRadians(L.roty.ev(L.ctx));
                    if (L.rotz != null) raz = Math.toRadians(L.rotz.ev(L.ctx));
                } catch (Throwable ignored) {}
            }
            boolean lrot = rax != 0 || ray != 0 || raz != 0;
            double lcx = Math.cos(rax), lsx = Math.sin(rax), lcy = Math.cos(ray),
                   lsy = Math.sin(ray), lcz = Math.cos(raz), lsz = Math.sin(raz);

            double minX = lMinX[li], maxX = lMaxX[li], minY = lMinY[li], maxY = lMaxY[li], radiusMax = lRad[li];
            double width = Math.max(0.001, maxX - minX), height = Math.max(0.001, maxY - minY);

            int stride = Math.max(1, (int) Math.ceil(L.refresh * thin));

            for (int i = from + (elapsed % stride); i < to; i += stride) {
                if (density < 1 && buf.seed[i] > density) continue;

                double lx = buf.x[i] * scale * zoom, ly = buf.y[i] * scale * zoom, lz = buf.z[i] * scale * zoom;
                double s = buf.seed[i];
                double sc = 1.0, ox = 0, oy = 0, oz = 0;
                boolean flying = false; double flySpd = 0;

                if (appear < 1 && !inAnim.equals("none")) {
                    double a = appear;
                    switch (inAnim) {
                        case "fade": if (s > a) continue; break;
                        case "type": if (buf.f[i] > a) continue; break;
                        case "wipe": if ((buf.x[i] - minX) / width > a) continue; break;
                        case "rise": oy -= (1 - ease(a)) * (height + 4); break;
                        case "drop": oy += (1 - ease(a)) * (height + 6); break;
                        case "fly": {
                            double local = (a - s * 0.5) / 0.5;   // каждая точка вылетает в свой момент
                            if (local <= 0) continue;             // ещё не вылетела — не рисуем
                            if (local < 1) {                      // летит
                                double d = (1 - local) * flyDist;
                                oz += d; flying = true; flySpd = d / LIFE_TRAVEL;
                            }                                     // local >= 1 — уже на месте, обычная точка
                            break;
                        }
                        case "explode": {
                            double k = (1 - ease(a)) * (radiusMax * 3 + 4);
                            ox += (s - 0.5) * 2 * k; oy += (hash(i, 1) - 0.5) * 2 * k; oz += (hash(i, 2) - 0.5) * 2 * k;
                            break;
                        }
                        case "scale": sc = ease(a); break;
                        case "spiral": {
                            double ang = (1 - ease(a)) * Math.PI * 4;
                            double c2 = Math.cos(ang), s2 = Math.sin(ang);
                            double nx = lx * c2 - ly * s2, ny = lx * s2 + ly * c2;
                            lx = nx; ly = ny; sc = 0.15 + 0.85 * ease(a);
                            break;
                        }
                    }
                }
                if (vanish > 0 && !outAnim.equals("none")) {
                    double v = vanish;
                    switch (outAnim) {
                        case "fade": case "dissolve": if (s < v) continue; break;
                        case "wipe": if ((buf.x[i] - minX) / width < v) continue; break;
                        case "fly": {
                            double d = v * v * flyDist * 1.8 * (0.5 + s);
                            oz -= d; flying = true; flySpd = d / LIFE_TRAVEL;
                            break;
                        }
                        case "scatter": {
                            double k = v * v * (radiusMax * 3 + 6);
                            ox += (s - 0.5) * 2 * k; oy += (hash(i, 1) - 0.5) * 2 * k; oz += (hash(i, 2) - 0.5) * 2 * k;
                            break;
                        }
                        case "fall": oy -= v * v * (height + 12) * (0.5 + s); break;
                        case "implode": case "shrink": sc *= (1 - v); break;
                    }
                }

                lx = lx * sc + ox; ly = ly * sc + oy; lz = lz * sc + oz;
                if (lrot) {                       // наклон/вращение самого слоя
                    double x1 = lx * lcz - ly * lsz, y1 = lx * lsz + ly * lcz;
                    double y2 = y1 * lcx - lz * lsx, z2 = y1 * lsx + lz * lcx;
                    double x3 = x1 * lcy + z2 * lsy, z3 = -x1 * lsy + z2 * lcy;
                    lx = x3; ly = y2; lz = z3;
                }
                lx += lox; ly += loy; lz += loz;

                if (rotate) {
                    double y1 = ly * cxr - lz * sxr, z1 = ly * sxr + lz * cxr;
                    double x2 = lx * cyr + z1 * syr, z2 = -lx * syr + z1 * cyr;
                    double x3 = x2 * czr - y1 * szr, y3 = x2 * szr + y1 * czr;
                    lx = x3; ly = y3; lz = z2;
                }

                double wx = cx + R[0] * lx + U[0] * ly + F[0] * lz;
                double wy = cy + R[1] * lx + U[1] * ly + F[1] * lz;
                double wz = cz + R[2] * lx + U[2] * ly + F[2] * lz;
                if (!anchor.equals("player")) { wx += offset[0]; wy += offset[1]; wz += offset[2]; }

                if (cull > 0) {
                    boolean seen = false;
                    for (int k = 0; k < nv; k++) {
                        double ddx = vx[k] - wx, ddy = vy[k] - wy, ddz = vz[k] - wz;
                        if (ddx * ddx + ddy * ddy + ddz * ddz <= cull2) { seen = true; break; }
                    }
                    if (!seen) continue;
                }

                // ---- эмиттер: пропуск по шансу и дрожание при каждом вылете ----
                int cycle = elapsed / stride;
                if (L.chance < 1 && hash(i, cycle * 7 + 3) > L.chance) continue;
                if (L.jitter > 0) {
                    wx += (hash(i, cycle * 11 + 1) - 0.5) * 2 * L.jitter;
                    wy += (hash(i, cycle * 13 + 5) - 0.5) * 2 * L.jitter;
                    wz += (hash(i, cycle * 17 + 9) - 0.5) * 2 * L.jitter;
                }

                motionVector(L, i, wx, wy, wz, near, tmp);

                int rgb = buf.rgb[i] >= 0 ? buf.rgb[i] : L.col.rgb(buf.f[i], T);
                Object data = null;
                double dx = tmp[0], dy = tmp[1] + L.lift / Math.max(1e-6, L.mspeed), dz = tmp[2];
                double speed = L.mspeed;
                int count = 0;

                // ---- своя скорость на каждую точку (мировые оси) ----
                boolean ownVel = L.vx != null || L.vy != null || L.vz != null;
                if (ownVel) {
                    L.vctx.fit(8);
                    L.vctx.v[Expr.S_I] = i - from; L.vctx.v[Expr.S_N] = Math.max(1, to - from);
                    L.vctx.v[Expr.S_TIME] = T; L.vctx.v[Expr.S_PROG] = p01;
                    L.vctx.v[Expr.S_T] = buf.f[i]; L.vctx.v[Expr.S_U] = cycle;
                    try {
                        dx = L.vx != null ? L.vx.ev(L.vctx) : 0;
                        dy = L.vy != null ? L.vy.ev(L.vctx) : 0;
                        dz = L.vz != null ? L.vz.ev(L.vctx) : 0;
                    } catch (Throwable ignored) { dx = 0; dy = 0; dz = 0; }
                    speed = L.mspeed;
                }

                if (flying) {                   // ванильная механика скорости: летит и гаснет на месте фигуры
                    dx = -F[0]; dy = -F[1]; dz = -F[2];
                    speed = flySpd;
                } else if (Painter.isDust(L.particle)) {
                    data = dustCache.get(rgb);
                    if (data == null) { data = Painter.dustData(rgb, L.psize); if (dustCache.size() < 512) dustCache.put(rgb, data); }
                } else if (Painter.isSpellColor(L.particle) && L.motion.equals("none")) {
                    dx = Math.max(0.001, ((rgb >> 16) & 255) / 255.0);
                    dy = ((rgb >> 8) & 255) / 255.0;
                    dz = (rgb & 255) / 255.0;
                    speed = 1;
                } else if (Painter.isNote(L.particle) && L.motion.equals("none")) {
                    dx = ((buf.f[i] * 24) % 24) / 24.0; dy = 0; dz = 0; speed = 1;
                }
                if (Painter.isDust(L.particle) && data == null) {
                    data = dustCache.get(rgb);
                    if (data == null) { data = Painter.dustData(rgb, L.psize); if (dustCache.size() < 512) dustCache.put(rgb, data); }
                }

                if (L.pcount > 0 && !flying) {   // одно облачко из нескольких частиц ОДНИМ пакетом
                    count = L.pcount;
                    dx = L.spread; dy = L.spread; dz = L.spread;
                    speed = L.mspeed;
                }

                Painter.send(viewers, world, L.particle, data, wx, wy, wz, dx, dy, dz, speed, count);
                emitted++;

                // ---- хвост: несколько частиц позади по вектору движения ----
                if (L.trail > 0 && (ownVel || !L.motion.equals("none"))) {
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len > 1e-6) {
                        double ux = dx / len, uy = dy / len, uz = dz / len;
                        for (int k = 1; k <= L.trail; k++) {
                            double d = k * L.tgap;
                            Painter.send(viewers, world, L.particle, data,
                                    wx - ux * d, wy - uy * d, wz - uz * d, dx, dy, dz, speed, count);
                            emitted++;
                        }
                    }
                }
            }
        }
        lastEmitted = emitted;
        return emitted;
    }

    private void motionVector(Layer L, int i, double wx, double wy, double wz, Player near, double[] out) {
        out[0] = 0; out[1] = 0; out[2] = 0;
        String m = L.motion;
        if (m.equals("none")) return;
        double dx = 0, dy = 0, dz = 0;
        if (m.startsWith("vec:")) {
            String[] pr = m.substring(4).split("[,;]");
            try {
                dx = Double.parseDouble(pr[0]);
                dy = pr.length > 1 ? Double.parseDouble(pr[1]) : 0;
                dz = pr.length > 2 ? Double.parseDouble(pr[2]) : 0;
            } catch (Exception ignored) {}
        } else if (m.equals("out") || m.equals("in")) {
            dx = wx - cx; dy = wy - cy; dz = wz - cz;
            if (m.equals("in")) { dx = -dx; dy = -dy; dz = -dz; }
        } else if (m.equals("up")) dy = 1;
        else if (m.equals("down")) dy = -1;
        else if (m.equals("flow")) {
            double a = buf.tx[i], b = buf.ty[i], c = buf.tz[i];
            dx = R[0] * a + U[0] * b + F[0] * c;
            dy = R[1] * a + U[1] * b + F[1] * c;
            dz = R[2] * a + U[2] * b + F[2] * c;
        } else if (m.equals("spin")) {
            double rx = wx - cx, rz = wz - cz;
            dx = -rz; dz = rx;
        } else if (m.equals("to_player") || m.equals("from_player")) {
            if (near == null) return;
            Location e = near.getEyeLocation();
            dx = e.getX() - wx; dy = e.getY() - wy; dz = e.getZ() - wz;
            if (m.equals("from_player")) { dx = -dx; dy = -dy; dz = -dz; }
        } else if (m.equals("look")) { dx = F[0]; dy = F[1]; dz = F[2]; }
        else if (m.equals("random")) {
            dx = buf.seed[i] - 0.5; dy = hash(i, 3) - 0.5; dz = hash(i, 5) - 0.5;
        }
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return;
        out[0] = dx / len; out[1] = dy / len; out[2] = dz / len;
    }

    private static double ease(double x) {
        x = Expr.clamp(x, 0, 1);
        return 1 - Math.pow(1 - x, 3);
    }

    private static double hash(int i, int salt) {
        int h = i * 0x27D4EB2D + salt * 0x165667B1;
        h ^= h >>> 15; h *= 0x2545F491; h ^= h >>> 13;
        return ((h >>> 8) & 0xFFFF) / 65535.0;
    }
}
