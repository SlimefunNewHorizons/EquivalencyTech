package io.github.sefiraat.equivalencytech.recipes;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import io.github.sefiraat.equivalencytech.statics.DebugLogs;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmcDefinitions {

    private final Map<Material, Double> emcBase = new EnumMap<> (Material.class);
    private final Map<String, Double> emcSFBase = new HashMap<>();
    private final Map<Material, Double> emcExtended = new EnumMap<> (Material.class);
    private final Map<String, Double> emcEQ = new HashMap<>();
    private final Map<String, Double> emcSlimefun = new HashMap<>();
    private boolean recipeRegistryCompatible = true;
    /**
     * Materiales vanilla que se están resolviendo en la rama actual de recetas.
     * Las recetas de Paper pueden contener ciclos (por ejemplo, una receta alternativa
     * que vuelve a pedir el resultado); sin este corte el cálculo de EMC bloquea el
     * hilo de arranque antes de que el límite de profundidad alcance a protegerlo.
     */
    private final java.util.Set<Material> emcVanillaEnCurso = EnumSet.noneOf(Material.class);
    /**
     * Valores vanilla intermedios ya resueltos (sin redondear). Sin esta memoria cada
     * material de la tanda recorría de nuevo todo el árbol de recetas, con una consulta
     * {@code getRecipesFor} por nodo: en Paper 26.2 eso dejó el hilo principal >10 s
     * colgado (watchdog, ticket #104). Solo se guardan resultados que no dependen de
     * un corte de ciclo ni del límite de profundidad. La clave es la pila (cantidad 1)
     * y no el material: {@code getRecipesFor} depende de la durabilidad, y los
     * ingredientes de varias opciones llegan con durabilidad comodín.
     */
    private final Map<ItemStack, Double> emcVanillaMemo = new HashMap<>();
    private final java.util.Set<ItemStack> emcVanillaSinValor = new java.util.HashSet<>();
    private int emcVanillaCortes = 0;
    /**
     * Recetas vanilla agrupadas por material del resultado. En Paper 26.2 cada
     * {@code getRecipesFor} recorre y materializa el registro completo; con los ~1300
     * recetas que añade SaneCrafting, una sola tanda encadenaba cientos de recorridos y
     * el watchdog saltaba 38 veces en staging. El índice se construye una vez, en un único
     * tick (el coste de un solo {@code getRecipesFor}), y conserva su semántica de filtro.
     */
    @Nullable
    private Map<Material, List<Recipe>> indiceRecetas;
    /** Presupuesto por tick para la fase de materiales, una vez que el índice existe. */
    private static final long PRESUPUESTO_TICK_NANOS = 20_000_000L;

    public Map<Material, Double> getEmcExtended() {
        return emcExtended;
    }

    public Map<String, Double> getEmcEQ() {
        return emcEQ;
    }

    public Map<String, Double> getEmcSlimefun() {
        return emcSlimefun;
    }

    public EmcDefinitions(EquivalencyTech plugin) {
        fillBase(plugin);
        fillSpecialCases();
        fillEQItems(plugin);
    }

    public void calcularSlimefunPorTandas(EquivalencyTech plugin) {
        if (!EquivalencyTech.getInstance().getManagerSupportedPlugins().isInstalledSlimefun()) {
            return;
        }
        final java.util.List<SlimefunItem> pendientes = new java.util.ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item instanceof SlimefunBackpack || !item.getAddon().getName().equals("Slimefun")) {
                continue;
            }
            if (Utils.isBlacklistedSlimefunId(item.getId())) {
                continue;
            }
            pendientes.add(item);
        }
        plugin.getLogger().info("Calculando EMC de " + pendientes.size()
                + " objetos de Slimefun en segundo plano...");

        final int porTanda = 25;
        final long inicio = System.currentTimeMillis();
        final int[] indice = {0};
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, tarea -> {
            int fin = Math.min(indice[0] + porTanda, pendientes.size());
            for (int i = indice[0]; i < fin; i++) {
                SlimefunItem item = pendientes.get(i);
                try {
                    Double emcValue = getSFEmcValue(plugin, item.getItem(), 1);
                    if (emcValue != null && emcValue != 0D) {
                        emcSlimefun.put(item.getId(), roundDown(emcValue, 2));
                    }
                } catch (Exception | StackOverflowError e) {
                    plugin.getLogger().warning("EMC de " + item.getId() + " no se pudo calcular: "
                            + e.getClass().getSimpleName());
                }
            }
            indice[0] = fin;
            if (fin >= pendientes.size()) {
                plugin.getLogger().info("EMC calculado: " + emcSlimefun.size() + " objetos en "
                        + ((System.currentTimeMillis() - inicio) / 1000) + "s");
                tarea.cancel();
            }
        }, 200L, 1L);
    }

    private void fillBase(EquivalencyTech plugin) {
        Map<String, Double> h = plugin.getConfigMainClass().getEmc().getEmcBaseValues();
        for (Map.Entry<String, Double> entry : h.entrySet()) {
            Material mat = Material.matchMaterial(entry.getKey());
            if (mat == null || Utils.isBlacklistedMaterial(mat)) {
                continue;
            }
            emcBase.put(mat, entry.getValue());
            DebugLogs.logEmcBaseValueLoaded(plugin, entry.getKey(), entry.getValue());
        }
        Map<String, Double> slimefunBase = plugin.getConfigMainClass().getEmc().getEmcSlimefunValues();
        for (Map.Entry<String, Double> entry : slimefunBase.entrySet()) {
            if (Utils.isBlacklistedSlimefunId(entry.getKey())) {
                continue;
            }
            emcSFBase.put(entry.getKey(), entry.getValue());
            DebugLogs.logEmcBaseValueLoaded(plugin, entry.getKey(), entry.getValue());
        }
    }

    private void fillSpecialCases() {
        emcExtended.put(Material.DRIED_KELP, specialCaseDriedKelp());
        emcExtended.put(Material.BONE_MEAL, specialCaseBoneMeal());
    }

    private Double specialCaseDriedKelp() {
        return emcBase.get(Material.KELP);
    }
    private Double specialCaseBoneMeal() {
        return emcBase.get(Material.BONE) != null ? emcBase.get(Material.BONE) / 3 : null;
    }

    /**
     * Paper 26.2 materializa recetas al consultar {@code getRecipesFor}. Hacerlo para
     * todos los materiales desde onEnable bloquea el arranque. Se conserva el cálculo
     * completo, pero se reparte una consulta por tick una vez que Paper ya está vivo.
     */
    public void calcularVanillaPorTandas(EquivalencyTech plugin, Runnable alFinalizar) {
        final List<Material> pendientes = new java.util.ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && material.isItem() && !Utils.isBlacklistedMaterial(material)) {
                pendientes.add(material);
            }
        }
        plugin.getLogger().info("Calculando EMC vanilla de " + pendientes.size()
                + " materiales por tandas tras el arranque...");

        final int[] indice = {0};
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, tarea -> {
            if (indiceRecetas == null) {
                // Tick propio para el índice: el iterador es una vista viva del registro y
                // no puede repartirse entre ticks sin riesgo de ConcurrentModificationException.
                indiceRecetas = indexarRecetasVanilla(plugin);
                return;
            }
            final long limite = System.nanoTime() + PRESUPUESTO_TICK_NANOS;
            do {
                Material material = pendientes.get(indice[0]++);
                ItemStack item = new ItemStack(material);
                Double emcValue = getEmcValue(plugin, item, 1);
                if (emcValue != null) {
                    DebugLogs.logEmcPosted(plugin, emcValue, 1);
                    emcExtended.put(item.getType(), roundDown(emcValue, 2));
                } else {
                    DebugLogs.logEmcNull(plugin, 1);
                }
            } while (indice[0] < pendientes.size() && System.nanoTime() < limite);
            if (indice[0] >= pendientes.size()) {
                plugin.getLogger().info("EMC vanilla calculado: " + emcExtended.size() + " materiales.");
                tarea.cancel();
                alFinalizar.run();
            }
        }, 1L, 1L);
    }

    /**
     * Recorre una sola vez el registro de recetas. Las recetas cuyo resultado es un objeto
     * de Slimefun (p. ej. las de la mesa mejorada convertidas por SaneCrafting) no definen
     * el valor de un material vanilla y se excluyen; así el resultado no depende de si otro
     * addon terminó de registrar recetas antes o después de este recorrido. Una receta que
     * Paper no puede materializar se omite sola en lugar de anular todo el cálculo: el
     * iterador ya avanzó antes de convertirla.
     */
    private Map<Material, List<Recipe>> indexarRecetasVanilla(EquivalencyTech plugin) {
        final long inicio = System.nanoTime();
        final Map<Material, List<Recipe>> indice = new EnumMap<>(Material.class);
        final java.util.Iterator<Recipe> recetas = Bukkit.recipeIterator();
        int total = 0;
        int deSlimefun = 0;
        int incompatibles = 0;
        while (recetas.hasNext()) {
            final Recipe receta;
            try {
                receta = recetas.next();
            } catch (IllegalArgumentException exception) {
                incompatibles++;
                continue;
            }
            final ItemStack resultado = receta.getResult();
            if (SlimefunItem.getByItem(resultado) != null) {
                deSlimefun++;
                continue;
            }
            indice.computeIfAbsent(resultado.getType(), material -> new java.util.ArrayList<>()).add(receta);
            total++;
        }
        plugin.getLogger().info("Índice de recetas EMC: " + total + " recetas vanilla en "
                + ((System.nanoTime() - inicio) / 1_000_000L) + " ms (" + deSlimefun
                + " con resultado Slimefun excluidas, " + incompatibles + " incompatibles omitidas).");
        return indice;
    }

    /** Misma regla que {@code CraftServer#getRecipesFor}: tipo igual y durabilidad igual o comodín (-1). */
    private List<Recipe> recetasPara(ItemStack item) {
        final List<Recipe> candidatas = indiceRecetas.getOrDefault(item.getType(), java.util.Collections.emptyList());
        if (item.getDurability() == -1) {
            return candidatas;
        }
        final List<Recipe> resultado = new java.util.ArrayList<>(candidatas.size());
        for (Recipe receta : candidatas) {
            if (receta.getResult().getDurability() == item.getDurability()) {
                resultado.add(receta);
            }
        }
        return resultado;
    }

    public boolean isRecipeRegistryCompatible() {
        return recipeRegistryCompatible;
    }

    private void fillEQItems(EquivalencyTech plugin) {
        for (Map.Entry<List<ItemStack>, ItemStack> recipeMap : Recipes.getEQRecipes(plugin).entrySet()) {
            ItemStack checkedItem = recipeMap.getValue();
            if (checkedItem == null || checkedItem.getItemMeta() == null) continue;
            DebugLogs.logBoring(plugin, checkedItem.getItemMeta().getDisplayName());
            Double itemAmount = 0D;
            for (ItemStack recipeItem : recipeMap.getKey()) {
                Double testAmount = getEQEmcValue(plugin, recipeItem, 1);
                if (testAmount != null) {
                    itemAmount += testAmount;
                }
            }
            DebugLogs.logBoring(plugin, checkedItem.getItemMeta().getDisplayName() + " added to EQ for : " + itemAmount);
            emcEQ.put(checkedItem.getItemMeta().getDisplayName(), roundDown(itemAmount,2));
        }
    }

    private final java.util.Set<String> enCurso = new java.util.HashSet<>();
    private static final int PROFUNDIDAD_MAXIMA = 32;

    private Double getSFEmcValue(EquivalencyTech plugin, ItemStack item, Integer nestLevel) {
        if (item == null || Utils.isBlacklisted(item) || nestLevel > PROFUNDIDAD_MAXIMA) {
            return null;
        }
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem == null) { // Vanilla
            DebugLogs.logBoring(plugin, item.getType().toString() + StringUtils.repeat(" >", nestLevel) + " Vanilla - getting found vanilla value");
            return getEmcValue(plugin, item, nestLevel + 1);
        }
        if (Utils.isBlacklistedSlimefunId(sfItem.getId())) {
            return null;
        }
        if (emcSFBase.containsKey(sfItem.getId())) {
            DebugLogs.logBoring(plugin, sfItem.getId() + StringUtils.repeat(" >", nestLevel) + " Item in SF Base");
            Double emcBaseValue = emcSFBase.get(sfItem.getId());
            if (emcBaseValue == null || emcBaseValue == 0) {
                DebugLogs.logBoring(plugin, sfItem.getId() + StringUtils.repeat(" >", nestLevel) + " Zero base val");
                return null;
            } else {
                DebugLogs.logEmcIsBase(plugin, emcBaseValue, nestLevel);
                return emcBaseValue;
            }
        }
        if (emcSlimefun.containsKey(sfItem.getId())) {
            DebugLogs.logBoring(plugin, sfItem.getId() + StringUtils.repeat(" >", nestLevel) + " Already calculated at : " + emcSlimefun.get(sfItem.getId()));
            return emcSlimefun.get(sfItem.getId());
        }
        ItemStack[] recipe = sfItem.getRecipe();
        Double amount = 0D;
        if (recipe == null) {
            DebugLogs.logBoring(plugin, sfItem.getId() + StringUtils.repeat(" >", nestLevel) + " Not in base and no recipes. Nulling out.");
            return null;
        }
        if (!enCurso.add(sfItem.getId())) {
            return null;
        }
        try {
            for (ItemStack recipeItem : recipe) {
                if (recipeItem != null) {
                    DebugLogs.logBoring(plugin, sfItem.getId() + StringUtils.repeat(" >", nestLevel) + " Checking recipe item");
                    Double stackAmount = getSFEmcValue(plugin, recipeItem, nestLevel + 1);
                    if (stackAmount == null) {
                        DebugLogs.logBoring(plugin, sfItem.getId() + StringUtils.repeat(" >", nestLevel) + " Null");
                        return null;
                    }
                    amount += (stackAmount / sfItem.getRecipeOutput().getAmount());
                }
            }
            if (amount == 0D) {
                DebugLogs.logBoring(plugin, sfItem.getId() + StringUtils.repeat(" >", nestLevel) + " Stack is null due to 0. If base items is missing?");
                return null;
            }
            return amount;
        } finally {
            enCurso.remove(sfItem.getId());
        }
    }

    @Nullable
    private Double getEQEmcValue(EquivalencyTech plugin, ItemStack itemStack, Integer nestLevel) {
        if (itemStack != null) {
            if (Utils.isBlacklisted(itemStack)) {
                return null;
            }
            DebugLogs.logEQStart(plugin, nestLevel, itemStack);
            if (ContainerStorage.isCraftable(itemStack, plugin)) {
                double amount = 0D;
                DebugLogs.logEQisCrafting(plugin, nestLevel);
                if (emcEQ.containsKey(itemStack.getItemMeta().getDisplayName())) {
                    amount = getEmcEQ().get(itemStack.getItemMeta().getDisplayName());
                    DebugLogs.logEmcIsRegisteredExtended(plugin, amount, nestLevel);
                } else {
                    List<ItemStack> itemStacks = Recipes.getEQRecipe(plugin, itemStack);
                    for (ItemStack itemStack1 : itemStacks) {
                        if (itemStack1 != null) {
                            Double stackAmount = getEQEmcValue(plugin, itemStack1, nestLevel + 1);
                            if (stackAmount != null) {
                                amount += stackAmount;
                            } else {
                                DebugLogs.logEmcNull(plugin, nestLevel);
                                return null;
                            }
                        }
                    }
                }
                return amount;
            } else {
                DebugLogs.logEQisNotCrafting(plugin, nestLevel);
                return getEmcValue(plugin, itemStack, nestLevel + 1);
            }
        } else {
            return 0D;
        }
    }

    @Nullable
    private Double getEmcValue(EquivalencyTech plugin, ItemStack i, Integer nestLevel) {
        if (i == null || Utils.isBlacklisted(i)) {
            return null;
        }
        if (!recipeRegistryCompatible) {
            return null;
        }
        Material m = i.getType();
        Double eVal = 0D;
        DebugLogs.logEmcTestingItemStack(plugin, i.getType().name(), nestLevel);
        if (nestLevel > 15) {
            emcVanillaCortes++;
            return null;
        }
        if (emcBase.containsKey(m)) {
            Double emcBaseValue = emcBase.get(m);
            if (emcBaseValue == null || emcBaseValue == 0) {
                return null;
            } else {
                DebugLogs.logEmcIsBase(plugin, emcBaseValue, nestLevel);
                return emcBaseValue;
            }
        } else if (emcExtended.containsKey(m)) {
            DebugLogs.logEmcIsRegisteredExtended(plugin, emcExtended.get(m), nestLevel);
            return emcExtended.get(m);
        }
        final ItemStack clave = i.asOne();
        if (emcVanillaMemo.containsKey(clave)) {
            return emcVanillaMemo.get(clave);
        } else if (emcVanillaSinValor.contains(clave)) {
            return null;
        }
        // La consulta al registro es la parte cara en Paper 26.2: se hace solo cuando
        // el material no tiene valor base, extendido ni memorizado.
        List<Recipe> recipeList;
        try {
            recipeList = indiceRecetas != null ? recetasPara(i) : Bukkit.getServer().getRecipesFor(i);
        } catch (IllegalArgumentException exception) {
            // Purpur 26.2 rechaza al materializar algunas recetas de terceros con un
            // resultado vacío. No se puede valorar el registro completo de forma segura;
            // cortar aquí evita que una tarea por tick llene la consola con el mismo fallo.
            recipeRegistryCompatible = false;
            plugin.getLogger().warning("El registro de recetas contiene una entrada incompatible; "
                    + "se omite el cálculo EMC derivado para proteger el servidor.");
            return null;
        }
        if (recipeList.isEmpty()) {
            DebugLogs.logEmcNoRecipes(plugin, nestLevel);
            emcVanillaSinValor.add(clave);
            return null;
        } else {
            // Sólo cortamos el ciclo de la rama actual: al volver se retira el
            // material, por lo que siguen evaluándose recetas alternativas válidas.
            if (!emcVanillaEnCurso.add(m)) {
                emcVanillaCortes++;
                DebugLogs.logEmcNull(plugin, nestLevel);
                return null;
            }
            final int cortesAntes = emcVanillaCortes;
            try {
                for (Recipe r : recipeList) {
                    Double tempVal = checkRecipe(plugin, r,nestLevel + 1);
                    if (tempVal != null && (eVal.equals(0D) || tempVal < eVal)) {
                        DebugLogs.logRecipeCheaper(plugin, nestLevel);
                        eVal = tempVal;
                    } else if (tempVal != null) {
                        DebugLogs.logRecipeNotCheaper(plugin, nestLevel);
                    }
                }
            } finally {
                emcVanillaEnCurso.remove(m);
            }
            if (emcVanillaCortes == cortesAntes) {
                emcVanillaMemo.put(clave, eVal);
            }
        }
        DebugLogs.logEmcRecipeResult(plugin, eVal, nestLevel);
        return eVal;
    }

    private Double roundDown(Double value, int places) {
        BigDecimal decimal = BigDecimal.valueOf(value);
        decimal = decimal.setScale(places, RoundingMode.DOWN);
        return decimal.doubleValue();
    }

    @Nullable
    private Double checkRecipe(EquivalencyTech plugin, Recipe recipe, Integer nestLevel) {
        DebugLogs.logCheckingRecipe(plugin, nestLevel);

        if (recipe instanceof ShapedRecipe) {
            return checkShaped(plugin, (ShapedRecipe) recipe, nestLevel);
        } else if (recipe instanceof ShapelessRecipe) {
            return checkShapeless(plugin, (ShapelessRecipe) recipe, nestLevel);
        } else if (recipe instanceof FurnaceRecipe) {
            return checkFurnace(plugin, (FurnaceRecipe) recipe, nestLevel);
        } else if (recipe instanceof StonecuttingRecipe) {
            return checkStoneCutter(plugin, (StonecuttingRecipe) recipe, nestLevel);
        } else if (recipe instanceof SmithingRecipe) {
            return checkSmithing(plugin, (SmithingRecipe) recipe, nestLevel);
        }

        return null;
    }

    @Nullable
    private Double checkShaped(EquivalencyTech plugin, ShapedRecipe recipe, int nestLevel) {
        DebugLogs.logRecipeType(plugin, "Shaped", nestLevel);
        double eVal= 0D;
        for (ItemStack i2 : recipe.getIngredientMap().values()) {
            if (i2 != null) {
                Double prVal = 0D;
                if (!i2.getType().equals(Material.AIR)) {
                    prVal = getEmcValue(plugin, i2, nestLevel + 1);
                }
                if (prVal != null) {
                    if (recipe.getResult().getAmount() > 1) {
                        DebugLogs.logRecipeMultipleOutputs(plugin, prVal, recipe.getResult().getAmount(), nestLevel);
                        eVal = eVal + (prVal / recipe.getResult().getAmount());
                    } else {
                        eVal = eVal + prVal;
                    }
                } else {
                    return null;
                }
            }
        }
        return eVal;
    }

    @Nullable
    private Double checkShapeless(EquivalencyTech plugin, ShapelessRecipe recipe, int nestLevel) {
        DebugLogs.logRecipeType(plugin, "Shapeless", nestLevel);
        Double eVal = 0D;
        for (ItemStack i2 : recipe.getIngredientList()) {
            Double prVal = getEmcValue(plugin, i2, nestLevel + 1);
            if (prVal != null) {
                if (recipe.getResult().getAmount() > 1) {
                    DebugLogs.logRecipeMultipleOutputs(plugin, prVal, recipe.getResult().getAmount(), nestLevel);
                    eVal = eVal + (prVal / recipe.getResult().getAmount());
                } else {
                    eVal = eVal + prVal;
                }
            } else {
                return null;
            }
        }
        return eVal;
    }

    @Nullable
    private Double checkFurnace(EquivalencyTech plugin, FurnaceRecipe recipe, int nestLevel) {
        DebugLogs.logRecipeType(plugin, "Furnace", nestLevel);
        Double prVal = getEmcValue(plugin, recipe.getInput(), nestLevel + 1);
        if (prVal != null) {
            if (recipe.getResult().getAmount() > 1) {
                DebugLogs.logRecipeMultipleOutputs(plugin, prVal, recipe.getResult().getAmount(), nestLevel);
                return prVal / recipe.getResult().getAmount();
            } else {
                return prVal;
            }
        } else {
            return null;
        }
    }

    @Nullable
    private Double checkStoneCutter(EquivalencyTech plugin, StonecuttingRecipe recipe, int nestLevel) {
        DebugLogs.logRecipeType(plugin, "Stonecutting", nestLevel);
        Double prVal = getEmcValue(plugin, recipe.getInput(), nestLevel + 1);
        if (prVal != null) {
            if (recipe.getResult().getAmount() > 1) {
                DebugLogs.logRecipeMultipleOutputs(plugin, prVal, recipe.getResult().getAmount(), nestLevel);
                return prVal / recipe.getResult().getAmount();
            } else {
                return prVal;
            }
        } else {
            return null;
        }
    }

    @Nullable
    private Double checkSmithing(EquivalencyTech plugin, SmithingRecipe recipe, int nestLevel) {
        DebugLogs.logRecipeType(plugin, "Smithing", nestLevel);
        Double baseVal = getEmcValue(plugin, recipe.getBase().getItemStack(), nestLevel + 1);
        Double additionVal = getEmcValue(plugin, recipe.getAddition().getItemStack(), nestLevel + 1);
        if (baseVal != null && additionVal != null) {
            double combinedVal = (baseVal + additionVal);
            if (recipe.getResult().getAmount() > 1) {
                DebugLogs.logRecipeMultipleOutputs(plugin, baseVal, recipe.getResult().getAmount(), nestLevel);
                return combinedVal / recipe.getResult().getAmount();
            } else {
                return combinedVal;
            }
        } else {
            return null;
        }
    }

    @Nullable
    public Double getEmcValue(Material material) {
        if (Utils.isBlacklistedMaterial(material)) {
            return null;
        }
        if (emcExtended.containsKey(material)) {
            return emcExtended.get(material);
        }
        return null;
    }

}
