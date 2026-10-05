package de.eron.redstoneplus.realm;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crews: the realm's creatures do not wander about one by one. They work in crews, like the machines they are.
 * <p>
 * A crew has a <b>node</b> (its leader) and members linked to it. The node decides; its orders travel down the links to
 * each member, taking a moment to arrive (the farther the member, the longer), and each member acknowledges. The links
 * and the orders running along them can be seen (see the client's CrewLinks). Members keep their places in a formation
 * that depends on the kind of crew (a wedge for warbands, single file for jackal packs, two columns for mite herds, a ring
 * for the hunters of the Sealed Reach), face the way their node faces, and sweep their gaze round in step.
 * <p>
 * When the crew engages, it encircles its target: each member takes a slot on a ring round it, and only the members
 * holding an attack token close in to strike; the tokens pass round the ring. A badly hurt member drops back behind the
 * node to repair. When the node falls, the crew elects a new one. A crew losing its fight calls nearby creatures of its
 * kind to join it. Crews never change who the creatures will fight: the Concordance (see {@link RealmRules}) still decides.
 */
public final class Crews {
    private Crews() {
    }

    // ============================================================================================ kinds of crews
    public enum Kind {
        /** Constructs and the Machine-Bound: a wedge behind the node. */
        WARBAND(5),
        /** Spark Mites: two neat columns. */
        HERD(6),
        /** Scrap Jackals: single file. */
        PACK(4),
        /** Bellows Hogs and Flesh Presses: single file. */
        WORKCREW(3),
        /** The things of the Sealed Reach: a ring. */
        HUNT(3);

        final int max;

        Kind(int max) {
            this.max = max;
        }
    }

    /** What the crew is doing. */
    public enum State {
        PATROL, ENGAGE, REGROUP, ELECTION, WORK
    }

    /** The orders that run down the links (shown by the client). */
    public enum Order {
        PING, ENGAGE, REGROUP, ELECT, CALL, WORK
    }

    /** Which crew kind a creature belongs to, or null if it does not join crews. */
    @Nullable
    static Kind kindOf(Entity e) {
        if (!(e instanceof PathfinderMob) || e instanceof Slime || e instanceof Echoes.EchoBoss || e instanceof Trackwright
                || e instanceof RealmFauna.LampMoth || !RealmRules.realmCreature(e)) {
            return null;
        }
        if (e instanceof SealedReach.Lawless) {
            return Kind.HUNT;
        }
        if (e instanceof RealmFauna.SparkMite) {
            return Kind.HERD;
        }
        if (e instanceof RealmFauna.ScrapJackal) {
            return Kind.PACK;
        }
        if (e instanceof MachineBound.BellowsHog || e instanceof MachineBound.FleshPress) {
            return Kind.WORKCREW;
        }
        return e instanceof net.minecraft.world.entity.monster.Enemy ? Kind.WARBAND : null;
    }

    // ============================================================================================ a crew
    public static final class Crew {
        private static int nextId = 1;
        final int id = nextId++;
        final Kind kind;
        final ServerLevel level;
        PathfinderMob node;
        final List<PathfinderMob> members = new ArrayList<>();
        State state = State.PATROL;
        long stateSince;
        Order order = Order.PING;
        long orderTick;
        @Nullable
        LivingEntity target;
        int lostTarget;
        int tokenTurn;
        boolean called;
        int strength;

        Crew(Kind kind, PathfinderMob node) {
            this.kind = kind;
            this.level = (ServerLevel) node.level();
            this.node = node;
            this.stateSince = this.level.getGameTime();
            this.orderTick = this.stateSince;
            this.strength = 1;
        }

        int size() {
            return this.members.size() + 1;
        }

        /** Ticks for an order to travel from the node to a member: a tick for every two blocks, at least three. */
        int latency(Mob member) {
            return Math.max(3, (int) (member.distanceTo(this.node) / 2.0));
        }

        boolean received(Mob member) {
            return this.level.getGameTime() >= this.orderTick + this.latency(member);
        }

        void order(Order order) {
            this.order = order;
            this.orderTick = this.level.getGameTime();
        }

        void state(State state, Order order) {
            this.state = state;
            this.stateSince = this.level.getGameTime();
            this.order(order);
        }

        /** The member's place in the formation, in world coordinates. */
        Vec3 slot(PathfinderMob member) {
            int i = this.members.indexOf(member);
            BlockPosWork work = WORK.get(this.node.getUUID());
            if (this.state == State.WORK && work != null) {
                // a ring round the work, everyone facing it
                double radius = 2.2 + member.getBbWidth();
                double a = (i + 1) * Mth.TWO_PI / (this.members.size() + 1) + Math.atan2(this.node.getZ() - work.pos.getZ() - 0.5,
                        this.node.getX() - work.pos.getX() - 0.5);
                return Vec3.atBottomCenterOf(work.pos).add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            }
            if (this.state == State.ENGAGE && this.target != null) {
                // a ring round the target, spread evenly, turning slowly so the ring never stands still
                double radius = this.target.getBbWidth() / 2 + member.getBbWidth() / 2 + 3.0;
                double a = i * Mth.TWO_PI / Math.max(1, this.members.size()) + (this.level.getGameTime() - this.stateSince) * 0.004;
                return this.target.position().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            }
            double spacing = Math.max(2.2, member.getBbWidth() + 1.2);
            double x;
            double z;
            switch (this.kind) {
                case WARBAND -> {
                    int row = i / 2 + 1;
                    x = (i % 2 == 0 ? -1 : 1) * row * spacing;
                    z = row * spacing;
                }
                case HERD -> {
                    x = (i % 2 == 0 ? -0.7 : 0.7) * spacing;
                    z = (i / 2 + 1) * spacing;
                }
                case HUNT -> {
                    double a = (i + 1) * Mth.TWO_PI / (this.members.size() + 1);
                    x = Math.sin(a) * spacing * 1.6;
                    z = Math.cos(a) * spacing * 1.6;
                }
                default -> {
                    x = 0;
                    z = (i + 1) * spacing;
                }
            }
            // a member hurt badly keeps back behind the node
            if (member.getHealth() < member.getMaxHealth() * 0.3F) {
                x *= 0.4;
                z = z + spacing * 1.5;
            }
            float yaw = this.node.yBodyRot * Mth.DEG_TO_RAD;
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);
            // local x is to the node's right, local z behind it
            double wx = -x * cos - z * -sin;
            double wz = -x * sin - z * cos;
            return this.node.position().add(wx, 0, wz);
        }

        /** True for the members allowed to close in and strike right now. */
        boolean holdsToken(Mob member) {
            if (member instanceof RangedAttackMob) {
                return true; // archers shoot from wherever they stand in the ring
            }
            int i = this.members.indexOf(member);
            int n = this.members.size();
            if (i < 0 || n == 0) {
                return true;
            }
            int tokens = Math.min(2, n);
            for (int k = 0; k < tokens; k++) {
                if ((this.tokenTurn + k) % n == i) {
                    return true;
                }
            }
            return false;
        }

        /** Where a member looks while it holds its place: the target, or the way the node faces, swept round in step. */
        float lookYaw(Mob member) {
            long t = this.level.getGameTime() - this.stateSince;
            float sweep = this.state == State.PATROL && this.node.getNavigation().isDone() ? (float) (Math.floorMod(t / 50, 4L) * 90 - 135) * 0.5F : 0.0F;
            return this.node.yBodyRot + sweep;
        }
    }

    // ============================================================================================ work
    /** A node at work on a block, since when. */
    record BlockPosWork(net.minecraft.core.BlockPos pos, long since) {
    }

    private static final Map<UUID, BlockPosWork> WORK = new HashMap<>();

    /**
     * Called by a creature's job while it works a block. Returns how many work ticks this tick is worth: one, plus one for
     * every member of its crew that has taken its place round the work.
     */
    static int working(Mob mob, net.minecraft.core.BlockPos pos) {
        BlockPosWork w = WORK.get(mob.getUUID());
        if (w == null || !w.pos.equals(pos)) {
            WORK.put(mob.getUUID(), new BlockPosWork(pos.immutable(), mob.level().getGameTime()));
        }
        Crew crew = BY_MEMBER.get(mob.getUUID());
        if (crew == null || crew.node != mob || crew.state != State.WORK) {
            return 1;
        }
        int help = 0;
        for (PathfinderMob m : crew.members) {
            if (crew.received(m) && m.position().distanceToSqr(crew.slot(m)) < 2.0 * 2.0) {
                help++;
            }
        }
        return 1 + help;
    }

    /** The job is over (done, or given up). */
    static void stoppedWorking(Mob mob) {
        WORK.remove(mob.getUUID());
    }

    // ============================================================================================ all the crews
    private static final Map<Integer, Crew> CREWS = new HashMap<>();
    private static final Map<UUID, Crew> BY_MEMBER = new HashMap<>();

    @Nullable
    public static Crew crewOf(Entity e) {
        return BY_MEMBER.get(e.getUUID());
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Crews::join);
        MinecraftForge.EVENT_BUS.addListener(Crews::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent e) -> {
            CREWS.clear();
            BY_MEMBER.clear();
            WORK.clear();
            FREE.clear();
        });
    }

    /** Creatures that lost their crew (it fell apart, or they strayed): every few seconds they look for a new one. */
    private static final java.util.Set<PathfinderMob> FREE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob) || !event.getLevel().dimension().equals(Realm.REALM)) {
            return;
        }
        Kind kind = kindOf(mob);
        if (kind == null) {
            return;
        }
        // every new body (a fresh spawn, or one loaded back with its chunk) gets the crew's hold on it
        mob.goalSelector.addGoal(0, new CrewGoal(mob));
        Crew old = BY_MEMBER.get(mob.getUUID());
        if (old != null) {
            // the same creature loaded back before its crew noticed it was gone: it takes its old place
            if (old.node.getUUID().equals(mob.getUUID())) {
                old.node = mob;
            } else {
                old.members.removeIf(m -> m.getUUID().equals(mob.getUUID()));
                old.members.add(mob);
            }
            return;
        }
        assign(mob, kind);
    }

    /** Puts a creature into the nearest crew of its kind with room in it, or makes it the node of a new one. */
    private static void assign(PathfinderMob mob, Kind kind) {
        Crew best = null;
        double bestD = 24 * 24;
        for (Crew crew : CREWS.values()) {
            if (crew.kind != kind || crew.level != mob.level() || crew.size() >= kind.max || !crew.node.isAlive()) {
                continue;
            }
            double d = crew.node.distanceToSqr(mob);
            if (d < bestD) {
                bestD = d;
                best = crew;
            }
        }
        if (best == null) {
            best = new Crew(kind, mob);
            CREWS.put(best.id, best);
        } else {
            best.members.add(mob);
            best.strength = Math.max(best.strength, best.size());
            best.order(Order.PING); // the newcomer is greeted down the links
            // the bigger machine leads
            if (mob.getMaxHealth() > best.node.getMaxHealth() * 1.4F) {
                best.members.remove(mob);
                best.members.add(0, best.node);
                best.node = mob;
            }
        }
        BY_MEMBER.put(mob.getUUID(), best);
        FREE.remove(mob);
    }

    private static void leave(Crew crew, PathfinderMob mob) {
        crew.members.remove(mob);
        BY_MEMBER.remove(mob.getUUID());
        if (mob.isAlive() && !mob.isRemoved()) {
            FREE.add(mob);
        }
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Crew> it = CREWS.values().iterator(); it.hasNext(); ) {
            Crew crew = it.next();
            // members that died or were unloaded leave
            for (PathfinderMob m : new ArrayList<>(crew.members)) {
                if (!m.isAlive() || m.isRemoved() || m.level() != crew.level || m.distanceToSqr(crew.node) > 64 * 64) {
                    leave(crew, m);
                }
            }
            if (!crew.node.isAlive() || crew.node.isRemoved()) {
                BY_MEMBER.remove(crew.node.getUUID());
                if (crew.members.isEmpty()) {
                    it.remove();
                    continue;
                }
                elect(crew);
            }
            think(crew);
        }
        // strays find a crew again
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount() % 100 == 0 && !FREE.isEmpty()) {
            for (PathfinderMob mob : new ArrayList<>(FREE)) {
                Kind kind = kindOf(mob);
                if (!mob.isAlive() || mob.isRemoved() || kind == null || BY_MEMBER.containsKey(mob.getUUID())) {
                    FREE.remove(mob);
                } else {
                    assign(mob, kind);
                }
            }
        }
        long now = 0;
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount() % 1200 == 0) {
            // forget jobs whose creature vanished without finishing them
            long t = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld().getGameTime();
            WORK.values().removeIf(w -> t - w.since > 6000);
        }
        for (ServerLevel level : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            if (level.dimension().equals(Realm.REALM)) {
                now = level.getGameTime();
                if (now % 10 == 0) {
                    sync(level);
                }
            }
        }
    }

    /** The node fell: the strongest member takes over, and everyone stops a moment to follow the election. */
    private static void elect(Crew crew) {
        PathfinderMob next = crew.members.get(0);
        for (PathfinderMob m : crew.members) {
            if (m.getMaxHealth() > next.getMaxHealth()) {
                next = m;
            }
        }
        crew.members.remove(next);
        crew.node = next;
        crew.state(State.ELECTION, Order.ELECT);
        crew.level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 2.0F), next.getX(), next.getY() + next.getBbHeight() + 0.5,
                next.getZ(), 30, 0.3, 1.5, 0.3, 0.0);
    }

    /** The node's decisions, once a tick. */
    private static void think(Crew crew) {
        long now = crew.level.getGameTime();
        long inState = now - crew.stateSince;
        switch (crew.state) {
            case ELECTION -> {
                if (inState > 30) {
                    crew.state(crew.target != null ? State.ENGAGE : State.PATROL, crew.target != null ? Order.ENGAGE : Order.PING);
                }
            }
            case WORK -> {
                BlockPosWork work = WORK.get(crew.node.getUUID());
                LivingEntity seen = sighted(crew);
                if (seen != null) {
                    crew.target = seen;
                    crew.lostTarget = 0;
                    crew.called = false;
                    crew.state(State.ENGAGE, Order.ENGAGE);
                } else if (work == null) {
                    crew.state(State.PATROL, Order.PING); // the job is done: back in formation
                } else if (inState % 20 == 10) {
                    // the members in place work in step with their node: one movement, all together
                    for (PathfinderMob m : crew.members) {
                        if (crew.received(m) && m.position().distanceToSqr(crew.slot(m)) < 2.0 * 2.0) {
                            crew.level.broadcastEntityEvent(m, RealmAnimated.ABILITY_EVENT);
                            Vec3 at = Vec3.atCenterOf(work.pos);
                            Vec3 from = m.getEyePosition();
                            for (int k = 1; k <= 6; k++) {
                                Vec3 p = from.lerp(at, k / 7.0);
                                crew.level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.1F), 0.7F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
            }
            case PATROL, REGROUP -> {
                if (WORK.containsKey(crew.node.getUUID()) && !crew.members.isEmpty()) {
                    crew.state(State.WORK, Order.WORK);
                    return;
                }
                LivingEntity seen = sighted(crew);
                if (seen != null) {
                    crew.target = seen;
                    crew.lostTarget = 0;
                    crew.called = false;
                    crew.state(State.ENGAGE, Order.ENGAGE);
                } else if (crew.state == State.REGROUP && inState > 80) {
                    crew.state(State.PATROL, Order.PING);
                } else if (now - crew.orderTick > 80) {
                    crew.order(Order.PING); // a heartbeat down the links
                }
            }
            case ENGAGE -> {
                LivingEntity t = crew.target;
                if (t == null || !t.isAlive() || t.isRemoved() || t.distanceToSqr(crew.node) > 48 * 48 || !anyoneTargets(crew, t)) {
                    if (++crew.lostTarget > 60) {
                        crew.target = null;
                        crew.state(State.REGROUP, Order.REGROUP);
                        for (PathfinderMob m : crew.members) {
                            if (m.getTarget() == t) {
                                m.setTarget(null);
                            }
                        }
                    }
                    return;
                }
                crew.lostTarget = 0;
                // members that have heard the order take the same target (the Concordance may still forbid it)
                for (PathfinderMob m : crew.members) {
                    if (m.getTarget() != t && crew.received(m) && m.getHealth() >= m.getMaxHealth() * 0.3F) {
                        m.setTarget(t);
                    }
                }
                // the tokens pass round the ring
                if (inState % 50 == 0) {
                    crew.tokenTurn++;
                }
                // losing: call for help, once
                if (!crew.called && (crew.size() * 2 <= crew.strength || crew.node.getHealth() < crew.node.getMaxHealth() * 0.5F)) {
                    crew.called = true;
                    callHelp(crew);
                }
            }
        }
        // a member that drops back repairs itself slowly
        if (now % 20 == 0) {
            for (PathfinderMob m : crew.members) {
                if (m.getHealth() < m.getMaxHealth() * 0.3F) {
                    m.heal(1.0F);
                    crew.level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.6F, 0.2F), 0.9F), m.getX(), m.getY() + m.getBbHeight() * 0.6,
                            m.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
    }

    /** A living target any of the crew has taken (the node first). */
    @Nullable
    private static LivingEntity sighted(Crew crew) {
        LivingEntity t = crew.node.getTarget();
        if (t != null && t.isAlive()) {
            return t;
        }
        for (PathfinderMob m : crew.members) {
            if (m.getTarget() != null && m.getTarget().isAlive()) {
                return m.getTarget();
            }
        }
        return null;
    }

    private static boolean anyoneTargets(Crew crew, LivingEntity t) {
        if (crew.node.getTarget() == t) {
            return true;
        }
        for (PathfinderMob m : crew.members) {
            if (m.getTarget() == t) {
                return true;
            }
        }
        return false;
    }

    /** The node sends a call: creatures of the crew's kind nearby without a crew join it and take its target. */
    private static void callHelp(Crew crew) {
        crew.order(Order.CALL);
        PathfinderMob node = crew.node;
        crew.level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.05F), 2.5F), node.getX(), node.getY() + node.getBbHeight() + 2,
                node.getZ(), 40, 0.15, 3.0, 0.15, 0.0);
        for (PathfinderMob other : crew.level.getEntitiesOfClass(PathfinderMob.class, node.getBoundingBox().inflate(40.0),
                m -> kindOf(m) == crew.kind && !BY_MEMBER.containsKey(m.getUUID()))) {
            crew.members.add(other);
            BY_MEMBER.put(other.getUUID(), crew);
            if (crew.target != null) {
                other.setTarget(crew.target);
            }
            if (crew.size() >= crew.kind.max + 2) {
                break;
            }
        }
    }

    // ============================================================================================ how a member keeps its place
    /**
     * The crew's hold on a member: while it has no attack token (or is in the middle of an election, or regrouping),
     * it goes to its place in the formation or the ring and looks where the crew looks. With a token it is let go, and
     * its own fighting goals take over.
     */
    static final class CrewGoal extends Goal {
        private final PathfinderMob mob;
        private int repath;

        CrewGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private Crew crew() {
            Crew crew = BY_MEMBER.get(this.mob.getUUID());
            return crew != null && crew.node != this.mob ? crew : null;
        }

        private boolean wanted(Crew crew) {
            if (crew.kind == Kind.HERD && (recentlyHurt(this.mob) || recentlyHurt(crew.node))) {
                return false; // a herd under attack scatters; it forms up again afterwards
            }
            if (!crew.received(this.mob)) {
                return crew.state != State.ENGAGE; // until the order arrives it carries on as it was
            }
            return switch (crew.state) {
                case ELECTION, REGROUP, WORK -> true;
                case ENGAGE -> !crew.holdsToken(this.mob) || this.mob.getHealth() < this.mob.getMaxHealth() * 0.3F || this.mob.getTarget() != crew.target;
                case PATROL -> this.mob.getTarget() == null;
            };
        }

        @Override
        public boolean canUse() {
            Crew crew = this.crew();
            if (crew == null || !this.wanted(crew)) {
                return false;
            }
            // in patrol it only steps in once the member has fallen out of place
            return crew.state != State.PATROL || this.mob.position().distanceToSqr(crew.slot(this.mob)) > 2.5 * 2.5 || crew.node.getNavigation().isDone();
        }

        @Override
        public boolean canContinueToUse() {
            Crew crew = this.crew();
            return crew != null && this.wanted(crew);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            Crew crew = this.crew();
            if (crew == null) {
                return;
            }
            Vec3 slot = crew.slot(this.mob);
            double d = this.mob.position().distanceToSqr(slot);
            if (d > 1.2 * 1.2) {
                if (--this.repath <= 0) {
                    this.repath = 8;
                    double speed = crew.state == State.ENGAGE ? 1.15 : d > 64 ? 1.2 : 1.0;
                    this.mob.getNavigation().moveTo(slot.x, slot.y, slot.z, speed);
                }
            } else {
                this.mob.getNavigation().stop();
            }
            if (crew.state == State.ENGAGE && crew.target != null) {
                this.mob.getLookControl().setLookAt(crew.target, 30.0F, 30.0F);
            } else if (crew.state == State.ELECTION) {
                this.mob.getLookControl().setLookAt(crew.node, 30.0F, 30.0F);
            } else if (crew.state == State.WORK && WORK.get(crew.node.getUUID()) != null) {
                Vec3 at = Vec3.atCenterOf(WORK.get(crew.node.getUUID()).pos);
                this.mob.getLookControl().setLookAt(at.x, at.y, at.z);
            } else if (d <= 1.2 * 1.2) {
                // in place: face the way the crew faces, all together
                float yaw = crew.lookYaw(this.mob);
                this.mob.setYRot(Mth.approachDegrees(this.mob.getYRot(), yaw, 12.0F));
                this.mob.yBodyRot = this.mob.getYRot();
                this.mob.yHeadRot = Mth.approachDegrees(this.mob.yHeadRot, yaw, 15.0F);
            }
        }
    }

    private static boolean recentlyHurt(Mob mob) {
        return mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100;
    }

    // ============================================================================================ telling the clients
    private static final SimpleChannel CHANNEL = ChannelBuilder.named(Realm.id("crews")).networkProtocolVersion(1).optional().simpleChannel();

    /** One crew as a client sees it: who the node is, who the members are and how far an order has to go to each. */
    public record CrewView(int id, int kind, int state, int order, long orderTick, int node, int target, int[] members, int[] latency,
                           int[] role) {
    }

    public record CrewPacket(List<CrewView> crews) {
        static void encode(CrewPacket p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.crews.size());
            for (CrewView c : p.crews) {
                buf.writeVarInt(c.id);
                buf.writeByte(c.kind);
                buf.writeByte(c.state);
                buf.writeByte(c.order);
                buf.writeVarLong(c.orderTick);
                buf.writeVarInt(c.node);
                buf.writeVarInt(c.target + 1);
                buf.writeVarInt(c.members.length);
                for (int i = 0; i < c.members.length; i++) {
                    buf.writeVarInt(c.members[i]);
                    buf.writeVarInt(c.latency[i]);
                    buf.writeByte(c.role[i]);
                }
            }
        }

        static CrewPacket decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<CrewView> list = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                int id = buf.readVarInt();
                int kind = buf.readByte();
                int state = buf.readByte();
                int order = buf.readByte();
                long tick = buf.readVarLong();
                int node = buf.readVarInt();
                int target = buf.readVarInt() - 1;
                int m = buf.readVarInt();
                int[] members = new int[m];
                int[] latency = new int[m];
                int[] role = new int[m];
                for (int i = 0; i < m; i++) {
                    members[i] = buf.readVarInt();
                    latency[i] = buf.readVarInt();
                    role[i] = buf.readByte();
                }
                list.add(new CrewView(id, kind, state, order, tick, node, target, members, latency, role));
            }
            return new CrewPacket(list);
        }
    }

    /** Set by the client: what to do with a packet (kept out of this common class). */
    public static java.util.function.Consumer<CrewPacket> clientHandler = p -> {
    };

    static {
        CHANNEL.messageBuilder(CrewPacket.class, 0).encoder(CrewPacket::encode).decoder(CrewPacket::decode)
                .consumerMainThread((packet, context) -> clientHandler.accept(packet)).add();
    }

    /** Roles a member shows: 0 member, 1 holding an attack token, 2 dropping back to repair. */
    private static int role(Crew crew, PathfinderMob m) {
        if (m.getHealth() < m.getMaxHealth() * 0.3F) {
            return 2;
        }
        return crew.state == State.ENGAGE && crew.holdsToken(m) ? 1 : 0;
    }

    private static void sync(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            List<CrewView> near = new ArrayList<>();
            for (Crew crew : CREWS.values()) {
                if (crew.level != level || crew.node.distanceToSqr(player) > 72 * 72) {
                    continue;
                }
                int n = crew.members.size();
                int[] ids = new int[n];
                int[] lat = new int[n];
                int[] roles = new int[n];
                for (int i = 0; i < n; i++) {
                    PathfinderMob m = crew.members.get(i);
                    ids[i] = m.getId();
                    lat[i] = crew.latency(m);
                    roles[i] = role(crew, m);
                }
                near.add(new CrewView(crew.id, crew.kind.ordinal(), crew.state.ordinal(), crew.order.ordinal(), crew.orderTick, crew.node.getId(),
                        crew.target == null ? -1 : crew.target.getId(), ids, lat, roles));
            }
            CHANNEL.send(new CrewPacket(near), PacketDistributor.PLAYER.with(player));
        }
    }
}
