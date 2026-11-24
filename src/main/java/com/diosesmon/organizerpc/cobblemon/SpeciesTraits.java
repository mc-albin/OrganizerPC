package com.diosesmon.organizerpc.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Datos de la especie que no van en PokemonInfo: legendario/mítico y si solo puede ser de un sexo. */
public final class SpeciesTraits {
    private static final Map<String, Boolean> SINGLE = new ConcurrentHashMap<>();

    private SpeciesTraits() {
    }

    /** Legendario o mítico (etiquetas de Cobblemon). */
    public static boolean legendary(Pokemon p) {
        try {
            var l = p.getForm().getLabels();
            if (l.contains("legendary") || l.contains("mythical")) return true;
            var s = p.getSpecies().getLabels();
            return s.contains("legendary") || s.contains("mythical");
        } catch (Exception e) {
            return false;
        }
    }

    /** Solo puede ser hembra (Jynx, Chansey…) o solo macho (Tauros, Hitmonlee…). */
    public static boolean singleGender(String speciesId) {
        if (speciesId == null || speciesId.isEmpty()) return false;
        return SINGLE.computeIfAbsent(speciesId, id -> {
            try {
                Species s = PokemonSpecies.getByIdentifier(Identifier.of(id));
                if (s == null) return false;
                float r = s.getMaleRatio();
                return r == 0F || r == 1F;
            } catch (Exception e) {
                return false;
            }
        });
    }
}
