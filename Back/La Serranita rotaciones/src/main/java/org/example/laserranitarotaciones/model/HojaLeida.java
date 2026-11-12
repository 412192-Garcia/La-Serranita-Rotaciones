package org.example.laserranitarotaciones.model;

import org.example.laserranitarotaciones.model.dto.FilaPuestoDto;

import java.util.List;

/** Una hoja (variación) ya leída: lo que se muestra y los turnos para armar las tarjetas. */
public record HojaLeida(List<Horario> horarios, List<FilaPuestoDto> filas, List<Turno> turnos) {
}
