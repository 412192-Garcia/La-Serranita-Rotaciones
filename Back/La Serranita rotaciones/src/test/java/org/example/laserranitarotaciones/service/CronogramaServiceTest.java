package org.example.laserranitarotaciones.service;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.CronogramaResumenDto;
import org.example.laserranitarotaciones.model.dto.PedidoSugerenciaDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
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

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
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
        assertThat(lleno.tarjetas()).hasSize(4);
        assertThat(cronogramas.puestos()).containsExactly("Boleteria", "Toboganes");
    }

    @Test
    void sugiereElMasAdecuadoPrimero() throws Exception {
        cronogramas.importar(excel());

        List<SugerenciaDto> lista = sugerencias.sugerir(new PedidoSugerenciaDto(4, Afluencia.ALTA, List.of()));
        assertThat(lista).extracting(s -> s.cronograma().nombre()).containsExactly("Lleno", "Tranqui");
        assertThat(lista.get(0).diferencias()).isEmpty();
        assertThat(lista.get(1).diferencias()).containsExactly(
                "Es para 2 empleados y hay 4.", "Es para afluencia baja.");

        List<SugerenciaDto> conFijo = sugerencias.sugerir(new PedidoSugerenciaDto(4, Afluencia.ALTA, List.of("boleteria")));
        assertThat(conFijo.get(0).diferencias()).containsExactly("En boleteria el empleado rota.");
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

            Sheet lleno = libro.createSheet("Lleno");
            fila(lleno, 0, "Día de mucha gente - ALTA");
            fila(lleno, 1, "Puesto", "10:00", "11:00");
            fila(lleno, 2, "Boleteria", 1, 2);
            fila(lleno, 3, "Toboganes", "3-4", 1);
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
