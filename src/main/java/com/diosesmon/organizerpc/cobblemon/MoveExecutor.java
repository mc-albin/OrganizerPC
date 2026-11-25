package com.diosesmon.organizerpc.cobblemon;

import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.RequestRenamePCBoxPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.SwapPCPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.core.plan.Operation;
import com.diosesmon.organizerpc.core.plan.Pos;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.UUID;

/**
 * Ejecuta un plan usando solo los paquetes oficiales de Cobblemon (los mismos que envía la PC
 * al arrastrar un Pokémon). Envía una operación, espera a que el servidor confirme el cambio
 * en la PC sincronizada y solo entonces envía la siguiente.
 *
 * Antes de cada paso comprueba que la PC sigue como se planificó; si algo cambió (otro movimiento,
 * lag, el servidor rechazó un paso) se detiene sin enviar nada más. El servidor además valida
 * UUID y posiciones, así que un paso obsoleto nunca puede duplicar ni borrar un Pokémon.
 */
public final class MoveExecutor {
    public enum Status { IDLE, RUNNING, DONE, STOPPED, FAILED }

    private static final int CONFIRM_TIMEOUT_TICKS = 100;
    /** Pausa entre movimientos (ms): ritmo de una persona rápida, sin chocar con límites de mensajes por segundo. */
    private static final long PAUSE_MS = 200L;

    private static MoveExecutor current;

    private final ClientPC pc;
    private final List<Operation> ops;
    private final Runnable onFinish;
    private int index;
    private boolean waiting;
    private int waitTicks;
    private long readyAt;
    private boolean stopRequested;
    private Status status = Status.RUNNING;
    private String message = "";

    private MoveExecutor(ClientPC pc, List<Operation> ops, Runnable onFinish) {
        this.pc = pc;
        this.ops = ops;
        this.onFinish = onFinish;
    }

    public static MoveExecutor start(ClientPC pc, List<Operation> ops, Runnable onFinish) {
        if (isRunning()) throw new IllegalStateException("Ya hay una organización en curso");
        current = new MoveExecutor(pc, ops, onFinish);
        return current;
    }

    public static MoveExecutor current() {
        return current;
    }

    public static boolean isRunning() {
        return current != null && current.status == Status.RUNNING;
    }

    public void requestStop() {
        stopRequested = true;
    }

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    public int done() {
        return index;
    }

    public int total() {
        return ops.size();
    }

    public static void tick(MinecraftClient client) {
        MoveExecutor ex = current;
        if (ex == null || ex.status != Status.RUNNING) return;
        ex.step(client);
    }

    private void step(MinecraftClient client) {
        if (!(client.currentScreen instanceof PCGUI gui) || gui.getPc() != pc) {
            finish(Status.STOPPED, "PC cerrada: detenido en " + index + "/" + ops.size());
            return;
        }
        if (waiting) {
            Operation op = ops.get(index);
            if (confirmed(op)) {
                waiting = false;
                index++;
                readyAt = System.currentTimeMillis() + PAUSE_MS;
            } else if (++waitTicks > CONFIRM_TIMEOUT_TICKS) {
                finish(Status.FAILED, "El servidor no confirmó el paso " + (index + 1) + ". Detenido.");
            }
            return;
        }
        if (stopRequested) {
            finish(Status.STOPPED, "Detenido por el usuario en " + index + "/" + ops.size());
            return;
        }
        if (index < ops.size() && System.currentTimeMillis() < readyAt) return;   // pausa entre movimientos
        if (index >= ops.size()) {
            finish(Status.DONE, "Listo: " + ops.size() + " pasos confirmados por el servidor");
            return;
        }
        Operation op = ops.get(index);
        if (!preconditionHolds(op)) {
            finish(Status.FAILED, "La PC cambió durante la organización (paso " + (index + 1) + "). Detenido sin cambios extra.");
            return;
        }
        send(op);
        waiting = true;
        waitTicks = 0;
    }

    private void finish(Status s, String msg) {
        status = s;
        message = msg;
        OrganizerPCClient.LOGGER.info("[Organizador] {}", msg);
        if (onFinish != null) onFinish.run();
    }

    private UUID uuidAt(Pos p) {
        if (p.box() < 0 || p.box() >= pc.getBoxes().size()) return null;
        Pokemon mon = pc.get(new PCPosition(p.box(), p.slot()));
        return mon == null ? null : mon.getUuid();
    }

    private boolean preconditionHolds(Operation op) {
        if (!op.pokemon().equals(uuidAt(op.from()))) return false;
        if (op.type() == Operation.Type.MOVE) return uuidAt(op.to()) == null;
        return op.other() != null && op.other().equals(uuidAt(op.to()));
    }

    private boolean confirmed(Operation op) {
        if (!op.pokemon().equals(uuidAt(op.to()))) return false;
        if (op.type() == Operation.Type.SWAP) return op.other().equals(uuidAt(op.from()));
        return uuidAt(op.from()) == null;
    }

    private static PCPosition pc(Pos p) {
        return new PCPosition(p.box(), p.slot());
    }

    private void send(Operation op) {
        if (op.type() == Operation.Type.MOVE) {
            new MovePCPokemonPacket(op.pokemon(), pc(op.from()), pc(op.to())).sendToServer();
        } else {
            new SwapPCPokemonPacket(op.pokemon(), pc(op.from()), op.other(), pc(op.to())).sendToServer();
        }
    }

    /** Renombra una caja real con el paquete oficial. El servidor limita el nombre a 19 caracteres. */
    public static void renameBox(ClientPC pc, int box, String name) {
        String n = name.length() > 19 ? name.substring(0, 19) : name;
        new RequestRenamePCBoxPacket(pc.getUuid(), box, n).sendToServer();
    }
}
