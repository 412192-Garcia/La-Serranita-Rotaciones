package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MejoraRepartoTest {

    private static AsignadorService.Presente persona(long id, String nombre, String... sabe) {
        Set<String> claves = Set.of(java.util.Arrays.stream(sabe).map(Nombres::clave).toArray(String[]::new));
        return new AsignadorService.Presente(id, nombre, claves, claves, Set.of());
    }

    @Test
    void cambiaDeLugarADosSiAsiQuedanEnLoQueSaben() {
        // A las 17 (segundo horario) el 1 (Agus, sabe Fotos) está en Minigolf y el 2 (Julián, sabe Minigolf) en Fotos.
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Cajero", null, false, List.of("1", "")),
                new PuestoDto("Fotos", null, false, List.of("Cerrado", "2")),
                new PuestoDto("Minigolf", null, false, List.of("2", "1")),
                new PuestoDto("Descanso", null, false, List.of("", "")));
        var agus = persona(10, "Agus", "Cajero", "Fotos");
        var julian = persona(20, "Julián", "Minigolf");

        List<PuestoDto> mejor = MejoraReparto.mejorar(puestos, Map.of(1, agus, 2, julian), Map.of());

        assertThat(mejor.get(1).celdas()).containsExactly("Cerrado", "1");
        assertThat(mejor.get(2).celdas()).containsExactly("2", "2");
        assertThat(mejor.get(0).celdas()).containsExactly("1", "");
    }

    @Test
    void laSegundaCajeraCubreElZorritoEnVezDeUnoQueNoLoSabe() {
        // Almuerza la del zorrito (3): la cubre el 4, que no sabe, mientras están las dos cajeras.
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Cajero", null, false, List.of("1-2")),
                new PuestoDto("Casa del zorrito", null, false, List.of("4")),
                new PuestoDto("Descanso", null, false, List.of("3")));
        var cj1 = new AsignadorService.Presente(1L, "CJ1", Set.of("cajero", "casa del zorrito"), Set.of("cajero", "casa del zorrito"), Set.of("cajero"));
        var cj2 = new AsignadorService.Presente(2L, "CJ2", Set.of("cajero", "casa del zorrito"), Set.of("cajero", "casa del zorrito"), Set.of("cajero"));
        var p07 = persona(4, "P07", "Entrada");

        List<PuestoDto> mejor = MejoraReparto.cubrirHuecos(puestos, Map.of(1, cj1, 2, cj2, 4, p07), Map.of());

        // Una cajera queda en Cajero y la otra cubre la Casa del zorrito.
        assertThat(List.of(mejor.get(0).celdas().get(0), mejor.get(1).celdas().get(0))).containsExactlyInAnyOrder("1", "2");
        assertThat(mejor).anySatisfy(p -> {
            assertThat(p.nombre()).isEqualTo("Libre");
            assertThat(p.celdas()).containsExactly("4");
        });
    }

    @Test
    void elDeLibreAbreUnPuestoCerradoQueSabePeroNoUnoQueTodaviaNoAbrio() {
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Cajero", null, false, List.of("1-2")),
                new PuestoDto("Pecera", null, false, List.of("Cerrado")),
                new PuestoDto("Tirolesa", null, false, List.of("Cerrado")),
                new PuestoDto("Libre", null, false, List.of("3-4")));
        var cj1 = persona(1, "CJ1", "Cajero", "Pecera");
        var cj2 = persona(2, "CJ2", "Cajero", "Pecera");
        var p13 = persona(3, "P13", "Pecera");
        var tj = persona(4, "TJ", "Tirolesa");

        List<PuestoDto> mejor = MejoraReparto.cubrirHuecos(puestos, Map.of(1, cj1, 2, cj2, 3, p13, 4, tj), Map.of(), Set.of("tirolesa"));

        assertThat(mejor.get(1).celdas()).containsExactly("3");
        assertThat(mejor.get(2).celdas()).containsExactly("Cerrado");
        assertThat(mejor.get(0).celdas()).containsExactly("1-2");
        assertThat(mejor.get(3).celdas()).containsExactly("4");
    }

    @Test
    void siCadaUnoYaSabeLoSuyoNoTocaNada() {
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Entrada", null, false, List.of("1", "2")),
                new PuestoDto("Canilla", null, false, List.of("2", "1")));
        var ana = persona(1, "Ana", "Entrada", "Canilla");
        var beto = persona(2, "Beto", "Entrada", "Canilla");

        assertThat(MejoraReparto.mejorar(puestos, Map.of(1, ana, 2, beto), Map.of())).isSameAs(puestos);
    }
}
