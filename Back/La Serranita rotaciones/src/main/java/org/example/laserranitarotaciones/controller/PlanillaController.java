package org.example.laserranitarotaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.PlanillaDto;
import org.example.laserranitarotaciones.service.PlanillaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/planillas")
@RequiredArgsConstructor
public class PlanillaController {

    private final PlanillaService planillas;

    @Operation(summary = "Lee una variación del plan y arma las tarjetas de los empleados")
    @PostMapping(value = "/leer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlanillaDto leer(@RequestParam MultipartFile archivo, @RequestParam(defaultValue = "0") int hoja) {
        return planillas.leer(archivo, hoja);
    }
}
