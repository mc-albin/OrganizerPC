package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.SortMode;

import java.util.List;

/**
 * Ventana «Ordenar cajas»: elige cajas y cómo ordenarlas. Junta los Pokémon seguidos (sin
 * huecos), completando las cajas en el orden elegido, y opcionalmente los ordena por IVs.
 */
final class ArrangeWindow {
    private ArrangeWindow() {
    }

    static void layout(OrganizerPanel p, Ui ui) {
        int[] w = p.win(PanelState.Win.ARRANGE);
        if (!p.windowFrame(ui, PanelState.Win.ARRANGE, w, "Ordenar cajas", Icons.BOX, () -> {
            PanelState.arrangeOpen = false;
            if (PanelState.stripMode == PanelState.StripMode.ARRANGE) PanelState.stripMode = PanelState.StripMode.NAVIGATE;
        })) return;
        int lx = w[0] + 7, iw = w[2] - 14;
        int r = w[1] + 24;

        r += SidePanel.header(ui, lx, r, iw, Icons.BOX, "¿Qué cajas?");
        int qw = (iw - 6) / 3;
        if (ui.chip(lx, r, qw, 13, "Esta caja", false, Ui.BLUE)) {
            PanelState.arrangeCats.clear();
            PanelState.arrangeBoxes.clear();
            PanelState.arrangeBoxes.add(p.currentBox());
        }
        if (ui.chip(lx + qw + 3, r, qw, 13, "Capturas", false, Ui.CYAN)) {
            PanelState.arrangeCats.clear();
            PanelState.arrangeBoxes.clear();
            PanelState.arrangeBoxes.addAll(p.cfg().captureBoxes);
            if (PanelState.arrangeBoxes.isEmpty()) PanelState.status("No tienes cajas de captura marcadas.", Ui.GOLD);
        }
        ui.tooltip(lx + qw + 3, r, qw, 13, "Todas tus cajas de captura.");
        boolean picking = PanelState.stripMode == PanelState.StripMode.ARRANGE;
        if (ui.chip(lx + 2 * (qw + 3), r, iw - 2 * (qw + 3), 13, picking ? "Eligiendo…" : "Elegir", picking, Ui.GOLD)) {
            PanelState.stripMode = picking ? PanelState.StripMode.NAVIGATE : PanelState.StripMode.ARRANGE;
        }
        ui.tooltip(lx + 2 * (qw + 3), r, iw - 2 * (qw + 3), 13, "Toca cajas en la barra de arriba.", "Se llenan en el orden en que las elijas.");
        r += 16;
        // Atajo: cajas de una categoría. Se ven 2 filas; con más, la rueda del ratón recorre todas (barra a la derecha).
        if (p.cfg().categories.stream().anyMatch(c -> !c.boxes.isEmpty())) {
            int lh = 2 * 13, cw = iw - 5;
            int top = p.listBegin(ui, "arrangeCats", lx, r, iw, lh);
            int fx = lx, cy = top;
            for (Category c : p.cfg().categories) {
                if (c.boxes.isEmpty()) continue;
                int chw = Math.min(cw, ui.width(c.name) + 10);
                if (fx + chw > lx + cw) {
                    fx = lx;
                    cy += 13;
                }
                boolean on = PanelState.arrangeCats.contains(c.id);
                if (ui.chip(fx, cy, chw, 11, c.name, on, c.color)) {
                    if (!PanelState.arrangeCats.remove(c.id)) PanelState.arrangeCats.add(c.id);
                    PanelState.arrangeBoxes.clear();
                    for (Category k : p.cfg().categories) {
                        if (!PanelState.arrangeCats.contains(k.id)) continue;
                        for (int b : k.boxes) if (!PanelState.arrangeBoxes.contains(b)) PanelState.arrangeBoxes.add(b);
                    }
                }
                ui.tooltip(fx, cy, chw, 11, "Las cajas de «" + c.name + "»", "Puedes elegir varias categorías.");
                fx += chw + 2;
            }
            p.listEnd(ui, "arrangeCats", lx, r, iw, lh, cy + 13 - top);
            r += lh + 2;
        }
        List<Integer> boxes = PanelState.arrangeBoxes;
        String list = boxes.isEmpty() ? "Ninguna elegida" : "Cajas: " + String.join(", ", boxes.stream().map(b -> String.valueOf(b + 1)).toList());
        r += ui.wrapped(lx, r, iw, list, boxes.isEmpty() ? Ui.GOLD : Ui.TEXT) + 4;

        r += SidePanel.header(ui, lx, r, iw, Icons.IVS, "¿Cómo?");
        SortMode[] sel = {PanelState.arrangeMode, PanelState.arrangeMode2};
        r += SortPicker.grid(ui, lx, r, iw, sel, "Solo juntar") + 4;
        PanelState.arrangeMode = sel[0];
        PanelState.arrangeMode2 = sel[1];

        if (ui.button(lx, w[1] + w[3] - 24, iw, 18, "Ordenar", Icons.BOX, boxes.isEmpty() ? Ui.Style.NORMAL : Ui.Style.PRIMARY)) {
            PanelState.stripMode = PanelState.StripMode.NAVIGATE;
            p.startArrange();
        }
        ui.tooltip(lx, w[1] + w[3] - 24, iw, 18, "Verás la vista previa antes de mover.");
    }
}
