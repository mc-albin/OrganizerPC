package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.afk.AfkTracker;
import com.diosesmon.organizerpc.cobblemon.MoveExecutor;
import com.diosesmon.organizerpc.cobblemon.PcReader;
import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.filter.Comparison;
import com.diosesmon.organizerpc.core.filter.ConditionType;
import com.diosesmon.organizerpc.core.filter.PerfectIvCountCondition;
import com.diosesmon.organizerpc.core.plan.PcModel;
import com.diosesmon.organizerpc.core.plan.Plan;
import com.diosesmon.organizerpc.core.plan.Planner;
import com.diosesmon.organizerpc.mixin.StorageWidgetAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Coordina las piezas del organizador dentro de la PC original de Cobblemon:
 * barra superior, panel de categorías, ventanas de Mover y Ajustes, vista previa y tutorial.
 * No abre ninguna pantalla nueva.
 */
public final class OrganizerPanel {
    final PCGUI gui;
    final TextFieldWidget nameField;
    final TextFieldWidget speciesField;
    /** Búsqueda de la barra de arriba (especie, grupo huevo, «ps31»…). */
    final TextFieldWidget searchField;
    /** Colores del tema en hexadecimal (pestaña Tema de Ajustes). */
    final TextFieldWidget hexBaseField, hexAccentField;

    PcModel snapshot;
    final Map<String, Integer> matchCounts = new HashMap<>();
    final Map<String, int[]> listRegions = new HashMap<>();
    private long signature;
    private int ticks;
    float delta;

    // Geometría (recalculada cada frame).
    /**
     * Escala de la interfaz del organizador respecto a la de la PC (1 = mismo tamaño).
     * Con escalas de GUI grandes (Automático, x3, x4) no cabe todo alrededor de la PC, así que
     * el organizador se dibuja más pequeño. Todas las coordenadas de geometría son "virtuales":
     * pantalla real dividida entre {@code k}. La PC y sus slots siguen en coordenadas reales.
     */
    float k = 1F;
    /** Tamaño de la pantalla en coordenadas virtuales. */
    int vw, vh;
    int pcLeft, pcTop, pcRight;
    int tbX, tbY, tbW, tbH, stripY, stripX0, chipW, perRow, stripRows;
    /** Columnas de botones a la izquierda de la barra de cajas y separación entre cajas. */
    int modeCols = 3, chipGap = 2;
    /** No cabe la barra encima de la PC: se pega arriba del todo (y en una sola fila si se puede). */
    boolean cramped;
    int sx, sy, sw, sh;
    int mvX, mvY, mvW, mvH;
    static final int SW_W = 310, SW_H = 258;
    /** Botones a la izquierda de la barra de cajas: bloquear, captura, ocultar, IVs, huevo, ajustes y tutorial. */
    static final int MODE_BUTTONS = 8;
    static final int AW_W = 210, AW_H = 284;
    /** Rectángulos de botones (para el tutorial): {x, y, w, h}. */
    int[] rOrganize = new int[4], rLock = new int[4], rCapture = new int[4], rHide = new int[4], rMove = new int[4],
            rAfk = new int[4], rEgg = new int[4], rBook = new int[4], rIvs = new int[4], rFilter = new int[4], rSearch = new int[4],
            rSettings = new int[4], rPairs = new int[4];
    /** La caja de búsqueda se dibujó este frame (no hay vista previa ni movimiento). */
    boolean searchShown;
    /** Zonas del editor de categorías para la guía: nombre, botón Listo y lista con scroll. */
    int[] gName = new int[4], gDone = new int[4], editorList;
    /** Origen (y) del contenido del editor este frame (ya con el scroll aplicado). */
    int editorTop;
    /** Secciones del editor: {inicio, fin} en coordenadas del contenido (sin scroll). */
    final Map<String, int[]> guideSec = new HashMap<>();

    OrganizerPanel(PCGUI gui) {
        this.gui = gui;
        var tr = MinecraftClient.getInstance().textRenderer;
        this.nameField = new TextFieldWidget(tr, 0, 0, 90, 10, Text.literal("Nombre"));
        nameField.setMaxLength(32);
        nameField.setDrawsBackground(false);
        nameField.setChangedListener(s -> {
            if (PanelState.editing != null) PanelState.editing.name = s;
        });
        if (PanelState.editing != null) nameField.setText(PanelState.editing.name);
        this.speciesField = new TextFieldWidget(tr, 0, 0, 90, 10, Text.literal("Especie"));
        speciesField.setMaxLength(24);
        speciesField.setDrawsBackground(false);
        this.searchField = new TextFieldWidget(tr, 0, 0, 90, 10, Text.literal("Buscar"));
        searchField.setMaxLength(48);
        searchField.setDrawsBackground(false);
        searchField.setText(Search.text);
        searchField.setChangedListener(s -> Search.text = s);
        this.hexBaseField = hexField(tr, true);
        this.hexAccentField = hexField(tr, false);
        refreshSnapshot();
        if (!cfg().tutorialDone && PanelState.tutorialStep < 0) {
            // Primera vez: el tutorial sale directamente (aunque el organizador estuviera oculto).
            PanelState.tutorialStep = 0;
            if (cfg().hidden) cfg().hidden = false;
        }
        if (AfkTracker.isAfk()) PanelState.status("Estás AFK. Pulsa el botón AFK para volver.", Ui.GOLD);
        else if (com.diosesmon.organizerpc.update.Updater.state() == com.diosesmon.organizerpc.update.Updater.State.AVAILABLE
                && !PanelState.updateNotified) {
            PanelState.updateNotified = true;
            PanelState.status("Hay una versión nueva de OrganizerPC (v" + com.diosesmon.organizerpc.update.Updater.latestVersion()
                    + "). Ajustes → Actualizar.", Ui.GOLD);
        }
    }

    private TextFieldWidget hexField(net.minecraft.client.font.TextRenderer tr, boolean base) {
        TextFieldWidget f = new TextFieldWidget(tr, 0, 0, 60, 10, Text.literal(base ? "Fondo" : "Acento"));
        f.setMaxLength(7);
        f.setDrawsBackground(false);
        f.setText(String.format("#%06X", (base ? cfg().themeBase : cfg().themeAccent) & 0xFFFFFF));
        f.setChangedListener(s -> {
            Integer c = com.diosesmon.organizerpc.config.ConfigStore.hex(s);
            if (c == null) return;
            if (base) cfg().themeBase = c;
            else cfg().themeAccent = c;
            Ui.syncTheme();
        });
        return f;
    }

    /** Pone en los campos hexadecimales los colores actuales del tema. */
    void syncHexFields() {
        hexBaseField.setText(String.format("#%06X", cfg().themeBase & 0xFFFFFF));
        hexAccentField.setText(String.format("#%06X", cfg().themeAccent & 0xFFFFFF));
    }

    boolean hexFieldsVisible() {
        return PanelState.settingsOpen && PanelState.settingsTab == SettingsWindow.TAB_THEME;
    }

    // ------------------------------------------------------------------ datos

    OrganizerConfig cfg() {
        return OrganizerPCClient.config();
    }

    ClientPC pc() {
        return gui.getPc();
    }

    int currentBox() {
        return gui.getStorage().getBox();
    }

    void goToBox(int box) {
        if (box >= 0 && box < boxCount() && box != currentBox()) gui.getStorage().setBox(box);
    }

    int boxCount() {
        return pc().getBoxes().size();
    }

    String boxName(int box) {
        List<ClientBox> boxes = pc().getBoxes();
        if (box < 0 || box >= boxes.size()) return "Caja " + (box + 1);
        String n = boxes.get(box).getName() == null ? "" : boxes.get(box).getName().getString();
        return n.isBlank() ? "Caja " + (box + 1) : n;
    }

    boolean locked(int box) {
        return cfg().lockedBoxes.contains(box);
    }

    boolean capture(int box) {
        return cfg().captureBoxes.contains(box);
    }

    /** Categoría (por prioridad) a la que pertenece una caja, o null. */
    Category ownerOf(int box) {
        for (Category c : cfg().categories) if (c.ownsBox(box)) return c;
        return null;
    }

    Pokemon pokemon(UUID uuid) {
        return uuid == null ? null : pc().findByUUID(uuid);
    }

    void refreshSnapshot() {
        try {
            snapshot = PcReader.read(pc());
            com.diosesmon.organizerpc.cobblemon.BreederExport.autoSync(pc());
        } catch (Exception e) {
            snapshot = null;
            PanelState.status(e.getMessage(), Ui.BAD);
        }
        recountMatches();
    }

    void recountMatches() {
        matchCounts.clear();
        if (snapshot == null) return;
        for (Category c : cfg().categories) {
            int n = 0;
            for (PokemonInfo p : snapshot.allInfos().values()) if (c.matches(p)) n++;
            matchCounts.put(c.id, n);
        }
    }

    /** Firma barata de la PC (UUID por posición) para releerla sola cuando cambia. */
    private long computeSignature() {
        long sig = 1;
        List<ClientBox> boxes = pc().getBoxes();
        for (ClientBox box : boxes) {
            for (Pokemon p : box.getSlots()) sig = sig * 31 + (p == null ? 0 : p.getUuid().hashCode());
        }
        return sig * 31 + boxes.size();
    }

    void tick() {
        if (PanelState.statusTicks > 0 && --PanelState.statusTicks == 0) PanelState.status = "";
        if (++ticks % 10 != 0) return;
        if (com.diosesmon.organizerpc.cobblemon.BreederExport.consumeDirty()) com.diosesmon.organizerpc.cobblemon.BreederExport.autoSync(pc());
        long sig = computeSignature();
        if (sig == signature) return;
        signature = sig;
        refreshSnapshot();
        if (snapshot != null) PanelState.selection.removeIf(u -> snapshot.positionOf(u) == null);
        if (PanelState.preview != null && !MoveExecutor.isRunning()) {
            Plan p = recomputePreview();
            if (p != null) PanelState.preview = p;
        }
    }

    Category highlightedCategory() {
        if (!cfg().highlightCategory || PanelState.preview != null) return null;
        return PanelState.editing;
    }

    List<BoxStorageSlot> boxSlots() {
        try {
            return ((StorageWidgetAccessor) (Object) gui.getStorage()).organizerpc$getBoxSlots();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Huecos del equipo que dibuja la PC (a la derecha de la caja). */
    List<com.cobblemon.mod.common.client.gui.pc.PartyStorageSlot> partySlots() {
        try {
            return ((StorageWidgetAccessor) (Object) gui.getStorage()).organizerpc$getPartySlots();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Marcas de IVs sobre los Pokémon del equipo dentro de la PC (coordenadas reales). */
    void partyIvMarks(DrawContext ctx) {
        for (var slot : partySlots()) {
            Pokemon mon = slot.getPokemon();
            if (mon != null) SlotMarks.ivBar(ctx, slot.getX(), slot.getY(), PcReader.info(mon));
        }
    }

    BoxStorageSlot slotAt(double mx, double my) {
        for (BoxStorageSlot s : boxSlots()) {
            if (mx >= s.getX() && mx < s.getX() + StorageSlot.SIZE && my >= s.getY() && my < s.getY() + StorageSlot.SIZE) return s;
        }
        return null;
    }

    int[] gridRect() {
        List<BoxStorageSlot> slots = boxSlots();
        if (slots.isEmpty()) return null;
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
        for (BoxStorageSlot s : slots) {
            x1 = Math.min(x1, s.getX());
            y1 = Math.min(y1, s.getY());
            x2 = Math.max(x2, s.getX() + StorageSlot.SIZE);
            y2 = Math.max(y2, s.getY() + StorageSlot.SIZE);
        }
        return new int[]{x1, y1, x2 - x1, y2 - y1};
    }

    // ------------------------------------------------------------------ geometría

    /** Filas naturales de la barra de cajas (30 cajas por fila). */
    private int naturalRows() {
        int n = Math.max(1, boxCount());
        return n <= 30 ? 1 : (n + 29) / 30;
    }

    /** Altura de la barra superior (no depende de la escala). */
    private static int topBarHeight(int rows) {
        return 5 + 18 + 5 + Math.max(rows * 18, 18) + 3;
    }

    /**
     * Elige la escala del organizador según la escala de GUI del juego y el espacio libre
     * alrededor de la PC. Solo usa tamaños de píxel enteros (texto nítido): con escala de GUI 4
     * prueba 4, 3, 2 y 1 píxeles por unidad y se queda con el mayor que cabe.
     */
    private float chooseScale() {
        double gs = MinecraftClient.getInstance().getWindow().getScaleFactor();
        if (gs <= 1) return 1F;
        int maxE = Math.max(1, (int) Math.floor(gs));
        int size = cfg().uiSize;
        if (size > 0) return (float) (Math.min(size, maxE) / gs);
        int w = gui.width, h = gui.height;
        int realLeft = (w - PCGUI.BASE_WIDTH) / 2;
        int realTop = (h - PCGUI.BASE_HEIGHT) / 2;
        int freeTop = realTop - 4;
        int freeRight = w - (realLeft + PCGUI.BASE_WIDTH) - 6;
        int tb = topBarHeight(naturalRows());
        // Nunca por debajo de 2 píxeles por unidad (con x3/x4 la escala 1 se ve diminuta):
        // si no cabe alrededor de la PC, la barra y el panel se solapan un poco con ella.
        int minE = Math.min(2, maxE);
        for (int e = maxE; e > minE; e--) {
            float kk = (float) (e / gs);
            boolean fits = tb * kk <= freeTop
                    && 205 * kk <= freeRight
                    && Math.max(SW_H, AW_H) * kk <= h - 4
                    && Math.max(SW_W, AW_W) * kk <= w - 4;
            if (fits) return kk;
        }
        return (float) (minE / gs);
    }

    int vx(double realX) {
        return (int) Math.floor(realX / k);
    }

    int vy(double realY) {
        return (int) Math.floor(realY / k);
    }

    /** Aplica la escala del organizador a la matriz (y a los recortes de {@link Ui}). */
    void pushScale(DrawContext ctx) {
        ctx.getMatrices().push();
        ctx.getMatrices().scale(k, k, 1F);
        Ui.scale = k;
    }

    void popScale(DrawContext ctx) {
        ctx.getMatrices().pop();
        Ui.scale = 1F;
    }

    void computeGeometry() {
        k = chooseScale();
        vw = (int) Math.ceil(gui.width / k);
        vh = (int) Math.ceil(gui.height / k);
        int w = vw, h = vh;
        int realLeft = (gui.width - PCGUI.BASE_WIDTH) / 2;
        int realTop = (gui.height - PCGUI.BASE_HEIGHT) / 2;
        pcLeft = vx(realLeft);
        pcTop = vy(realTop);
        pcRight = (int) Math.ceil((realLeft + PCGUI.BASE_WIDTH) / k);
        int pcW = pcRight - pcLeft;
        int pcH = (int) Math.ceil(PCGUI.BASE_HEIGHT / k);

        int n = Math.max(1, boxCount());
        tbW = Math.min(w - 4, 440);
        stripRows = naturalRows();
        chipGap = 2;
        cramped = pcTop - topBarHeight(stripRows) - 3 < 1;
        if (cramped && stripRows > 1) {
            // Sin sitio encima de la PC: barra más ancha con todas las cajas en una fila (tapa menos la PC).
            int wide = Math.min(w - 4, Math.max(440, pcW));
            int cw = (wide - 5 - (MODE_BUTTONS * 18 + 4) - 5 - (n - 1)) / n;
            if (cw >= 9) {
                stripRows = 1;
                tbW = wide;
                chipGap = 1;
            }
        }
        tbX = Math.max(2, Math.min(w - tbW - 2, pcLeft + pcW / 2 - tbW / 2));
        // Botones de la izquierda (bloquear, captura, ocultar, IVs, grupo huevo) en tantas filas como la barra.
        int btnRows = Math.min(stripRows, MODE_BUTTONS);
        modeCols = (MODE_BUTTONS + btnRows - 1) / btnRows;
        perRow = (n + stripRows - 1) / stripRows;
        stripX0 = tbX + 5 + modeCols * 18 + 4;
        int stripW = tbX + tbW - 5 - stripX0;
        chipW = Math.max(8, Math.min(22, (stripW - (perRow - 1) * chipGap) / perRow));
        tbH = topBarHeight(stripRows);
        tbY = Math.max(1, pcTop - tbH - 3);
        stripY = tbY + 28;

        sx = pcRight + 4;
        // Recuadro derecho (Categorías / Mover): mismo tamaño siempre.
        sy = pcTop;
        int avail = w - sx - 2;
        if (avail >= 240) {
            sw = 240;
        } else if (avail >= 205) {
            sw = avail;
        } else {
            sw = 220;
            sx = w - sw - 2;
        }
        // Si la barra de arriba baja hasta el panel (pantalla justa), el panel empieza debajo.
        if (tbY + tbH + 2 > sy && sx < tbX + tbW && sx + sw > tbX) sy = tbY + tbH + 2;
        sh = Math.max(Math.min(pcH + 20, h - sy - 2), Math.min(h - sy - 2, 330));

        // Panel Mover: debajo de la PC.
        mvW = Math.min(w - 4, Math.max(pcW + 60, 430));
        mvX = Math.max(2, pcLeft + pcW / 2 - mvW / 2);
        mvY = pcTop + pcH + 3;
        mvH = Math.min(112, h - mvY - 2);
        if (mvH < 92) {
            mvH = 92;
            mvY = h - mvH - 2;
        }

        if (PanelState.dragging != null) {
            long win = MinecraftClient.getInstance().getWindow().getHandle();
            if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) PanelState.dragging = null;
        }
    }

    /** Posición (ajustada a la pantalla) de una ventana flotante: {x, y, w, h}. */
    int[] win(PanelState.Win wnd) {
        int ww = wnd == PanelState.Win.ARRANGE ? AW_W : SW_W;
        int wh = wnd == PanelState.Win.ARRANGE ? AW_H : SW_H;
        if (wnd == PanelState.Win.SETTINGS) return new int[]{Math.max(0, (vw - ww) / 2), Math.max(0, (vh - wh) / 2), ww, wh};
        int[] pos = PanelState.winPos.computeIfAbsent(wnd, key -> new int[]{(vw - ww) / 2, (vh - wh) / 2});
        int x = Math.max(0, Math.min(vw - ww, pos[0]));
        int y = Math.max(0, Math.min(vh - wh, pos[1]));
        return new int[]{x, y, ww, wh};
    }

    private boolean busy() {
        return PanelState.preview != null || MoveExecutor.isRunning() || PanelState.confirm != null;
    }

    boolean moveWindowVisible() {
        return PanelState.moveOpen && !busy();
    }

    boolean settingsVisible() {
        return PanelState.settingsOpen && !MoveExecutor.isRunning();
    }

    boolean arrangeVisible() {
        return PanelState.arrangeOpen && !busy();
    }

    boolean inMovePanel(double mx, double my) {
        return false; // «Mover» vive dentro del recuadro derecho
    }

    boolean inTopBar(double mx, double my) {
        return mx >= tbX && mx < tbX + tbW && my >= tbY && my < tbY + tbH;
    }

    boolean inSide(double mx, double my) {
        if (sideCollapsed()) {
            int[] t = sideTab();
            return mx >= t[0] && mx < t[0] + t[2] && my >= t[1] && my < t[1] + t[3];
        }
        int[] h = hideTab();
        if (mx >= h[0] && mx < h[0] + h[2] && my >= h[1] && my < h[1] + h[3]) return true;
        return mx >= sx && mx < sx + sw && my >= sy && my < sy + sh;
    }

    /** Pestañita para ocultar el panel: sobresale por su borde izquierdo, como la cinta de un libro. */
    int[] hideTab() {
        return new int[]{sx - 12, sy + 6, 13, 24};
    }

    /**
     * Panel derecho plegado (con x3/x4 tapa mucho). Se despliega solo con vista previa,
     * confirmaciones, movimiento en curso o tutoriales, para no actuar sin verlo.
     */
    boolean sideCollapsed() {
        return cfg().sidePanelCollapsed && PanelState.preview == null && PanelState.confirm == null
                && !MoveExecutor.isRunning() && PanelState.tutorialStep < 0 && !Guide.active();
    }

    /** Pestañita del panel plegado, pegada al borde derecho: {x, y, w, h}. */
    int[] sideTab() {
        return new int[]{vw - 16, sy + 24, 16, 48};
    }

    boolean inWin(PanelState.Win wnd, double mx, double my) {
        boolean vis = wnd == PanelState.Win.ARRANGE ? arrangeVisible() : settingsVisible();
        if (!vis) return false;
        int[] r = win(wnd);
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    boolean nameFieldVisible() {
        return !sideCollapsed() && PanelState.editing != null && !PanelState.moveOpen && !busy() && PanelState.tutorialStep < 0 && !settingsVisible();
    }

    TextFieldWidget focusedField() {
        for (TextFieldWidget f : allFields()) if (f.isFocused()) return f;
        return null;
    }

    TextFieldWidget[] allFields() {
        return new TextFieldWidget[]{nameField, speciesField, searchField, hexBaseField, hexAccentField};
    }

    /** Pierde el foco el campo que ya no se ve (así las teclas vuelven a la PC). */
    void dropHiddenFocus() {
        boolean hidden = cfg().hidden;
        if (nameField.isFocused() && (hidden || !nameFieldVisible())) unfocusFields();
        if (speciesField.isFocused() && (hidden || !nameFieldVisible() || !SidePanel.speciesFieldShown)) unfocusFields();
        if (searchField.isFocused() && (hidden || !searchShown || PanelState.settingsOpen)) unfocusFields();
        if ((hexBaseField.isFocused() || hexAccentField.isFocused()) && !hexFieldsVisible()) unfocusFields();
    }

    /** Da el foco a un campo de texto si el ratón está encima. */
    boolean focusFieldAt(double mx, double my, TextFieldWidget... fields) {
        for (TextFieldWidget f : fields) {
            if (f.isMouseOver(mx, my)) {
                unfocusFields();
                f.mouseClicked(mx, my, 0);
                gui.setFocused(f);
                f.setFocused(true);
                return true;
            }
        }
        return false;
    }

    /** Dibuja los campos hexadecimales de la pestaña Tema (coordenadas virtuales). */
    void renderHexFields(DrawContext ctx, int mx, int my, float delta) {
        if (!hexFieldsVisible()) return;
        for (TextFieldWidget f : new TextFieldWidget[]{hexBaseField, hexAccentField}) {
            f.setEditableColor(Ui.TEXT & 0xFFFFFF);
            f.render(ctx, mx, my, delta);
        }
    }

    // ------------------------------------------------------------------ dibujo y entrada

    void render(DrawContext ctx, int mx, int my, float delta) {
        this.delta = delta;
        listRegions.clear(); // solo cuentan las listas dibujadas este frame (si no, la rueda mueve otra)
        Ui.syncTheme();
        computeGeometry();
        Search.refresh(snapshot);
        // La caja de la PC (vista previa y marcas) va en coordenadas reales.
        PreviewOverlay.render(this, ctx, delta);
        if (PanelState.preview == null) {
            SlotMarks.render(this, ctx, mx, my);
            Pairs.renderSlots(this, ctx);
        }

        // Todo lo del organizador va en coordenadas virtuales (escaladas).
        int rmx = mx, rmy = my;
        mx = vx(rmx);
        my = vy(rmy);
        if (PanelState.dragging != null) {
            PanelState.winPos.put(PanelState.dragging, new int[]{mx - PanelState.dragDX, my - PanelState.dragDY});
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 500);
        pushScale(ctx);
        boolean modal = settingsVisible();
        Ui ui = new Ui(Ui.Mode.DRAW, ctx, modal ? -9999 : mx, modal ? -9999 : my);
        searchShown = false;
        TopBar.layout(this, ui);
        if (searchShown) {
            searchField.setEditableColor(Ui.TEXT & 0xFFFFFF);
            searchField.render(ctx, mx, my, delta);
        }
        SidePanel.layout(this, ui);
        if (nameFieldVisible()) {
            nameField.setEditableColor(Ui.TEXT & 0xFFFFFF);
            nameField.render(ctx, mx, my, delta);
            if (SidePanel.speciesFieldShown) {
                speciesField.setEditableColor(Ui.TEXT & 0xFFFFFF);
                speciesField.render(ctx, mx, my, delta);
            }
        }
        if (Search.popupOpen && searchShown) {
            clearDepth();
            Search.popup(this, ui);
        } else {
            Search.popupOpen = false;
        }
        // Las ventanas tapan del todo lo de detrás (también los iconos, que se dibujan más "alto").
        if (arrangeVisible()) {
            clearDepth();
            ArrangeWindow.layout(this, ui);
        }
        if (PanelState.tutorialStep >= 0) {
            clearDepth();
            Tutorial.layout(this, ui);
        } else if (Guide.active()) {
            clearDepth();
            Guide.layout(this, ui);
        }
        if (modal) {
            clearDepth();
            ui = new Ui(Ui.Mode.DRAW, ctx, mx, my);
            SettingsWindow.layout(this, ui);
            renderHexFields(ctx, mx, my, delta);
        }
        dropHiddenFocus();
        // Tooltips con la escala del organizador (en x3/x4 los de Minecraft salen enormes).
        if (ui.tooltip() != null) Ui.drawTooltip(ctx, ui.tooltip(), mx, my, vw, vh);
        else if (PanelState.preview != null) PreviewOverlay.tooltip(this, ctx, rmx, rmy);
        else if (PanelState.tutorialStep < 0 && !Guide.active() && !settingsVisible()) EggHover.render(this, ctx, rmx, rmy);
        popScale(ctx);
        ctx.getMatrices().pop();
    }

    static void clearDepth() {
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
    }

    /** Devuelve true si el click lo gestiona el organizador (la PC no lo recibe). */
    boolean mouseClicked(double rmx, double rmy, int button) {
        computeGeometry();
        double mx = rmx / k, my = rmy / k;
        // Ajustes es una ventana aparte: tapa todo lo demás mientras está abierta.
        if (settingsVisible()) {
            if (button == 0 && !(hexFieldsVisible() && focusFieldAt(mx, my, hexBaseField, hexAccentField))) {
                unfocusFields();
                SettingsWindow.layout(this, new Ui(Ui.Mode.CLICK, null, (int) mx, (int) my));
            }
            return true;
        }
        if (PanelState.tutorialStep >= 0) {
            if (button == 0) Tutorial.layout(this, new Ui(Ui.Mode.CLICK, null, (int) mx, (int) my));
            return true;
        }
        if (Guide.active()) {
            Ui gu = new Ui(Ui.Mode.CLICK, null, (int) mx, (int) my);
            if (button == 0) Guide.layout(this, gu);
            if (gu.consumed() || !Guide.active()) return true;
            // Solo se puede usar la zona que señala la guía.
            int[] f = Guide.focus(this);
            if (f == null || mx < f[0] || mx >= f[0] + f[2] || my < f[1] || my >= f[1] + f[3]) return true;
        }
        Ui ui = new Ui(Ui.Mode.CLICK, null, (int) mx, (int) my);
        if (Search.inPopup(this, mx, my)) {
            unfocusFields();
            if (button == 0) Search.popup(this, ui);
            return true;
        }
        // Click fuera de la ventanita de filtro: se cierra (salvo en el propio botón, que la alterna).
        if (Search.popupOpen && !(mx >= rFilter[0] && mx < rFilter[0] + rFilter[2] && my >= rFilter[1] && my < rFilter[1] + rFilter[3])) {
            Search.popupOpen = false;
        }
        if (inWin(PanelState.Win.ARRANGE, mx, my)) {
            unfocusFields();
            if (button == 0) ArrangeWindow.layout(this, ui);
            return true;
        }
        boolean inTop = inTopBar(mx, my), inSide = inSide(mx, my), inMove = inMovePanel(mx, my);
        if (!inTop && !inSide && !inMove) {
            unfocusFields();
            return PreviewOverlay.click(this, rmx, rmy, button);
        }
        if (button != 0) return true;
        if (inTop && searchShown && focusFieldAt(mx, my, searchField)) return true;
        if (inSide && nameFieldVisible()) {
            if (focusFieldAt(mx, my, nameField)) return true;
            if (SidePanel.speciesFieldShown && focusFieldAt(mx, my, speciesField)) return true;
        }
        unfocusFields();
        if (inTop) TopBar.layout(this, ui);
        else SidePanel.layout(this, ui);
        return true;
    }

    boolean mouseScrolled(double rmx, double rmy, double amount) {
        computeGeometry();
        double mx = rmx / k, my = rmy / k;
        if (settingsVisible() || Search.inPopup(this, mx, my)) return true;
        if (inTopBar(mx, my)) {
            int b = currentBox() + (amount < 0 ? 1 : -1);
            goToBox(Math.floorMod(b, boxCount()));
            return true;
        }
        if (!inSide(mx, my) && !inMovePanel(mx, my) && !inWin(PanelState.Win.SETTINGS, mx, my)
                && !inWin(PanelState.Win.ARRANGE, mx, my)) return false;
        for (Map.Entry<String, int[]> e : listRegions.entrySet()) {
            int[] r = e.getValue();
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                String key = e.getKey();
                int max = PanelState.scrollMax.getOrDefault(key, 0);
                int s = PanelState.scroll.getOrDefault(key, 0) + (int) (-amount * 14);
                PanelState.scroll.put(key, Math.max(0, Math.min(max, s)));
                return true;
            }
        }
        return true;
    }

    void unfocusFields() {
        for (TextFieldWidget f : allFields()) {
            f.setFocused(false);
            if (gui.getFocused() == f) gui.setFocused(null);
        }
    }

    /** Barra de título de una ventana flotante: arrastrar para moverla. Devuelve false si se cerró. */
    boolean windowFrame(Ui ui, PanelState.Win wnd, int[] r, String title, net.minecraft.item.ItemStack icon, Runnable close) {
        ui.panel(r[0], r[1], r[2], r[3]);
        ui.icon(icon, r[0] + 5, r[1] + 4, 12);
        ui.text(r[0] + 20, r[1] + 6, title, Ui.TEXT);
        if (ui.button(r[0] + r[2] - 16, r[1] + 4, 12, 12, "✕", Ui.Style.GHOST)) {
            close.run();
            return false;
        }
        ui.tooltip(r[0] + 4, r[1] + 2, r[2] - 24, 14, "Arrastra para mover la ventana");
        if (!ui.drawing() && !ui.consumed() && ui.hover(r[0], r[1], r[2] - 20, 18)) {
            PanelState.dragging = wnd;
            PanelState.dragDX = ui.mx - r[0];
            PanelState.dragDY = ui.my - r[1];
            ui.consume();
            return false;
        }
        ui.rect(r[0] + 4, r[1] + 18, r[2] - 8, 1, 0x26FFFFFF);
        return true;
    }

    // ------------------------------------------------------------------ listas con scroll

    int listBegin(Ui ui, String key, int lx, int ly, int lw, int lh) {
        listRegions.put(key, new int[]{lx, ly, lw, lh});
        int max = PanelState.scrollMax.getOrDefault(key, 0);
        int s = Math.max(0, Math.min(max, PanelState.scroll.getOrDefault(key, 0)));
        PanelState.scroll.put(key, s);
        ui.beginClip(lx, ly, lw, lh);
        return ly - s;
    }

    void listEnd(Ui ui, String key, int lx, int ly, int lw, int lh, int contentH) {
        ui.endClip();
        int max = Math.max(0, contentH - lh);
        PanelState.scrollMax.put(key, max);
        int s = Math.max(0, Math.min(max, PanelState.scroll.getOrDefault(key, 0)));
        PanelState.scroll.put(key, s);
        if (max > 0) {
            int barH = Math.max(8, lh * lh / Math.max(1, contentH));
            int barY = ly + (lh - barH) * s / max;
            ui.round(lx + lw - 3, ly, 3, lh, 0x22FFFFFF);
            ui.round(lx + lw - 3, barY, 3, barH, 0x90FFFFFF);
        }
    }

    // ------------------------------------------------------------------ acciones

    private Set<Integer> allBoxes() {
        Set<Integer> s = new TreeSet<>();
        for (int i = 0; i < boxCount(); i++) s.add(i);
        return s;
    }

    Plan computeOrganizePlan(PanelState.PreviewKind kind, int box) {
        refreshSnapshot();
        if (snapshot == null) return null;
        OrganizerConfig c = cfg();
        Set<Integer> scope = switch (kind) {
            case ORGANIZE_BOX -> Set.of(box);
            case ORGANIZE_CAPTURE -> new TreeSet<>(c.captureBoxes);
            default -> allBoxes();
        };
        return Planner.organize(new Planner.Request(snapshot, c.categories, scope, c.lockedOrBreed(),
                c.unsortedMode, c.unsortedBox, PanelState.overrides, c.evictStrays, c.captureBoxes, c.compact,
                c.captureSort, c.captureSort2));
    }

    Plan computeArrangePlan() {
        refreshSnapshot();
        if (snapshot == null) return null;
        return Planner.arrange(snapshot, PanelState.arrangeBoxes, PanelState.arrangeMode, PanelState.arrangeMode2, cfg().lockedOrBreed(),
                cfg().captureBoxes);
    }

    Plan computeMovePlan(List<Integer> targets) {
        refreshSnapshot();
        if (snapshot == null) return null;
        List<UUID> ordered = new ArrayList<>();
        for (UUID u : PanelState.selection) if (snapshot.positionOf(u) != null) ordered.add(u);
        Comparator<UUID> byPos = Comparator.comparingInt((UUID u) -> snapshot.positionOf(u).box())
                .thenComparingInt(u -> snapshot.positionOf(u).slot());
        ordered.sort(byPos);
        return Planner.moveAndArrange(snapshot, ordered, targets, cfg().lockedBoxes, PanelState.moveSort, PanelState.moveSort2,
                cfg().captureBoxes);
    }

    /** Parejas de la wiki a las cajas de crianza, en orden: crianza por crianza, P1 macho, P1 hembra, P2… */
    Plan computeBreedPlan() {
        refreshSnapshot();
        if (snapshot == null) return null;
        return Planner.placePairs(snapshot, com.diosesmon.organizerpc.cobblemon.WikiSync.pairs().slotPairs(),
                cfg().breedBoxes, cfg().lockedBoxes);
    }

    /** «Mover a las cajas de crianza»: vista previa y confirmación, como el resto. */
    void startBreedMove() {
        if (cfg().breedBoxes.isEmpty()) {
            PanelState.status("Elige tus cajas de crianza en Ajustes → Crianza.", Ui.GOLD);
            return;
        }
        if (com.diosesmon.organizerpc.cobblemon.WikiSync.pairs().isEmpty()) {
            PanelState.status("La wiki todavía no eligió parejas.", Ui.GOLD);
            return;
        }
        Plan plan = computeBreedPlan();
        if (plan == null) return;
        if (plan.isEmpty()) {
            PanelState.status(plan.noSpace().isEmpty() ? "Las parejas ya están en sus cajas de crianza." : "No caben más parejas en tus cajas de crianza.", Ui.GREEN);
            return;
        }
        PanelState.settingsOpen = false;
        openPreview(plan, PanelState.PreviewKind.BREED, cfg().breedBoxes.get(0));
        if (!plan.noSpace().isEmpty()) PanelState.status(plan.noSpace().size() + " no caben en las 2 cajas de crianza.", Ui.GOLD);
    }

    Plan recomputePreview() {
        if (PanelState.previewKind == PanelState.PreviewKind.BREED) return computeBreedPlan();
        if (PanelState.previewKind == PanelState.PreviewKind.MOVE) return computeMovePlan(PanelState.previewTargets);
        if (PanelState.previewKind == PanelState.PreviewKind.ARRANGE) return computeArrangePlan();
        return computeOrganizePlan(PanelState.previewKind, PanelState.previewBox);
    }

    /** Botones «Organizar PC», «Esta caja» y «Capturas». */
    void startOrganize(PanelState.PreviewKind kind) {
        OrganizerConfig c = cfg();
        int usable = 0;
        String noBoxes = null;
        Set<Integer> blocked = new TreeSet<>(c.lockedOrBreed());
        blocked.addAll(c.captureBoxes);
        for (Category cat : c.categories) {
            if (!cat.enabled || cat.activeConditions().isEmpty()) continue;
            if (cat.boxes(boxCount(), blocked).isEmpty()) {
                if (noBoxes == null) noBoxes = cat.name;
            } else {
                usable++;
            }
        }
        if (usable == 0 && c.unsortedMode == OrganizerConfig.UnsortedMode.KEEP) {
            if (noBoxes != null) PanelState.status("«" + noBoxes + "» no tiene cajas. Elígelas en la categoría.", Ui.GOLD);
            else PanelState.status("Primero crea una categoría.", Ui.GOLD);
            return;
        }
        int box = currentBox();
        if (kind == PanelState.PreviewKind.ORGANIZE_BOX && locked(box)) {
            PanelState.status("Esta caja está bloqueada.", Ui.GOLD);
            return;
        }
        if (kind == PanelState.PreviewKind.ORGANIZE_CAPTURE && c.captureBoxes.isEmpty()) {
            PanelState.status("Primero marca tus cajas de captura con el botón de la Poké Ball junto a la barra de cajas.", Ui.GOLD);
            return;
        }
        Plan plan = computeOrganizePlan(kind, box);
        if (plan == null) return;
        if (plan.isEmpty()) {
            if (plan.noSpace().isEmpty()) PanelState.status("Todo está ya en su sitio.", Ui.GREEN);
            else PanelState.status(plan.noSpace().size() + " Pokémon no caben. Añade más cajas a su categoría.", Ui.GOLD);
            return;
        }
        askIfNoSpace(plan, () -> openPreview(plan, kind, box));
    }

    /** Si algunos no caben, pregunta antes de seguir con los que sí caben. */
    void askIfNoSpace(Plan plan, Runnable go) {
        if (plan.noSpace().isEmpty()) {
            go.run();
            return;
        }
        int fit = plan.movedCount();
        PanelState.confirm = new PanelState.Confirm("No caben todos",
                "Hay sitio para " + fit + " Pokémon, pero " + plan.noSpace().size()
                        + " no caben y se quedarán donde están. ¿Seguir con los que caben?",
                "Seguir", go);
    }

    /** «Ordenar cajas»: junta y ordena los Pokémon de las cajas elegidas. */
    void startArrange() {
        if (PanelState.arrangeBoxes.isEmpty()) {
            PanelState.status("Elige primero las cajas que quieres ordenar.", Ui.GOLD);
            return;
        }
        Plan plan = computeArrangePlan();
        if (plan == null) return;
        if (plan.isEmpty()) {
            PanelState.status("Esas cajas ya están ordenadas.", Ui.GREEN);
            return;
        }
        PanelState.arrangeOpen = false;
        openPreview(plan, PanelState.PreviewKind.ARRANGE, PanelState.arrangeBoxes.get(0));
    }

    void openPreview(Plan plan, PanelState.PreviewKind kind, int box) {
        PanelState.previewOpenedAt = System.currentTimeMillis();
        PanelState.preview = plan;
        PanelState.previewKind = kind;
        PanelState.previewBox = box;
        PanelState.stripMode = PanelState.StripMode.NAVIGATE;
        PanelState.scroll.put("pv_in", 0);
        PanelState.scroll.put("pv_out", 0);
        if (kind == PanelState.PreviewKind.MOVE || kind == PanelState.PreviewKind.ORGANIZE_BOX || kind == PanelState.PreviewKind.ARRANGE
                || kind == PanelState.PreviewKind.BREED) {
            goToBox(box);
            return;
        }
        int first = nextChangedBox(-1, 1);
        if (first >= 0) goToBox(first);
    }

    int nextChangedBox(int from, int dir) {
        Plan plan = PanelState.preview;
        if (plan == null) return -1;
        int n = boxCount();
        for (int i = 1; i <= n; i++) {
            int b = Math.floorMod(from + dir * i, n);
            if (PlanIndex.of(plan).changed(b)) return b;
        }
        return -1;
    }

    void cancelPreview() {
        PanelState.preview = null;
        PanelState.overrides.clear();
        PanelState.status("Cancelado. No se movió nada.", Ui.TEXT_DIM);
    }

    void confirmPreview() {
        if (MoveExecutor.isRunning() || PanelState.preview == null) return;
        if (!PanelState.confirmArmed()) return; // doble click desde «Mover N…»: no mover sin ver la vista previa
        Plan planned = PanelState.preview;
        Plan fresh = recomputePreview();
        if (fresh == null) return;
        if (!fresh.operations().equals(planned.operations())) {
            PanelState.preview = fresh;
            PanelState.status("La PC cambió: revisa la vista previa otra vez.", Ui.GOLD);
            return;
        }
        if (fresh.isEmpty()) {
            PanelState.preview = null;
            PanelState.status("Nada que mover.", Ui.TEXT_DIM);
            return;
        }
        PanelState.PreviewKind kind = PanelState.previewKind;
        List<UUID> moved = new ArrayList<>();
        for (Plan.Entry e : fresh.entries()) moved.add(e.uuid());
        PanelState.runStartedAt = System.currentTimeMillis();
        MoveExecutor.start(pc(), fresh.operations(), () -> {
            MoveExecutor done = MoveExecutor.current();
            PanelState.preview = null;
            if (done.status() == MoveExecutor.Status.DONE) {
                PanelState.overrides.clear();
                if (kind == PanelState.PreviewKind.MOVE) {
                    // Tras mover se restablece la pestaña Mover.
                    moved.forEach(PanelState.selection::remove);
                    PanelState.moveBoxes.clear();
                    PanelState.moveFilters.clear();
                    PanelState.moveDest = PanelState.MoveDest.HERE;
                }
                if (kind == PanelState.PreviewKind.ARRANGE) {
                    PanelState.arrangeBoxes.clear();
                    PanelState.arrangeCats.clear();
                }
            }
            refreshSnapshot();
            boolean ok = done.status() == MoveExecutor.Status.DONE;
            PanelState.status(ok ? "Listo: " + moved.size() + " Pokémon movidos." : done.message(), ok ? Ui.GREEN : Ui.GOLD);
        });
    }

    void cycleOverride(Plan.Entry e) {
        List<String> options = new ArrayList<>(e.matchingCategoryIds());
        options.add(Planner.NO_CATEGORY);
        String current = PanelState.overrides.getOrDefault(e.uuid(), e.categoryId() == null ? Planner.NO_CATEGORY : e.categoryId());
        int i = options.indexOf(current);
        PanelState.overrides.put(e.uuid(), options.get((i + 1) % options.size()));
        Plan np = recomputePreview();
        if (np != null) PanelState.preview = np;
    }

    // ------------------------------------------------------------------ ventanas

    void openMoveWindow() {
        PanelState.moveOpen = true;
        if (PanelState.moveTarget < 0 || PanelState.moveTarget >= boxCount()) PanelState.moveTarget = currentBox();
        if (PanelState.editing != null) finishEditing();
        PanelState.stripMode = PanelState.StripMode.NAVIGATE;
    }

    void closeMoveWindow() {
        PanelState.moveOpen = false;
    }

    Category categoryById(String id) {
        if (id == null) return null;
        for (Category c : cfg().categories) if (c.id.equals(id)) return c;
        return null;
    }

    /** Cajas destino según el modo elegido en la ventana Mover. */
    List<Integer> moveTargets() {
        Set<Integer> blocked = new TreeSet<>(cfg().lockedBoxes);
        blocked.addAll(cfg().captureBoxes);
        return switch (PanelState.moveDest) {
            case HERE -> List.of(currentBox());
            case BOX -> {
                List<Integer> out = new ArrayList<>();
                for (int b : PanelState.moveBoxes) if (b >= 0 && b < boxCount() && !locked(b)) out.add(b);
                yield out;
            }
            case CAPTURE -> {
                List<Integer> out = new ArrayList<>();
                for (int b : cfg().captureBoxes) if (b < boxCount() && !locked(b)) out.add(b);
                yield out;
            }
            case CATEGORY -> {
                Category c = categoryById(PanelState.moveCategoryId);
                yield c == null ? List.of() : c.boxes(boxCount(), blocked);
            }
        };
    }

    String moveDestLabel() {
        return switch (PanelState.moveDest) {
            case HERE -> "aquí (caja " + (currentBox() + 1) + ")";
            case BOX -> PanelState.moveBoxes.size() == 1 ? "a la caja " + (PanelState.moveBoxes.get(0) + 1)
                    : "a " + PanelState.moveBoxes.size() + " cajas";
            case CAPTURE -> "a las cajas de captura";
            case CATEGORY -> {
                Category c = categoryById(PanelState.moveCategoryId);
                yield c == null ? "a su categoría" : "a «" + c.name + "»";
            }
        };
    }

    /** Marca con los filtros elegidos (reemplaza lo marcado antes; se pueden combinar varios). */
    void applyMoveFilters() {
        refreshSnapshot();
        PanelState.selection.clear();
        if (snapshot == null || PanelState.moveFilters.isEmpty()) return;
        List<Category> filters = new ArrayList<>();
        for (String id : PanelState.moveFilters) {
            Category c = categoryById(id);
            if (c != null) filters.add(c);
        }
        for (int b = 0; b < snapshot.boxCount(); b++) {
            if (locked(b)) continue;
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                UUID u = snapshot.at(b, s);
                if (u == null) continue;
                PokemonInfo info = snapshot.info(u);
                boolean ok = PanelState.moveFilterAll;
                for (Category c : filters) {
                    boolean m = c.matches(info);
                    if (PanelState.moveFilterAll && !m) { ok = false; break; }
                    if (!PanelState.moveFilterAll && m) { ok = true; break; }
                }
                if (ok) PanelState.selection.add(u);
            }
        }
        // Con un solo filtro que tiene cajas, el destino pasa a ser su categoría.
        if (filters.size() == 1 && !filters.get(0).boxes.isEmpty()) {
            PanelState.moveDest = PanelState.MoveDest.CATEGORY;
            PanelState.moveCategoryId = filters.get(0).id;
        }
        PanelState.status(PanelState.selection.size() + " marcados con " + filters.size() + (filters.size() == 1 ? " filtro." : " filtros."), Ui.TEXT_DIM);
    }

    void moveMarked() {
        if (PanelState.selection.isEmpty()) {
            PanelState.status("Haz click en Pokémon de la caja para marcarlos.", Ui.GOLD);
            return;
        }
        List<Integer> targets = moveTargets();
        if (targets.isEmpty()) {
            PanelState.status("Ese destino no tiene cajas libres (sin cajas, bloqueadas o de captura).", Ui.GOLD);
            return;
        }
        if (targets.size() == 1 && locked(targets.get(0))) {
            PanelState.status("La caja " + (targets.get(0) + 1) + " está bloqueada.", Ui.GOLD);
            return;
        }
        Plan plan = computeMovePlan(targets);
        if (plan == null) return;
        if (plan.isEmpty()) {
            PanelState.status(plan.noSpace().isEmpty() ? "Ya están en su destino." : "No queda sitio en el destino.", Ui.GOLD);
            return;
        }
        PanelState.previewTargets = targets;
        askIfNoSpace(plan, () -> openPreview(plan, PanelState.PreviewKind.MOVE, targets.get(0)));
    }

    void markWholeBox() {
        if (snapshot == null) return;
        int box = currentBox();
        if (locked(box)) {
            PanelState.status("Esta caja está bloqueada.", Ui.GOLD);
            return;
        }
        int n = 0;
        for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
            UUID u = snapshot.at(box, s);
            if (u != null && PanelState.selection.add(u)) n++;
        }
        PanelState.status(n + " marcados en esta caja.", Ui.TEXT_DIM);
    }

    void markByCategory(Category c) {
        refreshSnapshot();
        if (snapshot == null) return;
        int n = 0;
        for (int b = 0; b < snapshot.boxCount(); b++) {
            if (locked(b)) continue;
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                UUID u = snapshot.at(b, s);
                if (u != null && c.matches(snapshot.info(u)) && PanelState.selection.add(u)) n++;
            }
        }
        PanelState.status(n + " marcados de «" + c.name + "».", Ui.TEXT_DIM);
    }

    void toggleSelection(UUID uuid) {
        if (!PanelState.selection.remove(uuid)) PanelState.selection.add(uuid);
    }

    /** Click en una caja de la barra según el modo. */
    void stripClick(int box) {
        switch (PanelState.stripMode) {
            case NAVIGATE -> goToBox(box);
            case LOCK -> {
                if (!cfg().lockedBoxes.remove(box)) cfg().lockedBoxes.add(box);
                OrganizerPCClient.saveConfig();
            }
            case CAPTURE -> {
                if (!cfg().captureBoxes.remove(box)) cfg().captureBoxes.add(box);
                OrganizerPCClient.saveConfig();
            }
            case ARRANGE -> {
                if (locked(box)) {
                    PanelState.status("La caja " + (box + 1) + " está bloqueada.", Ui.GOLD);
                    return;
                }
                if (!PanelState.arrangeBoxes.remove((Integer) box)) PanelState.arrangeBoxes.add(box);
            }
            case ASSIGN -> {
                Category c = PanelState.editing;
                if (c == null) {
                    PanelState.stripMode = PanelState.StripMode.NAVIGATE;
                    return;
                }
                if (locked(box)) {
                    PanelState.status("La caja " + (box + 1) + " está bloqueada.", Ui.GOLD);
                    return;
                }
                if (capture(box)) {
                    PanelState.status("La caja " + (box + 1) + " es de captura: no puede ser destino.", Ui.GOLD);
                    return;
                }
                if (!c.boxes.remove(box)) c.boxes.add(box);
                goToBox(box);
            }
        }
    }

    // ------------------------------------------------------------------ categorías

    Category newCategory() {
        Category cat = new Category();
        cat.color = Category.PALETTE[(cfg().categories.size() * 5) % Category.PALETTE.length];
        cat.name = "Categoría " + (cfg().categories.size() + 1);
        cfg().categories.add(cat);
        OrganizerPCClient.saveConfig();
        startEditing(cat);
        return cat;
    }

    void addIvTemplate() {
        List<Integer> free = new ArrayList<>();
        for (int b = 0; b < boxCount() && free.size() < 7; b++) if (ownerOf(b) == null && !locked(b) && !capture(b)) free.add(b);
        int[] colors = {0xFF90A4AE, 0xFF9CCC65, 0xFF26C6A0, 0xFF29B6F6, 0xFFB07CFF, 0xFFFF4F9A, 0xFFFFC857};
        for (int k = 0; k <= 6; k++) {
            Category cat = new Category();
            cat.name = k + "x31";
            cat.color = colors[k];
            if (k < free.size()) cat.boxes.add(free.get(k));
            PerfectIvCountCondition p = cat.condition(ConditionType.PERFECT_IV_COUNT);
            p.op = Comparison.EQ;
            p.count = k;
            cfg().categories.add(cat);
        }
        OrganizerPCClient.saveConfig();
        recountMatches();
        PanelState.status(free.size() < 7 ? "Creadas 7 categorías. Algunas no tienen caja: asígnalas." : "Creadas 0x31 … 6x31, cada una en una caja libre.", free.size() < 7 ? Ui.GOLD : Ui.GREEN);
    }

    void startEditing(Category cat) {
        PanelState.editing = cat;
        PanelState.deleteArmed = false;
        PanelState.colorPicker = false;
        PanelState.stripMode = cat.boxes.isEmpty() ? PanelState.StripMode.ASSIGN : PanelState.StripMode.NAVIGATE;
        nameField.setText(cat.name);
        speciesField.setText("");
        PanelState.scroll.put("editor", 0);
        recountMatches();
    }

    void finishEditing() {
        Category cat = PanelState.editing;
        if (cat != null && cat.name.isBlank()) cat.name = "Sin nombre";
        PanelState.editing = null;
        PanelState.colorPicker = false;
        PanelState.stripMode = PanelState.StripMode.NAVIGATE;
        unfocusFields();
        OrganizerPCClient.saveConfig();
        recountMatches();
    }

    static String boxLabel(Category cat, int index, int total) {
        String base = cat.name.trim();
        if (total <= 1) return base.length() > 19 ? base.substring(0, 19) : base;
        String suffix = " " + (index + 1);
        int max = 19 - suffix.length();
        return (base.length() > max ? base.substring(0, max) : base) + suffix;
    }

    void askRenameBoxes() {
        int n = 0;
        for (Category cat : cfg().categories) n += cat.boxes(boxCount(), Set.of()).size();
        if (n == 0) {
            PanelState.status("Ninguna categoría tiene cajas todavía.", Ui.GOLD);
            return;
        }
        PanelState.settingsOpen = false;
        PanelState.confirm = new PanelState.Confirm("Poner nombre a las cajas",
                "Cada caja de una categoría pasará a llamarse como ella (por ejemplo «6x31»). Son " + n + " cajas. No mueve Pokémon.",
                "Renombrar", this::renameBoxes);
    }

    void renameBoxes() {
        int n = 0;
        for (Category cat : cfg().categories) {
            List<Integer> boxes = cat.boxes(boxCount(), Set.of());
            for (int k = 0; k < boxes.size(); k++) {
                MoveExecutor.renameBox(pc(), boxes.get(k), boxLabel(cat, k, boxes.size()));
                n++;
            }
        }
        PanelState.status(n + " cajas renombradas.", Ui.GREEN);
    }
}
