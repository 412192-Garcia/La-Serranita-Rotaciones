package org.example.laserranitarotaciones.model.dto;

import java.util.List;

public record TareaHechaDto(Long tareaId, List<Long> empleadoIds) {
}
