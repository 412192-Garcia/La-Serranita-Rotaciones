package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;

import java.util.List;

public record CronogramaResumenDto(
        Long id,
        String nombre,
        Afluencia afluencia,
        int empleados,
        int puestos,
        String desde,
        String hasta,
        List<String> fijos) {
}
