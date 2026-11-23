package org.example.laserranitarotaciones.model.dto;

/** mantener: "No cerrar sesión" (el token dura 14 días en vez de 12 horas). */
public record LoginDto(String usuario, String clave, boolean mantener) {
}
