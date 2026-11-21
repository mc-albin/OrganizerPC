package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.diosesmon.organizerpc.OrganizerPCClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Línea de IVs debajo del nombre de tus Pokémon sacados. Se dibuja al empezar el dibujo del
 * nombre (antes de que cobblemon_wild_extras lo sustituya) con la misma colocación y escala que
 * usa ese nombre: así queda del mismo tamaño y justo una línea por debajo.
 */
public final class PartyNameplates {
    private static int errors;

    private PartyNameplates() {
    }

    /** {@code matrices} ya está en la posición de la entidad (como en renderNameTag). */
    public static void drawUnderLabel(PokemonEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        try {
            var cfg = OrganizerPCClient.config();
            if (cfg == null || !cfg.ivMarksParty || entity.isInvisible()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            Text line = PartyHudMarks.nameTagLine(entity);
            if (line == null) return;
            var dispatcher = mc.getEntityRenderDispatcher();
            double distSq = dispatcher.getSquaredDistanceToCamera(entity);
            if (distSq > 4096.0) return;

            // Misma cuenta que el nombre de Cobblemon / cobblemon_wild_extras.
            double s = Math.min(1.5, Math.max(0.65, MathHelper.map(distSq, -16.0, 96.0, 0.0, 1.0)));
            double t = MathHelper.map(s, 0.65, 1.5, 0.0, 1.0);
            double scaleA = MathHelper.lerp(t, 0.5, 1.0);
            double near = 1.0 - MathHelper.clamp((Math.sqrt(distSq) - 1.5) / 4.5, 0.0, 1.0);
            float y = (float) entity.getBoundingBox().getLengthY() + 0.65F + (float) (near * 0.35);
            double z = s + t + 0.12 + near * 0.3;
            double yOff = t / 2.0 + near * 0.12;
            float scale = (float) (scaleA * (1.0 - near * 0.08));

            matrices.push();
            matrices.translate(0.0, y, 0.0);
            matrices.multiply(dispatcher.getRotation());
            matrices.translate(0.0, yOff, -z);
            matrices.scale(0.025F * scale, -0.025F * scale, scale);
            Matrix4f m = matrices.peek().getPositionMatrix();
            TextRenderer tr = mc.textRenderer;
            float x = -tr.getWidth(line) / 2F;
            int back = (int) (mc.options.getTextBackgroundOpacity(0.25F) * 255F) << 24;
            // El nombre va en y = 0; la línea de IVs justo debajo.
            tr.draw(line, x, 10, 0x20FFFFFF, false, m, consumers, TextRenderer.TextLayerType.SEE_THROUGH, back, light);
            tr.draw(line, x, 10, 0xFFFFFFFF, false, m, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
            matrices.pop();
        } catch (Throwable e) {
            if (errors++ < 3) OrganizerPCClient.LOGGER.error("Línea de IVs bajo el nombre", e);
        }
    }
}
