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
    /** Parejas de crianza marcadas a mano (P1, P2…): solo ayuda visual. */
    public final List<BreedingPair> pairs = new ArrayList<>();
    /** Pokémon marcados «No usar en la página»: nunca salen en los breeders para la wiki. */
    public final java.util.Set<java.util.UUID> noWiki = new java.util.LinkedHashSet<>();
    /** Contar a los Ditto como breeders para la wiki (por defecto no). */
    public boolean breedDitto = false;
    /** No dar a la wiki como breeders las especies muy raras (iniciales, fósiles…): no se quieren gastar en crianza. */
    public boolean breedNoUltra = true;
    /** Cajas bloqueadas con borde y tono rojo (No = solo el candado). */
    public boolean lockTint = true;
    /** Bordes visuales en la PC: legendarios (dorado), shinys (arcoíris) y muy raros (morado). */
    public boolean borderLegend = false, borderShiny = false, borderUltra = false;
    /** Cajas de crianza (hasta 2): ahí van las parejas de la wiki; organizar y ordenar no las tocan. */
    public final List<Integer> breedBoxes = new ArrayList<>();
    /** Cajas de cada crianza de la wiki (por su id): cada crianza puede ir a sus propias cajas. */
    public final java.util.Map<String, List<Integer>> breedBoxesBy = new java.util.LinkedHashMap<>();
    /** Crianza elegida para mover a las cajas («» = todas, a las cajas de crianza generales). */
    public String breedSel = "";

    /** Cajas de una crianza («» = las generales). Se crea vacía la primera vez. */
    public List<Integer> boxesFor(String crianzaId) {
        if (crianzaId == null || crianzaId.isEmpty()) return breedBoxes;
        return breedBoxesBy.computeIfAbsent(crianzaId, k -> new ArrayList<>());
    }

    /** Bloqueadas más las de crianza: lo que organizar y ordenar no deben tocar. */
    public java.util.Set<Integer> lockedOrBreed() {
        java.util.Set<Integer> s = new TreeSet<>(lockedBoxes);
        s.addAll(breedBoxes);
        for (List<Integer> l : breedBoxesBy.values()) s.addAll(l);
        return s;
    }

    /** Pareja a la que pertenece un Pokémon, o null. */
    public BreedingPair pairOf(java.util.UUID uuid) {
        if (uuid == null) return null;
        for (BreedingPair p : pairs) if (p.members.contains(uuid)) return p;
        return null;
    }
}
