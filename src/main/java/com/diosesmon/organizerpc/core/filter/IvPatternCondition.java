package com.diosesmon.organizerpc.core.filter;

import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.StatKey;

import java.util.Arrays;
import java.util.StringJoiner;

/**
 * IV de cada estadística: da igual, debe ser 31 o debe ser 0.
 * Ejemplo Trick Room: Velocidad = 0. Ejemplo competitivo: PS, Def, DfE = 31.
 */
public final class IvPatternCondition implements Condition {
    public enum State {
        ANY, PERFECT, ZERO;

        public State next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public final State[] states = new State[6];

    public IvPatternCondition() {
        Arrays.fill(states, State.ANY);
    }

    public State get(StatKey s) {
        return states[s.ordinal()];
    }

    public void set(StatKey s, State st) {
        states[s.ordinal()] = st;
    }

    @Override
    public ConditionType type() {
        return ConditionType.IV_PATTERN;
    }

    @Override
    public boolean isEmpty() {
        for (State s : states) if (s != State.ANY) return false;
        return true;
    }

    @Override
    public boolean test(PokemonInfo p) {
        for (StatKey k : StatKey.values()) {
            int v = p.iv(k);
            switch (states[k.ordinal()]) {
                case PERFECT -> { if (v != 31) return false; }
                case ZERO -> { if (v != 0) return false; }
                case ANY -> { }
            }
        }
        return true;
    }

    @Override
    public String describe() {
        StringJoiner j = new StringJoiner(" ");
        for (StatKey k : StatKey.values()) {
            State s = states[k.ordinal()];
            if (s != State.ANY) j.add(k.shortLabel + (s == State.PERFECT ? " 31" : " 0"));
        }
        return j.length() == 0 ? "IVs: da igual" : "IVs: " + j;
    }

    @Override
    public Condition copy() {
        IvPatternCondition c = new IvPatternCondition();
        System.arraycopy(states, 0, c.states, 0, 6);
        return c;
    }
}
