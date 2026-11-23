package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * Un puesto que quedó sin nadie aunque se probó todo: hay que pedir un refuerzo (el encargado elige a quién).
 * celdas: posiciones de los horarios; horas: los mismos como texto.
 */
public record RefuerzoDto(String puesto, List<Integer> celdas, List<String> horas) {
}
