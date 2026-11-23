package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.RefuerzoDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import org.example.laserranitarotaciones.model.entity.Prioridad;

import static org.assertj.core.api.Assertions.assertThat;

class RefuerzosTest {

    @Test
    void unPuestoSinNadieQueNoEstaCerradoPideRefuerzo() {
        List<String> horas = List.of("12:00", "12:30", "13:00");
        List<PuestoDto> puestos = List.of(
                new PuestoDto("Casa del zorrito", List.of("5", "", "5")),
                new PuestoDto("Baños", List.of("Cerrado", "Cerrado", "2")),
                new PuestoDto("Libre", List.of("", "", "")),
                new PuestoDto("Descanso", List.of("", "5", "")));

        List<RefuerzoDto> r = SugerenciaService.refuerzos(puestos, horas);

        assertThat(r).containsExactly(new RefuerzoDto("Casa del zorrito", List.of(1), List.of("12:30")));
        assertThat(SugerenciaService.aviso(r.get(0))).isEqualTo("Falta gente en Casa del zorrito a las 12:30: pedí un refuerzo.");
    }

    @Test
    void conMenosGenteDeLaQueVaTambienPideRefuerzo() {
        // El Show iba con tres; al sacar al que no lo sabe quedan dos: hace falta uno más.
        List<String> horas = List.of("15:30", "16:00");
        List<PuestoDto> puestos = List.of(new PuestoDto("Show del zorrito", List.of("Cerrado", "6-18")));
        List<RefuerzoDto> r = SugerenciaService.refuerzos(puestos, horas, Map.of("Show del zorrito", List.of(0, 3)));
        assertThat(r).containsExactly(new RefuerzoDto("Show del zorrito", List.of(1), List.of("16:00")));
    }

    @Test
    void unPuestoDePrioridadBajaSinNadieQuedaCerradoYNoPideRefuerzo() {
        List<String> horas = List.of("12:00", "12:30");
        Map<String, Integer> pesos = Map.of("banos", Prioridad.BAJA.peso());
        List<PuestoDto> puestos = SugerenciaService.cerrarVaciosBajos(List.of(
                new PuestoDto("Baños", List.of("4", "")),
                new PuestoDto("Cajero", List.of("1", ""))), pesos);

        assertThat(puestos.get(0).celdas()).containsExactly("4", "Cerrado");
        assertThat(puestos.get(1).celdas()).containsExactly("1", "");
        assertThat(SugerenciaService.refuerzos(puestos, horas)).extracting(RefuerzoDto::puesto).containsExactly("Cajero");
    }
}
