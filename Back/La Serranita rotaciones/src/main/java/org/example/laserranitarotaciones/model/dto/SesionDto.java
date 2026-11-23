package org.example.laserranitarotaciones.model.dto;

/** Lo que vuelve al entrar: el token para mandar en cada pedido y quién es. */
public record SesionDto(String token, UsuarioDto usuario) {
}
