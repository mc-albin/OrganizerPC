package com.diosesmon.organizerpc.afk;

import com.diosesmon.organizerpc.OrganizerPCClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sabe si estás AFK en el servidor, desde que entras (no solo con la PC abierta).
 *
 * Fuentes, de más a menos fiable:
 * 1. Tu nombre en la lista de jugadores (TAB) contiene "AFK" (muchos plugins lo ponen).
 * 2. Mensajes del servidor sobre ti con "AFK" ("Ahora estás AFK", "Ya no estás AFK", "X is now AFK"...).
 *    Solo mensajes del sistema, nunca el chat de jugadores.
 * 3. El botón AFK del organizador (envía el comando y cambia el estado al momento;
 *    si el servidor responde, su respuesta manda).
 * Además mide cuánto tiempo llevas sin moverte.
 */
public final class AfkTracker {
    private static final Pattern SECOND_PERSON = Pattern.compile(
            "(est[aá]s|eres|you are|you're|you have|ya no|ahora|now|no longer)");
    private static final Pattern BACK = Pattern.compile(
            "(ya no|no longer|no est[aá]s|volv|de vuelta|regres|returned|is back|are back|not afk|desactiv|disabled)");

    private static boolean messageAfk;
    private static boolean tabAfk;
    private static long lastActivity = System.currentTimeMillis();
    private static double lx, ly, lz;
    private static float lyaw, lpitch;
    private static int ticks;

    private AfkTracker() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AfkTracker::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onSystemMessage(message));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        messageAfk = false;
        tabAfk = false;
        lastActivity = System.currentTimeMillis();
    }

    public static boolean isAfk() {
        return tabAfk || messageAfk;
    }

    /** Segundos sin moverte ni girar la cámara. */
    public static long idleSeconds() {
        return (System.currentTimeMillis() - lastActivity) / 1000;
    }

    /** Pulsar el botón AFK: envía el comando configurado (por defecto /afk). */
    public static void toggle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        String cmd = OrganizerPCClient.config().afkCommand.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) return;
        mc.getNetworkHandler().sendChatCommand(cmd);
        messageAfk = !isAfk();
        if (!messageAfk) tabAfk = false;
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (Math.abs(p.getX() - lx) > 0.01 || Math.abs(p.getY() - ly) > 0.01 || Math.abs(p.getZ() - lz) > 0.01
                || Math.abs(p.getYaw() - lyaw) > 0.5F || Math.abs(p.getPitch() - lpitch) > 0.5F) {
            lastActivity = System.currentTimeMillis();
            lx = p.getX();
            ly = p.getY();
            lz = p.getZ();
            lyaw = p.getYaw();
            lpitch = p.getPitch();
        }
        if (++ticks % 20 != 0 || mc.getNetworkHandler() == null) return;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
        Text dn = entry == null ? null : entry.getDisplayName();
        boolean nowTab = dn != null && dn.getString().toLowerCase(Locale.ROOT).contains("afk");
        if (tabAfk && !nowTab) messageAfk = false; // el servidor quitó la marca AFK
        tabAfk = nowTab;
    }

    private static void onSystemMessage(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || message == null) return;
        String s = message.getString().toLowerCase(Locale.ROOT);
        if (!s.contains("afk") || s.length() > 160) return;
        String me = mc.player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        // Solo mensajes sobre ti: con tu nombre o en segunda persona.
        if (!s.contains(me) && !SECOND_PERSON.matcher(s).find()) return;
        messageAfk = !BACK.matcher(s).find();
    }
}
