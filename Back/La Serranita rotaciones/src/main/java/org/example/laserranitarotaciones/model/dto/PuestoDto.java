package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * Una fila del cronograma. nota: aclaración que no cambia el puesto ("consultar al encargado").
 * iniciaRotacion: el puesto donde arranca la rotación (en las planillas, el * delante del nombre).
 * Una celda por horario: vacía, "3", "1-2" o "Cerrado".
 */
public record PuestoDto(String nombre, String nota, boolean iniciaRotacion, List<String> celdas) {

    public PuestoDto(String nombre, List<String> celdas) {
        this(nombre, null, false, celdas);
    }

    public PuestoDto(String nombre, String nota, List<String> celdas) {
        this(nombre, nota, false, celdas);
    }
}
