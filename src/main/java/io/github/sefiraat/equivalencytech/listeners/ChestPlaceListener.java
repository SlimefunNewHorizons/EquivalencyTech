package io.github.sefiraat.equivalencytech.listeners;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.configuration.ConfigMain;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import io.github.sefiraat.equivalencytech.statics.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ChestPlaceListener implements Listener {

    private final EquivalencyTech plugin;

    public ChestPlaceListener(EquivalencyTech plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() == Material.CHEST) {
            boolean isDis = isDis(e);
            boolean isCon = isCon(e);
            if (isDis || isCon) {
                if (Utils.isBlockedWorld(plugin, e.getBlockPlaced().getWorld())) {
                    String aviso = plugin.getConfig().getString("MESSAGES.CHEST_WORLD_BLOCKED");
                    if (aviso == null || aviso.isBlank()) {
                        aviso = "&cLos cofres de EMC no estan permitidos en esta modalidad.";
                    }
                    e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', aviso));
                    e.setCancelled(true);
                    return;
                }
                if (!noNearbyChest(e.getBlockPlaced())) {
                    e.setCancelled(true);
                }
                return;
            }
            if (nearbyEMCChest(e)) {
                e.getPlayer().sendMessage(Messages.messageEventEMCChestPlace(plugin));
                e.setCancelled(true);
            }
        }
    }

    // MONITOR observa el resultado final de Slimefun y las protecciones. Separar la
    // persistencia de la validacion evita registros huerfanos sin cancelar en MONITOR.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestPlaceAccepted(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.CHEST) {
            return;
        }
        if (isDis(e)) {
            placeDisChest(e);
        } else if (isCon(e)) {
            placeConChest(e);
        }
    }

    // El dueño se escribe sobre el id que devuelve addCChestStore, no sobre el que resuelve
    // getCChestIdStore: ese devuelve el primer key de la posicion y, cuando sobrevivia un
    // registro anterior, el dueño y LEVEL=1 caian en el id viejo y el nuevo nacia sin dueño.
    private void placeConChest(BlockPlaceEvent e) {
        Location location = e.getBlockPlaced().getLocation();
        Integer id = ConfigMain.addCChestStore(plugin, location);
        ConfigMain.setupCChest(plugin, id, e.getPlayer());
    }

    private void placeDisChest(BlockPlaceEvent e) {
        Location location = e.getBlockPlaced().getLocation();
        Integer id = ConfigMain.addDChestStore(plugin, location);
        ConfigMain.setupDChest(plugin, id, e.getPlayer());
    }

    // Prioridad HIGHEST con ignoreCancelled: las protecciones (BentoBox rompe en LOW,
    // WorldGuard/ProtectionStones en NORMAL) ya decidieron. Con el @EventHandler pelado
    // anterior este listener vaciaba el cofre y lo ponia a AIR aunque la rotura estuviese
    // cancelada, lo que permitia a un visitante robar un cofre EMC dentro de una isla.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestBreak(BlockBreakEvent e) {
        Location location = e.getBlock().getLocation();
        Integer disID = ConfigMain.getDChestIdStore(plugin, location);
        Integer conID = ConfigMain.getCChestIdStore(plugin, location);
        if (disID != null || conID != null) {
            if (!(e.getBlock().getState() instanceof Chest chest)) {
                // El bloque ya no es un cofre: la entrada quedo huerfana por una via sin
                // BlockBreakEvent (explosion, WorldEdit, reseteo de isla). Se limpia el
                // registro muerto en vez de romper con ClassCastException y de seguir
                // avisando por cada tick. No se cancela: el jugador rompe lo que haya.
                purgarRegistroHuerfano(location);
                return;
            }
            e.setCancelled(true);
            Inventory inventory = chest.getBlockInventory();
            for (ItemStack itemStack : inventory.getContents()) {
                if (itemStack != null && itemStack.getType() != Material.AIR) {
                    e.getBlock().getWorld().dropItemNaturally(location, itemStack);
                    itemStack.setAmount(0);
                }
            }
            e.getBlock().setType(Material.AIR);
            // Un solo drop por cofre roto (el cofre fisico es uno), pero se borran todos los ids
            // de la posicion: si quedase alguno, la posicion seguiria reclamada y el siguiente
            // jugador que rompiese un cofre normal ahi recibiria un cofre EMC de regalo.
            if (disID != null) {
                e.getBlock().getWorld().dropItemNaturally(location, plugin.getEqItems().getDissolutionChest().getItemClone());
                for (Integer id : ConfigMain.getAllDChestIdsStore(plugin, location)) {
                    ConfigMain.removeDChestStore(plugin, id);
                    ConfigMain.removeDChest(plugin, id);
                }
            }
            if (conID != null) {
                e.getBlock().getWorld().dropItemNaturally(location, plugin.getEqItems().getCondensatorChest().getItemClone());
                for (Integer id : ConfigMain.getAllCChestIdsStore(plugin, location)) {
                    ConfigMain.removeCChestStore(plugin, id);
                    ConfigMain.removeCChest(plugin, id);
                }
            }
        }

    }

    // Se purgan TODOS los ids que reclaman la posicion, no solo el primero: borrar uno solo
    // dejaba vivo al siguiente, que al quedar sin dueño reaparecia como aviso del RunnableEQTick.
    private void purgarRegistroHuerfano(Location location) {
        List<Integer> dis = ConfigMain.getAllDChestIdsStore(plugin, location);
        List<Integer> con = ConfigMain.getAllCChestIdsStore(plugin, location);
        for (Integer id : dis) {
            ConfigMain.removeDChestStore(plugin, id);
            ConfigMain.removeDChest(plugin, id);
        }
        for (Integer id : con) {
            ConfigMain.removeCChestStore(plugin, id);
            ConfigMain.removeCChest(plugin, id);
        }
        plugin.getLogger().info(
            "Registro huerfano de cofre EMC eliminado (dissolution=" + dis + ", condensate=" + con
                + ") : el bloque en " + location + " ya no es un cofre.");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChestInteract(PlayerInteractEvent e) {
        // Este evento salta con cualquier clic en cualquier bloque: solo un cofre puede ser un
        // cofre EMC, asi que el resto sale sin consultar el indice de block_storage.yml.
        if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CHEST) {
            Location location = e.getClickedBlock().getLocation();
            Integer disID = ConfigMain.getDChestIdStore(plugin, location);
            Integer conID = ConfigMain.getCChestIdStore(plugin, location);
            if (disID != null || conID != null) {
                if (Utils.isBlockedWorld(plugin, location.getWorld())) {
                    String aviso = plugin.getConfig().getString("MESSAGES.CHEST_WORLD_BLOCKED");
                    if (aviso == null || aviso.isBlank()) {
                        aviso = "&cLos cofres de EMC no estan permitidos en esta modalidad.";
                    }
                    e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', aviso));
                    e.setCancelled(true);
                    return;
                }
            }
            if (disID != null) {
                if (isChestBeingOpened(e) && !hasPermissionDChest(disID, e.getPlayer())) {
                    e.getPlayer().sendMessage(Messages.messageEventCantOpenNotOwner(plugin));
                    e.setCancelled(true);
                }
            }
            if (conID != null) {
                if (isChestBeingOpened(e)) {
                    openChest(e);
                } else if (isChestBeingSet(e)) {
                    setChest(e);
                }
            }
        }
    }


    private boolean isDis(BlockPlaceEvent e) {
        return ContainerStorage.isDisChest(e.getItemInHand(), plugin);
    }

    private boolean isCon(BlockPlaceEvent e) {
        return ContainerStorage.isConChest(e.getItemInHand(), plugin);
    }

    private boolean nearbyEMCChest(BlockPlaceEvent e) {
        List<Block> blockList = new ArrayList<>();
        Block block = e.getBlockPlaced();
        blockList.add(block.getRelative(BlockFace.NORTH));
        blockList.add(block.getRelative(BlockFace.SOUTH));
        blockList.add(block.getRelative(BlockFace.EAST));
        blockList.add(block.getRelative(BlockFace.WEST));
        for (Block b : blockList) {
            if (b.getType() == Material.CHEST && (getDisId(b) != null || getConId(b) != null)) {
                return true;
            }
        }
        return false;
    }

    private boolean noNearbyChest(Block block) {
        List<Block> blockList = new ArrayList<>();
        blockList.add(block.getRelative(BlockFace.NORTH));
        blockList.add(block.getRelative(BlockFace.SOUTH));
        blockList.add(block.getRelative(BlockFace.EAST));
        blockList.add(block.getRelative(BlockFace.WEST));
        for (Block b : blockList) {
            if (b.getType() == Material.CHEST) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPermissionDChest(Integer disId, Player player) {
        return ConfigMain.isOwnerDChest(plugin, player, disId) || player.isOp() || player.hasPermission("equitech.bypass");
    }

    private boolean hasPermissionCChest(Integer conId, Player player) {
        return ConfigMain.isOwnerCChest(plugin, player, conId) || player.isOp() || player.hasPermission("equitech.bypass");
    }

    private Integer getDisId(Block block) {
        return ConfigMain.getDChestIdStore(plugin, block.getLocation());
    }

    private Integer getConId(Block block) {
        return ConfigMain.getCChestIdStore(plugin, block.getLocation());
    }

    private boolean isChestBeingOpened(PlayerInteractEvent e) {
        return  e.getClickedBlock().getType() == Material.CHEST &&
                e.getAction() == Action.RIGHT_CLICK_BLOCK &&
                !e.getPlayer().isSneaking() &&
                e.useInteractedBlock() != Event.Result.DENY;
    }

    private boolean isChestBeingSet(PlayerInteractEvent e) {
        return  e.getClickedBlock().getType() == Material.CHEST &&
                e.getAction() == Action.RIGHT_CLICK_BLOCK &&
                e.getPlayer().isSneaking() &&
                e.useInteractedBlock() != Event.Result.DENY;
    }

    private void openChest(PlayerInteractEvent e) {
        Integer conId = getConId(e.getClickedBlock());
        if (conId != null && !hasPermissionCChest(conId, e.getPlayer())) {
            e.getPlayer().sendMessage(Messages.messageEventCantOpenNotOwner(plugin));
            e.setCancelled(true);
        }
    }

    private void setChest(PlayerInteractEvent e) {
        Integer conId = getConId(e.getClickedBlock());
        if (conId == null || hasPermissionCChest(conId, e.getPlayer())) {
            ItemStack itemStack = e.getPlayer().getInventory().getItemInMainHand().clone();
            if (itemStack.getType() == Material.AIR) {
                ConfigMain.setCChestItem(plugin, conId, null);
                e.getPlayer().sendMessage(Messages.messageEventItemUnset(plugin));
                return;
            }
            itemStack.setAmount(1);
            Double emcValue = Utils.getEMC(plugin, itemStack);
            if (Utils.canBeSynth(plugin, itemStack) && emcValue != null) {
                ConfigMain.setCChestItem(plugin, conId, itemStack);
                e.getPlayer().sendMessage(Messages.messageEventItemSet(plugin));
            } else {
                e.getPlayer().sendMessage(Messages.msgCmdEmcNone(plugin));
            }
        } else {
            e.getPlayer().sendMessage(Messages.messageEventCantOpenNotOwner(plugin));
        }
        e.setCancelled(true);
    }


}
