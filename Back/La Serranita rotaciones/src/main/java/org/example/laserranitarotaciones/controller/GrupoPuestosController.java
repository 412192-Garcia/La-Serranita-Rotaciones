package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.GrupoDto;
import org.example.laserranitarotaciones.service.GrupoPuestosService;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/grupos")
@RequiredArgsConstructor
public class GrupoPuestosController {

    private final GrupoPuestosService grupos;

    @Operation(summary = "Grupos de puestos (ej. Parque = toda la rueda)")
    @GetMapping
    public List<GrupoDto> listar() {
        return grupos.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrupoDto crear(@RequestBody GrupoDto dto) {
        return grupos.crear(dto);
    }

    @Operation(summary = "Cambia un grupo; si cambia el nombre, también en los empleados")
    @PutMapping("/{id}")
    public GrupoDto actualizar(@PathVariable Long id, @RequestBody GrupoDto dto) {
        return grupos.actualizar(id, dto);
    }

    @Operation(summary = "Borra un grupo; los que lo sabían pasan a saber sus puestos uno por uno")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        grupos.eliminar(id);
    }
}
