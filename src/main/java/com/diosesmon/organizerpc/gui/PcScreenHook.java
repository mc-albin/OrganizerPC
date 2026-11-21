package com.diosesmon.organizerpc.gui;

import com.cobblemon.mod.common.client.gui.pc.BoxStorageSlot;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.cobblemon.MoveExecutor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Engancha el organizador a la PC original de Cobblemon con los eventos de pantalla de
 * Fabric API. La PC sigue funcionando igual: solo se interceptan clics en nuestros paneles,
 * en la caja durante la vista previa y, con la ventana Mover abierta, sobre los Pokémon.
 * Con el organizador oculto solo queda un botón pequeño para volver a mostrarlo.
 */
public final class PcScreenHook {
    /** Panel de la PC abierta actualmente (null si no hay PC abierta). */
    static OrganizerPanel current;
    /** Errores de dibujo capturados (la PC sigue funcionando aunque el panel falle). */
    static int renderErrors;

    private PcScreenHook() {
    }

    public static void attach(PCGUI gui) {
        OrganizerPanel panel = new OrganizerPanel(gui);
        current = panel;

        ScreenEvents.afterRender(gui).register((screen, ctx, mx, my, delta) -> {
            try {
                if (OrganizerPCClient.config().hidden) HiddenButton.render(panel, ctx, mx, my);
                else panel.render(ctx, mx, my, delta);
            } catch (Exception e) {
                if (renderErrors++ < 5) OrganizerPCClient.LOGGER.error("Error dibujando el organizador", e);
            }
        });

        ScreenEvents.afterTick(gui).register(screen -> {
            try {
                if (!OrganizerPCClient.config().hidden) panel.tick();
            } catch (Exception e) {
                OrganizerPCClient.LOGGER.error("Error en el organizador", e);
            }
        });

        ScreenMouseEvents.allowMouseClick(gui).register((screen, mx, my, button) -> {
            if (OrganizerPCClient.config().hidden) return !HiddenButton.click(panel, mx, my, button);
            try {
                if (panel.mouseClicked(mx, my, button)) return false;
            } catch (Exception e) {
                OrganizerPCClient.LOGGER.error("Error en el panel del organizador", e);
                PanelState.status("Error interno: " + e, Ui.RED);
                return false;
            }
            BoxStorageSlot slot = panel.slotAt(mx, my);
            // Durante la ejecución no se puede arrastrar: la PC no debe cambiar a mitad del plan.
            if (MoveExecutor.isRunning() && slot != null) return false;
            if (PanelState.pairMode && button == 0) {
                Pokemon mon = slot != null ? slot.getPokemon() : null;
                if (mon == null) {
                    for (var ps : panel.partySlots()) {
                        if (mx >= ps.getX() && mx < ps.getX() + com.cobblemon.mod.common.client.gui.pc.StorageSlot.SIZE
                                && my >= ps.getY() && my < ps.getY() + com.cobblemon.mod.common.client.gui.pc.StorageSlot.SIZE) mon = ps.getPokemon();
                    }
                }
                if (mon != null) {
                    Pairs.toggle(mon.getUuid(), com.diosesmon.organizerpc.cobblemon.PcReader.info(mon).name());
                    return false;
                }
            }
            if (panel.moveWindowVisible() && button == 0 && slot != null) {
                Pokemon p = slot.getPokemon();
                if (p != null) {
                    if (panel.locked(panel.currentBox())) {
                        PanelState.status("Caja bloqueada: no se puede marcar.", Ui.GOLD);
                    } else {
                        panel.toggleSelection(p.getUuid());
                        Ui.click();
                    }
                    return false;
                }
            }
            return true;
        });

        ScreenMouseEvents.allowMouseScroll(gui).register((screen, mx, my, h, v) -> {
            if (OrganizerPCClient.config().hidden) return !PanelState.settingsOpen;
            return !panel.mouseScrolled(mx, my, v);
        });

        ScreenKeyboardEvents.allowKeyPress(gui).register((screen, key, scancode, modifiers) -> {
            // Un campo de texto con foco (búsqueda, nombre, especie, colores) se queda con la tecla:
            // así la PC no se cierra con la tecla de inventario mientras escribes.
            var field = panel.focusedField();
            if (field != null) {
                boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
                if (field == panel.searchField && enter) {
                    int b = Search.nextBox(panel.currentBox());
                    if (b >= 0) panel.goToBox(b);
                    else PanelState.status(Search.active() ? "Ninguna caja tiene resultados." : "Escribe algo para buscar.", Ui.GOLD);
                } else if (key == GLFW.GLFW_KEY_ESCAPE || enter) {
                    panel.unfocusFields();
                } else {
                    field.keyPressed(key, scancode, modifiers);
                }
                return false;
            }
            if (PanelState.settingsOpen && key == GLFW.GLFW_KEY_ESCAPE) {
                SettingsWindow.close();
                return false;
            }
            if (!PanelState.settingsOpen && Pairs.keyPressed(panel, key, scancode)) return false;
            if (PanelState.pairMode && key == GLFW.GLFW_KEY_ESCAPE) {
                PanelState.pairMode = false;
                return false;
            }
            if (OrganizerPCClient.config().hidden) return true;
            if (Search.popupOpen && key == GLFW.GLFW_KEY_ESCAPE) {
                Search.popupOpen = false;
                return false;
            }
            if (PanelState.tutorialStep >= 0 && key == GLFW.GLFW_KEY_ESCAPE) {
                PanelState.tutorialStep = -1;
                OrganizerPCClient.config().tutorialDone = true;
                OrganizerPCClient.saveConfig();
                return false;
            }
            if (Guide.active() && key == GLFW.GLFW_KEY_ESCAPE) {
                Guide.close();
                return false;
            }
            return true;
        });

        ScreenEvents.remove(gui).register(screen -> {
            if (current == panel) current = null;
            if (PanelState.editing != null) panel.finishEditing();
            PanelState.dragging = null;
            OrganizerPCClient.saveConfig();
        });
    }
}
