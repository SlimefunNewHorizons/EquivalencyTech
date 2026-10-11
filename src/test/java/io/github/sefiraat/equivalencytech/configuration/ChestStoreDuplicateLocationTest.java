package io.github.sefiraat.equivalencytech.configuration;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * block_storage.yml indexa los cofres EMC por id, no por posicion, asi que nada impedia que
 * varios ids reclamasen el mismo bloque. getDChestIdStore solo ve el PRIMERO, de modo que las
 * altas escribian el dueño en el id viejo y las bajas dejaban vivo al duplicado. Estas pruebas
 * fijan el contrato del barrido por posicion en el que se apoyan el alta, la purga y el runnable.
 */
class ChestStoreDuplicateLocationTest {

    private static final String SECCION = ConfigMain.DIS_CHEST_CFG;

    @Test
    void devuelveTodosLosIdsQueReclamanLaPosicionYEnOrden() {
        YamlConfiguration c = new YamlConfiguration();
        Location compartida = new Location(null, -3989868.0, -58.0, 1230872.0);
        // Orden de escritura deliberadamente desordenado: el barrido no puede depender de el.
        c.set(SECCION + ".125", compartida);
        c.set(SECCION + ".121", compartida);
        c.set(SECCION + ".123", compartida);

        assertEquals(List.of(121, 123, 125), ConfigMain.collectIdsAt(c, SECCION, compartida));
    }

    @Test
    void noArrastraIdsDeOtraPosicion() {
        YamlConfiguration c = new YamlConfiguration();
        Location uno = new Location(null, 10.0, 64.0, 20.0);
        Location otra = new Location(null, 11.0, 64.0, 20.0);
        c.set(SECCION + ".1", uno);
        c.set(SECCION + ".2", otra);
        c.set(SECCION + ".3", uno);

        assertEquals(List.of(1, 3), ConfigMain.collectIdsAt(c, SECCION, uno));
        assertEquals(List.of(2), ConfigMain.collectIdsAt(c, SECCION, otra));
    }

    @Test
    void unaPosicionSinRegistrosNoDevuelveNada() {
        YamlConfiguration c = new YamlConfiguration();
        c.set(SECCION + ".1", new Location(null, 10.0, 64.0, 20.0));

        assertTrue(ConfigMain.collectIdsAt(c, SECCION, new Location(null, 99.0, 64.0, 99.0)).isEmpty());
        assertTrue(ConfigMain.collectIdsAt(c, SECCION, null).isEmpty());
        assertTrue(ConfigMain.collectIdsAt(new YamlConfiguration(), SECCION,
            new Location(null, 10.0, 64.0, 20.0)).isEmpty());
    }

    @Test
    void lasClavesQueNoSonIdsNoRevientanElBarrido() {
        YamlConfiguration c = new YamlConfiguration();
        Location uno = new Location(null, 10.0, 64.0, 20.0);
        c.set(SECCION + ".basura", uno);
        c.set(SECCION + ".7", uno);

        assertEquals(List.of(7), ConfigMain.collectIdsAt(c, SECCION, uno));
    }

    @Test
    void elInventarioDeMigracionSoloExponeIdsNumericosOrdenados() {
        YamlConfiguration c = new YamlConfiguration();
        c.set(SECCION + ".19", new Location(null, 10.0, 64.0, 20.0));
        c.set(SECCION + ".legado", new Location(null, 11.0, 64.0, 20.0));
        c.set(SECCION + ".2", new Location(null, 12.0, 64.0, 20.0));

        assertEquals(List.of(2, 19), ConfigMain.collectIds(c, SECCION));
    }

    @Test
    void elAltaDevuelveElIdCreadoParaQueElDuenoNoCaigaEnElIdViejo() throws NoSuchMethodException {
        // El defecto original era de tipos: addDChestStore era void y el llamador tenia que
        // preguntar por la posicion, obteniendo el primer id en vez del recien creado.
        assertEquals(Integer.class, ConfigMain.class
            .getDeclaredMethod("addDChestStore", io.github.sefiraat.equivalencytech.EquivalencyTech.class, Location.class)
            .getReturnType());
        assertEquals(Integer.class, ConfigMain.class
            .getDeclaredMethod("addCChestStore", io.github.sefiraat.equivalencytech.EquivalencyTech.class, Location.class)
            .getReturnType());
    }

    @Test
    void elIndiceResuelveElPrimerIdEnElOrdenDeLaSeccion() {
        // getDChestIdStore paso de barrer la seccion a consultar el indice en memoria: tiene que
        // seguir devolviendo el primer id escrito, no el menor, y no confundir posiciones.
        YamlConfiguration c = new YamlConfiguration();
        Location compartida = new Location(null, 10.0, 64.0, 20.0);
        c.set(SECCION + ".9", compartida);
        c.set(SECCION + ".4", compartida);

        ConfigMain.ChestIndex index = ConfigMain.ChestIndex.build(c, SECCION);
        assertEquals(9, index.firstIdAt(new Location(null, 10.0, 64.0, 20.0)));
        assertEquals(List.of(4, 9), index.idsAt(compartida));
        assertNull(index.firstIdAt(new Location(null, 11.0, 64.0, 20.0)));
        assertNull(index.firstIdAt(null));
    }
}
