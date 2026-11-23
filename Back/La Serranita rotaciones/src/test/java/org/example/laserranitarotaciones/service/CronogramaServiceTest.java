package org.example.laserranitarotaciones.service;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.laserranitarotaciones.model.dto.BorradorDto;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDiaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.CronogramaResumenDto;
import org.example.laserranitarotaciones.model.dto.PedidoSugerenciaDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.AsignacionDto;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.PresenteDto;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.example.laserranitarotaciones.model.dto.PuestoParqueDto;
import org.example.laserranitarotaciones.model.dto.RenombreDto;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.example.laserranitarotaciones.model.dto.SugerenciaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.repository.CronogramaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CronogramaServiceTest {

    @Autowired
    private CronogramaService cronogramas;
    @Autowired
    private SugerenciaService sugerencias;
    @Autowired
    private CronogramaRepository repositorio;
    @Autowired
    private EmpleadoService empleados;
    @Autowired
    private EmpleadoRepository empleadoRepositorio;
    @Autowired
    private PuestoParqueService puestosParque;
    @Autowired
    private PuestoParqueRepository puestoRepositorio;
    @Autowired
    private UnificadorPuestosService unificador;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
        empleadoRepositorio.deleteAll();
        puestoRepositorio.deleteAll();
    }

    @Test
    void importaCadaHojaComoCronograma() throws Exception {
        List<CronogramaResumenDto> importados = cronogramas.importar(excel());

        assertThat(importados).extracting(CronogramaResumenDto::nombre).containsExactly("Tranqui", "Lleno");
        assertThat(importados).extracting(CronogramaResumenDto::afluencia).containsExactly(Afluencia.BAJA, Afluencia.ALTA);
        assertThat(importados.get(0).empleados()).isEqualTo(2);
        assertThat(importados.get(0).fijos()).containsExactly("Boleteria", "Toboganes");

        CronogramaDto lleno = cronogramas.obtener(importados.get(1).id());
        assertThat(lleno.horarios()).containsExactly("10:00", "11:00");
        assertThat(lleno.puestos().get(1).celdas()).containsExactly("3-4", "1");
        assertThat(lleno.puestos().get(1).nombre()).isEqualTo("Toboganes");
        assertThat(lleno.puestos().get(1).iniciaRotacion()).isTrue();
        assertThat(lleno.puestos().get(0).iniciaRotacion()).isFalse();
        assertThat(lleno.tarjetas()).hasSize(4);
        assertThat(cronogramas.puestos()).containsExactly("Boleteria", "Toboganes");
    }

    @Test
    void sugiereElMasAdecuadoPrimeroYRepartelosNumeros() throws Exception {
        cronogramas.importar(excel());
        List<String> todo = List.of("Boleteria", "Toboganes");
        List<PresenteDto> presentes = new ArrayList<>();
        for (String nombre : List.of("Ana", "Beto", "Carlos", "Dani")) {
            EmpleadoDto e = empleados.crear(new EmpleadoDto(null, nombre, todo,
                    nombre.equals("Ana") ? List.of("Boleteria") : List.of()));
            presentes.add(new PresenteDto(e.id(), null));
        }

        List<SugerenciaDto> lista = sugerencias.sugerir(new PedidoSugerenciaDto(Afluencia.ALTA, presentes));
        assertThat(lista).extracting(s -> s.cronograma().nombre()).containsExactly("Lleno", "Tranqui");
        assertThat(lista.get(0).diferencias()).isEmpty();
        assertThat(lista.get(0).asignacion()).extracting(AsignacionDto::nombre).doesNotContainNull().hasSize(4);
        assertThat(lista.get(1).diferencias()).startsWith(
                "Es para 2 empleados y vinieron 4.", "Es para afluencia baja.").hasSize(4);

        // Ana hoy sólo puede Toboganes: le toca un número que sólo hace Toboganes.
        presentes.set(0, new PresenteDto(presentes.get(0).empleadoId(), List.of("toboganes")));
        assertThat(sugerencias.sugerir(new PedidoSugerenciaDto(Afluencia.ALTA, presentes)).get(0).diferencias()).isEmpty();

        // Hoy no puede nada (y no importa la afluencia): antes que ponerla donde no puede, conviene el de 2
        // y dejarla sin puesto.
        presentes.set(0, new PresenteDto(presentes.get(0).empleadoId(), List.of()));
        SugerenciaDto sinAna = sugerencias.sugerir(new PedidoSugerenciaDto(null, presentes)).get(0);
        assertThat(sinAna.cronograma().nombre()).isEqualTo("Tranqui");
        assertThat(sinAna.diferencias()).contains("Ana queda sin puesto.");
    }

    @Test
    void conGenteDeMasNoEligeUnoChicoAunqueAlguienNoSepaUnPuesto() throws Exception {
        cronogramas.importar(excel());
        // Dani no sabe Boleteria: en el de 4 queda en Toboganes; el de 2 dejaría a dos sin puesto.
        List<PresenteDto> presentes = new ArrayList<>();
        for (String nombre : List.of("Ana", "Beto", "Carlos", "Dani")) {
            List<String> sabe = nombre.equals("Dani") ? List.of("Toboganes") : List.of("Boleteria", "Toboganes");
            presentes.add(new PresenteDto(empleados.crear(new EmpleadoDto(null, nombre, sabe, List.of())).id(), null));
        }
        assertThat(sugerencias.sugerir(new PedidoSugerenciaDto(Afluencia.BAJA, presentes)).get(0).cronograma().nombre())
                .isEqualTo("Lleno");
    }

    @Test
    void ocultarUnPuestoDeLosFijosNoTocaSuPrioridad() throws Exception {
        cronogramas.importar(excel());
        puestosParque.cambiarPrioridad(new PuestoParqueDto("Boleteria", Prioridad.ALTA));
        puestosParque.cambiarMostrarEnFijos(new PuestoParqueDto("boletería", null, false));
        assertThat(puestosParque.listar()).contains(new PuestoParqueDto("Boleteria", Prioridad.ALTA, false));

        // Cambiar la prioridad no lo vuelve a mostrar.
        puestosParque.cambiarPrioridad(new PuestoParqueDto("Boleteria", Prioridad.BAJA));
        assertThat(puestosParque.listar()).contains(new PuestoParqueDto("Boleteria", Prioridad.BAJA, false));
        assertThat(puestosParque.listar()).contains(new PuestoParqueDto("Toboganes", Prioridad.MEDIA, true));
    }

    @Test
    void losPuestosSonUnaListaCargadaYLosDeLosCronogramasSeAgreganSolos() throws Exception {
        cronogramas.importar(excel());
        puestosParque.crear(new PuestoParqueDto("Tirolesa", null));
        assertThat(cronogramas.puestos()).containsExactly("Boleteria", "Tirolesa", "Toboganes");

        puestosParque.cambiarAbreTarde(new PuestoParqueDto("tirolesa", null, true,
                new PuestoParqueDto.AbreTarde("13", null, 3, 4, 5)));
        assertThat(puestosParque.listar()).filteredOn(p -> p.nombre().equals("Tirolesa")).singleElement()
                .extracting(PuestoParqueDto::abreTarde).isEqualTo(new PuestoParqueDto.AbreTarde("13:00", null, 3, 4, 5));

        // Uno que está en un cronograma no se puede borrar; uno que no, sí (y sale de los empleados).
        assertThatThrownBy(() -> puestosParque.eliminar("Toboganes")).isInstanceOf(ValidacionException.class);
        empleados.crear(new EmpleadoDto(null, "Ana", List.of("Tirolesa", "Boleteria"), List.of("Tirolesa")));
        puestosParque.eliminar("Tirolesa");
        assertThat(cronogramas.puestos()).containsExactly("Boleteria", "Toboganes");
        assertThat(empleados.listar().get(0).puestos()).containsExactly("Boleteria");
        assertThat(empleados.listar().get(0).principales()).isEmpty();
    }

    @Test
    void generaConLosQueVinieronYLosReparte() {
        List<PresenteDto> presentes = new ArrayList<>();
        for (String nombre : List.of("Ana", "Beto", "Carla")) {
            List<String> principales = nombre.equals("Ana") ? List.of("Boleteria") : List.of();
            EmpleadoDto e = empleados.crear(new EmpleadoDto(null, nombre, List.of("Boleteria", "Toboganes", "Pileta"), principales));
            presentes.add(new PresenteDto(e.id(), null));
        }
        var pedido = new PedidoGeneradorDto("Hoy", Afluencia.MEDIA, 99, "10:00", "12:00", 60, "11:00", "11:00",
                List.of(new PedidoGeneradorDto.Fijo("Boleteria", 1)),
                List.of(new PedidoGeneradorDto.Lugar(List.of("Toboganes"), true), new PedidoGeneradorDto.Lugar(List.of("Pileta"), false)),
                List.of());
        BorradorDto b = sugerencias.generarConPresentes(new PedidoGeneradorDiaDto(pedido, presentes));

        // La cantidad sale de los que vinieron (no del 99), y Ana cae en su principal.
        assertThat(b.cronograma().empleados()).isEqualTo(3);
        assertThat(b.asignacion()).extracting(AsignacionDto::nombre).containsExactlyInAnyOrder("Ana", "Beto", "Carla");
        assertThat(b.asignacion()).filteredOn(a -> a.numero() == 1).singleElement().extracting(AsignacionDto::nombre).isEqualTo("Ana");
    }

    @Test
    void unPuestoDePrioridadBajaQueNadieSabeQuedaCerradoYLaPersonaLibre() {
        puestosParque.crear(new PuestoParqueDto("Pileta", null));
        puestosParque.cambiarPrioridad(new PuestoParqueDto("Pileta", Prioridad.BAJA));
        List<PresenteDto> presentes = new ArrayList<>();
        for (String nombre : List.of("Ana", "Beto", "Carla")) {
            EmpleadoDto e = empleados.crear(new EmpleadoDto(null, nombre, List.of("Boleteria", "Toboganes"), List.of()));
            presentes.add(new PresenteDto(e.id(), null));
        }
        var pedido = new PedidoGeneradorDto("Hoy", Afluencia.MEDIA, 3, "10:00", "12:00", 60, "11:00", "11:00",
                List.of(new PedidoGeneradorDto.Fijo("Boleteria", 1)),
                List.of(new PedidoGeneradorDto.Lugar(List.of("Toboganes"), true), new PedidoGeneradorDto.Lugar(List.of("Pileta"), false)),
                List.of());
        BorradorDto b = sugerencias.generarConPresentes(new PedidoGeneradorDiaDto(pedido, presentes));

        // Nadie sabe Pileta (baja): queda cerrada y quien tocaba ahí pasa a Libre; no hay "no sabe".
        PuestoDto pileta = b.cronograma().puestos().stream().filter(p -> p.nombre().equals("Pileta")).findFirst().orElseThrow();
        // Un puesto de prioridad baja sin nadie queda Cerrado (no pide refuerzo).
        assertThat(pileta.celdas()).containsOnly("Cerrado");
        assertThat(b.refuerzos()).noneMatch(r -> r.puesto().equals("Pileta"));
        assertThat(b.cronograma().puestos()).anyMatch(p -> p.nombre().equals("Libre"));
        assertThat(b.avisos()).noneMatch(a -> a.contains("no sabe")).anyMatch(a -> a.startsWith("Pileta queda cerrado"));
    }

    @Test
    void nadieVaAUnPuestoQuePodriaHacerElDeLaEstrella() {
        List<PresenteDto> presentes = new ArrayList<>();
        for (String nombre : List.of("Ana", "Beto", "Carla")) {
            List<String> principales = nombre.equals("Beto") ? List.of("Pileta") : List.of();
            EmpleadoDto e = empleados.crear(new EmpleadoDto(null, nombre, List.of("Boleteria", "Toboganes", "Pileta"), principales));
            presentes.add(new PresenteDto(e.id(), null));
        }
        // Toboganes y Pileta rotan: sin la estrella, Beto pasaría la mitad del día en Toboganes.
        var pedido = new PedidoGeneradorDto("Hoy", Afluencia.MEDIA, 3, "10:00", "14:00", 60, "12:00", "12:00",
                List.of(new PedidoGeneradorDto.Fijo("Boleteria", 1)),
                List.of(new PedidoGeneradorDto.Lugar(List.of("Toboganes"), true), new PedidoGeneradorDto.Lugar(List.of("Pileta"), false)),
                List.of());
        BorradorDto b = sugerencias.generarConPresentes(new PedidoGeneradorDiaDto(pedido, presentes));

        int beto = b.asignacion().stream().filter(a -> "Beto".equals(a.nombre())).findFirst().orElseThrow().numero();
        PuestoDto pileta = b.cronograma().puestos().stream().filter(p -> p.nombre().equals("Pileta")).findFirst().orElseThrow();
        PuestoDto descanso = b.cronograma().puestos().stream().filter(p -> p.nombre().equals("Descanso")).findFirst().orElseThrow();
        for (int k = 0; k < pileta.celdas().size(); k++) {
            if (PlanRotacion.empleados(descanso.celdas().get(k)).contains(beto)) continue;
            assertThat(PlanRotacion.empleados(pileta.celdas().get(k))).as("horario " + k).containsExactly(beto);
        }
    }

    @Test
    void validaYNormalizaAlGuardar() {
        CronogramaDto guardado = cronogramas.crear(new CronogramaDto(null, " Base ", Afluencia.MEDIA,
                List.of("9", "10.30", "11"), List.of(new PuestoDto("Cantina", List.of("1 - 2", "", "c"))),
                null, null, null));
        assertThat(guardado.nombre()).isEqualTo("Base");
        assertThat(guardado.horarios()).containsExactly("09:00", "10:30", "11:00");
        assertThat(guardado.puestos().get(0).celdas()).containsExactly("1-2", "", "Cerrado");

        assertThatThrownBy(() -> cronogramas.crear(new CronogramaDto(null, "Mal", Afluencia.MEDIA,
                List.of("10:00", "09:00"), List.of(new PuestoDto("Cantina", List.of("1", "1"))),
                null, null, null)))
                .isInstanceOf(ValidacionException.class)
                .hasMessage("Los horarios tienen que ir de menor a mayor.");
        assertThatThrownBy(() -> cronogramas.crear(new CronogramaDto(null, "Mal", Afluencia.MEDIA,
                List.of("10:00"), List.of(new PuestoDto("Cantina", List.of("Juan"))),
                null, null, null)))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("no es un número de empleado");
    }

    @Test
    void rechazaUnaHojaSinAfluencia() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Sin dato");
            fila(hoja, 0, "Plan");
            fila(hoja, 1, "Puesto", "10:00");
            fila(hoja, 2, "Cantina", 1);
            libro.write(salida);
            var archivo = new MockMultipartFile("archivo", "Plan.xlsx", null, salida.toByteArray());
            assertThatThrownBy(() -> cronogramas.importar(archivo))
                    .isInstanceOf(ValidacionException.class)
                    .hasMessageContaining("no dice la afluencia");
        }
        assertThat(repositorio.count()).isZero();
    }

    @Test
    void laPrioridadDeUnPuestoQuedaGuardada() throws Exception {
        cronogramas.importar(excel());
        assertThat(puestosParque.listar()).extracting(PuestoParqueDto::prioridad).containsOnly(Prioridad.MEDIA);

        puestosParque.cambiarPrioridad(new PuestoParqueDto("boletería", Prioridad.ALTA));
        assertThat(puestosParque.listar()).contains(new PuestoParqueDto("Boleteria", Prioridad.ALTA));
        assertThat(puestosParque.pesos()).containsEntry("boleteria", Prioridad.ALTA.peso());
    }

    @Test
    void unEmpleadoEnEntrenamientoAcompanaAOtro() {
        EmpleadoDto ana = empleados.crear(new EmpleadoDto(null, "Ana", List.of("Boleteria"), List.of()));
        EmpleadoDto juan = empleados.crear(new EmpleadoDto(null, "Juan", List.of(), List.of(), true, ana.id()));
        assertThat(juan.enEntrenamiento()).isTrue();
        assertThat(juan.acompananteHabitualId()).isEqualTo(ana.id());

        assertThatThrownBy(() -> empleados.crear(new EmpleadoDto(null, "Leo", List.of(), List.of(), true, juan.id())))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("también está en entrenamiento");

        empleados.eliminar(ana.id());
        assertThat(empleados.listar()).singleElement().extracting(EmpleadoDto::acompananteHabitualId).isNull();
    }

    @Test
    void renombrarJuntaFilasYActualizaEmpleadosYPrioridad() {
        CronogramaDto c = cronogramas.crear(new CronogramaDto(null, "Base", Afluencia.MEDIA, List.of("11:00", "12:00"),
                List.of(new PuestoDto("Cajero", List.of("1", "1")),
                        new PuestoDto("Cajero 2", List.of("9", "")),
                        new PuestoDto("Canilla o Laberinto", "consultar al encargado", List.of("3", "4"))),
                null, null, null));
        EmpleadoDto beto = empleados.crear(new EmpleadoDto(null, "Beto", List.of("Cajero 2"), List.of("Cajero 2")));
        puestosParque.cambiarPrioridad(new PuestoParqueDto("Cajero 2", Prioridad.ALTA));

        UnificadorPuestosService.Resultado r = unificador.renombrar(new RenombreDto("cajero 2", "Cajero"));
        unificador.renombrar(new RenombreDto("Canilla o Laberinto", "Canilla"));

        assertThat(r).isEqualTo(new UnificadorPuestosService.Resultado(1, 1));
        CronogramaDto despues = cronogramas.obtener(c.id());
        assertThat(despues.puestos()).extracting(PuestoDto::nombre).containsExactly("Cajero", "Canilla");
        assertThat(despues.puestos().get(0).celdas()).containsExactly("1-9", "1");
        assertThat(despues.puestos().get(1).nota()).isEqualTo("consultar al encargado");
        EmpleadoDto betoDespues = empleados.buscar(List.of(beto.id())).get(0);
        assertThat(betoDespues.puestos()).containsExactly("Cajero");
        assertThat(betoDespues.principales()).containsExactly("Cajero");
        assertThat(puestosParque.pesos()).containsEntry("cajero", Prioridad.ALTA.peso()).doesNotContainKey("cajero 2");
    }

    @Test
    void rechazaArchivosQueNoSonExcel() {
        var archivo = new MockMultipartFile("archivo", "plan.txt", "text/plain", "hola".getBytes());
        assertThatThrownBy(() -> cronogramas.importar(archivo)).isInstanceOf(ValidacionException.class);
    }

    private static MockMultipartFile excel() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet tranqui = libro.createSheet("Tranqui");
            fila(tranqui, 0, "Afluencia: Baja");
            fila(tranqui, 1, "Puesto", "10:00", "11:00");
            fila(tranqui, 2, "Boleteria", 1, 1);
            fila(tranqui, 3, "Toboganes", 2, 2);

            Sheet lleno = libro.createSheet("Hoja 2");
            fila(lleno, 0, "Afluencia", "Alta", "Nombre", "Lleno");
            fila(lleno, 1, "Puesto", "10:00", "11:00");
            fila(lleno, 2, "Boleteria", 1, 2);
            fila(lleno, 3, "*Toboganes", "3-4", 1);
            fila(lleno, 4, "Descanso", 2, "3-4");

            XSSFCellStyle amarillo = libro.createCellStyle();
            amarillo.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            amarillo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            lleno.getRow(2).getCell(1).setCellStyle(amarillo);

            libro.write(salida);
            return new MockMultipartFile("archivo", "Plan.xlsx", null, salida.toByteArray());
        }
    }

    private static void fila(Sheet hoja, int indice, Object... valores) {
        Row fila = hoja.createRow(indice);
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] instanceof Integer n) fila.createCell(i).setCellValue(n);
            else fila.createCell(i).setCellValue((String) valores[i]);
        }
    }
}
