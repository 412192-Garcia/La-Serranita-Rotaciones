package org.example.laserranitarotaciones.model.entity;

/**
 * Qué pasa con un puesto en un horario.
 * ABRE_TARDE: cerrado fuera del horario; su gente cubre el parque mientras tanto (Tirolesa).
 * REFUERZO: abierto todo el día con su gente de siempre; en el horario van personas de más (Casa del zorrito).
 * MENOS: un puesto fijo con varios; en el horario se sacan personas, que pasan a cubrir el parque (Cajero a la tarde).
 */
public enum TipoHorario {
    ABRE_TARDE, REFUERZO, MENOS
}
