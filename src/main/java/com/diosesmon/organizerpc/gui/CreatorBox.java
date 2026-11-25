package com.diosesmon.organizerpc.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Foto del creador en un recuadro encima del botón AFK, con un punto rojo que parpadea cuando está en vivo en Twitch.
 * Mide siempre los mismos píxeles de pantalla (igual en x1, x2, x3 y x4).
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

    /**
     * {x, y, lado} en coordenadas de la GUI de Minecraft: encima del botón AFK, alineado a su derecha.
     * Si la barra de arriba no deja sitio encima, va a la derecha de la barra.
     */
    private static float[] real(OrganizerPanel p) {
        float side = (float) (PX / gs()), gap = (float) (3 / gs());
        float x = (p.rAfk[0] + p.rAfk[2]) * p.k - side;
        float y = p.tbY * p.k - side - gap;
        if (y < 1) {
            x = (p.tbX + p.tbW) * p.k + gap;
            y = p.tbY * p.k;
        }
        return new float[]{x, y, side};
    }

    /** Lo mismo en coordenadas del organizador (para el ratón y el tooltip). */
    static int[] rect(OrganizerPanel p) {
        float[] r = real(p);
        return new int[]{(int) Math.floor(r[0] / p.k), (int) Math.floor(r[1] / p.k),
                Math.max(1, Math.round(r[2] / p.k)), Math.max(1, Math.round(r[2] / p.k))};
    }

    static boolean visible(OrganizerPanel p) {
        if (p.rAfk[2] <= 0) return false;
        float[] r = real(p);
        return r[0] >= 0 && r[0] + r[2] <= p.gui.width && r[1] >= 0;
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

    /** Tooltip con colores: el nombre en morado, el estado en rojo o gris y cómo ir al canal. */
    private static java.util.List<net.minecraft.text.Text> tip() {
        var name = net.minecraft.text.Text.literal("iAlbin73").setStyle(net.minecraft.text.Style.EMPTY.withBold(true).withColor(PURPLE));
        java.util.List<net.minecraft.text.Text> l = new java.util.ArrayList<>();
        l.add(net.minecraft.text.Text.literal("Creador del mod ").withColor(0xE6E8EC).append(name));
        l.add(live ? net.minecraft.text.Text.literal("● EN VIVO en Twitch").setStyle(net.minecraft.text.Style.EMPTY.withBold(true).withColor(RED))
                : net.minecraft.text.Text.literal("● No está en vivo ahora").withColor(0x8A8F98));
        l.add(net.minecraft.text.Text.empty());
        l.add(net.minecraft.text.Text.literal("Dar clic para ir").withColor(PURPLE));
        return l;
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
        // Recuadro redondeado como los paneles del organizador, con la foto dentro
        int border = hov ? Ui.BORDER_HOVER : Ui.BORDER;
        ui.ctx.fill(1, 0, PX - 1, PX, border);
        ui.ctx.fill(0, 1, PX, PX - 1, border);
        ui.ctx.fill(1, 1, PX - 1, PX - 1, Ui.BG | 0xFF000000);
        ui.ctx.fill(2, 2, PX - 2, 3, Ui.alpha(0xFFFFFFFF, 0x18));
        ui.ctx.drawTexture(LOGO, 3, 3, 0, 0, PX - 6, PX - 6, PX - 6, PX - 6);
        // En vivo: punto rojo en la esquina que parpadea dos veces cada 3 segundos
        if (live) {
            long t = System.currentTimeMillis() % 3000L;
            boolean on = !(t >= 2300 && t < 2450) && !(t >= 2600 && t < 2750);
            int d = 7, dx = PX - d - 3, dy = 3;
            ui.ctx.fill(dx - 1, dy - 1, dx + d + 1, dy + d + 1, 0xFF101215);
            if (on) {
                ui.ctx.fill(dx, dy, dx + d, dy + d, 0xFFFF2A2A);
                ui.ctx.fill(dx + 1, dy + 1, dx + 3, dy + 3, 0xFFFFB0B0);
            } else {
                ui.ctx.fill(dx, dy, dx + d, dy + d, 0xFF5A2A2E);
            }
        }
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
