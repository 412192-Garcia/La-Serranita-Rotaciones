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

        assertThat(servicio.fijos(conAlmuerzo)).containsExactly("Minigolf", "Tobogan", "Entrada");
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
        assertThat(servicio.fijos(separados)).containsExactly("Carrera");
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

    @Test
    void alVolverDelDescansoSoloDiceAdondeVa() {
        PlanRotacion conDescanso = PlanRotacion.de(
                List.of("11:00", "12:00", "13:00"),
                List.of(
                        new PuestoDto("Tobogán", List.of("1", "2", "1")),
                        new PuestoDto("Descanso", List.of("2", "1-3", "2"))));
        assertThat(textos(servicio.armarTarjetas(conDescanso), 1)).containsExactly(
                "Tobogán", "Al ser cubierto por 2 rote a Descanso", "Tobogán");
    }

    @Test
    void unPuestoQueAbreUnRatoNoEsFijo() {
        PlanRotacion conBasura = PlanRotacion.de(
                List.of("11:00", "12:00", "13:00", "14:00"),
                List.of(
                        new PuestoDto("Minigolf", List.of("1", "1", "1", "1")),
                        new PuestoDto("Basura", List.of("", "", "", "2-3")),
                        new PuestoDto("Pileta", List.of("Cerrado", "Cerrado", "Cerrado", "4"))));
        assertThat(servicio.fijos(conBasura)).containsExactly("Minigolf");
    }

    @Test
    void elAsteriscoMarcaDondeArrancaLaRotacionYSeVeEnLaTarjeta() {
        Nombres.NombrePuesto leido = Nombres.separar("*Travesía (consultar al encargado)");
        assertThat(leido.nombre()).isEqualTo("Travesía");
        assertThat(leido.nota()).isEqualTo("consultar al encargado");
        assertThat(leido.iniciaRotacion()).isTrue();
        assertThat(Nombres.separar("Tobogán").iniciaRotacion()).isFalse();

        PlanRotacion plan = PlanRotacion.de(
                List.of("11:00", "12:00"),
                List.of(
                        new PuestoDto("Tobogán", List.of("1", "2")),
                        new PuestoDto("Travesía", null, true, List.of("2", "1"))));
        assertThat(textos(servicio.armarTarjetas(plan), 1)).containsExactly("Tobogán", "Al ser cubierto por 2 rote a *Travesía");

        PlanRotacion tres = PlanRotacion.de(
                List.of("11:00"),
                List.of(
                        new PuestoDto("Travesía", null, true, List.of("5")),
                        new PuestoDto("Búsqueda", null, true, List.of("5")),
                        new PuestoDto("Laberinto", null, true, List.of("5"))));
        assertThat(textos(servicio.armarTarjetas(tres), 5)).containsExactly("*Travesía, Búsqueda y Laberinto");
    }

    @Test
    void enumeraConComas() {
        assertThat(RotacionService.enumerar(List.of("Travesía", "Búsqueda", "Laberinto"))).isEqualTo("Travesía, Búsqueda y Laberinto");
        assertThat(RotacionService.enumerar(List.of("6", "7"))).isEqualTo("6 y 7");
    }

    private static List<String> textos(List<TarjetaDto> tarjetas, int empleado) {
        return tarjetas.stream()
                .filter(t -> t.empleado() == empleado)
                .findFirst().orElseThrow()
                .renglones().stream().map(RenglonDto::texto).toList();
    }
}
