package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.StatKey;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Iconos (objetos de Cobblemon con alternativa vanilla) y colores de estadísticas. */
final class Icons {
    static final ItemStack POKE_BALL = of("cobblemon:poke_ball", Items.ENDER_EYE);
    static final ItemStack PC = of("cobblemon:pc", Items.CHEST);
    static final ItemStack BOX = new ItemStack(Items.CHEST);
    static final ItemStack MOVE = of("cobblemon:link_cable", Items.ENDER_PEARL);
    static final ItemStack IVS = of("cobblemon:destiny_knot", Items.STRING);
    static final ItemStack EGG = of("cobblemon:lucky_egg", Items.EGG);
    static final ItemStack TAG = new ItemStack(Items.NAME_TAG);
    static final ItemStack SETTINGS = new ItemStack(Items.COMPARATOR);
    static final ItemStack BOOK = new ItemStack(Items.BOOK);
    static final ItemStack BRUSH = new ItemStack(Items.BRUSH);
    static final ItemStack PLUS = new ItemStack(Items.WRITABLE_BOOK);
    static final ItemStack HIDE = new ItemStack(Items.ENDER_EYE);
    static final ItemStack FILTER = new ItemStack(Items.HOPPER);
    static final ItemStack PAIR = of("cobblemon:love_ball", Items.PINK_TULIP);
    static final ItemStack BORDERS = new ItemStack(Items.GLOW_ITEM_FRAME);
    static final ItemStack SEARCH = new ItemStack(Items.SPYGLASS);
    static final ItemStack THEME = new ItemStack(Items.MAGENTA_DYE);
    static final ItemStack SIZE = new ItemStack(Items.SPYGLASS);

    private Icons() {
    }

    private static ItemStack of(String id, Item fallback) {
        Item item = Registries.ITEM.get(Identifier.of(id));
        return new ItemStack(item == Items.AIR ? fallback : item);
    }

    /** Color de cada estadística. */
    static int statColor(StatKey s) {
        return switch (s) {
            case HP -> 0xFF4CD07D;              // verde
            case ATTACK -> 0xFFFF5C5C;          // rojo
            case DEFENCE -> 0xFFC9A44A;         // amarillo cafesito
            case SPECIAL_ATTACK -> 0xFFB07CFF;  // morado
            case SPECIAL_DEFENCE -> 0xFFFFE066; // amarillo
            case SPEED -> 0xFF5AA9FF;           // azul
        };
    }
}
