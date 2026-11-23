package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.core.BreedingPair;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.plan.Pos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parejas de crianza (P1, P2…): el jugador marca a mano qué Pokémon van juntos para criar y el
 * mod solo los señala (insignia de color en la caja, en el equipo y en la barra de cajas).
 * Se marcan con el modo Parejas (click en el Pokémon) o con una tecla con el ratón encima.
 */
public final class Pairs {
    /** Tecla para meter o sacar de la pareja elegida al Pokémon bajo el ratón (configurable en Controles). */
    public static final KeyBinding KEY = new KeyBinding("Marcar pareja de crianza (OrganizerPC)",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "OrganizerPC");

    /** Valor de {@link PanelState#selectedPair} para la ficha «No usar» (marca Pokémon para que la wiki no los use). */
    static final int NO_WIKI = -1;
    /** Color de la marca «No usar». */
    static final int NO_WIKI_COLOR = 0xFF9AA0A6;

    private Pairs() {
    }

    private static OrganizerConfig cfg() {
        return OrganizerPCClient.config();
    }

    static BreedingPair byNumber(int n) {
        for (BreedingPair p : cfg().pairs) if (p.number == n) return p;
        return null;
    }

    /** Pareja elegida para marcar; si no hay ninguna, crea P1. */
    static BreedingPair selected() {
        BreedingPair p = byNumber(PanelState.selectedPair);
        if (p != null) return p;
        if (!cfg().pairs.isEmpty()) {
            p = cfg().pairs.get(0);
        } else {
            p = new BreedingPair(1);
            cfg().pairs.add(p);
        }
        PanelState.selectedPair = p.number;
        return p;
    }

    static BreedingPair newPair() {
        int n = 1;
        for (BreedingPair p : cfg().pairs) n = Math.max(n, p.number + 1);
        BreedingPair p = new BreedingPair(n);
        cfg().pairs.add(p);
        PanelState.selectedPair = n;
        OrganizerPCClient.saveConfig();
        return p;
    }

    /** Mete o saca al Pokémon de la pareja elegida. Si estaba en otra, se cambia a esta. */
    static void toggle(UUID u, String name) {
        if (PanelState.selectedPair == NO_WIKI) {
            if (cfg().noWiki.remove(u)) {
                PanelState.status(name + ": la wiki ya puede usarlo.", Ui.TEXT_DIM);
            } else {
                cfg().noWiki.add(u);
                PanelState.status(name + ": no se usará en la wiki.", NO_WIKI_COLOR);
            }
            Ui.click();
            OrganizerPCClient.saveConfig();
            com.diosesmon.organizerpc.cobblemon.BreederExport.markDirty();
            return;
        }
        BreedingPair target = selected();
        BreedingPair cur = cfg().pairOf(u);
        if (cur == target) {
            target.members.remove(u);
            PanelState.status(name + " ya no está en " + target.label() + ".", Ui.TEXT_DIM);
        } else if (target.full()) {
            PanelState.status(target.label() + " ya tiene 2. Quita uno o crea otra pareja con +.", Ui.GOLD);
            return;
        } else {
            if (cur != null) cur.members.remove(u);
            target.members.add(u);
            PanelState.status(name + " → " + target.label() + (target.full() ? " (pareja completa)" : "") + ".", target.color);
        }
        Ui.click();
        OrganizerPCClient.saveConfig();
        com.diosesmon.organizerpc.cobblemon.BreederExport.markDirty();
    }

    /** Dónde está un miembro: caja de la PC, equipo o ninguno de los dos (guardería, pasto…). */
    static String where(OrganizerPanel p, UUID u) {
        if (p != null && p.snapshot != null) {
            Pos pos = p.snapshot.positionOf(u);
            if (pos != null) return "Caja " + (pos.box() + 1);
        }
        for (Pokemon m : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
            if (m != null && m.getUuid().equals(u)) return "En tu equipo";
        }
        return "No está en la PC ni en el equipo";
    }

    static String name(OrganizerPanel p, UUID u) {
        if (p != null && p.snapshot != null) {
            PokemonInfo info = p.snapshot.info(u);
            if (info != null) return info.name();
        }
        for (Pokemon m : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
            if (m != null && m.getUuid().equals(u)) return com.diosesmon.organizerpc.cobblemon.PcReader.info(m).name();
        }
        return "?";
    }

    /** Cajas con algún miembro de una pareja → color de la primera pareja que tiene (0 = ninguna). */
    static int boxColor(OrganizerPanel p, int box) {
        if (p.snapshot == null) return 0;
        for (BreedingPair bp : cfg().pairs) {
            for (UUID u : bp.members) {
                Pos pos = p.snapshot.positionOf(u);
                if (pos != null && pos.box() == box) return bp.color;
            }
        }
        for (UUID u : com.diosesmon.organizerpc.cobblemon.WikiSync.pairs().orderedMembers()) {
            Pos pos = p.snapshot.positionOf(u);
            if (pos != null && pos.box() == box) return com.diosesmon.organizerpc.cobblemon.WikiSync.pairs().markOf(u).color();
        }
        return 0;
    }

    static List<String> boxTip(OrganizerPanel p, int box) {
        List<String> out = new ArrayList<>();
        if (p.snapshot == null) return out;
        for (BreedingPair bp : cfg().pairs) {
            for (UUID u : bp.members) {
                Pos pos = p.snapshot.positionOf(u);
                if (pos != null && pos.box() == box) out.add("Pareja " + bp.label() + ": " + name(p, u));
            }
        }
        var wp = com.diosesmon.organizerpc.cobblemon.WikiSync.pairs();
        for (UUID u : wp.orderedMembers()) {
            Pos pos = p.snapshot.positionOf(u);
            if (pos == null || pos.box() != box) continue;
            var m = wp.markOf(u);
            out.add(m.crianza() + " " + (m.pair() > 0 ? m.label() : "(espera una cría)") + ": " + name(p, u) + (m.female() ? " ♀" : " ♂"));
        }
        return out;
    }

    /** Insignia «P1» arriba a la izquierda del hueco y borde del color de la pareja. */
    static void badge(DrawContext ctx, int x, int y, BreedingPair bp) {
        badge(ctx, x, y, bp.label(), bp.color);
    }

    /** Insignia con texto y color (parejas manuales «P1» y de la wiki «L2»). */
    static void badge(DrawContext ctx, int x, int y, String t, int color) {
        int size = StorageSlot.SIZE;
        Ui ui = new Ui(Ui.Mode.DRAW, ctx, -1, -1);
        ui.roundBorder(x, y, size, size, color);
        ui.roundBorder(x + 1, y + 1, size - 2, size - 2, Ui.alpha(color, 0x70));
        var tr = MinecraftClient.getInstance().textRenderer;
        double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float sc = (float) (Math.max(1, Math.round(0.5 * gs)) / gs);
        int w = (int) Math.ceil(tr.getWidth(t) * sc) + 3, h = (int) Math.ceil(8 * sc) + 2;
        ctx.fill(x + 1, y + 1, x + 1 + w, y + 1 + h, color);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 2.5F, y + 2, 0);
        ctx.getMatrices().scale(sc, sc, 1F);
        ctx.drawText(tr, t, 0, 0, 0xFF101215, false);
        ctx.getMatrices().pop();
    }

    /** Marca «No usar en la wiki»: «No» gris abajo a la izquierda del hueco. */
    static void noWikiBadge(DrawContext ctx, int x, int y) {
        int size = StorageSlot.SIZE;
        var tr = MinecraftClient.getInstance().textRenderer;
        String t = "No";
        double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float sc = (float) (Math.max(1, Math.round(0.5 * gs)) / gs);
        int w = (int) Math.ceil(tr.getWidth(t) * sc) + 3, h = (int) Math.ceil(8 * sc) + 2;
        int by = y + size - 1 - h;
        ctx.fill(x + 1, by, x + 1 + w, by + h, NO_WIKI_COLOR);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 2.5F, by + 1, 0);
        ctx.getMatrices().scale(sc, sc, 1F);
        ctx.drawText(tr, t, 0, 0, 0xFF101215, false);
        ctx.getMatrices().pop();
    }

    private static void marks(DrawContext ctx, StorageSlot slot) {
        Pokemon mon = slot.getPokemon();
        if (mon == null) return;
        BreedingPair bp = cfg().pairOf(mon.getUuid());
        if (bp != null) {
            badge(ctx, slot.getX(), slot.getY(), bp);
        } else {
            var wm = com.diosesmon.organizerpc.cobblemon.WikiSync.pairs().markOf(mon.getUuid());
            // «C1♂»: crianza, número de pareja y el sexo que debe tener
            if (wm != null) badge(ctx, slot.getX(), slot.getY(), wm.label() + (wm.female() ? "♀" : "♂"), wm.color());
        }
        if (cfg().noWiki.contains(mon.getUuid())) noWikiBadge(ctx, slot.getX(), slot.getY());
    }

    /** Insignias sobre los Pokémon de la caja abierta y del equipo de la PC (coordenadas reales). */
    static void renderSlots(OrganizerPanel p, DrawContext ctx) {
        if (cfg().pairs.isEmpty() && cfg().noWiki.isEmpty() && com.diosesmon.organizerpc.cobblemon.WikiSync.pairs().isEmpty()) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 420);
        for (var slot : p.boxSlots()) marks(ctx, slot);
        for (var slot : p.partySlots()) marks(ctx, slot);
        ctx.getMatrices().pop();
    }

    /** Tecla con el ratón encima de un Pokémon de la caja (o del equipo) de la PC. */
    static boolean keyPressed(OrganizerPanel p, int key, int scancode) {
        if (!KEY.matchesKey(key, scancode)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
        Pokemon mon = null;
        var box = p.slotAt(mx, my);
        if (box != null) mon = box.getPokemon();
        if (mon == null) {
            for (var slot : p.partySlots()) {
                if (mx >= slot.getX() && mx < slot.getX() + StorageSlot.SIZE && my >= slot.getY() && my < slot.getY() + StorageSlot.SIZE) {
                    mon = slot.getPokemon();
                }
            }
        }
        if (mon == null) return false;
        toggle(mon.getUuid(), com.diosesmon.organizerpc.cobblemon.PcReader.info(mon).name());
        return true;
    }

    // ------------------------------------------------------------------ barra de arriba en modo Parejas

    /** Fila de la barra de arriba con las parejas: elegir, crear, ir y borrar. */
    static void row(OrganizerPanel p, Ui ui, int x, int y, int w) {
        int H = 18;
        ui.icon(Icons.PAIR, x, y + 3, 12);
        int cx = x + 16;
        int right = x + w - 60 - 4 - 40 - 4 - 46 - 4;
        boolean noMode = PanelState.selectedPair == NO_WIKI;
        BreedingPair sel = noMode ? null : selected();
        {
            String nl = "No usar";
            int cw = ui.width(nl) + 8;
            boolean hov = ui.hover(cx, y, cw, H);
            ui.box(cx, y, cw, H, noMode ? Ui.mix(Ui.BTN, NO_WIKI_COLOR, 0.45F) : hov ? Ui.BTN_HOVER : Ui.BTN,
                    noMode ? NO_WIKI_COLOR : hov ? Ui.BORDER_HOVER : Ui.BORDER);
            ui.text(cx + 4, y + 5, nl, noMode ? Ui.TEXT_ON : NO_WIKI_COLOR);
            ui.tooltip(cx, y, cw, H, "No usar en la wiki", "Click aquí y luego en un Pokémon: la wiki", "de crianza no lo usará como breeder.",
                    "Llevan " + cfg().noWiki.size() + " marcados.");
            if (ui.area(cx, y, cw, H)) PanelState.selectedPair = noMode ? (cfg().pairs.isEmpty() ? 0 : cfg().pairs.get(0).number) : NO_WIKI;
            cx += cw + 6;
        }
        for (BreedingPair bp : new ArrayList<>(cfg().pairs)) {
            int cw = ui.width(bp.label()) + 22;
            if (cx + cw > right - 22) break;
            boolean on = bp == sel;
            boolean hov = ui.hover(cx, y, cw, H);
            ui.box(cx, y, cw, H, on ? Ui.mix(Ui.BTN, bp.color, 0.45F) : hov ? Ui.BTN_HOVER : Ui.BTN, on ? bp.color : hov ? Ui.BORDER_HOVER : Ui.BORDER);
            ui.text(cx + 4, y + 5, bp.label(), on ? Ui.TEXT_ON : bp.color);
            for (int i = 0; i < 2; i++) {
                int dx = cx + cw - 14 + i * 6;
                if (i < bp.members.size()) ui.round(dx, y + 6, 5, 5, bp.color);
                else ui.roundBorder(dx, y + 6, 5, 5, Ui.alpha(bp.color, 0x90));
            }
            if (ui.wantsTooltip(cx, y, cw, H)) {
                List<String> tip = new ArrayList<>();
                tip.add("Pareja " + bp.label());
                if (bp.members.isEmpty()) tip.add("Vacía: haz click en dos Pokémon de la caja.");
                for (UUID u : bp.members) tip.add("· " + name(p, u) + " — " + where(p, u));
                tip.add(on ? "Click otra vez: ir a su caja" : "Click: marcar en esta pareja");
                ui.tooltipLines(cx, y, cw, H, tip);
            }
            if (ui.area(cx, y, cw, H)) {
                if (on) goTo(p, bp);
                else PanelState.selectedPair = bp.number;
            }
            cx += cw + 3;
        }
        if (ui.button(cx, y, 18, H, "+", Ui.Style.NORMAL)) newPair();
        ui.tooltip(cx, y, 18, H, "Nueva pareja");

        int bx = x + w - 60;
        if (ui.button(bx, y, 60, H, "Listo", Ui.Style.PRIMARY)) PanelState.pairMode = false;
        if (sel == null) return;
        bx -= 44;
        if (ui.button(bx, y, 40, H, "Ir", Ui.Style.NORMAL)) goTo(p, sel);
        ui.tooltip(bx, y, 40, H, "Ir a la caja de " + sel.label() + ".");
        bx -= 50;
        boolean armed = PanelState.deletePairArmed == sel.number;
        if (ui.button(bx, y, 46, H, armed ? "¿Seguro?" : "Borrar", Ui.Style.DANGER)) {
            if (armed) {
                cfg().pairs.remove(sel);
                PanelState.deletePairArmed = 0;
                PanelState.selectedPair = cfg().pairs.isEmpty() ? 0 : cfg().pairs.get(0).number;
                OrganizerPCClient.saveConfig();
                PanelState.status("Pareja " + sel.label() + " borrada (no se movió nada).", Ui.TEXT_DIM);
            } else {
                PanelState.deletePairArmed = sel.number;
            }
        }
        ui.tooltip(bx, y, 46, H, "Borra la pareja " + sel.label() + ". No mueve Pokémon.");
    }

    static void goTo(OrganizerPanel p, BreedingPair bp) {
        if (bp == null || p.snapshot == null) return;
        for (UUID u : bp.members) {
            Pos pos = p.snapshot.positionOf(u);
            if (pos != null) {
                p.goToBox(pos.box());
                return;
            }
        }
        PanelState.status(bp.members.isEmpty() ? bp.label() + " está vacía." : bp.label() + " no está en la PC.", Ui.GOLD);
    }
}
