package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;

import java.util.List;

/** empleados, fijos y tarjetas los calcula el servidor; al guardar se ignoran. */
public record CronogramaDto(
        Long id,
        String nombre,
        Afluencia afluencia,
        List<String> horarios,
        List<PuestoDto> puestos,
        Integer empleados,
        List<String> fijos,
        List<TarjetaDto> tarjetas) {
}
