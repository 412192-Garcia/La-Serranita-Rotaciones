package org.example.laserranitarotaciones.service;

import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.example.laserranitarotaciones.model.HojaLeida;
import org.example.laserranitarotaciones.model.Horario;
import org.example.laserranitarotaciones.model.Puesto;
import org.example.laserranitarotaciones.model.Turno;
import org.example.laserranitarotaciones.model.dto.CeldaDto;
import org.example.laserranitarotaciones.model.dto.FilaPuestoDto;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee una hoja del plan. La 1ª fila se ignora (título), la 2ª tiene los horarios desde la columna B
 * y de la 3ª en adelante va un puesto por fila: el nombre en A y en cada horario el número de
 * empleado, o varios separados con guión ("1-2").
 */
@Service
public class LectorPlanillaService {

    private static final int FILA_HORARIOS = 1;
    private static final Pattern HORA = Pattern.compile("^(\\d{1,2})(?:[:.](\\d{2}))?(?::\\d{2})?\\s*(?:hs?\\.?)?$");
    private static final Pattern NUMERO = Pattern.compile("\\d+");

    public HojaLeida leer(Workbook libro, int indiceHoja) {
        Sheet hoja = libro.getSheetAt(indiceHoja);
        DataFormatter formato = new DataFormatter(Locale.forLanguageTag("es-AR"));
        FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();

        List<Horario> horarios = leerHorarios(hoja, formato, evaluador);
        List<FilaPuestoDto> filas = new ArrayList<>();
        List<Turno> turnos = new ArrayList<>();

        for (int r = FILA_HORARIOS + 1; r <= hoja.getLastRowNum(); r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) continue;
            String nombre = texto(fila.getCell(0), formato, evaluador);
            if (nombre.isEmpty()) continue;

            Puesto puesto = new Puesto(filas.size() + 1, nombre);
            List<CeldaDto> celdas = new ArrayList<>();
            for (Horario horario : horarios) {
                Cell celda = fila.getCell(horario.orden());
                String valor = texto(celda, formato, evaluador);
                celdas.add(new CeldaDto(valor, celda == null ? null : fondo(celda.getCellStyle()), celda == null ? null : colorLetra(libro, celda.getCellStyle())));
                for (int empleado : empleados(valor)) {
                    turnos.add(new Turno(puesto, horario, empleado));
                }
            }
            filas.add(new FilaPuestoDto(nombre, celdas));
        }
        if (filas.isEmpty()) {
            throw new PlanillaInvalidaException("La hoja \"" + hoja.getSheetName() + "\" no tiene puestos.");
        }
        return new HojaLeida(horarios, filas, turnos);
    }

    private List<Horario> leerHorarios(Sheet hoja, DataFormatter formato, FormulaEvaluator evaluador) {
        Row fila = hoja.getRow(FILA_HORARIOS);
        List<Horario> horarios = new ArrayList<>();
        if (fila != null) {
            for (int c = 1; c < fila.getLastCellNum(); c++) {
                Cell celda = fila.getCell(c);
                String texto = texto(celda, formato, evaluador);
                if (texto.isEmpty()) break;
                horarios.add(new Horario(c, hora(celda, texto, hoja.getSheetName())));
            }
        }
        if (horarios.isEmpty()) {
            throw new PlanillaInvalidaException("La hoja \"" + hoja.getSheetName() + "\" no tiene horarios en la segunda fila.");
        }
        return horarios;
    }

    private LocalTime hora(Cell celda, String texto, String nombreHoja) {
        // Una hora de Excel es la fracción del día; un 8 suelto se toma como texto ("8" = 8:00).
        if (celda.getCellType() == CellType.NUMERIC
                && (DateUtil.isCellDateFormatted(celda) || celda.getNumericCellValue() < 1)) {
            double valor = celda.getNumericCellValue();
            long segundos = Math.round((valor - Math.floor(valor)) * 86_400) % 86_400;
            return LocalTime.ofSecondOfDay(segundos);
        }
        Matcher m = HORA.matcher(texto.toLowerCase(Locale.ROOT));
        if (m.matches()) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            if (h < 24 && min < 60) return LocalTime.of(h, min);
        }
        throw new PlanillaInvalidaException("En la hoja \"" + nombreHoja + "\", \"" + texto + "\" no es un horario.");
    }

    private static List<Integer> empleados(String valor) {
        List<Integer> lista = new ArrayList<>();
        for (String parte : valor.split("-")) {
            String p = parte.trim();
            if (NUMERO.matcher(p).matches()) lista.add(Integer.parseInt(p));
        }
        return lista;
    }

    private static String texto(Cell celda, DataFormatter formato, FormulaEvaluator evaluador) {
        return celda == null ? "" : formato.formatCellValue(celda, evaluador).trim();
    }

    private static String fondo(CellStyle estilo) {
        if (estilo.getFillPattern() != FillPatternType.SOLID_FOREGROUND) return null;
        return hex(estilo.getFillForegroundColorColor());
    }

    private static String colorLetra(Workbook libro, CellStyle estilo) {
        var fuente = libro.getFontAt(estilo.getFontIndex());
        if (fuente instanceof XSSFFont f) return hex(f.getXSSFColor());
        if (fuente instanceof HSSFFont f && libro instanceof HSSFWorkbook h) return hex(f.getHSSFColor(h));
        return null;
    }

    private static String hex(Color color) {
        byte[] rgb = null;
        if (color instanceof XSSFColor x && !x.isAuto()) {
            rgb = x.getRGBWithTint();
            if (rgb == null) rgb = x.getRGB();
        } else if (color instanceof HSSFColor h && h.getIndex() != HSSFColor.HSSFColorPredefined.AUTOMATIC.getIndex()) {
            short[] t = h.getTriplet();
            rgb = new byte[]{(byte) t[0], (byte) t[1], (byte) t[2]};
        }
        if (rgb == null || rgb.length < 3) return null;
        int i = rgb.length - 3;
        return String.format("#%02X%02X%02X", rgb[i] & 0xFF, rgb[i + 1] & 0xFF, rgb[i + 2] & 0xFF);
    }
}
