package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.OrganizerPCClient;
import net.minecraft.item.ItemStack;

import java.util.function.Function;

/** Tutorial guiado: oscurece la pantalla y señala cada parte del organizador. */
final class Tutorial {
    private record Step(ItemStack icon, String title, String text, Function<OrganizerPanel, int[]> rect) {
    }

    private static final Step[] STEPS = {
            new Step(Icons.POKE_BALL, "Bienvenido al organizador",
                    "Ordena tu PC solo, dentro de la propia PC. Te lo enseño en un momento.",
                    p -> new int[]{p.sx, p.sy, p.sw, p.sh}),
            new Step(Icons.BOX, "Barra de cajas",
                    "Cada cuadro es una caja de tu PC. Click para ir a ella; la rueda del ratón también sirve. El color es su categoría.",
                    p -> new int[]{p.tbX + 2, p.stripY - 2, p.tbW - 4, p.stripRows * 18 + 1}),
            new Step(Icons.TAG, "Categorías",
                    "Una categoría dice qué Pokémon van juntos (IVs en 31 o en 0, cuántos 31, grupo huevo) y en qué cajas. Prueba la plantilla 0x31 … 6x31.",
                    p -> new int[]{p.sx, p.sy, p.sw, p.sh}),
            new Step(Icons.PC, "Organizar",
                    "La caja de la PC te enseña cómo quedará y a la derecha ves quién llega y quién se va. Nada se mueve hasta que pulses Mover.",
                    p -> p.rOrganize),
            new Step(Icons.BOX, "Bloquear cajas",
                    "Activa el candado y toca cajas: las bloqueadas no se tocan nunca, ningún Pokémon entra ni sale.",
                    p -> p.rLock),
            new Step(Icons.POKE_BALL, "Cajas de captura",
                    "Marca las cajas donde guardas los Pokémon nuevos. El botón «Capturas» los reparte en tus categorías y esas cajas nunca reciben Pokémon.",
                    p -> p.rCapture),
            new Step(Icons.HIDE, "Ocultar",
                    "Esconde todo el organizador. Para volver, pulsa la Poké Ball que queda encima de la PC.",
                    p -> p.rHide),
            new Step(Icons.MOVE, "Mover a mano",
                    "Abre la pestaña Mover, haz click en los Pokémon para marcarlos, elige la caja destino y pulsa Mover.",
                    p -> p.rMove),
            new Step(Icons.BOOK, "AFK",
                    "Si está en amarillo, estás AFK. Click para ponerte AFK o para volver.",
                    p -> p.rAfk),
            new Step(Icons.EGG, "Grupo huevo",
                    "Actívalo y pasa el ratón por un Pokémon de la caja: verás a qué grupos huevo pertenece.",
                    p -> p.rEgg),
            new Step(Icons.IVS, "Marcas de IVs",
                    "Enciende o apaga los cuadraditos de IVs de cada Pokémon: relleno = 31, hueco = 0, el número es cuántos 31 tiene.",
                    p -> p.rIvs),
            new Step(Icons.SEARCH, "Buscar",
                    "Escribe una especie, un grupo huevo (campo, hada…) o un IV (ps31, vel0): las cajas que tienen alguno se iluminan. El embudo abre más filtros.",
                    p -> p.rSearch),
            new Step(Icons.BORDERS, "Bordes",
                    "Resalta en la PC los legendarios (borde dorado con estrella), los shinys (borde arcoíris) y los muy raros (borde morado).",
                    p -> p.rPairs),
            new Step(Icons.PAIR, "Crianza",
                    "Junto a AFK: elige qué crianza de la wiki mover y a qué cajas (cada una en las suyas), Ditto, muy raros y el .txt de breeders.",
                    p -> p.rBreed),
            new Step(Icons.SETTINGS, "Ajustes",
                    "Marcas de IVs, tamaños de la interfaz, tema de colores y cómo se organiza la PC.",
                    p -> p.rSettings),
            new Step(Icons.BOOK, "Tutoriales",
                    "Pulsa el libro cuando quieras para repetir esto o para crear una categoría paso a paso.",
                    p -> p.rBook),
    };

    private Tutorial() {
    }

    private static void close() {
        PanelState.tutorialStep = -1;
        OrganizerPCClient.config().tutorialDone = true;
        OrganizerPCClient.saveConfig();
    }

    static void layout(OrganizerPanel p, Ui ui) {
        int i = PanelState.tutorialStep;
        if (i < 0 || i >= STEPS.length) {
            close();
            return;
        }
        Step s = STEPS[i];
        int[] r = s.rect().apply(p);
        int W = p.vw, H = p.vh;

        // Oscurecer todo menos la zona señalada.
        int dim = 0xB8000000;
        ui.rect(0, 0, W, r[1] - 3, dim);
        ui.rect(0, r[1] + r[3] + 3, W, H - (r[1] + r[3] + 3), dim);
        ui.rect(0, r[1] - 3, r[0] - 3, r[3] + 6, dim);
        ui.rect(r[0] + r[2] + 3, r[1] - 3, W - (r[0] + r[2] + 3), r[3] + 6, dim);
        boolean pulse = (System.currentTimeMillis() / 450) % 2 == 0;
        ui.roundBorder(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, pulse ? Ui.GOLD : 0xFFB38B2E);

        int cw = 190;
        int textH = ui.wrappedHeight(cw - 14, s.text());
        int ch = 22 + textH + 26;
        int cx = Math.max(4, Math.min(W - cw - 4, r[0] + r[2] / 2 - cw / 2));
        int cy;
        if (r[2] < 200 && r[3] > 100) { // panel lateral: tarjeta a su izquierda
            cx = Math.max(4, r[0] - cw - 10);
            cy = Math.max(4, Math.min(H - ch - 4, r[1] + 20));
        } else if (r[1] + r[3] + 10 + ch < H) {
            cy = r[1] + r[3] + 10;
        } else if (r[1] - 10 - ch > 0) {
            cy = r[1] - 10 - ch;
        } else {
            cy = (H - ch) / 2;
        }
        ui.panel(cx, cy, cw, ch);
        ui.icon(s.icon(), cx + 7, cy + 6, 12);
        ui.text(cx + 23, cy + 8, s.title(), Ui.TEXT);
        ui.rect(cx + 6, cy + 20, cw - 12, 1, 0x26FFFFFF);
        ui.wrapped(cx + 7, cy + 25, cw - 14, s.text(), Ui.TEXT);

        // Puntos de progreso.
        for (int k = 0; k < STEPS.length; k++) {
            ui.round(cx + 8 + k * 7, cy + ch - 13, 4, 4, k == i ? Ui.GOLD : Ui.LINE);
        }
        boolean last = i == STEPS.length - 1;
        int by = cy + ch - 19;
        if (ui.button(cx + cw - 76, by, 70, 14, last ? "Empezar" : "Siguiente", last ? Ui.Style.PRIMARY : Ui.Style.SELECTED)) {
            if (last) close();
            else PanelState.tutorialStep++;
        }
        if (ui.button(cx + cw - 124, by, 45, 14, "Saltar", Ui.Style.GHOST)) {
            close();
            return;
        }
        if (i > 0 && ui.button(cx + cw - 142, by, 16, 14, "◀", Ui.Style.GHOST)) PanelState.tutorialStep--;
        ui.consume();
    }
}
