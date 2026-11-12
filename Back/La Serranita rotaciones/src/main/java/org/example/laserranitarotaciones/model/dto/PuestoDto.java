package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * Una fila del cronograma. nota: aclaración que no cambia el puesto ("consultar al encargado").
 * Una celda por horario: vacía, "3", "1-2" o "Cerrado".
 */
public record PuestoDto(String nombre, String nota, List<String> celdas) {

    public PuestoDto(String nombre, List<String> celdas) {
        this(nombre, null, celdas);
    }
}
