package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.PuestoParqueDto;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.example.laserranitarotaciones.model.entity.PuestoParque;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PuestoParqueService {

    private final PuestoParqueRepository repositorio;
    private final CronogramaService cronogramas;

    /** Los puestos de los cronogramas, con su prioridad (Media si nunca se cambió). */
    @Transactional(readOnly = true)
    public List<PuestoParqueDto> listar() {
        Map<String, PuestoParque> guardados = new HashMap<>();
        repositorio.findAll().forEach(p -> guardados.put(p.getClave(), p));
        Map<String, PuestoParqueDto> lista = new LinkedHashMap<>();
        for (String nombre : cronogramas.puestos()) {
            PuestoParque p = guardados.get(Nombres.clave(nombre));
            lista.putIfAbsent(Nombres.clave(nombre), p == null
                    ? new PuestoParqueDto(nombre, Prioridad.MEDIA)
                    : new PuestoParqueDto(nombre, p.getPrioridad(), p.isMostrarEnFijos()));
        }
        return List.copyOf(lista.values());
    }

    /** Sólo la prioridad: lo de la columna de fijos no se toca. */
    @Transactional
    public PuestoParqueDto cambiarPrioridad(PuestoParqueDto dto) {
        if (dto.prioridad() == null) throw new ValidacionException("Falta la prioridad.");
        PuestoParque p = buscarOCrear(dto.nombre());
        p.setPrioridad(dto.prioridad());
        return aDto(repositorio.save(p));
    }

    /** Si aparece o no en la columna "Puestos fijos" de los cronogramas. La prioridad no se toca. */
    @Transactional
    public PuestoParqueDto cambiarMostrarEnFijos(PuestoParqueDto dto) {
        PuestoParque p = buscarOCrear(dto.nombre());
        p.setMostrarEnFijos(dto.mostrarEnFijos());
        return aDto(repositorio.save(p));
    }

    private PuestoParque buscarOCrear(String nombre) {
        if (nombre == null || nombre.isBlank()) throw new ValidacionException("Falta el puesto.");
        String clave = Nombres.clave(nombre);
        PuestoParque p = repositorio.findByClave(clave).orElseGet(() -> {
            PuestoParque nuevo = new PuestoParque();
            nuevo.setPrioridad(Prioridad.MEDIA);
            return nuevo;
        });
        p.setNombre(nombre.trim());
        p.setClave(clave);
        return p;
    }

    private static PuestoParqueDto aDto(PuestoParque p) {
        return new PuestoParqueDto(p.getNombre(), p.getPrioridad(), p.isMostrarEnFijos());
    }

    /** Clave del puesto → peso de su prioridad. Lo que no está es Media. */
    @Transactional(readOnly = true)
    public Map<String, Integer> pesos() {
        Map<String, Integer> pesos = new HashMap<>();
        repositorio.findAll().forEach(p -> pesos.put(p.getClave(), p.getPrioridad().peso()));
        return pesos;
    }
}
