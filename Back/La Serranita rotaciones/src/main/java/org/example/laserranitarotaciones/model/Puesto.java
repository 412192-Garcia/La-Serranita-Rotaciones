package org.example.laserranitarotaciones.model;

/** Fila de la planilla. nota puede ser null. iniciaRotacion: ahí arranca la rotación (el *). */
public record Puesto(int orden, String nombre, String nota, boolean iniciaRotacion) {

    public boolean esDescanso() {
        return nombre.trim().equalsIgnoreCase("Descanso");
    }

    /** Libre: los que sobran en ese horario, sin puesto. */
    public boolean esLibre() {
        return nombre.trim().equalsIgnoreCase("Libre");
    }

    /** Como se lee en la tarjeta: "Canilla (consultar al encargado)". El * lo agrega quien arma el renglón. */
    public String conNota() {
        return nota == null || nota.isBlank() ? nombre : nombre + " (" + nota + ")";
    }
}
