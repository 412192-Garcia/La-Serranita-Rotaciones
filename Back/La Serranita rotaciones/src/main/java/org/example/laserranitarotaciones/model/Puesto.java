package org.example.laserranitarotaciones.model;

/** Fila de la planilla. nota puede ser null. */
public record Puesto(int orden, String nombre, String nota) {

    public boolean esDescanso() {
        return nombre.trim().equalsIgnoreCase("Descanso");
    }

    /** Como se lee en la tarjeta: "Canilla (consultar al encargado)". */
    public String conNota() {
        return nota == null || nota.isBlank() ? nombre : nombre + " (" + nota + ")";
    }
}
