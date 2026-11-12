package org.example.laserranitarotaciones.model;

/** Fila de la planilla. */
public record Puesto(int orden, String nombre) {

    public boolean esDescanso() {
        return nombre.trim().equalsIgnoreCase("Descanso");
    }
}
