package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.StatKey;
import com.diosesmon.organizerpc.core.plan.PcModel;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Búsqueda de la barra de arriba: ilumina las cajas (y los Pokémon de la caja abierta) que
 * cumplen. Texto libre (especie, grupo huevo, «ps31», «vel0», «3x31») y un filtro con grupos
 * huevo, IV de cada estadística y número de 31. Todo se combina: deben cumplir todo.
 */
final class Search {
    /** Grupos huevo del filtro: basta con pertenecer a uno. */
    static final Set<EggGroupKey> groups = EnumSet.noneOf(EggGroupKey.class);
    /** IV pedido por estadística: -1 da igual, 31 o 0. */
    static final int[] stats = {-1, -1, -1, -1, -1, -1};
    /** Número exacto de 31; -1 da igual. */
    static int count = -1;
    /** true: únicamente de los grupos marcados (sin grupos extra); false: basta con uno. */
    static boolean eggOnly;
    /** Géneros del filtro (PokemonInfo.MALE…); vacío = da igual. */
    static final Set<Integer> genders = new java.util.TreeSet<>();
    /** Solo shinys. */
    static boolean shinyOnly;
    /** Tamaños del filtro (nombres del mod de tamaños: Tiny, Small…); vacío = da igual. */
    static final Set<String> sizes = new java.util.LinkedHashSet<>();
    static String text = "";
    static boolean popupOpen;

    private static String cacheKey;
    private static PcModel cacheModel;
    private static int[] boxHits = new int[0];
    private static final Set<UUID> hits = new HashSet<>();
    private static int total;

    private Search() {
    }

    static boolean filterActive() {
        if (!groups.isEmpty() || count >= 0 || !genders.isEmpty() || shinyOnly || !sizes.isEmpty()) return true;
        for (int v : stats) if (v >= 0) return true;
        return false;
    }

    static boolean active() {
        return !text.isBlank() || filterActive();
    }

    static void clear() {
        groups.clear();
        genders.clear();
        sizes.clear();
        shinyOnly = false;
        eggOnly = false;
        java.util.Arrays.fill(stats, -1);
        count = -1;
        text = "";
    }

    static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ texto

    private sealed interface Term permits StatTerm, CountTerm, GroupTerm, NameTerm, DexTerm, GenderTerm, ShinyTerm, SizeTerm, PairTerm {
    }

    private record StatTerm(StatKey stat, int value) implements Term {
    }

    private record CountTerm(int count) implements Term {
    }

    /** Basta con pertenecer a uno de los grupos («agua» = Agua 1, 2 o 3). */
    private record GroupTerm(Set<EggGroupKey> groups) implements Term {
    }

    private record NameTerm(String text) implements Term {
    }

    private record GenderTerm(int gender) implements Term {
    }

    private record ShinyTerm() implements Term {
    }

    /** Pareja de crianza «p1», «p2»… */
    private record PairTerm(int number) implements Term {
    }

    private record SizeTerm(String size) implements Term {
    }

    /** Número de Pokédex (o rango «1-151»). Vale cualquier número de la Pokédex. */
    private record DexTerm(int from, int to) implements Term {
    }

    private static StatKey stat(String s) {
        return switch (s) {
            case "ps", "hp" -> StatKey.HP;
            case "ata", "atk", "at", "ataque" -> StatKey.ATTACK;
            case "def", "defensa" -> StatKey.DEFENCE;
            case "ate", "spa", "sat", "ataesp", "atesp" -> StatKey.SPECIAL_ATTACK;
            case "dfe", "spd", "sdf", "defesp", "dfesp" -> StatKey.SPECIAL_DEFENCE;
            case "vel", "spe", "speed", "velocidad" -> StatKey.SPEED;
            default -> null;
        };
    }

    private static Set<EggGroupKey> group(String token) {
        if (token.length() < 3) return null;
        for (EggGroupKey g : EggGroupKey.values()) {
            String es = norm(g.label).replace(" ", "");
            String en = g.name().toLowerCase(Locale.ROOT).replace("_", "");
            if (es.equals(token) || en.equals(token)) return EnumSet.of(g);
        }
        // Prefijo («hum» → Humanoide, «agua» → Agua 1, 2 y 3).
        Set<EggGroupKey> found = EnumSet.noneOf(EggGroupKey.class);
        for (EggGroupKey g : EggGroupKey.values()) {
            String es = norm(g.label).replace(" ", "");
            String en = g.name().toLowerCase(Locale.ROOT).replace("_", "");
            if ((es.startsWith(token) || en.startsWith(token)) && g != EggGroupKey.DITTO) found.add(g);
        }
        return found.isEmpty() ? null : found;
    }

    static List<Term> parse(String raw) {
        String q = norm(raw).replaceAll("agua\\s+([123])", "agua$1").replaceAll("water\\s+([123])", "water$1")
                .replaceAll("human\\s+like", "humanlike").replaceAll("sin\\s+genero", "singenero").replaceAll("([a-z]+)\\s*[:=]\\s*(31|0)\\b", "$1$2");
        List<Term> out = new ArrayList<>();
        for (String t : q.split("[\\s,;]+")) {
            if (t.isEmpty()) continue;
            var m = java.util.regex.Pattern.compile("^([a-z]+)(31|0)$").matcher(t);
            if (m.matches() && stat(m.group(1)) != null) {
                out.add(new StatTerm(stat(m.group(1)), Integer.parseInt(m.group(2))));
                continue;
            }
            var d = java.util.regex.Pattern.compile("^#?([0-9]{1,5})(?:-#?([0-9]{1,5}))?$").matcher(t);
            if (d.matches()) {
                int a = Integer.parseInt(d.group(1)), b = d.group(2) == null ? a : Integer.parseInt(d.group(2));
                out.add(new DexTerm(Math.min(a, b), Math.max(a, b)));
                continue;
            }
            var c = java.util.regex.Pattern.compile("^([0-6])x(31)?$").matcher(t);
            if (c.matches()) {
                out.add(new CountTerm(Integer.parseInt(c.group(1))));
                continue;
            }
            int gen = switch (t) {
                case "macho", "male", "♂" -> PokemonInfo.MALE;
                case "hembra", "female", "♀" -> PokemonInfo.FEMALE;
                case "singenero", "genderless", "asexual" -> PokemonInfo.GENDERLESS;
                default -> 0;
            };
            if (gen != 0) {
                out.add(new GenderTerm(gen));
                continue;
            }
            var pm = java.util.regex.Pattern.compile("^p([0-9]{1,2})$").matcher(t);
            if (pm.matches()) {
                out.add(new PairTerm(Integer.parseInt(pm.group(1))));
                continue;
            }
            if (t.equals("shiny") || t.equals("variocolor")) {
                out.add(new ShinyTerm());
                continue;
            }
            String size = SizeVariation.byToken(t);
            if (size != null) {
                out.add(new SizeTerm(size));
                continue;
            }
            Set<EggGroupKey> g = group(t);
            if (g != null) {
                out.add(new GroupTerm(g));
                continue;
            }
            out.add(new NameTerm(t));
        }
        return out;
    }

    /** Descripción corta de lo que entendió la búsqueda (para el tooltip). */
    static List<String> explain() {
        List<String> l = new ArrayList<>();
        for (Term t : parse(text)) {
            l.add(switch (t) {
                case StatTerm s -> "· " + s.stat().shortLabel + " en " + s.value();
                case CountTerm c -> "· " + c.count() + "x31";
                case GroupTerm g -> "· Grupo huevo " + String.join(" o ", g.groups().stream().map(k -> k.label).toList());
                case NameTerm n -> "· Nombre contiene " + n.text() + "";
                case DexTerm d -> d.from() == d.to() ? "· Pokédex nº " + d.from() : "· Pokédex del " + d.from() + " al " + d.to();
                case GenderTerm g -> "· " + genderLabel(g.gender());
                case ShinyTerm s -> "· Shiny";
                case PairTerm pt -> "· Pareja P" + pt.number();
                case SizeTerm z -> "· Tamaño " + z.size();
            });
        }
        if (!groups.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (EggGroupKey g : groups) names.add(g.label);
            l.add("· Filtro: " + (eggOnly ? "únicamente " + String.join(" y ", names) : String.join(" o ", names)));
        }
        if (shinyOnly) l.add("· Filtro: shiny");
        if (!sizes.isEmpty()) l.add("· Filtro: tamaño " + String.join(" o ", sizes));
        if (!genders.isEmpty()) {
            List<String> gl = new ArrayList<>();
            for (int g : genders) gl.add(genderLabel(g));
            l.add("· Filtro: " + String.join(" o ", gl));
        }
        for (StatKey k : StatKey.values()) if (stats[k.ordinal()] >= 0) l.add("· Filtro: " + k.shortLabel + " en " + stats[k.ordinal()]);
        if (count >= 0) l.add("· Filtro: " + count + "x31");
        return l;
    }

    private static boolean matches(PokemonInfo p, List<Term> terms) {
        if (!groups.isEmpty()) {
            if (eggOnly ? p.eggGroups().isEmpty() || !groups.containsAll(p.eggGroups()) : Collections.disjoint(groups, p.eggGroups())) return false;
        }
        if (!genders.isEmpty() && !genders.contains(p.gender())) return false;
        if (wantsSex(terms) && com.diosesmon.organizerpc.cobblemon.SpeciesTraits.singleGender(p.speciesId())) {
            singleHidden.add(SidePanel.speciesName(p.speciesId()));
            return false;
        }
        if (shinyOnly && !p.shiny()) return false;
        if (!sizes.isEmpty() && !sizes.contains(SizeVariation.sizeOf(p.scale()))) return false;
        for (StatKey k : StatKey.values()) {
            int want = stats[k.ordinal()];
            if (want >= 0 && p.iv(k) != want) return false;
        }
        if (count >= 0 && p.perfectIvCount() != count) return false;
        String name = null;
        for (Term t : terms) {
            boolean ok = switch (t) {
                case StatTerm s -> p.iv(s.stat()) == s.value();
                case CountTerm c -> p.perfectIvCount() == c.count();
                case GroupTerm g -> !Collections.disjoint(g.groups(), p.eggGroups());
                case NameTerm n -> {
                    if (name == null) name = norm(p.name()) + " " + norm(SidePanel.speciesName(p.speciesId())) + " " + p.speciesId();
                    yield name.contains(n.text());
                }
                case DexTerm d -> p.dex() >= d.from() && p.dex() <= d.to();
                case GenderTerm g -> p.gender() == g.gender();
                case ShinyTerm s -> p.shiny();
                case PairTerm pt -> {
                    var bp = com.diosesmon.organizerpc.OrganizerPCClient.config().pairOf(p.uuid());
                    yield bp != null && bp.number == pt.number();
                }
                case SizeTerm z -> z.size().equals(SizeVariation.sizeOf(p.scale()));
            };
            if (!ok) return false;
        }
        return true;
    }

    /** Especies de un solo sexo que el filtro quitó (para avisar). */
    static final Set<String> singleHidden = new java.util.TreeSet<>();

    /** Se busca macho o hembra: para criar, una especie de un solo sexo (Jynx, Tauros…) no sirve para pasar el sexo. */
    private static boolean wantsSex(List<Term> terms) {
        if (genders.contains(PokemonInfo.MALE) || genders.contains(PokemonInfo.FEMALE)) return true;
        for (Term t : terms) if (t instanceof GenderTerm g && (g.gender() == PokemonInfo.MALE || g.gender() == PokemonInfo.FEMALE)) return true;
        return false;
    }

    private static String key() {
        int pairsHash = 0;
        for (var bp : com.diosesmon.organizerpc.OrganizerPCClient.config().pairs) pairsHash = pairsHash * 31 + bp.number * 7 + bp.members.hashCode();
        return pairsHash + "|" + text + "|" + groups + "|" + eggOnly + "|" + genders + "|" + shinyOnly + "|" + sizes + "|" + java.util.Arrays.toString(stats) + "|" + count;
    }

    static String genderLabel(int g) {
        return switch (g) {
            case PokemonInfo.MALE -> "Macho";
            case PokemonInfo.FEMALE -> "Hembra";
            case PokemonInfo.GENDERLESS -> "Sin género";
            default -> "Género desconocido";
        };
    }

    /** Recalcula los resultados si cambió la búsqueda o la PC. */
    static void refresh(PcModel model) {
        String k = key();
        if (model == cacheModel && k.equals(cacheKey)) return;
        cacheModel = model;
        cacheKey = k;
        hits.clear();
        singleHidden.clear();
        total = 0;
        boxHits = new int[model == null ? 0 : model.boxCount()];
        if (model == null || !active()) return;
        List<Term> terms = parse(text);
        for (int b = 0; b < model.boxCount(); b++) {
            for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                UUID u = model.at(b, s);
                if (u == null) continue;
                PokemonInfo info = model.info(u);
                if (info != null && matches(info, terms)) {
                    hits.add(u);
                    boxHits[b]++;
                    total++;
                }
            }
        }
    }

    static int boxHits(int box) {
        return box >= 0 && box < boxHits.length ? boxHits[box] : 0;
    }

    static boolean hit(UUID u) {
        return hits.contains(u);
    }

    static int total() {
        return total;
    }

    /** Siguiente caja (en círculo) con resultados, o -1. */
    static int nextBox(int from) {
        int n = boxHits.length;
        for (int i = 1; i <= n; i++) {
            int b = Math.floorMod(from + i, n);
            if (boxHits[b] > 0) return b;
        }
        return -1;
    }

    // ------------------------------------------------------------------ ventanita de filtro

    static final int POP_W = 214, POP_H = 248;

    /** Posición de la ventanita: debajo del botón de filtro, sin salirse de la pantalla. */
    static int[] popupRect(OrganizerPanel p) {
        int[] b = p.rFilter;
        int x = Math.max(2, Math.min(p.vw - POP_W - 2, b[0] + b[2] - POP_W));
        int y = b[1] + b[3] + 3;
        if (y + POP_H > p.vh - 2) y = Math.max(2, p.vh - POP_H - 2);
        return new int[]{x, y, POP_W, POP_H};
    }

    static boolean inPopup(OrganizerPanel p, double mx, double my) {
        if (!popupOpen) return false;
        int[] r = popupRect(p);
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    static void popup(OrganizerPanel p, Ui ui) {
        int[] r = popupRect(p);
        int x = r[0], y = r[1], w = r[2], h = r[3];
        ui.panel(x, y, w, h);
        int lx = x + 6, iw = w - 12;
        ui.icon(Icons.FILTER, lx, y + 4, 11);
        ui.text(lx + 14, y + 6, "Filtrar", Ui.TEXT);
        if (ui.button(x + w - 16, y + 4, 12, 12, "", Ui.Style.GHOST)) popupOpen = false;
        ui.cross(x + w - 13, y + 7, 6, Ui.TEXT_DIM);
        ui.rect(x + 4, y + 18, w - 8, 1, 0x26FFFFFF);
        int ry = y + 22;

        ui.small(lx, ry, "Grupo huevo", Ui.TEXT_DIM);
        // Alguno: basta con uno de los marcados. Solo estos: todos sus grupos deben estar marcados.
        int mw = 52;
        if (ui.chip(x + w - 6 - 2 * mw - 2, ry - 2, mw, 10, "Alguno", !eggOnly, Ui.BLUE)) eggOnly = false;
        ui.tooltip(x + w - 6 - 2 * mw - 2, ry - 2, mw, 10, "Entra si es de alguno de los grupos marcados.");
        if (ui.chip(x + w - 6 - mw, ry - 2, mw, 10, "Solo estos", eggOnly, Ui.BLUE)) eggOnly = true;
        ui.tooltip(x + w - 6 - mw, ry - 2, mw, 10, "Únicamente de esos grupos: con solo Campo,", "nada de Campo + Hada ni otros extra.");
        ry += 11;
        EggGroupKey[] gs = EggGroupKey.values();
        int gw = (iw - 4) / 3;
        for (int i = 0; i < gs.length; i++) {
            int bx = lx + (i % 3) * (gw + 2), by = ry + (i / 3) * 12;
            if (ui.chip(bx, by, i % 3 == 2 ? iw - 2 * (gw + 2) : gw, 11, gs[i].label, groups.contains(gs[i]), Ui.PURPLE)) {
                if (!groups.remove(gs[i])) groups.add(gs[i]);
            }
        }
        ry += ((gs.length + 2) / 3) * 12 + 4;

        ui.small(lx, ry, "Género", Ui.TEXT_DIM);
        ry += 9;
        String[] gl = {"Macho", "Hembra", "Sin género"};
        int[] gv = {PokemonInfo.MALE, PokemonInfo.FEMALE, PokemonInfo.GENDERLESS};
        int[] gc = {Ui.BLUE, Ui.PINK, Ui.TEXT_DIM};
        for (int i = 0; i < 3; i++) {
            int bx = lx + i * (gw + 2);
            if (ui.chip(bx, ry, i == 2 ? iw - 2 * (gw + 2) : gw, 11, gl[i], genders.contains(gv[i]), gc[i])) {
                if (!genders.remove((Integer) gv[i])) genders.add(gv[i]);
            }
        }
        ry += 15;

        ui.small(lx, ry, "Shiny y tamaño", Ui.TEXT_DIM);
        ry += 9;
        List<SizeVariation.Size> svs = SizeVariation.sizes();
        int cols = 4, sw2 = (iw - (cols - 1) * 2) / cols;
        if (ui.chip(lx, ry, sw2, 11, "Shiny", shinyOnly, Ui.GOLD)) shinyOnly = !shinyOnly;
        ui.tooltip(lx, ry, sw2, 11, "Solo Pokémon shiny.");
        for (int i = 0; i < svs.size(); i++) {
            int k = i + 1;
            int bx = lx + (k % cols) * (sw2 + 2), by = ry + (k / cols) * 12;
            SizeVariation.Size z = svs.get(i);
            if (ui.chip(bx, by, k % cols == cols - 1 ? iw - (cols - 1) * (sw2 + 2) : sw2, 11, z.name(), sizes.contains(z.name()), z.color())) {
                if (!sizes.remove(z.name())) sizes.add(z.name());
            }
            ui.tooltip(bx, by, sw2, 11, "Tamaño " + z.name(), "Modificador de " + z.min() + " a " + z.max() + ".");
        }
        ry += ((svs.size() + 1 + cols - 1) / cols) * 12 + 3;

        ui.small(lx, ry, "IV de cada estadística (click: — / 31 / 0)", Ui.TEXT_DIM);
        ry += 9;
        StatKey[] ks = StatKey.values();
        int sw = (iw - 10) / 6;
        for (int i = 0; i < 6; i++) {
            int bx = lx + i * (sw + 2);
            int v = stats[i];
            int col = Icons.statColor(ks[i]);
            boolean hov = ui.hover(bx, ry, sw, 22);
            ui.box(bx, ry, sw, 22, v == 31 ? Ui.mix(Ui.BTN, Ui.GREEN, 0.45F) : v == 0 ? Ui.mix(Ui.BTN, Ui.RED, 0.45F) : hov ? Ui.BTN_HOVER : Ui.BTN,
                    v == 31 ? Ui.GREEN : v == 0 ? Ui.RED : hov ? Ui.BORDER_HOVER : Ui.BORDER);
            ui.rect(bx + 3, ry + 3, sw - 6, 1, col);
            ui.textCenter(bx + sw / 2 + 1, ry + 5, ks[i].shortLabel, Ui.TEXT);
            String vs = v < 0 ? "—" : String.valueOf(v);
            ui.small(bx + (sw - ui.smallWidth(vs)) / 2 + 1, ry + 14, vs, v < 0 ? Ui.TEXT_DIM : Ui.TEXT_ON);
            ui.tooltip(bx, ry, sw, 22, "IV de " + ks[i].shortLabel, "Click: da igual → 31 → 0");
            if (ui.area(bx, ry, sw, 22)) stats[i] = v < 0 ? 31 : v == 31 ? 0 : -1;
        }
        ry += 26;

        ui.small(lx, ry, "¿Cuántos 31?", Ui.TEXT_DIM);
        ry += 9;
        int cw = (iw - 14) / 8;
        for (int k = -1; k <= 6; k++) {
            int bx = lx + (k + 1) * (cw + 2);
            if (ui.chip(bx, ry, k == 6 ? iw - 7 * (cw + 2) : cw, 12, k < 0 ? "—" : k + "", count == k, Ui.GOLD)) count = k;
            ui.tooltip(bx, ry, cw, 12, k < 0 ? "Da igual" : "Exactamente " + k + "x31");
        }
        ry += 16;
        if (!singleHidden.isEmpty()) {
            String names = String.join(", ", singleHidden);
            ui.fit(lx, ry, iw, "Sin un solo sexo: " + names, Ui.PINK, false);
            ui.tooltip(lx, ry - 1, iw, 9, "No salen: " + names, "Solo pueden ser de un sexo y romperían la cadena.");
        } else {
            ui.small(lx, ry, "Nº Pokédex: escribe 25 o 1-151 en Buscar", Ui.TEXT_DIM);
        }

        int hw = (iw - 3) / 2;
        if (ui.button(lx, y + h - 20, hw, 15, "Limpiar", Ui.Style.NORMAL)) {
            clear();
            p.searchField.setText("");
        }
        ui.tooltip(lx, y + h - 20, hw, 15, "Quita la búsqueda y el filtro.");
        if (ui.button(lx + hw + 3, y + h - 20, iw - hw - 3, 15, "Listo", Ui.Style.PRIMARY)) popupOpen = false;
    }
}
