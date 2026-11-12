package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** Un empleado que vino hoy. puestosHoy: lo que puede hacer hoy; null = todo lo que sabe. */
public record PresenteDto(Long empleadoId, List<String> puestosHoy) {
}
