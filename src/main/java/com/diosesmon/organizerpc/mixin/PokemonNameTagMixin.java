package com.diosesmon.organizerpc.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.diosesmon.organizerpc.gui.PartyNameplates;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Al empezar el dibujo del nombre de un Pokémon, añade debajo la línea de IVs (solo los tuyos).
 * Prioridad baja para ir antes que cobblemon_wild_extras, que cancela el nombre original.
 */
@Mixin(value = PokemonRenderer.class, remap = false, priority = 500)
abstract class PokemonNameTagMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), require = 0)
    private void organizerpc$ivLine(PokemonEntity entity, Text text, MatrixStack matrices, VertexConsumerProvider consumers,
                                    int light, float tickDelta, CallbackInfo ci) {
        PartyNameplates.drawUnderLabel(entity, matrices, consumers, light);
        PartyNameplates.drawBrazal(entity, matrices, consumers, light);
    }
}
