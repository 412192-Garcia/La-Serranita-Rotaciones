package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.dto.FaltanteRolDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.RenglonDto;
import org.example.laserranitarotaciones.model.dto.TarjetaDto;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolesPuestoTest {

    private static final RolesPuesto.Config SHOW = new RolesPuesto.Config("Show del zorrito", List.of("Zorrito", "Presentador"), "Ayudante");
    private static final Map<String, RolesPuesto.Config> CONFIGS = Map.of(Nombres.clave("Show del zorrito"), SHOW);
    private static final List<String> HORAS = List.of("15:00", "16:00");

    private static AsignadorService.Presente presente(long id, String... sabe) {
        Set<String> claves = new java.util.HashSet<>();
        for (String s : sabe) claves.add(Nombres.clave(s));
        return new AsignadorService.Presente(id, "E" + id, claves, claves, Set.of());
    }

    private static final Map<Integer, AsignadorService.Presente> POR_NUMERO = Map.of(
            1, presente(1, "Show del zorrito", "Minigolf"),
            2, presente(2, "Show del zorrito", "Minigolf"),
            3, presente(3, "Show del zorrito", "Minigolf"),
            4, presente(4, "Show del zorrito", "Minigolf"),
            5, presente(5, "Show del zorrito", "Minigolf"));

    private static Map<Long, Set<String>> sabe(Object... pares) {
        Map<Long, Set<String>> m = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.computeIfAbsent(((Number) pares[i]).longValue(), x -> new java.util.HashSet<>())
                    .add(RolesPuesto.clave("Show del zorrito", (String) pares[i + 1]));
        }
        return m;
    }

    @Test
    void cadaRolLoHaceAlguienPreparadoDeLosQueEstan() {
        List<PuestoDto> puestos = List.of(new PuestoDto("Show del zorrito", List.of("", "1-2-3")));
        List<PuestoDto> r = RolesPuesto.asignar(puestos, POR_NUMERO, CONFIGS, sabe(3, "Zorrito", 1, "Presentador"), Map.of());
        assertThat(r.get(0).celdas()).containsExactly("", "1-2-3");
        assertThat(r.get(0).roles().get(1)).containsExactly(Map.entry("Zorrito", 3), Map.entry("Presentador", 1));
        assertThat(RolesPuesto.faltantes(r, CONFIGS, HORAS)).isEmpty();
    }

    @Test
    void siNadieDelPuestoSabeSeTraeAUnoDeLibre() {
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Show del zorrito", List.of("", "1-2")),
                new PuestoDto("Libre", List.of("", "5")));
        List<PuestoDto> r = RolesPuesto.asignar(puestos, POR_NUMERO, CONFIGS, sabe(5, "Zorrito", 1, "Presentador"), Map.of());
        // El 5 entra como Zorrito y el 2 (sin rol) pasa a Libre.
        assertThat(r.get(0).celdas().get(1)).isEqualTo("1-5");
        assertThat(r.get(1).celdas().get(1)).isEqualTo("2");
        assertThat(r.get(0).roles().get(1)).containsEntry("Zorrito", 5).containsEntry("Presentador", 1);
    }

    @Test
    void siNoSeCambiaConUnoDeOtroPuestoQueSepaLoQueHacia() {
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Show del zorrito", List.of("", "1-2")),
                new PuestoDto("Minigolf", List.of("4", "4")));
        List<PuestoDto> r = RolesPuesto.asignar(puestos, POR_NUMERO, CONFIGS, sabe(4, "Zorrito", 1, "Presentador"), Map.of());
        assertThat(r.get(0).celdas().get(1)).isEqualTo("1-4");
        assertThat(r.get(1).celdas()).containsExactly("4", "2");
        assertThat(r.get(0).roles().get(1)).containsEntry("Zorrito", 4);
    }

    @Test
    void laDeLaCasaPresentaElShowYLaCajeraLaCubre() {
        // CZ1 (2, estrella en Casa del zorrito) es Presentadora; en el show está la cajera (3), que sabe la Casa.
        var cz1 = new AsignadorService.Presente(2L, "CZ1", Set.of("show del zorrito", "casa del zorrito"),
                Set.of("show del zorrito", "casa del zorrito"), Set.of("casa del zorrito"));
        var cj2 = presente(3, "Show del zorrito", "Casa del zorrito", "Cajero");
        var p01 = presente(14, "Show del zorrito");
        Map<Integer, AsignadorService.Presente> porNumero = Map.of(2, cz1, 3, cj2, 14, p01);
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Casa del zorrito", List.of("2", "2")),
                new PuestoDto("Show del zorrito", List.of("", "3-14")));

        List<PuestoDto> r = RolesPuesto.asignar(puestos, porNumero, CONFIGS, sabe(2, "Presentador", 14, "Zorrito"), Map.of());

        assertThat(r.get(0).celdas()).containsExactly("2", "3");
        assertThat(r.get(1).celdas().get(1)).isEqualTo("14-2");
        assertThat(r.get(1).roles().get(1)).containsEntry("Zorrito", 14).containsEntry("Presentador", 2);
    }

    @Test
    void siNadieDelShowSabeLaCasaLaCubreLaCajeraLibre() {
        // CZ1 (2) es Presentadora y está en la Casa; en el show el 5 y el 6 no saben la Casa; la cajera (3) está libre.
        var cz1 = new AsignadorService.Presente(2L, "CZ1", Set.of("show del zorrito", "casa del zorrito"),
                Set.of("show del zorrito", "casa del zorrito"), Set.of("casa del zorrito"));
        var cj2 = presente(3, "Casa del zorrito", "Cajero");
        Map<Integer, AsignadorService.Presente> porNumero = Map.of(2, cz1, 3, cj2, 5, presente(5, "Show del zorrito"),
                6, presente(6, "Show del zorrito"));
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Casa del zorrito", List.of("2", "2")),
                new PuestoDto("Show del zorrito", List.of("", "5-6")),
                new PuestoDto("Libre", List.of("3", "3")));

        List<PuestoDto> r = RolesPuesto.asignar(puestos, porNumero, CONFIGS, sabe(2, "Presentador", 5, "Zorrito"), Map.of());

        assertThat(r.get(0).celdas()).containsExactly("2", "3");
        assertThat(r.get(1).celdas().get(1)).isEqualTo("5-2");
        assertThat(r.get(2).celdas()).containsExactly("3", "6");
        assertThat(r.get(1).roles().get(1)).containsEntry("Zorrito", 5).containsEntry("Presentador", 2);
    }

    @Test
    void loQueNadiePuedeQuedaComoFaltanteYSeCubreAMano() {
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Show del zorrito", List.of("", "1-2")),
                new PuestoDto("Minigolf", List.of("4", "4")));
        List<PuestoDto> r = RolesPuesto.asignar(puestos, POR_NUMERO, CONFIGS, sabe(1, "Presentador"), Map.of());
        List<FaltanteRolDto> faltantes = RolesPuesto.faltantes(r, CONFIGS, HORAS);
        assertThat(faltantes).containsExactly(new FaltanteRolDto("Show del zorrito", "Zorrito", List.of(1), List.of("16:00")));
        assertThat(RolesPuesto.aviso(faltantes.get(0))).isEqualTo("Nadie de los que vinieron puede ser Zorrito en Show del zorrito (16:00).");

        // Se elige al 4, que estaba en Minigolf: se cambia con el 2 (sin rol).
        List<PuestoDto> cubierto = RolesPuesto.cubrir(r, SHOW, "zorrito", List.of(1), 4, "E4", POR_NUMERO, HORAS);
        assertThat(cubierto.get(0).celdas().get(1)).isEqualTo("1-4");
        assertThat(cubierto.get(1).celdas()).containsExactly("4", "2");
        assertThat(cubierto.get(0).roles().get(1)).containsEntry("Zorrito", 4).containsEntry("Presentador", 1);
        assertThat(RolesPuesto.faltantes(cubierto, CONFIGS, HORAS)).isEmpty();
    }

    @Test
    void siNadieSabeLoQueHaciaElElegidoYAhiQuedaOtroSoloSePasa() {
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Show del zorrito", List.of("", "1-2")),
                new PuestoDto("Cajero", List.of("4-5", "4-5")));
        List<PuestoDto> cubierto = RolesPuesto.cubrir(puestos, SHOW, "Zorrito", List.of(1), 4, "E4", POR_NUMERO, HORAS);
        assertThat(cubierto.get(0).celdas().get(1)).isEqualTo("1-2-4");
        assertThat(cubierto.get(1).celdas()).containsExactly("4-5", "5");
    }

    @Test
    void noSeEligeAAlguienQueEstaAlmorzando() {
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Show del zorrito", List.of("", "1-2")),
                new PuestoDto("Descanso", List.of("", "4")));
        assertThatThrownBy(() -> RolesPuesto.cubrir(puestos, SHOW, "Zorrito", List.of(1), 4, "E4", POR_NUMERO, HORAS))
                .hasMessage("E4 está almorzando a las 16:00.");
    }

    @Test
    void laTarjetaDiceElRol() {
        List<PuestoDto> puestos = List.of(new PuestoDto("Show del zorrito", null, false, List.of("", "1-2"),
                List.of(Map.of(), Map.of("Zorrito", 2))));
        List<TarjetaDto> tarjetas = List.of(new TarjetaDto(2, null, List.of(
                new RenglonDto("15:00", "Minigolf"), new RenglonDto("16:00", "Al ser cubierto por 5 rote a Show del zorrito"))));
        assertThat(RolesPuesto.conRoles(tarjetas, puestos, HORAS).get(0).renglones().get(1).texto())
                .isEqualTo("Al ser cubierto por 5 rote a Show del zorrito (Zorrito)");

        // El otro de la celda, sin rol: con dos, hace el rol que falta (Presentador); con tres, el tercero es Ayudante.
        List<TarjetaDto> delUno = List.of(new TarjetaDto(1, null, List.of(new RenglonDto("16:00", "Rote a Show del zorrito"))));
        Map<String, RolesPuesto.Config> configs = Map.of(Nombres.clave("Show del zorrito"), SHOW);
        assertThat(RolesPuesto.conRoles(delUno, puestos, HORAS, configs)
                .get(0).renglones().get(0).texto()).isEqualTo("Rote a Show del zorrito (Presentador)");
        List<PuestoDto> conTres = List.of(new PuestoDto("Show del zorrito", null, false, List.of("", "1-2-3"),
                List.of(Map.of(), Map.of("Zorrito", 2))));
        List<TarjetaDto> delTres = List.of(new TarjetaDto(3, null, List.of(new RenglonDto("16:00", "Rote a Show del zorrito"))));
        assertThat(RolesPuesto.conRoles(delTres, conTres, HORAS, configs)
                .get(0).renglones().get(0).texto()).isEqualTo("Rote a Show del zorrito (Ayudante)");
    }

    @Test
    void seGuardaYSeLee() {
        Map<String, Integer> roles = new java.util.LinkedHashMap<>();
        roles.put("Zorrito", 3);
        roles.put("Presentador", 5);
        assertThat(RolesPuesto.guardar(roles)).isEqualTo("Zorrito=3;Presentador=5");
        assertThat(RolesPuesto.leer("Zorrito=3;Presentador=5")).isEqualTo(roles);
        // Un número que ya no está en la celda se cae.
        assertThat(RolesPuesto.limpiar(List.of(roles), List.of("3-4"))).containsExactly(Map.of("Zorrito", 3));
    }
}
