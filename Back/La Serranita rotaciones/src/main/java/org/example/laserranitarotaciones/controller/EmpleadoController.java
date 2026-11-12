package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.service.EmpleadoService;
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
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoService empleados;

    @Operation(summary = "Lista los empleados con los puestos que saben hacer")
    @GetMapping
    public List<EmpleadoDto> listar() {
        return empleados.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpleadoDto crear(@RequestBody EmpleadoDto dto) {
        return empleados.crear(dto);
    }

    @PutMapping("/{id}")
    public EmpleadoDto actualizar(@PathVariable Long id, @RequestBody EmpleadoDto dto) {
        return empleados.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        empleados.eliminar(id);
    }
}
