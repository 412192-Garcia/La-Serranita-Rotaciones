package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.BorradorDto;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.CronogramaResumenDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDiaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.PedidoSugerenciaDto;
import org.example.laserranitarotaciones.model.dto.SugerenciaDto;
import org.example.laserranitarotaciones.service.CronogramaService;
import org.example.laserranitarotaciones.service.GeneradorService;
import org.example.laserranitarotaciones.service.SugerenciaService;
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
@RequestMapping("/api/cronogramas")
@RequiredArgsConstructor
public class CronogramaController {

    private final CronogramaService cronogramas;
    private final SugerenciaService sugerencias;
    private final GeneradorService generador;

    @Operation(summary = "Lista los cronogramas")
    @GetMapping
    public List<CronogramaResumenDto> listar() {
        return cronogramas.listar();
    }

    @Operation(summary = "Un cronograma con las tarjetas de sus empleados")
    @GetMapping("/{id}")
    public CronogramaDto obtener(@PathVariable Long id) {
        return cronogramas.obtener(id);
    }

    @Operation(summary = "Nombres de puestos de todos los cronogramas, sin Descanso")
    @GetMapping("/puestos")
    public List<String> puestos() {
        return cronogramas.puestos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CronogramaDto crear(@RequestBody CronogramaDto dto) {
        return cronogramas.crear(dto);
    }

    @PutMapping("/{id}")
    public CronogramaDto actualizar(@PathVariable Long id, @RequestBody CronogramaDto dto) {
        return cronogramas.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        cronogramas.eliminar(id);
    }

    @Operation(summary = "Guarda cada hoja del Excel como un cronograma")
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<CronogramaResumenDto> importar(@RequestParam MultipartFile archivo) {
        return cronogramas.importar(archivo);
    }

    @Operation(summary = "Los cronogramas más adecuados para el día, del mejor al peor")
    @PostMapping("/sugerir")
    public List<SugerenciaDto> sugerir(@RequestBody PedidoSugerenciaDto pedido) {
        return sugerencias.sugerir(pedido);
    }

    @Operation(summary = "Arma un borrador para los que vinieron y reparte los números (no lo guarda)")
    @PostMapping("/generar-dia")
    public BorradorDto generarConPresentes(@RequestBody PedidoGeneradorDiaDto pedido) {
        return sugerencias.generarConPresentes(pedido);
    }

    @Operation(summary = "Elige a mano quién cubre un rol que nadie de los que vinieron podía hacer (no lo guarda)")
    @PostMapping("/cubrir-rol")
    public BorradorDto cubrirRol(@RequestBody org.example.laserranitarotaciones.model.dto.PedidoCubrirRolDto pedido) {
        return sugerencias.cubrirRol(pedido);
    }

    @Operation(summary = "Arma un borrador de cronograma (no lo guarda)")
    @PostMapping("/generar")
    public BorradorDto generar(@RequestBody PedidoGeneradorDto pedido) {
        return generador.generar(pedido);
    }
}
