package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.PuestoParqueDto;
import org.example.laserranitarotaciones.model.dto.RenombreDto;
import org.example.laserranitarotaciones.service.PuestoParqueService;
import org.example.laserranitarotaciones.service.UnificadorPuestosService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final UnificadorPuestosService unificador;

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

    @Operation(summary = "Agrega un puesto a la lista (por ejemplo uno que todavía no está en ningún cronograma)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PuestoParqueDto crear(@RequestBody PuestoParqueDto dto) {
        return puestos.crear(dto);
    }

    @Operation(summary = "Borra un puesto que no está en ningún cronograma",
            description = "También sale de lo que sabe cada empleado y de los grupos.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@RequestParam String nombre) {
        puestos.eliminar(nombre);
    }

    @Operation(summary = "Si el puesto abre más tarde: horario y personas según la afluencia",
            description = "Sólo usa nombre y abreTarde (null = abierto todo el día).")
    @PutMapping("/abre-tarde")
    public PuestoParqueDto cambiarAbreTarde(@RequestBody PuestoParqueDto dto) {
        return puestos.cambiarAbreTarde(dto);
    }

    @Operation(summary = "Si el puesto aparece en la columna Puestos fijos de los cronogramas",
            description = "Sólo usa nombre y mostrarEnFijos; la prioridad no cambia.")
    @PutMapping("/fijos")
    public PuestoParqueDto cambiarMostrarEnFijos(@RequestBody PuestoParqueDto dto) {
        return puestos.cambiarMostrarEnFijos(dto);
    }

    @Operation(summary = "Cambia el nombre de un puesto en cronogramas, empleados y prioridad",
            description = "Si un cronograma ya tiene el nombre nuevo, las dos filas se juntan.")
    @PostMapping("/renombrar")
    public UnificadorPuestosService.Resultado renombrar(@RequestBody RenombreDto pedido) {
        return unificador.renombrar(pedido);
    }
}
