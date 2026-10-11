package io.github.sefiraat.equivalencytech.item;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.item.builders.AeternalisFuel;
import io.github.sefiraat.equivalencytech.item.builders.AlchemicalCoal;
import io.github.sefiraat.equivalencytech.item.builders.CondensatorChest;
import io.github.sefiraat.equivalencytech.item.builders.DarkMatter;
import io.github.sefiraat.equivalencytech.item.builders.DissolutionChest;
import io.github.sefiraat.equivalencytech.item.builders.MobiusFuel;
import io.github.sefiraat.equivalencytech.item.builders.RedMatter;
import io.github.sefiraat.equivalencytech.item.builders.TransmutationOrb;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class EQItems {

    private final EquivalencyTech plugin;
    private final TransmutationOrb transmutationOrb;
    private final AlchemicalCoal alchemicalCoal;
    private final MobiusFuel mobiusFuel;
    private final AeternalisFuel aeternalisFuel;
    private final DarkMatter darkMatter;
    private final RedMatter redMatter;
    private final DissolutionChest dissolutionChest;
    private final CondensatorChest condensatorChest;
    private final Map<String, ItemStack> eqItemMap;
    /** Id PDC (COAL1, DMATTER...) -> nombre de configuracion, la clave de eqItemMap. */
    private final Map<String, String> eqNameById;

    public EquivalencyTech getPlugin() {
        return plugin;
    }

    public TransmutationOrb getTransmutationOrb() {
        return transmutationOrb;
    }

    public AlchemicalCoal getAlchemicalCoal() {
        return alchemicalCoal;
    }

    public MobiusFuel getMobiusFuel() {
        return mobiusFuel;
    }

    public AeternalisFuel getAeternalisFuel() {
        return aeternalisFuel;
    }

    public DarkMatter getDarkMatter() {
        return darkMatter;
    }

    public RedMatter getRedMatter() {
        return redMatter;
    }

    public DissolutionChest getDissolutionChest() {
        return dissolutionChest;
    }

    public CondensatorChest getCondensatorChest() {
        return condensatorChest;
    }

    public Map<String, ItemStack> getEqItemMap() {
        return eqItemMap;
    }

    public String getEqNameById(String id) {
        return id == null ? null : eqNameById.get(id);
    }

    public EQItems(EquivalencyTech plugin) {
        this.plugin = plugin;
        transmutationOrb = new TransmutationOrb(plugin);
        alchemicalCoal = new AlchemicalCoal(plugin);
        mobiusFuel = new MobiusFuel(plugin);
        aeternalisFuel = new AeternalisFuel(plugin);
        darkMatter = new DarkMatter(plugin);
        redMatter = new RedMatter(plugin);
        dissolutionChest = new DissolutionChest(plugin);
        condensatorChest = new CondensatorChest(plugin);

        eqItemMap = new HashMap<>();
        eqNameById = new HashMap<>();
        ItemStack[] items = {
            transmutationOrb.getItem(), alchemicalCoal.getItem(), mobiusFuel.getItem(),
            aeternalisFuel.getItem(), darkMatter.getItem(), redMatter.getItem(),
            dissolutionChest.getItem(), condensatorChest.getItem()
        };
        for (ItemStack item : items) {
            String name = Utils.eqNameConfig(item.getItemMeta().getDisplayName());
            eqItemMap.put(name, item.clone());
            eqNameById.put(ContainerStorage.getItemID(item, plugin), name);
        }

    }


}
