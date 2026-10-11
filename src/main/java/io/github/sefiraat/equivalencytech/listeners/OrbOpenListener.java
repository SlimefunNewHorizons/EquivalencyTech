package io.github.sefiraat.equivalencytech.listeners;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.gui.GuiTransmutationOrb;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;

public class OrbOpenListener implements Listener {

    private final EquivalencyTech plugin;

    public OrbOpenListener(@Nonnull EquivalencyTech plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent e) {
        // Este evento salta con cada clic de cada jugador y una vez por mano. Antes se pedia
        // getItemMeta() (copia completa de la meta) de cualquier item en la mano; ahora se mira
        // primero la accion y luego el PDC, que ya comprueba null y hasItemMeta.
        Action action = e.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Player player = e.getPlayer();
            ItemStack i = e.getItem();
            if (ContainerStorage.isTransmutationOrb(i, plugin)) {
                e.setCancelled(true);

                // El EMC y los items aprendidos se guardan por jugador y valen en todas las
                // modalidades. En un mundo donde los items se consiguen gratis, el orbe los
                // convierte en EMC que despues se gasta en la modalidad normal, asi que ahi no
                // se abre. La lista vive en config porque manana puede haber otro mundo
                if (io.github.sefiraat.equivalencytech.misc.Utils.isBlockedWorld(plugin, player.getWorld())) {
                    String aviso = plugin.getConfig().getString("MESSAGES.ORB_WORLD_BLOCKED");
                    if (aviso == null || aviso.isBlank()) {
                        aviso = plugin.getConfig().getString("GUI.ORB_WORLD_BLOCKED", "&cEl orbe no funciona en esta modalidad.");
                    }
                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', aviso));
                    return;
                }

                GuiTransmutationOrb gui = GuiTransmutationOrb.buildGui(plugin, player);
                gui.open(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiTransmutationOrb gui) {
            gui.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiTransmutationOrb
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

}
