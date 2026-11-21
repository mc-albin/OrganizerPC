package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.SortMode;

/**
 * Elegir hasta dos órdenes: el 1º manda y el 2º desempata.
 * {@code sel} es un array de dos posiciones que se modifica en el sitio.
 */
final class SortPicker {
    private SortPicker() {
    }

    private static String label(SortMode m, String noneLabel) {
        return m == SortMode.NONE ? noneLabel : m.label;
    }

    private static String[] tip(SortMode m) {
        return switch (m) {
            case NONE -> new String[]{"Sin orden: solo se juntan, sin huecos,", "en el mismo orden en que estaban."};
            case IVS_ASC -> new String[]{"Primero 1x31, luego 2x31… 6x31 (los 0x31 al final).", "Si no eliges 2º: a igualdad, primero 31 en PS,", "luego Ataque… (no importa la especie)."};
            case IVS_DESC -> new String[]{"Primero 6x31, luego 5x31…"};
            case IV_STATS -> new String[]{"Primero los que tienen 31 en PS, luego en", "Ataque, Defensa, At. Esp., Def. Esp., Velocidad."};
            case DEX -> new String[]{"Por número de Pokédex."};
            case LEVEL_DESC -> new String[]{"De mayor a menor nivel."};
            case EGG_GROUP -> new String[]{"Junta a los del mismo grupo huevo", "(p. ej. todos los Campo seguidos)."};
            case LIVING_DEX -> new String[]{"Living dex: cada familia en orden de Pokédex", "(Charmander, Charmeleon, Charizard).", "Si te falta uno, su hueco queda vacío.", "Los repetidos van al final."};
            case IV_COLUMNS_FILL -> new String[]{"Vertical, columnas completas: de arriba abajo", "sin huecos (1x31 por estadística, luego 2x31+)."};
            case IV_COLUMNS -> new String[]{"Vertical: llena las columnas de arriba abajo.", "Cada IV en 31 (PS, Ata, Def…) empieza columna nueva:", "13 con PS = 2 columnas y media; Ata va en la siguiente."};
        };
    }

    /** Rejilla de fichas con insignias «1» y «2». Devuelve la altura usada. */
    static int grid(Ui ui, int x, int y, int w, SortMode[] sel, String noneLabel) {
        ui.small(x, y, "Elige hasta 2: el 1º manda, el 2º desempata", Ui.TEXT_DIM);
        int r = y + 9;
        // Las dos variantes de columnas son una sola ficha; la variante se elige debajo.
        SortMode[] modes = java.util.Arrays.stream(SortMode.values()).filter(m -> m != SortMode.IV_COLUMNS_FILL).toArray(SortMode[]::new);
        int cw = (w - 3) / 2;
        for (int i = 0; i < modes.length; i++) {
            SortMode m = modes[i];
            int bx = x + (i % 2) * (cw + 3), by = r + (i / 2) * 14;
            int bw = i % 2 == 0 ? cw : w - cw - 3;
            int rank = m == SortMode.NONE ? (sel[0] == SortMode.NONE ? 1 : 0) : same(sel[0], m) ? 1 : same(sel[1], m) ? 2 : 0;
            if (ui.chip(bx, by, bw, 12, label(m, noneLabel), rank > 0, rank == 2 ? Ui.PURPLE : Ui.BLUE)) {
                click(sel, m);
            }
            if (rank > 0 && m != SortMode.NONE) {
                ui.round(bx + 2, by + 2, 8, 8, rank == 1 ? Ui.GOLD : Ui.PURPLE);
                ui.small(bx + 4, by + 3, String.valueOf(rank), 0xFF14110A);
            }
            ui.tooltip(bx, by, bw, 12, tip(m));
        }
        int ry = r + ((modes.length + 1) / 2) * 14;
        int extra = 0;
        int ci = sel[0].columns() ? 0 : sel[1].columns() ? 1 : -1;
        if (ci >= 0) {
            // Solo con «Columnas por IV»: completar las columnas o dejar huecos.
            int hw = (w - 3) / 2;
            if (ui.chip(x, ry, hw, 12, "Completas", sel[ci] == SortMode.IV_COLUMNS_FILL, Ui.GREEN)) sel[ci] = SortMode.IV_COLUMNS_FILL;
            ui.tooltip(x, ry, hw, 12, "Columnas completas: de arriba abajo, sin huecos.");
            if (ui.chip(x + hw + 3, ry, w - hw - 3, 12, "Con huecos", sel[ci] == SortMode.IV_COLUMNS, Ui.GREEN)) sel[ci] = SortMode.IV_COLUMNS;
            ui.tooltip(x + hw + 3, ry, w - hw - 3, 12, "Cada 1x31 (PS, Ata…) empieza columna nueva;", "los de 2x31 o más van juntos.", "Los que no caben van a las cajas de captura.");
            ry += 15;
            extra = 15;
        }
        ui.small(x, ry, "Resultado:", Ui.GOLD);
        int h = 0;
        for (var line : ui.tr.wrapLines(net.minecraft.text.Text.literal(SortMode.describe(sel[0], sel[1])), (int) ((w - 2) / 0.75F))) {
            if (ui.drawing()) {
                ui.ctx.getMatrices().push();
                ui.ctx.getMatrices().translate(x, ry + 8 + h, 0);
                ui.ctx.getMatrices().scale(0.75F, 0.75F, 1F);
                ui.ctx.drawText(ui.tr, line, 0, 0, Ui.TEXT, false);
                ui.ctx.getMatrices().pop();
            }
            h += 8;
        }
        return 9 + ((modes.length + 1) / 2) * 14 + extra + 9 + h;
    }

    /** Las dos variantes de columnas cuentan como el mismo orden. */
    private static boolean same(SortMode a, SortMode b) {
        return a == b || (a != null && b != null && a.columns() && b.columns());
    }

    private static void click(SortMode[] sel, SortMode m) {
        if (m == SortMode.NONE) {
            sel[0] = SortMode.NONE;
            sel[1] = SortMode.NONE;
        } else if (same(sel[0], m)) {
            sel[0] = sel[1];
            sel[1] = SortMode.NONE;
        } else if (same(sel[1], m)) {
            sel[1] = SortMode.NONE;
        } else if (sel[0] == SortMode.NONE) {
            sel[0] = m;
        } else {
            sel[1] = m;
        }
    }

    /** Versión compacta: dos botones «1º» y «2º» que cambian con cada click. Altura 13. */
    static void compact(Ui ui, int x, int y, int w, SortMode[] sel, String noneLabel) {
        int hw = (w - 3) / 2;
        String a = "1º " + label(sel[0], noneLabel);
        if (ui.button(x, y, hw, 13, a, sel[0] == SortMode.NONE ? Ui.Style.NORMAL : Ui.Style.SELECTED)) {
            sel[0] = sel[0].next();
            if (sel[0] == SortMode.NONE) sel[1] = SortMode.NONE;
            if (sel[1] == sel[0]) sel[1] = SortMode.NONE;
        }
        ui.tooltip(x, y, hw, 13, "Orden principal (click para cambiar).");
        if (sel[0].columns()) {
            // Con columnas, el 2º botón elige si se completan o no.
            boolean fill = sel[0] == SortMode.IV_COLUMNS_FILL;
            if (ui.button(x + hw + 3, y, w - hw - 3, 13, fill ? "Completas" : "Con huecos", Ui.Style.SELECTED)) {
                sel[0] = fill ? SortMode.IV_COLUMNS : SortMode.IV_COLUMNS_FILL;
                sel[1] = SortMode.NONE;
            }
            ui.tooltip(x + hw + 3, y, w - hw - 3, 13, "Sí: columnas seguidas, sin huecos.", "No: cada 1x31 empieza columna; 2x31+ juntos;", "los que no caben, a captura.");
            return;
        }
        String b = "2º " + (sel[1] == SortMode.NONE ? "—" : sel[1].label);
        boolean enabled = sel[0] != SortMode.NONE;
        if (ui.button(x + hw + 3, y, w - hw - 3, 13, b, sel[1] == SortMode.NONE ? Ui.Style.NORMAL : Ui.Style.SELECTED) && enabled) {
            do {
                sel[1] = sel[1].next();
            } while (sel[1] == sel[0] && sel[1] != SortMode.NONE);
        }
        ui.tooltip(x + hw + 3, y, w - hw - 3, 13, "Desempate (click para cambiar).");
    }
}
