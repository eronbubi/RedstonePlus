package de.eron.redstoneplus.realm.client;

import com.mojang.math.Axis;
import de.eron.redstoneplus.realm.Grid;
import de.eron.redstoneplus.realm.LightCycle;
import de.eron.redstoneplus.realm.Trackwright;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.BitSet;

/**
 * The Grid Map (default key M): the lightlines of every loaded chunk around you, the lattice of the Grid with a marker on
 * every node (depots, spires, trackworks and bell gates stand there), the Trackwrights out laying new lines, and every
 * light cycle with the way it rides. Scroll to zoom, drag to look around, right click to centre on your cycle again.
 */
public class GridMapScreen extends Screen {
    private static final int[] ZOOMS = {1, 2, 3, 4, 6};
    private int zoom = 2;
    private double centerX;
    private double centerZ;
    private boolean follow = true;

    public GridMapScreen() {
        super(Component.translatable("screen.redstoneplus.grid_map"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xF00C0404);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        GridScan.allowMore(GridScan.MAP_BUDGET);
        Entity self = mc.player.getVehicle() instanceof LightCycle c ? c : mc.player;
        if (this.follow) {
            this.centerX = Mth.lerp(partialTick, self.xo, self.getX());
            this.centerZ = Mth.lerp(partialTick, self.zo, self.getZ());
        }
        int ppb = ZOOMS[this.zoom];
        int left = 10;
        int top = 26;
        int right = this.width - 10;
        int bottom = this.height - 26;
        int midX = (left + right) / 2;
        int midY = (top + bottom) / 2;
        int halfW = (right - left) / 2 / ppb + 1;
        int halfH = (bottom - top) / 2 / ppb + 1;
        int cx = Mth.floor(this.centerX);
        int cz = Mth.floor(this.centerZ);

        g.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFFFF3A20);
        g.fill(left, top, right, bottom, 0xFF140606);
        g.enableScissor(left, top, right, bottom);
        // the lattice of the Grid, and a marker on every node
        for (int wx = cx - halfW; wx <= cx + halfW; wx++) {
            if (Grid.onNorthSouthLine(wx)) {
                int sx = midX + (wx - cx) * ppb;
                g.fill(sx, top, sx + Math.max(1, ppb / 2), bottom, 0x28FF7040);
            }
        }
        for (int wz = cz - halfH; wz <= cz + halfH; wz++) {
            if (Grid.onEastWestLine(wz)) {
                int sy = midY + (wz - cz) * ppb;
                g.fill(left, sy, right, sy + Math.max(1, ppb / 2), 0x28FF7040);
            }
        }
        for (int i = Grid.cell(cx - halfW) - 1; i <= Grid.cell(cx + halfW) + 1; i++) {
            for (int j = Grid.cell(cz - halfH) - 1; j <= Grid.cell(cz + halfH) + 1; j++) {
                int sx = midX + (Grid.line(i) - cx) * ppb;
                int sy = midY + (Grid.line(j) - cz) * ppb;
                int r = Math.max(3, Grid.NODE_R * ppb / 3);
                for (int k = 0; k <= r; k++) {
                    g.fill(sx - (r - k), sy - k, sx + (r - k) + 1, sy - k + 1, 0x60FF9A40);
                    g.fill(sx - (r - k), sy + k, sx + (r - k) + 1, sy + k + 1, 0x60FF9A40);
                }
            }
        }
        // the lightlines that are there
        int loaded = 0;
        for (int chx = (cx - halfW) >> 4; chx <= (cx + halfW) >> 4; chx++) {
            for (int chz = (cz - halfH) >> 4; chz <= (cz + halfH) >> 4; chz++) {
                BitSet bits = GridScan.chunk(chx, chz);
                if (bits == null) {
                    continue;
                }
                loaded++;
                for (int b = bits.nextSetBit(0); b >= 0; b = bits.nextSetBit(b + 1)) {
                    int wx = (chx << 4) + (b & 15);
                    int wz = (chz << 4) + (b >> 4);
                    int sx = midX + (wx - cx) * ppb;
                    int sy = midY + (wz - cz) * ppb;
                    g.fill(sx, sy, sx + ppb, sy + ppb, 0xFFFF4A24);
                }
            }
        }
        // who is out there
        for (Entity e : mc.level.entitiesForRendering()) {
            int sx = midX + (int) Math.round((e.getX() - cx) * ppb);
            int sy = midY + (int) Math.round((e.getZ() - cz) * ppb);
            if (e instanceof Trackwright) {
                g.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFFFC040);
            } else if (e instanceof LightCycle cycle) {
                arrow(g, sx, sy, cycle.getYRot(), cycle == self ? 0xFFFFFFFF : 0xFFFF1A10);
            }
        }
        if (self == mc.player) {
            arrow(g, midX + (int) Math.round((mc.player.getX() - cx) * ppb), midY + (int) Math.round((mc.player.getZ() - cz) * ppb),
                    mc.player.getYRot(), 0xFFFFFFFF);
        }
        g.disableScissor();

        // title, legend and the coordinates under the mouse
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFB070);
        String legend = Component.translatable("screen.redstoneplus.grid_map.legend").getString();
        g.drawString(this.font, legend, left, this.height - 18, 0xFFC8A08A);
        if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
            int wx = cx + Math.floorDiv(mouseX - midX, ppb);
            int wz = cz + Math.floorDiv(mouseY - midY, ppb);
            String at = wx + ", " + wz + (GridScan.tile(wx, wz) ? "  ■ " + Component.translatable("screen.redstoneplus.grid_map.tile").getString() : "");
            g.drawString(this.font, at, right - this.font.width(at), this.height - 18, 0xFFFFD0A0);
        }
        if (loaded == 0) {
            g.drawCenteredString(this.font, Component.translatable("screen.redstoneplus.grid_map.empty"), midX, midY + 20, 0xFFB08070);
        }
    }

    private static void arrow(GuiGraphics g, int x, int y, float yaw, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yaw + 180.0F));
        g.fill(-1, -5, 2, 4, 0xFF000000);
        g.fill(-1, -4, 2, 3, color);
        g.fill(-3, -2, 4, 0, color);
        g.fill(-2, -3, 3, -2, color);
        g.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.zoom = Mth.clamp(this.zoom + (scrollY > 0 ? 1 : -1), 0, ZOOMS.length - 1);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            this.follow = false;
            this.centerX -= dragX / ZOOMS[this.zoom];
            this.centerZ -= dragY / ZOOMS[this.zoom];
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            this.follow = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (CycleClient.GRID_MAP.matches(key, scan)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
