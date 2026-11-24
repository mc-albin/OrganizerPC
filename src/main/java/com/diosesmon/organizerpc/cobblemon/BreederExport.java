package com.diosesmon.organizerpc.cobblemon;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.core.Breeders;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.plan.PcModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Escribe los breeders de la PC y del equipo en {@code organizerpc/breeders.txt} (carpeta del juego).
 * Lo hace el botón «Descargar .txt» del botón Crianza (junto a AFK) y, solo si está WikiDiosesmonViewer,
 * también cada vez que cambia la PC abierta (la wiki del juego lo lee de ahí).
 */
public final class BreederExport {
    public static final String VIEWER_MOD_ID = "wikidiosesmonviewer";
    private static String lastBody;
    private static boolean dirty;

    private BreederExport() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("organizerpc").resolve("breeders.txt");
    }

    public static boolean viewerPresent() {
        return FabricLoader.getInstance().isModLoaded(VIEWER_MOD_ID);
    }

    /** Última PC leída: con la PC cerrada, el equipo se sigue vigilando y el archivo se reescribe con ella. */
    private static ClientPC lastPc;

    /** Solo los breeders (para contarlos). */
    public static List<Breeders.Entry> collect(ClientPC pc) {
        List<Breeders.Entry> out = new ArrayList<>();
        for (Breeders.Entry e : collectAll(pc)) if (e.breeder()) out.add(e);
        return out;
    }

    /**
     * Todos los Pokémon de la PC (sin cajas bloqueadas) y del equipo, sin parejas manuales ni «No usar»:
     * los breeders con «B» y el resto con «S» (la wiki los usa para ver qué crías nacieron).
     */
    public static List<Breeders.Entry> collectAll(ClientPC pc) {
        OrganizerConfig cfg = OrganizerPCClient.config();
        List<Breeders.Entry> out = new ArrayList<>();
        var party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
        for (int i = 0; i < party.size(); i++) add(out, cfg, party.get(i), "Equipo #" + (i + 1));
        if (pc != null) {
            List<ClientBox> boxes = pc.getBoxes();
            for (int b = 0; b < boxes.size(); b++) {
                if (cfg.lockedBoxes.contains(b)) continue;
                List<Pokemon> slots = boxes.get(b).getSlots();
                for (int s = 0; s < Math.min(PcModel.SLOTS_PER_BOX, slots.size()); s++) {
                    add(out, cfg, slots.get(s), "Caja " + (b + 1) + " #" + (s + 1));
                }
            }
        }
        return out;
    }

    private static void add(List<Breeders.Entry> out, OrganizerConfig cfg, Pokemon p, String place) {
        if (p == null) return;
        if (cfg.noWiki.contains(p.getUuid()) || cfg.pairOf(p.getUuid()) != null) return;
        PokemonInfo info = PcReader.info(p);
        // Muy raros (iniciales, fósiles…): con el filtro puesto no son breeders (siguen en la lista para ver crías)
        boolean breeder = Breeders.isBreeder(info, cfg.breedDitto)
                && !(cfg.breedNoUltra && com.diosesmon.organizerpc.core.UltraRare.is(info.dex()));
        String species = p.getSpecies().getTranslatedName().getString();
        try {
            String form = p.getForm().getName();
            if (form != null && !form.isBlank() && !form.equalsIgnoreCase("normal")) species += " (" + form + ")";
        } catch (Exception ignored) {
            // Sin forma: solo la especie.
        }
        out.add(new Breeders.Entry(info.dex(), species, info.gender(), info.eggGroups(), info.ivs(), place, info.uuid(), breeder));
    }

    /** Escribe el archivo y devuelve cuántos breeders tiene. */
    public static int write(ClientPC pc) throws IOException {
        if (pc != null) lastPc = pc;
        List<Breeders.Entry> all = collectAll(pc);
        write(all);
        return (int) all.stream().filter(Breeders.Entry::breeder).count();
    }

    /** Lo que cambia de verdad (sin fecha ni jugador): si es igual, no hace falta reescribir. */
    private static String body(List<Breeders.Entry> entries) {
        return Breeders.toText(entries, List.of(dittoLine()));
    }

    private static String dittoLine() {
        return "Ditto: " + (OrganizerPCClient.config().breedDitto ? "sí" : "no") + " · muy raros: " + (OrganizerPCClient.config().breedNoUltra ? "no" : "sí");
    }

    private static int write(List<Breeders.Entry> entries) throws IOException {
        List<String> meta = new ArrayList<>();
        meta.add("fecha: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        meta.add("jugador: " + MinecraftClient.getInstance().getSession().getUsername());
        meta.add(dittoLine());
        String text = Breeders.toText(entries, meta);
        Path f = file();
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling("breeders.txt.tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        lastBody = body(entries);
        return entries.size();
    }

    /** Con WikiDiosesmonViewer: reescribe el archivo si cambiaron los breeders. Sin él no hace nada. */
    public static void autoSync(ClientPC pc) {
        if (pc == null || !viewerPresent()) return;
        lastPc = pc;
        try {
            List<Breeders.Entry> entries = collectAll(pc);
            if (body(entries).equals(lastBody) && Files.exists(file())) return;
            write(entries);
        } catch (Exception e) {
            OrganizerPCClient.LOGGER.warn("No se pudo escribir breeders.txt", e);
        }
    }

    /** Cambió el equipo con la PC cerrada (huevo que nace, Pokémon que sale): reescribe con la última PC leída. */
    public static void autoSyncParty() {
        if (lastPc != null) autoSync(lastPc);
    }

    /** Algo cambió fuera de la PC (marca «No usar», pareja, Ditto): la próxima vuelta reescribe. */
    public static void markDirty() {
        dirty = true;
    }

    public static boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** Abre la carpeta del archivo en el explorador. */
    public static void openFolder() {
        try {
            net.minecraft.util.Util.getOperatingSystem().open(file().getParent().toFile());
        } catch (Exception e) {
            OrganizerPCClient.LOGGER.warn("No se pudo abrir la carpeta de breeders", e);
        }
    }
}
