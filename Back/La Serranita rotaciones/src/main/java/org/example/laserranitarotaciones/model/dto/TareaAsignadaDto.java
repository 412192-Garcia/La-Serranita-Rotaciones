package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** aviso: lo que no se pudo cumplir (falta gente, nadie sabe hacerla); null si salió bien. */
public record TareaAsignadaDto(Long tareaId, String nombre, int cantidad, List<Long> empleadoIds, String aviso) {
}
