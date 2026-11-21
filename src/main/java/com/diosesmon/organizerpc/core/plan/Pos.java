package com.diosesmon.organizerpc.core.plan;

/** Posición real en la PC: caja (0..n-1) y espacio (0..29). */
public record Pos(int box, int slot) {
    @Override
    public String toString() {
        return "C" + (box + 1) + "#" + (slot + 1);
    }
}
