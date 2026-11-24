package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.cobblemon.SpeciesTraits;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.UltraRare;
import net.minecraft.client.gui.DrawContext;

/**
 * Bordes visuales en las casillas de la PC: legendarios (borde dorado con estrella), shinys (borde arcoíris que
 * se mueve) y muy raros (borde morado). Se eligen en la ventanita del botón de bordes, junto a la barra de cajas.
 */
final class Borders {
    static final int GOLD = 0xFFFFC83D, PURPLE = 0xFFB57BFF;

    private Borders() {
    }

    static boolean any(OrganizerConfig c) {
        return c.borderLegend || c.borderShiny || c.borderUltra;
    }

    /** Borde de una casilla (coordenadas reales de la PC). */
    static void slot(Ui ui, DrawContext ctx, int x, int y, int size, Pokemon mon, OrganizerConfig c) {
        boolean shiny = c.borderShiny && mon.getShiny();
        boolean legend = c.borderLegend && SpeciesTraits.legendary(mon);
        boolean ultra = c.borderUltra && !legend && UltraRare.is(mon.getSpecies().getNationalPokedexNumber());
        if (shiny) rainbow(ctx, x, y, size);
        int in = shiny ? 2 : 0;
        if (legend) {
            ui.roundBorder(x + in, y + in, size - 2 * in, size - 2 * in, GOLD);
            ui.roundBorder(x + in + 1, y + in + 1, size - 2 * in - 2, size - 2 * in - 2, Ui.alpha(GOLD, 0x80));
            star(ctx, x + size - 9, y + 1, GOLD);
        } else if (ultra) {
            ui.roundBorder(x + in, y + in, size - 2 * in, size - 2 * in, PURPLE);
            ui.roundBorder(x + in + 1, y + in + 1, size - 2 * in - 2, size - 2 * in - 2, Ui.alpha(PURPLE, 0x70));
            ctx.fill(x + size - 5, y + 2, x + size - 3, y + 4, PURPLE);
        }
    }

    /** Borde de 2 píxeles que cambia de color a lo largo y se va moviendo (efecto tornasol). */
    static void rainbow(DrawContext ctx, int x, int y, int size) {
        float t = (System.currentTimeMillis() % 3000L) / 3000F;
        int n = (size - 1) * 4;
        for (int i = 0; i < n; i += 2) {
            int px, py;
            if (i < size - 1) { px = x + i; py = y; }
            else if (i < 2 * (size - 1)) { px = x + size - 1; py = y + i - (size - 1); }
            else if (i < 3 * (size - 1)) { px = x + size - 1 - (i - 2 * (size - 1)); py = y + size - 1; }
            else { px = x; py = y + size - 1 - (i - 3 * (size - 1)); }
            int col = 0xFF000000 | java.awt.Color.HSBtoRGB((i / (float) n + t) % 1F, 0.75F, 1F);
            // 2x2 hacia dentro: el borde se ve grueso y sigue el contorno
            int ix = px == x ? px : px >= x + size - 1 ? px - 1 : px;
            int iy = py == y ? py : py >= y + size - 1 ? py - 1 : py;
            ctx.fill(ix, iy, ix + 2, iy + 2, col);
        }
    }

    /** Estrellita en la esquina (con sombra para que se lea sobre cualquier Pokémon). */
    static void star(DrawContext ctx, int x, int y, int color) {
        var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(0.85F, 0.85F, 1F);
        ctx.drawText(tr, "★", 0, 0, color, true);
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------ ventanita

    static final int POP_W = 170, POP_H = 92;

    static int[] popupRect(OrganizerPanel p) {
        int[] b = p.rPairs == null ? new int[]{p.tbX + 5, p.stripY, 16, 16} : p.rPairs;
        int x = Math.max(2, Math.min(p.vw - POP_W - 2, b[0]));
        int y = b[1] + b[3] + 3;
        if (y + POP_H > p.vh - 2) y = Math.max(2, p.vh - POP_H - 2);
        return new int[]{x, y, POP_W, POP_H};
    }

    static boolean inPopup(OrganizerPanel p, double mx, double my) {
        if (!PanelState.bordersOpen) return false;
        int[] r = popupRect(p);
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    static void popup(OrganizerPanel p, Ui ui) {
        OrganizerConfig c = p.cfg();
        int[] r = popupRect(p);
        int x = r[0], y = r[1], w = r[2], h = r[3];
        ui.panel(x, y, w, h);
        ui.icon(Icons.BORDERS, x + 5, y + 4, 11);
        ui.text(x + 19, y + 6, "Bordes", Ui.TEXT);
        if (ui.button(x + w - 16, y + 4, 12, 12, "", Ui.Style.GHOST)) PanelState.bordersOpen = false;
        ui.cross(x + w - 13, y + 7, 6, Ui.TEXT_DIM);
        ui.rect(x + 4, y + 18, w - 8, 1, 0x26FFFFFF);
        int lx = x + 6, iw = w - 12, ry = y + 23;
        if (row(ui, lx, ry, iw, "Legendarios", c.borderLegend, GOLD, "Borde dorado con estrella.", "Legendarios y míticos.")) c.borderLegend = !c.borderLegend;
        ry += 20;
        if (row(ui, lx, ry, iw, "Shinys", c.borderShiny, 0, "Borde arcoíris que se mueve.")) c.borderShiny = !c.borderShiny;
        ry += 20;
        if (row(ui, lx, ry, iw, "Muy raros", c.borderUltra, PURPLE, "Borde morado: su aparición más común", "es Muy raro (iniciales, fósiles…).")) c.borderUltra = !c.borderUltra;
    }

    private static boolean row(Ui ui, int x, int y, int w, String label, boolean on, int color, String... tip) {
        boolean clicked = ui.toggleButton(x + 20, y, w - 20, 16, label + ": " + (on ? "Sí" : "No"), on);
        ui.tooltip(x, y, w, 16, tip);
        // Muestra del borde
        if (ui.drawing()) {
            if (color == 0) {
                rainbow(ui.ctx, x, y, 16);
            } else {
                ui.roundBorder(x, y, 16, 16, color);
                ui.roundBorder(x + 1, y + 1, 14, 14, Ui.alpha(color, 0x80));
            }
        }
        if (clicked) OrganizerPCClient.saveConfig();
        return clicked;
    }
}
