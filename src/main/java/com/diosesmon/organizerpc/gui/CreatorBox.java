package com.diosesmon.organizerpc.gui;

import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Recuadro del creador debajo de la PC: su logo, «Creador del mod iAlbin73» y un punto rojo que se enciende
 * cuando está en vivo en Twitch. Click: abre su canal.
 */
final class CreatorBox {
    static final String CHANNEL = "ialbin73";
    private static final Identifier LOGO = Identifier.of("organizerpc", "textures/gui/creator.png");
    private static final long EVERY_MS = 120_000L;
    private static volatile boolean live, checking;
    private static volatile long checkedAt;
    private static HttpClient http;

    private CreatorBox() {
    }

    static int[] rect(OrganizerPanel p) {
        int w = 116, h = 28;
        int x = p.pcLeft + (p.pcRight - p.pcLeft) / 2 - w / 2;
        int y = p.pcBottom() + 3;
        return new int[]{x, y, w, h};
    }

    static boolean visible(OrganizerPanel p) {
        if (PanelState.moveOpen || PanelState.preview != null) return false;
        int[] r = rect(p);
        return r[1] + r[3] <= p.vh - 1;
    }

    static boolean in(OrganizerPanel p, double mx, double my) {
        if (!visible(p)) return false;
        int[] r = rect(p);
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    static void layout(OrganizerPanel p, Ui ui) {
        if (!visible(p)) return;
        int[] r = rect(p);
        int x = r[0], y = r[1], w = r[2], h = r[3];
        if (ui.drawing()) {
            poll();
            boolean hov = ui.hover(x, y, w, h);
            ui.box(x, y, w, h, hov ? Ui.BTN_HOVER : Ui.BG, live ? Ui.alpha(0xFFFF3B3B, 0xC0) : hov ? Ui.BORDER_HOVER : Ui.BORDER);
            ui.ctx.drawTexture(LOGO, x + 3, y + 3, 0, 0, 22, 22, 22, 22);
            ui.small(x + 29, y + 5, "Creador del mod", Ui.TEXT_DIM);
            ui.text(x + 29, y + 14, "iAlbin73", Ui.TEXT);
            // Punto de directo: rojo encendido (con brillo que late) en vivo, apagado si no
            int dx = x + w - 11, dy = y + 4;
            if (live) {
                float a = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 300.0);
                ui.round(dx - 2, dy - 2, 11, 11, Ui.alpha(0xFFFF3B3B, (int) (0x30 + 0x50 * a)));
                ui.round(dx, dy, 7, 7, 0xFFFF2A2A);
                ui.round(dx + 1, dy + 1, 2, 2, 0xFFFFB0B0);
            } else {
                ui.round(dx, dy, 7, 7, 0xFF4A2326);
            }
            ui.small(x + w - 4 - ui.smallWidth(live ? "EN VIVO" : "Twitch"), y + 17, live ? "EN VIVO" : "Twitch", live ? 0xFFFF6B6B : Ui.TEXT_DIM);
        }
        ui.tooltip(x, y, w, h, live ? "¡iAlbin73 está en vivo en Twitch!" : "iAlbin73 no está en vivo ahora.", "Click: abrir twitch.tv/" + CHANNEL);
        if (ui.area(x, y, w, h)) {
            try {
                net.minecraft.util.Util.getOperatingSystem().open(URI.create("https://www.twitch.tv/" + CHANNEL));
            } catch (Exception ignored) {
                // Sin navegador: no pasa nada.
            }
        }
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
