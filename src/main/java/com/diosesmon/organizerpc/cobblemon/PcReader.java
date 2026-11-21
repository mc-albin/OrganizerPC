package com.diosesmon.organizerpc.cobblemon;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.plan.PcModel;
import com.diosesmon.organizerpc.core.plan.Pos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Convierte la PC sincronizada por Cobblemon en un {@link PcModel}. Solo lectura. */
public final class PcReader {
    private static final Stats[] STATS = {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };

    /** Caché por instancia de Pokemon: los grupos huevo y los IVs no cambian mientras la PC está abierta. */
    private static final Map<Pokemon, PokemonInfo> CACHE = new java.util.WeakHashMap<>();

    private PcReader() {
    }

    public static PcModel read(ClientPC pc) {
        List<ClientBox> boxes = pc.getBoxes();
        PcModel model = new PcModel(boxes.size());
        Map<UUID, Pos> seen = new HashMap<>();
        for (int b = 0; b < boxes.size(); b++) {
            List<Pokemon> slots = boxes.get(b).getSlots();
            for (int s = 0; s < Math.min(PcModel.SLOTS_PER_BOX, slots.size()); s++) {
                Pokemon p = slots.get(s);
                if (p == null) continue;
                if (seen.containsKey(p.getUuid())) {
                    throw new IllegalStateException("La PC del cliente tiene un UUID repetido en "
                            + seen.get(p.getUuid()) + " y " + new Pos(b, s) + ". Cierra y vuelve a abrir la PC.");
                }
                seen.put(p.getUuid(), new Pos(b, s));
                model.put(new Pos(b, s), info(p));
            }
        }
        return model;
    }

    public static PokemonInfo info(Pokemon p) {
        PokemonInfo cached = CACHE.get(p);
        if (cached != null && cached.uuid().equals(p.getUuid())) return cached;
        int[] ivs = new int[6];
        IVs src = p.getIvs();
        for (int i = 0; i < 6; i++) {
            // Valor base real del IV (no el efectivo por Hyper Training).
            Integer v = src == null ? null : src.get(STATS[i]);
            ivs[i] = v == null ? -1 : v;
        }
        String name = p.getNickname() != null && !p.getNickname().getString().isBlank()
                ? p.getNickname().getString()
                : p.getSpecies().getTranslatedName().getString();
        PokemonInfo info = new PokemonInfo(p.getUuid(), name, p.getSpecies().getResourceIdentifier().toString(),
                p.getSpecies().getNationalPokedexNumber(),
                p.getLevel(), ivs, EggGroupData.lookup(p), p.getShiny(), gender(p), scale(p));
        CACHE.put(p, info);
        return info;
    }

    private static float scale(Pokemon p) {
        try {
            return p.getScaleModifier();
        } catch (Exception e) {
            return 1F;
        }
    }

    private static int gender(Pokemon p) {
        try {
            return switch (p.getGender()) {
                case MALE -> PokemonInfo.MALE;
                case FEMALE -> PokemonInfo.FEMALE;
                case GENDERLESS -> PokemonInfo.GENDERLESS;
            };
        } catch (Exception e) {
            return PokemonInfo.UNKNOWN;
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
