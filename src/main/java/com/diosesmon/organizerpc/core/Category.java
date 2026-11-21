package com.diosesmon.organizerpc.core;

import com.diosesmon.organizerpc.core.filter.Condition;
import com.diosesmon.organizerpc.core.filter.ConditionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Categoría del usuario: QUÉ Pokémon (condiciones) y A QUÉ cajas van (elegidas por el usuario).
 * La prioridad es su posición en la lista (arriba = más prioridad).
 */
public final class Category {
    public enum Logic { AND, OR }

    /** 32 colores: tonos vivos y pastel. */
    public static final int[] PALETTE = {
            0xFFFF5C5C, 0xFFFF7A45, 0xFFFF9F43, 0xFFFFC857, 0xFFFFE066, 0xFFD4E157, 0xFF9CCC65, 0xFF4CD07D,
            0xFF26C6A0, 0xFF4DD6E0, 0xFF29B6F6, 0xFF5AA9FF, 0xFF7986CB, 0xFF9575CD, 0xFFB07CFF, 0xFFCE93D8,
            0xFFF783AC, 0xFFFF4F9A, 0xFFE57373, 0xFFBCAAA4, 0xFFC9A44A, 0xFFA1887F, 0xFF90A4AE, 0xFFE0E0E0,
            0xFFFFAB91, 0xFFFFCC80, 0xFFFFF59D, 0xFFC5E1A5, 0xFF80CBC4, 0xFF81D4FA, 0xFFB39DDB, 0xFFF8BBD0
    };

    public String id = UUID.randomUUID().toString();
    public String name = "NUEVA";
    public int color = PALETTE[0];
    public boolean enabled = true;
    /** Cajas destino elegidas (índice 0 = Caja 1). Se llenan en orden ascendente. */
    public final TreeSet<Integer> boxes = new TreeSet<>();
    public Logic logic = Logic.AND;
    /** Orden de los Pokémon dentro de sus cajas al organizar. */
    public SortMode sortMode = SortMode.NONE;
    /** Segundo orden (desempate). */
    public SortMode sortMode2 = SortMode.NONE;
    public final List<Condition> conditions = new ArrayList<>();

    /** Condiciones que de verdad filtran (las vacías = "da igual" no cuentan). */
    public List<Condition> activeConditions() {
        List<Condition> out = new ArrayList<>();
        for (Condition c : conditions) if (!c.isEmpty()) out.add(c);
        return out;
    }

    /** Una categoría sin condiciones activas no clasifica nada (evita mover todo por error). */
    public boolean matches(PokemonInfo p) {
        List<Condition> active = activeConditions();
        if (active.isEmpty()) return false;
        if (logic == Logic.AND) {
            for (Condition c : active) if (!c.test(p)) return false;
            return true;
        }
        for (Condition c : active) if (c.test(p)) return true;
        return false;
    }

    /** Cajas destino válidas y no bloqueadas, en orden de llenado. */
    public List<Integer> boxes(int pcBoxCount, Set<Integer> locked) {
        List<Integer> out = new ArrayList<>();
        for (int b : boxes) if (b >= 0 && b < pcBoxCount && !locked.contains(b)) out.add(b);
        return out;
    }

    public boolean ownsBox(int box) {
        return boxes.contains(box);
    }

    /** Devuelve la condición del tipo pedido, creándola si no existe. */
    @SuppressWarnings("unchecked")
    public <T extends Condition> T condition(ConditionType type) {
        for (Condition c : conditions) if (c.type() == type) return (T) c;
        Condition c = type.create();
        conditions.add(c);
        return (T) c;
    }

    public Category copy() {
        Category c = new Category();
        c.id = id;
        c.name = name;
        c.color = color;
        c.enabled = enabled;
        c.boxes.addAll(boxes);
        c.logic = logic;
        c.sortMode = sortMode;
        c.sortMode2 = sortMode2;
        for (Condition cond : conditions) c.conditions.add(cond.copy());
        return c;
    }

    /** «C35-38,40»: cajas seguidas como rango (sin «…», siempre completas). */
    public String boxesLabel() {
        if (boxes.isEmpty()) return "sin cajas";
        java.util.List<Integer> sorted = new java.util.ArrayList<>(new java.util.TreeSet<>(boxes));
        StringJoiner j = new StringJoiner(",");
        for (int i = 0; i < sorted.size(); ) {
            int a = sorted.get(i), k = i;
            while (k + 1 < sorted.size() && sorted.get(k + 1) == sorted.get(k) + 1) k++;
            int b = sorted.get(k);
            j.add(b - a >= 2 ? (a + 1) + "-" + (b + 1) : b == a ? String.valueOf(a + 1) : (a + 1) + "," + (b + 1));
            i = k + 1;
        }
        return "C" + j;
    }
}
