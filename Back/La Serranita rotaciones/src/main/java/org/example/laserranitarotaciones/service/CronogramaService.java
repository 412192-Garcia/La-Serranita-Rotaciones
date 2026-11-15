package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.CronogramaResumenDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.entity.Cronograma;
import org.example.laserranitarotaciones.model.entity.PuestoCronograma;
import org.example.laserranitarotaciones.repository.CronogramaRepository;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.example.laserranitarotaciones.model.entity.PuestoParque;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CronogramaService {

    private static final Pattern CELDA = Pattern.compile("^\\d+(-\\d+)*$");

    private final CronogramaRepository repositorio;
    private final RotacionService rotacion;
    private final ImportadorExcelService importador;
    private final PuestoParqueRepository puestosParque;

    @Transactional(readOnly = true)
    public List<CronogramaResumenDto> listar() {
        return repositorio.findAll().stream()
                .map(c -> resumen(aDto(c, false)))
                .sorted(Comparator.comparing(CronogramaResumenDto::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public CronogramaDto obtener(Long id) {
        return aDto(buscar(id), true);
    }

    /** Los nombres de los puestos del parque (la lista cargada en Puestos). */
    @Transactional(readOnly = true)
    public List<String> puestos() {
        Set<String> nombres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        puestosParque.findAll().stream().map(PuestoParque::getNombre).forEach(nombres::add);
        return new ArrayList<>(nombres);
    }

    /** Al arrancar: los puestos de los cronogramas que todavía no estén en la lista se agregan. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void registrarPuestosDeCronogramas() {
        repositorio.findAll().forEach(c -> PuestoParqueService.registrar(puestosParque, c));
    }

    @Transactional
    public CronogramaDto crear(CronogramaDto dto) {
        Cronograma c = new Cronograma();
        aplicar(c, normalizar(dto));
        PuestoParqueService.registrar(puestosParque, c);
        return aDto(repositorio.save(c), true);
    }

    @Transactional
    public CronogramaDto actualizar(Long id, CronogramaDto dto) {
        Cronograma c = buscar(id);
        aplicar(c, normalizar(dto));
        PuestoParqueService.registrar(puestosParque, c);
        return aDto(repositorio.save(c), true);
    }

    @Transactional
    public void eliminar(Long id) {
        repositorio.delete(buscar(id));
    }

    @Transactional
    public List<CronogramaResumenDto> importar(MultipartFile archivo) {
        List<CronogramaDto> leidos;
        try (InputStream entrada = archivo.getInputStream()) {
            leidos = importador.leer(entrada);
        } catch (IOException e) {
            throw new ValidacionException("No se pudo leer el archivo.");
        }
        List<CronogramaResumenDto> guardados = new ArrayList<>();
        for (CronogramaDto dto : leidos) {
            Cronograma c = new Cronograma();
            try {
                aplicar(c, normalizar(dto));
            } catch (ValidacionException e) {
                throw new ValidacionException("Hoja \"" + dto.nombre() + "\": " + e.getMessage());
            }
            PuestoParqueService.registrar(puestosParque, c);
            guardados.add(resumen(aDto(repositorio.save(c), false)));
        }
        return guardados;
    }

    @Transactional(readOnly = true)
    public List<CronogramaDto> todos() {
        return repositorio.findAll().stream().map(c -> aDto(c, false)).toList();
    }

    public CronogramaResumenDto resumen(CronogramaDto c) {
        List<String> horarios = c.horarios();
        return new CronogramaResumenDto(c.id(), c.nombre(), c.afluencia(), c.empleados(), c.puestos().size(),
                horarios.isEmpty() ? null : horarios.get(0),
                horarios.isEmpty() ? null : horarios.get(horarios.size() - 1),
                c.fijos());
    }

    private Cronograma buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese cronograma no existe."));
    }

    private CronogramaDto aDto(Cronograma c, boolean conTarjetas) {
        List<String> horarios = c.getHorarios().stream().map(RotacionService::formatear).toList();
        List<PuestoDto> puestos = c.getPuestos().stream()
                .map(p -> new PuestoDto(p.getNombre(), p.getNota(), p.isIniciaRotacion(), List.copyOf(p.getCeldas())))
                .toList();
        PlanRotacion plan = PlanRotacion.de(horarios, puestos);
        return new CronogramaDto(c.getId(), c.getNombre(), c.getAfluencia(), horarios, puestos,
                rotacion.cantidadEmpleados(plan), rotacion.fijos(plan),
                conTarjetas ? rotacion.armarTarjetas(plan) : null);
    }

    private static void aplicar(Cronograma c, CronogramaDto dto) {
        c.setNombre(dto.nombre());
        c.setAfluencia(dto.afluencia());
        c.getHorarios().clear();
        dto.horarios().forEach(h -> c.getHorarios().add(LocalTime.parse(h)));
        c.getPuestos().clear();
        for (PuestoDto p : dto.puestos()) {
            PuestoCronograma puesto = new PuestoCronograma();
            puesto.setNombre(p.nombre());
            puesto.setNota(p.nota());
            puesto.setIniciaRotacion(p.iniciaRotacion());
            puesto.getCeldas().addAll(p.celdas());
            c.getPuestos().add(puesto);
        }
    }

    /** Valida y deja todo en formato fijo: horas "HH:mm", celdas "1-2" o "Cerrado". */
    static CronogramaDto normalizar(CronogramaDto dto) {
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (nombre.length() > 100) throw new ValidacionException("El nombre es muy largo.");
        if (dto.afluencia() == null) throw new ValidacionException("Falta cuánta gente se espera.");

        List<String> horasDto = dto.horarios() == null ? List.of() : dto.horarios();
        if (horasDto.isEmpty()) throw new ValidacionException("Tiene que tener al menos un horario.");
        List<String> horarios = new ArrayList<>();
        LocalTime anterior = null;
        for (String texto : horasDto) {
            LocalTime hora = texto == null ? null : ImportadorExcelService.parsearHora(texto);
            if (hora == null) throw new ValidacionException("\"" + texto + "\" no es un horario.");
            if (anterior != null && !hora.isAfter(anterior)) {
                throw new ValidacionException("Los horarios tienen que ir de menor a mayor.");
            }
            horarios.add(RotacionService.formatear(hora));
            anterior = hora;
        }

        List<PuestoDto> puestosDto = dto.puestos() == null ? List.of() : dto.puestos();
        if (puestosDto.isEmpty()) throw new ValidacionException("Tiene que tener al menos un puesto.");
        Set<String> nombres = new HashSet<>();
        List<PuestoDto> puestos = new ArrayList<>();
        for (PuestoDto p : puestosDto) {
            // Una nota escrita en el nombre ("Canilla (consultar al encargado)") pasa a su campo.
            Nombres.NombrePuesto escrito = Nombres.separar(p.nombre());
            String nombrePuesto = escrito.nombre();
            String nota = p.nota() != null && !p.nota().isBlank() ? p.nota().trim() : escrito.nota();
            boolean iniciaRotacion = p.iniciaRotacion() || escrito.iniciaRotacion();
            if (nota != null && nota.length() > 100) throw new ValidacionException("La nota de " + nombrePuesto + " es muy larga.");
            if (nombrePuesto.isEmpty()) throw new ValidacionException("Hay un puesto sin nombre.");
            if (nombrePuesto.length() > 60) throw new ValidacionException("El nombre de " + nombrePuesto + " es muy largo.");
            if (!nombres.add(nombrePuesto.toLowerCase(Locale.ROOT))) {
                throw new ValidacionException("El puesto " + nombrePuesto + " está repetido.");
            }
            List<String> celdasDto = p.celdas() == null ? List.of() : p.celdas();
            List<String> celdas = new ArrayList<>();
            for (int i = 0; i < horarios.size(); i++) {
                String valor = i < celdasDto.size() && celdasDto.get(i) != null ? celdasDto.get(i).replaceAll("\\s", "") : "";
                if (PlanRotacion.esCerrado(valor)) {
                    celdas.add(PlanRotacion.CERRADO);
                    continue;
                }
                if (!valor.isEmpty() && !CELDA.matcher(valor).matches()) {
                    throw new ValidacionException(nombrePuesto + " a las " + horarios.get(i)
                            + ": \"" + valor + "\" no es un número de empleado.");
                }
                celdas.add(PlanRotacion.empleados(valor).stream().map(String::valueOf).collect(Collectors.joining("-")));
            }
            puestos.add(new PuestoDto(nombrePuesto, nota, iniciaRotacion, celdas));
        }
        return new CronogramaDto(dto.id(), nombre, dto.afluencia(), horarios, puestos, null, null, null);
    }
}
