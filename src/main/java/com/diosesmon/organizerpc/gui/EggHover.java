package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.cobblemon.PcReader;
import com.diosesmon.organizerpc.core.EggGroupKey;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ventanita con el grupo huevo del Pokémon que está bajo el ratón en la caja de la PC.
 * Se dibuja en coordenadas reales, a la derecha del ratón (la izquierda de la PC es de otro mod).
 */
final class EggHover {
    private static UUID cachedUuid;
    private static Set<EggGroupKey> cachedGroups = Set.of();

    private EggHover() {
    }

    /** {@code rmx}/{@code rmy}: ratón real. Se dibuja con la escala del organizador (ya aplicada). */
    static void render(OrganizerPanel p, DrawContext ctx, int rmx, int rmy) {
        BoxStorageSlot slot = p.slotAt(rmx, rmy);
        int mx = p.vx(rmx), my = p.vy(rmy);
        if (slot == null) return;
        Pokemon mon = slot.getPokemon();
        if (mon == null) return;
        var wm = com.diosesmon.organizerpc.cobblemon.WikiSync.pairs().markOf(mon.getUuid());
        boolean eggs = OrganizerPCClient.config().eggGroupHover;
        if (!eggs && wm == null) return;
        if (!mon.getUuid().equals(cachedUuid)) {
            cachedUuid = mon.getUuid();
            try {
                cachedGroups = PcReader.info(mon).eggGroups();
            } catch (Exception e) {
                cachedGroups = Set.of();
            }
        }

        Ui ui = new Ui(Ui.Mode.DRAW, ctx, mx, my);
        List<String> names = new ArrayList<>();
        if (eggs) {
            for (EggGroupKey g : cachedGroups) names.add(g.label);
            if (names.isEmpty()) names.add("Desconocido");
        }
        // Crianza de la wiki: a qué crianza y pareja va, qué paso y si cambia de género.
        List<String> wiki = new ArrayList<>();
        if (wm != null) {
            // Lo que hay que hacer: con quién se cría, qué sexo ponerle y qué IV pasa con su brazal (31 o 0)
            wiki.add(wm.crianza() + " · " + (wm.pair() > 0 ? wm.label() : "espera una cría"));
            if (wm.partner() != null) wiki.add("Cría con: " + Pairs.name(p, wm.partner()));
            wiki.add("Ponle: " + (wm.female() ? "hembra" : "macho") + (wm.genderChange() ? " · paga el cambio" : ""));
            if (!wm.brazal().isBlank()) wiki.add("Brazal: pasa " + wm.brazal());
            if (wm.pair() > 0) wiki.add("Estado: " + wm.stateLabel());
        }
        String title = eggs ? "Grupo huevo" : "Crianza";
        int w = ui.smallWidth(title);
        for (String n : names) w = Math.max(w, ui.width(n) + 10);
        for (String n : wiki) w = Math.max(w, ui.width(n) + 10);
        w += 10;
        int h = 4 + 8 + names.size() * 10 + (wiki.isEmpty() ? 0 : wiki.size() * 10 + (eggs ? 4 : 0)) + 3;

        int x = mx + 10, y = my - h - 4;
        int sw = p.vw, sh = p.vh;
        if (x + w > sw - 2) x = mx - w - 4;
        if (y < 2) y = my + 12;
        y = Math.min(y, sh - h - 2);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 600);
        ui.panel(x, y, w, h);
        ui.small(x + 5, y + 4, title, Ui.TEXT_DIM);
        int ty = y + 12;
        for (String n : names) {
            ui.round(x + 5, ty + 2, 4, 4, Ui.GOLD);
            ui.text(x + 12, ty, n, Ui.TEXT);
            ty += 10;
        }
        if (!wiki.isEmpty() && eggs) ty += 4;
        for (int i = 0; i < wiki.size(); i++) {
            ui.round(x + 5, ty + 2, 4, 4, wm.color());
            ui.text(x + 12, ty, wiki.get(i), i == 0 ? wm.color() : Ui.TEXT);
            ty += 10;
        }
        ctx.getMatrices().pop();
    }
}
