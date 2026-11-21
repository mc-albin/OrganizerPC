package com.diosesmon.organizerpc.core.plan;

import com.diosesmon.organizerpc.core.PokemonInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Copia simulable de la PC. El planificador trabaja aquí, nunca sobre los datos de Cobblemon. */
public final class PcModel {
    public static final int SLOTS_PER_BOX = 30;

    private final UUID[][] slots;
    private final Map<UUID, Pos> positions = new HashMap<>();
    private final Map<UUID, PokemonInfo> infos = new HashMap<>();

    public PcModel(int boxCount) {
        slots = new UUID[boxCount][SLOTS_PER_BOX];
    }

    public int boxCount() {
        return slots.length;
    }

    public void put(Pos pos, PokemonInfo info) {
        if (slots[pos.box()][pos.slot()] != null) throw new IllegalStateException("Espacio ocupado " + pos);
        if (positions.containsKey(info.uuid())) throw new IllegalStateException("UUID duplicado " + info.uuid());
        slots[pos.box()][pos.slot()] = info.uuid();
        positions.put(info.uuid(), pos);
        infos.put(info.uuid(), info);
    }

    public UUID at(Pos pos) {
        return slots[pos.box()][pos.slot()];
    }

    public UUID at(int box, int slot) {
        return slots[box][slot];
    }

    public Pos positionOf(UUID uuid) {
        return positions.get(uuid);
    }

    public PokemonInfo info(UUID uuid) {
        return infos.get(uuid);
    }

    public Map<UUID, PokemonInfo> allInfos() {
        return infos;
    }

    public int firstEmpty(int box) {
        for (int s = 0; s < SLOTS_PER_BOX; s++) if (slots[box][s] == null) return s;
        return -1;
    }

    public int freeSlots(int box) {
        int n = 0;
        for (int s = 0; s < SLOTS_PER_BOX; s++) if (slots[box][s] == null) n++;
        return n;
    }

    public void move(UUID uuid, Pos to) {
        Pos from = positions.get(uuid);
        if (from == null) throw new IllegalStateException("Pokémon desconocido");
        if (slots[to.box()][to.slot()] != null) throw new IllegalStateException("Destino ocupado " + to);
        slots[from.box()][from.slot()] = null;
        slots[to.box()][to.slot()] = uuid;
        positions.put(uuid, to);
    }

    public void swap(Pos a, Pos b) {
        UUID ua = at(a), ub = at(b);
        if (ua == null || ub == null) throw new IllegalStateException("Intercambio con espacio vacío");
        slots[a.box()][a.slot()] = ub;
        slots[b.box()][b.slot()] = ua;
        positions.put(ua, b);
        positions.put(ub, a);
    }

    public PcModel copy() {
        PcModel m = new PcModel(slots.length);
        for (Map.Entry<UUID, Pos> e : positions.entrySet()) {
            m.put(e.getValue(), infos.get(e.getKey()));
        }
        return m;
    }
}
