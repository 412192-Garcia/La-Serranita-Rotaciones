package org.example.laserranitarotaciones.model.dto;

/** Un rol de un puesto para el que el empleado está preparado (Show del zorrito, Zorrito). */
public record RolSabidoDto(String puesto, String rol) {

    /** Cómo se guarda en el empleado. */
    public String guardado() {
        return puesto + "|" + rol;
    }

    public static RolSabidoDto de(String guardado) {
        int i = guardado.lastIndexOf('|');
        return i < 0 ? new RolSabidoDto(guardado, "") : new RolSabidoDto(guardado.substring(0, i), guardado.substring(i + 1));
    }
}
