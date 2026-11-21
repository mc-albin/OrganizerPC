package com.diosesmon.organizerpc.mixin;

import com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot;
import com.cobblemon.mod.common.client.gui.pc.StorageWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

/** Acceso de solo lectura a los espacios de caja que dibuja la PC original (para resaltar y seleccionar). */
@Mixin(value = StorageWidget.class, remap = false)
public interface StorageWidgetAccessor {
    @Accessor("boxSlots")
    ArrayList<BoxStorageSlot> organizerpc$getBoxSlots();

    @Accessor("partySlots")
    ArrayList<com.cobblemon.mod.common.client.gui.pc.PartyStorageSlot> organizerpc$getPartySlots();
}
