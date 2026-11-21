package com.diosesmon.organizerpc.core.filter;

import com.diosesmon.organizerpc.core.PokemonInfo;

/**
 * Una condición de filtro. Las implementaciones son mutables para que la interfaz
 * pueda editarlas en el sitio. Para añadir un filtro nuevo: crear la clase, añadir
 * su tipo en {@link ConditionType} y su (de)serialización en ConfigStore.
 */
public interface Condition {
    ConditionType type();

    boolean test(PokemonInfo pokemon);

    /** Resumen corto para la interfaz. */
    String describe();

    Condition copy();

    /** Una condición vacía ("da igual") no se tiene en cuenta al filtrar. */
    default boolean isEmpty() {
        return false;
    }
}
