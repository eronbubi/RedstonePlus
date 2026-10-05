package de.eron.redstoneplus.realm;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import de.eron.redstoneplus.RedstonePlus;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidInteractionRegistry;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The realm's liquids. Each has its own effect on whoever wades in, its own way of working on the world around it, and
 * turns into something solid where it meets water, lava or another of them:
 * <ul>
 *     <li><b>Molten Redstone</b>: the realm's blood. Powers redstone like a block of it, quickens and scalds whoever wades
 *     in. Water cools it into Redstone Vein, lava into Realm Redstone Ore; it sets Ember Oil alight.</li>
 *     <li><b>Ember Oil</b>: thick and flammable. Slows you, and burns hot and long when fire reaches it. Water makes slag of
 *     it, lava cinder rock.</li>
 *     <li><b>Rust Brine</b>: the red water of the salt flats. Weakens and starves, eats your armour, and rusts iron it
 *     touches. Water: red clay; lava: hematite.</li>
 *     <li><b>Resonant Ichor</b>: light, amber, singing. It buoys you up, makes you jump high, and its chimes wake sculk
 *     sensors (and the traps on them). Water: salt crust; lava: resonant crystal.</li>
 *     <li><b>Blight Sap</b>: what seeps through the Sealed Reach. Withers and blinds, spreads blight into the ground,
 *     feeds the things of the Reach. Water: blight crust; lava: cinder rock; Dawn Nectar cleanses it.</li>
 *     <li><b>Dawn Nectar</b>: the realm's hope, rare until the realm heals. Mends you, grows everything around it,
 *     cleanses blight, puts out fire. Lava turns it to bell bronze.</li>
 * </ul>
 */
public final class RealmLiquids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, RedstonePlus.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, RedstonePlus.MODID);

    /** Each liquid: its colour (also of its fog), how bright it glows, how thick and heavy it is, and whether you can see through it. */
    public enum Kind {
        MOLTEN_REDSTONE(0xFF2A12, 15, 3000, 4500, 1000, false),
        EMBER_OIL(0xFF8A1A, 4, 900, 3000, 400, false),
        RUST_BRINE(0x8A2A18, 0, 1200, 1500, 300, true),
        RESONANT_ICHOR(0xFFA830, 10, 400, 900, 300, true),
        BLIGHT_SAP(0x5A0A0A, 2, 1500, 5000, 300, false),
        DAWN_NECTAR(0xFFC840, 12, 1000, 1200, 300, true);

        final int color;
        final int light;
        final int density;
        final int viscosity;
        final int temperature;
        public final boolean seeThrough;

        Kind(int color, int light, int density, int viscosity, int temperature, boolean seeThrough) {
            this.color = color;
            this.light = light;
            this.density = density;
            this.viscosity = viscosity;
            this.temperature = temperature;
            this.seeThrough = seeThrough;
        }

        public String id() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }

        public Liquid liquid() {
            return LIQUIDS.get(this);
        }
    }

    /** The registered parts of one liquid. */
    public record Liquid(Kind kind, RegistryObject<FluidType> type, RegistryObject<FlowingFluid> source, RegistryObject<FlowingFluid> flowing,
                         RegistryObject<LiquidBlock> block, RegistryObject<Item> bucket) {
    }

    public static final Map<Kind, Liquid> LIQUIDS = new EnumMap<>(Kind.class);

    static {
        for (Kind kind : Kind.values()) {
            register(kind);
        }
    }

    private RealmLiquids() {
    }

    @SuppressWarnings("unchecked")
    private static void register(Kind kind) {
        String id = kind.id();
        RegistryObject<FluidType> type = TYPES.register(id, () -> new RealmFluidType(kind));
        RegistryObject<FlowingFluid>[] holder = new RegistryObject[2];
        RegistryObject<LiquidBlock>[] block = new RegistryObject[1];
        RegistryObject<Item>[] bucket = new RegistryObject[1];
        java.util.function.Supplier<ForgeFlowingFluid.Properties> props = () -> new ForgeFlowingFluid.Properties(type, holder[0], holder[1])
                .bucket(bucket[0]).block(block[0])
                .tickRate(kind == Kind.RESONANT_ICHOR ? 4 : kind.viscosity >= 3000 ? 20 : 8)
                .slopeFindDistance(kind.viscosity >= 3000 ? 2 : 4)
                .levelDecreasePerBlock(kind.viscosity >= 3000 ? 2 : 1);
        holder[0] = FLUIDS.register(id, () -> new ForgeFlowingFluid.Source(props.get()));
        holder[1] = FLUIDS.register(id + "_flowing", () -> new ForgeFlowingFluid.Flowing(props.get()));
        block[0] = Realm.BLOCKS.register(id, () -> new RealmLiquidBlock(holder[0], kind, BlockBehaviour.Properties.of()
                .mapColor(kind == Kind.DAWN_NECTAR || kind == Kind.RESONANT_ICHOR ? MapColor.GOLD : kind == Kind.EMBER_OIL ? MapColor.COLOR_ORANGE
                        : MapColor.FIRE)
                .replaceable().noCollission().strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid()
                .sound(SoundType.EMPTY).lightLevel(s -> kind.light).randomTicks()));
        bucket[0] = Realm.item(id + "_bucket", p -> new BucketItem(holder[0], p),
                () -> new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
        LIQUIDS.put(kind, new Liquid(kind, type, holder[0], holder[1], block[0], bucket[0]));
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(RealmLiquids::interactions));
    }

    private static FluidType type(Kind kind) {
        return LIQUIDS.get(kind).type().get();
    }

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    /** What each liquid turns into where it meets another (the liquid named first is the one that is replaced). */
    private static void interactions() {
        FluidType water = net.minecraftforge.common.ForgeMod.WATER_TYPE.get();
        FluidType lava = net.minecraftforge.common.ForgeMod.LAVA_TYPE.get();
        add(Kind.MOLTEN_REDSTONE, water, s(Realm.REDSTONE_VEIN.get()), s(Realm.FOSSIL_CIRCUIT.get()));
        add(Kind.MOLTEN_REDSTONE, lava, s(Realm.REALM_REDSTONE_ORE.get()), s(Realm.CINDER_ROCK.get()));
        add(Kind.EMBER_OIL, water, s(Realm.SLAG.get()), s(Realm.SLAG.get()));
        add(Kind.EMBER_OIL, lava, s(Realm.CINDER_ROCK.get()), s(Blocks.FIRE));
        add(Kind.EMBER_OIL, type(Kind.MOLTEN_REDSTONE), s(Blocks.FIRE), s(Blocks.FIRE));
        add(Kind.RUST_BRINE, water, s(Realm.RED_CLAY.get()), s(Realm.RED_CLAY.get()));
        add(Kind.RUST_BRINE, lava, s(Realm.HEMATITE.get()), s(Realm.DARK_HEMATITE.get()));
        add(Kind.RESONANT_ICHOR, water, s(Realm.SALT_CRUST.get()), s(Realm.SALT_CRUST.get()));
        add(Kind.RESONANT_ICHOR, lava, s(Realm.RESONANT_CRYSTAL.get()), s(Realm.SALT_CRUST.get()));
        add(Kind.BLIGHT_SAP, water, s(Realm.BLIGHT_CRUST.get()), s(Realm.BLIGHT_CRUST.get()));
        add(Kind.BLIGHT_SAP, lava, s(Realm.CINDER_ROCK.get()), s(Realm.CINDER_ROCK.get()));
        add(Kind.BLIGHT_SAP, type(Kind.DAWN_NECTAR), s(Realm.HEATHER_TURF.get()), s(Realm.HEATHER_TURF.get()));
        add(Kind.DAWN_NECTAR, lava, s(Realm.BELL_BRONZE.get()), s(Realm.REALMSTONE_BRICKS.get()));
    }

    private static void add(Kind kind, FluidType other, BlockState fromSource, BlockState fromFlowing) {
        FluidInteractionRegistry.addInteraction(type(kind), new FluidInteractionRegistry.InteractionInformation(other,
                state -> state.isSource() ? fromSource : fromFlowing));
    }

    // ============================================================================================ the fluid types
    /** The physics of a liquid, its sounds, and (on the client) its textures and fog. */
    static final class RealmFluidType extends FluidType {
        private final Kind kind;

        RealmFluidType(Kind kind) {
            super(FluidType.Properties.create().descriptionId("fluid_type.redstoneplus." + kind.id()).lightLevel(kind.light)
                    .density(kind.density).viscosity(kind.viscosity).temperature(kind.temperature)
                    .motionScale(kind.viscosity >= 3000 ? 0.004 : kind == Kind.RESONANT_ICHOR ? 0.02 : 0.012)
                    .canSwim(kind != Kind.MOLTEN_REDSTONE && kind != Kind.BLIGHT_SAP).canDrown(true)
                    .canExtinguish(kind == Kind.DAWN_NECTAR || kind == Kind.RUST_BRINE || kind == Kind.RESONANT_ICHOR)
                    .fallDistanceModifier(kind == Kind.RESONANT_ICHOR ? 0.0F : 0.5F)
                    .canConvertToSource(kind == Kind.RUST_BRINE || kind == Kind.DAWN_NECTAR)
                    .supportsBoating(kind == Kind.RUST_BRINE || kind == Kind.DAWN_NECTAR || kind == Kind.RESONANT_ICHOR)
                    .sound(SoundActions.BUCKET_FILL, kind.viscosity >= 3000 ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, kind.viscosity >= 3000 ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY));
            this.kind = kind;
        }

        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            // FluidType's constructor calls this before this.kind is set, so kind is only read later, when the game asks
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return Realm.id("block/" + kind.id() + "_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return Realm.id("block/" + kind.id() + "_flow");
                }

                @Override
                public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount,
                                               Vector3f fluidFogColor) {
                    return new Vector3f(((kind.color >> 16) & 255) / 255.0F, ((kind.color >> 8) & 255) / 255.0F, (kind.color & 255) / 255.0F);
                }

                @Override
                public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance,
                                            float farDistance, FogShape shape) {
                    // under a liquid you can see this far: a little in the clear ones, almost nothing in the thick ones
                    float far = kind.seeThrough ? 12.0F : kind == Kind.MOLTEN_REDSTONE ? 2.5F : 4.0F;
                    RenderSystem.setShaderFogStart(kind.seeThrough ? 0.5F : 0.0F);
                    RenderSystem.setShaderFogEnd(far);
                }
            });
        }
    }

    // ============================================================================================ the liquid blocks
    /** A liquid block that does something to whoever is in it and to the world around it. */
    public static class RealmLiquidBlock extends LiquidBlock {
        private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.05F), 1.0F);
        public final Kind kind;

        public RealmLiquidBlock(java.util.function.Supplier<? extends FlowingFluid> fluid, Kind kind, Properties properties) {
            super(fluid, properties);
            this.kind = kind;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return true;
        }

        // ---- Molten Redstone carries a signal
        @Override
        protected boolean isSignalSource(BlockState state) {
            return this.kind == Kind.MOLTEN_REDSTONE;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            if (this.kind != Kind.MOLTEN_REDSTONE) {
                return 0;
            }
            int lvl = Math.min(7, state.getValue(LEVEL));
            return Math.max(0, 15 - lvl * 2);
        }

        // ---- Ember Oil burns
        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return this.kind == Kind.EMBER_OIL ? 200 : 0;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return this.kind == Kind.EMBER_OIL ? 60 : 0;
        }

        @Override
        public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return this.kind == Kind.EMBER_OIL;
        }

        // ---- whoever wades in
        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            super.entityInside(state, level, pos, entity);
            if (level.isClientSide()) {
                return;
            }
            if (this.kind == Kind.RESONANT_ICHOR) {
                // buoyant: it lifts whatever is in it
                var v = entity.getDeltaMovement();
                if (v.y < 0.35) {
                    entity.setDeltaMovement(v.x, Math.min(0.35, v.y + 0.07), v.z);
                    entity.hurtMarked = true;
                }
                entity.resetFallDistance();
            }
            if (!(entity instanceof LivingEntity living) || entity.tickCount % 10 != 0) {
                return;
            }
            switch (this.kind) {
                case MOLTEN_REDSTONE -> {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true));
                    living.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, false, true));
                    if (entity.tickCount % 20 == 0 && !living.fireImmune()) {
                        living.hurt(level.damageSources().hotFloor(), 2.0F);
                    }
                }
                case EMBER_OIL -> {
                    // thick going, but it coats you: for a while after, fire cannot touch you
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
                    living.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0, false, true));
                }
                case RUST_BRINE -> {
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
                    living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0, false, true));
                    if (entity.tickCount % 40 == 0) {
                        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                                EquipmentSlot.MAINHAND}) {
                            ItemStack armor = living.getItemBySlot(slot);
                            if (!armor.isEmpty() && armor.isDamageableItem()) {
                                armor.hurtAndBreak(1, living, slot);
                            }
                        }
                    }
                }
                case RESONANT_ICHOR -> living.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 2, false, true));
                case BLIGHT_SAP -> {
                    if (RealmRules.lawless(living)) {
                        living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false)); // it feeds them
                    } else {
                        living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true));
                        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true));
                        living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, true));
                    }
                }
                case DAWN_NECTAR -> {
                    living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
                    living.clearFire();
                    if (living instanceof Player player && entity.tickCount % 40 == 0) {
                        player.getFoodData().eat(1, 0.5F);
                    }
                }
            }
        }

        // ---- what it does to the world around it
        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            super.randomTick(state, level, pos, random);
            BlockPos near = pos.relative(Direction.getRandom(random));
            BlockState other = level.getBlockState(near);
            switch (this.kind) {
                case MOLTEN_REDSTONE -> {
                    // redstone crystals grow on the rock at its shores
                    BlockPos above = near.above();
                    if (state.getFluidState().isSource() && random.nextInt(6) == 0 && other.isSolid() && !other.is(this)
                            && level.getBlockState(above).isAir()) {
                        level.setBlock(above, Realm.REDSTONE_CLUSTER.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                case EMBER_OIL -> {
                    // fire, lava, magma or a burning campfire nearby sets it alight
                    if (other.is(Blocks.FIRE) || other.is(Blocks.LAVA) || other.is(Blocks.MAGMA_BLOCK) || other.is(Blocks.CAMPFIRE)
                            && other.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                        BlockPos above = pos.above();
                        if (level.getBlockState(above).isAir()) {
                            level.setBlock(above, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
                case RUST_BRINE -> {
                    // iron rusts away
                    if (other.is(Blocks.IRON_BLOCK)) {
                        level.setBlock(near, Realm.RUST_PLATING.get().defaultBlockState(), Block.UPDATE_ALL);
                    } else if (other.is(Blocks.IRON_BARS) || other.is(Blocks.CHAIN) || other.is(Blocks.RAIL) || other.is(Blocks.IRON_TRAPDOOR)) {
                        level.destroyBlock(near, false);
                        level.sendParticles(RED, near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
                    }
                }
                case RESONANT_ICHOR -> {
                    // it chimes, and the chime is a vibration sculk sensors (and the traps wired to them) hear
                    if (random.nextInt(3) == 0) {
                        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.6F + random.nextFloat() * 0.8F);
                        level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(state));
                    }
                }
                case BLIGHT_SAP -> {
                    if (RealmStory.healed(level)) {
                        // the realm is healed: the sap turns to nectar
                        level.setBlock(pos, RealmLiquids.Kind.DAWN_NECTAR.liquid().block().get().defaultBlockState()
                                .setValue(LEVEL, state.getValue(LEVEL)), Block.UPDATE_ALL);
                    } else if (other.is(Grid.PAVABLE) && !other.is(Realm.BLIGHT_CRUST.get()) && !other.is(Realm.CINDER_ROCK.get())) {
                        level.setBlock(near, Realm.BLIGHT_CRUST.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                case DAWN_NECTAR -> {
                    if (other.is(Realm.BLIGHT_CRUST.get())) {
                        level.setBlock(near, Realm.HEATHER_TURF.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                    // everything that grows around it grows
                    BlockPos plant = pos.offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
                    BlockState ps = level.getBlockState(plant);
                    if (ps.getBlock() instanceof BonemealableBlock grow && grow.isValidBonemealTarget(level, plant, ps)
                            && grow.isBonemealSuccess(level, random, plant, ps)) {
                        grow.performBonemeal(level, random, plant, ps);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, plant.getX() + 0.5, plant.getY() + 0.5, plant.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                    }
                }
            }
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            super.animateTick(state, level, pos, random);
            if (!level.getBlockState(pos.above()).isAir() || random.nextInt(4) != 0) {
                return;
            }
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 0.95;
            double z = pos.getZ() + random.nextDouble();
            switch (this.kind) {
                case MOLTEN_REDSTONE -> level.addParticle(RED, x, y, z, 0, 0.02, 0);
                case EMBER_OIL -> level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.01, 0);
                case RESONANT_ICHOR -> level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.03, 0);
                case BLIGHT_SAP -> level.addParticle(ParticleTypes.ASH, x, y, z, 0, 0.0, 0);
                case DAWN_NECTAR -> level.addParticle(ParticleTypes.WAX_ON, x, y, z, 0, 0.02, 0);
                default -> {
                }
            }
        }
    }
}
