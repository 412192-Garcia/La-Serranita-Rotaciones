package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** Una celda por horario: vacía, "3", "1-2" o "Cerrado". */
public record PuestoDto(String nombre, List<String> celdas) {
}
