package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot;
import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.cobblemon.PcReader;
import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.StatKey;
import net.minecraft.client.gui.DrawContext;

/**
 * Marcas sobre los Pokémon de la caja: barrita de IVs (verde 31, rojo 0), marcados para mover,
 * categoría que se edita y caja bloqueada.
 */
final class SlotMarks {
    private SlotMarks() {
    }

    static int countColor(int perfect) {
        return switch (perfect) {
            case 6 -> Ui.GOLD;
            case 5 -> Ui.GREEN;
            case 4 -> Ui.BLUE;
            case 3 -> Ui.PURPLE;
            default -> 0xFFE0E3E7;
        };
    }

    /**
     * Marca de IVs: un punto del color de cada estadística que tiene 31 y el número total.
     * Si no tiene ningún 31 no se dibuja nada.
     */
    /**
     * Marca de IVs: un cuadradito relleno por cada stat en 31 y uno hueco por cada stat en 0
     * (con el color de la stat), y el número de 31. Si no tiene ni 31 ni 0, no se dibuja nada.
     */
    static void ivBar(DrawContext ctx, int sx, int sy, PokemonInfo info) {
        ivBar(ctx, sx, sy, info, StorageSlot.SIZE);
    }

    /** Igual, para un hueco de {@code size} píxeles: la marca va abajo a la derecha del hueco. */
    static void ivBar(DrawContext ctx, int sx, int sy, PokemonInfo info, int size) {
        Bar bar = Bar.of(info);
        if (bar != null) bar.draw(ctx, sx + size - bar.w - 1, sy + size - bar.h - 1, info);
    }

    /** La marca con su borde derecho en {@code right} y su borde de abajo en {@code bottom}. */
    static void ivBarAbove(DrawContext ctx, int right, int bottom, PokemonInfo info) {
        Bar bar = Bar.of(info);
        if (bar != null) bar.draw(ctx, right - bar.w, bottom - bar.h, info);
    }

    /** Medidas de una marca. El número se escala a píxeles enteros de pantalla (no se deforma en x3). */
    private record Bar(int n, int zeros, int dot, float textScale, int w, int h) {
        static Bar of(PokemonInfo info) {
            if (!info.ivsKnown()) return null;
            int n = info.perfectIvCount();
            int zeros = 0;
            for (StatKey k : StatKey.values()) if (info.iv(k) == 0) zeros++;
            if (n == 0 && zeros == 0) return null;
            var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            int lvl = com.diosesmon.organizerpc.OrganizerPCClient.config().ivMarkSize;
            int dot = lvl == 0 ? 2 : lvl == 1 ? 3 : 4;
            float base = lvl == 0 ? 0.5F : lvl == 1 ? 0.6F : 0.75F;
            double gs = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();
            float textScale = (float) (Math.max(1, Math.round(base * gs)) / gs);
            int bh = Math.max(dot + 2, (int) Math.ceil(8 * textScale) + 1);
            int numW = n > 0 ? (int) Math.ceil(tr.getWidth(String.valueOf(n)) * textScale) + 1 : 0;
            int gap = n > 0 && zeros > 0 ? 1 : 0;
            int w = (n + zeros) * (dot + 1) + gap + 1 + numW + 1;
            return new Bar(n, zeros, dot, textScale, w, bh);
        }

        void draw(DrawContext ctx, int x, int y, PokemonInfo info) {
            var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            ctx.fill(x, y, x + w, y + h, 0xC0000000);
            int dy = y + (h - dot) / 2;
            int px = x + 1;
            for (StatKey k : StatKey.values()) {
                if (info.iv(k) != 31) continue;
                ctx.fill(px, dy, px + dot, dy + dot, Icons.statColor(k));
                px += dot + 1;
            }
            if (n > 0 && zeros > 0) px += 1;
            for (StatKey k : StatKey.values()) {
                if (info.iv(k) != 0) continue;
                int col = Icons.statColor(k);
                if (dot <= 2) {
                    // Muy pequeño: dos píxeles en diagonal (se distingue del relleno).
                    ctx.fill(px, dy, px + 1, dy + 1, col);
                    ctx.fill(px + 1, dy + 1, px + 2, dy + 2, col);
                } else {
                    ctx.drawBorder(px, dy, dot, dot, col);
                }
                px += dot + 1;
            }
            if (n > 0) {
                double gs = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();
                float ty = (float) (Math.round((y + (h - 8 * textScale) / 2F) * gs) / gs);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(px, ty, 0);
                ctx.getMatrices().scale(textScale, textScale, 1F);
                ctx.drawText(tr, String.valueOf(n), 0, 0, countColor(n), false);
                ctx.getMatrices().pop();
            }
        }
    }

    static void render(OrganizerPanel p, DrawContext ctx, int mx, int my) {
        var cfg = p.cfg();
        Category highlight = p.highlightedCategory();
        boolean marking = PanelState.moveOpen;
        boolean locked = p.locked(p.currentBox());
        boolean searching = Search.active();
        if (!cfg.showIvBadges && highlight == null && PanelState.selection.isEmpty() && !locked && !marking && !searching) return;

        Ui ui = new Ui(Ui.Mode.DRAW, ctx, mx, my);
        int size = StorageSlot.SIZE;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        for (BoxStorageSlot slot : p.boxSlots()) {
            Pokemon mon = slot.getPokemon();
            if (mon == null) continue;
            int x = slot.getX(), y = slot.getY();
            PokemonInfo info = PcReader.info(mon);
            if (highlight != null && highlight.matches(info)) {
                ui.roundBorder(x, y, size, size, highlight.color);
                ui.roundBorder(x + 1, y + 1, size - 2, size - 2, Ui.alpha(highlight.color, 0x80));
            }
            boolean sel = PanelState.selection.contains(mon.getUuid());
            if (sel) {
                ui.round(x, y, size, size, Ui.alpha(Ui.GOLD, 0x38));
                ui.roundBorder(x, y, size, size, Ui.GOLD);
                ui.round(x + 2, y + 2, 7, 7, Ui.GOLD);
                ctx.drawText(ui.tr, "✔", x + 2, y + 1, 0xFF14110A, false);
            } else if (marking && !locked && mx >= x && mx < x + size && my >= y && my < y + size) {
                ui.roundBorder(x, y, size, size, Ui.alpha(Ui.GOLD, 0xA0));
            }
            if (cfg.showIvBadges) ivBar(ctx, x, y, info);
            if (searching) {
                // Búsqueda: los que coinciden se iluminan, el resto se oscurece.
                if (Search.hit(mon.getUuid())) {
                    ui.roundBorder(x, y, size, size, Ui.THEME);
                    ui.roundBorder(x + 1, y + 1, size - 2, size - 2, Ui.alpha(Ui.THEME, 0x90));
                } else {
                    ui.round(x + 1, y + 1, size - 2, size - 2, 0xA0000000);
                }
            }
        }
        if (cfg.showIvBadges) p.partyIvMarks(ctx);
        if (locked) {
            int[] g = p.gridRect();
            if (g != null) {
                ui.round(g[0] - 2, g[1] - 2, g[2] + 4, g[3] + 4, 0x16FF0000);
                ui.roundBorder(g[0] - 3, g[1] - 3, g[2] + 6, g[3] + 6, Ui.alpha(Ui.RED, 0xB0));
                ui.box(g[0] + g[2] - 14, g[1] - 7, 13, 12, 0xFF3A181D, Ui.RED);
                ui.lock(g[0] + g[2] - 11, g[1] - 5, 0xFFFF8A8A);
            }
        }
        ctx.getMatrices().pop();
    }
}
