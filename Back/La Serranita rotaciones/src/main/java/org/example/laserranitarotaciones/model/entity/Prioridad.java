package org.example.laserranitarotaciones.model.entity;

/** Qué tan importante es que un puesto esté cubierto. peso: lo que cuesta dejarlo sin nadie que sepa. */
public enum Prioridad {
    BAJA(30), MEDIA(100), ALTA(300);

    private final int peso;

    Prioridad(int peso) {
        this.peso = peso;
    }

    public int peso() {
        return peso;
    }
}
