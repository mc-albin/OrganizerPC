package com.diosesmon.organizerpc.cobblemon;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.core.WikiPairs;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enlace con la wiki de crianza (solo con WikiDiosesmonViewer):
 * <ul>
 *   <li>Lee {@code organizerpc/parejas.json} (lo escribe el visor cuando la página elige parejas) cuando cambia.</li>
 *   <li>Vigila el equipo aunque la PC esté cerrada (huevos que nacen) y reescribe {@code breeders.txt}.</li>
 * </ul>
 */
public final class WikiSync {
    private static final long MAX_BYTES = 1024 * 1024;
    private static WikiPairs pairs = WikiPairs.EMPTY;
    private static long pairsMtime = -1;
    private static long partySig;
    private static int ticks;

    private WikiSync() {
    }

    public static WikiPairs pairs() {
        return pairs;
    }

    public static Path pairsFile() {
        return BreederExport.file().resolveSibling("parejas.json");
    }

    public static void tick(MinecraftClient mc) {
        if (++ticks % 40 != 0 || !BreederExport.viewerPresent()) return;
        reloadPairs();
        if (mc.player == null) return;
        long sig = partySignature();
        if (sig != partySig) {
            partySig = sig;
            BreederExport.autoSyncParty();
        }
    }

    private static void reloadPairs() {
        Path f = pairsFile();
        try {
            if (!Files.isRegularFile(f)) {
                if (pairsMtime != -1) {
                    pairs = WikiPairs.EMPTY;
                    pairsMtime = -1;
                }
                return;
            }
            long m = Files.getLastModifiedTime(f).toMillis();
            if (m == pairsMtime || Files.size(f) > MAX_BYTES) return;
            pairsMtime = m;
            pairs = WikiPairs.parse(Files.readString(f, StandardCharsets.UTF_8));
            BreederExport.markDirty();
        } catch (Exception e) {
            OrganizerPCClient.LOGGER.warn("No se pudo leer parejas.json", e);
        }
    }

    /** UUID y especie de cada hueco del equipo: cambia cuando entra, sale o nace un Pokémon. */
    private static long partySignature() {
        long sig = 7;
        try {
            for (Pokemon p : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
                sig = sig * 31 + (p == null ? 0 : p.getUuid().hashCode() * 17L + p.getSpecies().getResourceIdentifier().hashCode());
            }
        } catch (Exception e) {
            return partySig;
        }
        return sig;
    }
}
