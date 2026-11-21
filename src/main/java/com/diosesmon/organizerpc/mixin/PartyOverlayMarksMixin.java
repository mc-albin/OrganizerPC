package com.diosesmon.organizerpc.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.diosesmon.organizerpc.gui.PartyHudMarks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marcas de IVs dentro del propio dibujo del equipo de Cobblemon. Así heredan la escala que le
 * apliquen otros mods (diosesmon-client lo reescala con x3/x4) y quedan siempre en su sitio.
 */
@Mixin(value = PartyOverlay.class, remap = false)
abstract class PartyOverlayMarksMixin {
    @Inject(method = {"render", "method_1753"}, at = @At("TAIL"), require = 0)
    private void organizerpc$ivMarks(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        PartyHudMarks.render(ctx);
    }
}
