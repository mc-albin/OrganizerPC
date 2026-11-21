package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.cobblemon.PcReader;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.StatKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Marcas de IVs de tu equipo fuera de la PC: en el equipo de la pantalla, en la pantalla de
 * datos (Summary) y debajo del nombre de tus Pokémon sacados. Todo depende del ajuste
 * «Marcas de IVs en tu equipo».
 */
public final class PartyHudMarks {
    /** Colocación de PartyOverlay (Cobblemon 1.7.3): 30 de alto, paso 34; retrato 21x21 en x = 22 (+6 el seleccionado). */
    private static final int SLOT_STEP = 34, SLOT_HEIGHT = 30, PORTRAIT_X = 22, PORTRAIT_SIZE = 21, SELECTED_SHIFT = 6;
    /** Ancho del hueco del equipo en la pantalla de datos (PartySlotWidget.WIDTH). */
    private static final int SUMMARY_SLOT_W = 46;
    private static int errors;

    private PartyHudMarks() {
    }

    private static boolean enabled() {
        var c = OrganizerPCClient.config();
        return c != null && c.ivMarksParty;
    }

    /**
     * Se llama desde el final del render del equipo de Cobblemon (mixin), con su escala y su
     * tamaño de ventana ya aplicados, así que la colocación coincide aunque otro mod lo reescale.
     */
    public static void render(DrawContext ctx) {
        try {
            if (!enabled()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            var storage = CobblemonClient.INSTANCE.getStorage();
            List<Pokemon> slots = storage.getParty().getSlots();
            int total = slots.size() * SLOT_HEIGHT;
            int startY = mc.getWindow().getScaledHeight() / 2 - total / 2 - 10;
            int selected = storage.getSelectedSlot();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 300);
            for (int i = 0; i < slots.size(); i++) {
                Pokemon mon = slots.get(i);
                if (mon == null) continue;
                int x = PORTRAIT_X + (i == selected ? SELECTED_SHIFT : 0);
                int y = startY + SLOT_STEP * i + 2;
                SlotMarks.ivBar(ctx, x, y, PcReader.info(mon), PORTRAIT_SIZE);
            }
            ctx.getMatrices().pop();
        } catch (Throwable t) {
            if (errors++ < 3) OrganizerPCClient.LOGGER.error("Marcas de IVs del equipo", t);
        }
    }

    /** Hueco del equipo en la pantalla de datos: la marca va justo encima, a la derecha. */
    public static void summarySlot(DrawContext ctx, Pokemon mon, int x, int y) {
        try {
            if (mon == null || !enabled()) return;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 300);
            SlotMarks.ivBarAbove(ctx, x + SUMMARY_SLOT_W - 2, y - 1, PcReader.info(mon));
            ctx.getMatrices().pop();
        } catch (Throwable t) {
            if (errors++ < 3) OrganizerPCClient.LOGGER.error("Marcas de IVs en datos", t);
        }
    }

    /**
     * Línea de IVs para debajo del nombre: solo si el Pokémon es tuyo y está en tu equipo
     * (el cliente sabe qué entidad es cada uno por su estado «sacado»). Null si no toca.
     */
    public static Text nameTagLine(PokemonEntity entity) {
        if (!enabled()) return null;
        Pokemon mine = null;
        for (Pokemon p : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
            if (p != null && p.getState() instanceof SentOutState s && s.getEntity() == entity) {
                mine = p;
                break;
            }
        }
        if (mine == null) return null;
        PokemonInfo info = PcReader.info(mine);
        if (!info.ivsKnown()) return null;
        int n = info.perfectIvCount();
        MutableText line = Text.literal("");
        boolean any = false;
        for (StatKey k : StatKey.values()) {
            if (info.iv(k) != 31) continue;
            line.append(Text.literal("■").withColor(Icons.statColor(k)));
            any = true;
        }
        for (StatKey k : StatKey.values()) {
            if (info.iv(k) != 0) continue;
            line.append(Text.literal("□").withColor(Icons.statColor(k)));
            any = true;
        }
        if (!any) return null;
        if (n > 0) line.append(Text.literal(" " + n + "x31").withColor(SlotMarks.countColor(n)));
        return line;
    }
}
