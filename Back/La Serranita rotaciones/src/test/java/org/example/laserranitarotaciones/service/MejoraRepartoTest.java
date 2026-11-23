package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.entity.Prioridad;
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
    void elDeLaEstrellaNoVuelveASuPuestoSiDejaVacioUnoMasImportante() {
        // Almuerza la del zorrito: la cubre el de Baños (estrella en Baños, que en el almuerzo está cerrado).
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Casa del zorrito", null, false, List.of("2")),
                new PuestoDto("Baños", null, false, List.of("Cerrado")),
                new PuestoDto("Descanso", null, false, List.of("5")));
        var p11 = new AsignadorService.Presente(2L, "P11", Set.of("banos"), Set.of("banos"), Set.of("banos"));
        Map<String, Integer> pesos = Map.of("banos", org.example.laserranitarotaciones.model.entity.Prioridad.BAJA.peso());

        List<PuestoDto> r = MejoraReparto.respetarEstrellas(puestos, Map.of(2, p11), pesos);

        assertThat(r.get(0).celdas()).containsExactly("2");
        assertThat(r.get(1).celdas()).containsExactly("Cerrado");
    }

    @Test
    void laDelZorritoComeMasTardeSiAsiLaCubreUnaCajeraLibre() {
        // 12:00 (k=0): la del zorrito (5) come y la cubre el 2, que no sabe. 13:00 (k=1): la cajera 4 está libre.
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Casa del zorrito", null, false, List.of("2", "5")),
                new PuestoDto("Baños", null, false, List.of("Cerrado", "2")),
                new PuestoDto("Libre", null, false, List.of("", "4")),
                new PuestoDto("Descanso", null, false, List.of("5", "")));
        var p11 = persona(2, "P11", "Baños");
        var cj2 = persona(4, "CJ2", "Cajero", "Casa del zorrito");
        var cz1 = persona(5, "CZ1", "Casa del zorrito");

        List<PuestoDto> r = MejoraReparto.correrAlmuerzos(puestos, Map.of(2, p11, 4, cj2, 5, cz1), List.of(0, 1));

        assertThat(r.get(0).celdas()).containsExactly("5", "4");
        assertThat(r.get(2).celdas()).containsExactly("2", "");
        assertThat(r.get(3).celdas()).containsExactly("", "5");
    }

    @Test
    void elCajeroNuncaLoHaceAlguienQueNoSabe() {
        Map<String, Integer> pesos = Map.of("cajero", org.example.laserranitarotaciones.model.entity.Prioridad.SIEMPRE.peso());
        var p11 = persona(2, "P11", "Baños", "Minigolf");
        var cz1 = persona(5, "CZ1", "Casa del zorrito");
        var cj = persona(4, "CJ2", "Cajero", "Minigolf");

        var p03 = persona(3, "P03", "Entrada");
        // k=0: la cajera está en Libre: entra ella y el 2 pasa a Libre.
        // k=1: la cajera está en Minigolf, que el 2 sabe: se cambian.
        // k=2: la cajera está en Casa del zorrito, que el 3 no sabe y quedaría vacía: refuerzo, el 3 a Libre.
        // k=3: la cajera está en Búsqueda con otro: va a Cajero y el 3 a Libre.
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Cajero", null, false, List.of("2", "2", "3", "3")),
                new PuestoDto("Minigolf", null, false, List.of("5", "4", "", "")),
                new PuestoDto("Casa del zorrito", null, false, List.of("", "", "4", "")),
                new PuestoDto("Búsqueda", null, false, List.of("", "", "", "4-5")),
                new PuestoDto("Libre", null, false, List.of("4", "", "", "")),
                new PuestoDto("Descanso", null, false, List.of("", "", "", "")));

        List<PuestoDto> r = MejoraReparto.siempreConQuienSabe(puestos, Map.of(2, p11, 3, p03, 4, cj, 5, cz1), pesos);

        assertThat(r.get(0).celdas()).containsExactly("4", "4", "", "4");
        assertThat(r.get(1).celdas()).containsExactly("5", "2", "", "");
        assertThat(r.get(2).celdas()).containsExactly("", "", "4", "");
        assertThat(r.get(3).celdas()).containsExactly("", "", "", "5");
        assertThat(r.get(4).celdas()).containsExactly("2", "", "3", "3");
    }

    @Test
    void losDeLibreAyudanYLaCajeraVuelveAFotos() {
        // 14:00: Nancy (estrella Baños) está libre y va a ayudar a Baños. 17:00: Nancy hace Fotos y Camila (la cajera
        // que salió) está libre: Camila toma Fotos y Nancy vuelve a Baños.
        String banos = Nombres.clave("Baños");
        var nancy = new AsignadorService.Presente(1L, "Nancy", Set.of(banos, "fotos", "minigolf"), Set.of(banos, "fotos", "minigolf"), Set.of(banos));
        var evelyn = new AsignadorService.Presente(4L, "Evelyn", Set.of(banos), Set.of(banos), Set.of(banos));
        var camila = persona(3, "Camila", "Fotos", "Cajero");
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Baños", null, false, List.of("4", "4")),
                new PuestoDto("Fotos", null, false, List.of("Cerrado", "1")),
                new PuestoDto("Libre", null, false, List.of("1", "3")),
                new PuestoDto("Descanso", null, false, List.of("", "")));

        List<PuestoDto> r = MejoraReparto.libresAyudan(puestos, Map.of(1, nancy, 3, camila, 4, evelyn), Map.of());

        assertThat(r.get(0).celdas()).containsExactly("1-4", "1-4");
        assertThat(r.get(1).celdas()).containsExactly("Cerrado", "3");
        assertThat(r.get(2).celdas()).containsExactly("", "");
    }

    @Test
    void laCajeraNoVaAPeceraConElCajeroAbierto() {
        // A las 13 la cajera 3 (estrella Cajero) quedó en Pecera: vuelve al Cajero y Pecera cierra ese rato.
        String cajero = Nombres.clave("Cajero");
        var c2 = new AsignadorService.Presente(2L, "Camila", Set.of(cajero), Set.of(cajero), Set.of(cajero));
        var c3 = new AsignadorService.Presente(3L, "Delfina", Set.of(cajero, "pecera"), Set.of(cajero, "pecera"), Set.of(cajero));
        Map<String, Integer> pesos = Map.of(cajero, Prioridad.SIEMPRE.peso(), "pecera", Prioridad.BAJA.peso());
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Cajero", null, false, List.of("2-3", "2")),
                new PuestoDto("Pecera", null, false, List.of("Cerrado", "3")));

        List<PuestoDto> r = MejoraReparto.estrellaAntesQueBajo(puestos, Map.of(2, c2, 3, c3), pesos, Set.of());

        assertThat(r.get(0).celdas()).containsExactly("2-3", "2-3");
        assertThat(r.get(1).celdas()).containsExactly("Cerrado", "Cerrado");
    }

    @Test
    void fotosLaHaceLaMismaHastaElCierreYCierraPecera() {
        // Fotos abre a las 17 (k = 1) y la tiene Nancy, que no sabe. La cajera 2 (sabe Fotos) está en Pecera y a las
        // 18 sola en Cajero; la 3 está en Entrada a las 18. Queda la 2 en Fotos todo el rato: Pecera cierra, a las 18
        // la 3 vuelve al Cajero y el de Pecera (9) cubre Entrada. Nancy pasa a Libre.
        String banos = Nombres.clave("Baños");
        var nancy = new AsignadorService.Presente(1L, "Nancy", Set.of(banos), Set.of(banos), Set.of(banos));
        var c2 = persona(2, "Camila", "Fotos", "Cajero", "Entrada");
        var c3 = persona(3, "Delfina", "Cajero", "Entrada");
        var p9 = persona(9, "P9", "Pecera", "Entrada");
        Map<String, Integer> pesos = Map.of(Nombres.clave("Cajero"), Prioridad.SIEMPRE.peso(), Nombres.clave("Entrada"), Prioridad.ALTA.peso(),
                Nombres.clave("Pecera"), Prioridad.BAJA.peso(), Nombres.clave("Fotos"), Prioridad.MEDIA.peso());
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Cajero", null, false, List.of("3", "3", "2")),
                new PuestoDto("Fotos", null, false, List.of("Cerrado", "1", "1")),
                new PuestoDto("Entrada", null, false, List.of("9", "9", "3")),
                new PuestoDto("Pecera", null, false, List.of("2", "2", "9")),
                new PuestoDto("Libre", null, false, List.of("1", "", "")));

        List<PuestoDto> r = MejoraReparto.unoHastaElCierre(puestos, Map.of(1, nancy, 2, c2, 3, c3, 9, p9), pesos, "Fotos", Set.of());

        assertThat(r.get(1).celdas()).containsExactly("Cerrado", "2", "2");
        assertThat(r.get(0).celdas()).containsExactly("3", "3", "3");
        assertThat(r.get(2).celdas()).containsExactly("9", "9", "9");
        assertThat(r.get(3).celdas()).containsExactly("2", "Cerrado", "Cerrado");
        assertThat(r.get(4).celdas()).containsExactly("1", "1", "1");
    }

    @Test
    void enLaRuedaNadieRepiteElMismoPuestoSeguido() {
        // El 7 queda dos medias horas en Pecera: en la segunda se cambia con el de Carrera, que sabe Pecera.
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Pecera", null, false, List.of("7", "7", "9")),
                new PuestoDto("Carrera", null, false, List.of("9", "8", "8")));
        var p7 = persona(7, "P7", "Pecera", "Carrera");
        var p8 = persona(8, "P8", "Pecera", "Carrera");
        var p9 = persona(9, "P9", "Pecera", "Carrera");
        Set<String> rueda = Set.of(Nombres.clave("Pecera"), Nombres.clave("Carrera"));

        List<PuestoDto> r = MejoraReparto.sinRepetir(puestos, Map.of(7, p7, 8, p8, 9, p9), rueda);

        assertThat(r.get(0).celdas()).containsExactly("7", "8", "9");
        assertThat(r.get(1).celdas()).containsExactly("9", "7", "8");
    }

    @Test
    void sinNadieQueSobreHaceUnaRondaDeTres() {
        // Almuerza la de Minigolf: Evelyn (sólo Baños) quedó en Canilla, Nancy en Baños y el 10 cubre Minigolf.
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Baños", null, false, List.of("1")),
                new PuestoDto("Canilla", null, false, List.of("4")),
                new PuestoDto("Minigolf", null, false, List.of("10")),
                new PuestoDto("Descanso", null, false, List.of("5")));
        // Las dos tienen estrella en Baños: que entre Evelyn no le saca la estrella al puesto.
        String banos = Nombres.clave("Baños");
        var nancy = new AsignadorService.Presente(1L, "Nancy", Set.of(banos, "minigolf"), Set.of(banos, "minigolf"), Set.of(banos));
        var evelyn = new AsignadorService.Presente(4L, "Evelyn", Set.of(banos), Set.of(banos), Set.of(banos));
        var p10 = persona(10, "P10", "Minigolf", "Canilla");

        List<PuestoDto> r = MejoraReparto.cubrirHuecos(puestos, Map.of(1, nancy, 4, evelyn, 10, p10), Map.of());

        assertThat(r.get(0).celdas()).containsExactly("4");
        assertThat(r.get(1).celdas()).containsExactly("10");
        assertThat(r.get(2).celdas()).containsExactly("1");
    }

    @Test
    void unPuestoConRolesNoLoHaceQuienNoLoSabe() {
        // Show del zorrito (Media, con roles): el 1 lo sabe; el 2 no, y en Libre nadie lo sabe.
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Show del zorrito", null, false, List.of("1-2")),
                new PuestoDto("Minigolf", null, false, List.of("3")),
                new PuestoDto("Libre", null, false, List.of("4")));
        var sabe = persona(1, "CZ1", "Show del zorrito");
        var nancy = persona(2, "Nancy", "Minigolf", "Baños");
        var p3 = persona(3, "P3", "Minigolf");
        var p4 = persona(4, "P4", "Entrada");
        Map<String, Integer> pesos = Map.of("show del zorrito", Prioridad.MEDIA.peso());
        var porNumero = Map.of(1, sabe, 2, nancy, 3, p3, 4, p4);

        // Sin marcarlo, como es Media, queda como está.
        assertThat(MejoraReparto.siempreConQuienSabe(puestos, porNumero, pesos)).isSameAs(puestos);
        // Marcado (tiene roles): Nancy sale a Minigolf, que sabe, y nadie que lo sepa la reemplaza: el show queda
        // con uno y el otro lugar es para un refuerzo.
        List<PuestoDto> r = MejoraReparto.siempreConQuienSabe(puestos, porNumero, pesos, Set.of("show del zorrito"));
        assertThat(r.get(0).celdas()).containsExactly("1");
        assertThat(r.get(1).celdas().get(0) + "|" + r.get(2).celdas().get(0)).isIn("2-3|4", "3|2-4", "3|4-2");
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
