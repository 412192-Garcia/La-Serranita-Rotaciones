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
            lista.putIfAbsent(Nombres.clave(nombre), new PuestoParqueDto(nombre, p == null ? Prioridad.MEDIA : p.getPrioridad()));
        }
        return List.copyOf(lista.values());
    }

    @Transactional
    public PuestoParqueDto cambiarPrioridad(PuestoParqueDto dto) {
        if (dto.nombre() == null || dto.nombre().isBlank()) throw new ValidacionException("Falta el puesto.");
        if (dto.prioridad() == null) throw new ValidacionException("Falta la prioridad.");
        String clave = Nombres.clave(dto.nombre());
        PuestoParque p = repositorio.findByClave(clave).orElseGet(PuestoParque::new);
        p.setNombre(dto.nombre().trim());
        p.setClave(clave);
        p.setPrioridad(dto.prioridad());
        repositorio.save(p);
        return new PuestoParqueDto(p.getNombre(), p.getPrioridad());
    }

    /** Clave del puesto → peso de su prioridad. Lo que no está es Media. */
    @Transactional(readOnly = true)
    public Map<String, Integer> pesos() {
        Map<String, Integer> pesos = new HashMap<>();
        repositorio.findAll().forEach(p -> pesos.put(p.getClave(), p.getPrioridad().peso()));
        return pesos;
    }
}
