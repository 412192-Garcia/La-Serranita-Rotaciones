package org.example.laserranitarotaciones.service;

/** El mensaje se muestra tal cual al usuario. */
public class PlanillaInvalidaException extends RuntimeException {

    public PlanillaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
