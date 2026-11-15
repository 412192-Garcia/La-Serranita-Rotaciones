package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * Un cronograma generado, sin guardar, y lo que no se pudo cumplir. asignacion: si se generó con los
 * empleados que vinieron, quién hace cada número (vacía si no).
 */
public record BorradorDto(CronogramaDto cronograma, List<String> avisos, List<AsignacionDto> asignacion) {

    public BorradorDto(CronogramaDto cronograma, List<String> avisos) {
        this(cronograma, avisos, List.of());
    }
}
