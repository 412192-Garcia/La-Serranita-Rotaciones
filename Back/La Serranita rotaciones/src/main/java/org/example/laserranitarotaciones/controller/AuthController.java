package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.config.Acceso;
import org.example.laserranitarotaciones.config.JwtService;
import org.example.laserranitarotaciones.model.dto.LoginDto;
import org.example.laserranitarotaciones.model.dto.SesionDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final Acceso acceso;
    private final JwtService jwt;

    @Operation(summary = "Entrar con el usuario y la contraseña del .env; devuelve el token")
    @PostMapping("/login")
    public SesionDto entrar(@RequestBody LoginDto dto) {
        return acceso.entrar(dto, jwt);
    }
}
