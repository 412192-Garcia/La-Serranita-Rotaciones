package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.entity.Empleado;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final EmpleadoRepository repositorio;

    @Transactional(readOnly = true)
    public List<EmpleadoDto> listar() {
        return repositorio.findAll().stream()
                .map(EmpleadoService::aDto)
                .sorted(Comparator.comparing(EmpleadoDto::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmpleadoDto> buscar(List<Long> ids) {
        return repositorio.findAllById(ids).stream().map(EmpleadoService::aDto).toList();
    }

    @Transactional
    public EmpleadoDto crear(EmpleadoDto dto) {
        Empleado e = new Empleado();
        aplicar(e, dto);
        return aDto(repositorio.save(e));
    }

    @Transactional
    public EmpleadoDto actualizar(Long id, EmpleadoDto dto) {
        Empleado e = repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese empleado no existe."));
        aplicar(e, dto);
        return aDto(repositorio.save(e));
    }

    @Transactional
    public void eliminar(Long id) {
        repositorio.delete(repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese empleado no existe.")));
        // Los que lo acompañaban quedan sin acompañante habitual.
        repositorio.findAll().stream()
                .filter(e -> id.equals(e.getIdAcompananteHabitual()))
                .forEach(e -> e.setIdAcompananteHabitual(null));
    }

    static EmpleadoDto aDto(Empleado e) {
        return new EmpleadoDto(e.getId(), e.getNombre(), List.copyOf(e.getPuestos()), List.copyOf(e.getPrincipales()),
                e.isEnEntrenamiento(), e.getIdAcompananteHabitual());
    }

    private void aplicar(Empleado e, EmpleadoDto dto) {
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (nombre.length() > 60) throw new ValidacionException("El nombre es muy largo.");

        // Sin repetidos, comparando sin mayúsculas ni acentos.
        Map<String, String> puestos = new LinkedHashMap<>();
        for (String p : dto.puestos() == null ? List.<String>of() : dto.puestos()) {
            if (p == null || p.isBlank() || Nombres.esDescanso(p)) continue;
            puestos.putIfAbsent(Nombres.clave(p), p.trim());
        }
        List<String> principales = new ArrayList<>();
        for (String p : dto.principales() == null ? List.<String>of() : dto.principales()) {
            String puesto = puestos.get(Nombres.clave(p));
            if (puesto == null) throw new ValidacionException(p + " es principal pero no está en lo que sabe hacer.");
            if (!principales.contains(puesto)) principales.add(puesto);
        }

        Long acompanante = dto.enEntrenamiento() ? dto.acompananteHabitualId() : null;
        if (acompanante != null) {
            if (acompanante.equals(e.getId())) throw new ValidacionException("No puede acompañarse a sí mismo.");
            Empleado otro = repositorio.findById(acompanante)
                    .orElseThrow(() -> new ValidacionException("El acompañante elegido ya no existe."));
            if (otro.isEnEntrenamiento()) throw new ValidacionException(otro.getNombre() + " también está en entrenamiento.");
        }

        e.setNombre(nombre);
        e.setEnEntrenamiento(dto.enEntrenamiento());
        e.setIdAcompananteHabitual(acompanante);
        e.getPuestos().clear();
        e.getPuestos().addAll(puestos.values());
        e.getPrincipales().clear();
        e.getPrincipales().addAll(principales);
    }
}
