package io.github.sefiraat.equivalencytech.runnables;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.configuration.ConfigMain;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunnableEQTick extends BukkitRunnable {

    public final EquivalencyTech plugin;
    public final boolean sf;

    private final Set<Integer> warnedDChests = new HashSet<>();
    private final Set<Integer> warnedCChests = new HashSet<>();

    private final Set<Integer> warnedDOwners = new HashSet<>();
    private final Set<Integer> warnedCOwners = new HashSet<>();

    /*
     * A duplicated location is a persistence inconsistency, not proof that either record is
     * disposable. Keep the warning bounded without modifying a player's chest indexes from the
     * tick thread; reconciliation is an explicit, backed-up administrator operation.
     */
    private final Set<Location> warnedDDuplicateLocations = new HashSet<>();
    private final Set<Location> warnedCDuplicateLocations = new HashSet<>();

    public RunnableEQTick(EquivalencyTech plugin) {
        this.plugin = plugin;
        sf = EquivalencyTech.getInstance().getManagerSupportedPlugins().isInstalledSlimefun();
    }

    @Override
    public void run() {
        processDChests();
        processCChests();
    }

    private void processDChests() {
        Set<Location> vistas = new HashSet<>();
        for (Location location : ConfigMain.getAllDChestLocations(plugin)) {
            if (Utils.isBlockedWorld(plugin, location.getWorld())) {
                continue;
            }
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                if (!vistas.add(location)) {
                    detectarDuplicados(ConfigMain.getAllDChestIdsStore(plugin, location), location, true);
                    continue;
                }
                Integer storedId = ConfigMain.getDChestIdStore(plugin, location);
                if (storedId == null) {
                    continue;
                }
                int chestId = storedId;
                String playerUUID = ConfigMain.getOwnerDChest(plugin, chestId);
                if (playerUUID == null) {
                    if (detectarDuplicados(ConfigMain.getAllDChestIdsStore(plugin, location), location, true)) {
                        continue;
                    }
                    if (warnedDOwners.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger()
                            .warning(getErrorOrphanOwner("Dissolution", chestId, location, "dissolution_chests.yml"));
                    }
                    continue;
                }
                warnedDOwners.remove(chestId);

                // Sin snapshot: getState() copiaba el tile entity entero (inventario incluido)
                // y se pedia dos veces por cofre y por tick.
                BlockState state = location.getBlock().getState(false);

                if (!(state instanceof Chest)) {
                    if (warnedDChests.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger().warning(getErrorDissolutionChest(chestId, location));
                    }
                    continue;
                }

                warnedDChests.remove(chestId);

                Chest chest = (Chest) state;
                Inventory inventory = chest.getBlockInventory();
                ItemStack[] contents = inventory.getContents();
                for (int slot = 0; slot < contents.length; slot++) {
                    ItemStack itemStack = contents[slot];
                    if (itemStack != null && itemStack.getType() != Material.AIR) {
                        // Se clasifica la pila una sola vez y se reutiliza en getEMC y canBeSynth,
                        // que antes repetian getByItem y la lectura del PDC por su cuenta.
                        boolean isEQ = ContainerStorage.isCraftable(itemStack, plugin);
                        SlimefunItem sfItem = sf ? Utils.resolveSlimefunItem(itemStack) : null;
                        Material material = itemStack.getType();

                        Double emcBase = Utils.getEMC(plugin, itemStack, sfItem, isEQ);
                        if (emcBase == null) {
                            continue;
                        }
                        // Ley de Entropia (Infinity Tier): se pierde el 50% al disolver (0.50x),
                        // impidiendo bucles y granjas infinitas de conversion.
                        Double emcValue = Utils.roundDown(emcBase * 0.50, 2);
                        if (emcValue != null && emcValue > 0 && Utils.canBeSynth(itemStack, sfItem, isEQ)) {
                            String entryName;
                            if (isEQ) {
                                entryName = Utils.eqEntryName(plugin, itemStack);
                            } else if (sfItem != null) {
                                entryName = sfItem.getId();
                            } else {
                                entryName = material.toString();
                            }
                            if (!ConfigMain.hasLearnedItem(plugin, playerUUID, entryName)) {
                                ConfigMain.addLearnedItem(plugin, playerUUID, entryName);
                            }
                            ConfigMain.addPlayerEmc(plugin, playerUUID, emcValue);
                            if (itemStack.getAmount() <= 1) {
                                inventory.setItem(slot, null);
                            } else {
                                itemStack.setAmount(itemStack.getAmount() - 1);
                                inventory.setItem(slot, itemStack);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void processCChests() {
        Set<Location> vistas = new HashSet<>();
        for (Location location : ConfigMain.getAllCChestLocations(plugin)) {
            if (Utils.isBlockedWorld(plugin, location.getWorld())) {
                continue;
            }
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                if (!vistas.add(location)) {
                    detectarDuplicados(ConfigMain.getAllCChestIdsStore(plugin, location), location, false);
                    continue;
                }
                Integer storedId = ConfigMain.getCChestIdStore(plugin, location);
                if (storedId == null) {
                    continue;
                }
                int chestId = storedId;
                String playerUUID = ConfigMain.getOwnerCChest(plugin, chestId);
                if (playerUUID == null) {
                    if (detectarDuplicados(ConfigMain.getAllCChestIdsStore(plugin, location), location, false)) {
                        continue;
                    }
                    if (warnedCOwners.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger()
                            .warning(getErrorOrphanOwner("Condensate", chestId, location, "condensate_chests.yml"));
                    }
                    continue;
                }
                warnedCOwners.remove(chestId);

                // Sin snapshot: getState() copiaba el tile entity entero (inventario incluido)
                // y se pedia dos veces por cofre y por tick.
                BlockState state = location.getBlock().getState(false);

                if (!(state instanceof Chest)) {
                    if (warnedCChests.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger().warning(getErrorCondensateChest(chestId, location));
                    }
                    continue;
                }

                warnedCChests.remove(chestId);

                Chest chest = (Chest) state;
                Inventory inventory = chest.getBlockInventory();
                ItemStack itemStack = ConfigMain.getCChestItem(plugin, chestId);
                if (itemStack != null) {
                    Double emcValue = ConfigMain.getCChestItemEmc(plugin, chestId, itemStack);
                    if (emcValue != null && emcValue > 0) {
                        // Sintesis pasiva en cofre requiere recargo del +50% de EMC (1.50x) segun el lore oficial
                        Double requiredEmc = Utils.roundDown(emcValue * 1.50, 2);
                        Double playerEmc = ConfigMain.getPlayerEmc(plugin, playerUUID);
                        if (playerEmc >= requiredEmc) {
                            HashMap<Integer, ItemStack> failed = inventory.addItem(itemStack);
                            if (failed.isEmpty()) {
                                ConfigMain.removePlayerEmc(plugin, playerUUID, requiredEmc);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Detects conflicting location claims without deleting anything. A previous implementation
     * removed all but one ID while this periodic task ran, which could silently discard a valid
     * owner or configured output item before the records had been reconciled and backed up.
     */
    private boolean detectarDuplicados(List<Integer> ids, Location location, boolean dissolution) {
        if (ids.size() < 2) {
            (dissolution ? warnedDDuplicateLocations : warnedCDuplicateLocations).remove(location);
            return false;
        }
        Set<Location> warnedLocations = dissolution ? warnedDDuplicateLocations : warnedCDuplicateLocations;
        if (warnedLocations.add(location)) {
            EquivalencyTech.getInstance().getLogger().warning(
                "Registros duplicados de cofre EMC detectados ("
                    + (dissolution ? "dissolution" : "condensation") + "=" + ids
                    + ") en " + location
                    + "; no se modificaron. Requiere migracion explicita con respaldo.");
        }
        return true;
    }

    public static String getErrorDissolutionChest(int chestId, Location location) {
        return MessageFormat.format(
            "A Dissolution chest (ID: {0}) has been removed wrongly. " +
                "Either replace with a vanilla chest (location : {1}) " +
                "or remove from dissolution_chests.yml",
            chestId,
            location.toString()
        );
    }

    public static String getErrorOrphanOwner(String kind, int chestId, Location location, String file) {
        return MessageFormat.format(
            "A {0} chest (ID: {1}) has no OWNING_PLAYER recorded and cannot be processed. " +
                "Either restore its owner in {2} or remove the entry (location : {3})",
            kind,
            chestId,
            file,
            location.toString()
        );
    }

    public static String getErrorCondensateChest(int chestId, Location location) {
        return MessageFormat.format(
            "A Condensate chest (ID: {0}) has been removed wrongly. " +
                "Either replace with a vanilla chest (location : {1})  " +
                "or remove from condensate_chests.yml",
            chestId,
            location.toString()
        );
    }
}
