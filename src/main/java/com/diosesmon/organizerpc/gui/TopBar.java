package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.afk.AfkTracker;
import com.diosesmon.organizerpc.cobblemon.MoveExecutor;
import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.plan.Plan;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Barra encima de la PC: acciones principales, AFK, grupo huevo, ocultar y la barra de cajas clicable. */
final class TopBar {
    private static final int H = 18;

    private TopBar() {
    }

    static void layout(OrganizerPanel p, Ui ui) {
        int x = p.tbX, y = p.tbY, w = p.tbW;
        ui.panel(x, y, w, p.tbH);
        int rx = x + 5, ry = y + 5, rw = w - 10;

        MoveExecutor ex = MoveExecutor.current();
        if (ex != null && ex.status() == MoveExecutor.Status.RUNNING) {
            runningRow(ui, ex, rx, ry, rw);
        } else if (PanelState.preview != null) {
            previewRow(p, ui, rx, ry, rw);
        } else if (PanelState.stripMode == PanelState.StripMode.ASSIGN && PanelState.editing != null) {
            Category c = PanelState.editing;
            ui.round(rx, ry + 5, 8, 8, c.color);
            modeRow(ui, rx, ry, rw, "Elige las cajas de «" + c.name + "»");
        } else if (PanelState.stripMode == PanelState.StripMode.LOCK) {
            ui.lock(rx + 1, ry + 5, Ui.RED);
            modeRow(ui, rx, ry, rw, "Toca cajas para bloquearlas o desbloquearlas");
        } else if (PanelState.stripMode == PanelState.StripMode.ARRANGE) {
            ui.icon(Icons.BOX, rx - 1, ry + 3, 11);
            modeRow(ui, rx, ry, rw, "Toca las cajas que quieres ordenar (en orden)");
        } else if (PanelState.stripMode == PanelState.StripMode.CAPTURE) {
            ui.icon(Icons.POKE_BALL, rx - 1, ry + 3, 11);
            modeRow(ui, rx, ry, rw, "Toca cajas para marcarlas como cajas de captura");
        } else {
            normalRow(p, ui, rx, ry, rw);
        }
        modeButtons(p, ui);
        strip(p, ui);
    }

    private static void modeRow(Ui ui, int x, int y, int w, String text) {
        ui.textTrim(x + 13, y + 5, w - 80, text, Ui.TEXT);
        if (ui.button(x + w - 60, y, 60, H, "Listo", Ui.Style.PRIMARY)) PanelState.stripMode = PanelState.StripMode.NAVIGATE;
    }

    private static int bw(Ui ui, String label, boolean icon) {
        return ui.width(label) + (icon ? 15 : 0) + 12;
    }

    private static void normalRow(OrganizerPanel p, Ui ui, int x, int y, int w) {
        boolean afk = AfkTracker.isAfk();
        int afkW = ui.width("AFK") + 20;
        int filterW = ui.width("Filtrar") + 27;
        int right = x + w - afkW - 6;

        String[] labels = {"Organizar PC", "Capturas", "Ordenar"};
        ItemStack[] icons = {Icons.PC, Icons.POKE_BALL, Icons.IVS};
        int[] widths = new int[3];
        boolean withIcons = true;
        int total = 0;
        for (int i = 0; i < 3; i++) {
            widths[i] = bw(ui, labels[i], true);
            total += widths[i] + 4;
        }
        // El buscador necesita al menos 100 de ancho: si no cabe, primero botones sin icono
        // y después «Filtrar» solo con la tolva.
        int minSearch = 100;
        if (x + total + minSearch + filterW + 2 > right) {
            withIcons = false;
            total = 0;
            for (int i = 0; i < 3; i++) {
                widths[i] = bw(ui, labels[i], false);
                total += widths[i] + 4;
            }
        }
        boolean filterText = x + total + minSearch + filterW + 2 <= right;
        if (!filterText) filterW = 20;
        int bx = x;
        p.rOrganize = new int[]{bx, y, widths[0], H};
        if (ui.button(bx, y, widths[0], H, labels[0], withIcons ? icons[0] : null, Ui.Style.PRIMARY)) {
            p.startOrganize(PanelState.PreviewKind.ORGANIZE_ALL);
        }
        ui.tooltip(bx, y, widths[0], H, "Ordena toda la PC según tus categorías.", "Antes verás cómo queda: nada se mueve sin confirmar.");
        bx += widths[0] + 4;
        if (ui.button(bx, y, widths[1], H, labels[1], withIcons ? icons[1] : null, Ui.Style.NORMAL)) {
            p.startOrganize(PanelState.PreviewKind.ORGANIZE_CAPTURE);
        }
        ui.tooltip(bx, y, widths[1], H, "Reparte en tus categorías los Pokémon", "de las cajas de captura (" + p.cfg().captureBoxes.size() + " marcadas).");
        bx += widths[1] + 4;
        if (ui.button(bx, y, widths[2], H, labels[2], withIcons ? icons[2] : null,
                PanelState.arrangeOpen ? Ui.Style.SELECTED : Ui.Style.NORMAL)) {
            PanelState.arrangeOpen = !PanelState.arrangeOpen;
            if (PanelState.arrangeOpen && PanelState.arrangeBoxes.isEmpty()) PanelState.arrangeBoxes.add(p.currentBox());
        }
        ui.tooltip(bx, y, widths[2], H, "Junta y ordena los Pokémon de las cajas", "que elijas (quita huecos, ordena por IVs, columnas…).");
        bx += widths[2] + 4;

        searchBox(p, ui, bx, y, right - filterW - 2 - bx);
        int fx = right - filterW;
        p.rFilter = new int[]{fx, y, filterW, H};
        boolean fOn = Search.filterActive();
        if (ui.button(fx, y, filterW, H, filterText ? "Filtrar" : "", Icons.FILTER, Search.popupOpen || fOn ? Ui.Style.SELECTED : Ui.Style.NORMAL)) {
            Search.popupOpen = !Search.popupOpen;
        }
        if (fOn) ui.round(fx + filterW - 6, y + 2, 4, 4, Ui.THEME);
        ui.tooltip(fx, y, filterW, H, "Filtrar", "Grupo huevo, IV de cada estadística y nº de 31.", "Las cajas que tienen alguno se iluminan.");

        // AFK: siempre dice «AFK»; amarillo = estás AFK, apagado = no lo estás.
        int ax = x + w - afkW;
        p.rAfk = new int[]{ax, y, afkW, H};
        boolean hov = ui.hover(ax, y, afkW, H);
        if (afk) ui.box(ax, y, afkW, H, Ui.mix(Ui.BTN, Ui.GOLD, hov ? 0.45F : 0.32F), Ui.GOLD);
        else ui.box(ax, y, afkW, H, hov ? Ui.BTN_HOVER : Ui.BTN, hov ? Ui.BORDER_HOVER : Ui.BORDER);
        ui.round(ax + 6, y + 6, 6, 6, afk ? Ui.GOLD : Ui.LINE);
        ui.text(ax + 15, y + 5, "AFK", afk ? Ui.TEXT_ON : Ui.TEXT_DIM);
        ui.tooltip(ax, y, afkW, H, afk ? "Estás AFK" : "No estás AFK",
                afk ? "Click para volver." : "Click para ponerte AFK.");
        if (ui.area(ax, y, afkW, H)) AfkTracker.toggle();
    }

    /** Caja de búsqueda: el texto lo dibuja el campo de Minecraft; aquí el marco, el icono y el contador. */
    private static void searchBox(OrganizerPanel p, Ui ui, int x, int y, int w) {
        if (w < 40) return;
        p.searchShown = true;
        p.rSearch = new int[]{x, y, w, H};
        boolean focus = p.searchField.isFocused();
        boolean active = Search.active();
        ui.box(x, y, w, H, Ui.FIELD, focus ? Ui.THEME : active ? Ui.alpha(Ui.THEME, 0xA0) : Ui.BORDER);
        ui.icon(Icons.SEARCH, x + 3, y + 4, 10);
        String count = active ? String.valueOf(Search.total()) : "";
        int cw = active ? ui.width(count) + 8 : 0;
        int clearW = active ? 11 : 0;
        int fx = x + 16, fw = w - 16 - cw - clearW - 4;
        p.searchField.setX(fx);
        p.searchField.setY(y + 5);
        p.searchField.setWidth(Math.max(10, fw));
        if (p.searchField.getText().isEmpty() && !focus) {
            ui.textTrim(fx, y + 5, fw, Search.filterActive() ? "Filtro activo" : "Buscar…", Ui.TEXT_DIM);
        }
        if (active) {
            int total = Search.total();
            int cx = x + w - clearW - 3 - cw;
            ui.round(cx, y + 4, cw - 2, 10, total == 0 ? Ui.alpha(Ui.RED, 0xC0) : Ui.alpha(Ui.THEME, 0xD0));
            ui.text(cx + 3, y + 5, count, Ui.TEXT_ON);
            // X dibujada a mano (la «✕» del texto no cabía y salía «…»).
            int qx = x + w - clearW - 2, qy = y + 4;
            boolean qh = ui.hover(qx, qy, clearW, 10);
            if (qh) ui.round(qx, qy, clearW, 10, Ui.alpha(Ui.RED, 0x70));
            ui.cross(qx + (clearW - 7) / 2, qy + 1, 7, qh ? Ui.TEXT_ON : Ui.TEXT_DIM);
            if (ui.area(qx, qy, clearW, 10)) {
                Search.clear();
                p.searchField.setText("");
                Search.popupOpen = false;
            }
            ui.tooltip(qx, qy, clearW, 10, "Quitar la búsqueda");
        }
        java.util.List<String> tip = new ArrayList<>();
        if (active) {
            tip.add(Search.total() + " Pokémon coinciden");
            tip.addAll(Search.explain());
            tip.add("Enter: ir a la siguiente caja con resultados");
        } else {
            tip.add("Buscar en la PC: se iluminan las cajas que tienen alguno");
            tip.add("· Especie: pikachu, eevee…");
            tip.add("· Grupo huevo: campo, hada, agua 1, dragón…");
            tip.add("· IV: ps31, ata31, def0, vel31… · nº de 31: 3x31");
            tip.add("· Nº Pokédex: 25, #150 o un rango 1-151");
            tip.add("Se combinan: «campo vel31» = Campo con 31 en Vel");
        }
        if (!(ui.hover(x + w - clearW - 2, y + 4, clearW, 10) && active)) ui.tooltipLines(x, y, w - cw - clearW, H, tip);
    }

    private static void previewRow(OrganizerPanel p, Ui ui, int x, int y, int w) {
        Plan plan = PanelState.preview;
        ui.round(x, y + 5, 8, 8, Ui.GOLD);
        ui.text(x + 12, y + 5, "Vista previa", Ui.GOLD);
        int bx = x + w - 140;
        String info = plan.movedCount() + " Pokémon cambian de sitio";
        int ix = x + 14 + ui.width("Vista previa") + 6;
        ui.textTrim(ix, y + 5, bx - ix - 4, info, Ui.TEXT_DIM);
        if (ui.button(bx, y, 66, H, "Mover", Icons.MOVE, PanelState.confirmArmed() ? Ui.Style.PRIMARY : Ui.Style.NORMAL)) p.confirmPreview();
        ui.tooltip(bx, y, 66, H, "Mover de verdad los Pokémon.");
        if (ui.button(bx + 70, y, 70, H, "Cancelar", Ui.Style.DANGER)) p.cancelPreview();
        ui.tooltip(bx + 70, y, 70, H, "No se mueve nada.");
    }

    private static void runningRow(Ui ui, MoveExecutor ex, int x, int y, int w) {
        String t = "Moviendo " + ex.done() + "/" + ex.total();
        ui.text(x, y + 5, t, Ui.BLUE);
        int bx = x + ui.width(t) + 8, bw = w - (bx - x) - 76;
        ui.round(bx, y + 6, bw, 6, Ui.LINE);
        int fill = ex.total() == 0 ? bw : bw * ex.done() / ex.total();
        if (fill >= 4) ui.round(bx, y + 6, fill, 6, Ui.GREEN);
        // Detener va donde estaba «Cancelar» (no donde estaba «Mover») y no responde al principio:
        // así un doble click en «Mover» no lo pulsa sin querer.
        boolean armed = PanelState.stopArmed();
        if (ui.button(x + w - 70, y, 70, H, "Detener", armed ? Ui.Style.DANGER : Ui.Style.NORMAL) && armed) {
            ex.requestStop();
            PanelState.status("Deteniendo…", Ui.GOLD);
        }
    }

    /** Posición del botón i (0-4) a la izquierda de la barra de cajas, en rejilla de {@code modeCols} columnas. */
    private static int[] modeRect(OrganizerPanel p, int i) {
        return new int[]{p.tbX + 5 + (i % p.modeCols) * 18, p.stripY + (i / p.modeCols) * 18, 16, 16};
    }

    /** Botones a la izquierda de la barra de cajas: bloquear, captura, ocultar, marcas de IVs y grupo huevo. */
    private static void modeButtons(OrganizerPanel p, Ui ui) {
        if (PanelState.preview != null || MoveExecutor.isRunning()) return;
        int[] r = modeRect(p, 0);
        boolean lockOn = PanelState.stripMode == PanelState.StripMode.LOCK;
        p.rLock = r;
        if (ui.button(r[0], r[1], 16, 16, "", null, lockOn ? Ui.Style.DANGER : Ui.Style.NORMAL)) {
            PanelState.stripMode = lockOn ? PanelState.StripMode.NAVIGATE : PanelState.StripMode.LOCK;
        }
        ui.lock(r[0] + 5, r[1] + 4, lockOn ? 0xFFFFFFFF : 0xFFFF8A8A);
        ui.tooltip(r[0], r[1], 16, 16, "Bloquear cajas", "El organizador nunca saca ni mete Pokémon en ellas.");

        r = modeRect(p, 1);
        boolean capOn = PanelState.stripMode == PanelState.StripMode.CAPTURE;
        p.rCapture = r;
        if (ui.button(r[0], r[1], 16, 16, "", Icons.POKE_BALL, capOn ? Ui.Style.WARN : Ui.Style.NORMAL)) {
            PanelState.stripMode = capOn ? PanelState.StripMode.NAVIGATE : PanelState.StripMode.CAPTURE;
        }
        ui.tooltip(r[0], r[1], 16, 16, "Cajas de captura",
                "Donde guardas los Pokémon nuevos. «Capturas» los reparte",
                "en tus categorías y estas cajas nunca reciben Pokémon.");

        r = modeRect(p, 2);
        p.rHide = r;
        if (ui.button(r[0], r[1], 16, 16, "", Icons.HIDE, Ui.Style.NORMAL)) HiddenButton.hide(p);
        ui.tooltip(r[0], r[1], 16, 16, "Ocultar el organizador",
                "Para volver, pulsa la Poké Ball encima de la PC.");

        r = modeRect(p, 3);
        p.rIvs = r;
        boolean ivOn = p.cfg().showIvBadges;
        if (ui.button(r[0], r[1], 16, 16, "", Icons.IVS, ivOn ? Ui.Style.PRIMARY : Ui.Style.NORMAL)) {
            p.cfg().showIvBadges = !ivOn;
            com.diosesmon.organizerpc.OrganizerPCClient.saveConfig();
        }
        ui.tooltip(r[0], r[1], 16, 16, ivOn ? "Marcas de IVs: visibles" : "Marcas de IVs: ocultas",
                "Click para " + (ivOn ? "quitarlas." : "verlas.") + " Relleno = 31, hueco = 0.");

        r = modeRect(p, 4);
        p.rEgg = r;
        boolean eggOn = p.cfg().eggGroupHover;
        if (ui.button(r[0], r[1], 16, 16, "", Icons.EGG, eggOn ? Ui.Style.PRIMARY : Ui.Style.NORMAL)) {
            p.cfg().eggGroupHover = !eggOn;
            com.diosesmon.organizerpc.OrganizerPCClient.saveConfig();
        }
        ui.tooltip(r[0], r[1], 16, 16, eggOn ? "Grupo huevo: visible" : "Grupo huevo: oculto",
                "Pasa el ratón por un Pokémon para ver su grupo huevo.");

        r = modeRect(p, 5);
        p.rSettings = r;
        if (ui.button(r[0], r[1], 16, 16, "", Icons.SETTINGS, PanelState.settingsOpen ? Ui.Style.SELECTED : Ui.Style.NORMAL)) {
            PanelState.settingsOpen = !PanelState.settingsOpen;
            if (PanelState.settingsOpen) p.syncHexFields();
        }
        boolean upd = com.diosesmon.organizerpc.update.Updater.state() == com.diosesmon.organizerpc.update.Updater.State.AVAILABLE;
        if (upd) ui.round(r[0] + 11, r[1] + 1, 4, 4, Ui.GOLD);
        ui.tooltip(r[0], r[1], 16, 16, "Ajustes", upd ? "¡Hay una versión nueva! Actualízala aquí." : "Marcas, tamaños, tema y más.");

        r = modeRect(p, 6);
        p.rBook = r;
        if (ui.button(r[0], r[1], 16, 16, "", Icons.BOOK, Guide.active() ? Ui.Style.SELECTED : Ui.Style.NORMAL)) Guide.open();
        ui.tooltip(r[0], r[1], 16, 16, "Tutoriales", "Cómo funciona y crear una categoría paso a paso.");
    }

    /** Barra de cajas: cada ficha es una caja de la PC. */
    private static void strip(OrganizerPanel p, Ui ui) {
        int n = p.boxCount();
        int current = p.currentBox();
        Plan plan = PanelState.preview;
        Category editing = PanelState.editing;
        boolean assign = PanelState.stripMode == PanelState.StripMode.ASSIGN && editing != null;
        int gap = p.chipGap;
        int startX = p.stripX0;
        boolean searching = Search.active();
        for (int b = 0; b < n; b++) {
            int cx = startX + (b % p.perRow) * (p.chipW + gap);
            int cy = p.stripY + (b / p.perRow) * 18;
            int cw = p.chipW, ch = 16;
            boolean locked = p.locked(b);
            boolean capture = p.capture(b);
            Category owner = p.ownerOf(b);
            boolean mine = assign && editing.ownsBox(b);
            boolean arranging = PanelState.stripMode == PanelState.StripMode.ARRANGE || (PanelState.arrangeOpen && PanelState.preview == null);
            int arrangeIdx = arranging ? PanelState.arrangeBoxes.indexOf(b) : -1;
            boolean hov = ui.hover(cx, cy, cw, ch);

            int fill = Ui.ITEM;
            int stripe = 0;
            if (owner != null) {
                fill = Ui.alpha(owner.color, owner.enabled ? 0x48 : 0x20);
                stripe = owner.enabled ? owner.color : Ui.alpha(owner.color, 0x80);
            }
            if (assign) {
                if (mine) {
                    fill = Ui.alpha(editing.color, 0xB0);
                    stripe = editing.color;
                } else if (owner != null) {
                    fill = Ui.alpha(owner.color, 0x18);
                    stripe = Ui.alpha(owner.color, 0x60);
                }
            }
            if (capture) {
                fill = Ui.alpha(Ui.CYAN, 0x40);
                stripe = Ui.CYAN;
            }
            if (locked) {
                fill = 0xFF3A181D;
                stripe = 0;
            }
            ui.round(cx, cy, cw, ch, fill);
            if (stripe != 0) ui.rect(cx + 1, cy + ch - 2, cw - 2, 2, stripe);
            int border = b == current || mine ? 0xFFFFFFFF : hov ? Ui.BORDER_HOVER : Ui.alpha(0xFFFFFF, 0x18);
            ui.roundBorder(cx, cy, cw, ch, border);
            int numColor = b == current || mine ? 0xFFFFFFFF : Ui.TEXT_DIM;
            if (locked) {
                ui.lock(cx + (cw - 7) / 2, cy + 4, 0xFFFF7A7A);
            } else {
                String num = String.valueOf(b + 1);
                int tw = ui.width(num);
                if (tw + 2 <= cw) ui.text(cx + (cw - tw) / 2 + 1, cy + 4, num, numColor);
                else ui.small(cx + (cw - ui.smallWidth(num)) / 2 + 1, cy + 5, num, numColor);
            }
            if (arrangeIdx >= 0) {
                ui.roundBorder(cx, cy, cw, ch, Ui.GOLD);
                ui.round(cx + cw - 5, cy + 1, 4, 4, Ui.GOLD);
            }
            int found = searching ? Search.boxHits(b) : 0;
            if (searching) {
                if (found > 0) {
                    // Caja con resultados: se ilumina con el color del tema.
                    ui.round(cx, cy, cw, ch, Ui.alpha(Ui.THEME, 0x55));
                    ui.roundBorder(cx, cy, cw, ch, Ui.THEME);
                    ui.roundBorder(cx - 1, cy - 1, cw + 2, ch + 2, Ui.alpha(Ui.THEME, 0x70));
                } else {
                    ui.round(cx, cy, cw, ch, 0x99000000);
                }
            }
            PlanIndex idx = plan == null ? null : PlanIndex.of(plan);
            if (idx != null) {
                if (idx.in(b) > 0) ui.round(cx + cw - 5, cy + 1, 4, 4, Ui.GREEN);
                if (idx.out(b) > 0) ui.round(cx + 1, cy + 1, 4, 4, Ui.RED);
            }
            if (ui.area(cx, cy, cw, ch)) p.stripClick(b);
            if (!ui.wantsTooltip(cx, cy, cw, ch)) continue;

            List<String> tip = new ArrayList<>();
            tip.add("Caja " + (b + 1) + (p.boxName(b).equals("Caja " + (b + 1)) ? "" : " · " + p.boxName(b)));
            if (locked) tip.add("Bloqueada: el organizador no la toca");
            if (capture) tip.add("Caja de captura");
            tip.add(owner == null ? "Sin categoría" : "Categoría: " + owner.name);
            if (p.snapshot != null && b < p.snapshot.boxCount()) tip.add((30 - p.snapshot.freeSlots(b)) + "/30 ocupados");
            if (idx != null) {
                int in = idx.in(b), out = idx.out(b);
                if (in + out > 0) tip.add("Llegan " + in + " · se van " + out);
            }
            if (searching) tip.add(found == 0 ? "Búsqueda: ninguno aquí" : "Búsqueda: " + found + " aquí");
            tip.add(switch (PanelState.stripMode) {
                case NAVIGATE -> "Click: ver esta caja";
                case LOCK -> "Click: " + (locked ? "desbloquear" : "bloquear");
                case CAPTURE -> "Click: " + (capture ? "quitar de captura" : "marcar como captura");
                case ARRANGE -> "Click: " + (arrangeIdx >= 0 ? "quitar de" : "añadir a") + " las cajas a ordenar";
                case ASSIGN -> editing == null ? "" : "Click: " + (mine ? "quitar de" : "añadir a") + " «" + editing.name + "»";
            });
            ui.tooltipLines(cx, cy, cw, ch, tip);
        }
    }
}
