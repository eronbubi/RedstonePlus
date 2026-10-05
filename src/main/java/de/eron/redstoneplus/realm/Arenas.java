package de.eron.redstoneplus.realm;

/**
 * The arenas of the Echoes and of the Overtoll, as plans: for every column of an arena, which block stands at each
 * height. Plain Java on purpose (no Minecraft types), so the arenas can be drawn and checked outside the game;
 * {@link Sanctums} turns the characters into blocks.
 * <p>
 * Heights are relative to the arena's floor ({@code dy = 0} is the floor block, you stand at 1). A column is written into
 * an array indexed {@code dy + DEPTH}: ' ' leaves the world as it is, '.' clears it, every other character is a block
 * (see {@link Sanctums#block}).
 */
public final class Arenas {
    public static final int DEPTH = 24;
    public static final int HEIGHT = 100;
    public static final int SIZE = DEPTH + HEIGHT;

    private Arenas() {
    }

    /** One arena. */
    public interface Plan {
        /** How far the arena reaches from its centre. */
        int radius();

        /** Where the Echo Seal (or, for the Great Cradle, the socket) lies. */
        int sealDy();

        void column(int dx, int dz, char[] out);
    }

    /** The plan for an Echo (0 force, 1 signal, 2 resonance, 3 heat, 4 flow) or, for 5, the Great Cradle. */
    public static Plan of(int kind) {
        return PLANS[kind];
    }

    private static final Plan[] PLANS = {new PressThrone(), new Switchboard(), new BelfryHollow(), new FurnaceCrown(), new SluiceBasin(),
            new GreatCradle()};

    // ============================================================================================ helpers
    static void set(char[] out, int dy, char c) {
        int i = dy + DEPTH;
        if (i >= 0 && i < out.length) {
            out[i] = c;
        }
    }

    static void fill(char[] out, int from, int to, char c) {
        for (int y = from; y <= to; y++) {
            set(out, y, c);
        }
    }

    static long h(int x, int z, int salt) {
        return Artery.hash(x, z, salt);
    }

    static int hmod(int x, int z, int salt, int mod) {
        return (int) Math.floorMod(h(x, z, salt), (long) mod);
    }

    static double angle(int dx, int dz) {
        return Math.atan2(dz, dx);
    }

    /** Signed angular distance from a to b, in -pi..pi. */
    static double angDiff(double a, double b) {
        double d = (a - b) % (Math.PI * 2);
        if (d > Math.PI) {
            d -= Math.PI * 2;
        }
        if (d < -Math.PI) {
            d += Math.PI * 2;
        }
        return d;
    }

    /** True within {@code half} blocks of one of n spokes (the first at angle a0). */
    static boolean onSpoke(int dx, int dz, int n, double a0, double half) {
        double r = Math.hypot(dx, dz);
        double a = angle(dx, dz);
        double step = Math.PI * 2 / n;
        double k = Math.round((a - a0) / step);
        double off = angDiff(a, a0 + k * step);
        return Math.abs(Math.sin(off)) * r <= half && Math.cos(off) > 0;
    }

    /** The nearest of n points on a ring: its offset from (dx, dz), as {ox, oz, index}. */
    static double[] ringPoint(int dx, int dz, int n, double radius, double a0) {
        double step = Math.PI * 2 / n;
        double a = angle(dx, dz);
        int k = (int) Math.round((a - a0) / step);
        double pa = a0 + k * step;
        double px = Math.cos(pa) * radius;
        double pz = Math.sin(pa) * radius;
        return new double[]{dx - px, dz - pz, Math.floorMod(k, n)};
    }

    /** Ground under an arena: foundations below the floor, cleared space above. */
    static void ground(char[] out, int floor, int clearTo, char fillC, char floorC) {
        fill(out, -DEPTH + 4, floor - 1, fillC);
        set(out, floor, floorC);
        fill(out, floor + 1, clearTo, '.');
    }

    /** The rim of an arena: from its edge outwards the floor blends back into the land. */
    static boolean blend(int dx, int dz, int r0, int r1, char[] out) {
        double r = Math.hypot(dx, dz);
        if (r <= r0) {
            return false;
        }
        if (r <= r1) {
            fill(out, -12, 0, 'd');
            set(out, 0, hmod(dx, dz, 9, 5) == 0 ? 'c' : '#');
            fill(out, 1, 6 + (int) ((r - r0) * 2), '.');
        }
        return true;
    }

    // ============================================================================================ the Press Throne (Force)
    /**
     * A sunken octagonal court of karst brick, terraced down from the rim. Twelve colossal hydraulic pistons stand round it,
     * joined by arcaded walls; two raised walkways cross over the court from piston to piston. In the middle a stepped
     * throne with a towering backrest of rusted plate, its veins all running to the seal.
     */
    static final class PressThrone implements Plan {
        public int radius() {
            return 56;
        }

        public int sealDy() {
            return 1;
        }

        public void column(int dx, int dz, char[] out) {
            if (blend(dx, dz, 51, 56, out)) {
                return;
            }
            double r = Math.hypot(dx, dz);
            double a = angle(dx, dz);
            // the court: octagonal, sunk four blocks, terraced back up to the rim
            double oct = Math.max(Math.abs(dx), Math.abs(dz)) * 0.55 + (Math.abs(dx) + Math.abs(dz)) * 0.45 * 0.7071;
            int floor = oct <= 34 ? -4 : oct <= 40 ? -4 + (int) ((oct - 34) * 4 / 6) : 0;
            char floorC;
            if (oct > 33.5 && oct <= 40) {
                floorC = 'C';
            } else if (onSpoke(dx, dz, 8, Math.PI / 8, 0.7) && r > 12) {
                floorC = 'v';
            } else if (Math.abs(r - 20) < 0.6 || Math.abs(r - 28) < 0.6) {
                floorC = hmod(dx, dz, 3, 6) == 0 ? 'O' : 'C';
            } else {
                floorC = (Math.floorDiv(dx, 3) + Math.floorDiv(dz, 3)) % 2 == 0 ? 'k' : 'l';
            }
            ground(out, floor, 46, 'd', floorC);
            // the throne: four steps up from the court, bronze on top
            if (r <= 11) {
                int step = r <= 4 ? 4 : r <= 6 ? 3 : r <= 8.5 ? 2 : 1;
                fill(out, -4, -4 + step, 'k');
                set(out, -4 + step, r <= 4 ? (r <= 2.5 ? 'b' : 'r') : (r > 10.2 || Math.abs(r - 6.5) < 0.5 || Math.abs(r - 8.7) < 0.5) ? 'C' : 'k');
            }
            // the backrest, north of the seat, and its arms
            if (dz >= -12 && dz <= -10 && Math.abs(dx) <= 6) {
                int top = 22 - Math.abs(dx);
                fill(out, -4, top, dx == 0 || Math.abs(dx) == 3 ? 'v' : 'r');
                set(out, top + 1, 'C');
                if (dx == 0) {
                    fill(out, top + 2, top + 4, 'R');
                }
            }
            if (Math.abs(dx) >= 6 && Math.abs(dx) <= 8 && dz >= -10 && dz <= -2) {
                fill(out, -4, 1, 'r');
                set(out, 2, 'C');
            }
            // twelve hydraulic pistons
            double[] p = ringPoint(dx, dz, 12, 44, Math.PI / 12);
            if (Math.abs(p[0]) <= 2.5 && Math.abs(p[1]) <= 2.5) {
                boolean core = Math.abs(p[0]) <= 1.5 && Math.abs(p[1]) <= 1.5;
                for (int y = floor; y <= 24; y++) {
                    char c = y % 6 == 0 ? 'r' : 'k';
                    if (y >= 10 && y <= 18 && !core) {
                        c = '.'; // the ram shows between the cylinder and the head
                    } else if (y >= 10 && y <= 18) {
                        c = (y % 2 == 0) ? 'r' : 'U';
                    }
                    set(out, y, c);
                }
                set(out, 25, 'p');
                if (core && Math.abs(p[0]) < 0.5 && Math.abs(p[1]) < 0.5) {
                    set(out, 26, 'v');
                    set(out, 27, 'T');
                }
            }
            // arcaded walls between the pistons
            if (r >= 46 && r <= 49) {
                double seg = Math.PI * 2 / 12;
                double off = Math.abs(angDiff(a, Math.PI / 12 + Math.round((a - Math.PI / 12) / seg) * seg));
                double along = off * r;
                if (along > 3.0) {
                    // three arches per bay
                    double bay = (off / (seg / 2)); // 0 at the piston, 1 midway
                    double archPos = (bay * 3) % 1.0;
                    int archTop = (int) (6 + 3 * Math.sin(archPos * Math.PI));
                    boolean opening = r < 48.5 && archPos > 0.15 && archPos < 0.85;
                    for (int y = 1; y <= 11; y++) {
                        if (opening && y <= archTop) {
                            set(out, y, '.');
                        } else {
                            set(out, y, y == 11 ? 'C' : y == 7 ? 'r' : 'k');
                        }
                    }
                    if (hmod(dx, dz, 4, 2) == 0 && r > 48) {
                        set(out, 12, 'k');
                    }
                    if (hmod(dx, dz, 5, 23) == 0) {
                        set(out, 12, 'T');
                    }
                }
            }
            // the raised walkways: a cross over the court at height 14, on piers
            boolean walkX = Math.abs(dz) <= 1 && Math.abs(dx) >= 13 && Math.abs(dx) <= 43;
            boolean walkZ = Math.abs(dx) <= 1 && Math.abs(dz) >= 13 && Math.abs(dz) <= 43;
            if (walkX || walkZ) {
                set(out, 14, 'w');
                int along = walkX ? Math.abs(dx) : Math.abs(dz);
                if (along % 10 == 3 || along == 13) {
                    fill(out, floor + 1, 13, 'C');
                }
            }
            if ((Math.abs(dz) == 2 && Math.abs(dx) >= 13 && Math.abs(dx) <= 43) || (Math.abs(dx) == 2 && Math.abs(dz) >= 13 && Math.abs(dz) <= 43)) {
                set(out, 15, 'i');
                if ((Math.abs(dx) + Math.abs(dz)) % 6 == 0) {
                    set(out, 15, 'T');
                }
            }
        }
    }

    // ============================================================================================ the Switchboard (Signal)
    /**
     * A round floor of fossil circuit engraved like a board: traces turning at right angles, vias, and chips with rows of
     * copper pins. Sixteen signal pylons stand round it, their tops joined by a crown ring that hangs cables. In the middle,
     * the raised core: a block of tiles lit by a grid of lamps.
     */
    static final class Switchboard implements Plan {
        public int radius() {
            return 56;
        }

        public int sealDy() {
            return 5;
        }

        public void column(int dx, int dz, char[] out) {
            if (blend(dx, dz, 52, 56, out)) {
                return;
            }
            double r = Math.hypot(dx, dz);
            char floorC = 'f';
            int ci = Math.floorDiv(dx, 8);
            int cj = Math.floorDiv(dz, 8);
            int u = Math.floorMod(dx, 8);
            int v = Math.floorMod(dz, 8);
            boolean chip = hmod(ci, cj, 31, 6) == 0 && r < 42 && r > 12;
            if (chip) {
                floorC = (u == 0 || v == 0) ? '.' : (u == 1 || u == 7 || v == 1 || v == 7) ? 'u' : 'w';
                if ((u == 0 || v == 0) && (u + v) % 2 == 1) {
                    floorC = 'u'; // the pins
                }
                if (floorC == '.') {
                    floorC = 'f';
                }
            } else {
                boolean traceX = v == 4 && hmod(ci, cj, 32, 3) != 0;
                boolean traceZ = u == 4 && hmod(ci, cj, 33, 3) != 0;
                if (traceX && traceZ || (traceX || traceZ) && u == 4 && v == 4) {
                    floorC = 'R'; // a via where traces meet
                } else if (traceX || traceZ) {
                    floorC = 'v';
                }
            }
            if (Math.abs(r - 30) < 0.7 || Math.abs(r - 45) < 0.7) {
                floorC = 'U';
            }
            if (Math.abs(r - 30) < 0.7 && hmod(dx, dz, 34, 9) == 0) {
                floorC = 'O';
            }
            ground(out, 0, 44, 'd', floorC);
            // the core: a raised block of tiles under a grid of lamps, steps on four sides
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            if (m <= 8) {
                fill(out, 1, 3, m == 8 ? 'u' : 'w');
                set(out, 4, m == 8 ? 'U' : (dx % 2 == 0 && dz % 2 == 0) ? 'T' : 'w');
                if (m <= 2) {
                    set(out, 4, 'b');
                }
            }
            int lo = Math.min(Math.abs(dx), Math.abs(dz));
            if (lo <= 1 && m >= 9 && m <= 11) {
                fill(out, 1, 12 - m, 'w');
            }
            // sixteen pylons with cross arms and lightning rods
            double[] p = ringPoint(dx, dz, 16, 49, 0);
            double pd = Math.max(Math.abs(p[0]), Math.abs(p[1]));
            if (pd <= 1.5) {
                fill(out, 1, 4, 'r');
                set(out, 5, 'C');
            }
            if (Math.abs(p[0]) < 0.5 && Math.abs(p[1]) < 0.5) {
                for (int y = 6; y <= 30; y++) {
                    set(out, y, y % 5 == 0 ? 'u' : 'r');
                }
                set(out, 31, 'T');
                set(out, 32, 'j');
            }
            // the crown ring at the pylons' tops, and the cables it hangs
            if (Math.abs(r - 49) < 0.7) {
                set(out, 28, 'r');
                if (hmod(dx, dz, 35, 5) == 0) {
                    fill(out, 23, 27, 'h');
                    set(out, 22, 'T');
                }
            }
            // cross arms on each pylon pointing inwards
            double pa = angle(dx, dz);
            double[] pp = ringPoint(dx, dz, 16, 49, 0);
            double along = Math.hypot(pp[0], pp[1]);
            if (along <= 3.5 && Math.abs(Math.sin(angDiff(pa, Math.round(pa / (Math.PI / 8)) * (Math.PI / 8))) * r) < 0.6) {
                set(out, 26, 'r');
            }
            // the chips stand proud of the board
            if (chip && u != 0 && v != 0) {
                set(out, 1, (u == 1 || u == 7 || v == 1 || v == 7) ? 'u' : (u + v) % 4 == 0 ? 'T' : 'w');
            }
            // eight relay columns round the core
            double[] q = ringPoint(dx, dz, 8, 22, Math.PI / 8);
            double qm = Math.max(Math.abs(q[0]), Math.abs(q[1]));
            if (qm <= 1.0) {
                for (int y = 1; y <= 14; y++) {
                    set(out, y, y % 4 == 0 ? 'U' : 'r');
                }
                set(out, 15, qm < 0.5 ? 'R' : 'C');
                if (qm < 0.5) {
                    set(out, 16, 'T');
                }
            }
            // eight cable buses sagging from the crown ring down to the relays and on to the core
            if (r >= 9 && r <= 49 && onSpoke(dx, dz, 8, Math.PI / 8, 0.7)) {
                double sag = r >= 22 ? 27 - 7 * Math.sin(Math.PI * (r - 22) / 27) : 16 - 9 * (22 - r) / 13.0;
                set(out, (int) Math.round(sag), r < 22 ? 'u' : 'r');
                if (hmod(dx, dz, 36, 7) == 0) {
                    set(out, (int) Math.round(sag) - 1, 'T');
                }
            }
            // the parapet
            if (r >= 52 && r < 53.5) {
                fill(out, 1, 2, 'C');
            }
        }
    }

    // ============================================================================================ the Belfry Hollow (Resonance)
    /**
     * A deep bowl terraced like an amphitheatre, crystal growing on its tiers, a bronze floor at the bottom. Eight open bell
     * towers stand round the rim. Over the middle hangs a great bronze bell, threaded on the giant chain like a bead.
     */
    static final class BelfryHollow implements Plan {
        public int radius() {
            return 56;
        }

        public int sealDy() {
            return -15;
        }

        static int floor(double r) {
            if (r > 50) {
                return 0;
            }
            double f = -16.0 * (1 - (r / 50) * (r / 50));
            return (int) Math.floor(f / 2.0) * 2;
        }

        public void column(int dx, int dz, char[] out) {
            if (blend(dx, dz, 53, 56, out)) {
                return;
            }
            double r = Math.hypot(dx, dz);
            int fl = floor(r);
            boolean edge = floor(r + 1) != fl;
            char floorC = edge ? 'C' : hmod(dx, dz, 41, 13) == 0 ? 'z' : hmod(dx, dz, 42, 3) == 0 ? 'd' : 's';
            if (r <= 8) {
                floorC = Math.abs(r - 3) < 0.6 || Math.abs(r - 6) < 0.6 ? 'v' : 'b';
            }
            ground(out, fl, 52, 'd', floorC);
            if (edge && r > 10) {
                set(out, fl + 1, hmod(dx, dz, 43, 17) == 0 ? 'T' : '.');
            }
            // crystal spikes on the tiers
            if (r > 12 && r < 46 && hmod(dx, dz, 44, 41) == 0) {
                int hgt = 2 + hmod(dx, dz, 45, 4);
                fill(out, fl + 1, fl + hgt, 'z');
            }
            // the bell towers on the rim: open lattices with a bell under the roof
            double[] p = ringPoint(dx, dz, 8, 52, Math.PI / 8);
            int ax = (int) Math.round(Math.abs(p[0]));
            int az = (int) Math.round(Math.abs(p[1]));
            if (ax <= 2 && az <= 2) {
                boolean corner = ax == 2 && az == 2;
                for (int y = 1; y <= 28; y++) {
                    if (corner) {
                        set(out, y, y % 7 == 0 ? 'C' : 'd');
                    } else if (y % 7 == 0) {
                        set(out, y, 'C');
                    }
                }
                fill(out, 29, 30, ax <= 1 && az <= 1 ? 'C' : '.');
                if (ax == 0 && az == 0) {
                    set(out, 22, 'o');
                    set(out, 31, 'T');
                }
            }
            // the great bell over the middle, threaded on the chain: a hollow bronze shell, flared at the lip
            if (r <= 13) {
                for (int y = 30; y <= 46; y++) {
                    double hh = y - 30;
                    double radiusAt = hh <= 1 ? 12.5 : 12 - hh * 0.42;
                    boolean shell = Math.abs(r - radiusAt) < 0.9 || (y == 46 && r <= radiusAt && r >= 3);
                    if (shell) {
                        char c = (y == 33 || y == 34) ? 'v' : 'b';
                        if (hmod(dx, y, 46, 9) == 0 && y > 35) {
                            c = 'v'; // its crack
                        }
                        set(out, y, c);
                    }
                }
            }
            // sixteen crystal obelisks on the middle tier, singing to the bell
            double[] o = ringPoint(dx, dz, 16, 31, 0);
            double om = Math.max(Math.abs(o[0]), Math.abs(o[1]));
            if (om <= 1.0) {
                int top = fl + 7 + (int) o[2] % 3 * 2;
                fill(out, fl + 1, top, om < 0.5 ? 'z' : 'd');
                set(out, top + 1, om < 0.5 ? 'z' : '.');
                if (om < 0.5) {
                    set(out, top + 2, 'T');
                }
            }
            if (r >= 50 && r < 52) {
                fill(out, 1, 2, 'C');
            }
        }
    }

    // ============================================================================================ the Furnace Crown (Heat)
    /**
     * A platform of black brick in a moat of lava, inside a burning wall with six great chimneys. Four bridges cross the
     * moat through gatehouses. In the middle the Crown: eight ribs of brick curving up and in over the seal like the
     * prongs of a crown, spiked with magma, round a ring of braziers.
     */
    static final class FurnaceCrown implements Plan {
        public int radius() {
            return 56;
        }

        public int sealDy() {
            return 3;
        }

        public void column(int dx, int dz, char[] out) {
            if (blend(dx, dz, 51, 56, out)) {
                return;
            }
            double r = Math.hypot(dx, dz);
            int lo = Math.min(Math.abs(dx), Math.abs(dz));
            boolean bridge = lo <= 2;
            if (r <= 34) {
                char floorC = onSpoke(dx, dz, 12, 0, 0.6) && r > 14 ? 'm' : Math.abs(r - 24) < 0.6 ? 'n' : 'x';
                ground(out, 0, 44, 'd', floorC);
            } else if (r <= 42) {
                ground(out, -4, 44, 'n', 'n');
                if (bridge) {
                    set(out, 0, 'x');
                    fill(out, -3, -1, 'n');
                    if (lo == 2) {
                        set(out, 1, 'i');
                    }
                } else {
                    fill(out, -3, -1, 'V');
                }
            } else {
                ground(out, 0, 44, 'd', 'n');
                // the wall, with a band of magma and crenellations; gatehouses where the bridges pass
                boolean gate = lo <= 2;
                for (int y = 1; y <= 12; y++) {
                    set(out, y, gate && y <= 7 ? '.' : y == 6 ? 'm' : 'n');
                }
                if ((dx + dz) % 2 == 0) {
                    set(out, 13, 'n');
                }
                if (lo >= 3 && lo <= 5 && (Math.abs(dx) < 5 || Math.abs(dz) < 5 || true)) {
                    if (lo <= 5 && Math.max(Math.abs(dx), Math.abs(dz)) > 42) {
                        fill(out, 1, 18, lo == 3 ? 'm' : 'n');
                        set(out, 19, 'F');
                    }
                }
            }
            // six chimneys, hollow, banded with magma, fire at the top
            double[] p = ringPoint(dx, dz, 6, 46, Math.PI / 6);
            double pr = Math.hypot(p[0], p[1]);
            if (pr <= 4.3) {
                for (int y = 1; y <= 38; y++) {
                    if (pr > 2.6) {
                        set(out, y, y % 8 == 0 ? 'm' : 'n');
                    } else {
                        set(out, y, pr <= 1.6 ? 'm' : '.');
                    }
                }
                if (pr <= 1.6) {
                    set(out, 39, 'F');
                }
            }
            // the Crown: eight ribs curving up and in over the seal
            if (r >= 3 && r <= 12.5) {
                double a = angle(dx, dz);
                double seg = Math.PI / 4;
                double off = angDiff(a, Math.round(a / seg) * seg);
                if (Math.abs(Math.sin(off)) * r <= 1.0) {
                    double y = 20 * Math.sqrt(Math.max(0, 1 - Math.pow((r - 3) / 9.5, 2)));
                    for (int yy = (int) Math.floor(y - 1.5); yy <= (int) Math.ceil(y + 0.5); yy++) {
                        set(out, Math.max(1, yy), 'x');
                    }
                    if (r < 4.2) {
                        fill(out, (int) y, (int) y + 4, 'm');
                    }
                }
            }
            // the dais and its braziers
            if (r <= 5) {
                fill(out, 1, 2, 'x');
                if (r <= 2) {
                    set(out, 2, 'b');
                }
            }
            if (r > 6 && r <= 7.5) {
                set(out, 1, 'm');
                if (hmod(dx, dz, 51, 4) == 0) {
                    set(out, 2, 'F');
                }
            }
        }
    }

    // ============================================================================================ the Sluice Basin (Flow)
    /**
     * Terraces of copper stepping down into a basin: ten channels of molten redstone run down them into the ring pool at
     * the bottom; an island in the pool holds the seal, reached by three bridges. An arcaded aqueduct circles over the
     * upper terrace, and copper towers stand on the rim.
     */
    static final class SluiceBasin implements Plan {
        public int radius() {
            return 56;
        }

        public int sealDy() {
            return -5;
        }

        static int terrace(double r) {
            return r > 44 ? 0 : r > 36 ? -3 : r > 26 ? -6 : -9;
        }

        public void column(int dx, int dz, char[] out) {
            if (blend(dx, dz, 52, 56, out)) {
                return;
            }
            double r = Math.hypot(dx, dz);
            int t = terrace(r);
            boolean step = terrace(r + 1) != t;
            char floorC = step ? 'U' : hmod(dx, dz, 61, 5) == 0 ? 'u' : '#';
            ground(out, t, 40, 'd', floorC);
            // ten channels running down to the pool
            if (r > 22 && r < 51 && onSpoke(dx, dz, 10, 0, 1.2)) {
                set(out, t, 'M');
                set(out, t - 1, 'U');
            }
            // the ring pool and the island
            if (r > 13 && r <= 22) {
                set(out, -9, 'M');
                set(out, -10, 'M');
                set(out, -11, 'U');
                // three bridges
                if (onSpoke(dx, dz, 3, Math.PI / 2, 1.6)) {
                    set(out, -8, 'u');
                    fill(out, -9, -10, 'U');
                    if (onSpoke(dx, dz, 3, Math.PI / 2, 1.6) && !onSpoke(dx, dz, 3, Math.PI / 2, 0.6)) {
                        set(out, -7, 'i');
                    }
                }
            }
            if (r <= 13) {
                fill(out, -10, -6, r > 12 ? 'U' : 'd');
                set(out, -6, Math.abs(r - 7) < 0.6 ? 'v' : r <= 3 ? 'b' : '#');
                fill(out, -5, 30, '.');
            }
            // the aqueduct over the upper terrace: arches every 12 degrees, a copper channel on top
            if (Math.abs(r - 40) < 1.6) {
                double a = angle(dx, dz);
                double seg = Math.PI * 2 / 30;
                double off = Math.abs(angDiff(a, Math.round(a / seg) * seg)) * r;
                int base = terrace(r);
                if (off < 1.2) {
                    fill(out, base + 1, 9, 'U');
                } else {
                    int archTop = (int) (5 + 3 * Math.cos(off / (seg * r / 2) * Math.PI / 2));
                    fill(out, archTop + 1, 9, 'u');
                }
                set(out, 10, Math.abs(r - 40) < 0.6 ? 'M' : 'U');
            }
            // copper towers on the rim with a lamp
            double[] p = ringPoint(dx, dz, 10, 49, Math.PI / 10);
            if (Math.abs(p[0]) <= 1.5 && Math.abs(p[1]) <= 1.5) {
                fill(out, 1, 16, Math.abs(p[0]) < 0.5 && Math.abs(p[1]) < 0.5 ? 'U' : 'u');
                set(out, 17, Math.abs(p[0]) < 0.5 && Math.abs(p[1]) < 0.5 ? 'T' : 'U');
            }
            if (r >= 51 && r < 52.5) {
                fill(out, 1, 2, 'U');
            }
        }
    }

    // ============================================================================================ the Great Cradle (the Overtoll)
    /**
     * The heart of the artery. A vast plaza ringed by a moat of molten redstone and a colonnade, the five Echo pylons
     * standing round it, and over all of it a cage of six ribs of vessel wall curving up to an open crown. In the middle
     * the Great Cradle: two pillars sixty blocks high whose broken beam once held the Bell, chains hanging from the stumps,
     * and between them the bronze socket where the Heart of the Five calls the Bell down.
     */
    static final class GreatCradle implements Plan {
        public int radius() {
            return 92;
        }

        public int sealDy() {
            return 3;
        }

        /** The look of each Echo's pylon: body, trim. */
        private static final char[][] PYLON = {{'k', 'r'}, {'f', 'u'}, {'d', 'z'}, {'x', 'm'}, {'U', 'u'}};

        public void column(int dx, int dz, char[] out) {
            if (blend(dx, dz, 86, 92, out)) {
                return;
            }
            double r = Math.hypot(dx, dz);
            double a = angle(dx, dz);
            char floorC;
            if (r % 10 < 0.8 && r > 15) {
                floorC = 'C';
            } else if (onSpoke(dx, dz, 5, Math.PI / 2, 1.0) && r > 15 && r < 60) {
                floorC = 'v';
            } else if (r < 22) {
                floorC = hmod(dx, dz, 71, 4) == 0 ? 'b' : 'w';
            } else {
                floorC = ((int) Math.floor(a / (Math.PI / 20)) + (int) (r / 5)) % 2 == 0 ? 'w' : '#';
            }
            ground(out, 0, 96, 'd', floorC);
            // the socket and its dais
            if (r <= 14) {
                fill(out, 1, r <= 12 ? 2 : 1, 'b');
                if (Math.abs(r - 8) < 0.6 || Math.abs(r - 11) < 0.6) {
                    set(out, r <= 12 ? 2 : 1, 'v');
                }
                if (r <= 3) {
                    set(out, 3, 'b');
                }
            }
            // the Great Cradle: two pillars and a broken beam with chains hanging from its stumps
            int ax = Math.abs(dx);
            if (Math.abs(ax - 26) <= 4 && Math.abs(dz) <= 4) {
                boolean face = Math.abs(dz) == 4 || Math.abs(ax - 26) == 4;
                for (int y = 1; y <= 64; y++) {
                    set(out, y, y % 9 == 0 ? 'C' : face && dz == 0 ? 'v' : hmod(dx, y, 72, 7) == 0 ? 'c' : '#');
                }
            }
            if (Math.abs(ax - 26) <= 6 && Math.abs(dz) <= 6 && (Math.abs(ax - 26) > 4 || Math.abs(dz) > 4)) {
                fill(out, 1, 10 - Math.max(Math.abs(ax - 26), Math.abs(dz)), '#');
            }
            if (Math.abs(dz) <= 3 && ax < 26 && ax > 6 + hmod(dx, dz, 73, 3)) {
                fill(out, 58, 63, 'r');
                set(out, 64, 'b');
                if (dz == 0 && (ax == 10 || ax == 15 || ax == 20)) {
                    fill(out, 58 - (ax == 10 ? 30 : ax == 15 ? 20 : 12), 57, 'h');
                }
            }
            // the moat of molten redstone, crossed by five bridges
            if (r >= 68 && r <= 72) {
                boolean bridge = onSpoke(dx, dz, 5, Math.PI / 2 + Math.PI / 5, 2.2);
                if (bridge) {
                    set(out, 0, 'w');
                    if (onSpoke(dx, dz, 5, Math.PI / 2 + Math.PI / 5, 2.2) && !onSpoke(dx, dz, 5, Math.PI / 2 + Math.PI / 5, 1.2)) {
                        set(out, 1, 'i');
                    }
                } else {
                    set(out, 0, 'M');
                    set(out, -1, 'M');
                    set(out, -2, 'd');
                }
            }
            // the five Echo pylons
            double[] p = ringPoint(dx, dz, 5, 60, Math.PI / 2);
            int k = (int) p[2];
            double pm = Math.max(Math.abs(p[0]), Math.abs(p[1]));
            if (pm <= 3.5) {
                char body = PYLON[k][0];
                char trim = PYLON[k][1];
                int top = pm <= 2.5 ? 44 : 6;
                for (int y = 1; y <= top; y++) {
                    set(out, y, y % 8 == 0 ? trim : body);
                }
                if (pm <= 0.6) {
                    fill(out, 7, 43, 'v');
                }
                if (pm > 2.5 || pm <= 1.5) {
                    // the cap: a ring with an empty socket in its middle
                    if (pm > 1.5) {
                        fill(out, 45, 47, trim);
                    } else {
                        set(out, 45, trim);
                        set(out, 46, 'T');
                    }
                }
            }
            // the colonnade, with an architrave
            if (r >= 76 && r <= 80) {
                double seg = Math.PI * 2 / 60;
                double off = Math.abs(angDiff(a, Math.round(a / seg) * seg)) * r;
                if (off < 1.2 && r >= 77 && r <= 79) {
                    fill(out, 1, 17, 'C');
                }
                if (r >= 76.5) {
                    fill(out, 18, 19, '#');
                    if (hmod(dx, dz, 74, 2) == 0) {
                        set(out, 20, 'C');
                    }
                }
            }
            // the cage of ribs over all of it, open at the top
            double ribSeg = Math.PI / 3;
            double ribOff = angDiff(a, Math.round(a / ribSeg) * ribSeg);
            if (r >= 10 && r <= 80 && Math.abs(Math.sin(ribOff)) * r <= 1.8 && Math.cos(ribOff) > 0) {
                double y = 19 + 70 * Math.sqrt(Math.max(0, 1 - (r / 80) * (r / 80)));
                boolean core = Math.abs(Math.sin(ribOff)) * r <= 0.6;
                for (int yy = (int) Math.floor(y - 2); yy <= (int) Math.ceil(y + 1); yy++) {
                    set(out, yy, core && yy == (int) Math.round(y) ? 'v' : 'a');
                }
                if (r > 74) {
                    fill(out, 1, (int) y, 'a');
                }
            }
        }
    }
}
