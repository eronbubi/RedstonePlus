package de.eron.redstoneplus.realm.client;

import com.mojang.math.Axis;
import de.eron.redstoneplus.realm.Grid;
import de.eron.redstoneplus.realm.LightCycle;
import de.eron.redstoneplus.realm.Trackwright;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The light cycle's HUD while riding: a minimap of the lightlines around you (north up, the Grid's lattice faint behind,
 * the tiles bright, Trackwrights at work as amber dots, other cycles as red ones, your cycle as the arrow in the middle),
 * and beside it speed, whether the cycle is locked onto a line, the next junction and the ways it offers, the turn you
 * queued, and the warning when the cycle holds still for the Great Bell.
 */
final class CycleHud {
    private static final int RADIUS = 30;
    private static final int SCALE = 2;
    private static final int MAP = RADIUS * 2 * SCALE;
    private static final int BG = 0xC8140606;
    private static final int FRAME = 0xFFFF3A20;
    private static final int LATTICE = 0x30FF7040;
    private static final int TILE = 0xFFFF4A24;
    private static final int TEXT = 0xFFFFD0A0;
    private static final int WARN = 0xFFFF2A10;

    private CycleHud() {
    }

    static void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof LightCycle cycle)) {
            return;
        }
        Font font = mc.font;
        int x0 = 8;
        int y0 = 8;
        double px = Mth.lerp(partialTick, cycle.xo, cycle.getX());
        double pz = Mth.lerp(partialTick, cycle.zo, cycle.getZ());
        int bx = Mth.floor(px);
        int bz = Mth.floor(pz);

        // ---- the map
        g.fill(x0 - 2, y0 - 2, x0 + MAP + 2, y0 + MAP + 2, FRAME);
        g.fill(x0, y0, x0 + MAP, y0 + MAP, BG);
        g.enableScissor(x0, y0, x0 + MAP, y0 + MAP);
        for (int d = -RADIUS; d < RADIUS; d++) {
            int wx = bx + d;
            int wz = bz + d;
            if (Grid.onNorthSouthLine(wx)) {
                g.fill(x0 + (d + RADIUS) * SCALE, y0, x0 + (d + RADIUS) * SCALE + SCALE, y0 + MAP, LATTICE);
            }
            if (Grid.onEastWestLine(wz)) {
                g.fill(x0, y0 + (d + RADIUS) * SCALE, x0 + MAP, y0 + (d + RADIUS) * SCALE + SCALE, LATTICE);
            }
        }
        for (int dz = -RADIUS; dz < RADIUS; dz++) {
            for (int dx = -RADIUS; dx < RADIUS; dx++) {
                if (GridScan.tile(bx + dx, bz + dz)) {
                    int sx = x0 + (dx + RADIUS) * SCALE;
                    int sy = y0 + (dz + RADIUS) * SCALE;
                    g.fill(sx, sy, sx + SCALE, sy + SCALE, TILE);
                }
            }
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            boolean builder = e instanceof Trackwright;
            boolean other = e instanceof LightCycle && e != cycle;
            if (!builder && !other) {
                continue;
            }
            int dx = Mth.floor(e.getX()) - bx;
            int dz = Mth.floor(e.getZ()) - bz;
            if (Math.abs(dx) < RADIUS && Math.abs(dz) < RADIUS) {
                int sx = x0 + (dx + RADIUS) * SCALE;
                int sy = y0 + (dz + RADIUS) * SCALE;
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, builder ? 0xFFFFC040 : 0xFFFF1010);
            }
        }
        // the cycle: an arrow turned the way it rides
        g.pose().pushPose();
        g.pose().translate(x0 + MAP / 2.0F, y0 + MAP / 2.0F, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(partialTick, cycle.yRotO, cycle.getYRot()) + 180.0F));
        g.fill(-1, -5, 2, 4, 0xFFFFFFFF);
        g.fill(-3, -2, 4, 0, 0xFFFFFFFF);
        g.fill(-2, -4, 3, -2, 0xFFFFFFFF);
        g.pose().popPose();
        g.disableScissor();

        // ---- the readout
        int tx = x0 + MAP + 8;
        int ty = y0;
        double bps = cycle.getDeltaMovement().horizontalDistance() * 20.0;
        if (!cycle.isControlledByLocalInstance() || bps < 0.01) {
            bps = Math.abs(cycle.speed()) * 20.0;
        }
        g.drawString(font, Component.translatable("hud.redstoneplus.cycle.speed", Math.round(bps)), tx, ty, TEXT);
        ty += 11;
        g.drawString(font, Component.translatable(cycle.onTrack() ? "hud.redstoneplus.cycle.locked" : "hud.redstoneplus.cycle.free"), tx, ty,
                cycle.onTrack() ? 0xFFFF6A30 : 0xFFB89880);
        ty += 11;
        Direction dir = cycle.trackDirection();
        if (dir != null) {
            String ways = junctionAhead(mc, cycle, dir);
            if (ways != null) {
                g.drawString(font, Component.literal(ways), tx, ty, TEXT);
                ty += 11;
            }
        }
        int queued = cycle.queuedTurn();
        if (queued != 0) {
            g.drawString(font, Component.translatable(queued < 0 ? "hud.redstoneplus.cycle.turn_left" : "hud.redstoneplus.cycle.turn_right"), tx, ty,
                    0xFFFFE070);
            ty += 11;
        }
        if (cycle.holdingForBell()) {
            boolean blink = (mc.level.getGameTime() / 6) % 2 == 0;
            g.drawString(font, Component.translatable("hud.redstoneplus.cycle.bell"), tx, ty, blink ? WARN : 0xFFFFA080);
            ty += 11;
        }
        g.drawString(font, Component.translatable("hud.redstoneplus.cycle.keys", CycleClient.GRID_MAP.getTranslatedKeyMessage()), tx, ty, 0xFF9A7A6A);
    }

    /** "Junction 14: ◀ ▲ ▶" for the next tile ahead with a way off to the side, within 64 tiles. */
    private static String junctionAhead(Minecraft mc, LightCycle cycle, Direction dir) {
        BlockPos pos = BlockPos.containing(cycle.getX(), cycle.getY() - 0.5, cycle.getZ());
        int y = Grid.tileY(mc.level, pos.getX(), pos.getZ(), pos.getY());
        if (y == Integer.MIN_VALUE) {
            return null;
        }
        BlockPos tile = new BlockPos(pos.getX(), y, pos.getZ());
        for (int d = 1; d <= 64; d++) {
            BlockPos next = tile.relative(dir);
            int ny = Grid.tileY(mc.level, next.getX(), next.getZ(), tile.getY());
            if (ny == Integer.MIN_VALUE) {
                return Component.translatable("hud.redstoneplus.cycle.end", d - 1).getString();
            }
            tile = new BlockPos(next.getX(), ny, next.getZ());
            boolean left = Grid.tileY(mc.level, tile.relative(dir.getCounterClockWise()).getX(), tile.relative(dir.getCounterClockWise()).getZ(), ny)
                    != Integer.MIN_VALUE;
            boolean right = Grid.tileY(mc.level, tile.relative(dir.getClockWise()).getX(), tile.relative(dir.getClockWise()).getZ(), ny)
                    != Integer.MIN_VALUE;
            if (left || right) {
                BlockPos ahead = tile.relative(dir);
                boolean straight = Grid.tileY(mc.level, ahead.getX(), ahead.getZ(), ny) != Integer.MIN_VALUE;
                String ways = (left ? "◀ " : "") + (straight ? "▲ " : "") + (right ? "▶" : "");
                return Component.translatable("hud.redstoneplus.cycle.junction", d, ways.trim()).getString();
            }
        }
        return null;
    }
}
