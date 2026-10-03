package io.github.sefiraat.equivalencytech.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.configuration.ConfigMain;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import io.github.sefiraat.equivalencytech.statics.Messages;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@CommandAlias("EquivalencyTech|ET")
@Description("EquivalencyTech Main")
public class Commands extends BaseCommand {

    private final EquivalencyTech plugin;

    public EquivalencyTech getPlugin() {
        return plugin;
    }

    public Commands(EquivalencyTech plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onDefault(CommandSender sender) {
        if (sender instanceof Player) {
            sender.sendMessage(Messages.msgCmdSubcommand(plugin));
        }
    }

    @Subcommand("ItemEmc")
    @Description("Displays the EMC value for the held item.")
    public class ItemEmc extends BaseCommand {

        @Default
        public void onDefault(CommandSender sender) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                ItemStack i = player.getInventory().getItemInMainHand();
                SlimefunItem sfItem = null;
                if (EquivalencyTech.getInstance().getManagerSupportedPlugins().isInstalledSlimefun()) {
                    sfItem = SlimefunItem.getByItem(i);
                }
                if (sfItem != null) {
                    Double val = Utils.getEMC(plugin, i);
                    if (val != null) {
                        player.sendMessage(Messages.msgCmdEmcDisplay(sfItem.getId(), val));
                        player.sendMessage(Messages.msgCmdEmcDisplayStack(sfItem.getId(), i.getAmount(), val * i.getAmount()));
                    } else {
                        player.sendMessage(Messages.msgCmdEmcNone(plugin));
                    }
                    return;
                }
                if (i.getType() != Material.AIR) {
                    Double val = Utils.getEMC(plugin, i);
                    if (val != null) {
                        String name = ContainerStorage.isCraftable(i, plugin) ? i.getItemMeta().getDisplayName() : i.getType().toString();
                        player.sendMessage(Messages.msgCmdEmcDisplay(name, val));
                        player.sendMessage(Messages.msgCmdEmcDisplayStack(name, i.getAmount(), val * i.getAmount()));
                    } else {
                        player.sendMessage(Messages.msgCmdEmcNone(plugin));
                    }
                } else {
                    player.sendMessage(Messages.msgCmdEmcMustHold(plugin));
                }
            }
        }
    }

    @Subcommand("Emc")
    @Description("Displays the player's emc.")
    public class Emc extends BaseCommand {

        @Default
        public void onDefault(CommandSender sender) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(Messages.messageCommandEmc(plugin, player));
            }
        }
    }

    @Subcommand("GiveItem")
    @CommandPermission("EquiTech.Admin")
    @Description("Gives Debug Items")
    public class GiveItem extends BaseCommand {

        @Default
        public void onDefault(CommandSender sender) {
            if (sender instanceof Player) {
                sender.sendMessage(Messages.messageCommandSelectItem(plugin));
            }
        }

        @Subcommand("TransmutationOrb")
        @CommandCompletion("@players")
        public void onGiveItemOrb(CommandSender sender, OnlinePlayer player) {
            if (sender instanceof Player) {
                Utils.givePlayerOrb(plugin, player.getPlayer());
            }
        }

        @Subcommand("DissolutionChest")
        @CommandCompletion("@players")
        public void onGiveItemDChest(CommandSender sender, OnlinePlayer player) {
            if (sender instanceof Player) {
                Utils.givePlayerDChest(plugin, player.getPlayer());
            }
        }

        @Subcommand("CondensateChest")
        @CommandCompletion("@players")
        public void onGiveItemCChest(CommandSender sender, OnlinePlayer player) {
            if (sender instanceof Player) {
                Utils.givePlayerCChest(plugin, player.getPlayer());
            }
        }
    }

    /**
     * Sanea solo indices que ya apuntan a un bloque que no es cofre. No fuerza cargas de chunks,
     * no altera bloques ni inventarios y deja intactos los cofres sin propietario para revision.
     */
    @Subcommand("MigrateChests")
    @CommandPermission("EquiTech.Admin")
    @Description("Preview or safely migrate stale EMC chest indexes")
    public class MigrateChests extends BaseCommand {

        @Default
        public void preview(CommandSender sender) {
            MigrationScan scan = scan();
            sender.sendMessage("EquivalencyTech migration preview: candidates=" + scan.candidates.size()
                + ", preserved-chests=" + scan.liveChests + ", unloaded=" + scan.unloaded
                + ". Run /et migratechests apply to back up and remove candidates only.");
        }

        @Subcommand("apply")
        public void apply(CommandSender sender) {
            MigrationScan scan = scan();
            if (scan.candidates.isEmpty()) {
                sender.sendMessage("EquivalencyTech migration: no verified stale indexes to remove.");
                return;
            }
            try {
                String backup = plugin.getConfigMainClass().backupChestStoresForMigration();
                for (MigrationEntry entry : scan.candidates) {
                    if (entry.dissolution) {
                        ConfigMain.removeDChestStore(plugin, entry.id);
                        ConfigMain.removeDChest(plugin, entry.id);
                    } else {
                        ConfigMain.removeCChestStore(plugin, entry.id);
                        ConfigMain.removeCChest(plugin, entry.id);
                    }
                }
                plugin.getConfigMainClass().saveChestMigration();
                sender.sendMessage("EquivalencyTech migration applied: removed=" + scan.candidates.size()
                    + ", backup=" + backup + ". No blocks or inventories were changed.");
            } catch (IOException e) {
                plugin.getLogger().warning("Chest migration aborted before mutation: backup failed: " + e.getMessage());
                sender.sendMessage("EquivalencyTech migration aborted: unable to create a complete backup.");
            }
        }

        private MigrationScan scan() {
            MigrationScan scan = new MigrationScan();
            scan(false, ConfigMain.getDChestIds(plugin), scan);
            scan(true, ConfigMain.getCChestIds(plugin), scan);
            return scan;
        }

        private void scan(boolean condensate, List<Integer> ids, MigrationScan scan) {
            for (Integer id : ids) {
                Location location = condensate ? ConfigMain.getCChestLocation(plugin, id)
                    : ConfigMain.getDChestLocation(plugin, id);
                if (location == null || location.getWorld() == null
                    || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    scan.unloaded++;
                    continue;
                }
                if (location.getBlock().getState() instanceof Chest) {
                    scan.liveChests++;
                    continue;
                }
                scan.candidates.add(new MigrationEntry(id, !condensate));
            }
        }
    }

    private static final class MigrationScan {
        private final List<MigrationEntry> candidates = new ArrayList<>();
        private int liveChests;
        private int unloaded;
    }

    private static final class MigrationEntry {
        private final int id;
        private final boolean dissolution;

        private MigrationEntry(int id, boolean dissolution) {
            this.id = id;
            this.dissolution = dissolution;
        }
    }

}
