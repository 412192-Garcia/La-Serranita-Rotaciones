package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * puestos: lo que sabe, puede tener grupos ("Parque"). puedeHacer: los mismos con cada grupo cambiado
 * por sus puestos (lo calcula el back; al guardar se ignora). principales tiene que estar dentro de
 * puedeHacer. acompananteHabitualId: a quién acompaña mientras está en entrenamiento (puede ser null).
 * tareas: ids de las tareas que piden saber y sabe hacer.
 */
public record EmpleadoDto(
        Long id,
        String nombre,
        List<String> puestos,
        List<String> principales,
        boolean enEntrenamiento,
        Long acompananteHabitualId,
        List<Long> tareas,
        List<String> puedeHacer) {

    public EmpleadoDto(Long id, String nombre, List<String> puestos, List<String> principales) {
        this(id, nombre, puestos, principales, false, null, List.of());
    }

    public EmpleadoDto(Long id, String nombre, List<String> puestos, List<String> principales,
                       boolean enEntrenamiento, Long acompananteHabitualId) {
        this(id, nombre, puestos, principales, enEntrenamiento, acompananteHabitualId, List.of());
    }

    public EmpleadoDto(Long id, String nombre, List<String> puestos, List<String> principales,
                       boolean enEntrenamiento, Long acompananteHabitualId, List<Long> tareas) {
        this(id, nombre, puestos, principales, enEntrenamiento, acompananteHabitualId, tareas, null);
    }
}
