package org.example.laserranitarotaciones.model;

/** Un empleado en un puesto en un horario. Una celda con "1-2" da dos turnos. */
public record Turno(Puesto puesto, Horario horario, int empleado) {
}
