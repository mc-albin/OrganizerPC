package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.afk.AfkTracker;
import net.minecraft.client.gui.DrawContext;

/** Organizador oculto: solo un botón pequeño encima de la PC para volver a mostrarlo. */
final class HiddenButton {
    private HiddenButton() {
    }

    private static int[] rect(OrganizerPanel p) {
        p.computeGeometry();
        int w = 22, h = 18;
        return new int[]{p.pcLeft, Math.max(1, p.pcTop - h - 3), w, h};
    }

    /** Botón al lado: seguir viendo las marcas de IVs con el organizador oculto. */
    private static int[] ivRect(int[] r) {
        return new int[]{r[0] + r[2] + 3, r[1], 22, r[3]};
    }

    static void render(OrganizerPanel p, DrawContext ctx, int rmx, int rmy) {
        Ui.syncTheme();
        int[] r = rect(p);
        int mx = p.vx(rmx), my = p.vy(rmy);
        boolean ivOn = OrganizerPCClient.config().ivMarksWhenHidden;
        if (ivOn) {
            // Marcas de IVs sobre los Pokémon de la caja, aunque el organizador esté oculto.
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 400);
            for (var slot : p.boxSlots()) {
                var mon = slot.getPokemon();
                if (mon != null) SlotMarks.ivBar(ctx, slot.getX(), slot.getY(), com.diosesmon.organizerpc.cobblemon.PcReader.info(mon));
            }
            p.partyIvMarks(ctx);
            ctx.getMatrices().pop();
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 500);
        p.pushScale(ctx);
        // Con Ajustes abierto, los botones de detrás no reaccionan al ratón.
        Ui ui = new Ui(Ui.Mode.DRAW, ctx, PanelState.settingsOpen ? -9999 : mx, PanelState.settingsOpen ? -9999 : my);
        boolean hov = ui.hover(r[0], r[1], r[2], r[3]);
        ui.box(r[0], r[1], r[2], r[3], hov ? Ui.BTN_HOVER : Ui.BG, hov ? Ui.BORDER_HOVER : Ui.BORDER);
        ui.icon(Icons.POKE_BALL, r[0] + 4, r[1] + 3, 12);
        // Puntito de estado AFK también con el organizador oculto.
        ui.round(r[0] + r[2] - 6, r[1] + 2, 4, 4, AfkTracker.isAfk() ? Ui.GOLD : Ui.GREEN);
        ui.tooltip(r[0], r[1], r[2], r[3], "Mostrar el organizador",
                AfkTracker.isAfk() ? "Estás AFK" : "No estás AFK");

        int[] iv = ivRect(r);
        boolean ihov = ui.hover(iv[0], iv[1], iv[2], iv[3]);
        ui.box(iv[0], iv[1], iv[2], iv[3], ivOn ? Ui.alpha(Ui.GREEN, 0x50) : ihov ? Ui.BTN_HOVER : Ui.BG,
                ivOn ? Ui.GREEN : ihov ? Ui.BORDER_HOVER : Ui.BORDER);
        ui.icon(Icons.IVS, iv[0] + 5, iv[1] + 3, 12);
        ui.tooltip(iv[0], iv[1], iv[2], iv[3], ivOn ? "Marcas de IVs: visibles" : "Marcas de IVs: ocultas",
                "Click para " + (ivOn ? "quitarlas" : "verlas") + " con el organizador oculto.");

        int[] st = settingsRect(r);
        boolean sOpen = PanelState.settingsOpen;
        boolean shov = ui.hover(st[0], st[1], st[2], st[3]);
        ui.box(st[0], st[1], st[2], st[3], sOpen ? Ui.SEL_BG : shov ? Ui.BTN_HOVER : Ui.BG, sOpen ? Ui.SEL_BORDER : shov ? Ui.BORDER_HOVER : Ui.BORDER);
        ui.icon(Icons.SETTINGS, st[0] + 5, st[1] + 3, 12);
        ui.tooltip(st[0], st[1], st[2], st[3], "Ajustes");

        // Ventana de Ajustes también con el organizador oculto.
        int[] af = afkRect(r);
        boolean afk = AfkTracker.isAfk();
        boolean ahov = ui.hover(af[0], af[1], af[2], af[3]);
        ui.box(af[0], af[1], af[2], af[3], afk ? Ui.alpha(Ui.GOLD, ahov ? 0x60 : 0x40) : ahov ? Ui.BTN_HOVER : Ui.BG,
                afk ? Ui.GOLD : ahov ? Ui.BORDER_HOVER : Ui.BORDER);
        ui.textCenter(af[0] + af[2] / 2 + 1, af[1] + 5, "AFK", afk ? 0xFFFFFFFF : Ui.TEXT_DIM);
        ui.tooltip(af[0], af[1], af[2], af[3], afk ? "Estás AFK" : "No estás AFK",
                afk ? "Click para volver." : "Click para ponerte AFK.");

        int[] eg = eggRect(r);
        boolean eggOn = OrganizerPCClient.config().eggGroupHover;
        boolean ehov = ui.hover(eg[0], eg[1], eg[2], eg[3]);
        ui.box(eg[0], eg[1], eg[2], eg[3], eggOn ? Ui.alpha(Ui.GREEN, 0x50) : ehov ? Ui.BTN_HOVER : Ui.BG,
                eggOn ? Ui.GREEN : ehov ? Ui.BORDER_HOVER : Ui.BORDER);
        ui.icon(Icons.EGG, eg[0] + 5, eg[1] + 3, 12);
        ui.tooltip(eg[0], eg[1], eg[2], eg[3], eggOn ? "Grupo huevo: visible" : "Grupo huevo: oculto",
                "Pasa el ratón por un Pokémon para ver su grupo huevo.");

        Ui top = ui;
        if (sOpen) {
            OrganizerPanel.clearDepth();
            top = new Ui(Ui.Mode.DRAW, ctx, mx, my);
            SettingsWindow.layout(p, top);
            p.renderHexFields(ctx, mx, my, 0);
        }
        p.dropHiddenFocus();
        if (top.tooltip() != null) Ui.drawTooltip(ctx, top.tooltip(), mx, my, p.vw, p.vh);
        else if (ui.tooltip() != null) Ui.drawTooltip(ctx, ui.tooltip(), mx, my, p.vw, p.vh);
        else if (!PanelState.settingsOpen) EggHover.render(p, ctx, rmx, rmy);
        p.popScale(ctx);
        ctx.getMatrices().pop();
    }

    private static int[] settingsRect(int[] r) {
        int[] iv = ivRect(r);
        return new int[]{iv[0] + iv[2] + 3, r[1], 22, r[3]};
    }

    private static int[] afkRect(int[] r) {
        int[] st = settingsRect(r);
        return new int[]{st[0] + st[2] + 3, r[1], 28, r[3]};
    }

    private static int[] eggRect(int[] r) {
        int[] af = afkRect(r);
        return new int[]{af[0] + af[2] + 3, r[1], 22, r[3]};
    }

    private static boolean in(int[] b, double mx, double my) {
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    static boolean click(OrganizerPanel p, double rmx, double rmy, int button) {
        int[] r = rect(p);
        double mx = rmx / p.k, my = rmy / p.k;
        if (p.settingsVisible()) {
            // Ajustes tapa todo: los clics solo van a la ventana (y a sus campos de color).
            if (button == 0 && !(p.hexFieldsVisible() && p.focusFieldAt(mx, my, p.hexBaseField, p.hexAccentField))) {
                p.unfocusFields();
                SettingsWindow.layout(p, new Ui(Ui.Mode.CLICK, null, (int) mx, (int) my));
            }
            return true;
        }
        int[] st = settingsRect(r);
        if (mx >= st[0] && mx < st[0] + st[2] && my >= st[1] && my < st[1] + st[3]) {
            if (button == 0) {
                Ui.click();
                PanelState.settingsOpen = !PanelState.settingsOpen;
                if (PanelState.settingsOpen) p.syncHexFields();
            }
            return true;
        }
        if (in(afkRect(r), mx, my)) {
            if (button == 0) {
                Ui.click();
                AfkTracker.toggle();
            }
            return true;
        }
        if (in(eggRect(r), mx, my)) {
            if (button == 0) {
                Ui.click();
                OrganizerPCClient.config().eggGroupHover = !OrganizerPCClient.config().eggGroupHover;
                OrganizerPCClient.saveConfig();
            }
            return true;
        }
        int[] iv = ivRect(r);
        if (mx >= iv[0] && mx < iv[0] + iv[2] && my >= iv[1] && my < iv[1] + iv[3]) {
            if (button == 0) {
                Ui.click();
                OrganizerPCClient.config().ivMarksWhenHidden = !OrganizerPCClient.config().ivMarksWhenHidden;
                OrganizerPCClient.saveConfig();
            }
            return true;
        }
        if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) return false;
        if (button == 0) {
            Ui.click();
            OrganizerPCClient.config().hidden = false;
            OrganizerPCClient.saveConfig();
        }
        return true;
    }

    /** Ocultar todo: cancela lo que esté a medias (sin mover nada). */
    static void hide(OrganizerPanel p) {
        if (com.diosesmon.organizerpc.cobblemon.MoveExecutor.isRunning()) {
            PanelState.status("Espera a que termine de mover.", Ui.GOLD);
            return;
        }
        PanelState.preview = null;
        PanelState.overrides.clear();
        PanelState.confirm = null;
        PanelState.tutorialStep = -1;
        Guide.close();
        PanelState.stripMode = PanelState.StripMode.NAVIGATE;
        if (PanelState.editing != null) p.finishEditing();
        p.closeMoveWindow();
        PanelState.arrangeOpen = false;
        OrganizerPCClient.config().hidden = true;
        OrganizerPCClient.saveConfig();
    }
}
