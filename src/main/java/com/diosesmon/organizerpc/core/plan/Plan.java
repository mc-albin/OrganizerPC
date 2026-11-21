package com.diosesmon.organizerpc.core.plan;

import com.diosesmon.organizerpc.core.PokemonInfo;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de planificar: operaciones a ejecutar, la PC tal como quedará ({@code after})
 * y el resumen para la vista previa.
 */
public record Plan(List<Operation> operations, List<Entry> entries, List<PokemonInfo> noSpace,
                   int scannedCount, PcModel before, PcModel after) {

    /**
     * Un Pokémon que cambia de ubicación.
     *
     * @param categoryId categoría asignada, o null si va a "sin categoría" / movimiento manual.
     * @param matchingCategoryIds todas las categorías que cumple (para resolver conflictos a mano).
     */
    public record Entry(UUID uuid, PokemonInfo info, Pos from, Pos to, String categoryId,
                        String categoryName, List<String> matchingCategoryIds, Reason reason,
                        String leftCategoryName) {
    }

    /** Por qué se mueve un Pokémon (se explica en la vista previa). */
    public enum Reason {
        /** Cumple una categoría y va a una de sus cajas. */
        CATEGORY,
        /** Estaba en la caja de una categoría que no cumple: sale a una caja libre sin categoría. */
        EVICTED,
        /** No cumple ninguna categoría y va a la caja elegida para ellos en Ajustes. */
        UNSORTED_BOX,
        /** Ya estaba en su categoría; solo cambia de hueco para quedar ordenado. */
        SORTED,
        /** Intercambiado para hacer sitio (acaba fuera de su sitio original sin destino propio). */
        SWAPPED,
        /** Movido a mano desde la ventana Mover. */
        MANUAL
    }

    public int movedCount() {
        return entries.size();
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    public Entry entryFor(UUID uuid) {
        for (Entry e : entries) if (e.uuid().equals(uuid)) return e;
        return null;
    }

    /** Pokémon que entran en la caja / salen de ella. */
    public int incoming(int box) {
        int n = 0;
        for (Entry e : entries) if (e.to().box() == box && e.from().box() != box) n++;
        return n;
    }

    public int outgoing(int box) {
        int n = 0;
        for (Entry e : entries) if (e.from().box() == box && e.to().box() != box) n++;
        return n;
    }
}
