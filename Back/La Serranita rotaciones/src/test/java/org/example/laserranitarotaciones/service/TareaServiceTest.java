package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.PedidoTareasDto;
import org.example.laserranitarotaciones.model.dto.PresenteTareaDto;
import org.example.laserranitarotaciones.model.dto.TareaAsignadaDto;
import org.example.laserranitarotaciones.model.dto.TareaDto;
import org.example.laserranitarotaciones.model.dto.TareaHechaDto;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.example.laserranitarotaciones.repository.TareaHechaRepository;
import org.example.laserranitarotaciones.repository.TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TareaServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    @Autowired
    private TareaService tareas;
    @Autowired
    private EmpleadoService empleados;
    @Autowired
    private TareaRepository tareaRepositorio;
    @Autowired
    private TareaHechaRepository hechaRepositorio;
    @Autowired
    private EmpleadoRepository empleadoRepositorio;

    private TareaDto sopladora;
    private TareaDto plancha;
    private TareaDto ordenar;
    private EmpleadoDto ana;
    private EmpleadoDto beto;
    private EmpleadoDto carla;
    private EmpleadoDto dani;
    private EmpleadoDto juan;

    @BeforeEach
    void cargar() {
        hechaRepositorio.deleteAll();
        tareaRepositorio.deleteAll();
        empleadoRepositorio.deleteAll();
        sopladora = tareas.crear(new TareaDto(null, "Pasar la sopladora", 2, false));
        plancha = tareas.crear(new TareaDto(null, "Pasar la plancha", 1, true));
        ordenar = tareas.crear(new TareaDto(null, "Ordenar juegos", 1, false));
        ana = empleados.crear(new EmpleadoDto(null, "Ana", List.of(), List.of(), false, null, List.of(plancha.id())));
        beto = empleados.crear(new EmpleadoDto(null, "Beto", List.of(), List.of()));
        carla = empleados.crear(new EmpleadoDto(null, "Carla", List.of(), List.of()));
        dani = empleados.crear(new EmpleadoDto(null, "Dani", List.of(), List.of()));
        juan = empleados.crear(new EmpleadoDto(null, "Juan", List.of(), List.of(), true, beto.id()));
    }

    @Test
    void reparteRespetandoQuienSabeYElQueEstaEnEntrenamientoVaConSuAcompanante() {
        Map<String, TareaAsignadaDto> r = repartir(todos());

        assertThat(r.get("Pasar la plancha").empleadoIds()).containsExactly(ana.id());
        assertThat(r.get("Pasar la sopladora").empleadoIds()).containsExactly(beto.id(), juan.id());
        assertThat(r.get("Ordenar juegos").empleadoIds()).containsExactly(carla.id());
        assertThat(r.values()).allMatch(t -> t.aviso() == null);
    }

    @Test
    void noRepiteLaTareaDeAyer() {
        tareas.guardarHechas(HOY.minusDays(1), List.of(new TareaHechaDto(ordenar.id(), List.of(carla.id()))));

        assertThat(repartir(todos()).get("Ordenar juegos").empleadoIds()).containsExactly(dani.id());
    }

    @Test
    void siFaltaGenteOMadieSabeAvisa() {
        Map<String, TareaAsignadaDto> soloBeto = repartir(List.of(new PresenteTareaDto(beto.id(), null)));

        assertThat(soloBeto.get("Pasar la plancha").aviso()).isEqualTo("Nadie de los que vinieron sabe hacerla.");
        assertThat(soloBeto.get("Pasar la sopladora").aviso()).isEqualTo("Falta 1 persona.");
        assertThat(soloBeto.get("Ordenar juegos").empleadoIds()).containsExactly(beto.id());
    }

    @Test
    void validaYAlBorrarSeOlvida() {
        assertThatThrownBy(() -> tareas.crear(new TareaDto(null, "pasar la Sopladora", 1, false)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya hay");
        assertThatThrownBy(() -> tareas.crear(new TareaDto(null, "Barrer", 0, false)))
                .isInstanceOf(ValidacionException.class);

        tareas.eliminar(plancha.id());
        assertThat(empleados.buscar(List.of(ana.id())).get(0).tareas()).isEmpty();
    }

    private List<PresenteTareaDto> todos() {
        return List.of(
                new PresenteTareaDto(ana.id(), null),
                new PresenteTareaDto(beto.id(), null),
                new PresenteTareaDto(carla.id(), null),
                new PresenteTareaDto(dani.id(), null),
                new PresenteTareaDto(juan.id(), beto.id()));
    }

    private Map<String, TareaAsignadaDto> repartir(List<PresenteTareaDto> presentes) {
        return tareas.repartir(new PedidoTareasDto(HOY, presentes)).stream()
                .collect(Collectors.toMap(TareaAsignadaDto::nombre, t -> t));
    }
}
