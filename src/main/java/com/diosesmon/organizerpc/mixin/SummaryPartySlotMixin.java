package com.diosesmon.organizerpc.mixin;

import com.cobblemon.mod.common.client.gui.summary.widgets.PartySlotWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.gui.PartyHudMarks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marcas de IVs en los huecos del equipo de la pantalla de datos (Summary) de Cobblemon. */
@Mixin(value = PartySlotWidget.class, remap = false)
abstract class SummaryPartySlotMixin {
    @Shadow
    @Final
    private Pokemon pokemon;

    @Inject(method = {"renderWidget", "method_48579"}, at = @At("TAIL"), require = 0)
    private void organizerpc$ivMarks(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ClickableWidget self = (ClickableWidget) (Object) this;
        PartyHudMarks.summarySlot(ctx, pokemon, self.getX(), self.getY());
    }
}
