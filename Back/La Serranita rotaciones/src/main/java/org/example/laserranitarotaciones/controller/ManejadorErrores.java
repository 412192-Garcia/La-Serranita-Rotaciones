package org.example.laserranitarotaciones.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.laserranitarotaciones.model.dto.ErrorDto;
import org.example.laserranitarotaciones.service.PlanillaInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(PlanillaInvalidaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto planillaInvalida(PlanillaInvalidaException e) {
        return new ErrorDto(e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ErrorDto muyGrande() {
        return new ErrorDto("El archivo es demasiado grande.");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDto inesperado(RuntimeException e) {
        log.error("Error inesperado", e);
        return new ErrorDto("No se pudo leer la planilla.");
    }
}
