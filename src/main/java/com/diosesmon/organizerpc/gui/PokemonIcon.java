package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.client.gui.pc.StorageWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.gui.DrawContext;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Dibuja un Pokémon con el mismo render que usa la PC de Cobblemon (25x25 px).
 *
 * Importante: el slot de Cobblemon recorta el modelo con una zona de recorte en coordenadas
 * absolutas de pantalla calculadas a partir de su posición. Por eso el slot se coloca en la
 * posición real y nunca se traslada ni escala con la matriz (si no, el modelo queda recortado).
 */
final class PokemonIcon {
    static final int SIZE = StorageSlot.SIZE;

    /** Espacio de caja que muestra el Pokémon que le indiquemos. */
    static final class Slot extends StorageSlot {
        final Pokemon mon;

        Slot(StorageWidget parent, Pokemon mon) {
            super(0, 0, parent, button -> { });
            this.mon = mon;
        }

        @Override
        public Pokemon getPokemon() {
            return mon;
        }
    }

    // Un slot por Pokémon: cada uno guarda su propio estado de animación.
    private static final Map<Pokemon, Slot> SLOTS = new WeakHashMap<>();

    private PokemonIcon() {
    }

    static void draw(DrawContext ctx, StorageWidget parent, Pokemon mon, int x, int y, float delta) {
        if (mon == null) return;
        Slot slot = SLOTS.get(mon);
        if (slot == null) {
            slot = new Slot(parent, mon);
            SLOTS.put(mon, slot);
        }
        // Con el organizador escalado, el slot va en su posición real (el recorte de Cobblemon
        // es absoluto) y el modelo se encoge alrededor de esa esquina.
        float k = Ui.scale;
        int rx = Math.round(x * k), ry = Math.round(y * k);
        slot.setX(rx);
        slot.setY(ry);
        ctx.getMatrices().push();
        if (k != 1F) {
            ctx.getMatrices().scale(1F / k, 1F / k, 1F);
            ctx.getMatrices().translate(rx, ry, 0);
            ctx.getMatrices().scale(k, k, 1F);
            ctx.getMatrices().translate(-rx, -ry, 0);
        }
        // Solo profundidad (no cambia x/y): el modelo queda por encima del fondo del panel.
        ctx.getMatrices().translate(0, 0, 60);
        slot.render(ctx, -9999, -9999, delta);
        ctx.getMatrices().pop();
    }
}
