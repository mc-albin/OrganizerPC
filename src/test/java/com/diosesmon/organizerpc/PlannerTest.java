package com.diosesmon.organizerpc;

import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.OrganizerConfig.UnsortedMode;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.StatKey;
import com.diosesmon.organizerpc.core.filter.Comparison;
import com.diosesmon.organizerpc.core.filter.Condition;
import com.diosesmon.organizerpc.core.filter.EggGroupCondition;
import com.diosesmon.organizerpc.core.filter.IvPatternCondition;
import com.diosesmon.organizerpc.core.filter.IvPatternCondition.State;
import com.diosesmon.organizerpc.core.filter.PerfectIvCountCondition;
import com.diosesmon.organizerpc.core.plan.Operation;
import com.diosesmon.organizerpc.core.plan.PcModel;
import com.diosesmon.organizerpc.core.plan.Plan;
import com.diosesmon.organizerpc.core.plan.Planner;
import com.diosesmon.organizerpc.core.plan.Pos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PlannerTest {

    private static PokemonInfo mon(String name, int[] ivs, EggGroupKey... groups) {
        EnumSet<EggGroupKey> g = groups.length == 0 ? EnumSet.noneOf(EggGroupKey.class) : EnumSet.of(groups[0], groups);
        return new PokemonInfo(UUID.randomUUID(), name, "cobblemon:" + name.toLowerCase(), 1, 50, ivs, g, false);
    }

    private static int[] ivsWithPerfect(int n) {
        int[] v = {10, 10, 10, 10, 10, 10};
        for (int i = 0; i < n; i++) v[i] = 31;
        return v;
    }

    private static Category cat(String name, int box, Category.Logic logic, Condition... conds) {
        Category c = new Category();
        c.name = name;
        c.boxes.add(box);
        c.logic = logic;
        c.conditions.addAll(List.of(conds));
        return c;
    }

    private static IvPatternCondition pattern(StatKey stat, State state) {
        IvPatternCondition p = new IvPatternCondition();
        p.set(stat, state);
        return p;
    }

    private static Set<Integer> all(PcModel m) {
        return IntStream.range(0, m.boxCount()).boxed().collect(Collectors.toSet());
    }

    private static Planner.Request req(PcModel m, List<Category> cats, Set<Integer> scope, Set<Integer> locked,
                                       UnsortedMode mode, int unsortedBox, Map<UUID, String> overrides) {
        return new Planner.Request(m, cats, scope, locked, mode, unsortedBox, overrides);
    }

    private static Planner.Request req(PcModel m, List<Category> cats) {
        return req(m, cats, all(m), Set.of(), UnsortedMode.KEEP, 0, Map.of());
    }

    /** Aplica las operaciones validando lo mismo que valida el servidor de Cobblemon 1.7.3. */
    private static PcModel applyLikeServer(PcModel start, List<Operation> ops) {
        PcModel m = start.copy();
        for (Operation op : ops) {
            assertEquals(op.pokemon(), m.at(op.from()), "UUID en origen no coincide: " + op);
            if (op.type() == Operation.Type.MOVE) {
                assertNull(m.at(op.to()), "Destino no vacío: " + op);
                m.move(op.pokemon(), op.to());
            } else {
                assertEquals(op.other(), m.at(op.to()), "UUID en destino no coincide: " + op);
                m.swap(op.from(), op.to());
            }
        }
        return m;
    }

    private static void assertSameMons(PcModel a, PcModel b) {
        Set<UUID> ua = new HashSet<>(), ub = new HashSet<>();
        int na = 0, nb = 0;
        for (int box = 0; box < a.boxCount(); box++)
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                if (a.at(box, s) != null) { ua.add(a.at(box, s)); na++; }
                if (b.at(box, s) != null) { ub.add(b.at(box, s)); nb++; }
            }
        assertEquals(na, nb, "Cambió el número de Pokémon");
        assertEquals(ua, ub, "Cambió el conjunto de Pokémon");
        assertEquals(na, ub.size(), "Pokémon duplicado");
    }

    private static void assertSamePositions(PcModel a, PcModel b) {
        for (int box = 0; box < a.boxCount(); box++)
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) assertEquals(a.at(box, s), b.at(box, s));
    }

    @Test
    void perfectIvFiltersAreExact() {
        for (int n = 0; n <= 6; n++) {
            PokemonInfo p = mon("x", ivsWithPerfect(n));
            for (int k = 0; k <= 6; k++) {
                assertEquals(n == k, new PerfectIvCountCondition(Comparison.EQ, k).test(p));
                assertEquals(n >= k, new PerfectIvCountCondition(Comparison.GE, k).test(p));
            }
        }
        assertTrue(new PerfectIvCountCondition(Comparison.EQ, -1).isEmpty());
    }

    @Test
    void zeroIsNotConfusedWithThirtyOne() {
        PokemonInfo trick = mon("trick", new int[]{31, 31, 31, 31, 31, 0});
        assertTrue(pattern(StatKey.SPEED, State.ZERO).test(trick));
        assertFalse(pattern(StatKey.SPEED, State.PERFECT).test(trick));
        assertTrue(pattern(StatKey.HP, State.PERFECT).test(trick));
        assertFalse(pattern(StatKey.HP, State.ZERO).test(trick));
        assertTrue(new PerfectIvCountCondition(Comparison.EQ, 5).test(trick));
        PokemonInfo one = mon("one", new int[]{1, 30, 31, 31, 31, 31});
        assertFalse(pattern(StatKey.HP, State.ZERO).test(one), "1 no es 0");
        assertFalse(pattern(StatKey.ATTACK, State.PERFECT).test(one), "30 no es 31");
        IvPatternCondition multi = new IvPatternCondition();
        multi.set(StatKey.HP, State.PERFECT);
        multi.set(StatKey.SPEED, State.ZERO);
        assertTrue(multi.test(trick));
        assertFalse(multi.test(one));
    }

    @Test
    void unknownIvsNeverMatch() {
        PokemonInfo p = mon("?", new int[]{-1, -1, -1, -1, -1, -1});
        assertFalse(new PerfectIvCountCondition(Comparison.EQ, 0).test(p));
        assertFalse(pattern(StatKey.SPEED, State.ZERO).test(p));
    }

    @Test
    void eggGroupsAnyAndAll() {
        PokemonInfo dratini = mon("Dratini", ivsWithPerfect(0), EggGroupKey.WATER_1, EggGroupKey.DRAGON);
        assertTrue(new EggGroupCondition(EnumSet.of(EggGroupKey.DRAGON), false).test(dratini));
        assertTrue(new EggGroupCondition(EnumSet.of(EggGroupKey.WATER_1), false).test(dratini));
        assertTrue(new EggGroupCondition(EnumSet.of(EggGroupKey.WATER_1, EggGroupKey.DRAGON), true).test(dratini));
        assertFalse(new EggGroupCondition(EnumSet.of(EggGroupKey.DRAGON, EggGroupKey.FIELD), true).test(dratini));
        assertFalse(new EggGroupCondition(EnumSet.of(EggGroupKey.FIELD), false).test(dratini));
        PokemonInfo unknown = mon("?", ivsWithPerfect(0));
        assertFalse(new EggGroupCondition(EnumSet.of(EggGroupKey.UNDISCOVERED), false).test(unknown));
    }

    @Test
    void combinedAndOrAndEmptySections() {
        Category and = cat("CRIANZA", 0, Category.Logic.AND,
                new EggGroupCondition(EnumSet.of(EggGroupKey.DRAGON), false),
                new PerfectIvCountCondition(Comparison.EQ, 5));
        Category or = cat("OR", 0, Category.Logic.OR,
                new EggGroupCondition(EnumSet.of(EggGroupKey.DRAGON), false),
                new PerfectIvCountCondition(Comparison.EQ, 5));
        PokemonInfo dragon5 = mon("a", ivsWithPerfect(5), EggGroupKey.DRAGON);
        PokemonInfo dragon2 = mon("b", ivsWithPerfect(2), EggGroupKey.DRAGON);
        PokemonInfo field5 = mon("c", ivsWithPerfect(5), EggGroupKey.FIELD);
        assertTrue(and.matches(dragon5));
        assertFalse(and.matches(dragon2));
        assertFalse(and.matches(field5));
        assertTrue(or.matches(dragon2));
        assertTrue(or.matches(field5));
        assertFalse(new Category().matches(dragon5), "Categoría vacía no debe clasificar");
        // Secciones en "da igual" no cuentan, ni siquiera con OR.
        Category withEmpty = cat("x", 0, Category.Logic.OR, new IvPatternCondition(), new EggGroupCondition(),
                new PerfectIvCountCondition(Comparison.EQ, 5));
        assertTrue(withEmpty.matches(dragon5));
        assertFalse(withEmpty.matches(dragon2));
        Category allEmpty = cat("y", 0, Category.Logic.AND, new IvPatternCondition(), new EggGroupCondition());
        assertFalse(allEmpty.matches(dragon5));
    }

    @Test
    void organizesIntoIvBoxes() {
        PcModel m = new PcModel(8);
        List<PokemonInfo> mons = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            PokemonInfo p = mon("m" + i, ivsWithPerfect(i % 7));
            mons.add(p);
            m.put(new Pos(0, i), p);
        }
        List<Category> cats = new ArrayList<>();
        for (int n = 0; n <= 6; n++) cats.add(cat(n + "x31", n + 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, n)));
        Plan plan = Planner.organize(req(m, cats));
        PcModel after = applyLikeServer(m, plan.operations());
        assertSameMons(m, after);
        assertSamePositions(after, plan.after());
        for (PokemonInfo p : mons) assertEquals(p.perfectIvCount() + 1, after.positionOf(p.uuid()).box(), p.name());
        assertEquals(28, plan.movedCount());
        assertEquals(4, plan.incoming(7));
        assertEquals(28, plan.outgoing(0));
    }

    @Test
    void fullBoxesResolvedWithSwaps() {
        PcModel m = new PcModel(2);
        List<PokemonInfo> sixes = new ArrayList<>(), zeros = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            PokemonInfo six = mon("six" + i, ivsWithPerfect(6));
            PokemonInfo zero = mon("zero" + i, ivsWithPerfect(0));
            sixes.add(six);
            zeros.add(zero);
            m.put(new Pos(0, i), six);
            m.put(new Pos(1, i), zero);
        }
        List<Category> cats = List.of(
                cat("0x31", 0, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 0)),
                cat("6x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6)));
        Plan plan = Planner.organize(req(m, cats));
        PcModel after = applyLikeServer(m, plan.operations());
        assertSameMons(m, after);
        for (PokemonInfo p : zeros) assertEquals(0, after.positionOf(p.uuid()).box());
        for (PokemonInfo p : sixes) assertEquals(1, after.positionOf(p.uuid()).box());
        assertEquals(30, plan.operations().size(), "Un swap por pareja");
    }

    @Test
    void priorityAndManualOverride() {
        PcModel m = new PcModel(4);
        PokemonInfo p = mon("Dragonite", new int[]{31, 31, 31, 31, 31, 0}, EggGroupKey.DRAGON, EggGroupKey.WATER_1);
        m.put(new Pos(0, 0), p);
        Category c5 = cat("5x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 5));
        Category dragon = cat("DRAGÓN", 2, Category.Logic.AND, new EggGroupCondition(EnumSet.of(EggGroupKey.DRAGON), false));
        Category trick = cat("TRICK ROOM", 3, Category.Logic.AND,
                pattern(StatKey.SPEED, State.ZERO), new PerfectIvCountCondition(Comparison.GE, 3));
        List<Category> cats = List.of(trick, c5, dragon);

        Plan plan = Planner.organize(req(m, cats));
        assertEquals(3, applyLikeServer(m, plan.operations()).positionOf(p.uuid()).box(), "Gana la primera categoría");
        assertEquals(3, plan.entries().get(0).matchingCategoryIds().size());

        Plan manual = Planner.organize(req(m, cats, all(m), Set.of(), UnsortedMode.KEEP, 0, Map.of(p.uuid(), dragon.id)));
        assertEquals(2, applyLikeServer(m, manual.operations()).positionOf(p.uuid()).box(), "Selección manual");

        Plan none = Planner.organize(req(m, cats, all(m), Set.of(), UnsortedMode.KEEP, 0, Map.of(p.uuid(), Planner.NO_CATEGORY)));
        assertTrue(none.isEmpty(), "Sin categoría y fuera de cajas destino: no se mueve");
    }

    @Test
    void unsortedKeepEvictsOnlyFromCategoryBoxes() {
        PcModel m = new PcModel(3);
        PokemonInfo stray = mon("stray", ivsWithPerfect(2));
        PokemonInfo other = mon("other", ivsWithPerfect(2));
        PokemonInfo six = mon("six", ivsWithPerfect(6));
        m.put(new Pos(1, 0), stray);
        m.put(new Pos(2, 0), other);
        m.put(new Pos(0, 0), six);
        List<Category> cats = List.of(cat("6x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6)));
        Plan plan = Planner.organize(req(m, cats));
        PcModel after = applyLikeServer(m, plan.operations());
        assertSameMons(m, after);
        assertEquals(1, after.positionOf(six.uuid()).box());
        assertNotEquals(1, after.positionOf(stray.uuid()).box());
        assertEquals(new Pos(2, 0), after.positionOf(other.uuid()));
        Plan.Entry e = plan.entryFor(stray.uuid());
        assertEquals(Plan.Reason.EVICTED, e.reason());
        assertEquals("6x31", e.leftCategoryName());
        assertEquals(Plan.Reason.CATEGORY, plan.entryFor(six.uuid()).reason());

        // Con la opción desactivada, el que no cumple se queda en la caja de la categoría.
        Plan keep = Planner.organize(new Planner.Request(m, cats, all(m), Set.of(), UnsortedMode.KEEP, 0, Map.of(), false));
        PcModel afterKeep = applyLikeServer(m, keep.operations());
        assertEquals(new Pos(1, 0), afterKeep.positionOf(stray.uuid()));
        assertEquals(1, afterKeep.positionOf(six.uuid()).box());
    }

    @Test
    void unsortedMoveToBox() {
        PcModel m = new PcModel(3);
        PokemonInfo stray = mon("stray", ivsWithPerfect(2));
        m.put(new Pos(0, 5), stray);
        List<Category> cats = List.of(cat("6x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6)));
        Plan plan = Planner.organize(req(m, cats, all(m), Set.of(), UnsortedMode.MOVE_TO_BOX, 2, Map.of()));
        assertEquals(2, applyLikeServer(m, plan.operations()).positionOf(stray.uuid()).box());
    }

    @Test
    void noSpaceLeavesPokemonInPlace() {
        PcModel m = new PcModel(2);
        for (int i = 0; i < 30; i++) m.put(new Pos(1, i), mon("f" + i, ivsWithPerfect(6)));
        PokemonInfo extra = mon("extra", ivsWithPerfect(6));
        m.put(new Pos(0, 0), extra);
        List<Category> cats = List.of(cat("6x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6)));
        Plan plan = Planner.organize(req(m, cats));
        assertTrue(plan.isEmpty());
        assertEquals(1, plan.noSpace().size());
        assertEquals(extra.uuid(), plan.noSpace().get(0).uuid());
    }

    @Test
    void multipleChosenBoxesFillInOrder() {
        PcModel m = new PcModel(6);
        for (int i = 0; i < 30; i++) m.put(new Pos(0, i), mon("a" + i, ivsWithPerfect(6)));
        for (int i = 0; i < 5; i++) m.put(new Pos(1, i), mon("b" + i, ivsWithPerfect(6)));
        Category c = cat("6x31", 5, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6));
        c.boxes.add(3); // cajas no consecutivas
        Plan plan = Planner.organize(req(m, List.of(c)));
        PcModel after = applyLikeServer(m, plan.operations());
        assertEquals(0, after.freeSlots(3));
        assertEquals(25, after.freeSlots(5));
    }

    @Test
    void lockedBoxesAreNeverTouched() {
        PcModel m = new PcModel(4);
        PokemonInfo lockedSix = mon("lockedSix", ivsWithPerfect(6));
        PokemonInfo freeSix = mon("freeSix", ivsWithPerfect(6));
        PokemonInfo strayInLockedTarget = mon("stray", ivsWithPerfect(1));
        m.put(new Pos(0, 0), lockedSix);           // caja 0 bloqueada
        m.put(new Pos(2, 0), freeSix);
        m.put(new Pos(1, 0), strayInLockedTarget);  // caja 1 = destino, pero bloqueada
        Category six = cat("6x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6));
        six.boxes.add(3);
        Plan plan = Planner.organize(req(m, List.of(six), all(m), Set.of(0, 1), UnsortedMode.KEEP, 0, Map.of()));
        PcModel after = applyLikeServer(m, plan.operations());
        assertEquals(new Pos(0, 0), after.positionOf(lockedSix.uuid()), "No sale de caja bloqueada");
        assertEquals(new Pos(1, 0), after.positionOf(strayInLockedTarget.uuid()), "Caja bloqueada intacta");
        assertEquals(3, after.positionOf(freeSix.uuid()).box(), "Usa la caja destino no bloqueada");
        for (Operation op : plan.operations()) {
            assertFalse(op.from().box() == 0 || op.from().box() == 1 || op.to().box() == 0 || op.to().box() == 1);
        }
        // Si todas sus cajas están bloqueadas, la categoría no se usa.
        Plan none = Planner.organize(req(m, List.of(six), all(m), Set.of(0, 1, 3), UnsortedMode.KEEP, 0, Map.of()));
        assertTrue(none.isEmpty());
        // Mover marcados: ni desde ni hacia cajas bloqueadas.
        Plan sel = Planner.moveSelection(m, List.of(lockedSix.uuid(), freeSix.uuid()), 3, Set.of(0));
        assertEquals(1, sel.movedCount());
        assertTrue(Planner.moveSelection(m, List.of(freeSix.uuid()), 1, Set.of(1)).isEmpty());
    }

    @Test
    void scopeIsRespected() {
        PcModel m = new PcModel(3);
        PokemonInfo inScope = mon("in", ivsWithPerfect(6));
        PokemonInfo outScope = mon("out", ivsWithPerfect(6));
        m.put(new Pos(0, 0), inScope);
        m.put(new Pos(2, 0), outScope);
        List<Category> cats = List.of(cat("6x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6)));
        Plan plan = Planner.organize(req(m, cats, Set.of(0), Set.of(), UnsortedMode.KEEP, 0, Map.of()));
        PcModel after = applyLikeServer(m, plan.operations());
        assertEquals(1, after.positionOf(inScope.uuid()).box());
        assertEquals(new Pos(2, 0), after.positionOf(outScope.uuid()));
    }

    @Test
    void moveSelectionFillsEmptySlotsOnly() {
        PcModel m = new PcModel(2);
        List<UUID> sel = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            PokemonInfo p = mon("s" + i, ivsWithPerfect(i));
            m.put(new Pos(0, i), p);
            sel.add(p.uuid());
        }
        for (int i = 0; i < 27; i++) m.put(new Pos(1, i), mon("full" + i, ivsWithPerfect(0)));
        Plan plan = Planner.moveSelection(m, sel, 1, Set.of());
        PcModel after = applyLikeServer(m, plan.operations());
        assertSameMons(m, after);
        assertSamePositions(after, plan.after());
        assertEquals(3, plan.movedCount());
        assertEquals(2, plan.noSpace().size());
        assertEquals(0, after.freeSlots(1));
    }

    @Test
    void sortsInsideCategoryBoxes() {
        PcModel m = new PcModel(3);
        int[][] ivs = {
                {10, 10, 10, 10, 10, 10},   // 0x31
                {10, 31, 10, 10, 10, 10},   // 1x31 (Ata)
                {31, 10, 10, 10, 10, 10},   // 1x31 (PS)
                {31, 31, 31, 10, 10, 10},   // 3x31
                {31, 31, 10, 10, 10, 10},   // 2x31
        };
        List<PokemonInfo> mons = new ArrayList<>();
        for (int i = 0; i < ivs.length; i++) {
            PokemonInfo p = mon("m" + i, ivs[i]);
            mons.add(p);
            m.put(new Pos(1, 10 + i * 3), p); // desordenados y con huecos
        }
        Category c = cat("TODOS", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.GE, 0));
        c.sortMode = com.diosesmon.organizerpc.core.SortMode.IVS_DESC;
        Plan plan = Planner.organize(req(m, List.of(c)));
        PcModel after = applyLikeServer(m, plan.operations());
        assertSameMons(m, after);
        assertEquals(mons.get(3).uuid(), after.at(1, 0), "3x31 primero");
        assertEquals(mons.get(4).uuid(), after.at(1, 1), "luego 2x31");
        assertEquals(mons.get(2).uuid(), after.at(1, 2), "1x31 con PS antes que con Ata");
        assertEquals(mons.get(1).uuid(), after.at(1, 3));
        assertEquals(mons.get(0).uuid(), after.at(1, 4), "0x31 al final");
        for (Plan.Entry e : plan.entries()) assertEquals(Plan.Reason.SORTED, e.reason());
        // Ya ordenado: no hay más movimientos.
        assertTrue(Planner.organize(req(after, List.of(c))).isEmpty());

        c.sortMode = com.diosesmon.organizerpc.core.SortMode.IVS_ASC;
        PcModel asc = applyLikeServer(after, Planner.organize(req(after, List.of(c))).operations());
        assertEquals(mons.get(2).uuid(), asc.at(1, 0), "1x31 (PS) primero en ascendente");
        assertEquals(mons.get(0).uuid(), asc.at(1, 4), "0x31 siempre al final");
    }

    @Test
    void captureBoxesAreEmptiedButNeverFilled() {
        PcModel m = new PcModel(4);
        PokemonInfo six = mon("six", ivsWithPerfect(6));
        PokemonInfo stray = mon("stray", ivsWithPerfect(1));
        PokemonInfo lost = mon("lost", ivsWithPerfect(2));
        m.put(new Pos(0, 0), six);    // caja 0 = captura
        m.put(new Pos(0, 1), lost);   // sin categoría: se queda en captura
        m.put(new Pos(2, 0), stray);  // estorba en la caja de 6x31
        Category c = cat("6x31", 2, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6));
        c.boxes.add(0); // aunque el usuario la marque, una caja de captura no es destino
        Plan plan = Planner.organize(new Planner.Request(m, List.of(c), Set.of(0, 1, 2, 3), Set.of(),
                UnsortedMode.KEEP, 0, Map.of(), true, Set.of(0)));
        PcModel after = applyLikeServer(m, plan.operations());
        assertSameMons(m, after);
        assertEquals(2, after.positionOf(six.uuid()).box(), "sale de captura a su categoría");
        assertEquals(new Pos(0, 1), after.positionOf(lost.uuid()), "sin categoría se queda");
        assertNotEquals(0, after.positionOf(stray.uuid()).box(), "nadie entra en la caja de captura");
    }

    @Test
    void speciesFilter() {
        com.diosesmon.organizerpc.core.filter.SpeciesCondition sp = new com.diosesmon.organizerpc.core.filter.SpeciesCondition();
        sp.add("Slowpoke");
        sp.add("cobblemon:slowbro");
        assertTrue(sp.test(mon("Slowpoke", ivsWithPerfect(0))));
        assertTrue(sp.test(mon("Slowbro", ivsWithPerfect(0))));
        assertFalse(sp.test(mon("Slowking", ivsWithPerfect(0))));
        Category c = cat("SLOW", 0, Category.Logic.AND, sp);
        assertFalse(c.matches(mon("Eevee", ivsWithPerfect(6))));
        assertEquals("Slowpoke", com.diosesmon.organizerpc.core.filter.SpeciesCondition.pretty("cobblemon:slowpoke"));
    }

    @Test
    void eggGroupOnlyMode() {
        EggGroupCondition onlyField = new EggGroupCondition(EnumSet.of(EggGroupKey.FIELD), EggGroupCondition.Mode.ONLY);
        assertTrue(onlyField.test(mon("Poochyena", ivsWithPerfect(0), EggGroupKey.FIELD)));
        assertFalse(onlyField.test(mon("Pikachu", ivsWithPerfect(0), EggGroupKey.FIELD, EggGroupKey.FAIRY)), "tiene un grupo extra");
        assertFalse(onlyField.test(mon("Dratini", ivsWithPerfect(0), EggGroupKey.WATER_1, EggGroupKey.DRAGON)));
        EggGroupCondition onlyTwo = new EggGroupCondition(EnumSet.of(EggGroupKey.WATER_1, EggGroupKey.MONSTER), EggGroupCondition.Mode.ONLY);
        assertTrue(onlyTwo.test(mon("Slowpoke", ivsWithPerfect(0), EggGroupKey.MONSTER, EggGroupKey.WATER_1)));
        assertTrue(onlyTwo.test(mon("Psyduck", ivsWithPerfect(0), EggGroupKey.WATER_1)));
        assertFalse(onlyTwo.test(mon("Dratini", ivsWithPerfect(0), EggGroupKey.WATER_1, EggGroupKey.DRAGON)));
    }

    @Test
    void arrangeCompactsAndSortsIgnoringSpecies() {
        PcModel m = new PcModel(3);
        PokemonInfo a = mon("Zubat", ivsWithPerfect(2));
        PokemonInfo b = mon("Abra", ivsWithPerfect(1));
        PokemonInfo c = mon("Mew", ivsWithPerfect(0));
        PokemonInfo d = mon("Eevee", ivsWithPerfect(1));
        m.put(new Pos(0, 7), a);
        m.put(new Pos(0, 20), b);
        m.put(new Pos(1, 3), c);
        m.put(new Pos(1, 29), d);
        // Solo juntar: mismo orden, sin huecos, completando la caja 0 primero.
        Plan compact = Planner.arrange(m, List.of(0, 1), com.diosesmon.organizerpc.core.SortMode.NONE, Set.of());
        PcModel ac = applyLikeServer(m, compact.operations());
        assertEquals(a.uuid(), ac.at(0, 0));
        assertEquals(b.uuid(), ac.at(0, 1));
        assertEquals(c.uuid(), ac.at(0, 2));
        assertEquals(d.uuid(), ac.at(0, 3));
        assertEquals(30, ac.freeSlots(1));
        // Por IVs ascendente: 1x31 (en el orden en que estaban), 2x31 y los 0x31 al final.
        Plan iv = Planner.arrange(m, List.of(0, 1), com.diosesmon.organizerpc.core.SortMode.IVS_ASC, Set.of());
        PcModel ai = applyLikeServer(m, iv.operations());
        assertEquals(b.uuid(), ai.at(0, 0));
        assertEquals(d.uuid(), ai.at(0, 1));
        assertEquals(a.uuid(), ai.at(0, 2));
        assertEquals(c.uuid(), ai.at(0, 3), "0x31 al final");
        assertSameMons(m, ai);
    }

    @Test
    void compactOnOrganizeFillsHoles() {
        PcModel m = new PcModel(3);
        PokemonInfo x = mon("x", ivsWithPerfect(6));
        PokemonInfo y = mon("y", ivsWithPerfect(6));
        PokemonInfo stay = mon("stay", ivsWithPerfect(1));
        m.put(new Pos(1, 12), x);
        m.put(new Pos(1, 25), y);
        m.put(new Pos(0, 9), stay); // caja de captura con hueco delante
        Category c = cat("6x31", 1, Category.Logic.AND, new PerfectIvCountCondition(Comparison.EQ, 6));
        Plan plan = Planner.organize(new Planner.Request(m, List.of(c), all(m), Set.of(), UnsortedMode.KEEP, 0, Map.of(), true, Set.of(0), true));
        PcModel after = applyLikeServer(m, plan.operations());
        assertEquals(x.uuid(), after.at(1, 0));
        assertEquals(y.uuid(), after.at(1, 1));
        assertEquals(stay.uuid(), after.at(0, 0), "la caja de captura también se junta");
    }

    @Test
    void moveSelectionToSeveralBoxes() {
        PcModel m = new PcModel(4);
        List<UUID> sel = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            PokemonInfo p = mon("s" + i, ivsWithPerfect(0));
            m.put(new Pos(i / 30 == 0 ? 0 : 3, i % 30), p);
            sel.add(p.uuid());
        }
        Plan plan = Planner.moveSelection(m, sel, List.of(1, 2), Set.of());
        PcModel after = applyLikeServer(m, plan.operations());
        assertEquals(0, after.freeSlots(1));
        assertEquals(25, after.freeSlots(2));
        assertTrue(plan.noSpace().isEmpty());
    }

    @Test
    void moveAndArrangeSortsDestination() {
        PcModel m = new PcModel(3);
        PokemonInfo old = mon("old", ivsWithPerfect(3));
        PokemonInfo n1 = mon("n1", ivsWithPerfect(1));
        PokemonInfo n2 = mon("n2", ivsWithPerfect(0));
        m.put(new Pos(1, 5), old);
        m.put(new Pos(0, 0), n1);
        m.put(new Pos(0, 1), n2);
        Plan plan = Planner.moveAndArrange(m, List.of(n1.uuid(), n2.uuid()), List.of(1), Set.of(),
                com.diosesmon.organizerpc.core.SortMode.IVS_ASC);
        PcModel after = applyLikeServer(m, plan.operations());
        assertSameMons(m, after);
        assertEquals(n1.uuid(), after.at(1, 0));
        assertEquals(old.uuid(), after.at(1, 1));
        assertEquals(n2.uuid(), after.at(1, 2), "0x31 al final");
        assertSamePositions(after, plan.after());
    }

    @Test
    void twoSortModesChain() {
        PcModel m = new PcModel(2);
        PokemonInfo hp1 = new PokemonInfo(UUID.randomUUID(), "a", "cobblemon:a", 50, 1, new int[]{31, 1, 1, 1, 1, 1}, null, false);
        PokemonInfo atk1 = new PokemonInfo(UUID.randomUUID(), "b", "cobblemon:b", 10, 1, new int[]{1, 31, 1, 1, 1, 1}, null, false);
        PokemonInfo hp1low = new PokemonInfo(UUID.randomUUID(), "c", "cobblemon:c", 5, 1, new int[]{31, 2, 1, 1, 1, 1}, null, false);
        m.put(new Pos(0, 0), atk1);
        m.put(new Pos(0, 1), hp1);
        m.put(new Pos(0, 2), hp1low);
        // 1º nº de 31 (todos 1x31), 2º Pokédex: manda el 2º elegido -> dex 5, 10, 50.
        Plan plan = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IVS_ASC,
                com.diosesmon.organizerpc.core.SortMode.DEX, Set.of());
        PcModel after = applyLikeServer(m, plan.operations());
        assertEquals(hp1low.uuid(), after.at(0, 0));
        assertEquals(atk1.uuid(), after.at(0, 1));
        assertEquals(hp1.uuid(), after.at(0, 2));
        // Sin 2º orden: a igualdad de 31, PS primero (hp1 y hp1low antes que atk1, en su orden previo).
        Plan only = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IVS_ASC,
                com.diosesmon.organizerpc.core.SortMode.NONE, Set.of());
        PcModel a2 = applyLikeServer(m, only.operations());
        assertEquals(hp1.uuid(), a2.at(0, 0));
        assertEquals(hp1low.uuid(), a2.at(0, 1));
        assertEquals(atk1.uuid(), a2.at(0, 2));
        // «PS, Ata…» como 1º y «0x31 → 6x31» como 2º: manda el nº de 31.
        PokemonInfo four = new PokemonInfo(UUID.randomUUID(), "t", "cobblemon:t", 158, 1, new int[]{1, 31, 31, 31, 31, 1}, null, false);
        m.put(new Pos(0, 3), four);
        Plan mixed = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IV_STATS,
                com.diosesmon.organizerpc.core.SortMode.IVS_ASC, Set.of());
        PcModel a3 = applyLikeServer(m, mixed.operations());
        assertEquals(four.uuid(), a3.at(0, 3), "el 4x31 va al final, no entre los 1x31");
    }

    @Test
    void ivSortsAreCorrectOnRandomBoxes() {
        Random r = new Random(77);
        for (int iter = 0; iter < 100; iter++) {
            PcModel m = new PcModel(2);
            int n = 5 + r.nextInt(50);
            List<Pos> free = new ArrayList<>();
            for (int b = 0; b < 2; b++) for (int s = 0; s < 30; s++) free.add(new Pos(b, s));
            java.util.Collections.shuffle(free, r);
            for (int i = 0; i < n; i++) {
                int[] ivs = new int[6];
                for (int k = 0; k < 6; k++) ivs[k] = r.nextInt(3) == 0 ? 31 : r.nextInt(31);
                m.put(free.get(i), new PokemonInfo(UUID.randomUUID(), "p" + i, "cobblemon:p" + r.nextInt(5), r.nextInt(900), 1 + r.nextInt(99), ivs, null, false));
            }
            com.diosesmon.organizerpc.core.SortMode[][] combos = {
                    {com.diosesmon.organizerpc.core.SortMode.IVS_ASC, com.diosesmon.organizerpc.core.SortMode.NONE},
                    {com.diosesmon.organizerpc.core.SortMode.IV_STATS, com.diosesmon.organizerpc.core.SortMode.IVS_ASC},
                    {com.diosesmon.organizerpc.core.SortMode.IVS_ASC, com.diosesmon.organizerpc.core.SortMode.IV_STATS},
                    {com.diosesmon.organizerpc.core.SortMode.IVS_DESC, com.diosesmon.organizerpc.core.SortMode.DEX}};
            for (var combo : combos) {
                Plan plan = Planner.arrange(m, List.of(0, 1), combo[0], combo[1], Set.of());
                PcModel after = applyLikeServer(m, plan.operations());
                assertSameMons(m, after);
                List<PokemonInfo> seq = new ArrayList<>();
                for (int b = 0; b < 2; b++) for (int s = 0; s < 30; s++) if (after.at(b, s) != null) seq.add(after.info(after.at(b, s)));
                assertEquals(n, seq.size());
                // Sin huecos: los n primeros huecos ocupados.
                for (int i = 0; i < n; i++) assertNotNull(after.at(i / 30, i % 30), "hueco en " + i);
                boolean desc = combo[0] == com.diosesmon.organizerpc.core.SortMode.IVS_DESC;
                // Los 0x31 siempre al final: tras el primero sin 31 ya no aparece ninguno con 31.
                boolean seenZero = false;
                for (PokemonInfo pi : seq) {
                    if (pi.perfectIvCount() == 0) seenZero = true;
                    else assertFalse(seenZero, "un Pokémon con 31 quedó detrás de un 0x31");
                }
                List<PokemonInfo> withIvs = new ArrayList<>();
                for (PokemonInfo pi : seq) if (pi.perfectIvCount() > 0) withIvs.add(pi);
                seq = withIvs;
                for (int i = 1; i < seq.size(); i++) {
                    int prev = seq.get(i - 1).perfectIvCount(), cur = seq.get(i).perfectIvCount();
                    assertTrue(desc ? prev >= cur : prev <= cur, "orden por nº de 31 roto en " + i + " con " + combo[0] + "+" + combo[1]);
                    if (prev == cur && combo[1] == com.diosesmon.organizerpc.core.SortMode.DEX) {
                        assertTrue(seq.get(i - 1).dex() <= seq.get(i).dex(), "desempate por Pokédex roto");
                    }
                }
            }
        }
    }

    @Test
    void randomizedInvariants() {
        Random r = new Random(1234);
        EggGroupKey[] groups = EggGroupKey.values();
        StatKey[] stats = StatKey.values();
        for (int iter = 0; iter < 400; iter++) {
            int boxes = 2 + r.nextInt(8);
            PcModel m = new PcModel(boxes);
            int count = r.nextInt(boxes * 30 + 1);
            List<Pos> free = new ArrayList<>();
            for (int b = 0; b < boxes; b++) for (int s = 0; s < 30; s++) free.add(new Pos(b, s));
            java.util.Collections.shuffle(free, r);
            for (int i = 0; i < count; i++) {
                int[] ivs = new int[6];
                for (int k = 0; k < 6; k++) ivs[k] = r.nextInt(4) == 0 ? 31 : (r.nextInt(5) == 0 ? 0 : r.nextInt(31));
                m.put(free.get(i), mon("p" + i, ivs, groups[r.nextInt(groups.length)], groups[r.nextInt(groups.length)]));
            }
            List<Category> cats = new ArrayList<>();
            int nc = 1 + r.nextInt(5);
            for (int c = 0; c < nc; c++) {
                Category cat = new Category();
                int nb = 1 + r.nextInt(3);
                for (int k = 0; k < nb; k++) cat.boxes.add(r.nextInt(boxes));
                cat.logic = r.nextBoolean() ? Category.Logic.AND : Category.Logic.OR;
                cat.conditions.add(new PerfectIvCountCondition(r.nextBoolean() ? Comparison.EQ : Comparison.GE, r.nextInt(8) - 1));
                if (r.nextBoolean()) cat.conditions.add(new EggGroupCondition(EnumSet.of(groups[r.nextInt(groups.length)]), false));
                if (r.nextInt(3) == 0) cat.conditions.add(pattern(stats[r.nextInt(6)], r.nextBoolean() ? State.ZERO : State.PERFECT));
                cats.add(cat);
            }
            Set<Integer> scope = new HashSet<>();
            Set<Integer> locked = new HashSet<>();
            for (int b = 0; b < boxes; b++) {
                if (r.nextInt(4) != 0) scope.add(b);
                if (r.nextInt(5) == 0) locked.add(b);
            }
            UnsortedMode mode = r.nextBoolean() ? UnsortedMode.KEEP : UnsortedMode.MOVE_TO_BOX;
            Plan plan = Planner.organize(req(m, cats, scope, locked, mode, r.nextInt(boxes), new HashMap<>()));
            PcModel after = applyLikeServer(m, plan.operations());
            assertSameMons(m, after);
            assertSamePositions(after, plan.after());

            for (Operation op : plan.operations()) {
                assertFalse(locked.contains(op.from().box()) || locked.contains(op.to().box()), "Tocó caja bloqueada");
            }
            for (int b = 0; b < boxes; b++) {
                if (scope.contains(b) && !locked.contains(b)) continue;
                for (int s = 0; s < 30; s++) {
                    UUID u = m.at(b, s);
                    if (u != null) assertEquals(new Pos(b, s), after.positionOf(u));
                }
            }
            Set<UUID> noSpace = plan.noSpace().stream().map(PokemonInfo::uuid).collect(Collectors.toSet());
            for (Plan.Entry e : plan.entries()) {
                if (e.categoryId() == null || noSpace.contains(e.uuid())) continue;
                Category c = cats.stream().filter(x -> x.id.equals(e.categoryId())).findFirst().orElseThrow();
                assertTrue(c.ownsBox(after.positionOf(e.uuid()).box()), "Mal colocado");
            }
            assertTrue(plan.operations().size() <= 2 * plan.scannedCount() + 1);
        }
    }

    private static int[] perfectIn(StatKey k) {
        int[] v = {10, 10, 10, 10, 10, 10};
        v[k.ordinal()] = 31;
        return v;
    }

    @Test
    void ivColumnsStartsNewColumnPerStat() {
        PcModel m = new PcModel(2);
        int slot = 0;
        // 13 con 31 en PS y 4 con 31 en Ataque, mezclados en la caja 1.
        List<PokemonInfo> hp = new ArrayList<>(), atk = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            PokemonInfo p = i % 4 == 3 && atk.size() < 4 ? mon("A" + i, perfectIn(StatKey.ATTACK)) : mon("H" + i, perfectIn(StatKey.HP));
            (p.name().startsWith("A") ? atk : hp).add(p);
            m.put(new Pos(0, slot++), p);
        }
        assertEquals(13, hp.size());
        assertEquals(4, atk.size());
        Plan plan = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IV_COLUMNS, Set.of());
        PcModel after = applyLikeServer(m, plan.operations());
        // PS: columnas 0 y 1 llenas (10) + 3 arriba en la columna 2; Ataque empieza en la columna 3.
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 5; row++) {
                UUID u = after.at(0, row * 6 + col);
                if (col < 2 || row < 3) assertTrue(u != null && after.info(u).name().startsWith("H"), "col " + col + " fila " + row);
                else assertNull(u, "hueco esperado en col 2 fila " + row);
            }
        }
        for (int row = 0; row < 4; row++) {
            UUID u = after.at(0, row * 6 + 3);
            assertTrue(u != null && after.info(u).name().startsWith("A"), "Ataque en columna 3, fila " + row);
        }
        assertEquals(17, after.allInfos().size());
    }

    @Test
    void ivColumnsFallsBackWhenGapsDoNotFit() {
        PcModel m = new PcModel(1);
        StatKey[] ks = StatKey.values();
        // 30 Pokémon, 6 grupos de 5 → cabe justo (una columna por grupo).
        int slot = 0;
        for (int g = 0; g < 6; g++) for (int i = 0; i < 5; i++) m.put(new Pos(0, slot++), mon("M" + g + i, perfectIn(ks[g])));
        Plan plan = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IV_COLUMNS, Set.of());
        PcModel after = applyLikeServer(m, plan.operations());
        for (int g = 0; g < 6; g++) {
            for (int row = 0; row < 5; row++) {
                assertEquals("M" + g, after.info(after.at(0, row * 6 + g)).name().substring(0, 2));
            }
        }
        // 6 grupos de 4 + 6 sueltos de PS: con saltos no cabe; se llenan columnas seguidas sin perder a nadie.
        PcModel m2 = new PcModel(1);
        slot = 0;
        for (int g = 0; g < 6; g++) for (int i = 0; i < 4; i++) m2.put(new Pos(0, slot++), mon("N" + g + i, perfectIn(ks[g])));
        for (int i = 0; i < 6; i++) m2.put(new Pos(0, slot++), mon("X" + i, perfectIn(StatKey.HP)));
        Plan p2 = Planner.arrange(m2, List.of(0), com.diosesmon.organizerpc.core.SortMode.IV_COLUMNS, Set.of());
        PcModel a2 = applyLikeServer(m2, p2.operations());
        assertEquals(30, a2.allInfos().size());
        assertEquals(0, a2.freeSlots(0));
    }

    @Test
    void ivColumnsGroupsTwoOrMorePerfectTogether() {
        PcModel m = new PcModel(1);
        int slot = 0;
        // 2 con 31 en PS, luego 3 de 2x31, 2 de 3x31 y 1 de 6x31: estos seis comparten columnas.
        m.put(new Pos(0, slot++), mon("H1", perfectIn(StatKey.HP)));
        m.put(new Pos(0, slot++), mon("H2", perfectIn(StatKey.HP)));
        for (int i = 0; i < 3; i++) m.put(new Pos(0, slot++), mon("D" + i, ivsWithPerfect(2)));
        for (int i = 0; i < 2; i++) m.put(new Pos(0, slot++), mon("T" + i, ivsWithPerfect(3)));
        m.put(new Pos(0, slot++), mon("S0", ivsWithPerfect(6)));
        Plan plan = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IV_COLUMNS, Set.of());
        PcModel after = applyLikeServer(m, plan.operations());
        // Columna 0: H1, H2. Columna 1 (5 filas) y columna 2: D0 D1 D2 T0 T1 | S0, seguidos.
        assertEquals("H", after.info(after.at(0, 0)).name().substring(0, 1));
        assertEquals("H", after.info(after.at(0, 6)).name().substring(0, 1));
        assertNull(after.at(0, 12));
        String[] col1 = {"D", "D", "D", "T", "T"};
        for (int row = 0; row < 5; row++) assertEquals(col1[row], after.info(after.at(0, row * 6 + 1)).name().substring(0, 1));
        assertEquals("S0", after.info(after.at(0, 2)).name());
    }

    @Test
    void ivColumnsWithoutFillSendsLeftoversToCapture() {
        PcModel m = new PcModel(2);
        StatKey[] ks = StatKey.values();
        int slot = 0;
        // 6 grupos de 4 + 6 más de PS (10 en PS = 2 columnas) → 7 columnas: Vel no cabe en la caja 1.
        for (int g = 0; g < 6; g++) for (int i = 0; i < 4; i++) m.put(new Pos(0, slot++), mon("N" + g + i, perfectIn(ks[g])));
        for (int i = 0; i < 6; i++) m.put(new Pos(0, slot++), mon("X" + i, perfectIn(StatKey.HP)));
        Plan plan = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IV_COLUMNS, com.diosesmon.organizerpc.core.SortMode.NONE, Set.of(), Set.of(1));
        PcModel after = applyLikeServer(m, plan.operations());
        assertEquals(30, after.allInfos().size());
        assertEquals(4, 30 - after.freeSlots(1)); // 4 de Vel en la caja de captura
        for (int s = 0; s < 4; s++) assertEquals(StatKey.SPEED, statOf(after.info(after.at(1, s))));
        // Fill: todo seguido, sin huecos y sin usar la captura.
        Plan fill = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.IV_COLUMNS_FILL, com.diosesmon.organizerpc.core.SortMode.NONE, Set.of(), Set.of(1));
        PcModel a2 = applyLikeServer(m, fill.operations());
        assertEquals(0, a2.freeSlots(0));
        assertEquals(30, a2.freeSlots(1));
    }

    private static StatKey statOf(PokemonInfo p) {
        for (StatKey k : StatKey.values()) if (p.iv(k) == 31) return k;
        return null;
    }

    private static PokemonInfo dexMon(String species, int dex, int perfect) {
        return new PokemonInfo(UUID.randomUUID(), species, "cobblemon:" + species, dex, 50, ivsWithPerfect(perfect), null, false);
    }

    @Test
    void livingDexLeavesGapForMissingSpecies() {
        var old = com.diosesmon.organizerpc.core.LivingDex.families;
        try {
            List<String> fire = List.of("cobblemon:charmander", "cobblemon:charmeleon", "cobblemon:charizard");
            List<String> grass = List.of("cobblemon:bulbasaur", "cobblemon:ivysaur", "cobblemon:venusaur");
            com.diosesmon.organizerpc.core.LivingDex.families = id -> fire.contains(id) ? fire : grass.contains(id) ? grass : List.of(id);
            PcModel m = new PcModel(1);
            // Sin Charmander ni Ivysaur; dos Charizard (el de más 31 va a su hueco, el otro al final).
            m.put(new Pos(0, 0), dexMon("charizard", 6, 1));
            m.put(new Pos(0, 1), dexMon("charmeleon", 5, 0));
            m.put(new Pos(0, 2), dexMon("venusaur", 3, 0));
            m.put(new Pos(0, 3), dexMon("bulbasaur", 1, 0));
            m.put(new Pos(0, 4), dexMon("charizard", 6, 4));
            Plan plan = Planner.arrange(m, List.of(0), com.diosesmon.organizerpc.core.SortMode.LIVING_DEX, Set.of());
            PcModel a = applyLikeServer(m, plan.operations());
            assertEquals("bulbasaur", a.info(a.at(0, 0)).name());
            assertNull(a.at(0, 1)); // falta Ivysaur
            assertEquals("venusaur", a.info(a.at(0, 2)).name());
            assertNull(a.at(0, 3)); // falta Charmander
            assertEquals("charmeleon", a.info(a.at(0, 4)).name());
            assertEquals("charizard", a.info(a.at(0, 5)).name());
            assertEquals(4, a.info(a.at(0, 5)).perfectIvCount());
            assertEquals("charizard", a.info(a.at(0, 6)).name()); // repetido al final
            assertEquals(5, a.allInfos().size());
        } finally {
            com.diosesmon.organizerpc.core.LivingDex.families = old;
        }
    }
}
