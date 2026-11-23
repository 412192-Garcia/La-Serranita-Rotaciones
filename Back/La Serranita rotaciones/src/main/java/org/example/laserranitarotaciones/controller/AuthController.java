package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.config.UsuarioAutenticado;
import org.example.laserranitarotaciones.model.dto.LoginDto;
import org.example.laserranitarotaciones.model.dto.SesionDto;
import org.example.laserranitarotaciones.model.dto.UsuarioDto;
import org.example.laserranitarotaciones.service.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarios;

    @Operation(summary = "Entrar con usuario y contraseña; devuelve el token")
    @PostMapping("/login")
    public SesionDto entrar(@RequestBody LoginDto dto) {
        return usuarios.entrar(dto);
    }

    @Operation(summary = "Quién está logueado (sirve para saber si el token sigue andando)")
    @GetMapping("/yo")
    public UsuarioDto yo(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return usuarios.buscar(yo.id());
    }
}
