<p align="center"><img src="https://raw.githubusercontent.com/DrakesCraft-Labs/EquivalencyTech/main/banner.svg" alt="EquivalencyTech banner" width="100%"></p>

# EquivalencyTech

> ### 🏰 ¡Únete a la Comunidad Oficial de DrakesCraft!
> 
> * 🎮 **IP del Servidor**: `play.drakescraft.net` *(Java 1.21.11 & Bedrock)*
> * 💬 **Discord Oficial**: [discord.gg/drakescraft](https://discord.gg/rv3vtXZTk7)
> * 🌐 **Web & Guía**: [drakescraft.net](https://drakescraft.net) — 🛒 **Tienda**: [tienda.drakescraft.net](https://tienda.drakescraft.net)
> 
> *¡Juega con este addon y más de 80 expansiones optimizadas en vivo en nuestra network de supervivencia técnica!*

---

EMC-based matter transmutation for the DrakesCraft Slimefun ecosystem. Players learn item
values, store EMC and convert resources through a deliberate progression path.

## DrakesCraft edition

- Targets Java 21 and Paper/Purpur 1.21.11.
- Compiles against the `com.github.drakescraft_labs.slimefun4` compatibility API.
- Preserves the addon's original package structure and gameplay model.
- Uses maintained dependency repositories and reproducible Maven builds.

## Building

```bash
mvn -B -ntp clean package
```

The deployable artifact is produced in `target/`. It requires
[`Slimefun4-Drake`](https://github.com/DrakesCraft-Labs/Slimefun4-Drake) at runtime.

## Provenance

Integrated from [SlimefunGuguProject/EquivalencyTech](https://github.com/SlimefunGuguProject/EquivalencyTech).
Original authorship and the GPL-3.0 license are preserved. DrakesCraft-Labs maintains only the
1.21.11 compatibility layer and server integration.

---

## 📄 License & Upstream Attribution

This project is a sovereign fork maintained by [**JackStar6677-1**](https://github.com/JackStar6677-1) under [**DrakesCraft Labs**](https://github.com/DrakesCraft-Labs).

- **Original Project:** Created by the upstream authors and the open-source community.
- **DrakesCraft Optimizations:** Modernized for Paper/Purpur 1.21.11+, Java 21, high concurrency, asynchronous safety, and exploit/duplication prevention.
- **License:** Distributed under the original **GNU General Public License v3.0 (GPLv3)** (or original upstream license). See the [LICENSE](LICENSE) file for complete terms.
