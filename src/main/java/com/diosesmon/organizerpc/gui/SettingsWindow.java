package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.SortMode;
import net.minecraft.client.MinecraftClient;

/**
 * Ajustes: una ventana aparte en el centro (oscurece lo demás), con pestañas arriba,
 * botones «Opción: Sí/No» y «Listo» abajo. Funciona también con el organizador oculto.
 */
final class SettingsWindow {
    static final int TAB_GENERAL = 0, TAB_MARKS = 1, TAB_BOXES = 2, TAB_SIZES = 3, TAB_THEME = 4, TAB_BREED = 5;
    private static final String[] TABS = {"General", "Marcas", "Cajas", "Tamaños", "Tema", "Crianza"};
    /** Breeders contados para la pestaña Crianza (se recuentan cada segundo, no en cada fotograma). */
    private static int breederCount = -1;
    private static long breederCountAt;

    private SettingsWindow() {
    }

    static void close() {
        PanelState.settingsOpen = false;
        OrganizerPCClient.saveConfig();
    }

    private static void save() {
        OrganizerPCClient.saveConfig();
    }

    /** Botón ancho «Texto: Sí/No» (verde encendido). Devuelve true al pulsarlo. */
    private static boolean toggle(Ui ui, int x, int y, int w, String label, boolean on, String... tip) {
        boolean clicked = ui.toggleButton(x, y, w, 16, label + ": " + (on ? "Sí" : "No"), on);
        ui.tooltip(x, y, w, 16, tip);
        return clicked;
    }

    static void layout(OrganizerPanel p, Ui ui) {
        ui.rect(0, 0, p.vw, p.vh, 0x99000000);
        int[] w = p.win(PanelState.Win.SETTINGS);
        int x = w[0], y = w[1], W = w[2], H = w[3];
        ui.panel(x, y, W, H);
        ui.icon(Icons.SETTINGS, x + 8, y + 7, 12);
        ui.text(x + 23, y + 9, "Ajustes", Ui.TEXT);

        if (ui.button(x + W - 18, y + 5, 13, 13, "", Ui.Style.GHOST)) {
            close();
            return;
        }
        ui.cross(x + W - 15, y + 8, 7, Ui.TEXT_DIM);
        ui.tooltip(x + W - 18, y + 5, 13, 13, "Cerrar (Esc)");
        updateRow(ui, x + 70, y + 4, x + W - 22);

        // Pestañas en su propia fila, todas del mismo ancho (antes tapaban el título).
        int n = TABS.length, gap = 3, ty = y + 23;
        int tw = (W - 14 - (n - 1) * gap) / n;
        for (int i = 0; i < n; i++) {
            int tx = x + 7 + i * (tw + gap);
            int w2 = i == n - 1 ? x + W - 7 - tx : tw;
            if (ui.button(tx, ty, w2, 16, TABS[i], PanelState.settingsTab == i ? Ui.Style.SELECTED : Ui.Style.NORMAL)) {
                PanelState.settingsTab = i;
                if (i == TAB_THEME) p.syncHexFields();
                p.unfocusFields();
            }
        }
        ui.rect(x + 6, ty + 20, W - 12, 1, 0x26FFFFFF);

        int lx = x + 10, iw = W - 20, r = ty + 26;
        switch (PanelState.settingsTab) {
            case TAB_MARKS -> marks(p, ui, lx, r, iw);
            case TAB_BOXES -> boxes(p, ui, lx, r, iw);
            case TAB_SIZES -> sizes(p, ui, lx, r, iw);
            case TAB_THEME -> theme(p, ui, lx, r, iw);
            case TAB_BREED -> breeding(p, ui, lx, r, iw);
            default -> general(p, ui, lx, r, iw);
        }

        if (ui.button(lx, y + H - 22, iw, 16, "Listo", Ui.Style.LIGHT)) close();
    }

    /** Versión y actualización desde GitHub, en la cabecera (entre el título y la X). */
    private static void updateRow(Ui ui, int left, int y, int right) {
        var st = com.diosesmon.organizerpc.update.Updater.state();
        String cur = "v" + com.diosesmon.organizerpc.update.Updater.currentVersion();
        String latest = com.diosesmon.organizerpc.update.Updater.latestVersion();
        String notes = com.diosesmon.organizerpc.update.Updater.notes();
        switch (st) {
            case AVAILABLE, ERROR -> {
                String label = "Actualizar a v" + latest;
                int bw = ui.width(label) + 14;
                int bx = right - bw;
                if (ui.button(bx, y, bw, 15, label, st == com.diosesmon.organizerpc.update.Updater.State.ERROR ? Ui.Style.DANGER : Ui.Style.WARN)) {
                    com.diosesmon.organizerpc.update.Updater.downloadAsync();
                }
                ui.tooltip(bx, y, bw, 15, st == com.diosesmon.organizerpc.update.Updater.State.ERROR
                        ? new String[]{com.diosesmon.organizerpc.update.Updater.error(), "Click para reintentar."}
                        : notes.isBlank() ? new String[]{"Tienes " + cur + ". Se descarga ahora", "y se instala al cerrar el juego."}
                        : new String[]{"Novedades: " + notes, "Se descarga ahora y se instala al cerrar el juego."});
                ui.textRight(bx - 5, y + 4, cur, Ui.TEXT_DIM);
            }
            case DOWNLOADING -> ui.textRight(right, y + 4, "Descargando v" + latest, Ui.GOLD);
            case READY -> {
                String t = "v" + latest + " lista: se instala al cerrar el juego";
                ui.fit(left, y + 4, right - left, t, Ui.GREEN, false);
            }
            default -> ui.textRight(right, y + 4, cur, Ui.TEXT_DIM);
        }
    }

    // ------------------------------------------------------------------ pestañas

    private static void general(OrganizerPanel p, Ui ui, int lx, int r, int iw) {
        OrganizerConfig c = p.cfg();
        int hw = (iw - 4) / 2;
        ui.small(lx, r, "Pokémon que no cumplen ninguna categoría", Ui.TEXT_DIM);
        r += 9;
        boolean keep = c.unsortedMode == OrganizerConfig.UnsortedMode.KEEP;
        if (ui.toggleButton(lx, r, hw, 16, "Se quedan donde están", keep)) {
            c.unsortedMode = OrganizerConfig.UnsortedMode.KEEP;
            save();
        }
        ui.tooltip(lx, r, hw, 16, "No se mueven (salvo que ocupen la caja de una categoría).");
        int bw = iw - hw - 4 - 46;
        if (ui.toggleButton(lx + hw + 4, r, bw, 16, "Van a la caja " + (c.unsortedBox + 1), !keep)) {
            c.unsortedMode = OrganizerConfig.UnsortedMode.MOVE_TO_BOX;
            save();
        }
        ui.tooltip(lx + hw + 4, r, bw, 16, "Todos los que no cumplen ninguna categoría", "van a esta caja. Cámbiala con − y +.");
        int px = lx + iw - 44;
        if (ui.button(px, r, 21, 16, "−", Ui.Style.NORMAL)) {
            c.unsortedBox = Math.max(0, c.unsortedBox - 1);
            save();
        }
        if (ui.button(px + 23, r, 21, 16, "+", Ui.Style.NORMAL)) {
            c.unsortedBox = Math.min(Math.max(0, p.boxCount() - 1), c.unsortedBox + 1);
            save();
        }
        r += 21;
        if (toggle(ui, lx, r, hw, "Sacar a los que no cumplen", c.evictStrays,
                "Sí: un Pokémon que está en la caja de una", "categoría sin cumplirla sale a una caja libre.", "No: se queda donde está.")) {
            c.evictStrays = !c.evictStrays;
            save();
        }
        if (toggle(ui, lx + hw + 4, r, iw - hw - 4, "Juntar sin huecos", c.compact,
                "Coloca los Pokémon seguidos en las cajas de", "categoría y completa las cajas de captura.")) {
            c.compact = !c.compact;
            save();
        }
        r += 25;
        ui.small(lx, r, "Orden de las cajas de captura", Ui.TEXT_DIM);
        r += 9;
        SortMode[] cs = {c.captureSort, c.captureSort2};
        SortPicker.compact(ui, lx, r, iw, cs, "Sin orden");
        if (cs[0] != c.captureSort || cs[1] != c.captureSort2) {
            c.captureSort = cs[0];
            c.captureSort2 = cs[1];
            save();
        }
        ui.tooltip(lx, r - 9, iw, 8, "Se aplica al pulsar «Capturas» y al", "mover Pokémon hacia las cajas de captura.");
        r += 22;
        if (ui.button(lx, r, iw, 16, "Ver el tutorial", Icons.BOOK, Ui.Style.NORMAL)) {
            close();
            Guide.open();
            if (OrganizerPCClient.config().hidden) {
                OrganizerPCClient.config().hidden = false; // el tutorial necesita ver el organizador
                save();
            }
        }
    }

    private static void marks(OrganizerPanel p, Ui ui, int lx, int r, int iw) {
        OrganizerConfig c = p.cfg();
        if (toggle(ui, lx, r, iw, "Marcas de IVs en cada Pokémon", c.showIvBadges,
                "Relleno = IV en 31, hueco = IV en 0 (color de la stat).", "El número es cuántos 31 tiene.")) {
            c.showIvBadges = !c.showIvBadges;
            save();
        }
        r += 20;
        if (toggle(ui, lx, r, iw, "Marcas de IVs con el organizador oculto", c.ivMarksWhenHidden,
                "Sigue mostrando las marcas aunque", "el organizador esté oculto.")) {
            c.ivMarksWhenHidden = !c.ivMarksWhenHidden;
            save();
        }
        r += 20;
        if (toggle(ui, lx, r, iw, "Marcas de IVs en tu equipo", c.ivMarksParty,
                "En el equipo de la pantalla, en la pantalla de datos", "y debajo del nombre de tus Pokémon sacados.")) {
            c.ivMarksParty = !c.ivMarksParty;
            save();
        }
        r += 20;
        if (toggle(ui, lx, r, iw, "Grupo huevo al pasar el ratón", c.eggGroupHover,
                "Ventanita con el grupo huevo del", "Pokémon que tienes debajo del ratón.")) {
            c.eggGroupHover = !c.eggGroupHover;
            save();
        }
        r += 20;
        if (toggle(ui, lx, r, iw, "Resaltar al editar una categoría", c.highlightCategory,
                "Borde de color en los Pokémon que cumplen", "la categoría que estás editando.")) {
            c.highlightCategory = !c.highlightCategory;
            save();
        }
        r += 24;
        ui.small(lx, r, "Los botones de IVs y grupo huevo también están", Ui.TEXT_DIM);
        ui.small(lx, r + 8, "junto a la barra de cajas (y encima de la PC si está oculto).", Ui.TEXT_DIM);
    }

    private static void boxes(OrganizerPanel p, Ui ui, int lx, int r, int iw) {
        OrganizerConfig c = p.cfg();
        int hw = (iw - 4) / 2;
        if (ui.button(lx, r, iw, 16, "Nombrar cajas como su categoría", Icons.BRUSH, Ui.Style.NORMAL)) {
            close();
            p.askRenameBoxes();
        }
        ui.tooltip(lx, r, iw, 16, "Cada caja de una categoría pasa a llamarse", "como ella (p. ej. «6x31»). No mueve Pokémon.");
        r += 21;
        if (ui.button(lx, r, hw, 16, "Desbloquear todas (" + c.lockedBoxes.size() + ")", Ui.Style.NORMAL)) {
            c.lockedBoxes.clear();
            save();
        }
        ui.tooltip(lx, r, hw, 16, "Quita el candado de todas las cajas.");
        if (ui.button(lx + hw + 4, r, iw - hw - 4, 16, "Quitar captura (" + c.captureBoxes.size() + ")", Ui.Style.NORMAL)) {
            c.captureBoxes.clear();
            save();
        }
        ui.tooltip(lx + hw + 4, r, iw - hw - 4, 16, "Quita la marca de captura de todas las cajas.");
        r += 24;
        r += ui.wrapped(lx, r, iw, "Para bloquear o marcar cajas de captura una a una, usa el candado y la Poké Ball junto a la barra de cajas.", Ui.TEXT_DIM);
    }

    /** Breeders para la wiki de crianza: Ditto sí/no, descargar el .txt y la marca «No usar». */
    private static void breeding(OrganizerPanel p, Ui ui, int lx, int r, int iw) {
        OrganizerConfig c = p.cfg();
        long now = System.currentTimeMillis();
        if (breederCount < 0 || now - breederCountAt > 1000) {
            try {
                breederCount = com.diosesmon.organizerpc.cobblemon.BreederExport.collect(p.pc()).size();
            } catch (Exception e) {
                breederCount = 0;
            }
            breederCountAt = now;
        }
        ui.text(lx, r, "Breeders para la wiki", Ui.TEXT);
        ui.textRight(lx + iw, r, breederCount + " Pokémon", Ui.THEME);
        r += 12;
        r += ui.wrapped(lx, r, iw, "De 1 a 5 IVs en 31 o algún IV en 0, con grupo huevo. Sin cajas bloqueadas, parejas manuales ni «No usar».",
                Ui.TEXT_DIM) + 5;
        int hw = (iw - 4) / 2;
        if (toggle(ui, lx, r, hw, "Incluir Ditto", c.breedDitto, "Sí: los Ditto también salen como breeders.", "No: la wiki nunca los usa.")) {
            c.breedDitto = !c.breedDitto;
            save();
            com.diosesmon.organizerpc.cobblemon.BreederExport.markDirty();
            breederCount = -1;
        }
        boolean any = !c.noWiki.isEmpty();
        if (ui.button(lx + hw + 4, r, iw - hw - 4, 16, any ? "Quitar «No usar»: " + c.noWiki.size() : "Sin marcas «No usar»",
                any ? Ui.Style.NORMAL : Ui.Style.GHOST) && any) {
            c.noWiki.clear();
            save();
            com.diosesmon.organizerpc.cobblemon.BreederExport.markDirty();
            breederCount = -1;
            PanelState.status("Ya no hay Pokémon marcados «No usar».", Ui.TEXT_DIM);
        }
        ui.tooltip(lx + hw + 4, r, iw - hw - 4, 16, "Para marcar uno: botón de parejas, la love ball;", "ficha «No usar» y click en el Pokémon.");
        r += 22;
        if (ui.button(lx, r, iw, 18, "Descargar .txt de breeders", Ui.Style.PRIMARY)) {
            try {
                int n = com.diosesmon.organizerpc.cobblemon.BreederExport.write(p.pc());
                com.diosesmon.organizerpc.cobblemon.BreederExport.openFolder();
                PanelState.status("breeders.txt listo, " + n + " Pokémon. Súbelo en la wiki: Mi stock → Importar .txt.", Ui.GREEN);
            } catch (Exception e) {
                PanelState.status("No se pudo guardar breeders.txt: " + e.getMessage(), Ui.BAD);
            }
        }
        ui.tooltip(lx, r, iw, 18, "Guarda organizerpc/breeders.txt en la carpeta", "del juego y la abre. En la wiki: Crianza → Importar.");
        r += 24;
        r = breedBoxes(p, ui, lx, r, iw);
        boolean viewer = com.diosesmon.organizerpc.cobblemon.BreederExport.viewerPresent();
        ui.wrapped(lx, r, iw, viewer
                ? "Tienes WikiDiosesmonViewer: la wiki del juego recibe tus breeders sola cada vez que abres la PC."
                : "Sin WikiDiosesmonViewer solo funciona el .txt. Con ese mod, la wiki del juego los recibe sola.",
                viewer ? Ui.GREEN : Ui.TEXT_DIM);
    }

    /**
     * Qué crianza mover (todas o una), sus 2 cajas (− / + para cambiarlas, × para quitar) y el botón que lleva ahí
     * sus parejas. Cada crianza recuerda sus propias cajas.
     */
    private static int breedBoxes(OrganizerPanel p, Ui ui, int lx, int r, int iw) {
        OrganizerConfig c = p.cfg();
        var wp = com.diosesmon.organizerpc.cobblemon.WikiSync.pairs();
        String sel = p.breedSel();
        {
            java.util.List<com.diosesmon.organizerpc.core.WikiPairs.Crianza> cs = wp.crianzas();
            String name = "Todas";
            int col = Ui.TEXT;
            for (var cr : cs) if (cr.id().equals(sel)) { name = cr.name(); col = cr.color(); }
            ui.small(lx, r + 5, "Crianza", Ui.TEXT_DIM);
            int x0 = lx + 74, w = lx + iw - x0;
            ui.box(x0, r, w, 16, Ui.BTN, Ui.BORDER);
            ui.text(x0 + 20, r + 4, name + (sel.isEmpty() ? " (cajas generales)" : ""), col);
            int k = -1;
            for (int i = 0; i < cs.size(); i++) if (cs.get(i).id().equals(sel)) k = i;
            int dir = 0;
            if (ui.button(x0, r, 16, 16, "<", Ui.Style.NORMAL)) dir = -1;
            if (ui.button(x0 + w - 16, r, 16, 16, ">", Ui.Style.NORMAL)) dir = 1;
            if (dir != 0) {
                int m = cs.size() + 1, nk = Math.floorMod(k + 1 + dir, m) - 1;   // -1 = todas
                c.breedSel = nk < 0 ? "" : cs.get(nk).id();
                save();
                sel = p.breedSel();
            }
            ui.tooltip(x0, r, w, 16, "Elige qué crianza mover a sus cajas.", "Cada crianza recuerda sus propias cajas.");
            r += 21;
        }
        java.util.List<Integer> boxes = c.boxesFor(sel);
        ui.small(lx, r + 5, "Sus cajas", Ui.TEXT_DIM);
        int x0 = lx + 74, cw = (lx + iw - x0 - 4) / 2;
        for (int i = 0; i < 2; i++) {
            int bx = x0 + i * (cw + 4);
            if (i < boxes.size()) {
                int box = boxes.get(i);
                ui.box(bx, r, cw, 16, Ui.BTN, Ui.BORDER);
                ui.text(bx + 4, r + 4, "Caja " + (box + 1), Ui.TEXT);
                int px = bx + cw - 45;
                if (ui.button(px, r, 14, 16, "−", Ui.Style.NORMAL)) moveBreedBox(p, boxes, i, -1);
                if (ui.button(px + 15, r, 14, 16, "+", Ui.Style.NORMAL)) moveBreedBox(p, boxes, i, 1);
                if (ui.button(px + 30, r, 14, 16, "×", Ui.Style.DANGER)) {
                    boxes.remove(i);
                    save();
                    return r + 22;
                }
                ui.tooltip(bx, r, cw - 46, 16, "Aquí van las parejas que elige la wiki.", "Organizar y ordenar no tocan esta caja.");
            } else {
                if (ui.button(bx, r, cw, 16, "+ Elegir caja", Ui.Style.NORMAL)) {
                    int box = p.currentBox();
                    while (boxes.contains(box) || c.lockedBoxes.contains(box)) box = (box + 1) % Math.max(1, p.boxCount());
                    boxes.add(box);
                    save();
                }
                ui.tooltip(bx, r, cw, 16, "Empieza con la caja abierta; cámbiala con − y +.");
                break;
            }
        }
        r += 21;
        int n = wp.memberCount(sel);
        boolean can = !boxes.isEmpty() && n > 0;
        String what = sel.isEmpty() ? "todas las crianzas" : "esta crianza";
        if (ui.button(lx, r, iw, 18, n > 0 ? "Mover " + what + " a sus cajas: " + n : "La wiki todavía no eligió parejas",
                can ? Ui.Style.PRIMARY : Ui.Style.GHOST) && can) {
            p.startBreedMove();
        }
        ui.tooltip(lx, r, iw, 18, "Una pareja por fila, en orden: C1, C2, C3…", "Antes ves la vista previa y confirmas.");
        return r + 24;
    }

    private static void moveBreedBox(OrganizerPanel p, java.util.List<Integer> boxes, int i, int d) {
        OrganizerConfig c = p.cfg();
        int n = Math.max(1, p.boxCount()), box = boxes.get(i);
        for (int k = 0; k < n; k++) {
            box = Math.floorMod(box + d, n);
            if (!boxes.contains(box) && !c.lockedBoxes.contains(box)) {
                boxes.set(i, box);
                save();
                return;
            }
        }
    }

    private static void sizes(OrganizerPanel p, Ui ui, int lx, int r, int iw) {
        OrganizerConfig c = p.cfg();
        double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int maxE = Math.max(1, (int) Math.floor(gs));
        int now = Math.max(1, Math.round((float) (p.k * gs)));
        ui.text(lx, r, "Tamaño del organizador", Ui.TEXT);
        ui.textRight(lx + iw, r, "Ahora x" + now + " · juego x" + (int) gs, Ui.TEXT_DIM);
        r += 12;
        String[] labels = {"Auto", "x1", "x2", "x3", "x4"};
        int cw = (iw - 4 * 3) / 5;
        for (int i = 0; i < 5; i++) {
            int bx = lx + i * (cw + 3);
            int bwid = i == 4 ? iw - 4 * (cw + 3) : cw;
            boolean possible = i == 0 || i <= maxE;
            if (ui.toggleButton(bx, r, bwid, 16, labels[i], c.uiSize == i) && possible) {
                c.uiSize = i;
                save();
            }
            if (!possible) ui.round(bx + 1, r + 1, bwid - 2, 14, 0x90000000);
            ui.tooltip(bx, r, bwid, 16, i == 0 ? new String[]{"Lo más grande que quepa alrededor de la PC,", "nunca más pequeño que x2."}
                    : possible ? new String[]{"Cada píxel del organizador ocupa " + i + " píxeles."}
                    : new String[]{"Tu escala de GUI es x" + maxE + ": no se puede más grande."});
        }
        r += 21;
        r += ui.wrapped(lx, r, iw, "Si no cabe alrededor de la PC (escala x3 o x4), la barra se pega arriba y el panel tapa un poco la parte del equipo.", Ui.TEXT_DIM) + 6;

        ui.text(lx, r, "Marcas de IVs", Ui.TEXT);
        r += 12;
        String[] sizes = {"Pequeño", "Mediano", "Grande"};
        int sw = (iw - 2 * 3) / 3;
        for (int i = 0; i < 3; i++) {
            int bx = lx + i * (sw + 3);
            if (ui.toggleButton(bx, r, i == 2 ? iw - 2 * (sw + 3) : sw, 16, sizes[i], c.ivMarkSize == i)) {
                c.ivMarkSize = i;
                save();
            }
        }
    }

    private static void theme(OrganizerPanel p, Ui ui, int lx, int r, int iw) {
        OrganizerConfig c = p.cfg();
        ui.small(lx, r, "Temas", Ui.TEXT_DIM);
        r += 9;
        int cols = 3, pw = (iw - (cols - 1) * 3) / cols;
        for (int i = 0; i < Ui.PRESETS.length; i++) {
            Ui.Preset pr = Ui.PRESETS[i];
            int bx = lx + (i % cols) * (pw + 3), by = r + (i / cols) * 19;
            boolean sel = (c.themeBase | 0xFF000000) == pr.base() && (c.themeAccent | 0xFF000000) == pr.accent();
            boolean hov = ui.hover(bx, by, pw, 16);
            ui.box(bx, by, pw, 16, pr.base(), sel ? 0xFFFFFFFF : hov ? pr.accent() : Ui.shade(pr.base(), -0.5F));
            ui.round(bx + 4, by + 4, 8, 8, pr.accent());
            ui.text(bx + 16, by + 4, pr.name(), Ui.luminance(pr.base()) > 0.55F ? 0xFF15181C : 0xFFF2F3F5);
            if (ui.area(bx, by, pw, 16)) {
                c.themeBase = pr.base();
                c.themeAccent = pr.accent();
                Ui.syncTheme();
                p.syncHexFields();
                save();
            }
        }
        r += ((Ui.PRESETS.length + cols - 1) / cols) * 19 + 6;

        r = hexRow(p, ui, lx, r, iw, "Color de fondo", p.hexBaseField, c.themeBase);
        r = hexRow(p, ui, lx, r, iw, "Color de acento", p.hexAccentField, c.themeAccent);
        ui.small(lx, r + 1, "Escribe el color en hexadecimal, por ejemplo #4E545A.", Ui.TEXT_DIM);
        r += 12;
        if (ui.button(lx, r, iw, 16, "Restablecer (tema PC)", Ui.Style.NORMAL)) {
            c.themeBase = Ui.PRESETS[0].base();
            c.themeAccent = Ui.PRESETS[0].accent();
            Ui.syncTheme();
            p.syncHexFields();
            save();
        }
    }

    /** Fila «Nombre [#RRGGBB] ■»: el texto lo pinta el campo de Minecraft. */
    private static int hexRow(OrganizerPanel p, Ui ui, int lx, int r, int iw, String label, net.minecraft.client.gui.widget.TextFieldWidget f, int color) {
        ui.text(lx, r + 4, label, Ui.TEXT);
        int fx = lx + iw - 110, fw = 80;
        boolean bad = com.diosesmon.organizerpc.config.ConfigStore.hex(f.getText()) == null;
        ui.box(fx, r, fw, 16, Ui.FIELD, f.isFocused() ? Ui.THEME : bad ? Ui.RED : Ui.BORDER);
        f.setX(fx + 5);
        f.setY(r + 4);
        f.setWidth(fw - 10);
        ui.box(lx + iw - 24, r, 24, 16, color | 0xFF000000, 0xFFFFFFFF);
        ui.tooltip(fx, r, fw, 16, "Click y escribe un color: #RRGGBB");
        return r + 20;
    }
}
