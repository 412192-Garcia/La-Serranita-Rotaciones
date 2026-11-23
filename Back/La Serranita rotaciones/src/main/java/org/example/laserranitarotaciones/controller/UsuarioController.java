package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.config.UsuarioAutenticado;
import org.example.laserranitarotaciones.model.dto.CambioClaveDto;
import org.example.laserranitarotaciones.model.dto.NuevoUsuarioDto;
import org.example.laserranitarotaciones.model.dto.UsuarioDto;
import org.example.laserranitarotaciones.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarios;

    @GetMapping
    public List<UsuarioDto> listar() {
        return usuarios.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDto crear(@RequestBody NuevoUsuarioDto dto) {
        return usuarios.crear(dto);
    }

    @Operation(summary = "Cambiar la propia contraseña (pide la actual)")
    @PutMapping("/yo/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPropia(@AuthenticationPrincipal UsuarioAutenticado yo, @RequestBody CambioClaveDto dto) {
        usuarios.cambiarPropia(yo.id(), dto);
    }

    @Operation(summary = "Poner una contraseña nueva a otro usuario (si se la olvidó)")
    @PutMapping("/{id}/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarDeOtro(@PathVariable Long id, @RequestBody CambioClaveDto dto) {
        usuarios.cambiarDeOtro(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
        usuarios.eliminar(id, yo.id());
    }
}
