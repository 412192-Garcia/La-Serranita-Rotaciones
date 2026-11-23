package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.RolSabidoDto;
import org.example.laserranitarotaciones.model.entity.Empleado;
import org.example.laserranitarotaciones.model.entity.PuestoParque;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
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
    private final GrupoPuestosService grupos;
    private final PuestoParqueRepository puestosParque;

    @Transactional(readOnly = true)
    public List<EmpleadoDto> listar() {
        return repositorio.findAll().stream()
                .map(this::aDto)
                .sorted(Comparator.comparing(EmpleadoDto::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Una tarea que se borra deja de figurar en lo que sabe cada uno. */
    @Transactional
    public void olvidarTarea(Long idTarea) {
        repositorio.findAll().forEach(e -> e.getTareas().remove(idTarea));
    }

    @Transactional(readOnly = true)
    public List<EmpleadoDto> buscar(List<Long> ids) {
        return repositorio.findAllById(ids).stream().map(this::aDto).toList();
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

    private EmpleadoDto aDto(Empleado e) {
        return new EmpleadoDto(e.getId(), e.getNombre(), List.copyOf(e.getPuestos()), List.copyOf(e.getPrincipales()),
                e.isEnEntrenamiento(), e.getIdAcompananteHabitual(), List.copyOf(e.getTareas()),
                grupos.expandir(e.getPuestos()), e.getRoles().stream().map(RolSabidoDto::de).toList(), e.getApodo());
    }

    private void aplicar(Empleado e, EmpleadoDto dto) {
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (nombre.length() > 60) throw new ValidacionException("El nombre es muy largo.");
        String apodo = dto.apodo() == null || dto.apodo().isBlank() ? null : dto.apodo().trim().replaceAll("\\s+", " ");
        if (apodo != null && apodo.length() > 30) throw new ValidacionException("El apodo es muy largo (hasta 30 letras).");

        // Sin repetidos, comparando sin mayúsculas ni acentos. Los grupos quedan con su nombre.
        Map<String, String> nombresGrupos = grupos.nombres();
        Map<String, String> puestos = new LinkedHashMap<>();
        for (String p : dto.puestos() == null ? List.<String>of() : dto.puestos()) {
            if (p == null || p.isBlank() || Nombres.esDescanso(p)) continue;
            puestos.putIfAbsent(Nombres.clave(p), nombresGrupos.getOrDefault(Nombres.clave(p), p.trim()));
        }
        // El principal es un puesto, puede ser uno que sabe por un grupo.
        Map<String, String> puede = new LinkedHashMap<>();
        grupos.expandir(puestos.values()).forEach(p -> puede.put(Nombres.clave(p), p));
        List<String> principales = new ArrayList<>();
        for (String p : dto.principales() == null ? List.<String>of() : dto.principales()) {
            String puesto = puede.get(Nombres.clave(p));
            if (puesto == null) throw new ValidacionException(p + " es principal pero no está en lo que sabe hacer.");
            if (!principales.contains(puesto)) principales.add(puesto);
        }

        // Un rol sólo si sabe el puesto y el puesto tiene ese rol. Se guarda con los nombres del puesto.
        List<String> roles = new ArrayList<>();
        for (RolSabidoDto r : dto.roles() == null ? List.<RolSabidoDto>of() : dto.roles()) {
            if (r == null || r.puesto() == null || r.rol() == null) continue;
            String puesto = puede.get(Nombres.clave(r.puesto()));
            if (puesto == null) throw new ValidacionException("Para ser " + r.rol() + " tiene que saber " + r.puesto() + ".");
            PuestoParque p = puestosParque.findByClave(Nombres.clave(puesto)).orElse(null);
            String rol = p == null ? null : p.getRoles().stream().filter(x -> Nombres.clave(x).equals(Nombres.clave(r.rol()))).findFirst().orElse(null);
            if (rol == null) throw new ValidacionException(puesto + " no tiene el rol " + r.rol() + ".");
            String guardado = new RolSabidoDto(p.getNombre(), rol).guardado();
            if (!roles.contains(guardado)) roles.add(guardado);
        }

        Long acompanante = dto.enEntrenamiento() ? dto.acompananteHabitualId() : null;
        if (acompanante != null) {
            if (acompanante.equals(e.getId())) throw new ValidacionException("No puede acompañarse a sí mismo.");
            Empleado otro = repositorio.findById(acompanante)
                    .orElseThrow(() -> new ValidacionException("El acompañante elegido ya no existe."));
            if (otro.isEnEntrenamiento()) throw new ValidacionException(otro.getNombre() + " también está en entrenamiento.");
        }

        e.setNombre(nombre);
        e.setApodo(apodo);
        e.setEnEntrenamiento(dto.enEntrenamiento());
        e.setIdAcompananteHabitual(acompanante);
        e.getPuestos().clear();
        e.getPuestos().addAll(puestos.values());
        e.getPrincipales().clear();
        e.getPrincipales().addAll(principales);
        e.getTareas().clear();
        if (dto.tareas() != null) e.getTareas().addAll(dto.tareas());
        e.getRoles().clear();
        e.getRoles().addAll(roles);
    }
}
