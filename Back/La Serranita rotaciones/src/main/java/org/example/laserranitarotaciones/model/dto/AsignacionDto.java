package org.example.laserranitarotaciones.model.dto;

/** Quién hace el número del cronograma. empleadoId null = no hay nadie para ese número. */
public record AsignacionDto(int numero, Long empleadoId, String nombre) {
}
