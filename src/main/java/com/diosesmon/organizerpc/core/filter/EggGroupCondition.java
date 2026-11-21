package com.diosesmon.organizerpc.core.filter;

import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.PokemonInfo;

import java.util.EnumSet;
import java.util.StringJoiner;

/**
 * Grupo huevo.
 * ANY: pertenece a alguno de los marcados.
 * ALL: pertenece a todos los marcados (puede tener otros).
 * ONLY: todos sus grupos están entre los marcados (ninguno extra). Con solo «Campo», entra
 * un Pokémon que es únicamente de Campo, pero no uno de Campo y Hada.
 */
public final class EggGroupCondition implements Condition {
    public enum Mode { ANY, ALL, ONLY }

    public final EnumSet<EggGroupKey> groups = EnumSet.noneOf(EggGroupKey.class);
    public Mode mode = Mode.ANY;

    public EggGroupCondition() {
    }

    public EggGroupCondition(EnumSet<EggGroupKey> groups, Mode mode) {
        this.groups.addAll(groups);
        this.mode = mode;
    }

    public EggGroupCondition(EnumSet<EggGroupKey> groups, boolean requireAll) {
        this(groups, requireAll ? Mode.ALL : Mode.ANY);
    }

    @Override
    public ConditionType type() {
        return ConditionType.EGG_GROUP;
    }

    @Override
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    @Override
    public boolean test(PokemonInfo p) {
        if (groups.isEmpty()) return true;
        if (p.eggGroups().isEmpty()) return false;
        return switch (mode) {
            case ALL -> p.eggGroups().containsAll(groups);
            case ONLY -> groups.containsAll(p.eggGroups());
            case ANY -> {
                for (EggGroupKey g : groups) if (p.eggGroups().contains(g)) yield true;
                yield false;
            }
        };
    }

    @Override
    public String describe() {
        if (groups.isEmpty()) return "Huevo: da igual";
        StringJoiner j = new StringJoiner(mode == Mode.ANY ? " o " : " y ");
        for (EggGroupKey g : groups) j.add(g.label);
        return "Huevo: " + (mode == Mode.ONLY ? "solo " : "") + j;
    }

    @Override
    public Condition copy() {
        return new EggGroupCondition(groups, mode);
    }
}
