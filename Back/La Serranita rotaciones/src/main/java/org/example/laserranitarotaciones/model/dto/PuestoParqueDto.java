package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Prioridad;

/** mostrarEnFijos: si aparece en la columna "Puestos fijos" de los cronogramas (Cajero casi siempre es fijo). */
public record PuestoParqueDto(String nombre, Prioridad prioridad, boolean mostrarEnFijos) {

    public PuestoParqueDto(String nombre, Prioridad prioridad) {
        this(nombre, prioridad, true);
    }
}
