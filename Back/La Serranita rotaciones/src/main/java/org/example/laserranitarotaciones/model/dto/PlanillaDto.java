package org.example.laserranitarotaciones.model.dto;

import java.util.List;

public record PlanillaDto(
        String plan,
        List<String> hojas,
        int hoja,
        List<String> horarios,
        List<FilaPuestoDto> filas,
        List<TarjetaDto> tarjetas) {
}
