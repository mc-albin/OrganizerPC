package com.diosesmon.organizerpc.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Orden «Living dex»: cada familia evolutiva que tienes ocupa un hueco por especie, en orden de
 * Pokédex (Charmander, Charmeleon, Charizard). Si te falta una especie, su hueco queda vacío.
 * Las familias van por número de Pokédex; los repetidos van al final.
 *
 * Las familias las da el cliente ({@link #families}); sin él, cada especie es su propia familia.
 */
public final class LivingDex {
    /** Familia evolutiva completa de una especie (ella incluida), en orden de Pokédex. */
    public static volatile Function<String, List<String>> families = id -> List.of(id);

    private LivingDex() {
    }

    /** Hueco asignado: índice dentro de la secuencia (con huecos) o -1 si es repetido. */
    public record Layout(Map<String, Integer> slotOfSpecies, int length) {
    }

    /**
     * Calcula las posiciones de cada especie para los Pokémon dados.
     *
     * @return índice de hueco por especie (de 0 a length-1), con los huecos de especies que faltan.
     */
    public static Layout layout(List<PokemonInfo> mons) {
        // Familias presentes, ordenadas por el menor nº de Pokédex que tengas de ellas.
        Map<String, List<String>> familyOf = new HashMap<>();
        Map<List<String>, Integer> familyDex = new LinkedHashMap<>();
        for (PokemonInfo p : mons) {
            List<String> fam = familyOf.computeIfAbsent(p.speciesId(), id -> {
                List<String> f = families.apply(id);
                return f == null || f.isEmpty() ? List.of(id) : f;
            });
            familyDex.merge(fam, p.dex(), Math::min);
        }
        List<List<String>> ordered = new ArrayList<>(familyDex.keySet());
        ordered.sort(Comparator.comparingInt(familyDex::get));
        Map<String, Integer> slot = new HashMap<>();
        int i = 0;
        for (List<String> fam : ordered) {
            for (String id : fam) slot.putIfAbsent(id, i++);
        }
        return new Layout(slot, i);
    }
}
