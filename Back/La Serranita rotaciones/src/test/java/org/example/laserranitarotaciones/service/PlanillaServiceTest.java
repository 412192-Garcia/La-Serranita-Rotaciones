package org.example.laserranitarotaciones.service;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.laserranitarotaciones.model.dto.PlanillaDto;
import org.example.laserranitarotaciones.model.dto.RenglonDto;
import org.example.laserranitarotaciones.model.dto.TarjetaDto;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanillaServiceTest {

    private final PlanillaService servicio = new PlanillaService(new LectorPlanillaService(), new RotacionService());

    @Test
    void armaLasTarjetasConLaRotacion() throws Exception {
        PlanillaDto planilla = servicio.leer(archivo(), 1);

        assertThat(planilla.plan()).isEqualTo("Plan 10");
        assertThat(planilla.hojas()).containsExactly("Variacion A", "Variacion B");
        assertThat(planilla.horarios()).containsExactly("10:00", "11:00", "12:00");
        assertThat(planilla.filas()).hasSize(4);
        assertThat(planilla.filas().get(0).celdas().get(0).fondo()).isEqualTo("#FFFF00");
        assertThat(planilla.tarjetas()).extracting(TarjetaDto::empleado).containsExactly(1, 2, 3, 4);

        assertThat(textos(planilla, 1)).containsExactly(
                "Boleteria",
                "Al ser cubierto por 2 rote a Toboganes",
                "Al ser cubierto por 3 rote a Descanso");
        assertThat(planilla.tarjetas().get(0).descanso()).isEqualTo("12:00");

        assertThat(textos(planilla, 2)).containsExactly(
                "Toboganes",
                "Al ser cubierto por 1 y 4 rote a Boleteria",
                "Boleteria");
        assertThat(textos(planilla, 3)).containsExactly("Toboganes y Cantina");
        assertThat(textos(planilla, 4)).containsExactly("Cantina", "Sin ser cubierto, rote a Toboganes");
    }

    @Test
    void rechazaArchivosQueNoSonExcel() {
        var archivo = new MockMultipartFile("archivo", "plan.txt", "text/plain", "hola".getBytes());
        assertThatThrownBy(() -> servicio.leer(archivo, 0)).isInstanceOf(PlanillaInvalidaException.class);
    }

    private static List<String> textos(PlanillaDto planilla, int empleado) {
        return planilla.tarjetas().stream()
                .filter(t -> t.empleado() == empleado)
                .findFirst().orElseThrow()
                .renglones().stream().map(RenglonDto::texto).toList();
    }

    private static MockMultipartFile archivo() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.createSheet("Variacion A").createRow(1).createCell(0).setCellValue("Puesto");
            Sheet hoja = libro.createSheet("Variacion B");
            hoja.createRow(0).createCell(0).setCellValue("Plan 10");
            fila(hoja, 1, "Puesto", "10:00", "11:00", "12:00");
            fila(hoja, 2, "Boleteria", 1, 2, 2);
            fila(hoja, 3, "Toboganes", 2, "1-4", 3);
            fila(hoja, 4, "Descanso", "", "", 1);
            fila(hoja, 5, "Cantina", 4, "", 3);

            XSSFCellStyle amarillo = libro.createCellStyle();
            amarillo.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            amarillo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hoja.getRow(2).getCell(1).setCellStyle(amarillo);

            libro.write(salida);
            return new MockMultipartFile("archivo", "Plan 10.xlsx", null, salida.toByteArray());
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
