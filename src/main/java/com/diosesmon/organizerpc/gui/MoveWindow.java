package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.SortMode;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pestaña «Mover» del recuadro derecho (mismo tamaño que «Categorías»).
 * Arriba los marcados, luego marcar con filtros, el destino, ordenar al mover y el botón Mover.
 */
final class MoveWindow {
    private MoveWindow() {
    }

    static void layout(OrganizerPanel p, Ui ui, int lx, int cy, int iw, int bottom) {
        List<UUID> marked = new ArrayList<>(PanelState.selection);
        int cell = PokemonIcon.SIZE;

        // Alturas fijas de abajo a arriba; los marcados ocupan el resto.
        int btnH = 18, sortH = 16, detailH = 62, destH = 27, filtersH = 52, markBtnH = 17, headH = 13;
        int markedH = bottom - cy - (btnH + sortH + detailH + destH + filtersH + markBtnH + headH) - 4;
        markedH = Math.max(cell + 6, markedH);

        int r = cy;
        // Marcados.
        ui.text(lx, r + 1, "Marcados", Ui.TEXT);
        String count = String.valueOf(marked.size());
        int bx0 = lx + ui.width("Marcados") + 5;
        ui.round(bx0, r, ui.width(count) + 8, 10, marked.isEmpty() ? Ui.LINE : Ui.alpha(Ui.GOLD, 0xC0));
        ui.text(bx0 + 4, r + 1, count, marked.isEmpty() ? Ui.TEXT_DIM : 0xFF14110A);
        if (!marked.isEmpty() && ui.button(lx + iw - 46, r - 1, 46, 12, "Limpiar", Ui.Style.GHOST)) {
            PanelState.selection.clear();
            PanelState.moveFilters.clear();
        }
        r += headH;

        ui.section(lx, r, iw, markedH);
        if (marked.isEmpty()) {
            int ty = r + 6;
            ty += ui.wrapped(lx + 6, ty, iw - 12, "Haz click en los Pokémon de la caja para marcarlos, o usa los filtros.", Ui.TEXT_DIM);
            ui.wrapped(lx + 6, ty + 2, iw - 12, "Puedes cambiar de caja y seguir marcando.", Ui.TEXT_DIM);
        } else {
            int cols = Math.max(1, (iw - 8) / (cell + 1));
            int top = p.listBegin(ui, "mw_marked", lx + 2, r + 2, iw - 4, markedH - 4);
            for (int i = 0; i < marked.size(); i++) {
                UUID u = marked.get(i);
                int ex = lx + 3 + (i % cols) * (cell + 1), ey = top + (i / cols) * (cell + 1);
                if (ey + cell < r + 2 || ey > r + markedH - 2) continue;
                boolean hov = ui.hover(ex, ey, cell, cell);
                ui.round(ex, ey, cell, cell, hov ? 0xFF3A2025 : Ui.ITEM);
                if (ui.drawing()) PokemonIcon.draw(ui.ctx, p.gui.getStorage(), p.pokemon(u), ex, ey, p.delta);
                if (hov) ui.text(ex + cell - 7, ey + 1, "✕", Ui.RED);
                PokemonInfo info = p.snapshot == null || !ui.wantsTooltip(ex, ey, cell, cell) ? null : p.snapshot.info(u);
                if (info != null) {
                    List<Text> tip = new ArrayList<>(PreviewOverlay.describe(p, info, null));
                    var pos = p.snapshot.positionOf(u);
                    if (pos != null) tip.add(Text.literal("En la caja " + (pos.box() + 1)).withColor(Ui.TEXT_DIM));
                    tip.add(Text.literal("Click: desmarcar").withColor(Ui.TEXT_DIM));
                    ui.tooltipText(ex, ey, cell, cell, tip);
                }
                if (ui.area(ex, ey, cell, cell)) PanelState.selection.remove(u);
            }
            int rows = (marked.size() + cols - 1) / cols;
            p.listEnd(ui, "mw_marked", lx + 2, r + 2, iw - 4, markedH - 4, rows * (cell + 1));
        }
        r += markedH + 3;

        // Marcar.
        if (ui.button(lx, r, iw, 15, "Marcar toda esta caja", Icons.BOX, Ui.Style.NORMAL)) p.markWholeBox();
        r += markBtnH;

        ui.small(lx, r + 2, "Filtros (puedes elegir varios)", Ui.TEXT_DIM);
        int fy = r + 13, fh = filtersH - 15;
        ui.section(lx, fy, iw, fh);
        if (p.cfg().categories.isEmpty()) {
            ui.small(lx + 5, fy + 5, "Crea categorías para poder filtrar", Ui.TEXT_DIM);
        } else {
            int top = p.listBegin(ui, "mw_filters", lx + 2, fy + 2, iw - 4, fh - 4);
            int fx = lx + 3, ry = top;
            for (Category c : p.cfg().categories) {
                int chw = Math.min(iw - 12, ui.width(c.name) + 12);
                if (fx + chw > lx + iw - 6) {
                    fx = lx + 3;
                    ry += 13;
                }
                boolean on = PanelState.moveFilters.contains(c.id);
                if (ui.chip(fx, ry, chw, 11, c.name, on, c.color)) {
                    if (!PanelState.moveFilters.remove(c.id)) PanelState.moveFilters.add(c.id);
                    p.applyMoveFilters();
                }
                ui.tooltip(fx, ry, chw, 11, "Marca en toda la PC los que cumplen " + c.name + ".",
                        "Con varios filtros se marcan los que cumplan alguno.", "(Sin cajas bloqueadas.)");
                fx += chw + 2;
            }
            p.listEnd(ui, "mw_filters", lx + 2, fy + 2, iw - 4, fh - 4, ry + 13 - top);
        }
        r += filtersH;

        // Destino.
        ui.text(lx, r + 1, "Destino", Ui.TEXT);
        int dy = r + 12;
        int dw = (iw - 6) / 4;
        PanelState.MoveDest[] dests = PanelState.MoveDest.values();
        String[] dl = {"Aquí", "Cajas", "Categoría", "Capturas"};
        PanelState.MoveDest[] order = {PanelState.MoveDest.HERE, PanelState.MoveDest.BOX, PanelState.MoveDest.CATEGORY, PanelState.MoveDest.CAPTURE};
        String[][] dt = {
                {"La caja que estás viendo", "(cambia sola al pasar de caja)."},
                {"Elige una o varias cajas abajo.", "Se llenan en el orden en que las elijas."},
                {"Las cajas de una categoría."},
                {"Todos los huecos libres de tus", "cajas de captura."}};
        for (int i = 0; i < 4; i++) {
            int bx = lx + i * (dw + 2);
            int bw = i == 3 ? iw - 3 * (dw + 2) : dw;
            Category destCat = p.categoryById(PanelState.moveCategoryId);
            int col = order[i] == PanelState.MoveDest.CATEGORY && destCat != null ? destCat.color
                    : order[i] == PanelState.MoveDest.CAPTURE ? Ui.CYAN : Ui.GOLD;
            if (ui.chip(bx, dy, bw, 13, dl[i], PanelState.moveDest == order[i], col)) {
                PanelState.moveDest = order[i];
                if (order[i] == PanelState.MoveDest.CATEGORY && p.categoryById(PanelState.moveCategoryId) == null) {
                    if (!PanelState.moveFilters.isEmpty()) PanelState.moveCategoryId = PanelState.moveFilters.iterator().next();
                    if (p.categoryById(PanelState.moveCategoryId) == null && !p.cfg().categories.isEmpty()) {
                        PanelState.moveCategoryId = p.cfg().categories.get(0).id;
                    }
                }
                if (order[i] == PanelState.MoveDest.BOX && PanelState.moveBoxes.isEmpty()) PanelState.moveBoxes.add(p.currentBox());
                if (order[i] == PanelState.MoveDest.CAPTURE) {
                    // Al mover a capturas se usa el orden de capturas de Ajustes.
                    PanelState.moveSort = p.cfg().captureSort;
                    PanelState.moveSort2 = p.cfg().captureSort2;
                }
            }
            ui.tooltip(bx, dy, bw, 13, dt[i]);
        }
        r += destH;

        int gy = r, gh = detailH - 3;
        ui.section(lx, gy, iw, gh);
        switch (PanelState.moveDest) {
            case CATEGORY -> {
                int top = p.listBegin(ui, "mw_destcat", lx + 2, gy + 2, iw - 4, gh - 4);
                int ry = top;
                for (Category c : p.cfg().categories) {
                    boolean sel = c.id.equals(PanelState.moveCategoryId);
                    boolean hov = ui.hover(lx + 3, ry, iw - 10, 12);
                    ui.round(lx + 3, ry, iw - 10, 12, sel ? Ui.alpha(c.color, 0x70) : hov ? Ui.ITEM_HOVER : 0x00000000);
                    ui.round(lx + 6, ry + 3, 6, 6, c.color);
                    String bl = c.boxes.isEmpty() ? "sin cajas" : c.boxesLabel();
                    int blw = ui.smallWidth(bl);
                    ui.textTrim(lx + 15, ry + 2, iw - 32 - blw, c.name, sel ? 0xFFFFFFFF : Ui.TEXT);
                    ui.small(lx + iw - 10 - blw, ry + 3, bl, c.boxes.isEmpty() ? Ui.GOLD : Ui.TEXT_DIM);
                    if (ui.area(lx + 3, ry, iw - 10, 12)) PanelState.moveCategoryId = c.id;
                    ry += 13;
                }
                p.listEnd(ui, "mw_destcat", lx + 2, gy + 2, iw - 4, gh - 4, ry - top);
            }
            case BOX -> {
                int n = p.boxCount();
                int tcols = 10, tw = (iw - 10 - (tcols - 1)) / tcols, tht = 13;
                int top = p.listBegin(ui, "mw_boxes", lx + 2, gy + 2, iw - 4, gh - 4);
                for (int b = 0; b < n; b++) {
                    int tbx = lx + 3 + (b % tcols) * (tw + 1), tby = top + (b / tcols) * (tht + 1);
                    boolean locked = p.locked(b);
                    int idx = PanelState.moveBoxes.indexOf(b);
                    boolean sel = idx >= 0;
                    boolean hov = ui.hover(tbx, tby, tw, tht);
                    int free = p.snapshot == null || b >= p.snapshot.boxCount() ? 0 : p.snapshot.freeSlots(b);
                    Category owner = p.ownerOf(b);
                    int fill = locked ? 0xFF3A181D : sel ? Ui.alpha(Ui.GOLD, 0xA0) : p.capture(b) ? Ui.alpha(Ui.CYAN, 0x40)
                            : owner != null ? Ui.alpha(owner.color, 0x40) : Ui.ITEM;
                    ui.box(tbx, tby, tw, tht, fill, sel ? Ui.GOLD : hov ? Ui.BORDER_HOVER : Ui.LINE);
                    if (locked) {
                        ui.lock(tbx + (tw - 7) / 2, tby + 2, 0xFFFF7A7A);
                    } else {
                        String num = String.valueOf(b + 1);
                        ui.small(tbx + (tw - ui.smallWidth(num)) / 2 + 1, tby + 3, num, sel ? 0xFF14110A : Ui.TEXT);
                    }
                    ui.tooltip(tbx, tby, tw, tht, "Caja " + (b + 1) + (owner != null ? " · " + owner.name : ""),
                            locked ? "Bloqueada" : free + " huecos libres",
                            locked ? "" : sel ? "Destino nº " + (idx + 1) + " · click: quitar" : "Click: añadir como destino");
                    if (ui.area(tbx, tby, tw, tht) && !locked) {
                        if (!PanelState.moveBoxes.remove((Integer) b)) PanelState.moveBoxes.add(b);
                    }
                }
                p.listEnd(ui, "mw_boxes", lx + 2, gy + 2, iw - 4, gh - 4, ((n + tcols - 1) / tcols) * (tht + 1));
            }
            case CAPTURE -> {
                List<Integer> caps = p.moveTargets();
                if (caps.isEmpty()) {
                    ui.wrapped(lx + 6, gy + 5, iw - 12, "No tienes cajas de captura. Márcalas con la Poké Ball junto a la barra de cajas.", Ui.GOLD);
                } else {
                    int free = 0;
                    for (int b : caps) free += p.snapshot == null ? 0 : p.snapshot.freeSlots(b);
                    ui.text(lx + 6, gy + 5, caps.size() + " cajas de captura", Ui.CYAN);
                    ui.textRight(lx + iw - 6, gy + 5, free + " huecos libres", free == 0 ? Ui.RED : Ui.GREEN);
                    String list = "Cajas " + String.join(", ", caps.stream().map(b -> String.valueOf(b + 1)).toList());
                    ui.wrapped(lx + 6, gy + 18, iw - 12, list, Ui.TEXT_DIM);
                }
            }
            default -> {
                int box = p.currentBox();
                int free = p.snapshot == null ? 0 : p.snapshot.freeSlots(box);
                ui.text(lx + 6, gy + 6, "Caja " + (box + 1), Ui.GOLD);
                String fr = free + (free == 1 ? " hueco libre" : " huecos libres");
                ui.textRight(lx + iw - 6, gy + 6, fr, free == 0 ? Ui.RED : Ui.GREEN);
                ui.wrapped(lx + 6, gy + 20, iw - 12, "Cambia de caja y el destino cambia contigo.", Ui.TEXT_DIM);
            }
        }
        r += detailH;

        // Ordenar al mover (dos niveles).
        ui.small(lx, r + 3, "Ordenar", Ui.TEXT_DIM);
        SortMode[] sel = {PanelState.moveSort, PanelState.moveSort2};
        SortPicker.compact(ui, lx + 36, r, iw - 36, sel, "No");
        PanelState.moveSort = sel[0];
        PanelState.moveSort2 = sel[1];
        ui.tooltip(lx, r, 34, 13, "Al mover, deja las cajas destino", "ordenadas y sin huecos.");

        String label = marked.isEmpty() ? "Marca Pokémon primero" : "Mover " + marked.size() + " " + p.moveDestLabel();
        if (ui.button(lx, bottom - btnH, iw, btnH, label, Icons.MOVE, marked.isEmpty() ? Ui.Style.NORMAL : Ui.Style.PRIMARY)) p.moveMarked();
        ui.tooltip(lx, bottom - btnH, iw, btnH, label, "Verás la vista previa antes de mover.");
    }
}
