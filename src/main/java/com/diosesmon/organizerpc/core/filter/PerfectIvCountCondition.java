package com.diosesmon.organizerpc.core.filter;

import com.diosesmon.organizerpc.core.PokemonInfo;

/** Cuántos IVs son 31: exactamente X o X o más (0..6). count = -1 significa "da igual". */
public final class PerfectIvCountCondition implements Condition {
    public Comparison op = Comparison.EQ;
    public int count = -1;

    public PerfectIvCountCondition() {
    }

    public PerfectIvCountCondition(Comparison op, int count) {
        this.op = op;
        this.count = Math.max(-1, Math.min(6, count));
    }

    @Override
    public ConditionType type() {
        return ConditionType.PERFECT_IV_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return count < 0;
    }

    @Override
    public boolean test(PokemonInfo p) {
        if (count < 0) return true;
        // Sin IVs fiables no se clasifica: evita meter en "0x31" a un Pokémon con datos desconocidos.
        if (!p.ivsKnown()) return false;
        return op.test(p.perfectIvCount(), count);
    }

    @Override
    public String describe() {
        if (count < 0) return "Nº de 31: da igual";
        return switch (op) {
            case EQ -> count + "x31 exactos";
            case GE -> count + "x31 o más";
            case LE -> count + "x31 o menos";
        };
    }

    @Override
    public Condition copy() {
        return new PerfectIvCountCondition(op, count);
    }
}
