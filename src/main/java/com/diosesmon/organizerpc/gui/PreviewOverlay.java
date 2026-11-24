package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot;
import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.StatKey;
import com.diosesmon.organizerpc.core.plan.Plan;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Vista previa dentro de la propia caja de la PC: la caja muestra los Pokémon que tendrá
 * tras mover (con el render original de Cobblemon). Los que llegan llevan borde verde y la
 * caja de origen; el panel derecho lista también los que se van.
 */
final class PreviewOverlay {
    private PreviewOverlay() {
    }

    private static UUID after(Plan plan, int box, int slot) {
        return box < plan.after().boxCount() ? plan.after().at(box, slot) : null;
    }

    private static boolean multi(Plan.Entry e) {
        return e != null && PanelState.previewKind != PanelState.PreviewKind.MOVE && e.matchingCategoryIds().size() > 1;
    }

    static void render(OrganizerPanel p, DrawContext ctx, float delta) {
        Plan plan = PanelState.preview;
        if (plan == null) return;
        int[] g = p.gridRect();
        if (g == null) return;
        int box = p.currentBox();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int size = StorageSlot.SIZE;
        Ui ui = new Ui(Ui.Mode.DRAW, ctx, -1, -1);

        // Tapar la caja real y dibujar cómo quedará.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        ui.box(g[0] - 3, g[1] - 3, g[2] + 6, g[3] + 6, 0xF2121519, Ui.GOLD);
        for (BoxStorageSlot s : p.boxSlots()) {
            ui.round(s.getX() + 1, s.getY() + 1, size - 2, size - 2, 0xFF1A1E24);
            UUID u = after(plan, box, s.getPosition().getSlot());
            if (u != null) PokemonIcon.draw(ctx, p.gui.getStorage(), p.pokemon(u), s.getX(), s.getY(), delta);
        }

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 450);
        PlanIndex idx = PlanIndex.of(plan);
        for (BoxStorageSlot s : p.boxSlots()) {
            UUID u = after(plan, box, s.getPosition().getSlot());
            if (u == null) continue;
            Plan.Entry e = idx.entry(u);
            int x = s.getX(), y = s.getY();
            if (e != null && e.from().box() != box) {
                ui.roundBorder(x, y, size, size, Ui.GREEN);
                label(ctx, tr, x + 2, y + size - 8, "C" + (e.from().box() + 1), Ui.GOLD);
            }
            PokemonInfo info = p.snapshot == null ? null : p.snapshot.info(u);
            if (info != null && p.cfg().showIvBadges) SlotMarks.ivBar(ctx, x, y, info);
            if (multi(e)) label(ctx, tr, x + 2, y + 2, "⇄", Ui.GOLD);
        }
        // Etiqueta "Así quedará" sobre la caja.
        String tag = "Así quedará";
        int tw = tr.getWidth(tag);
        int tx = g[0] + (g[2] - tw) / 2, ty = g[1] - 9;
        ui.box(tx - 5, ty - 2, tw + 10, 12, 0xFF2A2412, Ui.GOLD);
        ctx.drawText(tr, tag, tx, ty + 1, Ui.GOLD, false);
        if (!idx.changed(box)) {
            String t = p.locked(box) ? "Caja bloqueada: no cambia" : "Esta caja no cambia";
            ui.box(g[0] + g[2] / 2 - tr.getWidth(t) / 2 - 6, g[1] + g[3] / 2 - 7, tr.getWidth(t) + 12, 14, 0xE0121519, Ui.BORDER);
            ctx.drawCenteredTextWithShadow(tr, t, g[0] + g[2] / 2, g[1] + g[3] / 2 - 4, Ui.TEXT_DIM);
        }
        ctx.getMatrices().pop();
    }

    private static void label(DrawContext ctx, TextRenderer tr, int x, int y, String s, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(0.75F, 0.75F, 1F);
        ctx.fill(-1, -1, tr.getWidth(s) + 1, 8, 0xD0000000);
        ctx.drawText(tr, s, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    /** Bloquea clics en la rejilla durante la vista previa; ⇄ cambia la categoría de un Pokémon. */
    static boolean click(OrganizerPanel p, double mx, double my, int button) {
        Plan plan = PanelState.preview;
        if (plan == null) return false;
        int[] g = p.gridRect();
        if (g == null || mx < g[0] || mx >= g[0] + g[2] || my < g[1] || my >= g[1] + g[3]) return false;
        BoxStorageSlot s = p.slotAt(mx, my);
        if (s != null && button == 0) {
            UUID u = after(plan, p.currentBox(), s.getPosition().getSlot());
            Plan.Entry e = u == null ? null : PlanIndex.of(plan).entry(u);
            if (multi(e)) {
                Ui.click();
                p.cycleOverride(e);
            }
        }
        return true;
    }

    static void tooltip(OrganizerPanel p, DrawContext ctx, int mx, int my) {
        Plan plan = PanelState.preview;
        if (plan == null || p.snapshot == null) return;
        BoxStorageSlot s = p.slotAt(mx, my);
        if (s == null) return;
        UUID u = after(plan, p.currentBox(), s.getPosition().getSlot());
        if (u == null) return;
        PokemonInfo info = p.snapshot.info(u);
        if (info == null) return;
        Ui.drawTooltip(ctx, describe(p, info, PlanIndex.of(plan).entry(u)), p.vx(mx), p.vy(my), p.vw, p.vh);
    }

    /** Explicación corta de por qué se mueve. */
    static String reason(Plan.Entry e) {
        return switch (e.reason()) {
            case CATEGORY -> "Cumple " + e.categoryName() + " y va a su caja";
            case EVICTED -> (e.leftCategoryName() == null ? "No cumple la categoría de su caja"
                    : "No cumple " + e.leftCategoryName() + ", la categoría de su caja")
                    + ": sale a la primera caja libre sin categoría";
            case UNSORTED_BOX -> "No cumple ninguna categoría: va a la caja elegida en Ajustes";
            case SORTED -> e.categoryName() == null ? "Se coloca seguido y en orden, sin dejar huecos"
                    : "Ya está en " + e.categoryName() + ": cambia de hueco para quedar ordenado";
            case SWAPPED -> "Cambia de sitio para hacer hueco";
            case MANUAL -> "Movido desde la ventana Mover";
        };
    }

    static int reasonColor(Plan.Entry e) {
        return switch (e.reason()) {
            case CATEGORY, MANUAL -> Ui.GREEN;
            case EVICTED, UNSORTED_BOX -> Ui.ORANGE;
            case SORTED -> Ui.BLUE;
            case SWAPPED -> Ui.TEXT_DIM;
        };
    }

    /** Ficha de un Pokémon para tooltips: IVs con colores, grupo huevo y movimiento. */
    static List<Text> describe(OrganizerPanel p, PokemonInfo info, Plan.Entry e) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(info.name() + (info.shiny() ? " ★" : "")).withColor(Ui.TEXT)
                .append(Text.literal("  Nv. " + info.level()).withColor(Ui.TEXT_DIM)));
        MutableText ivs = Text.literal("");
        for (StatKey k : StatKey.values()) {
            int v = info.iv(k);
            ivs.append(Text.literal(k.shortLabel + " ").withColor(Icons.statColor(k)));
            ivs.append(Text.literal((v < 0 ? "?" : String.valueOf(v)) + "  ").withColor(v == 31 ? Ui.GREEN : v == 0 ? Ui.RED : Ui.TEXT_DIM));
        }
        lines.add(ivs);
        if (info.ivsKnown()) {
            lines.add(Text.literal(info.perfectIvCount() + "x31").withColor(SlotMarks.countColor(info.perfectIvCount())));
        }
        if (info.eggGroups().isEmpty()) {
            lines.add(Text.literal("Grupo huevo: desconocido").withColor(Ui.TEXT_DIM));
        } else {
            StringJoiner j = new StringJoiner(", ");
            for (EggGroupKey g : info.eggGroups()) j.add(g.label);
            lines.add(Text.literal("Huevo: ").withColor(Ui.TEXT_DIM).append(Text.literal(j.toString()).withColor(Ui.PURPLE)));
        }
        if (e != null) {
            lines.add(Text.literal("Caja " + (e.from().box() + 1) + " → Caja " + (e.to().box() + 1)).withColor(Ui.GOLD));
            lines.add(Text.literal(reason(e)).withColor(reasonColor(e)));
            if (PanelState.previewKind != PanelState.PreviewKind.MOVE && PanelState.preview != null) {
                lines.add(Text.literal("Categoría: " + (e.categoryName() == null ? "ninguna" : e.categoryName())).withColor(Ui.TEXT));
            }
            if (multi(e)) {
                StringJoiner j = new StringJoiner(", ");
                for (String id : e.matchingCategoryIds()) {
                    for (Category c : p.cfg().categories) if (c.id.equals(id)) j.add(c.name);
                }
                lines.add(Text.literal("Cumple: " + j).withColor(Ui.TEXT_DIM));
                lines.add(Text.literal("Click en la caja: elegir otra").withColor(Ui.GOLD));
            }
        }
        return lines;
    }
}
