package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.PuestoParqueDto;
import org.example.laserranitarotaciones.model.entity.Cronograma;
import org.example.laserranitarotaciones.model.entity.Empleado;
import org.example.laserranitarotaciones.model.entity.GrupoPuestos;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.example.laserranitarotaciones.model.entity.PuestoParque;
import org.example.laserranitarotaciones.model.entity.TipoHorario;
import org.example.laserranitarotaciones.repository.CronogramaRepository;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.example.laserranitarotaciones.repository.GrupoPuestosRepository;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los puestos del parque: una lista cargada, no la que sale de los cronogramas. Los puestos de un
 * cronograma se agregan solos al guardarlo; los demás (Tirolesa) se cargan a mano.
 */
@Service
@RequiredArgsConstructor
public class PuestoParqueService {

    private final PuestoParqueRepository repositorio;
    private final CronogramaRepository cronogramas;
    private final EmpleadoRepository empleados;
    private final GrupoPuestosRepository grupos;

    @Transactional(readOnly = true)
    public List<PuestoParqueDto> listar() {
        return repositorio.findAll().stream()
                .sorted(Comparator.comparing(PuestoParque::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(PuestoParqueService::aDto)
                .toList();
    }

    @Transactional
    public PuestoParqueDto crear(PuestoParqueDto dto) {
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (nombre.length() > 60) throw new ValidacionException("El nombre es muy largo.");
        if (Nombres.noEsPuesto(nombre)) throw new ValidacionException(nombre + " no es un puesto.");
        String clave = Nombres.clave(nombre);
        if (repositorio.findByClave(clave).isPresent()) throw new ValidacionException("Ya existe el puesto " + nombre + ".");
        if (grupos.findAll().stream().anyMatch(g -> g.getClave().equals(clave))) {
            throw new ValidacionException("Ya hay un grupo que se llama " + nombre + ".");
        }
        PuestoParque p = new PuestoParque();
        p.setNombre(nombre);
        p.setClave(clave);
        p.setPrioridad(dto.prioridad() == null ? Prioridad.MEDIA : dto.prioridad());
        return aDto(repositorio.save(p));
    }

    /** Sólo si ningún cronograma lo usa. Sale también de lo que sabe cada empleado y de los grupos. */
    @Transactional
    public void eliminar(String nombre) {
        String clave = Nombres.clave(nombre == null ? "" : nombre);
        PuestoParque p = repositorio.findByClave(clave).orElseThrow(() -> new NoEncontradoException("Ese puesto no existe."));
        long usan = cronogramas.findAll().stream()
                .filter(c -> c.getPuestos().stream().anyMatch(x -> Nombres.clave(x.getNombre()).equals(clave)))
                .count();
        if (usan > 0) {
            throw new ValidacionException(p.getNombre() + " está en " + usan + (usan == 1 ? " cronograma" : " cronogramas")
                    + ": sacalo de ahí primero, o unificalo con otro con el lápiz.");
        }
        for (Empleado e : empleados.findAll()) {
            e.getPuestos().removeIf(x -> Nombres.clave(x).equals(clave));
            e.getPrincipales().removeIf(x -> Nombres.clave(x).equals(clave));
        }
        for (GrupoPuestos g : grupos.findAll()) g.getPuestos().removeIf(x -> Nombres.clave(x).equals(clave));
        repositorio.delete(p);
    }

    /** Sólo la prioridad: lo demás no se toca. */
    @Transactional
    public PuestoParqueDto cambiarPrioridad(PuestoParqueDto dto) {
        if (dto.prioridad() == null) throw new ValidacionException("Falta la prioridad.");
        PuestoParque p = buscarOCrear(dto.nombre());
        p.setPrioridad(dto.prioridad());
        return aDto(repositorio.save(p));
    }

    /** Si aparece o no en la columna "Puestos fijos" de los cronogramas. Lo demás no se toca. */
    @Transactional
    public PuestoParqueDto cambiarMostrarEnFijos(PuestoParqueDto dto) {
        PuestoParque p = buscarOCrear(dto.nombre());
        p.setMostrarEnFijos(dto.mostrarEnFijos());
        return aDto(repositorio.save(p));
    }

    /** Si abre más tarde, desde y hasta qué hora y con cuántas personas. abreTarde null = abierto todo el día. */
    @Transactional
    public PuestoParqueDto cambiarAbreTarde(PuestoParqueDto dto) {
        PuestoParque p = buscarOCrear(dto.nombre());
        PuestoParqueDto.AbreTarde a = dto.abreTarde();
        if (a == null) {
            p.setAbre(null);
            p.setHasta(null);
            p.setPersonasBaja(null);
            p.setPersonasMedia(null);
            p.setPersonasAlta(null);
            p.setTipo(TipoHorario.ABRE_TARDE);
            return aDto(repositorio.save(p));
        }
        LocalTime abre = hora(a.abre());
        if (abre == null) throw new ValidacionException("Falta a qué hora abre.");
        LocalTime hasta = a.hasta() == null || a.hasta().isBlank() ? null : hora(a.hasta());
        if (hasta != null && !hasta.isAfter(abre)) throw new ValidacionException("Tiene que cerrar después de abrir.");
        for (int n : List.of(a.baja(), a.media(), a.alta())) {
            if (n < 1 || n > 10) throw new ValidacionException("Las personas tienen que ser de 1 a 10.");
        }
        p.setAbre(RotacionService.formatear(abre));
        p.setHasta(hasta == null ? null : RotacionService.formatear(hasta));
        p.setPersonasBaja(a.baja());
        p.setPersonasMedia(a.media());
        p.setPersonasAlta(a.alta());
        p.setTipo(a.tipo() == null ? TipoHorario.ABRE_TARDE : a.tipo());
        return aDto(repositorio.save(p));
    }

    /** Clave del puesto → peso de su prioridad. Lo que no está es Media. */
    @Transactional(readOnly = true)
    public Map<String, Integer> pesos() {
        Map<String, Integer> pesos = new HashMap<>();
        repositorio.findAll().forEach(p -> pesos.put(p.getClave(), p.getPrioridad().peso()));
        return pesos;
    }

    /** Agrega a la lista los puestos de un cronograma que todavía no estén (con prioridad media). */
    static void registrar(PuestoParqueRepository repositorio, Cronograma c) {
        c.getPuestos().forEach(x -> {
            String nombre = x.getNombre().trim();
            if (Nombres.noEsPuesto(nombre) || repositorio.findByClave(Nombres.clave(nombre)).isPresent()) return;
            PuestoParque p = new PuestoParque();
            p.setNombre(nombre);
            p.setClave(Nombres.clave(nombre));
            p.setPrioridad(Prioridad.MEDIA);
            repositorio.save(p);
        });
    }

    private PuestoParque buscarOCrear(String nombre) {
        if (nombre == null || nombre.isBlank()) throw new ValidacionException("Falta el puesto.");
        String clave = Nombres.clave(nombre);
        PuestoParque p = repositorio.findByClave(clave).orElseGet(() -> {
            PuestoParque nuevo = new PuestoParque();
            nuevo.setPrioridad(Prioridad.MEDIA);
            nuevo.setNombre(nombre.trim());
            return nuevo;
        });
        p.setClave(clave);
        return p;
    }

    private static LocalTime hora(String texto) {
        if (texto == null || texto.isBlank()) return null;
        LocalTime h = ImportadorExcelService.parsearHora(texto);
        if (h == null) throw new ValidacionException("\"" + texto + "\" no es un horario.");
        return h;
    }

    private static PuestoParqueDto aDto(PuestoParque p) {
        PuestoParqueDto.AbreTarde a = p.getAbre() == null ? null : new PuestoParqueDto.AbreTarde(p.getAbre(), p.getHasta(),
                valor(p.getPersonasBaja()), valor(p.getPersonasMedia()), valor(p.getPersonasAlta()), p.getTipo());
        return new PuestoParqueDto(p.getNombre(), p.getPrioridad(), p.isMostrarEnFijos(), a);
    }

    private static int valor(Integer n) {
        return n == null ? 1 : n;
    }
}
