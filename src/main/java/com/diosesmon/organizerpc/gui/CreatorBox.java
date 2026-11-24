package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Foto del creador en la esquina de abajo a la izquierda de la PC, con un punto rojo que se enciende cuando está
 * en vivo en Twitch. Mide siempre los mismos píxeles de pantalla (igual en x1, x2, x3 y x4).
 * Al pasar el ratón: «Creador del mod iAlbin73» y si está en vivo. Click: abre su canal.
 */
final class CreatorBox {
    static final String CHANNEL = "ialbin73";
    private static final Identifier LOGO = Identifier.of("organizerpc", "textures/gui/creator.png");
    /** Lado del recuadro en píxeles de pantalla. */
    private static final int PX = 34;
    private static final long EVERY_MS = 120_000L;
    private static volatile boolean live, checking;
    private static volatile long checkedAt;
    private static HttpClient http;

    private CreatorBox() {
    }

    private static double gs() {
        return MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    /** {x, y, lado} en coordenadas de la PC (GUI de Minecraft): pegado a la esquina de abajo a la izquierda. */
    private static float[] real(OrganizerPanel p) {
        float side = (float) (PX / gs());
        float x = (p.gui.width - PCGUI.BASE_WIDTH) / 2F;
        float y = (p.gui.height - PCGUI.BASE_HEIGHT) / 2F + PCGUI.BASE_HEIGHT + 2;
        return new float[]{x, y, side};
    }

    /** Lo mismo en coordenadas del organizador (para el ratón y el tooltip). */
    static int[] rect(OrganizerPanel p) {
        float[] r = real(p);
        return new int[]{(int) Math.floor(r[0] / p.k), (int) Math.floor(r[1] / p.k),
                Math.max(1, Math.round(r[2] / p.k)), Math.max(1, Math.round(r[2] / p.k))};
    }

    static boolean visible(OrganizerPanel p) {
        if (PanelState.moveOpen || PanelState.preview != null) return false;
        float[] r = real(p);
        return r[1] + r[2] <= p.gui.height;
    }

    static boolean in(OrganizerPanel p, double mx, double my) {
        if (!visible(p)) return false;
        int[] r = rect(p);
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    static void layout(OrganizerPanel p, Ui ui) {
        if (!visible(p)) return;
        int[] v = rect(p);
        if (ui.drawing()) {
            poll();
            draw(p, ui, ui.hover(v[0], v[1], v[2], v[3]));
        }
        ui.tooltipText(v[0], v[1], v[2], v[3], tip());
        if (ui.area(v[0], v[1], v[2], v[3])) {
            try {
                net.minecraft.util.Util.getOperatingSystem().open(URI.create("https://www.twitch.tv/" + CHANNEL));
            } catch (Exception ignored) {
                // Sin navegador: no pasa nada.
            }
        }
    }

    private static final int PURPLE = 0xB98CFF, RED = 0xFF4D4D;

    /** Tooltip con colores: el nombre en degradado verde → morado, el estado en rojo o gris y el canal en morado Twitch. */
    private static java.util.List<net.minecraft.text.Text> tip() {
        var name = net.minecraft.text.Text.empty();
        String n = "iAlbin73";
        int[] from = {0x5DFF8A}, to = {PURPLE};
        for (int i = 0; i < n.length(); i++) {
            float t = i / (float) (n.length() - 1);
            name.append(net.minecraft.text.Text.literal(String.valueOf(n.charAt(i)))
                    .setStyle(net.minecraft.text.Style.EMPTY.withBold(true).withColor(lerp(from[0], to[0], t))));
        }
        java.util.List<net.minecraft.text.Text> l = new java.util.ArrayList<>();
        l.add(net.minecraft.text.Text.literal("Creador del mod ").withColor(0xE6E8EC).append(name));
        l.add(live ? net.minecraft.text.Text.literal("● EN VIVO en Twitch").setStyle(net.minecraft.text.Style.EMPTY.withBold(true).withColor(RED))
                : net.minecraft.text.Text.literal("● No está en vivo ahora").withColor(0x8A8F98));
        l.add(net.minecraft.text.Text.empty());
        l.add(net.minecraft.text.Text.literal("twitch.tv/" + CHANNEL).withColor(PURPLE));
        l.add(net.minecraft.text.Text.literal("Click para abrir su canal").withColor(0x8A8F98));
        return l;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Dibuja en píxeles de pantalla: deshace la escala del organizador y la de la GUI. */
    private static void draw(OrganizerPanel p, Ui ui, boolean hov) {
        float[] r = real(p);
        var m = ui.ctx.getMatrices();
        m.push();
        m.scale(1F / p.k, 1F / p.k, 1F);
        m.translate(r[0], r[1], 0F);
        float inv = (float) (1.0 / gs());
        m.scale(inv, inv, 1F);
        int border = live ? 0xFFFF3B3B : hov ? 0xFFFFFFFF : 0xFF9146FF;
        ui.ctx.fill(0, 0, PX, PX, border);
        ui.ctx.fill(1, 1, PX - 1, PX - 1, 0xFF101215);
        ui.ctx.drawTexture(LOGO, 2, 2, 0, 0, PX - 4, PX - 4, PX - 4, PX - 4);
        // Punto de directo en la esquina: rojo encendido (con brillo que late) en vivo, apagado si no
        int d = 8, dx = PX - d - 2, dy = 2;
        if (live) {
            float a = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 300.0);
            ui.ctx.fill(dx - 2, dy - 2, dx + d + 2, dy + d + 2, Ui.alpha(0xFFFF3B3B, (int) (0x40 + 0x60 * a)));
        }
        ui.ctx.fill(dx - 1, dy - 1, dx + d + 1, dy + d + 1, 0xFF101215);
        ui.ctx.fill(dx, dy, dx + d, dy + d, live ? 0xFFFF2A2A : 0xFF5A2A2E);
        if (live) ui.ctx.fill(dx + 1, dy + 1, dx + 3, dy + 3, 0xFFFFB0B0);
        m.pop();
    }

    /** Pregunta cada 2 minutos (solo mientras se ve) si el canal está en directo. */
    private static void poll() {
        long now = System.currentTimeMillis();
        if (checking || now - checkedAt < EVERY_MS) return;
        checking = true;
        checkedAt = now;
        try {
            if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://decapi.me/twitch/uptime/" + CHANNEL))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "OrganizerPC").GET().build();
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
                if (err == null && res.statusCode() == 200) {
                    String b = res.body().toLowerCase(Locale.ROOT);
                    // «ialbin73 is offline» o un error: no está en vivo. Si no, trae el tiempo en directo.
                    live = !b.contains("offline") && !b.contains("error") && !b.contains("not found") && !b.isBlank();
                }
                checking = false;
            });
        } catch (Exception e) {
            checking = false;
        }
    }
}
