package org.example.laserranitarotaciones.model.dto;

/** Lo que vuelve al entrar: el token para mandar en cada pedido y con qué usuario se entró. */
public record SesionDto(String token, String usuario) {
}
