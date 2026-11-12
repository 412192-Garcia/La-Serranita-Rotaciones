package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.laserranitarotaciones.model.HojaLeida;
import org.example.laserranitarotaciones.model.dto.PlanillaDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanillaService {

    private final LectorPlanillaService lector;
    private final RotacionService rotacion;

    public PlanillaDto leer(MultipartFile archivo, int indiceHoja) {
        try (InputStream entrada = archivo.getInputStream(); Workbook libro = abrir(entrada)) {
            List<String> hojas = new ArrayList<>();
            libro.forEach(h -> hojas.add(h.getSheetName()));
            if (indiceHoja < 0 || indiceHoja >= hojas.size()) {
                throw new PlanillaInvalidaException("Esa variación no está en el archivo.");
            }
            HojaLeida hoja = lector.leer(libro, indiceHoja);
            return new PlanillaDto(
                    nombrePlan(archivo.getOriginalFilename()),
                    hojas,
                    indiceHoja,
                    hoja.horarios().stream().map(h -> RotacionService.formatear(h.hora())).toList(),
                    hoja.filas(),
                    rotacion.armarTarjetas(hoja));
        } catch (IOException e) {
            throw new PlanillaInvalidaException("No se pudo leer el archivo.");
        }
    }

    private static Workbook abrir(InputStream entrada) {
        try {
            return WorkbookFactory.create(entrada);
        } catch (Exception e) {
            throw new PlanillaInvalidaException("El archivo tiene que ser un Excel (.xlsx o .xls).");
        }
    }

    private static String nombrePlan(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) return "Plan";
        String nombre = nombreArchivo.replaceAll("^.*[\\/]", "");
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }
}
