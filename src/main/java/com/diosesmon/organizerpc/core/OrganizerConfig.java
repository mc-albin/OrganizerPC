package com.diosesmon.organizerpc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Configuración persistente del usuario. */
public final class OrganizerConfig {
    public enum UnsortedMode { KEEP, MOVE_TO_BOX }

    public final List<Category> categories = new ArrayList<>();
    /** Cajas bloqueadas: el organizador no saca ni mete Pokémon en ellas. */
    public final TreeSet<Integer> lockedBoxes = new TreeSet<>();
    /** Cajas de captura: sus Pokémon se reparten en las categorías y nunca reciben Pokémon. */
    public final TreeSet<Integer> captureBoxes = new TreeSet<>();
    public UnsortedMode unsortedMode = UnsortedMode.KEEP;
    public int unsortedBox = 0;
    public boolean showIvBadges = true;
    public boolean highlightCategory = true;
    public boolean tutorialDone = false;
    /** Comando que envía el botón AFK (sin la barra). */
    public String afkCommand = "afk";
    /** Organizador oculto (solo queda un botón pequeño en la PC). */
    public boolean hidden = false;
    /** Con el organizador oculto, seguir mostrando las marcas de IVs en la caja. */
    public boolean ivMarksWhenHidden = false;
    /** Ventanita con el grupo huevo del Pokémon bajo el ratón (también con el organizador oculto). */
    public boolean eggGroupHover = true;
    /** Sacar de las cajas de una categoría a los Pokémon que no la cumplen. */
    public boolean evictStrays = true;
    /** Al organizar, colocar los Pokémon seguidos (sin huecos) en las cajas de categoría y de captura. */
    public boolean compact = true;
    /** Tamaño de los puntos de IVs en cada Pokémon: 0 pequeño, 1 mediano, 2 grande. */
    public int ivMarkSize = 0;
    /** Orden de las cajas de captura (se aplica con «Capturas» y al mover hacia ellas). */
    public SortMode captureSort = SortMode.NONE;
    public SortMode captureSort2 = SortMode.NONE;
    /** Tema: color base de los paneles y color de acento (ARGB). Por defecto, gris de la PC con turquesa. */
    public int themeBase = 0xFF4E545A;
    public int themeAccent = 0xFF3DC2CC;
    /** Tamaño de la interfaz: 0 automático, 1-4 píxeles por unidad (como la escala de GUI). */
    public int uiSize = 0;
    /** Marcas de IVs en el equipo que se ve en pantalla fuera de la PC. */
    public boolean ivMarksParty = true;
    /** Panel derecho (Categorías / Mover) plegado: queda solo una pestañita en el borde. */
    public boolean sidePanelCollapsed = false;
}
