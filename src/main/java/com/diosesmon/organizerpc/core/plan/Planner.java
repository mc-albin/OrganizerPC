package com.diosesmon.organizerpc.core.plan;

import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.OrganizerConfig.UnsortedMode;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.SortMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Calcula la secuencia de movimientos sobre una copia de la PC.
 *
 * Garantías:
 * - Solo genera MOVE a espacios vacíos y SWAP entre dos Pokémon existentes, igual que la PC original.
 *   Ninguna operación crea ni borra Pokémon, así que no puede haber pérdidas ni duplicados.
 * - Las cajas bloqueadas nunca son origen ni destino.
 * - Las cajas de captura nunca son destino (quedan libres para los Pokémon nuevos).
 * - Solo se mueven Pokémon de las cajas del alcance.
 */
public final class Planner {

    /**
     * Datos de entrada para organizar.
     *
     * @param scopeBoxes cajas cuyos Pokémon se pueden recolocar (las bloqueadas se ignoran igualmente).
     * @param captureBoxes cajas para capturas nuevas: se vacían hacia las categorías y no reciben Pokémon.
     */
    public record Request(PcModel model, List<Category> categories, Set<Integer> scopeBoxes,
                          Set<Integer> lockedBoxes, UnsortedMode unsortedMode, int unsortedBox,
                          Map<UUID, String> overrides, boolean evictStrays, Set<Integer> captureBoxes,
                          boolean compact, SortMode captureSort, SortMode captureSort2) {

        public Request(PcModel model, List<Category> categories, Set<Integer> scopeBoxes,
                       Set<Integer> lockedBoxes, UnsortedMode unsortedMode, int unsortedBox,
                       Map<UUID, String> overrides, boolean evictStrays, Set<Integer> captureBoxes, boolean compact) {
            this(model, categories, scopeBoxes, lockedBoxes, unsortedMode, unsortedBox, overrides, evictStrays, captureBoxes,
                    compact, SortMode.NONE, SortMode.NONE);
        }

        public Request(PcModel model, List<Category> categories, Set<Integer> scopeBoxes,
                       Set<Integer> lockedBoxes, UnsortedMode unsortedMode, int unsortedBox,
                       Map<UUID, String> overrides, boolean evictStrays, Set<Integer> captureBoxes) {
            this(model, categories, scopeBoxes, lockedBoxes, unsortedMode, unsortedBox, overrides, evictStrays, captureBoxes, false,
                    SortMode.NONE, SortMode.NONE);
        }

        public Request(PcModel model, List<Category> categories, Set<Integer> scopeBoxes,
                       Set<Integer> lockedBoxes, UnsortedMode unsortedMode, int unsortedBox,
                       Map<UUID, String> overrides, boolean evictStrays) {
            this(model, categories, scopeBoxes, lockedBoxes, unsortedMode, unsortedBox, overrides, evictStrays, Set.of(), false,
                    SortMode.NONE, SortMode.NONE);
        }

        /** Por defecto, los que no cumplen salen de las cajas de categoría. */
        public Request(PcModel model, List<Category> categories, Set<Integer> scopeBoxes,
                       Set<Integer> lockedBoxes, UnsortedMode unsortedMode, int unsortedBox,
                       Map<UUID, String> overrides) {
            this(model, categories, scopeBoxes, lockedBoxes, unsortedMode, unsortedBox, overrides, true, Set.of(), false,
                    SortMode.NONE, SortMode.NONE);
        }
    }

    /** Clave de override: el Pokémon no se asigna a ninguna categoría. */
    public static final String NO_CATEGORY = "";

    private Planner() {
    }

    /** Cajas a las que no puede llegar nadie: bloqueadas y de captura. */
    private static Set<Integer> blocked(Set<Integer> locked, Set<Integer> capture) {
        Set<Integer> s = new TreeSet<>(locked);
        if (capture != null) s.addAll(capture);
        return s;
    }

    /** Categorías activas, en orden de prioridad, que el Pokémon cumple y tienen alguna caja usable. */
    public static List<Category> matching(PokemonInfo p, List<Category> categories, int boxCount, Set<Integer> locked) {
        List<Category> out = new ArrayList<>();
        for (Category c : categories) {
            if (c.enabled && !c.boxes(boxCount, locked).isEmpty() && c.matches(p)) out.add(c);
        }
        return out;
    }

    public static Plan organize(Request req) {
        PcModel original = req.model();
        PcModel m = original.copy();
        int boxCount = m.boxCount();
        Set<Integer> locked = req.lockedBoxes() == null ? Set.of() : req.lockedBoxes();
        Set<Integer> noTarget = blocked(locked, req.captureBoxes());

        List<Category> active = new ArrayList<>();
        for (Category c : req.categories()) {
            if (c.enabled && !c.boxes(boxCount, noTarget).isEmpty()) active.add(c);
        }

        Set<Integer> targetBoxes = new TreeSet<>();
        for (Category c : active) targetBoxes.addAll(c.boxes(boxCount, noTarget));

        Set<Integer> scope = new TreeSet<>();
        for (int b : req.scopeBoxes()) if (b >= 0 && b < boxCount && !locked.contains(b)) scope.add(b);

        // Cajas donde puede ir un Pokémon sin categoría que ocupa la caja de una categoría.
        List<Integer> freeBoxes = new ArrayList<>();
        for (int b = 0; b < boxCount; b++) if (!targetBoxes.contains(b) && !noTarget.contains(b)) freeBoxes.add(b);

        boolean unsortedToBox = req.unsortedMode() == UnsortedMode.MOVE_TO_BOX
                && req.unsortedBox() >= 0 && req.unsortedBox() < boxCount && !noTarget.contains(req.unsortedBox());

        // 1. Decidir el destino de cada Pokémon dentro del alcance.
        Map<UUID, List<Integer>> dest = new HashMap<>();
        Map<UUID, Category> chosenCat = new HashMap<>();
        Map<UUID, List<String>> matchIds = new HashMap<>();
        List<UUID> scoped = new ArrayList<>();
        for (int b : scope) {
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                UUID u = m.at(b, s);
                if (u != null) scoped.add(u);
            }
        }

        for (UUID u : scoped) {
            PokemonInfo info = m.info(u);
            List<Category> matches = matching(info, active, boxCount, noTarget);
            List<String> ids = new ArrayList<>();
            for (Category c : matches) ids.add(c.id);
            matchIds.put(u, ids);

            Category chosen = matches.isEmpty() ? null : matches.get(0);
            String override = req.overrides() == null ? null : req.overrides().get(u);
            if (override != null) {
                if (override.equals(NO_CATEGORY)) {
                    chosen = null;
                } else {
                    for (Category c : matches) if (c.id.equals(override)) chosen = c;
                }
            }

            int currentBox = m.positionOf(u).box();
            if (chosen != null) {
                chosenCat.put(u, chosen);
                dest.put(u, chosen.boxes(boxCount, noTarget));
            } else if (unsortedToBox) {
                dest.put(u, List.of(req.unsortedBox()));
            } else if (req.evictStrays() && targetBoxes.contains(currentBox)) {
                dest.put(u, freeBoxes);
            }
        }

        // 2. Pendientes: Pokémon que no están en una caja de su destino.
        LinkedHashSet<UUID> pending = new LinkedHashSet<>();
        for (UUID u : scoped) {
            List<Integer> d = dest.get(u);
            if (d != null && !d.contains(m.positionOf(u).box())) pending.add(u);
        }

        List<Operation> ops = new ArrayList<>();
        boolean progress = true;
        int guard = scoped.size() * 4 + 16;
        while (progress && !pending.isEmpty() && guard-- > 0) {
            progress = false;
            for (UUID u : new ArrayList<>(pending)) {
                if (!pending.contains(u)) continue;
                List<Integer> d = dest.get(u);
                Pos from = m.positionOf(u);
                if (d.contains(from.box())) {
                    pending.remove(u);
                    continue;
                }
                Pos empty = firstEmpty(m, d);
                if (empty != null) {
                    ops.add(Operation.move(u, from, empty));
                    m.move(u, empty);
                    pending.remove(u);
                    progress = true;
                    continue;
                }
                Pos victimPos = findVictim(m, d, pending, dest, from.box(), u);
                if (victimPos != null) {
                    UUID v = m.at(victimPos);
                    ops.add(Operation.swap(u, from, v, victimPos));
                    m.swap(from, victimPos);
                    pending.remove(u);
                    if (dest.get(v).contains(m.positionOf(v).box())) pending.remove(v);
                    progress = true;
                }
            }
        }

        // 3. Ordenar / juntar dentro de las cajas de cada categoría (solo cajas del alcance).
        for (Category c : active) {
            boolean sort = c.sortMode != null && c.sortMode != SortMode.NONE;
            if (!sort && !req.compact()) continue;
            List<Integer> boxes = new ArrayList<>();
            for (int b : c.boxes(boxCount, noTarget)) if (scope.contains(b)) boxes.add(b);
            if (boxes.isEmpty()) continue;
            sortInto(m, ops, boxes, c, chosenCat, overflowBoxes(req.captureBoxes(), locked, m.boxCount()));
        }
        // Cajas de captura: los que quedan, seguidos (y en su orden si se eligió uno).
        boolean captureSorted = req.captureSort() != null && req.captureSort() != SortMode.NONE;
        if ((req.compact() || captureSorted) && req.captureBoxes() != null) {
            List<Integer> cap = new ArrayList<>();
            for (int b : new TreeSet<>(req.captureBoxes())) if (scope.contains(b)) cap.add(b);
            if (!cap.isEmpty()) {
                place(m, ops, cap, membersOf(m, cap), req.captureSort(), req.captureSort2());
            }
        }

        // 4. Resumen.
        List<Plan.Entry> entries = new ArrayList<>();
        for (UUID u : scoped) {
            Pos a = original.positionOf(u);
            Pos b = m.positionOf(u);
            if (a.equals(b)) continue;
            Category c = chosenCat.get(u);
            Plan.Reason reason;
            String left = null;
            if (c != null) {
                reason = c.ownsBox(a.box()) ? Plan.Reason.SORTED : Plan.Reason.CATEGORY;
            } else if (unsortedToBox && b.box() == req.unsortedBox()) {
                reason = Plan.Reason.UNSORTED_BOX;
            } else if (dest.containsKey(u) && a.box() != b.box()) {
                reason = Plan.Reason.EVICTED;
                for (Category oc : active) {
                    if (oc.ownsBox(a.box())) {
                        left = oc.name;
                        break;
                    }
                }
            } else if (a.box() == b.box()) {
                reason = Plan.Reason.SORTED;
            } else {
                reason = Plan.Reason.SWAPPED;
            }
            entries.add(new Plan.Entry(u, m.info(u), a, b, c == null ? null : c.id,
                    c == null ? null : c.name, matchIds.getOrDefault(u, List.of()), reason, left));
        }
        List<PokemonInfo> noSpace = new ArrayList<>();
        for (UUID u : pending) noSpace.add(m.info(u));
        for (UUID u : scoped) {
            List<Integer> d = dest.get(u);
            if (d != null && d.isEmpty() && !pending.contains(u)) noSpace.add(m.info(u));
        }
        return new Plan(Collections.unmodifiableList(ops), entries, noSpace, scoped.size(), original, m);
    }

    /** Cajas de captura usables para los que no caben en columnas (sin bloqueadas). */
    private static List<Integer> overflowBoxes(Set<Integer> capture, Set<Integer> locked, int boxCount) {
        List<Integer> out = new ArrayList<>();
        if (capture == null) return out;
        for (int b : new TreeSet<>(capture)) if (b >= 0 && b < boxCount && !locked.contains(b)) out.add(b);
        return out;
    }

    /** Pokémon de las cajas dadas en orden de posición. */
    private static List<UUID> membersOf(PcModel m, List<Integer> boxes) {
        List<UUID> out = new ArrayList<>();
        for (int b : boxes) {
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                UUID u = m.at(b, s);
                if (u != null) out.add(u);
            }
        }
        return out;
    }

    /** Ordena/junta los Pokémon de la categoría dentro de sus cajas. */
    private static void sortInto(PcModel m, List<Operation> ops, List<Integer> boxes, Category c,
                                 Map<UUID, Category> chosenCat, List<Integer> overflow) {
        List<UUID> members = new ArrayList<>();
        for (UUID u : membersOf(m, boxes)) {
            Category chosen = chosenCat.get(u);
            if ((chosen == null || chosen == c) && c.matches(m.info(u))) members.add(u);
        }
        place(m, ops, boxes, members, c.sortMode, c.sortMode2, overflow);
    }

    /** Coloca con dos órdenes; «Columnas por IV» usa el reparto vertical, el resto va seguido. */
    private static void place(PcModel m, List<Operation> ops, List<Integer> boxes, List<UUID> members,
                              SortMode a, SortMode b) {
        place(m, ops, boxes, members, a, b, List.of());
    }

    /**
     * Igual; con «Columnas por IV» sin completar, los que no caben en sus cajas van a los huecos
     * libres de {@code overflow} (las cajas de captura). Si tampoco caben allí, se quedan en los
     * huecos que quedan en sus cajas.
     */
    private static void place(PcModel m, List<Operation> ops, List<Integer> boxes, List<UUID> members,
                              SortMode a, SortMode b, List<Integer> overflow) {
        Comparator<PokemonInfo> cmp = SortMode.chain(a, b);
        SortMode main = SortMode.normalize(a, b)[0];
        if (main == SortMode.LIVING_DEX) {
            placeLivingDex(m, ops, boxes, members, SortMode.normalize(a, b)[1], overflow);
            return;
        }
        if (!main.columns()) {
            placeInOrder(m, ops, boxes, members, cmp);
            return;
        }
        List<UUID> ordered = new ArrayList<>(members);
        ordered.sort((x, y) -> cmp.compare(m.info(x), m.info(y)));
        List<Pos> targets = columnTargets(m, ordered, boxes, main == SortMode.IV_COLUMNS);
        applyTargets(m, ops, ordered.subList(0, targets.size()), targets);
        List<Integer> extra = new ArrayList<>();
        for (int bx : overflow) if (!boxes.contains(bx) && !extra.contains(bx)) extra.add(bx);
        for (UUID u : ordered.subList(targets.size(), ordered.size())) {
            Pos to = firstEmpty(m, extra);
            if (to == null) break;
            ops.add(Operation.move(u, m.positionOf(u), to));
            m.move(u, to);
        }
    }

    /**
     * Living dex: el primero de cada especie (según el 2º orden; si no hay, el de más 31) va al hueco
     * de su especie dentro de su familia; los huecos de especies que faltan quedan vacíos. Los
     * repetidos van detrás, seguidos. Lo que no cabe va a {@code overflow} (captura).
     */
    private static void placeLivingDex(PcModel m, List<Operation> ops, List<Integer> boxes, List<UUID> members,
                                       SortMode tie, List<Integer> overflow) {
        List<PokemonInfo> infos = new ArrayList<>();
        for (UUID u : members) infos.add(m.info(u));
        com.diosesmon.organizerpc.core.LivingDex.Layout layout = com.diosesmon.organizerpc.core.LivingDex.layout(infos);
        Comparator<PokemonInfo> pick = tie == null || tie == SortMode.NONE
                ? Comparator.comparingInt(PokemonInfo::perfectIvCount).reversed()
                : tie.comparator();
        List<UUID> ordered = new ArrayList<>(members);
        ordered.sort((x, y) -> pick.compare(m.info(x), m.info(y)));
        int capacity = boxes.size() * PcModel.SLOTS_PER_BOX;
        java.util.Set<String> used = new java.util.HashSet<>();
        List<UUID> placed = new ArrayList<>();
        List<Pos> targets = new ArrayList<>();
        List<UUID> extras = new ArrayList<>();
        for (UUID u : ordered) {
            String sp = m.info(u).speciesId();
            Integer idx = layout.slotOfSpecies().get(sp);
            if (idx != null && used.add(sp) && idx < capacity) {
                placed.add(u);
                targets.add(new Pos(boxes.get(idx / PcModel.SLOTS_PER_BOX), idx % PcModel.SLOTS_PER_BOX));
            } else {
                extras.add(u);
            }
        }
        // Repetidos detrás de la última especie, en orden de Pokédex.
        extras.sort(Comparator.comparingInt((UUID u) -> m.info(u).dex()));
        int next = Math.min(layout.length(), capacity);
        List<UUID> left = new ArrayList<>();
        for (UUID u : extras) {
            if (next < capacity) {
                placed.add(u);
                targets.add(new Pos(boxes.get(next / PcModel.SLOTS_PER_BOX), next % PcModel.SLOTS_PER_BOX));
                next++;
            } else {
                left.add(u);
            }
        }
        applyTargets(m, ops, placed, targets);
        List<Integer> extra = new ArrayList<>();
        for (int bx : overflow) if (!boxes.contains(bx) && !extra.contains(bx)) extra.add(bx);
        for (UUID u : left) {
            Pos to = firstEmpty(m, extra);
            if (to == null) break;
            ops.add(Operation.move(u, m.positionOf(u), to));
            m.move(u, to);
        }
    }

    /** Columnas por caja (la PC de Cobblemon tiene 6 columnas y 5 filas; hueco = fila * 6 + columna). */
    static final int COLS = 6, ROWS = PcModel.SLOTS_PER_BOX / COLS;

    /**
     * Huecos destino en vertical: de arriba abajo por columnas. Con {@code breaks}, cada grupo
     * ({@link SortMode#columnGroup}) empieza en una columna nueva. Devuelve los huecos de los
     * primeros que caben (puede ser más corta que {@code ordered}).
     */
    static List<Pos> columnTargets(PcModel m, List<UUID> ordered, List<Integer> boxes, boolean breaks) {
        List<Pos> out = new ArrayList<>();
        int col = 0, row = 0, prevMask = -1;
        for (UUID u : ordered) {
            int mask = SortMode.columnGroup(m.info(u));
            if (breaks && prevMask >= 0 && mask != prevMask && row != 0) {
                col++;
                row = 0;
            }
            prevMask = mask;
            int bi = col / COLS;
            if (bi >= boxes.size()) return out;
            out.add(new Pos(boxes.get(bi), row * COLS + col % COLS));
            if (++row == ROWS) {
                row = 0;
                col++;
            }
        }
        return out;
    }

    /** Lleva cada Pokémon a su hueco destino: MOVE si está vacío, SWAP si lo ocupa otro. */
    private static void applyTargets(PcModel m, List<Operation> ops, List<UUID> ordered, List<Pos> targets) {
        for (int i = 0; i < ordered.size(); i++) {
            Pos target = targets.get(i);
            UUID want = ordered.get(i);
            Pos cur = m.positionOf(want);
            if (cur.equals(target)) continue;
            UUID occupant = m.at(target);
            if (occupant == null) {
                ops.add(Operation.move(want, cur, target));
                m.move(want, target);
            } else {
                ops.add(Operation.swap(want, cur, occupant, target));
                m.swap(cur, target);
            }
        }
    }

    /**
     * Coloca {@code members} (ordenados de forma estable con {@code mode}) seguidos desde el primer
     * hueco de la primera caja, sin dejar huecos. Cada paso deja fijo un hueco: MOVE si está vacío,
     * SWAP si lo ocupa otro.
     */
    private static void placeInOrder(PcModel m, List<Operation> ops, List<Integer> boxes, List<UUID> members,
                                     Comparator<PokemonInfo> cmp) {
        List<UUID> ordered = new ArrayList<>(members);
        ordered.sort((a, b) -> cmp.compare(m.info(a), m.info(b)));
        int i = 0;
        for (int b : boxes) {
            for (int s = 0; s < PcModel.SLOTS_PER_BOX && i < ordered.size(); s++, i++) {
                Pos target = new Pos(b, s);
                UUID want = ordered.get(i);
                Pos cur = m.positionOf(want);
                if (cur.equals(target)) continue;
                UUID occupant = m.at(target);
                if (occupant == null) {
                    ops.add(Operation.move(want, cur, target));
                    m.move(want, target);
                } else {
                    ops.add(Operation.swap(want, cur, occupant, target));
                    m.swap(cur, target);
                }
            }
        }
    }

    /**
     * «Ordenar cajas»: junta todos los Pokémon de las cajas elegidas (en ese orden de cajas),
     * sin huecos, y los ordena con {@code mode}. Las cajas bloqueadas se ignoran.
     */
    public static Plan arrange(PcModel original, List<Integer> boxes, SortMode mode, Set<Integer> locked) {
        return arrange(original, boxes, mode, SortMode.NONE, locked);
    }

    /** Igual, con un segundo orden de desempate. */
    public static Plan arrange(PcModel original, List<Integer> boxes, SortMode mode, SortMode mode2, Set<Integer> locked) {
        return arrange(original, boxes, mode, mode2, locked, Set.of());
    }

    /** Igual; con columnas sin completar, los que no caben van a las cajas de captura dadas. */
    public static Plan arrange(PcModel original, List<Integer> boxes, SortMode mode, SortMode mode2, Set<Integer> locked,
                               Set<Integer> capture) {
        PcModel m = original.copy();
        List<Integer> usable = new ArrayList<>();
        for (int b : boxes) if (b >= 0 && b < m.boxCount() && !locked.contains(b) && !usable.contains(b)) usable.add(b);
        List<Operation> ops = new ArrayList<>();
        List<UUID> members = membersOf(m, usable);
        place(m, ops, usable, members, mode, mode2, overflowBoxes(capture, locked, m.boxCount()));
        List<Plan.Entry> entries = new ArrayList<>();
        for (UUID u : members) {
            Pos a = original.positionOf(u), b = m.positionOf(u);
            if (!a.equals(b)) entries.add(new Plan.Entry(u, m.info(u), a, b, null, null, List.of(), Plan.Reason.SORTED, null));
        }
        return new Plan(Collections.unmodifiableList(ops), entries, List.of(), members.size(), original, m);
    }

    /**
     * Mueve los Pokémon marcados (en el orden dado) a huecos libres de una caja.
     * Se ignoran los que ya están allí o en cajas bloqueadas; los que no caben se reportan sin espacio.
     */
    public static Plan moveSelection(PcModel original, List<UUID> ordered, int targetBox, Set<Integer> locked) {
        return moveSelection(original, ordered, List.of(targetBox), locked);
    }

    /**
     * Mueve los marcados a las cajas destino y, si {@code mode} no es NONE, deja esas cajas
     * ordenadas y sin huecos (incluidos los Pokémon que ya estaban en ellas).
     */
    public static Plan moveAndArrange(PcModel original, List<UUID> ordered, List<Integer> targetBoxes,
                                      Set<Integer> locked, SortMode mode) {
        return moveAndArrange(original, ordered, targetBoxes, locked, mode, SortMode.NONE);
    }

    public static Plan moveAndArrange(PcModel original, List<UUID> ordered, List<Integer> targetBoxes,
                                      Set<Integer> locked, SortMode mode, SortMode mode2) {
        return moveAndArrange(original, ordered, targetBoxes, locked, mode, mode2, Set.of());
    }

    public static Plan moveAndArrange(PcModel original, List<UUID> ordered, List<Integer> targetBoxes,
                                      Set<Integer> locked, SortMode mode, SortMode mode2, Set<Integer> capture) {
        Plan moved = moveSelection(original, ordered, targetBoxes, locked);
        if (mode == null || mode == SortMode.NONE || moved.isEmpty()) return moved;
        PcModel m = moved.after().copy();
        List<Operation> ops = new ArrayList<>(moved.operations());
        List<Integer> usable = new ArrayList<>();
        for (int b : targetBoxes) if (b >= 0 && b < m.boxCount() && !locked.contains(b) && !usable.contains(b)) usable.add(b);
        List<UUID> members = membersOf(m, usable);
        place(m, ops, usable, members, mode, mode2, overflowBoxes(capture, locked, m.boxCount()));
        List<Plan.Entry> entries = new ArrayList<>();
        java.util.Set<UUID> manual = new java.util.HashSet<>();
        for (Plan.Entry e : moved.entries()) manual.add(e.uuid());
        for (UUID u : m.allInfos().keySet()) {
            Pos a = original.positionOf(u), b = m.positionOf(u);
            if (a == null || a.equals(b)) continue;
            entries.add(new Plan.Entry(u, m.info(u), a, b, null, null, List.of(),
                    manual.contains(u) ? Plan.Reason.MANUAL : Plan.Reason.SORTED, null));
        }
        return new Plan(Collections.unmodifiableList(ops), entries, moved.noSpace(), moved.scannedCount(), original, m);
    }

    /** Igual, pero llenando varias cajas destino en orden (por ejemplo las cajas de una categoría). */
    public static Plan moveSelection(PcModel original, List<UUID> ordered, List<Integer> targetBoxes, Set<Integer> locked) {
        PcModel m = original.copy();
        List<Operation> ops = new ArrayList<>();
        List<Plan.Entry> entries = new ArrayList<>();
        List<PokemonInfo> noSpace = new ArrayList<>();
        List<Integer> targets = new ArrayList<>();
        for (int b : targetBoxes) if (b >= 0 && b < m.boxCount() && !locked.contains(b) && !targets.contains(b)) targets.add(b);
        int scanned = 0;
        if (targets.isEmpty()) return new Plan(ops, entries, noSpace, 0, original, m);
        for (UUID u : ordered) {
            Pos from = m.positionOf(u);
            if (from == null || locked.contains(from.box())) continue;
            scanned++;
            if (targets.contains(from.box())) continue;
            Pos to = firstEmpty(m, targets);
            if (to == null) {
                noSpace.add(m.info(u));
                continue;
            }
            ops.add(Operation.move(u, from, to));
            m.move(u, to);
            entries.add(new Plan.Entry(u, m.info(u), from, to, null, null, List.of(), Plan.Reason.MANUAL, null));
        }
        return new Plan(ops, entries, noSpace, scanned, original, m);
    }

    private static Pos firstEmpty(PcModel m, List<Integer> boxes) {
        for (int b : boxes) {
            int s = m.firstEmpty(b);
            if (s >= 0) return new Pos(b, s);
        }
        return null;
    }

    /** Busca en las cajas destino un Pokémon pendiente; prefiere uno que quiera ir a la caja de origen. */
    private static Pos findVictim(PcModel m, List<Integer> boxes, Set<UUID> pending,
                                  Map<UUID, List<Integer>> dest, int fromBox, UUID self) {
        Pos fallback = null;
        for (int b : boxes) {
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                UUID v = m.at(b, s);
                if (v == null || v.equals(self) || !pending.contains(v)) continue;
                if (dest.get(v).contains(fromBox)) return new Pos(b, s);
                if (fallback == null) fallback = new Pos(b, s);
            }
        }
        return fallback;
    }

    /** Ordenaciones para traer Pokémon marcados. */
    public enum SortModeManual {
        ORIGINAL("Como están"),
        IVS_DESC("Más 31 primero"),
        DEX("Nº Pokédex"),
        LEVEL_DESC("Mayor nivel"),
        NAME("Nombre A-Z");

        public final String label;

        SortModeManual(String label) {
            this.label = label;
        }

        public SortModeManual next() {
            SortModeManual[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public Comparator<PokemonInfo> comparator() {
            return switch (this) {
                case ORIGINAL -> (a, b) -> 0;
                case IVS_DESC -> Comparator.comparingInt(PokemonInfo::perfectIvCount).reversed();
                case DEX -> Comparator.comparingInt(PokemonInfo::dex);
                case LEVEL_DESC -> Comparator.comparingInt(PokemonInfo::level).reversed();
                case NAME -> Comparator.comparing(PokemonInfo::name, String.CASE_INSENSITIVE_ORDER);
            };
        }
    }
}
