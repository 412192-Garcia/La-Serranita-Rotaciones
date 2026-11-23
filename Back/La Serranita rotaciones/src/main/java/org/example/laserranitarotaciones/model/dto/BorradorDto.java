package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * Un cronograma generado, sin guardar, y lo que no se pudo cumplir. asignacion: si se generó con los
 * empleados que vinieron, quién hace cada número (vacía si no). faltantes: roles que nadie de los que vinieron
 * puede hacer (hay que elegir a mano quién los cubre). refuerzos: puestos que quedaron sin nadie (pedir ayuda).
 * deConfiguracion: con los que vinieron, los avisos que son de cómo está cargado (reglas, horarios especiales),
 * aparte de los del día.
 */
public record BorradorDto(CronogramaDto cronograma, List<String> avisos, List<AsignacionDto> asignacion,
                          List<FaltanteRolDto> faltantes, List<RefuerzoDto> refuerzos, List<String> deConfiguracion) {

    public BorradorDto(CronogramaDto cronograma, List<String> avisos) {
        this(cronograma, avisos, List.of());
    }

    public BorradorDto(CronogramaDto cronograma, List<String> avisos, List<AsignacionDto> asignacion) {
        this(cronograma, avisos, asignacion, List.of(), List.of(), List.of());
    }

    public BorradorDto(CronogramaDto cronograma, List<String> avisos, List<AsignacionDto> asignacion,
                       List<FaltanteRolDto> faltantes, List<RefuerzoDto> refuerzos) {
        this(cronograma, avisos, asignacion, faltantes, refuerzos, List.of());
    }
}
