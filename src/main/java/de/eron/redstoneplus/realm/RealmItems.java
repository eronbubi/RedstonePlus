package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The counter tools. Each one beats the trap of one biome; some also help against the constructs. */
public final class RealmItems {
    private RealmItems() {
    }

    static void describe(String descriptionId, List<Component> tooltip) {
        tooltip.add(Component.translatable(descriptionId + ".desc").withStyle(ChatFormatting.GRAY));
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    /** Right click a Crusher to jam it for good. Used up. */
    public static class PistonBrace extends ModItems.DescribedItem {
        public PistonBrace(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            if (!state.is(Realm.CRUSHER.get())) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && TrapBlock.disarm(level, pos, state)) {
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
                if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
    }

    /**
     * Right click a Hazard Switch to burn it out. Right click a construct to short-circuit it:
     * it cannot move or use its ability for 5 seconds.
     */
    public static class PulseInjector extends ModItems.DescribedItem {
        public PulseInjector(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            if (!state.is(Realm.HAZARD_SWITCH.get())) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && TrapBlock.disarm(level, pos, state) && context.getPlayer() != null) {
                context.getItemInHand().hurtAndBreak(1, context.getPlayer(), LivingEntity.getSlotForHand(context.getHand()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
            if (!(target instanceof Constructs.Construct construct)) {
                return InteractionResult.PASS;
            }
            if (!player.level().isClientSide()) {
                construct.stun(100);
                player.getCooldowns().addCooldown(this, 60);
                stack.hurtAndBreak(4, player, LivingEntity.getSlotForHand(hand));
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
    }

    /** Right click a Floodgate to disarm it. Sneak + right click any realm trap to switch it between armed and safe. */
    public static class RepeaterWrench extends ModItems.DescribedItem {
        public RepeaterWrench(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            Player player = context.getPlayer();
            if (!(state.getBlock() instanceof TrapBlock)) {
                return InteractionResult.PASS;
            }
            boolean sneaking = player != null && player.isShiftKeyDown();
            if (!sneaking && !state.is(Realm.FLOODGATE.get())) {
                if (player != null && !level.isClientSide()) {
                    say(player, "message.redstoneplus.wrench_sneak");
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!level.isClientSide()) {
                if (state.getValue(TrapBlock.DISARMED)) {
                    TrapBlock.rearm(level, pos, state);
                    if (player != null) {
                        say(player, "message.redstoneplus.trap_armed");
                    }
                } else {
                    TrapBlock.disarm(level, pos, state);
                    if (player != null) {
                        say(player, "message.redstoneplus.trap_disarmed");
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
    }

    /** While held (either hand), realm traps within 10 blocks and Kiln Brute volleys do not fire. */
    public static class SignalJammer extends ModItems.DescribedItem {
        public static final double RANGE = 10.0;

        public SignalJammer(Properties properties) {
            super(properties);
        }

        public static boolean isHolding(Player player) {
            return player.getItemBySlot(EquipmentSlot.MAINHAND).is(Realm.SIGNAL_JAMMER.get())
                    || player.getItemBySlot(EquipmentSlot.OFFHAND).is(Realm.SIGNAL_JAMMER.get());
        }

        public static boolean isJammed(Level level, Vec3 at) {
            for (Player player : level.players()) {
                if (!player.isSpectator() && player.distanceToSqr(at) <= RANGE * RANGE && isHolding(player)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
            if (level instanceof ServerLevel server && entity instanceof Player player && isHolding(player) && level.getGameTime() % 20 == 0
                    && player.getItemBySlot(EquipmentSlot.MAINHAND) == stack) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2, player.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    /**
     * Shears that cut tripwire without setting it off, disarm Volley Launchers on right click
     * and deal extra damage to Spool Weavers.
     */
    public static class InsulatedCutters extends ShearsItem {
        public InsulatedCutters(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            if (state.is(Realm.VOLLEY_LAUNCHER.get())) {
                if (!level.isClientSide() && TrapBlock.disarm(level, pos, state) && context.getPlayer() != null) {
                    level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.2F);
                    context.getItemInHand().hurtAndBreak(1, context.getPlayer(), LivingEntity.getSlotForHand(context.getHand()));
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            return super.useOn(context);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            describe(this.getDescriptionId(), tooltip);
        }
    }
}
