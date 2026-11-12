package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** Tarjeta de un empleado. descanso es null si no tiene. */
public record TarjetaDto(int empleado, String descanso, List<RenglonDto> renglones) {
}
