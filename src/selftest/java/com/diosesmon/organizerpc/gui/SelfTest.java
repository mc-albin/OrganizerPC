package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.cobblemon.EggGroupData;
import com.diosesmon.organizerpc.cobblemon.MoveExecutor;
import com.diosesmon.organizerpc.cobblemon.PcReader;
import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.StatKey;
import com.diosesmon.organizerpc.core.filter.Comparison;
import com.diosesmon.organizerpc.core.filter.EggGroupCondition;
import com.diosesmon.organizerpc.core.filter.PerfectIvCountCondition;
import com.diosesmon.organizerpc.core.filter.ConditionType;
import com.diosesmon.organizerpc.core.filter.IvPatternCondition;
import com.diosesmon.organizerpc.core.plan.PcModel;
import com.diosesmon.organizerpc.core.plan.Plan;
import com.diosesmon.organizerpc.core.plan.Planner;
import com.diosesmon.organizerpc.core.plan.Pos;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Prueba de extremo a extremo dentro de Minecraft 1.21.1 + Cobblemon 1.7.3 (solo desarrollo).
 * Usa un mundo de un jugador: el cliente habla con el servidor integrado mediante los mismos
 * paquetes y handlers de red que en multijugador, y el resultado se comprueba en el PCStore
 * del servidor (la fuente de verdad que se guarda en disco).
 */
public final class SelfTest implements ClientModInitializer {
    private static final List<String> log = new ArrayList<>();
    private static int failures;
    private static int stage;
    private static int wait;
    private static int initialCount;
    private static Set<UUID> initialUuids;
    private static UUID mutatedUuid;
    private static List<UUID> manualSelection;

    @Override
    public void onInitializeClient() {
        if (System.getProperty("organizerpc.selftest") == null) return;
        ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
    }

    private static void check(boolean ok, String what) {
        log.add((ok ? "PASS " : "FAIL ") + what);
        OrganizerPCClient.LOGGER.info("[SELFTEST] {} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) failures++;
    }

    private static <T> T onServer(Supplier<T> s) {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        try {
            return server.submit(s).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ServerPlayerEntity player(MinecraftServer server) {
        return server.getPlayerManager().getPlayerList().get(0);
    }

    private static PCStore serverPc(MinecraftServer server) {
        return Cobblemon.INSTANCE.getStorage().getPC(player(server));
    }

    private static void runCommand(String cmd) {
        onServer(() -> {
            MinecraftServer server = MinecraftClient.getInstance().getServer();
            server.getCommandManager().executeWithPrefix(player(server).getCommandSource().withLevel(4), cmd);
            return null;
        });
    }

    /** PcModel del servidor con IVs y grupos huevo leídos de los datos del servidor. */
    private static PcModel serverModel() {
        return onServer(() -> {
            PCStore pc = serverPc(MinecraftClient.getInstance().getServer());
            PcModel m = new PcModel(pc.getBoxes().size());
            Stats[] stats = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
            for (int b = 0; b < pc.getBoxes().size(); b++) {
                for (int s = 0; s < 30; s++) {
                    Pokemon p = pc.get(new PCPosition(b, s));
                    if (p == null) continue;
                    int[] ivs = new int[6];
                    for (int i = 0; i < 6; i++) ivs[i] = p.getIvs().get(stats[i]);
                    m.put(new Pos(b, s), new PokemonInfo(p.getUuid(), p.getSpecies().getName(), p.getSpecies().getResourceIdentifier().toString(), 0, p.getLevel(), ivs,
                            EggGroupData.convertGroups(p.getForm().getEggGroups()), p.getShiny()));
                }
            }
            return m;
        });
    }

    private static Set<UUID> uuids(PcModel m) {
        return new HashSet<>(m.allInfos().keySet());
    }

    private static boolean samePositions(PcModel a, PcModel b) {
        if (a.boxCount() != b.boxCount()) return false;
        for (int box = 0; box < a.boxCount(); box++)
            for (int s = 0; s < 30; s++) {
                UUID x = a.at(box, s), y = b.at(box, s);
                if (x == null ? y != null : !x.equals(y)) return false;
            }
        return true;
    }

    private static Pokemon make(String species, int perfect, boolean speedZero) {
        Pokemon p = PokemonProperties.Companion.parse(species).create();
        Stats[] order = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
        int max = speedZero ? 5 : 6;
        for (int i = 0; i < 6; i++) p.setIV(order[i], 12);
        for (int i = 0; i < Math.min(perfect, max); i++) p.setIV(order[i], 31);
        if (speedZero) p.setIV(Stats.SPEED, 0);
        return p;
    }

    private static void populate() {
        onServer(() -> {
            PCStore pc = serverPc(MinecraftClient.getInstance().getServer());
            pc.clearPC();
            String[] species = {"dratini", "bulbasaur", "magikarp", "eevee", "ditto", "gible", "pikachu", "abra",
                    "geodude", "gastly", "caterpie", "pidgey", "horsea", "krabby", "mewtwo"};
            int i = 0;
            for (; i < 60; i++) {
                pc.set(new PCPosition(i / 30, i % 30), make(species[i % species.length], i % 7, i % 10 == 3));
            }
            // Caja 3 (destino de 6x31) llena de Pokémon 0x31: obliga a desalojar e intercambiar.
            for (int s = 0; s < 30; s++) pc.set(new PCPosition(2, s), make("bulbasaur", 0, false));
            // Unos cuantos en una caja destino de otra categoría.
            for (int s = 0; s < 10; s++) pc.set(new PCPosition(4, s), make(species[(s * 4) % species.length], (s * 3) % 7, s == 2));
            return null;
        });
    }

    private static Category category(String name, int... boxes) {
        Category c = new Category();
        c.name = name;
        for (int b : boxes) c.boxes.add(b);
        return c;
    }

    private static void count(Category c, Comparison op, int n) {
        PerfectIvCountCondition p = c.condition(ConditionType.PERFECT_IV_COUNT);
        p.op = op;
        p.count = n;
    }

    private static void configureCategories() {
        OrganizerConfig c = OrganizerPCClient.config();
        c.categories.clear();
        c.lockedBoxes.clear();
        c.unsortedMode = OrganizerConfig.UnsortedMode.KEEP;
        c.tutorialDone = true;
        PanelState.tutorialStep = -1;
        Category trick = category("TRICK ROOM", 9);
        IvPatternCondition speed0 = trick.condition(ConditionType.IV_PATTERN);
        speed0.set(StatKey.SPEED, IvPatternCondition.State.ZERO);
        count(trick, Comparison.GE, 3);
        Category breed = category("CRIANZA 5x31 DRAGÓN", 8);
        breed.conditions.add(new EggGroupCondition(EnumSet.of(EggGroupKey.DRAGON), false));
        count(breed, Comparison.EQ, 5);
        Category six = category("6x31", 2);
        count(six, Comparison.EQ, 6);
        Category five = category("5x31", 3);
        count(five, Comparison.EQ, 5);
        Category dragon = category("DRAGÓN", 4, 5);
        dragon.conditions.add(new EggGroupCondition(EnumSet.of(EggGroupKey.DRAGON), false));
        Category zero = category("0x31", 6, 7);
        count(zero, Comparison.EQ, 0);
        c.categories.addAll(List.of(trick, breed, six, five, dragon, zero));
        OrganizerPCClient.saveConfig();
    }

    /** Comprueba en el servidor que cada Pokémon está donde dictan las categorías y prioridades. */
    private static void verifyOrganized(PcModel server, Plan plan) {
        List<Category> cats = OrganizerPCClient.config().categories;
        Set<UUID> noSpace = new HashSet<>();
        for (PokemonInfo p : plan.noSpace()) noSpace.add(p.uuid());
        int wrong = 0;
        for (PokemonInfo p : server.allInfos().values()) {
            if (noSpace.contains(p.uuid())) continue;
            List<Category> m = Planner.matching(p, cats, server.boxCount(), OrganizerPCClient.config().lockedBoxes);
            int box = server.positionOf(p.uuid()).box();
            if (!m.isEmpty()) {
                if (!m.get(0).ownsBox(box)) {
                    wrong++;
                    if (wrong < 5) log.add("  mal: " + p.name() + " " + p.perfectIvCount() + "x31 en C" + (box + 1) + " debía " + m.get(0).name);
                }
            } else {
                for (Category c : cats) if (c.ownsBox(box)) {
                    wrong++;
                    if (wrong < 5) log.add("  sin categoría en caja de " + c.name + ": " + p.name());
                }
            }
        }
        check(wrong == 0, "Cada Pokémon está en la caja de su categoría de mayor prioridad (errores: " + wrong + ")");
    }

    private static void verifyIntegrity(String when) {
        PcModel s = serverModel();
        check(s.allInfos().size() == initialCount, when + ": mismo número de Pokémon en el servidor (" + s.allInfos().size() + "/" + initialCount + ")");
        check(uuids(s).equals(initialUuids), when + ": mismos UUID en el servidor (sin pérdidas ni duplicados)");
        PcModel client = PcReader.read(((PCGUI) MinecraftClient.getInstance().currentScreen).getPc());
        check(samePositions(s, client), when + ": la PC del cliente coincide con el servidor");
    }

    private static void finish() {
        log.add(failures == 0 ? "RESULTADO: TODO OK" : "RESULTADO: " + failures + " FALLOS");
        try {
            Files.write(Path.of("organizerpc-selftest.txt"), log);
        } catch (Exception ignored) {
        }
        OrganizerPCClient.LOGGER.info("[SELFTEST] {}", failures == 0 ? "TODO OK" : failures + " FALLOS");
        MinecraftClient.getInstance().scheduleStop();
        stage = 999;
    }

    private static boolean pcOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof PCGUI && PcScreenHook.current != null;
    }

    private static void tick(MinecraftClient client) {
        if (stage == 999) return;
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            step(client);
        } catch (Throwable t) {
            OrganizerPCClient.LOGGER.error("[SELFTEST] excepción", t);
            check(false, "Excepción: " + t);
            finish();
        }
    }

    private static void step(MinecraftClient client) {
        switch (stage) {
            case 0 -> {
                if (client.player == null || client.getServer() == null) return;
                wait = 60;
                stage = 1;
            }
            case 1 -> {
                // Grupos huevo empaquetados (ruta de servidor dedicado) frente a los datos reales del servidor.
                int total = onServer(() -> PokemonSpecies.getSpecies().size());
                List<String> mismatches = onServer(() -> {
                    List<String> out = new ArrayList<>();
                    for (Species sp : PokemonSpecies.getSpecies()) {
                        List<FormData> forms = new ArrayList<>(sp.getForms());
                        forms.add(sp.getStandardForm());
                        for (FormData f : forms) {
                            Set<EggGroupKey> live = EggGroupData.convertGroups(f.getEggGroups());
                            Set<EggGroupKey> bundled = EggGroupData.fromBundledData(sp.getResourceIdentifier().toString(), sp.getName(), f.getName());
                            if (bundled == null || !bundled.equals(live)) out.add(sp.getName() + "/" + f.getName() + " live=" + live + " json=" + bundled);
                        }
                    }
                    return out;
                });
                check(mismatches.isEmpty(), "Grupos huevo de los JSON = datos del servidor para " + total + " especies (difieren: " + mismatches.size() + ")");
                for (int i = 0; i < Math.min(8, mismatches.size()); i++) log.add("  " + mismatches.get(i));

                populate();
                PcModel s = serverModel();
                initialCount = s.allInfos().size();
                initialUuids = uuids(s);
                check(initialCount == 100, "PC de prueba creada con 100 Pokémon");
                configureCategories();
                runCommand("pc");
                stage = 2;
            }
            case 2 -> {
                if (!pcOpen()) return;
                wait = 20;
                stage = 3;
            }
            case 3 -> {
                OrganizerPanel panel = PcScreenHook.current;
                verifyIntegrity("Al abrir");
                // IVs reales en el cliente = servidor.
                PcModel s = serverModel();
                PcModel c = PcReader.read(panel.pc());
                int ivDiff = 0, eggDiff = 0;
                for (PokemonInfo si : s.allInfos().values()) {
                    PokemonInfo ci = c.info(si.uuid());
                    if (!java.util.Arrays.equals(si.ivs(), ci.ivs())) ivDiff++;
                    if (!si.eggGroups().equals(ci.eggGroups())) eggDiff++;
                }
                check(ivDiff == 0, "IVs leídos en el cliente = IVs del servidor (difieren " + ivDiff + ")");
                check(eggDiff == 0, "Grupos huevo en el cliente = servidor (difieren " + eggDiff + ")");

                PanelState.preview = null;
                PanelState.overrides.clear();
                panel.startOrganize(PanelState.PreviewKind.ORGANIZE_ALL);
                check(PanelState.preview != null && !PanelState.preview.isEmpty(), "ORGANIZAR PC genera una vista previa con cambios");
                log.add("  vista previa: " + PanelState.preview.movedCount() + " Pokémon, " + PanelState.preview.operations().size()
                        + " pasos, sin espacio " + PanelState.preview.noSpace().size());
                PcModel before = serverModel();
                check(samePositions(before, s), "La vista previa no mueve nada por sí sola");
                wait = 10;
                stage = 30;
            }
            case 30 -> {
                PcScreenHook.current.goToBox(PcScreenHook.current.nextChangedBox(PcScreenHook.current.currentBox(), 1));
                wait = 10;
                stage = 31;
            }
            case 31 -> {
                check(PcScreenHook.renderErrors == 0, "La vista previa (Antes/Después) se dibuja sin errores");
                check(samePositions(serverModel(), PcReader.read(PcScreenHook.current.pc())), "Ver la vista previa no cambia la PC");
                PcScreenHook.current.confirmPreview();
                check(MoveExecutor.isRunning(), "Confirmar inicia la ejecución");
                stage = 4;
            }
            case 4 -> {
                if (MoveExecutor.isRunning()) return;
                MoveExecutor ex = MoveExecutor.current();
                check(ex.status() == MoveExecutor.Status.DONE, "Organización completada: " + ex.message());
                wait = 10;
                stage = 5;
            }
            case 5 -> {
                verifyIntegrity("Tras organizar");
                Plan replay = Planner.organize(new Planner.Request(serverModel(), OrganizerPCClient.config().categories,
                        allBoxes(), OrganizerPCClient.config().lockedBoxes, OrganizerConfig.UnsortedMode.KEEP, 0, java.util.Map.of()));
                verifyOrganized(serverModel(), replay);
                check(replay.isEmpty(), "Organizar otra vez no genera movimientos (idempotente)");
                // Cerrar y reabrir la PC.
                client.setScreen(null);
                wait = 10;
                stage = 6;
            }
            case 6 -> {
                runCommand("pc");
                stage = 7;
            }
            case 7 -> {
                if (!pcOpen()) return;
                wait = 20;
                stage = 8;
            }
            case 8 -> {
                verifyIntegrity("Tras cerrar y reabrir la PC");
                // Movimiento manual de varios Pokémon a una caja vacía.
                PcModel s = serverModel();
                manualSelection = new ArrayList<>();
                for (int b = 0; b < s.boxCount() && manualSelection.size() < 4; b++)
                    for (int sl = 0; sl < 30 && manualSelection.size() < 4; sl++)
                        if (s.at(b, sl) != null) manualSelection.add(s.at(b, sl));
                PanelState.selection.clear();
                PanelState.selection.addAll(manualSelection);
                PcScreenHook.current.openMoveWindow();
                PanelState.moveBoxes.clear();
                PanelState.moveBoxes.add(11);
                PanelState.moveDest = PanelState.MoveDest.BOX;
                PcScreenHook.current.moveMarked();
                check(PanelState.preview != null && PanelState.preview.movedCount() == 4, "Vista previa de mover 4 marcados");
                PcScreenHook.current.confirmPreview();
                stage = 9;
            }
            case 9 -> {
                if (MoveExecutor.isRunning()) return;
                check(MoveExecutor.current().status() == MoveExecutor.Status.DONE, "Movimiento manual completado");
                PcModel s = serverModel();
                boolean all = true;
                for (UUID u : manualSelection) all &= s.positionOf(u).box() == 11;
                check(all, "Los 4 marcados están en C12 en el servidor");
                check(PanelState.selection.isEmpty(), "La selección se limpia tras mover");
                verifyIntegrity("Tras mover manualmente");

                // Vista previa obsoleta: cambiar la PC entre la vista previa y la confirmación.
                setBoxes(5, 13);
                PcScreenHook.current.startOrganize(PanelState.PreviewKind.ORGANIZE_ALL);
                Plan p = PanelState.preview;
                check(p != null && !p.isEmpty(), "Nueva vista previa tras cambiar la caja de 0x31");
                mutatedUuid = p.entries().get(0).uuid();
                onServer(() -> {
                    PCStore pc = serverPc(MinecraftClient.getInstance().getServer());
                    Pokemon mon = pc.get(new PCPosition(p.entries().get(0).from().box(), p.entries().get(0).from().slot()));
                    pc.move(mon, new PCPosition(20, 0));
                    return null;
                });
                wait = 10;
                stage = 10;
            }
            case 10 -> {
                Plan old = PanelState.preview;
                PcScreenHook.current.confirmPreview();
                check(!MoveExecutor.isRunning() && PanelState.preview != null && PanelState.preview != old,
                        "Si la PC cambia tras la vista previa, no se ejecuta y se muestra una nueva");
                PcScreenHook.current.confirmPreview();
                check(MoveExecutor.isRunning(), "Confirmar la vista previa actualizada sí ejecuta");
                stage = 11;
            }
            case 11 -> {
                if (MoveExecutor.isRunning()) return;
                check(MoveExecutor.current().status() == MoveExecutor.Status.DONE, "Ejecución tras vista previa actualizada");
                verifyIntegrity("Tras vista previa actualizada");

                // Cambio en mitad de la ejecución: debe detenerse sin pérdidas.
                setBoxes(5, 15);
                PcScreenHook.current.startOrganize(PanelState.PreviewKind.ORGANIZE_ALL);
                Plan p = PanelState.preview;
                Plan.Entry last = p.entries().get(p.entries().size() - 1);
                mutatedUuid = last.uuid();
                PcScreenHook.current.confirmPreview();
                stage = 12;
            }
            case 12 -> {
                MoveExecutor ex = MoveExecutor.current();
                if (ex.status() == MoveExecutor.Status.RUNNING && ex.done() < 2) return;
                if (ex.status() == MoveExecutor.Status.RUNNING) {
                    onServer(() -> {
                        PCStore pc = serverPc(MinecraftClient.getInstance().getServer());
                        for (int b = 0; b < pc.getBoxes().size(); b++)
                            for (int s = 0; s < 30; s++) {
                                Pokemon mon = pc.get(new PCPosition(b, s));
                                if (mon != null && mon.getUuid().equals(mutatedUuid)) {
                                    pc.move(mon, new PCPosition(21, 0));
                                    return null;
                                }
                            }
                        return null;
                    });
                }
                stage = 13;
            }
            case 13 -> {
                if (MoveExecutor.isRunning()) return;
                MoveExecutor ex = MoveExecutor.current();
                check(ex.status() == MoveExecutor.Status.FAILED, "Cambio a mitad: se detiene (" + ex.message() + ")");
                wait = 10;
                stage = 14;
            }
            case 14 -> {
                verifyIntegrity("Tras detener por cambio a mitad");

                // Detener manualmente.
                OrganizerPCClient.config().lockedBoxes.add(13);
                lockedBefore = boxContents(13);
                setBoxes(5, 17);
                PcScreenHook.current.startOrganize(PanelState.PreviewKind.ORGANIZE_ALL);
                boolean touches = false;
                for (var op : PanelState.preview.operations()) touches |= op.from().box() == 13 || op.to().box() == 13;
                check(!touches, "El plan no toca la caja bloqueada 13");
                PcScreenHook.current.confirmPreview();
                stage = 15;
            }
            case 15 -> {
                MoveExecutor ex = MoveExecutor.current();
                if (ex.status() == MoveExecutor.Status.RUNNING && ex.done() < 3) return;
                ex.requestStop();
                stage = 16;
            }
            case 16 -> {
                if (MoveExecutor.isRunning()) return;
                MoveExecutor ex = MoveExecutor.current();
                check(ex.status() == MoveExecutor.Status.STOPPED || ex.status() == MoveExecutor.Status.DONE,
                        "Botón Detener: " + ex.message());
                wait = 10;
                stage = 17;
            }
            case 17 -> {
                verifyIntegrity("Tras detener manualmente");
                check(boxContents(13).equals(lockedBefore), "La caja bloqueada no cambió en el servidor");
                // Renombrar cajas reales.
                PcScreenHook.current.renameBoxes();
                wait = 20;
                stage = 18;
            }
            case 18 -> {
                String n2 = onServer(() -> serverPc(MinecraftClient.getInstance().getServer()).getBoxes().get(2).getName());
                String n9 = onServer(() -> serverPc(MinecraftClient.getInstance().getServer()).getBoxes().get(8).getName());
                String n5 = onServer(() -> serverPc(MinecraftClient.getInstance().getServer()).getBoxes().get(5).getName());
                check("6x31".equals(n2), "Caja 3 renombrada en el servidor: '" + n2 + "'");
                check("CRIANZA 5x31 DRAGÓN".substring(0, 19).equals(n9), "Nombre largo recortado a 19: '" + n9 + "'");
                check("DRAGÓN 2".equals(n5), "Caja de desbordamiento numerada: '" + n5 + "'");
                String clientName = PcScreenHook.current.boxName(2);
                check("6x31".equals(clientName), "La PC del cliente muestra el nuevo nombre: '" + clientName + "'");

                // Recorrer todas las vistas del panel (dibujo y clic) buscando excepciones.
                PcScreenHook.renderErrors = 0;
                PanelState.preview = null;
                stage = 19;
                wait = 5;
            }
            case 19 -> {
                OrganizerPanel panel = PcScreenHook.current;
                clickSide();
                stage = 20;
                wait = 5;
            }
            case 20 -> {
                for (Category c : OrganizerPCClient.config().categories) {
                    PanelState.editing = c;
                    clickSide();
                }
                PanelState.editing = OrganizerPCClient.config().categories.get(1);
                stage = 21;
                wait = 5;
            }
            case 21 -> {
                PcScreenHook.current.finishEditing();
                PcScreenHook.current.openMoveWindow();
                stage = 22;
                wait = 5;
            }
            case 22 -> {
                PcScreenHook.current.closeMoveWindow();
                HiddenButton.hide(PcScreenHook.current);
                PanelState.tutorialStep = 0;
                stage = 23;
                wait = 5;
            }
            case 23 -> {
                OrganizerPCClient.config().hidden = false;
                PanelState.tutorialStep = -1;
                check(PcScreenHook.renderErrors == 0, "Todas las vistas del panel se dibujan sin errores (" + PcScreenHook.renderErrors + ")");
                verifyIntegrity("Final");
                finish();
            }
            default -> {
            }
        }
    }

    private static List<UUID> lockedBefore;

    private static void setBoxes(int category, int box) {
        Category c = OrganizerPCClient.config().categories.get(category);
        c.boxes.clear();
        c.boxes.add(box);
    }

    private static List<UUID> boxContents(int box) {
        PcModel m = serverModel();
        List<UUID> out = new ArrayList<>();
        for (int s = 0; s < 30; s++) out.add(m.at(box, s));
        return out;
    }

    /** Click en la zona de estado del panel derecho: recorre el código de clic sin pulsar botones. */
    private static void clickSide() {
        OrganizerPanel p = PcScreenHook.current;
        p.computeGeometry();
        p.mouseClicked(p.sx + 2, p.sy + p.sh - 3, 0);
        p.mouseClicked(p.tbX + p.tbW - 40, p.tbY + 5, 0);
    }

    private static Set<Integer> allBoxes() {
        Set<Integer> s = new HashSet<>();
        int n = onServer(() -> serverPc(MinecraftClient.getInstance().getServer()).getBoxes().size());
        for (int i = 0; i < n; i++) s.add(i);
        return s;
    }
}
