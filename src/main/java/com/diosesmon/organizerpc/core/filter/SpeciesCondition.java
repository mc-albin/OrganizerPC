package com.diosesmon.organizerpc.core.filter;

import com.diosesmon.organizerpc.core.PokemonInfo;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Solo las especies que elige el usuario (p. ej. Slowpoke, Slowbro y Slowking).
 * Se guardan los identificadores de especie ("cobblemon:slowpoke").
 */
public final class SpeciesCondition implements Condition {
    public final TreeSet<String> species = new TreeSet<>();

    public SpeciesCondition() {
    }

    public void add(String id) {
        species.add(normalize(id));
    }

    public boolean remove(String id) {
        return species.remove(normalize(id));
    }

    public boolean contains(String id) {
        return species.contains(normalize(id));
    }

    public static String normalize(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.contains(":") ? s : "cobblemon:" + s;
    }

    /** "cobblemon:slowpoke" → "Slowpoke". */
    public static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    @Override
    public ConditionType type() {
        return ConditionType.SPECIES;
    }

    @Override
    public boolean isEmpty() {
        return species.isEmpty();
    }

    @Override
    public boolean test(PokemonInfo p) {
        return species.isEmpty() || species.contains(p.speciesId());
    }

    @Override
    public String describe() {
        if (species.isEmpty()) return "Especies: todas";
        StringJoiner j = new StringJoiner(", ");
        int n = 0;
        for (String s : species) {
            if (n++ == 4) {
                j.add("…");
                break;
            }
            j.add(pretty(s));
        }
        return "Especies: " + j;
    }

    @Override
    public Condition copy() {
        SpeciesCondition c = new SpeciesCondition();
        c.species.addAll(species);
        return c;
    }
}
