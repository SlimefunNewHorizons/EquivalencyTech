package io.github.sefiraat.equivalencytech.misc;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.item.builders.CondensatorChest;
import io.github.sefiraat.equivalencytech.item.builders.DissolutionChest;
import io.github.sefiraat.equivalencytech.item.builders.TransmutationOrb;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import io.github.sefiraat.equivalencytech.statics.Messages;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class Utils {

    private static final Set<Material> BLACKLISTED_MATERIALS = EnumSet.of(
            Material.DIAMOND,
            Material.DIAMOND_BLOCK,
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.DIAMOND_SWORD,
            Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL,
            Material.DIAMOND_HOE,
            Material.DIAMOND_HORSE_ARMOR,
            Material.NETHERITE_INGOT,
            Material.NETHERITE_BLOCK,
            Material.NETHERITE_SCRAP,
            Material.ANCIENT_DEBRIS,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            Material.NETHERITE_SWORD,
            Material.NETHERITE_PICKAXE,
            Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL,
            Material.NETHERITE_HOE,
            Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            Material.EMERALD,
            Material.EMERALD_BLOCK,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.TOTEM_OF_UNDYING,
            Material.ELYTRA,
            Material.NETHER_STAR,
            Material.BEACON,
            Material.DRAGON_EGG
    );

    private static final Set<String> BLACKLISTED_SLIMEFUN_IDS = Set.of(
            "REINFORCED_ALLOY", "REINFORCED_PLATE", "CARBONADO",
            "BLISTERING_INGOT", "BLISTERING_INGOT_2", "BLISTERING_INGOT_3",
            "DAMASCUS_STEEL", "DURALUMIN", "CORINTHIAN_BRONZE",
            "SOLDER_INGOT", "BILLON_INGOT", "REDSTONE_ALLOY",
            "HARDENED_METAL_INGOT", "ALUMINUM_BRONZE_INGOT", "STEEL_INGOT",
            "GOLD_24K", "GOLD_22K", "GOLD_20K", "GOLD_18K", "GOLD_16K", "GOLD_14K", "GOLD_12K", "GOLD_10K", "GOLD_8K", "GOLD_6K", "GOLD_4K",
            "SYNTHETIC_DIAMOND", "SYNTHETIC_EMERALD", "SYNTHETIC_SAPPHIRE",
            "CARBON", "COMPRESSED_CARBON", "CARBON_CHUNK",
            "FERROSILICON", "URANIUM", "NEO_URANIUM", "BOOSTED_URANIUM",
            "REACTOR_COOLANT_CELL", "NETHER_ICE_COOLANT_CELL",
            "ENDER_LUMP_1", "ENDER_LUMP_2", "ENDER_LUMP_3"
    );

    private Utils() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isBlacklisted(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return true;
        }
        if (BLACKLISTED_MATERIALS.contains(itemStack.getType())) {
            return true;
        }
        try {
            if (EquivalencyTech.getInstance() != null
                    && EquivalencyTech.getInstance().getManagerSupportedPlugins() != null
                    && EquivalencyTech.getInstance().getManagerSupportedPlugins().isInstalledSlimefun()) {
                SlimefunItem sfItem = SlimefunItem.getByItem(itemStack);
                if (sfItem != null) {
                    return isBlacklistedSlimefunId(sfItem.getId());
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean isBlacklistedMaterial(Material material) {
        return material != null && BLACKLISTED_MATERIALS.contains(material);
    }

    public static boolean isBlacklistedSlimefunId(String sfId) {
        if (sfId == null) return false;
        String id = sfId.toUpperCase(Locale.ROOT);
        if (BLACKLISTED_SLIMEFUN_IDS.contains(id)) {
            return true;
        }
        return id.contains("INFINITY") || id.contains("SUPREME") || id.contains("QUARRY");
    }

    public static Double getEMC(EquivalencyTech plugin, ItemStack itemStack) {
        if (isBlacklisted(itemStack)) {
            return null;
        }
        SlimefunItem sfItem = null;
        if (EquivalencyTech.getInstance().getManagerSupportedPlugins().isInstalledSlimefun()) {
            sfItem = SlimefunItem.getByItem(itemStack);
        }
        if (sfItem != null) {
            return plugin.getEmcDefinitions().getEmcSlimefun().get(sfItem.getId());
        }
        if (ContainerStorage.isCraftable(itemStack, plugin)) {
            ItemStack eqStack = plugin.getEqItems().getEqItemMap().get(eqNameConfig(itemStack.getItemMeta().getDisplayName()));
            return plugin.getEmcDefinitions().getEmcEQ().get(eqStack.getItemMeta().getDisplayName());
        } else {
            return plugin.getEmcDefinitions().getEmcExtended().get(itemStack.getType());
        }
    }

    public static String eqNameConfig(String name) {
        return ChatColor.stripColor(name.replace(" ","_"));
    }

    public static String toTitleCase(String string) {
        final char[] delimiters = { ' ', '_' };
        return WordUtils.capitalizeFully(string, delimiters);
    }

    public static String materialFriendlyName(Material m) {
        return toTitleCase(m.name().replace("_", " "));
    }

    public static void givePlayerOrb(EquivalencyTech plugin, Player player) {
        TransmutationOrb i = plugin.getEqItems().getTransmutationOrb();
        player.getPlayer().getInventory().addItem(i.getItemClone());
        player.getPlayer().sendMessage(Messages.messageCommandItemGiven(plugin, i.getItem().getItemMeta().getDisplayName()));
    }

    public static void givePlayerDChest(EquivalencyTech plugin, Player player) {
        DissolutionChest i = plugin.getEqItems().getDissolutionChest();
        player.getPlayer().getInventory().addItem(i.getItemClone());
        player.getPlayer().sendMessage(Messages.messageCommandItemGiven(plugin, i.getItem().getItemMeta().getDisplayName()));
    }

    public static void givePlayerCChest(EquivalencyTech plugin, Player player) {
        CondensatorChest i = plugin.getEqItems().getCondensatorChest();
        player.getPlayer().getInventory().addItem(i.getItemClone());
        player.getPlayer().sendMessage(Messages.messageCommandItemGiven(plugin, i.getItem().getItemMeta().getDisplayName()));
    }

    public static double roundDown(double number, int places) {
        BigDecimal value = BigDecimal.valueOf(number);
        value = value.setScale(places, RoundingMode.DOWN);
        return value.doubleValue();
    }

    public static int totalRecipes(EquivalencyTech plugin) {
        int recExtended = plugin.getEmcDefinitions().getEmcExtended().size();
        int recEQ = plugin.getEmcDefinitions().getEmcEQ().size();
        return recExtended + recEQ;
    }

    public static boolean canBeSynth(EquivalencyTech plugin, ItemStack itemStack) {
        if (isBlacklisted(itemStack)) {
            return false;
        }
        if (itemStack.hasItemMeta()) {
            SlimefunItem sfItem = null;
            if (EquivalencyTech.getInstance().getManagerSupportedPlugins().isInstalledSlimefun()) {
                sfItem = SlimefunItem.getByItem(itemStack);
            }
            return ContainerStorage.isCraftable(itemStack, plugin) || sfItem != null;
        } else {
            return true;
        }
    }

    public static boolean isBlockedWorld(EquivalencyTech plugin, org.bukkit.World world) {
        if (world == null || plugin == null) {
            return false;
        }
        return isBlockedWorld(plugin.getConfig().getStringList("BLOCKED_WORLDS"), world.getName());
    }

    public static boolean isBlockedWorld(java.util.List<String> blockedWorlds, String worldName) {
        if (worldName == null || blockedWorlds == null) {
            return false;
        }
        String normalized = worldName.toLowerCase(java.util.Locale.ROOT);
        for (String b : blockedWorlds) {
            if (b != null && normalized.equalsIgnoreCase(b.trim())) {
                return true;
            }
        }
        return false;
    }

}
