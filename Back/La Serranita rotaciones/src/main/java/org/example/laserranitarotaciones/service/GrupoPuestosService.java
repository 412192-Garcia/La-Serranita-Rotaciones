package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.GrupoDto;
import org.example.laserranitarotaciones.model.entity.Empleado;
import org.example.laserranitarotaciones.model.entity.GrupoPuestos;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.example.laserranitarotaciones.repository.GrupoPuestosRepository;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Grupos de puestos. Un empleado puede saber un grupo ("Parque"): sabe todos sus puestos, también los
 * que se le agreguen al grupo después.
 */
@Service
@RequiredArgsConstructor
public class GrupoPuestosService {

    private final GrupoPuestosRepository repositorio;
    private final EmpleadoRepository empleados;
    private final PuestoParqueRepository puestos;

    @Transactional(readOnly = true)
    public List<GrupoDto> listar() {
        return repositorio.findAll().stream()
                .map(GrupoPuestosService::aDto)
                .sorted(Comparator.comparing(GrupoDto::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Cambia cada grupo por sus puestos. Sin repetidos (comparando con Nombres.clave). */
    @Transactional(readOnly = true)
    public List<String> expandir(Collection<String> nombres) {
        Map<String, GrupoPuestos> grupos = porClave();
        Map<String, String> puestos = new LinkedHashMap<>();
        for (String n : nombres) {
            GrupoPuestos g = grupos.get(Nombres.clave(n));
            if (g == null) puestos.putIfAbsent(Nombres.clave(n), n);
            else g.getPuestos().forEach(p -> puestos.putIfAbsent(Nombres.clave(p), p));
        }
        return List.copyOf(puestos.values());
    }

    /** Clave → nombre, de los grupos. */
    @Transactional(readOnly = true)
    public Map<String, String> nombres() {
        Map<String, String> m = new LinkedHashMap<>();
        repositorio.findAll().forEach(g -> m.put(g.getClave(), g.getNombre()));
        return m;
    }

    @Transactional
    public GrupoDto crear(GrupoDto dto) {
        GrupoPuestos g = new GrupoPuestos();
        aplicar(g, dto);
        return aDto(repositorio.save(g));
    }

    /** Si cambia el nombre, cambia también en lo que sabe cada empleado. */
    @Transactional
    public GrupoDto actualizar(Long id, GrupoDto dto) {
        GrupoPuestos g = buscar(id);
        String claveVieja = g.getClave();
        aplicar(g, dto);
        if (!claveVieja.equals(g.getClave())) {
            for (Empleado e : empleados.findAll()) {
                if (e.getPuestos().removeIf(p -> Nombres.clave(p).equals(claveVieja))) e.getPuestos().add(g.getNombre());
            }
        }
        return aDto(repositorio.save(g));
    }

    /** Los que sabían el grupo pasan a saber sus puestos uno por uno: no pierden nada. */
    @Transactional
    public void eliminar(Long id) {
        GrupoPuestos g = buscar(id);
        for (Empleado e : empleados.findAll()) {
            if (!e.getPuestos().removeIf(p -> Nombres.clave(p).equals(g.getClave()))) continue;
            for (String p : g.getPuestos()) {
                if (e.getPuestos().stream().noneMatch(x -> Nombres.clave(x).equals(Nombres.clave(p)))) e.getPuestos().add(p);
            }
        }
        repositorio.delete(g);
    }

    /** Cambia un puesto por otro dentro de los grupos (al renombrar o unificar puestos). */
    @Transactional
    public void renombrarPuesto(String claveDe, String a) {
        for (GrupoPuestos g : repositorio.findAll()) {
            List<String> lista = g.getPuestos();
            int i = indice(lista, claveDe);
            if (i < 0) continue;
            if (indice(lista, Nombres.clave(a)) >= 0) lista.remove(i);
            else lista.set(i, a);
        }
    }

    private void aplicar(GrupoPuestos g, GrupoDto dto) {
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (nombre.length() > 60) throw new ValidacionException("El nombre es muy largo.");
        String clave = Nombres.clave(nombre);
        if (Nombres.esDescanso(nombre) || puestos.findByClave(clave).isPresent()) {
            throw new ValidacionException("Ya hay un puesto que se llama " + nombre + ".");
        }
        Optional<GrupoPuestos> otro = repositorio.findAll().stream()
                .filter(x -> x.getClave().equals(clave) && !x.getId().equals(g.getId())).findFirst();
        if (otro.isPresent()) throw new ValidacionException("Ya hay un grupo que se llama " + nombre + ".");

        Set<String> grupos = porClave().keySet();
        Map<String, String> puestos = new LinkedHashMap<>();
        for (String p : dto.puestos() == null ? List.<String>of() : dto.puestos()) {
            if (p == null || p.isBlank() || Nombres.esDescanso(p)) continue;
            if (grupos.contains(Nombres.clave(p))) throw new ValidacionException("Un grupo no puede tener otro grupo adentro.");
            puestos.putIfAbsent(Nombres.clave(p), p.trim());
        }
        if (puestos.isEmpty()) throw new ValidacionException("Elegí al menos un puesto.");

        g.setNombre(nombre);
        g.setClave(clave);
        g.getPuestos().clear();
        g.getPuestos().addAll(puestos.values());
    }

    private GrupoPuestos buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese grupo no existe."));
    }

    private Map<String, GrupoPuestos> porClave() {
        Map<String, GrupoPuestos> m = new LinkedHashMap<>();
        repositorio.findAll().forEach(g -> m.put(g.getClave(), g));
        return m;
    }

    private static int indice(List<String> lista, String clave) {
        for (int i = 0; i < lista.size(); i++) if (Nombres.clave(lista.get(i)).equals(clave)) return i;
        return -1;
    }

    private static GrupoDto aDto(GrupoPuestos g) {
        return new GrupoDto(g.getId(), g.getNombre(), new ArrayList<>(g.getPuestos()));
    }
}
