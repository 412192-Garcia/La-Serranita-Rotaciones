package org.example.laserranitarotaciones.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lee un Excel con un cronograma por hoja. El nombre del cronograma es el de la hoja. La 2ª fila
 * tiene los horarios desde la columna B y de la 3ª en adelante va un puesto por fila: el nombre en A
 * y en cada horario el número de empleado, varios separados con guión ("1-2") o "Cerrado".
 * La 1ª fila dice la afluencia: "Baja", "Media" o "Alta" (por ejemplo A1 "Afluencia" y B1 "Alta").
 */
@Service
public class ImportadorExcelService {

    private static final int FILA_HORARIOS = 1;
    private static final Pattern HORA = Pattern.compile("^(\\d{1,2})(?:[:.](\\d{2}))?(?::\\d{2})?\\s*(?:hs?\\.?)?$");

    public List<CronogramaDto> leer(InputStream entrada) {
        try (Workbook libro = abrir(entrada)) {
            DataFormatter formato = new DataFormatter(Locale.forLanguageTag("es-AR"));
            FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();
            List<CronogramaDto> cronogramas = new ArrayList<>();
            for (Sheet hoja : libro) {
                if (hoja.getPhysicalNumberOfRows() == 0) continue;
                cronogramas.add(leerHoja(hoja, formato, evaluador));
            }
            if (cronogramas.isEmpty()) throw new ValidacionException("El archivo no tiene cronogramas.");
            return cronogramas;
        } catch (ValidacionException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidacionException("No se pudo leer el archivo.");
        }
    }

    private CronogramaDto leerHoja(Sheet hoja, DataFormatter formato, FormulaEvaluator evaluador) {
        List<String> horarios = leerHorarios(hoja, formato, evaluador);
        List<PuestoDto> puestos = new ArrayList<>();

        for (int r = FILA_HORARIOS + 1; r <= hoja.getLastRowNum(); r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) continue;
            String nombre = texto(fila.getCell(0), formato, evaluador);
            if (nombre.isEmpty()) continue;

            List<String> celdas = new ArrayList<>();
            for (int c = 1; c <= horarios.size(); c++) {
                String valor = texto(fila.getCell(c), formato, evaluador);
                celdas.add(PlanRotacion.esCerrado(valor) ? PlanRotacion.CERRADO
                        : PlanRotacion.empleados(valor).stream().map(String::valueOf).collect(Collectors.joining("-")));
            }
            puestos.add(new PuestoDto(nombre, celdas));
        }
        if (puestos.isEmpty()) {
            throw new ValidacionException("La hoja \"" + hoja.getSheetName() + "\" no tiene puestos.");
        }
        return new CronogramaDto(null, hoja.getSheetName().trim(),
                afluencia(hoja, formato, evaluador),
                horarios, puestos, null, null, null);
    }

    private static Afluencia afluencia(Sheet hoja, DataFormatter formato, FormulaEvaluator evaluador) {
        Row fila = hoja.getRow(0);
        if (fila != null) {
            for (Cell celda : fila) {
                String texto = sinAcentos(texto(celda, formato, evaluador));
                for (Afluencia a : Afluencia.values()) {
                    if (Pattern.compile("\\b" + a.name() + "\\b").matcher(texto).find()) return a;
                }
            }
        }
        throw new ValidacionException("La hoja \"" + hoja.getSheetName()
                + "\" no dice la afluencia en la primera fila (Baja, Media o Alta).");
    }

    private List<String> leerHorarios(Sheet hoja, DataFormatter formato, FormulaEvaluator evaluador) {
        Row fila = hoja.getRow(FILA_HORARIOS);
        List<String> horarios = new ArrayList<>();
        if (fila != null) {
            for (int c = 1; c < fila.getLastCellNum(); c++) {
                Cell celda = fila.getCell(c);
                String texto = texto(celda, formato, evaluador);
                if (texto.isEmpty()) break;
                horarios.add(RotacionService.formatear(hora(celda, texto, hoja.getSheetName())));
            }
        }
        if (horarios.isEmpty()) {
            throw new ValidacionException("La hoja \"" + hoja.getSheetName() + "\" no tiene horarios en la segunda fila.");
        }
        return horarios;
    }

    private static LocalTime hora(Cell celda, String texto, String nombreHoja) {
        // Una hora de Excel es la fracción del día; un 8 suelto se toma como texto ("8" = 8:00).
        if (celda.getCellType() == CellType.NUMERIC
                && (DateUtil.isCellDateFormatted(celda) || celda.getNumericCellValue() < 1)) {
            double valor = celda.getNumericCellValue();
            long segundos = Math.round((valor - Math.floor(valor)) * 86_400) % 86_400;
            return LocalTime.ofSecondOfDay(segundos);
        }
        LocalTime hora = parsearHora(texto);
        if (hora == null) {
            throw new ValidacionException("En la hoja \"" + nombreHoja + "\", \"" + texto + "\" no es un horario.");
        }
        return hora;
    }

    /** "8", "8:00", "08.30", "8 hs". null si no es una hora. */
    public static LocalTime parsearHora(String texto) {
        Matcher m = HORA.matcher(texto.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        return h < 24 && min < 60 ? LocalTime.of(h, min) : null;
    }

    private static Workbook abrir(InputStream entrada) {
        try {
            return WorkbookFactory.create(entrada);
        } catch (Exception e) {
            throw new ValidacionException("El archivo tiene que ser un Excel (.xlsx o .xls).");
        }
    }

    private static String texto(Cell celda, DataFormatter formato, FormulaEvaluator evaluador) {
        return celda == null ? "" : formato.formatCellValue(celda, evaluador).trim();
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }
}
