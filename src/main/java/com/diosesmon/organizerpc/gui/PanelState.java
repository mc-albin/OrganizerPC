package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.plan.Plan;
import com.diosesmon.organizerpc.core.plan.Planner;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Estado de la interfaz que sobrevive a redimensionar o reabrir la PC durante la sesión. */
final class PanelState {
    /** Qué hace un click en la barra de cajas. */
    enum StripMode { NAVIGATE, ASSIGN, LOCK, CAPTURE, ARRANGE }

    enum PreviewKind { ORGANIZE_ALL, ORGANIZE_BOX, ORGANIZE_CAPTURE, MOVE, ARRANGE, BREED }

    /** Ventanas flotantes (arrastrables). */
    enum Win { SETTINGS, ARRANGE }

    /** Adónde mueve la ventana Mover. */
    enum MoveDest { HERE, BOX, CATEGORY, CAPTURE }

    record Confirm(String title, String text, String yes, Runnable onYes) {
    }

    static StripMode stripMode = StripMode.NAVIGATE;

    static Category editing;
    static boolean deleteArmed;
    static boolean colorPicker;

    static Plan preview;
    static PreviewKind previewKind;
    static int previewBox;
    static java.util.List<Integer> previewTargets = java.util.List.of();
    static final Map<UUID, String> overrides = new HashMap<>();

    static Confirm confirm;

    // Ventanas flotantes.
    static boolean moveOpen;
    static boolean settingsOpen;
    static boolean arrangeOpen;
    static final Map<Win, int[]> winPos = new HashMap<>();
    static Win dragging;
    static int dragDX, dragDY;

    // Mover Pokémon.
    static int moveTarget = -1;
    static MoveDest moveDest = MoveDest.HERE;
    static String moveCategoryId;
    /** Categorías usadas como filtro para marcar (se pueden combinar). */
    static final Set<String> moveFilters = new java.util.LinkedHashSet<>();
    /** true: deben cumplir todos los filtros elegidos; false: basta con uno. */
    static boolean moveFilterAll;
    /** Cajas destino elegidas a mano (se llenan en este orden). */
    static final java.util.List<Integer> moveBoxes = new java.util.ArrayList<>();
    /** Ordenar las cajas destino al mover. */
    static com.diosesmon.organizerpc.core.SortMode moveSort = com.diosesmon.organizerpc.core.SortMode.NONE;
    static com.diosesmon.organizerpc.core.SortMode moveSort2 = com.diosesmon.organizerpc.core.SortMode.NONE;

    // Ordenar cajas.
    static final java.util.List<Integer> arrangeBoxes = new java.util.ArrayList<>();
    static final Set<String> arrangeCats = new java.util.LinkedHashSet<>();
    static com.diosesmon.organizerpc.core.SortMode arrangeMode = com.diosesmon.organizerpc.core.SortMode.NONE;
    static com.diosesmon.organizerpc.core.SortMode arrangeMode2 = com.diosesmon.organizerpc.core.SortMode.NONE;
    static final Set<UUID> selection = new LinkedHashSet<>();
    static Planner.SortModeManual sortMode = Planner.SortModeManual.IVS_DESC;

    static final Map<String, Integer> scroll = new HashMap<>();
    static final Map<String, Integer> scrollMax = new HashMap<>();

    static String status = "";
    static int statusColor = Ui.TEXT_DIM;
    static int statusTicks;

    /** Paso del tutorial; -1 = cerrado. */
    static int tutorialStep = -1;
    /** Menú de tutoriales abierto (elegir tutorial general o crear categoría). */
    static boolean tutorialMenu;
    /** Paso de la guía «Crear una categoría»; -1 = cerrada, 0 = pregunta «¿Qué quieres filtrar?». */
    static int guideStep = -1;
    static Guide.Kind guideKind = Guide.Kind.IV_COUNT;

    /** Modo Parejas: click en un Pokémon lo mete o saca de la pareja elegida. */
    static boolean pairMode;
    /** Número de la pareja elegida (P1 = 1); 0 = ninguna todavía. */
    static int selectedPair;
    /** Pareja con «Borrar» pulsado una vez (pide confirmación). */
    static int deletePairArmed;
    /** Ya se avisó en esta sesión de que hay una versión nueva. */
    static boolean updateNotified;
    /** Pestaña abierta en Ajustes. */
    static int settingsTab = 0;
    /** Momento (ms) en que empezó a mover / se abrió la vista previa: evita clics dobles accidentales. */
    static long runStartedAt, previewOpenedAt;

    /** Detener solo responde pasado un momento desde que empezó a mover (evita pulsarlo sin querer). */
    static boolean stopArmed() {
        return System.currentTimeMillis() - runStartedAt > 900;
    }

    /** «Mover» de la vista previa solo responde pasado un momento desde que se abrió. */
    static boolean confirmArmed() {
        return System.currentTimeMillis() - previewOpenedAt > 450;
    }

    static void status(String s, int color) {
        status = s;
        statusColor = color;
        statusTicks = 160;
    }

    private PanelState() {
    }
}
