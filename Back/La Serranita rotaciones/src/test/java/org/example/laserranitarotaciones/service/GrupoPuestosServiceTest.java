package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.GrupoDto;
import org.example.laserranitarotaciones.model.dto.PedidoSugerenciaDto;
import org.example.laserranitarotaciones.model.dto.PresenteDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.RenombreDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.repository.CronogramaRepository;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.example.laserranitarotaciones.repository.GrupoPuestosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GrupoPuestosServiceTest {

    @Autowired
    private GrupoPuestosService grupos;
    @Autowired
    private EmpleadoService empleados;
    @Autowired
    private CronogramaService cronogramas;
    @Autowired
    private SugerenciaService sugerencias;
    @Autowired
    private UnificadorPuestosService unificador;
    @Autowired
    private GrupoPuestosRepository grupoRepositorio;
    @Autowired
    private EmpleadoRepository empleadoRepositorio;
    @Autowired
    private CronogramaRepository cronogramaRepositorio;

    private GrupoDto parque;

    @BeforeEach
    void preparar() {
        empleadoRepositorio.deleteAll();
        grupoRepositorio.deleteAll();
        cronogramaRepositorio.deleteAll();
        cronogramas.crear(new CronogramaDto(null, "Base", Afluencia.MEDIA, List.of("10:00", "11:00"), List.of(
                new PuestoDto("Cajero", List.of("1", "1")),
                new PuestoDto("Tobogán", List.of("2", "3")),
                new PuestoDto("Carrera", List.of("3", "2"))), null, null, null));
        parque = grupos.crear(new GrupoDto(null, "Parque", List.of("Tobogán", "Carrera")));
    }

    @Test
    void elQueSabeElGrupoSabeSusPuestosYPuedeTenerUnoDePrincipal() {
        EmpleadoDto ana = empleados.crear(new EmpleadoDto(null, "Ana", List.of("parque"), List.of("Carrera")));
        assertThat(ana.puestos()).containsExactly("Parque");
        assertThat(ana.puedeHacer()).containsExactly("Tobogán", "Carrera");
        assertThat(ana.principales()).containsExactly("Carrera");

        // Lo que se le agrega al grupo después, también lo sabe.
        grupos.actualizar(parque.id(), new GrupoDto(null, "Parque", List.of("Tobogán", "Carrera", "Minigolf")));
        assertThat(empleados.listar().get(0).puedeHacer()).contains("Minigolf");
    }

    @Test
    void armarElDiaCuentaLoQueSabePorElGrupo() {
        List<PresenteDto> presentes = List.of(
                new PresenteDto(empleados.crear(new EmpleadoDto(null, "Cata", List.of("Cajero"), List.of("Cajero"))).id(), null),
                new PresenteDto(empleados.crear(new EmpleadoDto(null, "Beto", List.of("Parque"), List.of())).id(), null),
                // Hoy sólo Tobogán, aunque sabe todo el grupo: se lo puede limitar puesto por puesto.
                new PresenteDto(empleados.crear(new EmpleadoDto(null, "Dani", List.of("Parque"), List.of())).id(), List.of("Tobogán")));
        var mejor = sugerencias.sugerir(new PedidoSugerenciaDto(null, presentes)).get(0);
        assertThat(mejor.diferencias()).containsExactly("Dani tendría que hacer Carrera y hoy no puede.");
    }

    @Test
    void renombrarYBorrarNoLeHacenPerderNadaAlEmpleado() {
        empleados.crear(new EmpleadoDto(null, "Ana", List.of("Parque", "Cajero"), List.of()));
        grupos.actualizar(parque.id(), new GrupoDto(null, "Rueda", List.of("Tobogán", "Carrera")));
        assertThat(empleados.listar().get(0).puestos()).containsExactlyInAnyOrder("Rueda", "Cajero");

        unificador.renombrar(new RenombreDto("Carrera", "Karting"));
        assertThat(grupos.listar().get(0).puestos()).containsExactly("Tobogán", "Karting");

        grupos.eliminar(parque.id());
        assertThat(empleados.listar().get(0).puestos()).containsExactlyInAnyOrder("Cajero", "Tobogán", "Karting");
    }

    @Test
    void elNombreNoPuedeSerElDeUnPuestoNiTenerOtroGrupoAdentro() {
        assertThatThrownBy(() -> grupos.crear(new GrupoDto(null, "Cajero", List.of("Tobogán"))))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> grupos.crear(new GrupoDto(null, "Todo", List.of("Parque", "Cajero"))))
                .isInstanceOf(ValidacionException.class);
    }
}
