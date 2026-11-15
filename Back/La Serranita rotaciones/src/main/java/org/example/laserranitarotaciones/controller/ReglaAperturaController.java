package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.ReglaAperturaDto;
import org.example.laserranitarotaciones.service.ReglaAperturaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reglas-apertura")
@RequiredArgsConstructor
public class ReglaAperturaController {

    private final ReglaAperturaService reglas;

    @Operation(summary = "Qué puestos se abren según cuántos empleados hay (sin la Tirolesa) y la afluencia")
    @GetMapping
    public List<ReglaAperturaDto> listar() {
        return reglas.listar();
    }

    @Operation(summary = "Crea o cambia la regla de esa cantidad de empleados y afluencia")
    @PutMapping
    public ReglaAperturaDto guardar(@RequestBody ReglaAperturaDto dto) {
        return reglas.guardar(dto);
    }

    @Operation(summary = "El orden de rotación predeterminado (la rueda de cada regla sigue este orden)")
    @GetMapping("/orden-rotacion")
    public List<String> ordenRotacion() {
        return reglas.ordenRotacion();
    }

    @Operation(summary = "Cambia el orden de rotación predeterminado y reordena la rueda de todas las reglas")
    @PutMapping("/orden-rotacion")
    public List<String> guardarOrdenRotacion(@RequestBody List<String> nombres) {
        return reglas.guardarOrdenRotacion(nombres);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        reglas.eliminar(id);
    }

    @Operation(summary = "Reemplaza las reglas con la tabla \"Puestos abiertos\" de un Excel",
            description = "Poca gente = afluencia baja, mucha = alta; la media empieza igual que la baja.")
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReglaAperturaService.Importacion importar(@RequestParam MultipartFile archivo) {
        return reglas.importar(archivo);
    }
}
