package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.Category;
import net.minecraft.item.ItemStack;

/**
 * Menú de tutoriales y guía interactiva «Crear una categoría»: pregunta qué quieres filtrar,
 * crea la categoría y te lleva paso a paso por el editor. En cada paso solo se puede usar la
 * zona señalada (el resto de la pantalla queda oscurecido).
 */
final class Guide {
    enum Kind { IV_COUNT, IV_STATS, EGG, SPECIES }

    private static final int CW = 200;
    private static final int LAST = 5;

    private static final String[][] OPTIONS = {
            {"Cuántos IVs en 31", "Ej.: todos los 5x31 juntos"},
            {"IVs concretos en 31 o 0", "Ej.: PS en 31 y Ataque en 0"},
            {"Grupo huevo", "Ej.: todos los de Campo"},
            {"Especie", "Ej.: Eevee y su familia"},
    };
    private static final ItemStack[] OPTION_ICONS = {Icons.IVS, Icons.IVS, Icons.EGG, Icons.POKE_BALL};
    private static final String[] NAMES = {"IVs en 31", "IVs concretos", "Grupo huevo", "Especie"};

    private Guide() {
    }

    static boolean active() {
        return PanelState.tutorialMenu || PanelState.guideStep >= 0;
    }

    static void open() {
        PanelState.settingsOpen = false;
        PanelState.tutorialStep = -1;
        PanelState.guideStep = -1;
        PanelState.tutorialMenu = true;
    }

    static void close() {
        PanelState.tutorialMenu = false;
        if (PanelState.guideStep >= 0 && PanelState.stripMode == PanelState.StripMode.ASSIGN) {
            PanelState.stripMode = PanelState.StripMode.NAVIGATE;
        }
        PanelState.guideStep = -1;
    }

    private static String filterKey() {
        return switch (PanelState.guideKind) {
            case IV_COUNT -> "count";
            case IV_STATS -> "ivs";
            case EGG -> "egg";
            case SPECIES -> "species";
        };
    }

    /** Zona señalada en el paso actual (la única que admite clics), o null. */
    static int[] focus(OrganizerPanel p) {
        int s = PanelState.guideStep;
        if (PanelState.tutorialMenu || s <= 0 || PanelState.editing == null) return null;
        return switch (s) {
            case 1 -> p.gName;
            case 2 -> section(p, filterKey());
            case 3 -> new int[]{p.tbX + 2, p.stripY - 2, p.tbW - 4, p.stripRows * 18 + 1};
            case 4 -> section(p, "sort");
            default -> p.gDone;
        };
    }

    /** Sección del editor recortada a la parte visible de la lista. */
    private static int[] section(OrganizerPanel p, String key) {
        int[] l = p.editorList;
        int[] off = p.guideSec.get(key);
        if (l == null) return new int[]{p.sx, p.sy, p.sw, p.sh};
        if (off == null) return l;
        int y1 = Math.max(l[1], p.editorTop + off[0]);
        int y2 = Math.min(l[1] + l[3], p.editorTop + off[1]);
        if (y2 - y1 < 8) return l;
        return new int[]{l[0] - 2, y1, l[2] + 2, y2 - y1};
    }

    private static void go(OrganizerPanel p, int step) {
        PanelState.guideStep = step;
        if (PanelState.editing == null) return;
        PanelState.stripMode = step == 3 ? PanelState.StripMode.ASSIGN : PanelState.StripMode.NAVIGATE;
        String key = step == 2 ? filterKey() : step == 4 ? "sort" : null;
        int[] off = key == null ? null : p.guideSec.get(key);
        if (off != null) PanelState.scroll.put("editor", Math.max(0, off[0] - 2));
    }

    private static void start(OrganizerPanel p, Kind kind) {
        if (PanelState.editing != null) p.finishEditing();
        p.closeMoveWindow();
        PanelState.arrangeOpen = false;
        PanelState.settingsOpen = false;
        Category c = p.newCategory();
        c.name = NAMES[kind.ordinal()];
        p.nameField.setText(c.name);
        PanelState.guideKind = kind;
        go(p, 1);
    }

    static void layout(OrganizerPanel p, Ui ui) {
        if (PanelState.tutorialMenu) {
            menu(p, ui);
            return;
        }
        int s = PanelState.guideStep;
        if (s == 0) {
            question(p, ui);
            return;
        }
        if (PanelState.editing == null) {
            // Pulsó «Listo» en el editor: categoría guardada.
            close();
            PanelState.status("¡Categoría lista! Pulsa «Organizar PC» para colocar los Pokémon.", Ui.GREEN);
            return;
        }
        step(p, ui, s);
    }

    // ------------------------------------------------------------------ menú

    private static void cardHeader(Ui ui, int cx, int cy, int cw, ItemStack icon, String title) {
        ui.icon(icon, cx + 7, cy + 6, 12);
        ui.text(cx + 23, cy + 8, title, Ui.TEXT);
        ui.rect(cx + 6, cy + 20, cw - 12, 1, 0x26FFFFFF);
    }

    private static void menu(OrganizerPanel p, Ui ui) {
        ui.rect(0, 0, p.vw, p.vh, 0xB8000000);
        int ch = 26 + 20 + 20 + 18 + 4;
        int cx = (p.vw - CW) / 2, cy = (p.vh - ch) / 2;
        ui.panel(cx, cy, CW, ch);
        cardHeader(ui, cx, cy, CW, Icons.BOOK, "Tutoriales");
        int bx = cx + 7, bw = CW - 14, by = cy + 26;
        if (ui.button(bx, by, bw, 17, "Cómo funciona el organizador", Icons.PC, Ui.Style.SELECTED)) {
            PanelState.tutorialMenu = false;
            if (PanelState.editing != null) p.finishEditing();
            p.closeMoveWindow();
            PanelState.tutorialStep = 0;
        }
        by += 20;
        if (ui.button(bx, by, bw, 17, "Crear una categoría paso a paso", Icons.TAG, Ui.Style.PRIMARY)) {
            PanelState.tutorialMenu = false;
            PanelState.guideStep = 0;
        }
        by += 20;
        if (ui.button(bx, by, bw, 15, "Cerrar", Ui.Style.GHOST)) close();
        ui.consume();
    }

    // ------------------------------------------------------------------ pregunta

    private static void question(OrganizerPanel p, Ui ui) {
        ui.rect(0, 0, p.vw, p.vh, 0xB8000000);
        int optH = 24;
        int ch = 26 + 12 + OPTIONS.length * (optH + 3) + 20;
        int cx = (p.vw - CW) / 2, cy = (p.vh - ch) / 2;
        ui.panel(cx, cy, CW, ch);
        cardHeader(ui, cx, cy, CW, Icons.TAG, "Crear una categoría");
        ui.text(cx + 7, cy + 25, "¿Qué quieres filtrar?", Ui.GOLD);
        int by = cy + 38, bx = cx + 7, bw = CW - 14;
        for (int i = 0; i < OPTIONS.length; i++) {
            boolean hov = ui.hover(bx, by, bw, optH);
            ui.box(bx, by, bw, optH, hov ? Ui.BTN_HOVER : Ui.BTN, hov ? Ui.BORDER_HOVER : Ui.BORDER);
            ui.icon(OPTION_ICONS[i], bx + 5, by + 6, 12);
            ui.text(bx + 22, by + 4, OPTIONS[i][0], 0xFFFFFFFF);
            ui.small(bx + 22, by + 14, OPTIONS[i][1], Ui.TEXT_DIM);
            if (ui.area(bx, by, bw, optH)) {
                start(p, Kind.values()[i]);
                return;
            }
            by += optH + 3;
        }
        if (ui.button(cx + CW - 67, cy + ch - 18, 60, 14, "Cancelar", Ui.Style.GHOST)) close();
        ui.consume();
    }

    // ------------------------------------------------------------------ pasos

    private static String[] texts(OrganizerPanel p, int s) {
        Category c = PanelState.editing;
        return switch (s) {
            case 1 -> new String[]{"Nombre",
                    "Escribe aquí el nombre de tu categoría (por ejemplo «5x31 Campo»). El cuadrado de la derecha cambia su color."};
            case 2 -> {
                String n = "Ahora mismo cumplen " + p.matchCounts.getOrDefault(c.id, 0) + " Pokémon.";
                yield switch (PanelState.guideKind) {
                    case IV_COUNT -> new String[]{"¿Cuántos 31?",
                            "Toca cuántos IVs en 31 quieres (por ejemplo 5). Luego elige «Exactamente» o «o más». " + n};
                    case IV_STATS -> new String[]{"IVs concretos",
                            "Toca cada estadística para cambiarla: da igual → 31 → 0. Por ejemplo PS en 31 y Ataque en 0. " + n};
                    case EGG -> new String[]{"Grupo huevo",
                            "Marca uno o varios grupos. «Alguno»: entra si tiene cualquiera. «Solo estos»: sin grupos de más. " + n};
                    case SPECIES -> new String[]{"Especie",
                            "Escribe el nombre en el buscador y pulsa + (solo esa) o + familia (con sus evoluciones). " + n};
                };
            }
            case 3 -> new String[]{"Cajas",
                    "Toca en la barra de arriba las cajas donde irán. Puedes elegir varias; tócala otra vez para quitarla. "
                            + (c.boxes.isEmpty() ? "Aún ninguna." : "Elegidas: " + c.boxes.size() + ".")};
            case 4 -> new String[]{"Orden (opcional)",
                    "Cómo se ordenan dentro de sus cajas: por IVs, por grupo huevo… El 1º manda y el 2º desempata."};
            default -> new String[]{"¡Listo!",
                    "Pulsa Listo para guardarla. Después «Organizar PC» los coloca en sus cajas (antes verás la vista previa)."};
        };
    }

    private static void step(OrganizerPanel p, Ui ui, int s) {
        int[] r = focus(p);
        if (r == null) return;
        int W = p.vw, H = p.vh;
        int dim = 0xB8000000;
        ui.rect(0, 0, W, r[1] - 3, dim);
        ui.rect(0, r[1] + r[3] + 3, W, H - (r[1] + r[3] + 3), dim);
        ui.rect(0, r[1] - 3, r[0] - 3, r[3] + 6, dim);
        ui.rect(r[0] + r[2] + 3, r[1] - 3, W - (r[0] + r[2] + 3), r[3] + 6, dim);
        boolean pulse = (System.currentTimeMillis() / 450) % 2 == 0;
        ui.roundBorder(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, pulse ? Ui.GOLD : 0xFFB38B2E);

        String[] t = texts(p, s);
        int textH = ui.wrappedHeight(CW - 14, t[1]);
        int ch = 22 + textH + 26;
        int cx, cy;
        if (r[0] >= p.sx - 4) { // dentro del panel derecho: tarjeta a su izquierda
            cx = Math.max(4, p.sx - CW - 10);
            cy = Math.max(4, Math.min(H - ch - 4, r[1]));
        } else { // barra de cajas: tarjeta debajo
            cx = Math.max(4, Math.min(W - CW - 4, r[0] + r[2] / 2 - CW / 2));
            cy = Math.min(H - ch - 4, r[1] + r[3] + 10);
        }
        ui.panel(cx, cy, CW, ch);
        cardHeader(ui, cx, cy, CW, Icons.TAG, t[0]);
        ui.wrapped(cx + 7, cy + 25, CW - 14, t[1], Ui.TEXT);

        for (int k = 1; k <= LAST; k++) {
            ui.round(cx + 8 + (k - 1) * 7, cy + ch - 13, 4, 4, k == s ? Ui.GOLD : Ui.LINE);
        }
        int by = cy + ch - 19;
        boolean last = s == LAST;
        if (ui.button(cx + CW - 76, by, 70, 14, last ? "Terminar" : "Siguiente", last ? Ui.Style.PRIMARY : Ui.Style.SELECTED)) {
            if (last) {
                p.finishEditing();
                close();
                PanelState.status("¡Categoría lista! Pulsa «Organizar PC» para colocar los Pokémon.", Ui.GREEN);
            } else {
                go(p, s + 1);
            }
            return;
        }
        if (ui.button(cx + CW - 124, by, 45, 14, "Salir", Ui.Style.GHOST)) {
            close();
            return;
        }
        if (s > 1 && ui.button(cx + CW - 142, by, 16, 14, "◀", Ui.Style.GHOST)) {
            go(p, s - 1);
            return;
        }
        if (ui.hover(cx, cy, CW, ch)) ui.consume();
    }
}
