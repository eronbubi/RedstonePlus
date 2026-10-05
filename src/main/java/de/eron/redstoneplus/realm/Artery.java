package de.eron.redstoneplus.realm;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of the Redstone Realm. The realm does not go on forever: it is one giant <b>artery</b> of the world, a vessel
 * of land some eight thousand blocks long, winding through a red void. It swells into chambers (the widest is the Heart,
 * where the Great Cradle stands), sends side vessels off its flanks (one of them is the walled Sealed Reach), and at both
 * ends it dives down into the Blood Below, the sea of molten redstone at the bottom of the void, where it joins the world.
 * Veins of redstone hang from its rims and its underside.
 * <p>
 * This class is plain Java on purpose (no Minecraft types), so the map can be worked out and drawn outside the game.
 * Everything is fixed: every world has the same realm, laid out as a journey from the West Root to the East Root.
 * <p>
 * Terms: {@code s} is how far inside the vessel a point is: 1 on the spine, 0 at the rim, below 0 outside (the void).
 * {@code t} runs 0..1 along a vessel. The side is which flank of the spine a point lies on.
 */
public final class Artery {
    private Artery() {
    }

    // ============================================================================================ the vessels
    /** A vessel: a smooth spine through control points, and its half-width along it. */
    static final class Vessel {
        final String name;
        final double[] xs;
        final double[] zs;
        final double[] ts;
        final double length;
        /** half width at the start and the end; bulges are added on top. */
        final double w0;
        final double w1;
        final double[][] bulges; // {t, extra, sigma}
        final String biome;

        Vessel(String name, double[][] points, double w0, double w1, double[][] bulges, String biome) {
            this.name = name;
            this.w0 = w0;
            this.w1 = w1;
            this.bulges = bulges;
            this.biome = biome;
            // Catmull-Rom through the control points, sampled every few blocks
            List<double[]> out = new ArrayList<>();
            for (int i = 0; i < points.length - 1; i++) {
                double[] p0 = points[Math.max(0, i - 1)];
                double[] p1 = points[i];
                double[] p2 = points[i + 1];
                double[] p3 = points[Math.min(points.length - 1, i + 2)];
                double seg = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
                int steps = Math.max(2, (int) (seg / 16));
                for (int k = 0; k < steps; k++) {
                    double u = k / (double) steps;
                    out.add(new double[]{cr(p0[0], p1[0], p2[0], p3[0], u), cr(p0[1], p1[1], p2[1], p3[1], u)});
                }
            }
            out.add(points[points.length - 1]);
            this.xs = new double[out.size()];
            this.zs = new double[out.size()];
            this.ts = new double[out.size()];
            double len = 0;
            for (int i = 0; i < out.size(); i++) {
                this.xs[i] = out.get(i)[0];
                this.zs[i] = out.get(i)[1];
                if (i > 0) {
                    len += Math.hypot(this.xs[i] - this.xs[i - 1], this.zs[i] - this.zs[i - 1]);
                }
                this.ts[i] = len;
            }
            this.length = len;
            for (int i = 0; i < this.ts.length; i++) {
                this.ts[i] /= len;
            }
        }

        private static double cr(double a, double b, double c, double d, double u) {
            return 0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u * u * u);
        }

        double width(double t) {
            double w = this.w0 + (this.w1 - this.w0) * t;
            for (double[] b : this.bulges) {
                double d = (t - b[0]) / b[2];
                w += b[1] * Math.exp(-d * d);
            }
            // the vessel is alive: its walls swell and narrow in a slow rhythm along its length
            return w * (1.0 + 0.07 * Math.sin(t * this.length / 260.0));
        }

        /** The point of the spine at t. */
        double[] at(double t) {
            int i = 0;
            while (i < this.ts.length - 2 && this.ts[i + 1] < t) {
                i++;
            }
            double f = (t - this.ts[i]) / Math.max(1e-9, this.ts[i + 1] - this.ts[i]);
            f = Math.max(0, Math.min(1, f));
            double x = this.xs[i] + (this.xs[i + 1] - this.xs[i]) * f;
            double z = this.zs[i] + (this.zs[i + 1] - this.zs[i]) * f;
            double dx = this.xs[i + 1] - this.xs[i];
            double dz = this.zs[i + 1] - this.zs[i];
            double l = Math.max(1e-9, Math.hypot(dx, dz));
            // position, then the unit direction of travel
            return new double[]{x, z, dx / l, dz / l};
        }
    }

    /** The trunk, west to east. */
    static final Vessel TRUNK = new Vessel("trunk", new double[][]{
            {-900, 500}, {-300, 100}, {400, -150}, {1100, 300}, {1800, 800}, {2600, 600}, {3300, 0}, {3900, -600}, {4700, -500},
            {5400, 0}, {6000, 300}},
            200, 200, new double[][]{
            {0.10, 90, 0.05},   // the Piston chamber
            {0.26, 110, 0.04},  // the Switchyard
            {0.37, 80, 0.04},   // the Moors
            {0.50, 330, 0.03},  // the Heart
            {0.60, 230, 0.04},  // the Red Sea chamber
            {0.77, 120, 0.04},  // the Kiln chamber
            {0.88, 90, 0.05}}, null);

    /** Side vessels: they leave the trunk and taper to a closed end. */
    static final List<Vessel> BRANCHES = List.of(
            new Vessel("dune_vein", new double[][]{{1000, 260}, {1050, 700}, {800, 1150}, {900, 1550}}, 160, 50, new double[][]{}, "arsenal_dunes"),
            new Vessel("briar_vein", new double[][]{{2500, 620}, {2500, 150}, {2800, -250}, {2700, -650}}, 150, 40, new double[][]{{0.85, 70, 0.08}},
                    "tripwire_briar"),
            new Vessel("mire_vein", new double[][]{{4300, -560}, {4500, -1000}, {4200, -1450}, {4400, -1800}}, 140, 40, new double[][]{}, "vein_mire"),
            // the Sealed Reach: a side vessel that swells into a sac at its end; the Wirewrights walled its mouth
            new Vessel("sealed_reach", new double[][]{{5100, -250}, {5000, 300}, {5200, 800}, {5000, 1250}}, 120, 160,
                    new double[][]{{0.85, 260, 0.12}}, "sealed_reach"));

    static final List<Vessel> ALL = new ArrayList<>();

    static {
        ALL.add(TRUNK);
        ALL.addAll(BRANCHES);
    }

    // ============================================================================================ where the biomes lie
    /** A stretch of the trunk: from t0 to t1, one biome on the left flank and one on the right, and one beneath. */
    record Zone(double t0, double t1, String left, String right, String under) {
    }

    static final Zone[] ZONES = {
            new Zone(0.00, 0.06, "hematite_scarps", "hematite_scarps", "circuit_fossil_beds"),    // the West Root
            new Zone(0.06, 0.20, "piston_karst", "piston_karst", "circuit_fossil_beds"),
            new Zone(0.20, 0.31, "switchyard_flats", "arsenal_dunes", "circuit_fossil_beds"),
            new Zone(0.31, 0.42, "landmark_moors", "red_clay_fen", "resonance_hollows"),
            new Zone(0.42, 0.53, "rubedo_gardens", "rubedo_gardens", "resonance_hollows"),         // the Heart
            new Zone(0.53, 0.65, "sluice_gardens", "tempest_shoals", "resonance_hollows"),        // the Red Sea
            new Zone(0.65, 0.72, "lamplit_grove", "vein_mire", "circuit_fossil_beds"),
            new Zone(0.72, 0.83, "kiln_barrens", "kiln_barrens", "circuit_fossil_beds"),
            new Zone(0.83, 0.94, "oxide_salt_flats", "frostwork_wastes", "circuit_fossil_beds"),
            new Zone(0.94, 1.01, "hematite_scarps", "hematite_scarps", "circuit_fossil_beds")};  // the East Root

    /** The biome outside the vessel: the void, the Blood Below at its bottom. */
    public static final String VOID = "the_abyss";

    // ============================================================================================ the field
    private static final int RES = 16;
    private static int minX;
    private static int minZ;
    private static int nx;
    private static int nz;
    private static float[] sField;
    private static byte[] vesselField;
    private static float[] tField;
    private static byte[] sideField;
    private static volatile boolean ready;

    /** Works the field out (once, about a tenth of a second). */
    static void ensure() {
        if (ready) {
            return;
        }
        synchronized (Artery.class) {
            if (ready) {
                return;
            }
            double lo0 = 1e9;
            double lo1 = 1e9;
            double hi0 = -1e9;
            double hi1 = -1e9;
            for (Vessel v : ALL) {
                for (int i = 0; i < v.xs.length; i++) {
                    lo0 = Math.min(lo0, v.xs[i]);
                    hi0 = Math.max(hi0, v.xs[i]);
                    lo1 = Math.min(lo1, v.zs[i]);
                    hi1 = Math.max(hi1, v.zs[i]);
                }
            }
            int pad = 700;
            minX = (int) Math.floor((lo0 - pad) / RES) * RES;
            minZ = (int) Math.floor((lo1 - pad) / RES) * RES;
            nx = (int) ((hi0 + pad - minX) / RES) + 2;
            nz = (int) ((hi1 + pad - minZ) / RES) + 2;
            float[] s = new float[nx * nz];
            byte[] ves = new byte[nx * nz];
            float[] tt = new float[nx * nz];
            byte[] side = new byte[nx * nz];
            // coarse copies of the spines for the distance search
            List<double[][]> coarse = new ArrayList<>();
            for (Vessel v : ALL) {
                int step = Math.max(1, v.xs.length / Math.max(8, (int) (v.length / 24)));
                List<double[]> pts = new ArrayList<>();
                for (int i = 0; i < v.xs.length; i += step) {
                    pts.add(new double[]{v.xs[i], v.zs[i], v.ts[i]});
                }
                pts.add(new double[]{v.xs[v.xs.length - 1], v.zs[v.zs.length - 1], 1.0});
                coarse.add(pts.toArray(new double[0][]));
            }
            for (int j = 0; j < nz; j++) {
                for (int i = 0; i < nx; i++) {
                    double px = minX + i * RES;
                    double pz = minZ + j * RES;
                    double best = -1e9;
                    int bestV = 0;
                    double bestT = 0;
                    int bestSide = 0;
                    for (int vi = 0; vi < ALL.size(); vi++) {
                        Vessel v = ALL.get(vi);
                        double[][] c = coarse.get(vi);
                        double dBest = 1e18;
                        double tBest = 0;
                        int sBest = 0;
                        for (int k = 0; k < c.length - 1; k++) {
                            double ax = c[k][0];
                            double az = c[k][1];
                            double bx = c[k + 1][0] - ax;
                            double bz = c[k + 1][1] - az;
                            double l2 = bx * bx + bz * bz;
                            double f = l2 < 1e-9 ? 0 : ((px - ax) * bx + (pz - az) * bz) / l2;
                            f = Math.max(0, Math.min(1, f));
                            double qx = ax + bx * f - px;
                            double qz = az + bz * f - pz;
                            double d2 = qx * qx + qz * qz;
                            if (d2 < dBest) {
                                dBest = d2;
                                tBest = c[k][2] + (c[k + 1][2] - c[k][2]) * f;
                                sBest = (bx * (pz - az) - bz * (px - ax)) < 0 ? 0 : 1;
                            }
                        }
                        double sv = 1.0 - Math.sqrt(dBest) / v.width(tBest);
                        if (sv > best) {
                            best = sv;
                            bestV = vi;
                            bestT = tBest;
                            bestSide = sBest;
                        }
                    }
                    int idx = j * nx + i;
                    s[idx] = (float) best;
                    ves[idx] = (byte) bestV;
                    tt[idx] = (float) bestT;
                    side[idx] = (byte) bestSide;
                }
            }
            sField = s;
            vesselField = ves;
            tField = tt;
            sideField = side;
            ready = true;
        }
    }

    // ============================================================================================ asking the field
    /**
     * How far inside the vessel (x, z) lies: 1 on the spine, 0 at the rim, negative outside. The rim is roughened a
     * little so it is never a clean curve.
     */
    public static double s(double x, double z) {
        ensure();
        double gx = (x - minX) / RES;
        double gz = (z - minZ) / RES;
        int i = (int) Math.floor(gx);
        int j = (int) Math.floor(gz);
        if (i < 0 || j < 0 || i >= nx - 1 || j >= nz - 1) {
            return -1.0;
        }
        double fx = gx - i;
        double fz = gz - j;
        double a = sField[j * nx + i] * (1 - fx) + sField[j * nx + i + 1] * fx;
        double b = sField[(j + 1) * nx + i] * (1 - fx) + sField[(j + 1) * nx + i + 1] * fx;
        double v = a * (1 - fz) + b * fz;
        return v + 0.035 * (noise(x / 37.0, z / 37.0, 11) - 0.5) + 0.02 * (noise(x / 11.0, z / 11.0, 12) - 0.5);
    }

    private static int cell(double x, double z) {
        int i = (int) Math.round((x - minX) / RES);
        int j = (int) Math.round((z - minZ) / RES);
        if (i < 0 || j < 0 || i >= nx || j >= nz) {
            return -1;
        }
        return j * nx + i;
    }

    /** Which vessel (x, z) belongs to, and where along it. */
    public static Vessel vessel(double x, double z) {
        ensure();
        int c = cell(x, z);
        return c < 0 ? TRUNK : ALL.get(vesselField[c]);
    }

    public static double t(double x, double z) {
        ensure();
        int c = cell(x, z);
        return c < 0 ? 0 : tField[c];
    }

    /** True on land (with a little margin inside the rim). */
    public static boolean land(double x, double z) {
        return s(x, z) > 0.0;
    }

    /** The biome at (x, z), at a height of y: the void outside, the zone's biome on the land, its deep biome far down. */
    public static String biome(double x, double z, double y) {
        ensure();
        double sv = s(x, z);
        if (sv <= 0.0) {
            return VOID;
        }
        int c = cell(x, z);
        Vessel v = c < 0 ? TRUNK : ALL.get(vesselField[c]);
        // the boundaries between stretches waver, so they never run as straight cuts across the vessel
        double warp = 0.018 * (noise(x / 160.0, z / 160.0, 21) - 0.5) + 0.006 * (noise(x / 40.0, z / 40.0, 22) - 0.5);
        if (v != TRUNK) {
            double tb = (c < 0 ? 0 : tField[c]) + warp;
            if (tb < 0.06) {
                // where a side vessel leaves the trunk it still has the trunk's ground
                v = TRUNK;
            } else {
                return y < -8 && !v.biome.equals("sealed_reach") ? "circuit_fossil_beds" : v.biome;
            }
        }
        double t = trunkT(x, z) + warp;
        Zone zone = ZONES[ZONES.length - 1];
        for (Zone zn : ZONES) {
            if (t < zn.t1) {
                zone = zn;
                break;
            }
        }
        if (y < -8) {
            return zone.under;
        }
        boolean left = sideAt(x, z) == 0;
        // the flanks meet along a wavering line near the spine
        double u = 1.0 - sv;
        if (u < 0.12 + 0.1 * (noise(x / 90.0, z / 90.0, 23) - 0.5)) {
            left = noise(x / 70.0, z / 70.0, 24) < 0.5;
        }
        return left ? zone.left : zone.right;
    }

    /** t along the trunk, even for points that belong to a branch (the branch's mouth). */
    static double trunkT(double x, double z) {
        int c = cell(x, z);
        if (c < 0) {
            return 0;
        }
        return vesselField[c] == 0 ? tField[c] : nearestT(TRUNK, x, z);
    }

    private static int sideAt(double x, double z) {
        int c = cell(x, z);
        return c < 0 ? 0 : sideField[c];
    }

    /** The t of the nearest point of a vessel's spine. */
    static double nearestT(Vessel v, double x, double z) {
        double best = 1e18;
        double bt = 0;
        for (int i = 0; i < v.xs.length; i += 2) {
            double d = (v.xs[i] - x) * (v.xs[i] - x) + (v.zs[i] - z) * (v.zs[i] - z);
            if (d < best) {
                best = d;
                bt = v.ts[i];
            }
        }
        return bt;
    }

    /** A point well inside the land, as near (x, z) as there is one. */
    public static double[] nearestLand(double x, double z) {
        ensure();
        if (s(x, z) > 0.3) {
            return new double[]{x, z};
        }
        double best = 1e18;
        double bx = 0;
        double bz = 0;
        for (Vessel v : ALL) {
            for (int i = 0; i < v.xs.length; i++) {
                double d = (v.xs[i] - x) * (v.xs[i] - x) + (v.zs[i] - z) * (v.zs[i] - z);
                if (d < best && v.ts[i] > 0.05 && v.ts[i] < 0.95) {
                    best = d;
                    bx = v.xs[i];
                    bz = v.zs[i];
                }
            }
        }
        // from the spine, go back towards (x, z) as far as stays well inside
        double dx = x - bx;
        double dz = z - bz;
        double l = Math.hypot(dx, dz);
        if (l > 1) {
            for (double k = l; k > 0; k -= 8) {
                double px = bx + dx / l * k;
                double pz = bz + dz / l * k;
                if (s(px, pz) > 0.35) {
                    return new double[]{px, pz};
                }
            }
        }
        return new double[]{bx, bz};
    }

    /** How far outside the vessel (x, z) lies, in blocks (0 on the land). */
    public static double distanceOutside(double x, double z) {
        double sv = s(x, z);
        if (sv > 0) {
            return 0;
        }
        double[] n = nearestLand(x, z);
        return Math.max(0, Math.hypot(n[0] - x, n[1] - z) - 60);
    }

    /**
     * Near the two roots, the trunk dives down into the Blood Below: 0 for most of the trunk, rising to 1 at the very ends.
     */
    public static double dive(double x, double z) {
        ensure();
        int c = cell(x, z);
        if (c < 0 || vesselField[c] != 0) {
            return 0;
        }
        double t = tField[c];
        double d = t < 0.5 ? (0.05 - t) / 0.05 : (t - 0.95) / 0.05;
        return Math.max(0, Math.min(1, d));
    }

    /** How deep the Red Sea basin is here: 0 outside it, up to 1 in its middle. */
    public static double sea(double x, double z) {
        ensure();
        int c = cell(x, z);
        if (c < 0 || vesselField[c] != 0 || sideField[c] != 1) {
            return 0;
        }
        double t = tField[c];
        double along = 1 - Math.abs(t - 0.60) / 0.045;
        double sv = s(x, z);
        double across = Math.min(1, (1 - sv) * 2.2) * Math.min(1, sv * 4.0);
        double b = Math.max(0, along) * Math.max(0, across);
        return Math.min(1, b * 1.6) * (0.85 + 0.15 * noise(x / 50.0, z / 50.0, 31));
    }

    // ============================================================================================ the fixed places
    /** A place on the trunk: t along it and how far to one flank (in parts of the half width, minus = left). */
    static double[] place(double t, double across) {
        double[] p = TRUNK.at(t);
        double w = TRUNK.width(t);
        // the left normal of the direction of travel
        double nxv = p[3];
        double nzv = -p[2];
        return new double[]{p[0] + nxv * across * w, p[1] + nzv * across * w};
    }

    /** The seats of the five Echoes (in the order of RealmStory.Echo: force, signal, resonance, heat, flow). */
    public static final double[][] SEATS = {
            place(0.14, 0.25), place(0.26, 0.3), place(0.37, 0.3), place(0.77, -0.25), place(0.575, 0.35)};

    /** The Great Cradle, in the middle of the Heart. */
    public static final double[] HEART = place(0.50, 0.0);

    // ============================================================================================ noise
    /** Smooth value noise in 0..1. */
    static double noise(double x, double z, int salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash01(x0, z0, salt);
        double b = hash01(x0 + 1, z0, salt);
        double c = hash01(x0, z0 + 1, salt);
        double d = hash01(x0 + 1, z0 + 1, salt);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    static double hash01(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    static long hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
