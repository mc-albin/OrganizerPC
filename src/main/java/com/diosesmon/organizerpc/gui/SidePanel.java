package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.cobblemon.EggGroupData;
import com.diosesmon.organizerpc.cobblemon.MoveExecutor;
import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.SortMode;
import com.diosesmon.organizerpc.core.StatKey;
import com.diosesmon.organizerpc.core.filter.Comparison;
import com.diosesmon.organizerpc.core.filter.ConditionType;
import com.diosesmon.organizerpc.core.filter.EggGroupCondition;
import com.diosesmon.organizerpc.core.filter.IvPatternCondition;
import com.diosesmon.organizerpc.core.filter.PerfectIvCountCondition;
import com.diosesmon.organizerpc.core.filter.SpeciesCondition;
import com.diosesmon.organizerpc.core.plan.PcModel;
import com.diosesmon.organizerpc.core.plan.Plan;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Panel de categorías a la derecha de la PC; también muestra la vista previa y las confirmaciones. */
final class SidePanel {
    static final int PAD = 6;
    /** La caja de búsqueda de especies está visible este frame. */
    static boolean speciesFieldShown;

    private record SpeciesHit(String id, String name) {
    }

    private static String lastQuery = null;
    private static List<SpeciesHit> lastHits = List.of();
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

    private SidePanel() {
    }

    static void layout(OrganizerPanel p, Ui ui) {
        speciesFieldShown = false;
        if (p.sideCollapsed()) {
            int[] t = p.sideTab();
            boolean hov = ui.hover(t[0], t[1], t[2], t[3]);
            ui.box(t[0], t[1], t[2] + 4, t[3], hov ? Ui.BTN_HOVER : Ui.BG, hov ? Ui.THEME : Ui.BORDER);
            ui.textCenter(t[0] + t[2] / 2 + 1, t[1] + t[3] / 2 - 4, "", hov ? Ui.TEXT : Ui.TEXT_DIM);
            ui.tooltip(t[0], t[1], t[2], t[3], "Mostrar el panel", "(Categorías y Mover)");
            if (ui.area(t[0], t[1], t[2], t[3])) {
                p.cfg().sidePanelCollapsed = false;
                OrganizerPCClient.saveConfig();
            }
            return;
        }
        int x = p.sx, y = p.sy, w = p.sw, h = p.sh;
        ui.panel(x, y, w, h);
        boolean special = PanelState.preview != null || PanelState.confirm != null || MoveExecutor.isRunning();
        if (special) {
            ui.icon(PanelState.preview != null ? Icons.PC : Icons.TAG, x + PAD, y + 5, 12);
            ui.text(x + PAD + 16, y + 7, PanelState.preview != null ? "Vista previa" : MoveExecutor.isRunning() ? "Moviendo" : "Confirmar", Ui.TEXT);
        } else {
            // Pestañas: mismo recuadro y mismo tamaño para las dos.
            int tw = (w - PAD * 2 - 2) / 2;
            boolean move = PanelState.moveOpen;
            if (ui.button(x + PAD, y + 3, tw, 16, "Categorías", Icons.TAG, move ? Ui.Style.NORMAL : Ui.Style.SELECTED)) {
                if (move) p.closeMoveWindow();
            }
            p.rMove = new int[]{x + PAD + tw + 2, y + 3, tw, 16};
            if (ui.button(x + PAD + tw + 2, y + 3, tw, 16, "Mover", Icons.MOVE, move ? Ui.Style.SELECTED : Ui.Style.NORMAL)) {
                if (!move) p.openMoveWindow();
            }
            ui.tooltip(x + PAD + tw + 2, y + 3, tw, 16, "Marca Pokémon y muévelos a otra caja.");
        }
        // Ocultar: cinta que sobresale por el borde izquierdo del panel, como la de un libro.
        if (!special) {
            int[] t = p.hideTab();
            boolean th = ui.hover(t[0], t[1], t[2], t[3]);
            ui.box(t[0], t[1], t[2] + 3, t[3], th ? Ui.BTN_HOVER : Ui.BG, th ? Ui.THEME : Ui.BORDER);
            ui.rect(t[0] + 2, t[1] + 1, 1, t[3] - 2, Ui.alpha(Ui.THEME, th ? 0xFF : 0x90));
            ui.textCenter(t[0] + t[2] / 2 + 2, t[1] + t[3] / 2 - 4, "", th ? Ui.TEXT : Ui.TEXT_DIM);
            ui.tooltip(t[0], t[1], t[2], t[3], "Ocultar el panel", "Queda una pestañita en el borde derecho para volver.");
            if (ui.area(t[0], t[1], t[2], t[3])) {
                p.cfg().sidePanelCollapsed = true;
                p.unfocusFields();
                OrganizerPCClient.saveConfig();
            }
        }
        ui.rect(x + 5, y + 21, w - 10, 1, 0x26FFFFFF);

        int iw = w - PAD * 2;
        int lx = x + PAD;
        int cy = y + 26;
        int bottom = y + h - 24;

        MoveExecutor ex = MoveExecutor.current();
        if (ex != null && ex.status() == MoveExecutor.Status.RUNNING) {
            running(ui, ex, lx, cy, iw, bottom);
        } else if (PanelState.confirm != null) {
            confirm(ui, lx, cy, iw, bottom);
        } else if (PanelState.preview != null) {
            preview(p, ui, lx, cy, iw, bottom);
        } else if (PanelState.moveOpen) {
            MoveWindow.layout(p, ui, lx, cy, iw, bottom);
        } else if (PanelState.editing != null) {
            editor(p, ui, lx, cy, iw, bottom);
        } else {
            categories(p, ui, lx, cy, iw, bottom);
        }

        if (!PanelState.status.isEmpty()) {
            ui.rect(x + 5, bottom + 2, w - 10, 1, 0x26FFFFFF);
            ui.beginClip(x, bottom + 3, w, 20);
            ui.wrapped(lx, bottom + 5, iw, PanelState.status, PanelState.statusColor);
            ui.endClip();
        }
    }

    /** Título de sección con icono. Devuelve la altura usada. */
    static int header(Ui ui, int x, int y, int w, ItemStack icon, String text) {
        int tx = x;
        if (icon != null) {
            ui.icon(icon, x, y - 1, 10);
            tx += 13;
        }
        ui.text(tx, y, text, Ui.TEXT);
        ui.rect(x, y + 10, w, 1, 0x26FFFFFF);
        return 14;
    }

    /** Interruptor tipo móvil. */
    static boolean toggle(Ui ui, int x, int y, boolean on) {
        int col = on ? Ui.GREEN : Ui.LINE;
        ui.round(x, y, 18, 9, Ui.alpha(col, on ? 0xC0 : 0xFF));
        ui.round(on ? x + 10 : x + 1, y + 1, 7, 7, 0xFFF2F3F5);
        return ui.area(x, y, 18, 9);
    }

    // ------------------------------------------------------------------ ejecución / confirmación

    private static void running(Ui ui, MoveExecutor ex, int lx, int cy, int iw, int bottom) {
        ui.icon(Icons.MOVE, lx, cy, 12);
        ui.text(lx + 15, cy + 2, "Moviendo Pokémon", Ui.TEXT);
        ui.round(lx, cy + 20, iw, 7, Ui.LINE);
        int fill = ex.total() == 0 ? iw : iw * ex.done() / ex.total();
        if (fill >= 4) ui.round(lx, cy + 20, fill, 7, Ui.GREEN);
        ui.text(lx, cy + 32, ex.done() + " de " + ex.total(), Ui.TEXT_DIM);
        ui.wrapped(lx, cy + 48, iw, "El servidor confirma cada paso. No cierres la PC.", Ui.TEXT_DIM);
        // Detener a la derecha (donde estaba «Cancelar», no donde estaba «Mover») y sin responder
        // durante el primer momento: un doble click en «Mover» ya no lo pulsa sin querer.
        int hw = (iw - 3) / 2;
        boolean armed = PanelState.stopArmed();
        if (ui.button(lx + hw + 3, bottom - 18, iw - hw - 3, 16, "Detener", armed ? Ui.Style.DANGER : Ui.Style.NORMAL) && armed) {
            ex.requestStop();
            PanelState.status("Deteniendo…", Ui.GOLD);
        }
        ui.tooltip(lx + hw + 3, bottom - 18, iw - hw - 3, 16, "Para después del paso actual.", "Lo ya movido se queda donde está.");
    }

    private static void confirm(Ui ui, int lx, int cy, int iw, int bottom) {
        PanelState.Confirm c = PanelState.confirm;
        ui.text(lx, cy + 2, c.title(), Ui.GOLD);
        ui.wrapped(lx, cy + 16, iw, c.text(), Ui.TEXT);
        int hw = (iw - 3) / 2;
        if (ui.button(lx, bottom - 18, hw, 16, c.yes(), Ui.Style.PRIMARY)) {
            PanelState.confirm = null;
            c.onYes().run();
        }
        if (ui.button(lx + hw + 3, bottom - 18, iw - hw - 3, 16, "Cancelar", Ui.Style.DANGER)) PanelState.confirm = null;
    }

    // ------------------------------------------------------------------ vista previa

    private static void preview(OrganizerPanel p, Ui ui, int lx, int cy, int iw, int bottom) {
        Plan plan = PanelState.preview;
        String what = switch (PanelState.previewKind) {
            case MOVE -> "Mover a la caja " + (PanelState.previewBox + 1);
            case ORGANIZE_BOX -> "Organizar la caja " + (PanelState.previewBox + 1);
            case ORGANIZE_CAPTURE -> "Repartir las cajas de captura";
            case ORGANIZE_ALL -> "Organizar toda la PC";
            case ARRANGE -> "Ordenar " + PanelState.arrangeBoxes.size() + " cajas";
            case BREED -> "Parejas a las cajas de crianza";
        };
        ui.small(lx, cy, what + " · " + plan.movedCount() + " cambian", Ui.TEXT_DIM);
        cy += 9;
        if (!plan.noSpace().isEmpty()) {
            String ns = plan.noSpace().size() + " no caben y se quedan";
            ui.small(lx, cy, ns, Ui.GOLD);
            List<String> tip = new ArrayList<>();
            for (int i = 0; i < Math.min(12, plan.noSpace().size()); i++) tip.add("· " + plan.noSpace().get(i).name());
            if (plan.noSpace().size() > 12) tip.add("… y " + (plan.noSpace().size() - 12) + " más");
            tip.add("Añade otra caja a su categoría.");
            ui.tooltipLines(lx, cy, iw, 8, tip);
            cy += 9;
        }
        cy += 3;

        int box = p.currentBox();
        ui.section(lx, cy, iw, 18);
        if (ui.button(lx + 2, cy + 2, 14, 14, "◀", Ui.Style.GHOST)) {
            int b = p.nextChangedBox(box, -1);
            if (b >= 0) p.goToBox(b);
        }
        ui.tooltip(lx + 2, cy + 2, 14, 14, "Caja anterior con cambios");
        if (ui.button(lx + iw - 16, cy + 2, 14, 14, "▶", Ui.Style.GHOST)) {
            int b = p.nextChangedBox(box, 1);
            if (b >= 0) p.goToBox(b);
        }
        ui.tooltip(lx + iw - 16, cy + 2, 14, 14, "Siguiente caja con cambios");
        ui.textCenter(lx + iw / 2, cy + 5, "Caja " + (box + 1), Ui.TEXT);
        cy += 22;

        List<Plan.Entry> in = new ArrayList<>(), out = new ArrayList<>();
        int sorted = 0;
        for (Plan.Entry e : plan.entries()) {
            if (e.to().box() == box && e.from().box() != box) in.add(e);
            if (e.from().box() == box && e.to().box() != box) out.add(e);
            if (e.from().box() == box && e.to().box() == box) sorted++;
        }
        if (sorted > 0) {
            ui.small(lx, cy, sorted + " cambian de hueco para quedar ordenados", Ui.BLUE);
            cy += 9;
        }
        int avail = bottom - 22 - cy;
        int half = (avail - 4) / 2;
        cy += pokemonGroup(p, ui, lx, cy, iw, half, "Llegan", Ui.GREEN, in, true, "pv_in") + 4;
        pokemonGroup(p, ui, lx, cy, iw, half, "Se van", Ui.RED, out, false, "pv_out");

        int hw = (iw - 3) / 2;
        if (ui.button(lx, bottom - 18, hw, 16, "Mover", Icons.MOVE, PanelState.confirmArmed() ? Ui.Style.PRIMARY : Ui.Style.NORMAL)) p.confirmPreview();
        if (ui.button(lx + hw + 3, bottom - 18, iw - hw - 3, 16, "Cancelar", Ui.Style.DANGER)) p.cancelPreview();
        if (!PanelState.overrides.isEmpty() && PanelState.previewKind != PanelState.PreviewKind.MOVE) {
            String undo = "Deshacer ⇄ (" + PanelState.overrides.size() + ")";
            if (ui.button(lx, bottom - 33, iw, 13, undo, Ui.Style.GHOST)) {
                PanelState.overrides.clear();
                Plan np = p.recomputePreview();
                if (np != null) PanelState.preview = np;
            }
        }
    }

    private static String groupReason(List<Plan.Entry> entries, boolean arriving) {
        if (entries.isEmpty()) return null;
        Plan.Reason r = entries.get(0).reason();
        String left = entries.get(0).leftCategoryName();
        for (Plan.Entry e : entries) {
            if (e.reason() != r) return null;
            if (r == Plan.Reason.EVICTED && left != null && !left.equals(e.leftCategoryName())) left = null;
        }
        return switch (r) {
            case EVICTED -> arriving ? "Sacados de cajas de otra categoría"
                    : (left == null ? "No cumplen la categoría de esta caja" : "No cumplen " + left + "");
            case UNSORTED_BOX -> "Sin categoría (Ajustes)";
            case CATEGORY -> arriving ? null : "Van a su categoría";
            default -> null;
        };
    }

    private static int pokemonGroup(OrganizerPanel p, Ui ui, int lx, int cy, int iw, int h, String title, int color,
                                    List<Plan.Entry> entries, boolean arriving, String key) {
        ui.section(lx, cy, iw, h);
        ui.round(lx + 4, cy + 4, 5, 5, color);
        ui.text(lx + 12, cy + 3, title + " (" + entries.size() + ")", Ui.TEXT);
        int gy = cy + 14, gh = h - 16;
        String why = groupReason(entries, arriving);
        if (why != null) {
            ui.smallFit(lx + 5, gy, iw - 10, why, Ui.ORANGE);
            ui.tooltip(lx + 2, gy - 1, iw - 4, 8, why);
            gy += 9;
            gh -= 9;
        }
        if (entries.isEmpty()) {
            ui.small(lx + 5, gy + 2, arriving ? "Nadie llega a esta caja" : "Nadie se va de esta caja", Ui.TEXT_DIM);
            return h;
        }
        int cell = PokemonIcon.SIZE;
        int cols = Math.max(1, (iw - 8) / (cell + 1));
        int top = p.listBegin(ui, key, lx + 2, gy, iw - 4, gh);
        for (int i = 0; i < entries.size(); i++) {
            Plan.Entry e = entries.get(i);
            int ex = lx + 3 + (i % cols) * (cell + 1), ey = top + (i / cols) * (cell + 1);
            ui.round(ex, ey, cell, cell, Ui.ITEM);
            if (ui.drawing() && ey + cell > gy && ey < gy + gh) {
                Pokemon mon = p.pokemon(e.uuid());
                PokemonIcon.draw(ui.ctx, p.gui.getStorage(), mon, ex, ey, p.delta);
            }
            String lbl = arriving ? "C" + (e.from().box() + 1) : "C" + (e.to().box() + 1);
            ui.rect(ex, ey + cell - 7, ui.smallWidth(lbl) + 2, 7, 0xC0000000);
            ui.small(ex + 1, ey + cell - 6, lbl, PreviewOverlay.reasonColor(e));
            if (ui.wantsTooltip(ex, ey, cell, cell)) ui.tooltipText(ex, ey, cell, cell, PreviewOverlay.describe(p, e.info(), e));
        }
        int rows = (entries.size() + cols - 1) / cols;
        p.listEnd(ui, key, lx + 2, gy, iw - 4, gh, rows * (cell + 1));
        return h;
    }

    // ------------------------------------------------------------------ categorías

    private static void categories(OrganizerPanel p, Ui ui, int lx, int cy, int iw, int bottom) {
        OrganizerConfig c = p.cfg();
        int listBottom = bottom - 38;
        if (c.categories.isEmpty()) {
            ui.section(lx, cy, iw, listBottom - cy);
            int yy = cy + 8;
            ui.icon(Icons.TAG, lx + iw / 2 - 8, yy, 16);
            yy += 22;
            yy += ui.wrapped(lx + 6, yy, iw - 12, "Una categoría dice qué Pokémon van juntos y en qué cajas.", Ui.TEXT_DIM) + 4;
            ui.wrapped(lx + 6, yy, iw - 12, "Crea una o usa la plantilla de IVs.", Ui.TEXT_DIM);
        } else {
            ui.small(lx, cy, "Arriba = más prioridad · click para editar", Ui.TEXT_DIM);
            cy += 10;
            int lh = listBottom - cy;
            int top = p.listBegin(ui, "cats", lx, cy, iw, lh);
            int ry = top;
            for (int i = 0; i < c.categories.size(); i++) {
                Category cat = c.categories.get(i);
                int rh = 25;
                int rw = iw - 4;
                boolean hov = ui.hover(lx, ry, rw, rh - 3);
                ui.box(lx, ry, rw, rh - 3, hov ? Ui.ITEM : Ui.SECTION, hov ? Ui.BORDER_HOVER : Ui.LINE);
                ui.round(lx + 4, ry + 4, 4, rh - 11, cat.color);
                int textW = rw - 12 - 24 - (hov ? 26 : 0);
                ui.textTrim(lx + 12, ry + 3, textW, cat.name, cat.enabled ? Ui.TEXT : Ui.TEXT_DIM);
                String sub = (cat.boxes.isEmpty() ? "sin cajas" : cat.boxesLabel().replace("C", "cajas ").replace(",", ", "))
                        + " · " + p.matchCounts.getOrDefault(cat.id, 0) + " Pokémon";
                ui.smallFit(lx + 12, ry + 13, textW, sub, cat.boxes.isEmpty() ? Ui.GOLD : Ui.TEXT_DIM);
                List<String> tip = new ArrayList<>();
                tip.add(cat.name);
                for (var cond : cat.activeConditions()) tip.add("· " + cond.describe());
                if (cat.activeConditions().isEmpty()) tip.add("· Sin filtros todavía");
                if (cat.sortMode != SortMode.NONE) tip.add("· Orden: " + cat.sortMode.label);
                tip.add(p.matchCounts.getOrDefault(cat.id, 0) + " Pokémon de tu PC cumplen");
                ui.tooltipLines(lx, ry, textW + 12, rh - 3, tip);
                if (hov) {
                    int ax = lx + rw - 22 - 26;
                    if (ui.button(ax, ry + 5, 12, 12, "▲", Ui.Style.GHOST) && i > 0) {
                        c.categories.add(i - 1, c.categories.remove(i));
                        OrganizerPCClient.saveConfig();
                    }
                    if (ui.button(ax + 13, ry + 5, 12, 12, "▼", Ui.Style.GHOST) && i < c.categories.size() - 1) {
                        c.categories.add(i + 1, c.categories.remove(i));
                        OrganizerPCClient.saveConfig();
                    }
                }
                if (toggle(ui, lx + rw - 22, ry + 7, cat.enabled)) {
                    cat.enabled = !cat.enabled;
                    OrganizerPCClient.saveConfig();
                }
                ui.tooltip(lx + rw - 22, ry + 7, 18, 9, cat.enabled ? "Activa" : "Apagada (no se usa)");
                if (ui.area(lx, ry, textW + 12, rh - 3)) p.startEditing(cat);
                ry += rh;
            }
            p.listEnd(ui, "cats", lx, cy, iw, lh, ry - top);
        }

        int by = bottom - 35;
        if (ui.button(lx, by, iw, 17, "Nueva categoría", Icons.PLUS, Ui.Style.PRIMARY)) p.newCategory();
        by += 20;
        if (ui.button(lx, by, iw, 15, "Plantilla 0x31 … 6x31", Icons.IVS, Ui.Style.NORMAL)) {
            PanelState.confirm = new PanelState.Confirm("Plantilla de IVs",
                    "Crea 7 categorías (0x31, 1x31 … 6x31) y pone cada una en una caja libre. No mueve Pokémon.",
                    "Crear", p::addIvTemplate);
        }
    }

    // ------------------------------------------------------------------ especies

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Nombre traducido de una especie a partir de su identificador. */
    static String speciesName(String id) {
        return NAME_CACHE.computeIfAbsent(id, k -> {
            try {
                Species s = PokemonSpecies.getByIdentifier(Identifier.of(k));
                if (s != null) return s.getTranslatedName().getString();
            } catch (Exception ignored) {
            }
            return SpeciesCondition.pretty(k);
        });
    }

    private static List<SpeciesHit> search(String query) {
        if (query.equals(lastQuery)) return lastHits;
        lastQuery = query;
        List<SpeciesHit> hits = new ArrayList<>();
        String q = norm(query.trim());
        if (q.length() >= 2) {
            for (Species s : PokemonSpecies.getImplemented()) {
                String id = s.getResourceIdentifier().toString();
                String name = s.getTranslatedName().getString();
                if (norm(name).contains(q) || id.contains(q)) hits.add(new SpeciesHit(id, name));
                if (hits.size() >= 6) break;
            }
        }
        lastHits = hits;
        return hits;
    }

    private static int speciesSection(OrganizerPanel p, Ui ui, Category cat, int lx, int r, int cw, int clipTop, int clipBottom) {
        int start = r;
        r += header(ui, lx, r, cw, Icons.POKE_BALL, "Especies");
        SpeciesCondition sp = cat.condition(ConditionType.SPECIES);
        if (sp.species.isEmpty()) {
            ui.small(lx, r, "Todas (o elige solo algunas)", Ui.TEXT_DIM);
            r += 10;
        } else {
            int bx = lx;
            String toRemove = null;
            for (String id : sp.species) {
                String name = speciesName(id);
                int pw = Math.min(cw, ui.width(name) + 14);
                if (bx + pw > lx + cw) {
                    bx = lx;
                    r += 13;
                }
                boolean hov = ui.hover(bx, r, pw, 11);
                ui.box(bx, r, pw, 11, hov ? Ui.alpha(Ui.RED, 0x60) : Ui.alpha(cat.color, 0x55), hov ? Ui.RED : cat.color);
                ui.textTrim(bx + 3, r + 2, pw - 12, name, 0xFFFFFFFF);
                ui.text(bx + pw - 8, r + 2, "✕", hov ? 0xFFFFFFFF : 0x90FFFFFF);
                ui.tooltip(bx, r, pw, 11, name, "Click: quitar");
                if (ui.area(bx, r, pw, 11)) toRemove = id;
                bx += pw + 2;
            }
            if (toRemove != null) {
                sp.remove(toRemove);
                p.recountMatches();
            }
            r += 14;
        }
        // Buscador.
        ui.box(lx, r, cw, 14, Ui.FIELD, p.speciesField.isFocused() ? Ui.BLUE : Ui.BORDER);
        p.speciesField.setX(lx + 4);
        p.speciesField.setY(r + 3);
        p.speciesField.setWidth(cw - 8);
        speciesFieldShown = r + 3 >= clipTop && r + 13 <= clipBottom;
        if (p.speciesField.getText().isEmpty() && !p.speciesField.isFocused()) ui.text(lx + 4, r + 3, "Buscar especie…", Ui.TEXT_DIM);
        r += 17;
        String q = p.speciesField.getText();
        for (SpeciesHit hit : search(q)) {
            boolean has = sp.contains(hit.id());
            ui.box(lx, r, cw, 13, Ui.ITEM, Ui.LINE);
            ui.textTrim(lx + 4, r + 3, cw - 90, hit.name(), has ? Ui.GREEN : Ui.TEXT);
            if (ui.button(lx + cw - 84, r + 1, 30, 11, has ? "✔" : "+", has ? Ui.Style.SELECTED : Ui.Style.NORMAL) && !has) {
                sp.add(hit.id());
                p.recountMatches();
            }
            ui.tooltip(lx + cw - 84, r + 1, 30, 11, "Añadir solo " + hit.name());
            if (ui.button(lx + cw - 52, r + 1, 50, 11, "+ familia", Ui.Style.NORMAL)) {
                int n = 0;
                for (String id : EggGroupData.family(hit.id())) {
                    if (!sp.contains(id)) n++;
                    sp.add(id);
                }
                p.recountMatches();
                PanelState.status("Añadidas " + n + " especies de la familia de " + hit.name() + ".", Ui.GREEN);
            }
            ui.tooltip(lx + cw - 52, r + 1, 50, 11, "Añade " + hit.name() + " y toda su familia", "evolutiva (por ejemplo Slowpoke, Slowbro, Slowking).");
            r += 15;
        }
        int hw = (cw - 3) / 2;
        if (ui.button(lx, r, hw, 13, "De esta caja", Icons.BOX, Ui.Style.NORMAL)) {
            int n = 0;
            if (p.snapshot != null) {
                int box = p.currentBox();
                for (int s = 0; s < PcModel.SLOTS_PER_BOX; s++) {
                    UUID u = p.snapshot.at(box, s);
                    if (u == null) continue;
                    String id = p.snapshot.info(u).speciesId();
                    if (!id.isEmpty() && !sp.contains(id)) {
                        sp.add(id);
                        n++;
                    }
                }
            }
            p.recountMatches();
            PanelState.status("Añadidas " + n + " especies de la caja " + (p.currentBox() + 1) + ".", Ui.GREEN);
        }
        ui.tooltip(lx, r, hw, 13, "Añade todas las especies que hay", "en la caja que estás viendo.");
        if (!sp.species.isEmpty() && ui.button(lx + hw + 3, r, cw - hw - 3, 13, "Quitar todas", Ui.Style.NORMAL)) {
            sp.species.clear();
            p.recountMatches();
        }
        r += 16;
        return r - start;
    }

    // ------------------------------------------------------------------ editor

    private static void editor(OrganizerPanel p, Ui ui, int lx, int cy, int iw, int bottom) {
        Category cat = PanelState.editing;
        p.gName = new int[]{lx, cy, iw, 16};
        ui.box(lx, cy, iw - 20, 16, Ui.FIELD, p.nameField.isFocused() ? Ui.BLUE : Ui.BORDER);
        p.nameField.setX(lx + 4);
        p.nameField.setY(cy + 4);
        p.nameField.setWidth(iw - 28);
        if (ui.button(lx + iw - 17, cy, 17, 16, "", PanelState.colorPicker ? Ui.Style.SELECTED : Ui.Style.NORMAL)) {
            PanelState.colorPicker = !PanelState.colorPicker;
        }
        ui.round(lx + iw - 13, cy + 4, 9, 8, cat.color);
        ui.tooltip(lx + iw - 17, cy, 17, 16, "Elegir color");
        cy += 20;

        int footer = bottom - 17;
        int lh = footer - cy - 3;
        int top = p.listBegin(ui, "editor", lx, cy, iw, lh);
        int r = top;
        p.editorTop = top;
        p.editorList = new int[]{lx, cy, iw, lh};
        int s0;
        int cw = iw - 5;
        boolean changed = false;

        // Paleta de colores.
        if (PanelState.colorPicker) {
            int cols = 8, sz = (cw - (cols - 1) * 2) / cols;
            for (int i = 0; i < Category.PALETTE.length; i++) {
                int bx = lx + (i % cols) * (sz + 2), by = r + (i / cols) * (sz + 2);
                int col = Category.PALETTE[i];
                boolean sel = col == cat.color;
                ui.round(bx, by, sz, sz, col);
                if (sel || ui.hover(bx, by, sz, sz)) ui.roundBorder(bx, by, sz, sz, 0xFFFFFFFF);
                if (ui.area(bx, by, sz, sz)) {
                    cat.color = col;
                    PanelState.colorPicker = false;
                }
            }
            r += ((Category.PALETTE.length + cols - 1) / cols) * (sz + 2) + 4;
        }

        // IV de cada estadística.
        s0 = r;
        r += header(ui, lx, r, cw, Icons.IVS, "IVs");
        IvPatternCondition pat = cat.condition(ConditionType.IV_PATTERN);
        int sw = (cw - 3) / 2;
        StatKey[] stats = StatKey.values();
        for (int i = 0; i < 6; i++) {
            int bx = lx + (i % 2) * (sw + 3), by = r + (i / 2) * 16;
            IvPatternCondition.State st = pat.get(stats[i]);
            boolean hov = ui.hover(bx, by, sw, 14);
            ui.box(bx, by, sw, 14, hov ? Ui.ITEM_HOVER : Ui.ITEM, hov ? Ui.BORDER_HOVER : Ui.LINE);
            ui.rect(bx + 2, by + 3, 2, 8, Icons.statColor(stats[i]));
            ui.text(bx + 7, by + 3, stats[i].shortLabel, Ui.TEXT);
            int pw = 18, px = bx + sw - pw - 3;
            if (st == IvPatternCondition.State.PERFECT) {
                ui.round(px, by + 2, pw, 10, Ui.GREEN);
                ui.textCenter(px + pw / 2 + 1, by + 3, "31", 0xFF0F1A12);
            } else if (st == IvPatternCondition.State.ZERO) {
                ui.round(px, by + 2, pw, 10, Ui.RED);
                ui.textCenter(px + pw / 2 + 1, by + 3, "0", 0xFF1A0F10);
            } else {
                ui.roundBorder(px, by + 2, pw, 10, Ui.BORDER_HOVER);
                ui.textCenter(px + pw / 2 + 1, by + 3, "—", Ui.TEXT_DIM);
            }
            ui.tooltip(bx, by, sw, 14, "IV de " + stats[i].shortLabel, "Click: da igual → 31 → 0");
            if (ui.area(bx, by, sw, 14)) {
                pat.set(stats[i], st.next());
                changed = true;
            }
        }
        r += 50;
        p.guideSec.put("ivs", new int[]{s0 - top, r - top});

        s0 = r;
        PerfectIvCountCondition count = cat.condition(ConditionType.PERFECT_IV_COUNT);
        ui.small(lx, r, "¿Cuántos IVs en 31?", Ui.TEXT_DIM);
        r += 9;
        int dot = (cw - 8 * 2) / 8;
        for (int k = -1; k <= 6; k++) {
            int bx = lx + (k + 1) * (dot + 2);
            boolean on = count.count == k;
            boolean hov = ui.hover(bx, r, dot, 13);
            int col = k < 0 ? Ui.TEXT_DIM : Ui.GOLD;
            ui.box(bx, r, dot, 13, on ? Ui.alpha(col, 0xC0) : (hov ? Ui.ITEM_HOVER : Ui.ITEM), on ? col : (hov ? Ui.BORDER_HOVER : Ui.LINE));
            ui.textCenter(bx + dot / 2 + 1, r + 3, k < 0 ? "—" : String.valueOf(k), on ? 0xFF14110A : Ui.TEXT);
            ui.tooltip(bx, r, dot, 13, k < 0 ? "Da igual" : k + " IVs en 31");
            if (ui.area(bx, r, dot, 13)) {
                count.count = k;
                changed = true;
            }
        }
        r += 16;
        if (count.count >= 0) {
            int hw = (cw - 3) / 2;
            if (ui.chip(lx, r, hw, 12, "Exactamente", count.op == Comparison.EQ, Ui.BLUE)) { count.op = Comparison.EQ; changed = true; }
            if (ui.chip(lx + hw + 3, r, cw - hw - 3, 12, count.count + " o más", count.op == Comparison.GE, Ui.BLUE)) { count.op = Comparison.GE; changed = true; }
            r += 15;
        }
        p.guideSec.put("count", new int[]{s0 - top, r - top});
        r += 4;

        // Especies.
        s0 = r;
        r += speciesSection(p, ui, cat, lx, r, cw, cy, cy + lh) + 2;
        p.guideSec.put("species", new int[]{s0 - top, r - top});

        // Grupo huevo.
        s0 = r;
        r += header(ui, lx, r, cw, Icons.EGG, "Grupo huevo");
        EggGroupCondition egg = cat.condition(ConditionType.EGG_GROUP);
        EggGroupKey[] groups = EggGroupKey.values();
        int gw = (cw - 3) / 2;
        for (int i = 0; i < groups.length; i++) {
            int bx = lx + (i % 2) * (gw + 3), by = r + (i / 2) * 13;
            if (ui.chip(bx, by, gw, 11, groups[i].label, egg.groups.contains(groups[i]), Ui.PURPLE)) {
                if (!egg.groups.remove(groups[i])) egg.groups.add(groups[i]);
                changed = true;
            }
        }
        r += ((groups.length + 1) / 2) * 13 + 2;
        if (!egg.groups.isEmpty()) {
            int tw = (cw - 6) / 3;
            EggGroupCondition.Mode[] modes = EggGroupCondition.Mode.values();
            String[] labels = {"Alguno", "Todos", "Solo estos"};
            String[][] tips = {
                    {"Entra si pertenece a alguno", "de los grupos marcados."},
                    {"Entra si pertenece a todos los", "marcados (puede tener otros)."},
                    {"Entra solo si todos sus grupos están", "marcados: con solo Campo, nada de", "Campo + Hada ni otros extra."}};
            for (int i = 0; i < 3; i++) {
                int bx = lx + i * (tw + 3);
                int bwid = i == 2 ? cw - 2 * (tw + 3) : tw;
                if (ui.chip(bx, r, bwid, 12, labels[i], egg.mode == modes[i], Ui.BLUE)) { egg.mode = modes[i]; changed = true; }
                ui.tooltip(bx, r, bwid, 12, tips[i]);
            }
            r += 15;
        }
        p.guideSec.put("egg", new int[]{s0 - top, r - top});

        if (cat.activeConditions().size() >= 2) {
            r += 2;
            ui.small(lx, r, "Entre IVs, especies y grupo huevo, debe cumplir:", Ui.TEXT_DIM);
            r += 9;
            int hw = (cw - 3) / 2;
            if (ui.chip(lx, r, hw, 12, "Todos los filtros", cat.logic == Category.Logic.AND, Ui.BLUE)) { cat.logic = Category.Logic.AND; changed = true; }
            ui.tooltip(lx, r, hw, 12, "Todas las condiciones.");
            if (ui.chip(lx + hw + 3, r, cw - hw - 3, 12, "Algún filtro", cat.logic == Category.Logic.OR, Ui.BLUE)) { cat.logic = Category.Logic.OR; changed = true; }
            ui.tooltip(lx + hw + 3, r, cw - hw - 3, 12, "Basta con una.");
            r += 15;
        }
        r += 4;

        // Cajas.
        r += header(ui, lx, r, cw, Icons.BOX, "Cajas");
        if (cat.boxes.isEmpty()) {
            ui.small(lx, r + 1, "Todavía ninguna", Ui.GOLD);
            r += 10;
        } else {
            int bx = lx;
            for (int b : cat.boxes) {
                String num = String.valueOf(b + 1);
                int pw = ui.width(num) + 8;
                if (bx + pw > lx + cw) {
                    bx = lx;
                    r += 13;
                }
                ui.round(bx, r, pw, 11, Ui.alpha(cat.color, 0x90));
                ui.text(bx + 4, r + 2, num, 0xFFFFFFFF);
                ui.tooltip(bx, r, pw, 11, p.boxName(b), "Click: ir a la caja");
                if (ui.area(bx, r, pw, 11)) p.goToBox(b);
                bx += pw + 2;
            }
            r += 14;
        }
        boolean assigning = PanelState.stripMode == PanelState.StripMode.ASSIGN;
        if (ui.button(lx, r, cw, 15, assigning ? "Toca las cajas de arriba" : "Elegir en la barra", Icons.BOX,
                assigning ? Ui.Style.WARN : Ui.Style.NORMAL)) {
            PanelState.stripMode = assigning ? PanelState.StripMode.NAVIGATE : PanelState.StripMode.ASSIGN;
        }
        ui.tooltip(lx, r, cw, 15, "Haz click en las cajas de la barra de", "arriba para añadirlas o quitarlas.");
        r += 19;

        // Orden dentro de las cajas.
        s0 = r;
        r += header(ui, lx, r, cw, Icons.TAG, "Orden dentro de sus cajas");
        SortMode[] sel = {cat.sortMode, cat.sortMode2};
        r += SortPicker.grid(ui, lx, r, cw, sel, "Sin ordenar") + 4;
        p.guideSec.put("sort", new int[]{s0 - top, r - top});
        cat.sortMode = sel[0];
        cat.sortMode2 = sel[1];

        int matches = p.matchCounts.getOrDefault(cat.id, 0);
        boolean active = !cat.activeConditions().isEmpty();
        ui.round(lx, r + 2, 5, 5, active ? Ui.GREEN : Ui.GOLD);
        ui.text(lx + 9, r, active ? matches + " Pokémon cumplen" : "Elige al menos un filtro", active ? Ui.TEXT : Ui.GOLD);
        if (active) ui.tooltip(lx, r, cw, 10, "En la caja se marcan con un borde", "del color de la categoría.");
        r += 13;
        p.listEnd(ui, "editor", lx, cy, iw, lh, r - top);
        if (changed) p.recountMatches();

        String delLabel = PanelState.deleteArmed ? "¿Seguro?" : "Borrar";
        int delW = Math.max(50, ui.width(delLabel) + 14);
        if (ui.button(lx, footer, delW, 16, delLabel, Ui.Style.DANGER)) {
            if (PanelState.deleteArmed) {
                p.cfg().categories.remove(cat);
                PanelState.editing = null;
                PanelState.stripMode = PanelState.StripMode.NAVIGATE;
                p.unfocusFields();
                OrganizerPCClient.saveConfig();
                p.recountMatches();
                PanelState.status("Categoría borrada (no se movió nada).", Ui.TEXT_DIM);
                return;
            }
            PanelState.deleteArmed = true;
        }
        ui.tooltip(lx, footer, delW, 16, PanelState.deleteArmed ? "Click otra vez para borrarla." : "Borra la categoría. No mueve Pokémon.");
        p.gDone = new int[]{lx + delW + 3, footer, iw - delW - 3, 16};
        if (ui.button(lx + delW + 3, footer, iw - delW - 3, 16, "Listo", Ui.Style.PRIMARY)) p.finishEditing();
    }
}
