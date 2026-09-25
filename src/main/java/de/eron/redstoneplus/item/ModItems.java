package de.eron.redstoneplus.item;

import de.eron.redstoneplus.block.Wireless;
import de.eron.redstoneplus.block.entity.EntityTeleporterBlockEntity;
import de.eron.redstoneplus.entity.FrozenTntEntity;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** All item classes of the mod. Every item shows a translated description line. */
public final class ModItems {
    private ModItems() {
    }

    static void describe(String descriptionId, List<Component> tooltip) {
        tooltip.add(Component.translatable(descriptionId + ".desc").withStyle(ChatFormatting.GRAY));
    }

    public static class DescribedItem extends Item {
        public DescribedItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            describe(this.getDescriptionId(), tooltip);
        }
    }

    public static class DescribedBlockItem extends BlockItem {
        public DescribedBlockItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            describe(this.getBlock().getDescriptionId(), tooltip);
        }
    }

    /**
     * Fernzünder: right click an Entity Teleporter to link (any number of detonators can share one teleporter).
     * Using it releases the next stored entity 10 blocks above the block you are looking at, then it breaks.
     */
    public static class RemoteDetonator extends DescribedItem {
        public static final double RANGE = 256.0;

        public RemoteDetonator(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            if (!level.getBlockState(context.getClickedPos()).is(ModRegistry.ENTITY_TELEPORTER.get())) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                context.getItemInHand().set(ModRegistry.LINK.get(), GlobalPos.of(level.dimension(), context.getClickedPos().immutable()));
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("message.redstoneplus.linked"), true);
                }
                level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            GlobalPos link = stack.get(ModRegistry.LINK.get());
            if (link == null) {
                player.displayClientMessage(Component.translatable("message.redstoneplus.not_linked"), true);
                return InteractionResultHolder.fail(stack);
            }
            ServerLevel teleporterLevel = serverLevel.getServer().getLevel(link.dimension());
            EntityTeleporterBlockEntity teleporter = teleporterLevel == null ? null : EntityTeleporterBlockEntity.at(teleporterLevel, link.pos());
            if (teleporter == null) {
                player.displayClientMessage(Component.translatable("message.redstoneplus.teleporter_missing"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (teleporter.storedCount() == 0) {
                player.displayClientMessage(Component.translatable("message.redstoneplus.teleporter_empty"), true);
                return InteractionResultHolder.fail(stack);
            }
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("message.redstoneplus.no_target"), true);
                return InteractionResultHolder.fail(stack);
            }
            BlockPos target = hit.getBlockPos();
            teleporter.releaseNext(serverLevel, new Vec3(target.getX() + 0.5, target.getY() + 10.0, target.getZ() + 0.5));
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.shrink(1);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            GlobalPos link = stack.get(ModRegistry.LINK.get());
            if (link != null) {
                tooltip.add(Component.translatable("tooltip.redstoneplus.linked_to", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                        link.dimension().location().toString()).withStyle(ChatFormatting.DARK_PURPLE));
            }
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return stack.has(ModRegistry.LINK.get());
        }
    }

    /** Sends a one second pulse to all Wireless Receivers on its channel. Sneak + use changes the channel. */
    public static class RedstoneRemote extends DescribedItem {
        public RedstoneRemote(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            int channel = stack.getOrDefault(ModRegistry.CHANNEL.get(), 0);
            if (level instanceof ServerLevel serverLevel) {
                if (player.isShiftKeyDown()) {
                    channel = (channel + 1) % 16;
                    stack.set(ModRegistry.CHANNEL.get(), channel);
                    player.displayClientMessage(Component.translatable("message.redstoneplus.channel", channel + 1), true);
                } else {
                    Wireless.activate(serverLevel.getServer(), channel, 20);
                    player.displayClientMessage(Component.translatable("message.redstoneplus.remote_sent", channel + 1), true);
                    level.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0F, 1.4F);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            tooltip.add(Component.translatable("message.redstoneplus.channel", stack.getOrDefault(ModRegistry.CHANNEL.get(), 0) + 1)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** Rotates blocks: machines, gates, pistons, observers, dispensers... Sneak rotates backwards. */
    public static class Wrench extends DescribedItem {
        public Wrench(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE
                    || state.hasProperty(BlockStateProperties.BED_PART)
                    || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    || state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED)) {
                return InteractionResult.PASS;
            }
            boolean back = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
            BlockState rotated = null;
            if (state.hasProperty(BlockStateProperties.FACING)) {
                Direction[] all = Direction.values();
                Direction current = state.getValue(BlockStateProperties.FACING);
                Direction next = all[(current.ordinal() + (back ? all.length - 1 : 1)) % all.length];
                rotated = state.setValue(BlockStateProperties.FACING, next);
            } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                Direction current = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                rotated = state.setValue(BlockStateProperties.HORIZONTAL_FACING, back ? current.getCounterClockWise() : current.getClockWise());
            } else if (state.hasProperty(BlockStateProperties.AXIS)) {
                rotated = state.cycle(BlockStateProperties.AXIS);
            }
            if (rotated == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                level.setBlock(pos, rotated, Block.UPDATE_ALL);
                level.neighborChanged(pos, state.getBlock(), null);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.6F);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Turns every frozen TNT within 32 blocks into live TNT. */
    public static class TntActivator extends DescribedItem {
        public TntActivator(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                var frozen = level.getEntitiesOfClass(FrozenTntEntity.class, player.getBoundingBox().inflate(32.0));
                frozen.forEach(FrozenTntEntity::activate);
                player.displayClientMessage(Component.translatable("message.redstoneplus.activated", frozen.size()), true);
                player.getCooldowns().addCooldown(this, 10);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }

    /** Shows the redstone values of the clicked block. */
    public static class Multimeter extends DescribedItem {
        public Multimeter(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            if (!level.isClientSide() && context.getPlayer() != null) {
                BlockPos pos = context.getClickedPos();
                BlockState state = level.getBlockState(pos);
                int incoming = level.getBestNeighborSignal(pos);
                int weak = 0;
                int strong = 0;
                for (Direction dir : Direction.values()) {
                    weak = Math.max(weak, state.getSignal(level, pos, dir));
                    strong = Math.max(strong, state.getDirectSignal(level, pos, dir));
                }
                int comparator = state.hasAnalogOutputSignal() ? state.getAnalogOutputSignal(level, pos) : -1;
                Component message = Component.translatable("message.redstoneplus.multimeter", incoming, weak, strong,
                        comparator < 0 ? "-" : String.valueOf(comparator));
                context.getPlayer().displayClientMessage(message, false);
            }
            return InteractionResult.SUCCESS;
        }
    }
}
