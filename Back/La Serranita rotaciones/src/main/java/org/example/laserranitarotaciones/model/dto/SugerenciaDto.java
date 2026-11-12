package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** diferencias vacía = coincide en todo. */
public record SugerenciaDto(CronogramaResumenDto cronograma, List<String> diferencias) {
}
