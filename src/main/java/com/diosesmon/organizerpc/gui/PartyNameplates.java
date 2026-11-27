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
    private static int brazalErrors;
    /** Brazal de cada estadística (como la wiki los escribe: «31 DEF», «0 ATQ»…) → objeto de Cobblemon. */
    private static final java.util.Map<String, String> BRAZALES = java.util.Map.of(
            "PS", "cobblemon:power_weight", "ATQ", "cobblemon:power_bracer", "DEF", "cobblemon:power_belt",
            "ATQ.E", "cobblemon:power_lens", "DEF.E", "cobblemon:power_band", "VEL", "cobblemon:power_anklet");
    private static final java.util.Map<String, net.minecraft.item.ItemStack> STACKS = new java.util.HashMap<>();

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

    /**
     * Crianza de la wiki: holograma sobre el nombre de tu Pokémon sacado. Un cartel con su cruce y el sexo con el que cría
     * («C4 · ♀ HEMBRA · pasa 0 VEL») y, encima, girando, el brazal que le tienes que poner.
     * Va en el mismo lugar que el nombre de Cobblemon, así queda arriba aunque el modelo sea más grande que su caja.
     */
    public static void drawBrazal(PokemonEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || entity.isInvisible() || mc.world == null) return;
            var wiki = com.diosesmon.organizerpc.cobblemon.WikiSync.pairs();
            if (wiki.isEmpty()) return;
            com.cobblemon.mod.common.pokemon.Pokemon mine = null;
            for (var p : com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
                if (p != null && p.getState() instanceof com.cobblemon.mod.common.pokemon.activestate.SentOutState s && s.getEntity() == entity) {
                    mine = p;
                    break;
                }
            }
            if (mine == null) return;
            var mark = wiki.markOf(mine.getUuid());
            if (mark == null || mark.option()) return;   // las opciones no llevan holograma: tú decides en la wiki
            net.minecraft.item.ItemStack stack = null;
            if (!mark.brazal().isBlank()) {
                String id = BRAZALES.get(mark.brazal().substring(mark.brazal().indexOf(' ') + 1).trim());
                if (id != null) stack = STACKS.computeIfAbsent(id, k -> new net.minecraft.item.ItemStack(
                        net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(k))));
            }
            var dispatcher = mc.getEntityRenderDispatcher();
            double distSq = dispatcher.getSquaredDistanceToCamera(entity);
            if (distSq > 1024.0) return;
            float time = (mc.world.getTime() % 360) + mc.getRenderTickCounter().getTickDelta(true);
            int full = net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;

            // Misma colocación que el nombre (drawUnderLabel): el cartel va una línea por encima del nombre
            double s = Math.min(1.5, Math.max(0.65, MathHelper.map(distSq, -16.0, 96.0, 0.0, 1.0)));
            double t = MathHelper.map(s, 0.65, 1.5, 0.0, 1.0);
            double near = 1.0 - MathHelper.clamp((Math.sqrt(distSq) - 1.5) / 4.5, 0.0, 1.0);
            float y = (float) entity.getBoundingBox().getLengthY() + 0.65F + (float) (near * 0.35);
            double z = s + t + 0.12 + near * 0.3;
            double yOff = t / 2.0 + near * 0.12;
            float scale = (float) (MathHelper.lerp(t, 0.5, 1.0) * (1.0 - near * 0.08));

            matrices.push();
            matrices.translate(0.0, y, 0.0);
            matrices.multiply(dispatcher.getRotation());
            matrices.translate(0.0, yOff, -z);

            // Solo el símbolo del sexo que debe salir la cría (♂ azul, ♀ rosa; ★ dorada si es la final), grande y con borde
            String g = glyph(mark);
            int gc = "★".equals(g) ? 0xFFFFD25A : "♀".equals(g) ? 0xFFFF77B4 : "⚲".equals(g) ? 0xFFB07CFF : 0xFF5DADEC;
            TextRenderer tr = mc.textRenderer;
            matrices.push();
            matrices.translate(0.0, (0.025F * scale) * 4F, 0.0);
            matrices.scale(0.05F * scale, -0.05F * scale, scale);
            Matrix4f m = matrices.peek().getPositionMatrix();
            float x = -tr.getWidth(g) / 2F, gy = -8F;
            tr.draw(g, x, gy, 0x40FFFFFF, false, m, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, full);
            for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
                tr.draw(g, x + d[0] * 0.6F, gy + d[1] * 0.6F, 0xFF101215, false, m, consumers, TextRenderer.TextLayerType.NORMAL, 0, full);
            matrices.translate(0, 0, -0.01F);
            tr.draw(g, x, gy, gc, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, full);
            matrices.pop();

            if (stack != null && !stack.isEmpty()) {
                // El brazal, girando sobre el cartel
                matrices.translate(0.0, (0.025F * scale) * 22F + 0.1F * scale + MathHelper.sin(time / 10F) * 0.03F * scale, 0.0);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(time * 4F));
                matrices.scale(0.6F * scale, 0.6F * scale, 0.6F * scale);
                mc.getItemRenderer().renderItem(stack, net.minecraft.client.render.model.json.ModelTransformationMode.GROUND,
                        full, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, matrices, consumers, mc.world, 0);
            }
            matrices.pop();
        } catch (Throwable e) {
            if (brazalErrors++ < 3) OrganizerPCClient.LOGGER.error("Brazal flotante de la crianza", e);
        }
    }

    /** Sexo que debe salir la cría de este cruce: «♂», «♀» o «★» si es tu Pokémon final (datos viejos: el sexo de este Pokémon). */
    static String glyph(com.diosesmon.organizerpc.core.WikiPairs.Mark mark) {
        if (mark.childGender().isBlank() && mark.next().isBlank()) return mark.female() ? "♀" : "♂";
        if (mark.childFinal()) return "★";
        if ("X".equals(mark.childGender())) return "⚲";   // cría sin sexo: sirve de macho o de hembra
        return "F".equals(mark.childGender()) ? "♀" : "♂";
    }
}
