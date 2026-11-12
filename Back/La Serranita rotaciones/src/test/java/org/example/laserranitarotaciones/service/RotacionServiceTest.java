package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.RenglonDto;
import org.example.laserranitarotaciones.model.dto.TarjetaDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RotacionServiceTest {

    private final RotacionService servicio = new RotacionService();

    private final PlanRotacion plan = PlanRotacion.de(
            List.of("10:00", "11:00", "12:00"),
            List.of(
                    new PuestoDto("Boleteria", List.of("1", "2", "2")),
                    new PuestoDto("Toboganes", List.of("2", "1-4", "3")),
                    new PuestoDto("Descanso", List.of("", "", "1")),
                    new PuestoDto("Cantina", List.of("4", "", "3")),
                    new PuestoDto("Estacionamiento", List.of("5", "5", "5"))));

    @Test
    void armaLasTarjetasConLaRotacion() {
        List<TarjetaDto> tarjetas = servicio.armarTarjetas(plan);

        assertThat(tarjetas).extracting(TarjetaDto::empleado).containsExactly(1, 2, 3, 4, 5);
        assertThat(textos(tarjetas, 1)).containsExactly(
                "Boleteria",
                "Al ser cubierto por 2 rote a Toboganes",
                "Al ser cubierto por 3 rote a Descanso");
        assertThat(tarjetas.get(0).descanso()).isEqualTo("12:00");
        assertThat(textos(tarjetas, 2)).containsExactly(
                "Toboganes",
                "Al ser cubierto por 1 y 4 rote a Boleteria",
                "Boleteria");
        assertThat(textos(tarjetas, 3)).containsExactly("Toboganes y Cantina");
        assertThat(textos(tarjetas, 4)).containsExactly("Cantina", "Sin ser cubierto, rote a Toboganes");
        assertThat(textos(tarjetas, 5)).containsExactly("Estacionamiento", "Estacionamiento", "Estacionamiento");
    }

    @Test
    void cuentaEmpleadosYPuestosFijos() {
        assertThat(servicio.cantidadEmpleados(plan)).isEqualTo(5);
        assertThat(servicio.fijos(plan)).containsExactly("Estacionamiento");
    }

    @Test
    void fijoAunqueLoCubranEnElAlmuerzoYPuestoCerrado() {
        PlanRotacion conAlmuerzo = PlanRotacion.de(
                List.of("11:00", "12:00", "13:00", "14:00"),
                List.of(
                        new PuestoDto("Minigolf", List.of("2", "2", "4", "2")),
                        new PuestoDto("Tobogan", List.of("3", "Cerrado", "3", "3")),
                        new PuestoDto("Carrera", List.of("Cerrado", "3", "Cerrado", "Cerrado")),
                        new PuestoDto("Entrada", List.of("4", "4", "2", "4"))));

        assertThat(servicio.fijos(conAlmuerzo)).containsExactly("Minigolf", "Tobogan", "Carrera", "Entrada");
        assertThat(textos(servicio.armarTarjetas(conAlmuerzo), 3)).containsExactly(
                "Tobogan", "Rote a Carrera", "Rote a Tobogan", "Tobogan");
    }

    @Test
    void dosPuestosALaVezSonDosFilasConElMismoNumero() {
        PlanRotacion separados = PlanRotacion.de(
                List.of("11:00", "12:00", "13:00"),
                List.of(
                        new PuestoDto("Travesía", List.of("5", "5", "6")),
                        new PuestoDto("Búsqueda", List.of("5", "5", "7")),
                        new PuestoDto("Tobogán", List.of("6", "6", "5")),
                        new PuestoDto("Carrera", List.of("7", "7", "")),
                        new PuestoDto("Laberinto", List.of("", "", "7"))));
        List<TarjetaDto> tarjetas = servicio.armarTarjetas(separados);

        assertThat(textos(tarjetas, 5)).containsExactly(
                "Travesía y Búsqueda", "Travesía y Búsqueda", "Al ser cubierto por 6 y 7 rote a Tobogán");
        assertThat(textos(tarjetas, 7)).containsExactly(
                "Carrera", "Carrera", "Sin ser cubierto, rote a Búsqueda y Laberinto");
        assertThat(servicio.fijos(separados)).containsExactly("Carrera", "Laberinto");
    }

    @Test
    void laNotaSeSeparaDelNombreYSeLeeEnLaTarjeta() {
        assertThat(Nombres.separarNota("*Canilla (consultar al encargado)"))
                .containsExactly("Canilla", "consultar al encargado");
        assertThat(Nombres.separarNota("Flotante de minigolf")).containsExactly("Flotante de minigolf", null);

        PlanRotacion conNota = PlanRotacion.de(
                List.of("11:00", "12:00"),
                List.of(
                        new PuestoDto("Tobogán", List.of("1", "2")),
                        new PuestoDto("Canilla", "consultar al encargado", List.of("2", "1"))));
        assertThat(textos(servicio.armarTarjetas(conNota), 1)).containsExactly(
                "Tobogán", "Al ser cubierto por 2 rote a Canilla (consultar al encargado)");
    }

    private static List<String> textos(List<TarjetaDto> tarjetas, int empleado) {
        return tarjetas.stream()
                .filter(t -> t.empleado() == empleado)
                .findFirst().orElseThrow()
                .renglones().stream().map(RenglonDto::texto).toList();
    }
}
