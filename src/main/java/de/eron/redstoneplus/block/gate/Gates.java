package de.eron.redstoneplus.block.gate;

import de.eron.redstoneplus.redstone.NuclearNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** All concrete logic components. */
public final class Gates {
    private Gates() {
    }

    public enum Logic {
        AND, OR, XOR, NOT, NAND, NOR, XNOR, AMPLIFIER;

        boolean eval(boolean a, boolean b, boolean back) {
            return switch (this) {
                case AND -> a && b;
                case OR -> a || b;
                case XOR -> a ^ b;
                case NOT -> !back;
                case NAND -> !(a && b);
                case NOR -> !(a || b);
                case XNOR -> a == b;
                case AMPLIFIER -> back;
            };
        }
    }

    /** Stateless two-input gate: inputs left and right (NOT and AMPLIFIER read the back). Output 15 to the front. */
    public static class LogicGate extends GateBlock {
        private final Logic logic;

        public LogicGate(Properties properties, Logic logic) {
            super(properties);
            this.logic = logic;
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean a = on(level, pos, left(state));
            boolean b = on(level, pos, right(state));
            boolean back = on(level, pos, back(state));
            return state.setValue(POWERED, this.logic.eval(a, b, back));
        }
    }

    /** Toggles its output on every rising edge at the back. */
    public static class FlipFlop extends GateBlock {
        public FlipFlop(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(INPUT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(INPUT);
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            if (in && !state.getValue(INPUT)) {
                return state.setValue(INPUT, true).setValue(POWERED, !state.getValue(POWERED));
            }
            return state.setValue(INPUT, in);
        }
    }

    /** Left input sets, right input resets (reset wins). */
    public static class RsLatch extends GateBlock {
        public RsLatch(Properties properties) {
            super(properties);
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            if (on(level, pos, right(state))) {
                return state.setValue(POWERED, false);
            }
            if (on(level, pos, left(state))) {
                return state.setValue(POWERED, true);
            }
            return state;
        }
    }

    /** Turns any rising edge into a 2 game tick pulse. */
    public static class PulseLimiter extends GateBlock {
        public PulseLimiter(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(INPUT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(INPUT);
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            if (state.getValue(POWERED)) {
                return state.setValue(POWERED, false).setValue(INPUT, in);
            }
            if (in && !state.getValue(INPUT)) {
                level.scheduleTick(pos, this, 2);
                return state.setValue(POWERED, true).setValue(INPUT, true);
            }
            return state.setValue(INPUT, in);
        }
    }

    /** Keeps the output on for DELAY seconds after the input goes off. */
    public static class PulseExtender extends GateBlock {
        public PulseExtender(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(INPUT, false).setValue(DELAY, 2));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(INPUT, DELAY);
        }

        @Override
        protected boolean hasDelay() {
            return true;
        }

        @Override
        protected Component delayMessage(int value) {
            return Component.translatable("message.redstoneplus.delay_seconds", value);
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            if (in) {
                return state.setValue(INPUT, true).setValue(POWERED, true);
            }
            if (state.getValue(INPUT)) {
                // input just fell: keep the output alive and come back when the time is up
                level.scheduleTick(pos, this, state.getValue(DELAY) * 20);
                return state.setValue(INPUT, false);
            }
            return state.setValue(POWERED, false);
        }
    }

    /** A repeater with 1-20 redstone ticks of delay. */
    public static class Delay extends GateBlock {
        public Delay(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(DELAY, 4));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(DELAY);
        }

        @Override
        protected boolean hasDelay() {
            return true;
        }

        @Override
        protected int reactionDelay(BlockState state) {
            return state.getValue(DELAY) * 2;
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            return state.setValue(POWERED, on(level, pos, back(state)));
        }
    }

    /** Random output on every rising edge. */
    public static class Randomizer extends GateBlock {
        public Randomizer(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(INPUT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(INPUT);
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            if (in && !state.getValue(INPUT)) {
                return state.setValue(INPUT, true).setValue(POWERED, level.getRandom().nextBoolean());
            }
            return state.setValue(INPUT, in);
        }
    }

    /** Counts rising edges at the back (0-15) and outputs the count as signal strength. Left/right resets. */
    public static class Counter extends GateBlock {
        public static final IntegerProperty COUNT = IntegerProperty.create("count", 0, 15);

        public Counter(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(INPUT, false).setValue(COUNT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(INPUT, COUNT);
        }

        @Override
        protected int outputTowards(BlockState state, Direction towards) {
            return towards == state.getValue(FACING) ? state.getValue(COUNT) : 0;
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            int count = state.getValue(COUNT);
            if (on(level, pos, left(state)) || on(level, pos, right(state))) {
                count = 0;
            } else if (in && !state.getValue(INPUT)) {
                count = (count + 1) % 16;
            }
            return state.setValue(INPUT, in).setValue(COUNT, count).setValue(POWERED, count > 0);
        }
    }

    /** Each rising edge at the back moves the output one step: left, front, right, left... */
    public static class Sequencer extends GateBlock {
        public static final IntegerProperty STEP = IntegerProperty.create("step", 0, 2);

        public Sequencer(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(INPUT, false).setValue(STEP, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(INPUT, STEP);
        }

        private static Direction active(BlockState state) {
            return switch (state.getValue(STEP)) {
                case 0 -> left(state);
                case 1 -> state.getValue(FACING);
                default -> right(state);
            };
        }

        @Override
        protected int outputTowards(BlockState state, Direction towards) {
            return towards == active(state) ? 15 : 0;
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            if (in && !state.getValue(INPUT)) {
                return state.setValue(INPUT, true).setValue(POWERED, true).setValue(STEP, (state.getValue(STEP) + 1) % 3);
            }
            return state.setValue(INPUT, in).setValue(POWERED, in);
        }
    }

    /** Two independent lines crossing each other: back to front and left to right. */
    public static class Crossing extends GateBlock {
        public static final BooleanProperty SIDE_POWERED = BooleanProperty.create("side_powered");

        public Crossing(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(SIDE_POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(SIDE_POWERED);
        }

        @Override
        protected int outputTowards(BlockState state, Direction towards) {
            if (towards == state.getValue(FACING)) {
                return state.getValue(POWERED) ? 15 : 0;
            }
            if (towards == right(state)) {
                return state.getValue(SIDE_POWERED) ? 15 : 0;
            }
            return 0;
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            return state.setValue(POWERED, on(level, pos, back(state))).setValue(SIDE_POWERED, on(level, pos, left(state)));
        }
    }

    /** Pulses forever with a half period of DELAY redstone ticks, paused while the back input is powered. */
    public static class Clock extends GateBlock {
        public Clock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(DELAY, 10));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(DELAY);
        }

        @Override
        protected boolean hasDelay() {
            return true;
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            if (on(level, pos, back(state))) {
                return state.setValue(POWERED, false);
            }
            level.scheduleTick(pos, this, state.getValue(DELAY) * 2);
            return state.setValue(POWERED, !state.getValue(POWERED));
        }
    }

    /** Short pulse whenever the back input changes, on and off. */
    public static class EdgeDetector extends GateBlock {
        public EdgeDetector(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(INPUT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(INPUT);
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            if (in != state.getValue(INPUT)) {
                level.scheduleTick(pos, this, 2);
                return state.setValue(INPUT, in).setValue(POWERED, true);
            }
            return state.setValue(POWERED, false);
        }
    }

    public enum Analog { INVERT, ADD, SUBTRACT }

    /** Works with the real signal strengths 0-15 instead of on/off. */
    public static class AnalogGate extends GateBlock {
        private final Analog op;

        public AnalogGate(Properties properties, Analog op) {
            super(properties);
            this.op = op;
            this.registerDefaultState(this.defaultBlockState().setValue(Counter.COUNT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(Counter.COUNT);
        }

        @Override
        protected int outputTowards(BlockState state, Direction towards) {
            return towards == state.getValue(FACING) ? state.getValue(Counter.COUNT) : 0;
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            int back = input(level, pos, back(state));
            int l = input(level, pos, left(state));
            int r = input(level, pos, right(state));
            int out = switch (this.op) {
                case INVERT -> 15 - back;
                case ADD -> l + r;
                case SUBTRACT -> back - Math.max(l, r);
            };
            out = Math.max(0, Math.min(15, out));
            return state.setValue(Counter.COUNT, out).setValue(POWERED, out > 0);
        }
    }

    /**
     * Outputs 15 to the front and keeps every redstone wire connected to its output at full strength
     * for up to {@link NuclearNetwork#MAX_WIRES} wire blocks.
     */
    public static class NuclearRepeater extends GateBlock {
        public NuclearRepeater(Properties properties) {
            super(properties);
        }

        @Override
        protected BlockState update(BlockState state, ServerLevel level, BlockPos pos) {
            boolean in = on(level, pos, back(state));
            if (in) {
                // re-trace the wire network regularly so newly placed dust is picked up
                level.scheduleTick(pos, this, 20);
            }
            return state.setValue(POWERED, in);
        }

        @Override
        protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            super.tick(state, level, pos, random);
            BlockState now = level.getBlockState(pos);
            if (now.is(this)) {
                NuclearNetwork.refresh(level, pos, now.getValue(POWERED) ? pos.relative(now.getValue(FACING)) : null);
            }
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
            if (!level.isClientSide() && !state.is(newState.getBlock())) {
                NuclearNetwork.refresh(level, pos, null);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }

        public static boolean isNuclear(Level level, BlockPos pos) {
            return level.getBlockState(pos).getBlock() instanceof NuclearRepeater;
        }
    }
}
