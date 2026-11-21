package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.diosesmon.organizerpc.OrganizerPCClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Línea de IVs debajo del nombre de tus Pokémon sacados. Se dibuja aparte, después de las
 * entidades, para no depender de qué mod pinta el nombre (cobblemon_wild_extras reemplaza el
 * de Cobblemon). Va a la altura del nombre y una línea más abajo.
 */
public final class PartyNameplates {
    private static final double MAX_DIST_SQ = 24 * 24;
    private static int errors;

    private PartyNameplates() {
    }

    public static void render(WorldRenderContext ctx) {
        try {
            var cfg = OrganizerPCClient.config();
            if (cfg == null || !cfg.ivMarksParty) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || mc.world == null) return;
            VertexConsumerProvider consumers = ctx.consumers();
            MatrixStack matrices = ctx.matrixStack();
            if (consumers == null || matrices == null) return;
            Camera camera = ctx.camera();
            Vec3d cam = camera.getPos();
            float delta = ctx.tickCounter().getTickDelta(true);
            TextRenderer tr = mc.textRenderer;
            for (Pokemon p : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
                if (p == null || !(p.getState() instanceof SentOutState s)) continue;
                PokemonEntity e = s.getEntity();
                if (e == null || e.isRemoved() || e.isInvisible() || e.squaredDistanceTo(cam) > MAX_DIST_SQ) continue;
                Text line = PartyHudMarks.nameTagLine(e);
                if (line == null) continue;
                Vec3d pos = e.getLerpedPos(delta);
                double y = e.getBoundingBox().getLengthY() + 0.5;
                matrices.push();
                matrices.translate(pos.x - cam.x, pos.y - cam.y + y, pos.z - cam.z);
                matrices.multiply(camera.getRotation());
                matrices.scale(0.025F, -0.025F, 0.025F);
                Matrix4f m = matrices.peek().getPositionMatrix();
                float x = -tr.getWidth(line) / 2F;
                int back = (int) (mc.options.getTextBackgroundOpacity(0.25F) * 255F) << 24;
                // Una línea debajo del nombre (el nombre va en y = 0).
                tr.draw(line, x, 10, 0xFFFFFFFF, false, m, consumers, TextRenderer.TextLayerType.NORMAL, back,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE);
                matrices.pop();
            }
        } catch (Throwable t) {
            if (errors++ < 3) OrganizerPCClient.LOGGER.error("Línea de IVs bajo el nombre", t);
        }
    }
}
