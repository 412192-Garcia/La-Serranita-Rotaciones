package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.PuestoParqueDto;
import org.example.laserranitarotaciones.service.PuestoParqueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/puestos")
@RequiredArgsConstructor
public class PuestoParqueController {

    private final PuestoParqueService puestos;

    @Operation(summary = "Los puestos de los cronogramas con su prioridad")
    @GetMapping
    public List<PuestoParqueDto> listar() {
        return puestos.listar();
    }

    @Operation(summary = "Cambia la prioridad de un puesto")
    @PutMapping
    public PuestoParqueDto cambiarPrioridad(@RequestBody PuestoParqueDto dto) {
        return puestos.cambiarPrioridad(dto);
    }
}
