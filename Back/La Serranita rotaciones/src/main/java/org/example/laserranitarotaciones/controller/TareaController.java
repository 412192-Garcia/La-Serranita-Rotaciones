package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.PedidoTareasDto;
import org.example.laserranitarotaciones.model.dto.TareaAsignadaDto;
import org.example.laserranitarotaciones.model.dto.TareaDto;
import org.example.laserranitarotaciones.model.dto.TareaHechaDto;
import org.example.laserranitarotaciones.service.TareaService;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService tareas;

    @GetMapping
    public List<TareaDto> listar() {
        return tareas.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TareaDto crear(@RequestBody TareaDto dto) {
        return tareas.crear(dto);
    }

    @PutMapping("/{id}")
    public TareaDto actualizar(@PathVariable Long id, @RequestBody TareaDto dto) {
        return tareas.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        tareas.eliminar(id);
    }

    @Operation(summary = "Reparte todas las tareas entre los que vinieron, evitando repetir con los días anteriores")
    @PostMapping("/repartir")
    public List<TareaAsignadaDto> repartir(@RequestBody PedidoTareasDto pedido) {
        return tareas.repartir(pedido);
    }

    @Operation(summary = "Guarda quién hizo cada tarea ese día (reemplaza lo de esa fecha)")
    @PutMapping("/hechas/{fecha}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarHechas(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                              @RequestBody List<TareaHechaDto> hechas) {
        tareas.guardarHechas(fecha, hechas);
    }
}
