package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.AsignacionDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AsignadorServiceTest {

    private final AsignadorService asignador = new AsignadorService();

    private final PlanRotacion plan = PlanRotacion.de(
            List.of("11:00", "12:00", "13:00"),
            List.of(
                    new PuestoDto("Boletería", List.of("1", "1", "1")),
                    new PuestoDto("Minigolf", List.of("2", "2", "2")),
                    new PuestoDto("Tobogán", List.of("3", "4", "3")),
                    new PuestoDto("Carrera", List.of("4", "3", "4")),
                    new PuestoDto("Descanso", List.of("", "", ""))));

    @Test
    void respetaLoQueHoyPuedeYElPuestoPrincipal() {
        AsignadorService.Resultado r = asignador.asignar(plan, List.of(
                presente(1L, "Beto", Set.of("boleteria", "tobogan", "carrera"), null, Set.of()),
                presente(2L, "Carlos", Set.of("minigolf", "tobogan", "carrera"), Set.of("minigolf"), Set.of()),
                presente(3L, "Ana", Set.of("boleteria", "tobogan", "carrera"), null, Set.of("boleteria")),
                presente(4L, "Dani", Set.of("tobogan", "carrera", "minigolf"), null, Set.of())));

        Map<Integer, String> quien = quien(r.asignacion());
        assertThat(quien.get(1)).isEqualTo("Ana");
        assertThat(quien.get(2)).isEqualTo("Carlos");
        assertThat(Set.of(quien.get(3), quien.get(4))).containsExactlyInAnyOrder("Beto", "Dani");
        assertThat(r.avisos()).isEmpty();
    }

    @Test
    void siNoHayFormaAvisaQuienQuedaDondeNoPuede() {
        AsignadorService.Resultado r = asignador.asignar(plan, List.of(
                presente(1L, "Ana", Set.of("boleteria"), null, Set.of("boleteria")),
                presente(2L, "Carlos", Set.of("minigolf", "tobogan", "carrera"), Set.of("minigolf"), Set.of()),
                presente(3L, "Dani", Set.of("minigolf", "tobogan", "carrera"), Set.of("minigolf"), Set.of()),
                presente(4L, "Beto", Set.of("tobogan", "carrera"), null, Set.of())));

        assertThat(r.avisos()).hasSize(2).allMatch(a -> a.endsWith("y hoy no puede."));
        assertThat(quien(r.asignacion()).get(1)).isEqualTo("Ana");
    }

    @Test
    void sinGenteSuficienteQuedanNumerosVacios() {
        AsignadorService.Resultado r = asignador.asignar(plan, List.of(
                presente(1L, "Ana", Set.of("boleteria"), null, Set.of())));

        assertThat(r.asignacion()).hasSize(4);
        assertThat(r.asignacion().stream().filter(a -> a.empleadoId() == null)).hasSize(3);
        assertThat(r.avisos()).hasSize(3).allMatch(a -> a.startsWith("Nadie hace el número"));
    }

    @Test
    void siFaltaGenteQuedaSinCubrirElDeMenorPrioridad() {
        Set<String> todo = Set.of("boleteria", "minigolf", "tobogan", "carrera");
        AsignadorService.Resultado r = asignador.asignar(plan, List.of(
                presente(1L, "Ana", todo, null, Set.of()),
                presente(2L, "Beto", todo, null, Set.of()),
                presente(3L, "Carla", todo, null, Set.of())),
                Map.of("boleteria", Prioridad.ALTA.peso(), "minigolf", Prioridad.BAJA.peso()));

        assertThat(r.asignacion()).filteredOn(a -> a.empleadoId() == null)
                .extracting(AsignacionDto::numero).containsExactly(2);
    }

    @Test
    void siAlguienTieneQueHacerAlgoQueNoSabeQueSeaElDeMenorPrioridad() {
        Set<String> rotan = Set.of("tobogan", "carrera");
        AsignadorService.Resultado r = asignador.asignar(plan, List.of(
                presente(1L, "Ana", Set.of("boleteria", "minigolf"), null, Set.of()),
                presente(2L, "Beto", rotan, null, Set.of()),
                presente(3L, "Carla", rotan, null, Set.of()),
                presente(4L, "Dani", rotan, null, Set.of())),
                Map.of("boleteria", Prioridad.ALTA.peso(), "minigolf", Prioridad.BAJA.peso()));

        assertThat(quien(r.asignacion()).get(1)).isEqualTo("Ana");
        assertThat(r.avisos()).singleElement().asString().contains("Minigolf");
    }

    private static AsignadorService.Presente presente(Long id, String nombre, Set<String> sabe, Set<String> hoy, Set<String> principales) {
        return new AsignadorService.Presente(id, nombre, sabe, hoy == null ? sabe : hoy, principales);
    }

    private static Map<Integer, String> quien(List<AsignacionDto> asignacion) {
        return asignacion.stream().filter(a -> a.nombre() != null)
                .collect(Collectors.toMap(AsignacionDto::numero, AsignacionDto::nombre));
    }
}
