package com.diosesmon.organizerpc;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.diosesmon.organizerpc.afk.AfkTracker;
import com.diosesmon.organizerpc.cobblemon.MoveExecutor;
import com.diosesmon.organizerpc.config.ConfigStore;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.gui.PcScreenHook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OrganizerPCClient implements ClientModInitializer {
    public static final String MOD_ID = "organizerpc";
    public static final Logger LOGGER = LoggerFactory.getLogger("OrganizerPC");

    private static OrganizerConfig config;

    public static OrganizerConfig config() {
        return config;
    }

    public static void saveConfig() {
        ConfigStore.save(config);
    }

    private static int dexOf(String id) {
        try {
            var s = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(net.minecraft.util.Identifier.of(id));
            return s == null ? Integer.MAX_VALUE : s.getNationalPokedexNumber();
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public void onInitializeClient() {
        config = ConfigStore.load();
        com.diosesmon.organizerpc.update.Updater.checkAsync();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                c -> com.diosesmon.organizerpc.update.Updater.applyOnExit());
        // Se engancha a la PC original de Cobblemon cada vez que se inicializa (abrir o redimensionar).
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof PCGUI gui) PcScreenHook.attach(gui);
        });
        ClientTickEvents.END_CLIENT_TICK.register(MoveExecutor::tick);
        AfkTracker.register();
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(com.diosesmon.organizerpc.gui.PartyNameplates::render);
        // Living dex: familia evolutiva en orden de Pokédex (de los JSON de especies de Cobblemon).
        java.util.Map<String, java.util.List<String>> famCache = new java.util.concurrent.ConcurrentHashMap<>();
        com.diosesmon.organizerpc.core.LivingDex.families = id -> famCache.computeIfAbsent(id, k -> {
            java.util.List<String> fam = new java.util.ArrayList<>(com.diosesmon.organizerpc.cobblemon.EggGroupData.family(k));
            fam.sort(java.util.Comparator.comparingInt(OrganizerPCClient::dexOf).thenComparing(s -> s));
            return java.util.List.copyOf(fam);
        });
        com.diosesmon.organizerpc.cobblemon.EggGroupData.preloadAsync();
        LOGGER.info("OrganizerPC listo ({} categorías)", config.categories.size());
    }
}
