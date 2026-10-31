# EquivalencyTech Wiki

- [Español](#español)
- [English](#english)

---

## Español

### Cómo conseguir el Orbe de Transmutación

El orbe se craftea en una **mesa de crafteo** normal o en la **Enhanced Crafting Table** de Slimefun. La receta está en la guía de Slimefun, categoría **EquivalencyTech**.

Cadena de crafteo (`_` = hueco vacío):

**1. Alchemical Coal** — 5 Carbón

```
Carbón  Carbón  Carbón
Carbón  Carbón  _
_       _       _
```

**2. Mobius Fuel** — 5 Alchemical Coal (misma forma)

**3. Aeternalis Fuel** — 5 Mobius Fuel (misma forma)

**4. Dark Matter** — 8 Aeternalis Fuel + 1 Bloque de Netherite

```
Aeternalis  Aeternalis       Aeternalis
Aeternalis  Bloque Netherite Aeternalis
Aeternalis  Aeternalis       Aeternalis
```

**5. Red Matter** — 6 Aeternalis Fuel + 3 Dark Matter

```
Aeternalis   Aeternalis   Aeternalis
Dark Matter  Dark Matter  Dark Matter
Aeternalis   Aeternalis   Aeternalis
```

**6. Orbe de Transmutación** — 4 Bloques de Diamante + 4 Dark Matter + 1 Red Matter

```
Bloque Diamante  Dark Matter  Bloque Diamante
Dark Matter      Red Matter   Dark Matter
Bloque Diamante  Dark Matter  Bloque Diamante
```

Coste total de un orbe:
- 4 Bloques de Diamante
- 7 Bloques de Netherite
- 62 Aeternalis Fuel = 310 Mobius Fuel = 1550 Alchemical Coal = **7750 Carbón**

Los administradores pueden darlo con `/et giveitem transmutationorb <jugador>` (permiso `EquiTech.Admin`).

### Uso básico

- Clic derecho con el orbe para abrirlo.
- El orbe no se abre en los mundos de `BLOCKED_WORLDS` (`config.yml`).
- `/et itememc`: muestra el EMC del item en la mano. Si dice que no se puede condensar, el orbe no lo acepta.
- `/et emc`: muestra tu EMC.
- Solo se aceptan items básicos: sin encantamientos ni nombre personalizado.

### Qué se puede transmutar

Un item entra en el orbe solo si tiene valor EMC. Tiene valor si:
- Es vanilla y tiene valor en `EMC_VALUES.BASE` de `config.yml`.
- Es vanilla y se craftea, funde o corta con items que tienen valor (tablones, palos, bloques de hierro...).
- Es del **núcleo de Slimefun** y toda su receta tiene valor.

Items de Slimefun que **sí** se pueden transmutar (valores aproximados):

| Item | EMC |
|---|---|
| Magic Lump 1 / 2 / 3 | 4 / 16 / 64 |
| Polvos: Copper, Iron, Tin, Magnesium, Zinc, Lead, Silver, Aluminum Dust | 32 |
| Gold Dust | 64 |
| Lingotes: Copper, Tin, Silver, Lead, Aluminum, Zinc, Magnesium Ingot | 32 |
| Bronze Ingot | 96 |
| Brass Ingot | 96 |
| Nickel Ingot | 128 |
| Aluminum Brass Ingot | 160 |
| Cobalt Ingot | 192 |

### Qué NO se puede transmutar

Un item no tiene valor si:
1. Está en la lista negra.
2. Su receta usa un item de la lista negra o un item sin valor.
3. Viene de un **addon** (Networks, InfinityExpansion, etc.). Solo se calcula el núcleo de Slimefun.
4. Es vanilla, no tiene valor base y no se craftea con items que tengan valor.

**Vanilla en lista negra:**
- Diamante y todo lo de diamante (bloque, mena, armadura, herramientas, armadura de caballo).
- Netherite y todo lo de netherite (lingote, bloque, chatarra, Ancient Debris, armadura, herramientas, plantilla de mejora).
- Esmeralda, bloque y menas de esmeralda.
- Totem of Undying, Elytra, Nether Star, Beacon, Dragon Egg.

**Slimefun en lista negra:**
- Carbon, Compressed Carbon, Carbon Chunk, Carbonado.
- Blistering Ingot (1, 2, 3).
- Ender Lump 1, 2, 3 (Ender Lump 4 y 5 no existen en el núcleo de Slimefun).
- Gold 4K, 6K, 8K, 10K, 12K, 14K, 16K, 18K, 20K, 22K, 24K.
- Synthetic Diamond, Synthetic Emerald, Synthetic Sapphire.
- Aleaciones: Reinforced Alloy, Reinforced Plate, Redstone Alloy, Damascus Steel, Duralumin, Corinthian Bronze, Solder, Billon, Hardened Metal, Aluminum Bronze, Steel, Ferrosilicon.
- Uranium, Neo Uranium, Boosted Uranium, Reactor Coolant Cell, Nether Ice Coolant Cell.

**Sin valor por su receta:**

| Item | Motivo |
|---|---|
| Gilded Iron | Usa Gold 24K |
| Synthetic Shulker Shell | Usa Ender Lump 3 y Reinforced Plate |
| Power Crystal | Usa Synthetic Sapphire y Synthetic Diamond |
| Android Memory Core | Usa Power Crystal |
| Plastic Sheet | Usa Bucket of Oil, que vale 0 |
| Todos los Androids | Usan Plastic Sheet y Android Memory Core |
| Deepslate y variantes | Cobbled Deepslate no tiene valor base |
| Menas (Iron, Gold, Coal, Lapis, Redstone, Quartz...) | Valor base 0 |
| Player Head | Valor base 0 |

**Addons (nunca se transmutan):** Network Silicon, Network Circuit, Network Grabber, Advanced Grabber, Advanced Pusher y cualquier otro item de addon.

---

## English

### How to get the Transmutation Orb

Craft the orb in a normal **crafting table** or in the Slimefun **Enhanced Crafting Table**. The recipe is in the Slimefun guide, **EquivalencyTech** category.

Crafting chain (`_` = empty slot):

**1. Alchemical Coal** — 5 Coal

```
Coal  Coal  Coal
Coal  Coal  _
_     _     _
```

**2. Mobius Fuel** — 5 Alchemical Coal (same shape)

**3. Aeternalis Fuel** — 5 Mobius Fuel (same shape)

**4. Dark Matter** — 8 Aeternalis Fuel + 1 Netherite Block

```
Aeternalis  Aeternalis       Aeternalis
Aeternalis  Netherite Block  Aeternalis
Aeternalis  Aeternalis       Aeternalis
```

**5. Red Matter** — 6 Aeternalis Fuel + 3 Dark Matter

```
Aeternalis   Aeternalis   Aeternalis
Dark Matter  Dark Matter  Dark Matter
Aeternalis   Aeternalis   Aeternalis
```

**6. Transmutation Orb** — 4 Diamond Blocks + 4 Dark Matter + 1 Red Matter

```
Diamond Block  Dark Matter  Diamond Block
Dark Matter    Red Matter   Dark Matter
Diamond Block  Dark Matter  Diamond Block
```

Total cost of one orb:
- 4 Diamond Blocks
- 7 Netherite Blocks
- 62 Aeternalis Fuel = 310 Mobius Fuel = 1550 Alchemical Coal = **7750 Coal**

Admins can give it with `/et giveitem transmutationorb <player>` (permission `EquiTech.Admin`).

### Basic use

- Right-click the orb to open it.
- The orb does not open in worlds listed in `BLOCKED_WORLDS` (`config.yml`).
- `/et itememc`: shows the EMC of the held item. If it says the item cannot be condensed, the orb does not accept it.
- `/et emc`: shows your EMC.
- Only basic items are accepted: no enchantments and no custom name.

### What can be transmuted

An item goes into the orb only if it has an EMC value. It has a value if:
- It is vanilla and has a value in `EMC_VALUES.BASE` in `config.yml`.
- It is vanilla and is crafted, smelted or cut from items that have a value (planks, sticks, iron blocks...).
- It is from **Slimefun core** and its whole recipe has a value.

Slimefun items that **can** be transmuted (approximate values):

| Item | EMC |
|---|---|
| Magic Lump 1 / 2 / 3 | 4 / 16 / 64 |
| Dusts: Copper, Iron, Tin, Magnesium, Zinc, Lead, Silver, Aluminum Dust | 32 |
| Gold Dust | 64 |
| Ingots: Copper, Tin, Silver, Lead, Aluminum, Zinc, Magnesium Ingot | 32 |
| Bronze Ingot | 96 |
| Brass Ingot | 96 |
| Nickel Ingot | 128 |
| Aluminum Brass Ingot | 160 |
| Cobalt Ingot | 192 |

### What can NOT be transmuted

An item has no value if:
1. It is blacklisted.
2. Its recipe uses a blacklisted item or an item without a value.
3. It comes from an **addon** (Networks, InfinityExpansion, etc.). Only Slimefun core is calculated.
4. It is vanilla, has no base value and is not crafted from items that have a value.

**Blacklisted vanilla items:**
- Diamond and everything diamond (block, ore, armor, tools, horse armor).
- Netherite and everything netherite (ingot, block, scrap, Ancient Debris, armor, tools, upgrade template).
- Emerald, emerald block and emerald ores.
- Totem of Undying, Elytra, Nether Star, Beacon, Dragon Egg.

**Blacklisted Slimefun items:**
- Carbon, Compressed Carbon, Carbon Chunk, Carbonado.
- Blistering Ingot (1, 2, 3).
- Ender Lump 1, 2, 3 (Ender Lump 4 and 5 do not exist in Slimefun core).
- Gold 4K, 6K, 8K, 10K, 12K, 14K, 16K, 18K, 20K, 22K, 24K.
- Synthetic Diamond, Synthetic Emerald, Synthetic Sapphire.
- Alloys: Reinforced Alloy, Reinforced Plate, Redstone Alloy, Damascus Steel, Duralumin, Corinthian Bronze, Solder, Billon, Hardened Metal, Aluminum Bronze, Steel, Ferrosilicon.
- Uranium, Neo Uranium, Boosted Uranium, Reactor Coolant Cell, Nether Ice Coolant Cell.

**No value because of their recipe:**

| Item | Reason |
|---|---|
| Gilded Iron | Uses Gold 24K |
| Synthetic Shulker Shell | Uses Ender Lump 3 and Reinforced Plate |
| Power Crystal | Uses Synthetic Sapphire and Synthetic Diamond |
| Android Memory Core | Uses Power Crystal |
| Plastic Sheet | Uses Bucket of Oil, which is worth 0 |
| All Androids | Use Plastic Sheet and Android Memory Core |
| Deepslate and variants | Cobbled Deepslate has no base value |
| Ores (Iron, Gold, Coal, Lapis, Redstone, Quartz...) | Base value 0 |
| Player Head | Base value 0 |

**Addons (never transmutable):** Network Silicon, Network Circuit, Network Grabber, Advanced Grabber, Advanced Pusher and any other addon item.
