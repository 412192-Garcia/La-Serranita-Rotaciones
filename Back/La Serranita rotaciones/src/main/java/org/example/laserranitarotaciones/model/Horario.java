package org.example.laserranitarotaciones.model;

import java.time.LocalTime;

/** Columna de la planilla. orden arranca en 1. */
public record Horario(int orden, LocalTime hora) {
}
