package com.diosesmon.organizerpc.core.filter;

import java.util.function.Supplier;

public enum ConditionType {
    PERFECT_IV_COUNT(PerfectIvCountCondition::new),
    IV_PATTERN(IvPatternCondition::new),
    EGG_GROUP(EggGroupCondition::new),
    SPECIES(SpeciesCondition::new);

    private final Supplier<Condition> factory;

    ConditionType(Supplier<Condition> factory) {
        this.factory = factory;
    }

    public Condition create() {
        return factory.get();
    }
}
