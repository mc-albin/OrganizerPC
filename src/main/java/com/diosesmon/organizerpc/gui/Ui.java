package com.diosesmon.organizerpc.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Mini sistema de interfaz "inmediato": el mismo código de maquetación se ejecuta para dibujar
 * y para resolver clics, así dibujo y zonas clicables nunca se desincronizan.
 * Estilo: paneles casi negros, bordes finos, esquinas redondeadas e iconos de objetos.
 */
public final class Ui {
    public enum Mode { DRAW, CLICK }

    public enum Style { NORMAL, PRIMARY, DANGER, SELECTED, WARN, GHOST, LIGHT }

    // Fondo y bordes: salen del tema (color base + acento), ver applyTheme.
    public static int BG, SECTION, BTN, BTN_HOVER, BORDER, BORDER_HOVER, SEL_BG, SEL_BORDER, TEXT, TEXT_DIM;
    /** Línea interior clara arriba de los paneles (relieve como la PC de Cobblemon). */
    public static int BEVEL;
    /** Bordes suaves de secciones y fichas. */
    public static int LINE;
    /** Fondo de celdas y filas de listas. */
    public static int ITEM, ITEM_HOVER;
    /** Fondo de los campos de texto. */
    public static int FIELD;
    /** Texto sobre botones de color (blanco en temas oscuros, negro en claros). */
    public static int TEXT_ON;
    /** Color de acento del tema (bordes de foco, selección). */
    public static int THEME;

    /** Temas predefinidos: nombre, color base y color de acento. */
    public record Preset(String name, int base, int accent) {
    }

    public static final Preset[] PRESETS = {
            new Preset("PC", 0xFF4E545A, 0xFF3DC2CC),
            new Preset("Oscuro", 0xFF181B20, 0xFF5AA9FF),
            new Preset("Azul", 0xFF1F3550, 0xFF5AA9FF),
            new Preset("Rojo", 0xFF4A2226, 0xFFFF6B6B),
            new Preset("Morado", 0xFF33264A, 0xFFB07CFF),
            new Preset("Verde", 0xFF1F3A2B, 0xFF4CD07D),
    };

    private static int themeBase = 0, themeAccent = 0;

    static {
        applyTheme(PRESETS[0].base(), PRESETS[0].accent());
    }

    /** Mezcla dos colores (t = 0 → a, t = 1 → b). Conserva el alfa de {@code a}. */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    /** f > 0 aclara hacia blanco, f < 0 oscurece hacia negro. */
    public static int shade(int c, float f) {
        return f >= 0 ? mix(c, 0xFFFFFFFF, f) : mix(c, 0xFF000000, -f);
    }

    public static float luminance(int c) {
        return (0.299F * ((c >> 16) & 0xFF) + 0.587F * ((c >> 8) & 0xFF) + 0.114F * (c & 0xFF)) / 255F;
    }

    /** Recalcula todos los colores de la interfaz a partir del color base y el de acento. */
    public static void applyTheme(int base, int accent) {
        base |= 0xFF000000;
        accent |= 0xFF000000;
        if (base == themeBase && accent == themeAccent) return;
        themeBase = base;
        themeAccent = accent;
        boolean light = luminance(base) > 0.55F;
        BG = alpha(base, 0xF4);
        SECTION = shade(base, -0.22F);
        BTN = shade(base, light ? -0.06F : 0.08F);
        BTN_HOVER = shade(base, light ? -0.14F : 0.18F);
        BORDER = shade(base, -0.50F);
        BORDER_HOVER = light ? shade(base, -0.65F) : shade(base, 0.45F);
        BEVEL = shade(base, 0.22F);
        LINE = shade(base, -0.32F);
        ITEM = shade(base, -0.12F);
        ITEM_HOVER = shade(base, light ? -0.04F : 0.05F);
        FIELD = shade(base, -0.45F);
        SEL_BG = mix(base, accent, 0.35F);
        SEL_BORDER = accent;
        THEME = accent;
        TEXT = light ? 0xFF15181C : 0xFFF2F3F5;
        TEXT_DIM = mix(TEXT, base, 0.42F);
        TEXT_ON = light ? 0xFF101215 : 0xFFFFFFFF;
    }

    /** Aplica el tema guardado en la configuración (barato: no hace nada si no cambió). */
    public static void syncTheme() {
        var c = com.diosesmon.organizerpc.OrganizerPCClient.config();
        if (c != null) applyTheme(c.themeBase, c.themeAccent);
    }

    // Colores de acento.
    public static final int BLUE = 0xFF5AA9FF;
    public static final int GREEN = 0xFF4CD07D;
    public static final int GOLD = 0xFFFFC857;
    public static final int RED = 0xFFFF5C5C;
    public static final int PURPLE = 0xFFB07CFF;
    public static final int PINK = 0xFFF783AC;
    public static final int ORANGE = 0xFFFF9F43;
    public static final int CYAN = 0xFF4DD6E0;
    public static final int GOOD = GREEN;
    public static final int WARN = GOLD;
    public static final int BAD = RED;

    public final Mode mode;
    public final DrawContext ctx;
    public final TextRenderer tr;
    public final int mx, my;
    private boolean consumed;
    private List<Text> tooltip;
    private int clipX1 = Integer.MIN_VALUE, clipY1 = Integer.MIN_VALUE, clipX2 = Integer.MAX_VALUE, clipY2 = Integer.MAX_VALUE;
    /**
     * Escala aplicada a la matriz mientras se dibuja el organizador. El recorte (scissor) de
     * Minecraft 1.21.1 no usa la matriz, así que hay que pasarlo a coordenadas reales a mano.
     */
    public static float scale = 1F;

    public Ui(Mode mode, DrawContext ctx, int mx, int my) {
        this.mode = mode;
        this.ctx = ctx;
        this.tr = MinecraftClient.getInstance().textRenderer;
        this.mx = mx;
        this.my = my;
    }

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public boolean drawing() {
        return mode == Mode.DRAW;
    }

    public boolean consumed() {
        return consumed;
    }

    public void consume() {
        consumed = true;
    }

    public List<Text> tooltip() {
        return tooltip;
    }

    public boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h
                && mx >= clipX1 && mx < clipX2 && my >= clipY1 && my < clipY2;
    }

    private boolean visible(int y, int h) {
        return y + h > clipY1 && y < clipY2;
    }

    public void beginClip(int x, int y, int w, int h) {
        clipX1 = x;
        clipY1 = y;
        clipX2 = x + w;
        clipY2 = y + h;
        if (drawing()) {
            float s = scale;
            ctx.enableScissor((int) Math.floor(x * s), (int) Math.floor(y * s),
                    (int) Math.ceil((x + w) * s), (int) Math.ceil((y + h) * s));
        }
    }

    public void endClip() {
        clipX1 = clipY1 = Integer.MIN_VALUE;
        clipX2 = clipY2 = Integer.MAX_VALUE;
        if (drawing()) ctx.disableScissor();
    }

    // ------------------------------------------------------------------ primitivas

    public void rect(int x, int y, int w, int h, int color) {
        if (drawing() && visible(y, h) && w > 0 && h > 0) ctx.fill(x, y, x + w, y + h, color);
    }

    public void border(int x, int y, int w, int h, int color) {
        if (drawing() && visible(y, h)) ctx.drawBorder(x, y, w, h, color);
    }

    /** Rectángulo con esquinas redondeadas (radio 2 px). */
    public void round(int x, int y, int w, int h, int color) {
        if (!drawing() || !visible(y, h) || w < 4 || h < 4) {
            rect(x, y, w, h, color);
            return;
        }
        ctx.fill(x + 2, y, x + w - 2, y + h, color);
        ctx.fill(x + 1, y + 1, x + 2, y + h - 1, color);
        ctx.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, color);
        ctx.fill(x, y + 2, x + 1, y + h - 2, color);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
    }

    /** Borde con esquinas redondeadas (radio 2 px). */
    public void roundBorder(int x, int y, int w, int h, int color) {
        if (!drawing() || !visible(y, h) || w < 4 || h < 4) return;
        ctx.fill(x + 2, y, x + w - 2, y + 1, color);
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
        ctx.fill(x, y + 2, x + 1, y + h - 2, color);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
        ctx.fill(x + 1, y + 1, x + 2, y + 2, color);
        ctx.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        ctx.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        ctx.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }

    public void box(int x, int y, int w, int h, int fill, int border) {
        round(x, y, w, h, fill);
        roundBorder(x, y, w, h, border);
    }

    public void panel(int x, int y, int w, int h) {
        box(x, y, w, h, BG, BORDER);
        // Relieve: línea clara arriba, como los marcos de la PC.
        rect(x + 2, y + 1, w - 4, 1, alpha(BEVEL, 0x90));
    }

    public void section(int x, int y, int w, int h) {
        box(x, y, w, h, SECTION, LINE);
    }

    // ------------------------------------------------------------------ texto

    public void text(int x, int y, String s, int color) {
        if (drawing() && visible(y, 9)) ctx.drawText(tr, s, x, y, color, false);
    }

    /** Texto que cabe en {@code maxW}: si no cabe se hace más pequeño (sin «…»), y solo en último caso se corta. */
    public void textTrim(int x, int y, int maxW, String s, int color) {
        fit(x, y, maxW, s, color, false);
    }

    /**
     * Dibuja {@code s} en {@code maxW} de ancho. Si no cabe, lo encoge hasta el 60 % (centrado en
     * vertical en la línea de 8 px); si aun así no cabe, lo corta.
     */
    public void fit(int x, int y, int maxW, String s, int color, boolean center) {
        if (!drawing() || !visible(y, 9) || s.isEmpty() || maxW <= 0) return;
        int w = tr.getWidth(s);
        if (w <= maxW) {
            ctx.drawText(tr, s, center ? x + (maxW - w + 1) / 2 : x, y, color, false);
            return;
        }
        float sc = Math.max(0.6F, maxW / (float) w);
        String t = s;
        if (w * sc > maxW) t = tr.trimToWidth(s, (int) (maxW / sc));
        float tw = tr.getWidth(t) * sc;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(center ? x + (maxW - tw) / 2F : x, y + (8 - 8 * sc) / 2F, 0);
        ctx.getMatrices().scale(sc, sc, 1F);
        ctx.drawText(tr, t, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    /**
     * Tooltip propio, dibujado en las coordenadas (y la escala) del organizador: así en x3/x4
     * no sale enorme. Se mantiene dentro de {@code sw} x {@code sh}.
     */
    public static void drawTooltip(DrawContext ctx, List<Text> lines, int mx, int my, int sw, int sh) {
        if (lines == null || lines.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = 0;
        for (Text t : lines) w = Math.max(w, tr.getWidth(t));
        int h = lines.size() * 10 - 1;
        int x = mx + 10, y = my - 4;
        if (x + w + 8 > sw) x = Math.max(2, mx - w - 12);
        if (y + h + 8 > sh) y = Math.max(2, sh - h - 8);
        y = Math.max(2, y);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 800);
        Ui ui = new Ui(Mode.DRAW, ctx, -1, -1);
        ui.box(x - 4, y - 4, w + 8, h + 8, 0xF2101216, alpha(THEME, 0xC0));
        int ty = y;
        for (Text t : lines) {
            ctx.drawText(tr, t, x, ty, TEXT == 0xFF15181C ? 0xFFF2F3F5 : TEXT, true);
            ty += 10;
        }
        ctx.getMatrices().pop();
    }

    public void textRight(int rightX, int y, String s, int color) {
        text(rightX - tr.getWidth(s), y, s, color);
    }

    public void textCenter(int cx, int y, String s, int color) {
        text(cx - tr.getWidth(s) / 2, y, s, color);
    }

    public String trim(String s, int maxW) {
        if (tr.getWidth(s) <= maxW) return s;
        return tr.trimToWidth(s, Math.max(0, maxW - tr.getWidth("…"))) + "…";
    }

    public int width(String s) {
        return tr.getWidth(s);
    }

    /** Texto con salto de línea automático. Devuelve la altura usada. */
    public int wrapped(int x, int y, int maxW, String s, int color) {
        int h = 0;
        for (var line : tr.wrapLines(Text.literal(s), maxW)) {
            if (drawing() && visible(y + h, 9)) ctx.drawText(tr, line, x, y + h, color, false);
            h += 10;
        }
        return h;
    }

    public int wrappedHeight(int maxW, String s) {
        return tr.wrapLines(Text.literal(s), maxW).size() * 10;
    }

    /** Texto pequeño (75 %). */
    public void small(int x, int y, String s, int color) {
        if (!drawing() || !visible(y, 7)) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(0.75F, 0.75F, 1F);
        ctx.drawText(tr, s, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    /** Texto pequeño que cabe en {@code maxW}: se encoge hasta el 50 % antes de cortarse. */
    public void smallFit(int x, int y, int maxW, String s, int color) {
        if (!drawing() || !visible(y, 7) || s.isEmpty() || maxW <= 0) return;
        int w = tr.getWidth(s);
        float sc = Math.max(0.5F, Math.min(0.75F, maxW / (float) w));
        String t = w * sc > maxW ? tr.trimToWidth(s, (int) (maxW / sc)) : s;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y + (6 - 8 * sc) / 2F, 0);
        ctx.getMatrices().scale(sc, sc, 1F);
        ctx.drawText(tr, t, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    public int smallWidth(String s) {
        return (int) Math.ceil(tr.getWidth(s) * 0.75F);
    }

    // ------------------------------------------------------------------ iconos

    /** Icono de objeto a 12 px (o el tamaño dado). */
    public void icon(ItemStack stack, int x, int y, int size) {
        if (!drawing() || stack == null || stack.isEmpty() || !visible(y, size)) return;
        float s = size / 16F;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(s, s, 1F);
        ctx.drawItem(stack, 0, 0);
        ctx.getMatrices().pop();
    }

    /** Aspa (X) de {@code size} píxeles dibujada con rectángulos (no depende de la fuente). */
    public void cross(int x, int y, int size, int color) {
        if (!drawing()) return;
        for (int i = 0; i < size; i++) {
            ctx.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            ctx.fill(x + size - 1 - i, y + i, x + size - i, y + i + 1, color);
        }
    }

    /** Candado de 7x8 píxeles. */
    public void lock(int x, int y, int color) {
        if (!drawing()) return;
        ctx.fill(x + 1, y, x + 6, y + 1, color);
        ctx.fill(x + 1, y, x + 2, y + 4, color);
        ctx.fill(x + 5, y, x + 6, y + 4, color);
        ctx.fill(x, y + 3, x + 7, y + 8, color);
        ctx.fill(x + 3, y + 5, x + 4, y + 7, 0xFF000000);
    }

    // ------------------------------------------------------------------ tooltips

    /** true solo si el ratón está encima: así los tooltips caros se construyen solo cuando se ven. */
    public boolean wantsTooltip(int x, int y, int w, int h) {
        return drawing() && tooltip == null && hover(x, y, w, h);
    }

    public void tooltip(int x, int y, int w, int h, String... lines) {
        if (drawing() && hover(x, y, w, h)) {
            List<Text> l = new ArrayList<>();
            for (String s : lines) l.add(Text.literal(s));
            tooltip = l;
        }
    }

    public void tooltipLines(int x, int y, int w, int h, List<String> lines) {
        tooltip(x, y, w, h, lines.toArray(new String[0]));
    }

    public void tooltipText(int x, int y, int w, int h, List<Text> lines) {
        if (drawing() && hover(x, y, w, h)) tooltip = lines;
    }

    // ------------------------------------------------------------------ controles

    private boolean clicked(int x, int y, int w, int h) {
        if (drawing() || consumed || !hover(x, y, w, h)) return false;
        consumed = true;
        click();
        return true;
    }

    /** Botón redondeado con icono opcional. */
    public boolean button(int x, int y, int w, int h, String label, ItemStack icon, Style style) {
        if (drawing() && visible(y, h)) {
            boolean hov = hover(x, y, w, h);
            int fill, border, text;
            switch (style) {
                case PRIMARY -> { fill = mix(BTN, GREEN, hov ? 0.42F : 0.28F); border = GREEN; text = TEXT_ON; }
                case DANGER -> { fill = mix(BTN, RED, hov ? 0.42F : 0.28F); border = RED; text = TEXT_ON; }
                case WARN -> { fill = mix(BTN, GOLD, hov ? 0.40F : 0.26F); border = GOLD; text = TEXT_ON; }
                case SELECTED -> { fill = SEL_BG; border = SEL_BORDER; text = TEXT_ON; }
                case LIGHT -> { fill = hov ? 0xFFFFFFFF : 0xFFDDE1E6; border = 0xFFFFFFFF; text = 0xFF15181C; }
                case GHOST -> { fill = hov ? 0x40FFFFFF : 0x00000000; border = hov ? BORDER_HOVER : 0x00000000; text = hov ? TEXT : TEXT_DIM; }
                default -> { fill = hov ? BTN_HOVER : BTN; border = hov ? BORDER_HOVER : BORDER; text = TEXT; }
            }
            if (fill != 0) round(x, y, w, h, fill);
            if (border != 0) roundBorder(x, y, w, h, border);
            int iconSize = Math.min(12, h - 3);
            int contentW = (icon != null ? iconSize + (label.isEmpty() ? 0 : 3) : 0) + width(label);
            int maxText = w - 6 - (icon != null ? iconSize + 3 : 0);
            contentW = Math.min(contentW, w - 6);
            int cx = x + (w - contentW) / 2;
            if (icon != null) {
                icon(icon, cx, y + (h - iconSize) / 2, iconSize);
                cx += iconSize + 3;
            }
            if (!label.isEmpty()) fit(cx, y + (h - 8) / 2, Math.max(0, Math.min(maxText, x + w - 3 - cx)), label, text, false);
            return false;
        }
        return clicked(x, y, w, h);
    }

    public boolean button(int x, int y, int w, int h, String label, Style style) {
        return button(x, y, w, h, label, null, style);
    }

    public boolean button(int x, int y, int w, int h, String label) {
        return button(x, y, w, h, label, null, Style.NORMAL);
    }

    /** Ficha de color: encendida se rellena con su color. */
    public boolean chip(int x, int y, int w, int h, String label, boolean on, int color) {
        if (drawing() && visible(y, h)) {
            boolean hov = hover(x, y, w, h);
            round(x, y, w, h, on ? mix(BTN, color, 0.55F) : (hov ? BTN_HOVER : BTN));
            roundBorder(x, y, w, h, on ? color : (hov ? BORDER_HOVER : BORDER));
            fit(x + 2, y + (h - 8) / 2, w - 4, label, on ? TEXT_ON : TEXT_DIM, true);
            return false;
        }
        return clicked(x, y, w, h);
    }

    /** Botón ancho de ajustes: verde cuando está encendido (como «Opción: Sí»). */
    public boolean toggleButton(int x, int y, int w, int h, String label, boolean on) {
        if (drawing() && visible(y, h)) {
            boolean hov = hover(x, y, w, h);
            round(x, y, w, h, on ? mix(BTN, GREEN, hov ? 0.34F : 0.24F) : hov ? BTN_HOVER : BTN);
            roundBorder(x, y, w, h, on ? mix(GREEN, BORDER, 0.25F) : hov ? BORDER_HOVER : BORDER);
            fit(x + 3, y + (h - 8) / 2, w - 6, label, on ? TEXT_ON : TEXT, true);
            return false;
        }
        return clicked(x, y, w, h);
    }

    /** Zona clicable invisible (filas de listas). */
    public boolean area(int x, int y, int w, int h) {
        return clicked(x, y, w, h);
    }

    public static void click() {
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
