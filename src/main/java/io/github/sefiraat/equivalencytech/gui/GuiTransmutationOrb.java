package io.github.sefiraat.equivalencytech.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.configuration.ConfigMain;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import io.github.sefiraat.equivalencytech.statics.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Inventario nativo del orbe de transmutacion.
 *
 * <p>TriumphGUI 3.x inicializa reflexion contra campos eliminados de
 * CraftMetaItem en Paper 1.21.11. Esta implementacion conserva la mecanica y la
 * distribucion originales sin depender de internals de CraftBukkit.</p>
 */
public final class GuiTransmutationOrb implements InventoryHolder {

    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 36;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 46;
    private static final int INPUT_SLOT = 49;
    private static final int FORWARD_SLOT = 52;
    private static final List<Integer> BORDER_SLOTS = Arrays.asList(
        0, 1, 2, 3, 5, 6, 7, 8, 45, 47, 48, 50, 51, 53
    );

    private final EquivalencyTech plugin;
    private final Player player;
    private final Inventory inventory;
    private int page;

    private GuiTransmutationOrb(EquivalencyTech plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        String title = Messages.THEME_EMC_PURPLE
            + plugin.getConfigMainClass().getStrings().getItemTransmutationOrbName();
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);
        render();
    }

    public static GuiTransmutationOrb buildGui(EquivalencyTech plugin, Player player) {
        return new GuiTransmutationOrb(plugin, player);
    }

    public void open(Player target) {
        target.openInventory(inventory);
    }

    @Override
    @Nonnull
    public Inventory getInventory() {
        return inventory;
    }

    /** Redibuja una pagina completa y evita conservar botones u objetos viejos. */
    private void render() {
        inventory.clear();
        ItemStack border = GUIItems.guiOrbBorder(plugin);
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, border);
        }
        inventory.setItem(INFO_SLOT, GUIItems.guiOrbInfo(plugin, player));
        inventory.setItem(BACK_SLOT, navigationItem("Previous"));
        inventory.setItem(FORWARD_SLOT, navigationItem("Next"));

        List<ItemStack> learnedItems = getLearnedItems();
        int maxPage = maxPage(learnedItems.size());
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * PAGE_SIZE;
        for (int offset = 0; offset < PAGE_SIZE; offset++) {
            int itemIndex = start + offset;
            ItemStack item = itemIndex < learnedItems.size()
                ? learnedItems.get(itemIndex)
                : GUIItems.guiOrbFiller(plugin);
            inventory.setItem(9 + offset, item);
        }
    }

    /** Convierte los identificadores aprendidos en copias seguras para la GUI. */
    private List<ItemStack> getLearnedItems() {
        List<ItemStack> items = new ArrayList<>();
        for (String id : ConfigMain.getLearnedItems(plugin, player.getUniqueId().toString())) {
            ItemStack item = resolveLearnedItem(id);
            if (item == null || Utils.getEMC(plugin, item) == null) {
                continue;
            }
            boolean vanilla = plugin.getEqItems().getEqItemMap().get(id) == null
                && SlimefunItem.getById(id) == null;
            items.add(GUIItems.guiEMCItem(plugin, item, vanilla));
        }
        return items;
    }

    private ItemStack resolveLearnedItem(String id) {
        SlimefunItem slimefunItem = null;
        if (plugin.getManagerSupportedPlugins().isInstalledSlimefun()) {
            slimefunItem = SlimefunItem.getById(id);
        }
        if (slimefunItem != null) {
            return slimefunItem.getItem().clone();
        }

        ItemStack equivalencyItem = plugin.getEqItems().getEqItemMap().get(id);
        if (equivalencyItem != null) {
            return equivalencyItem.clone();
        }

        Material material = Material.matchMaterial(id);
        return material == null ? null : new ItemStack(material);
    }

    private static int maxPage(int itemCount) {
        return Math.max(0, (itemCount - 1) / PAGE_SIZE);
    }

    private static ItemStack navigationItem(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    /** Procesa solamente clics pertenecientes a este inventario. */
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clickingPlayer)
            || !clickingPlayer.getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < INVENTORY_SIZE) {
            event.setCancelled(true);
            if (rawSlot == BACK_SLOT && page > 0) {
                page--;
                render();
            } else if (rawSlot == FORWARD_SLOT && page < maxPage(getLearnedItems().size())) {
                page++;
                render();
            } else if (rawSlot == INPUT_SLOT) {
                inputItemAction(event, false);
            } else if (rawSlot >= 9 && rawSlot < 45) {
                emcItemClicked(event);
            }
            return;
        }

        // Shift-click desde el inventario del jugador alimenta el orbe igual
        // que en la GUI anterior, pero no permite que Bukkit mueva el stack.
        if (event.isShiftClick()) {
            event.setCancelled(true);
            inputItemAction(event, true);
        }
    }

    private void inputItemAction(InventoryClickEvent event, boolean shifted) {
        ItemStack itemStack = shifted ? event.getCurrentItem() : player.getItemOnCursor();
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }

        boolean equivalencyItem = ContainerStorage.isCraftable(itemStack, plugin);
        SlimefunItem slimefunItem = plugin.getManagerSupportedPlugins().isInstalledSlimefun()
            ? SlimefunItem.getByItem(itemStack)
            : null;
        if (itemStack.hasItemMeta() && !equivalencyItem && slimefunItem == null) {
            player.sendMessage(Messages.messageGuiItemMeta(plugin));
            return;
        }

        Double emcValue = Utils.getEMC(plugin, itemStack);
        if (emcValue == null) {
            player.sendMessage(Messages.msgCmdEmcNone(plugin));
            return;
        }

        String entryName = itemEntryName(itemStack, equivalencyItem, slimefunItem);
        boolean learned = ConfigMain.getLearnedItems(plugin, player.getUniqueId().toString()).contains(entryName);
        if (!learned) {
            ConfigMain.addLearnedItem(plugin, player.getUniqueId().toString(), entryName);
            player.sendMessage(Messages.messageGuiItemLearned(plugin));
        }
        int count = itemStack.getAmount();
        ConfigMain.addPlayerEmc(plugin, player, emcValue, emcValue * count, count);
        itemStack.setAmount(0);
        if (shifted) {
            event.setCurrentItem(null);
        } else {
            player.setItemOnCursor(null);
        }

        if (!learned) {
            player.closeInventory();
        } else {
            render();
        }
    }

    private void emcItemClicked(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()
            || clickedItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
            return;
        }
        if (event.isLeftClick()) {
            withdraw(clickedItem, 1);
        } else if (event.isRightClick()) {
            withdraw(clickedItem, clickedItem.getMaxStackSize());
        }
    }

    /** Retira hasta requestedAmount sin crear objetos cuando no hay EMC o espacio. */
    private void withdraw(ItemStack clickedItem, int requestedAmount) {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Messages.messageGuiNoSpace(plugin));
            return;
        }

        Double unitEmc = Utils.getEMC(plugin, clickedItem);
        if (unitEmc == null || unitEmc <= 0) {
            player.sendMessage(Messages.msgCmdEmcNone(plugin));
            return;
        }

        int affordable = (int) Math.floor(ConfigMain.getPlayerEmc(plugin, player) / unitEmc);
        int amount = Math.min(requestedAmount, affordable);
        if (amount <= 0) {
            player.sendMessage(Messages.messageGuiEmcNotEnough(plugin, player));
            return;
        }

        boolean equivalencyItem = ContainerStorage.isCraftable(clickedItem, plugin);
        SlimefunItem slimefunItem = plugin.getManagerSupportedPlugins().isInstalledSlimefun()
            ? SlimefunItem.getByItem(clickedItem)
            : null;
        String itemName = itemEntryName(clickedItem, equivalencyItem, slimefunItem);
        ItemStack output;
        if (equivalencyItem) {
            ItemStack configured = plugin.getEqItems().getEqItemMap().get(itemName);
            if (configured == null) {
                player.sendMessage(Messages.msgCmdEmcNone(plugin));
                return;
            }
            output = configured.clone();
        } else if (slimefunItem != null) {
            output = slimefunItem.getItem().clone();
        } else {
            output = new ItemStack(clickedItem.getType());
        }

        output.setAmount(amount);
        java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(output);
        int delivered = amount;
        if (!overflow.isEmpty()) {
            int failed = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            delivered -= failed;
        }
        if (delivered <= 0) {
            player.sendMessage(Messages.messageGuiNoSpace(plugin));
            return;
        }
        double totalEmc = unitEmc * delivered;
        ConfigMain.removePlayerEmc(plugin, player, totalEmc);
        player.sendMessage(Messages.messageGuiEmcRemoved(plugin, player, unitEmc, totalEmc, delivered));
        render();
    }

    private static String itemEntryName(ItemStack itemStack, boolean equivalencyItem, SlimefunItem slimefunItem) {
        if (equivalencyItem) {
            return Utils.eqNameConfig(itemStack.getItemMeta().getDisplayName());
        }
        if (slimefunItem != null) {
            return slimefunItem.getId();
        }
        return itemStack.getType().toString();
    }
}
