package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * principales tiene que estar dentro de puestos. acompananteHabitualId: a quién acompaña mientras
 * está en entrenamiento (puede ser null).
 */
public record EmpleadoDto(
        Long id,
        String nombre,
        List<String> puestos,
        List<String> principales,
        boolean enEntrenamiento,
        Long acompananteHabitualId) {

    public EmpleadoDto(Long id, String nombre, List<String> puestos, List<String> principales) {
        this(id, nombre, puestos, principales, false, null);
    }
}
