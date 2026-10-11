package io.github.sefiraat.equivalencytech.listeners;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.item.EQItems;
import io.github.sefiraat.equivalencytech.recipes.Recipes;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CraftListener implements Listener {

    private final EquivalencyTech plugin;

    /**
     * Recetas EQ y su resultado, en el mismo orden de comprobacion que antes. Son fijas tras el
     * arranque: antes se reconstruian en cada PrepareItemCraftEvent (unas 45 copias de cabezas
     * por cada cambio en la cuadricula de cualquier jugador).
     */
    private final List<ItemStack[]> patterns = new ArrayList<>();
    private final List<ItemStack> results = new ArrayList<>();

    public CraftListener(EquivalencyTech plugin) {
        this.plugin = plugin;
        EQItems eq = plugin.getEqItems();
        addPattern(Recipes.recipeCoal1Check(), eq.getAlchemicalCoal().getItem());
        addPattern(Recipes.recipeCoal2Check(plugin), eq.getMobiusFuel().getItem());
        addPattern(Recipes.recipeCoal3Check(plugin), eq.getAeternalisFuel().getItem());
        addPattern(Recipes.recipeDarkMatterCheck(plugin), eq.getDarkMatter().getItem());
        addPattern(Recipes.recipeRedMatterCheck(plugin), eq.getRedMatter().getItem());
        addPattern(Recipes.recipeTransmutationOrbCheck(plugin), eq.getTransmutationOrb().getItem());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void addPattern(List<ItemStack> pattern, ItemStack result) {
        patterns.add(pattern.toArray(new ItemStack[0]));
        results.add(result);
    }

    @EventHandler
    public void onPreCraft(PrepareItemCraftEvent e) {

        if (e.getView().getPlayer() instanceof Player && e.getRecipe() != null) {

            ItemStack[] contents = e.getInventory().getMatrix();

            // Todas las recetas EQ son de 3x3: la cuadricula 2x2 del inventario no puede casar.
            if (contents.length == 9) {
                for (int r = 0; r < patterns.size(); r++) {
                    if (matches(contents, patterns.get(r))) {
                        e.getInventory().setResult(results.get(r).clone());
                        return;
                    }
                }
            }

            if (contentsEQ(contents)) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    /**
     * Equivale a la comparacion anterior (List.equals contra copias con cantidad 1): un hueco
     * vacio solo casa con otro vacio y una pila casa si es similar, sin mirar la cantidad.
     */
    private static boolean matches(ItemStack[] contents, ItemStack[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            ItemStack actual = contents[i];
            ItemStack expected = pattern[i];
            if (actual == null || expected == null) {
                if (actual != expected) {
                    return false;
                }
            } else if (!actual.isSimilar(expected)) {
                return false;
            }
        }
        return true;
    }

    private boolean contentsEQ(ItemStack[] itemStacks) {
        for (ItemStack itemStack : itemStacks) {
            if (ContainerStorage.isCraftable(itemStack, plugin)) {
                return true;
            }
        }
        return false;
    }
}
