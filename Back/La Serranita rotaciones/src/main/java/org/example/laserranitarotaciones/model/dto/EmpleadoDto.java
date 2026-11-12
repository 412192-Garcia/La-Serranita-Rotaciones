package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * principales tiene que estar dentro de puestos. acompananteHabitualId: a quién acompaña mientras
 * está en entrenamiento (puede ser null). tareas: ids de las tareas que piden saber y sabe hacer.
 */
public record EmpleadoDto(
        Long id,
        String nombre,
        List<String> puestos,
        List<String> principales,
        boolean enEntrenamiento,
        Long acompananteHabitualId,
        List<Long> tareas) {

    public EmpleadoDto(Long id, String nombre, List<String> puestos, List<String> principales) {
        this(id, nombre, puestos, principales, false, null, List.of());
    }

    public EmpleadoDto(Long id, String nombre, List<String> puestos, List<String> principales,
                       boolean enEntrenamiento, Long acompananteHabitualId) {
        this(id, nombre, puestos, principales, enEntrenamiento, acompananteHabitualId, List.of());
    }
}
