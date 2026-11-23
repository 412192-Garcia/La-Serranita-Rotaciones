package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.ReglaAperturaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.example.laserranitarotaciones.model.entity.PuestoParque;
import org.example.laserranitarotaciones.model.entity.ReglaApertura;
import org.example.laserranitarotaciones.model.entity.TipoHorario;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.example.laserranitarotaciones.repository.ReglaAperturaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Las reglas de "puestos abiertos": con tantos empleados (todos los que vinieron) y tal afluencia, qué puestos
 * funcionan, cuáles son fijos, cuántos van a la Tirolesa y quién comparte qué. El generador arma la rueda con la
 * que corresponde.
 */
@Service
@RequiredArgsConstructor
public class ReglaAperturaService {

    /** Los puestos de la tabla que son fijos (la misma persona todo el día, como en las planillas); el resto rota. */
    private static final Set<String> FIJOS = Set.of(Nombres.clave("Cajero"), Nombres.clave("Casa del zorrito"));
    /** Baños también es fijo, salvo con muy poca gente (menos de estos empleados): ahí rota con los demás. */
    private static final int BANOS_FIJO_DESDE = 12;
    private static final String BANOS = Nombres.clave("Baños");
    /**
     * El recorrido de la rueda de las planillas del parque, si no se eligió otro en Puestos abiertos. Arranca en el
     * último puesto que se agregó (el último de la lista de la tabla, por ejemplo Pérgola o Pecera) y sigue en este
     * orden; lo que no está acá va al final.
     */
    private static final List<String> CIRCUITO = List.of("Carrera", "Baños", "Entrada", "Canilla", "Flotante de minigolf",
            "Minigolf", "Travesía", "Búsqueda", "Laberinto", "Tobogán").stream().map(Nombres::clave).toList();
    /** Cómo se llaman en la tabla del parque → cómo se llaman acá. */
    private static final Map<String, String> SINONIMOS = Map.of(
            Nombres.clave("Cajera"), "Cajero",
            Nombres.clave("Foto"), "Fotos",
            Nombres.clave("Flotante"), "Flotante de minigolf",
            Nombres.clave("Ayudante Tobogán"), "Pérgola",
            Nombres.clave("Ayudante de tobogán"), "Pérgola");

    private final ReglaAperturaRepository repositorio;
    private final PuestoParqueRepository puestos;

    public record Importacion(int reglas, List<String> avisos) {
    }

    /** El orden de rotación predeterminado: el elegido en Puestos abiertos o, si no hay, el de las planillas. */
    @Transactional(readOnly = true)
    public List<String> ordenRotacion() {
        List<PuestoParque> elegidos = puestos.findAll().stream().filter(p -> p.getOrdenRotacion() != null)
                .sorted(Comparator.comparingInt(PuestoParque::getOrdenRotacion)).toList();
        if (!elegidos.isEmpty()) return elegidos.stream().map(PuestoParque::getNombre).toList();
        return CIRCUITO.stream().map(puestos::findByClave).flatMap(java.util.Optional::stream).map(PuestoParque::getNombre).toList();
    }

    /**
     * Guarda el orden de rotación predeterminado y reordena la rueda de todas las reglas: cada una sigue
     * arrancando en el mismo lugar y el resto va en este orden (lo que no está en el orden, al final).
     */
    @Transactional
    public List<String> guardarOrdenRotacion(List<String> nombres) {
        List<PuestoParque> todos = puestos.findAll();
        Map<String, PuestoParque> porClave = new HashMap<>();
        todos.forEach(p -> porClave.put(p.getClave(), p));
        List<PuestoParque> enOrden = new ArrayList<>();
        for (String n : nombres == null ? List.<String>of() : nombres) {
            if (n == null || n.isBlank()) continue;
            PuestoParque p = porClave.get(Nombres.clave(n));
            if (p == null) throw new ValidacionException(n.trim() + " no está en la lista de puestos.");
            if (enOrden.contains(p)) throw new ValidacionException(p.getNombre() + " está repetido.");
            enOrden.add(p);
        }
        todos.forEach(p -> p.setOrdenRotacion(null));
        for (int i = 0; i < enOrden.size(); i++) enOrden.get(i).setOrdenRotacion(i);
        puestos.saveAll(todos);

        List<String> circuito = circuito();
        for (ReglaApertura r : repositorio.findAll()) {
            List<String> lugares = new ArrayList<>(r.getLugares());
            if (lugares.size() < 2) continue;
            List<String> resto = new ArrayList<>(lugares.subList(1, lugares.size()));
            resto.sort(Comparator.comparingInt(l -> enCircuito(List.of(l.split("\\+")), circuito)));
            resto.add(0, lugares.get(0));
            r.getLugares().clear();
            r.getLugares().addAll(resto);
            repositorio.save(r);
        }
        return ordenRotacion();
    }

    /** Las claves del orden de rotación, para ordenar las ruedas. */
    private List<String> circuito() {
        List<String> elegido = puestos.findAll().stream().filter(p -> p.getOrdenRotacion() != null)
                .sorted(Comparator.comparingInt(PuestoParque::getOrdenRotacion)).map(PuestoParque::getClave).toList();
        return elegido.isEmpty() ? CIRCUITO : elegido;
    }

    /** Dónde cae un lugar en el recorrido: el primero de sus puestos que está; si ninguno, al final. */
    private static int enCircuito(List<String> lugar, List<String> circuito) {
        return lugar.stream().mapToInt(x -> circuito.indexOf(Nombres.clave(x))).map(i -> i < 0 ? circuito.size() : i)
                .min().orElse(circuito.size());
    }

    @Transactional(readOnly = true)
    public List<ReglaAperturaDto> listar() {
        return repositorio.findAll().stream()
                .sorted(Comparator.comparingInt(ReglaApertura::getEmpleados).thenComparing(ReglaApertura::getAfluencia))
                .map(ReglaAperturaService::aDto)
                .toList();
    }

    /** Crea o cambia la regla de esa cantidad y afluencia (hay una sola por par). */
    @Transactional
    public ReglaAperturaDto guardar(ReglaAperturaDto dto) {
        if (dto.empleados() < 1 || dto.empleados() > 60) throw new ValidacionException("Los empleados tienen que ser de 1 a 60.");
        if (dto.afluencia() == null) throw new ValidacionException("Falta la afluencia.");
        ReglaApertura r = dto.id() != null
                ? repositorio.findById(dto.id()).orElseThrow(() -> new NoEncontradoException("Esa regla no existe."))
                : repositorio.findByEmpleadosAndAfluencia(dto.empleados(), dto.afluencia()).orElseGet(ReglaApertura::new);
        repositorio.findByEmpleadosAndAfluencia(dto.empleados(), dto.afluencia())
                .filter(otra -> !otra.getId().equals(r.getId()))
                .ifPresent(otra -> {
                    throw new ValidacionException("Ya hay una regla para " + dto.empleados() + " empleados con esa afluencia.");
                });

        List<String> fijos = new ArrayList<>();
        for (PedidoGeneradorDto.Fijo f : dto.fijos() == null ? List.<PedidoGeneradorDto.Fijo>of() : dto.fijos()) {
            if (f.puesto() == null || f.puesto().isBlank()) continue;
            if (f.personas() < 1 || f.personas() > 5) throw new ValidacionException("Un fijo lleva de 1 a 5 personas.");
            String abre = null;
            if (f.abre() != null && !f.abre().isBlank()) {
                java.time.LocalTime h = ImportadorExcelService.parsearHora(f.abre());
                if (h == null) throw new ValidacionException("\"" + f.abre() + "\" no es un horario (" + f.puesto().trim() + ").");
                abre = RotacionService.formatear(h);
            }
            fijos.add(nombre(f.puesto()) + "|" + f.personas() + (abre == null ? "" : "|" + abre));
        }
        List<String> lugares = new ArrayList<>();
        for (List<String> l : dto.lugares() == null ? List.<List<String>>of() : dto.lugares()) {
            List<String> nombres = l == null ? List.of() : l.stream().filter(x -> x != null && !x.isBlank()).map(this::nombre).toList();
            if (!nombres.isEmpty()) lugares.add(String.join("+", nombres));
        }
        if (fijos.isEmpty() && lugares.isEmpty()) throw new ValidacionException("La regla no tiene ningún puesto.");

        List<String> horarios = new ArrayList<>();
        for (ReglaAperturaDto.Horario h : dto.horarios() == null ? List.<ReglaAperturaDto.Horario>of() : dto.horarios()) {
            if (h == null || h.puesto() == null || h.puesto().isBlank()) continue;
            String puesto = nombre(h.puesto());
            if (h.personas() < 1 || h.personas() > 10) throw new ValidacionException(puesto + ": las personas van de 1 a 10.");
            java.time.LocalTime desde = h.desde() == null ? null : ImportadorExcelService.parsearHora(h.desde());
            if (desde == null) throw new ValidacionException(puesto + ": falta desde qué hora.");
            java.time.LocalTime hasta = h.hasta() == null || h.hasta().isBlank() ? null : ImportadorExcelService.parsearHora(h.hasta());
            if (h.hasta() != null && !h.hasta().isBlank() && hasta == null) throw new ValidacionException(puesto + ": \"" + h.hasta() + "\" no es un horario.");
            if (hasta != null && !hasta.isAfter(desde)) throw new ValidacionException(puesto + ": tiene que terminar después de empezar.");
            TipoHorario tipo = h.tipo() == null ? TipoHorario.ABRE_TARDE : h.tipo();
            horarios.add(puesto + "|" + tipo.name() + "|" + h.personas() + "|" + RotacionService.formatear(desde) + "|"
                    + (hasta == null ? "" : RotacionService.formatear(hasta)));
        }

        r.setEmpleados(dto.empleados());
        r.setAfluencia(dto.afluencia());
        r.getFijos().clear();
        r.getFijos().addAll(fijos);
        r.getLugares().clear();
        r.getLugares().addAll(lugares);
        r.getHorarios().clear();
        r.getHorarios().addAll(horarios);
        return aDto(repositorio.save(r));
    }

    @Transactional
    public void eliminar(Long id) {
        repositorio.delete(repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Esa regla no existe.")));
    }

    /**
     * Lee la tabla "Puestos abiertos" (Empleados, Afluencia, Puestos abiertos, Puestos compartidos) y
     * reemplaza las reglas. Poca gente es afluencia baja, mucha es alta; la media empieza igual que la baja.
     */
    @Transactional
    public Importacion importar(MultipartFile archivo) {
        List<ReglaAperturaDto> leidas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        List<String> circuito = circuito();
        try (InputStream entrada = archivo.getInputStream(); Workbook libro = WorkbookFactory.create(entrada)) {
            DataFormatter formato = new DataFormatter(Locale.forLanguageTag("es-AR"));
            FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();
            for (Sheet hoja : libro) leerHoja(hoja, formato, evaluador, leidas, avisos, circuito);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ValidacionException v) throw v;
            throw new ValidacionException("No se pudo leer el archivo. Tiene que ser un Excel con la tabla de puestos abiertos.");
        }
        if (leidas.isEmpty()) {
            throw new ValidacionException("No se encontró la tabla: hace falta una fila con Empleados, Afluencia y Puestos abiertos.");
        }
        // Lo que no viene en la tabla (la Tirolesa con su hora, los horarios del día) se copia de la regla de antes más
        // parecida: misma afluencia y la cantidad más cercana.
        List<ReglaAperturaDto> viejas = repositorio.findAll().stream().map(ReglaAperturaService::aDto).toList();
        repositorio.deleteAll();
        repositorio.flush();
        Map<Integer, ReglaAperturaDto> bajas = new HashMap<>();
        Set<Integer> medias = new java.util.HashSet<>();
        // La tabla del parque cuenta sin la Tirolesa: se le suma su gente (según la afluencia) para que la regla cuente
        // a todos.
        for (ReglaAperturaDto r : leidas) {
            guardar(conTardes(r, viejas));
            if (r.afluencia() == Afluencia.BAJA) bajas.put(r.empleados(), r);
            if (r.afluencia() == Afluencia.MEDIA) medias.add(r.empleados());
        }
        int total = leidas.size();
        for (ReglaAperturaDto b : bajas.values()) {
            if (medias.contains(b.empleados())) continue;
            guardar(conTardes(new ReglaAperturaDto(null, b.empleados(), Afluencia.MEDIA, b.fijos(), b.lugares()), viejas));
            total++;
        }
        return new Importacion(total, avisos);
    }

    /**
     * Una regla de la tabla (que cuenta sin la Tirolesa) con la gente de los puestos que abren tarde y siguen hasta
     * el cierre, desde temprano (la Tirolesa, desde las 13): se agregan como fijos con las personas de esa afluencia
     * y se suman a los empleados.
     */
    private ReglaAperturaDto conTardes(ReglaAperturaDto r, List<ReglaAperturaDto> viejas) {
        List<PedidoGeneradorDto.Fijo> fijos = new ArrayList<>(r.fijos());
        // Con reglas de antes: de la más parecida, los fijos que abren tarde y los horarios del día.
        ReglaAperturaDto parecida = viejas.stream().filter(v -> v.afluencia() == r.afluencia())
                .min(Comparator.comparingInt(v -> Math.abs((v.empleados() - v.fijos().stream().filter(f -> f.abre() != null)
                        .mapToInt(PedidoGeneradorDto.Fijo::personas).sum()) - r.empleados()))).orElse(null);
        if (parecida != null) {
            int suma = 0;
            for (PedidoGeneradorDto.Fijo f : parecida.fijos()) {
                if (f.abre() == null || fijos.stream().anyMatch(x -> Nombres.clave(x.puesto()).equals(Nombres.clave(f.puesto())))) continue;
                fijos.add(f);
                suma += f.personas();
            }
            Set<String> todoElDia = new java.util.HashSet<>();
            r.lugares().forEach(l -> l.forEach(p -> todoElDia.add(Nombres.clave(p))));
            List<ReglaAperturaDto.Horario> horarios = parecida.horarios().stream()
                    .filter(h -> !(h.tipo() == TipoHorario.ABRE_TARDE && todoElDia.contains(Nombres.clave(h.puesto())))).toList();
            return new ReglaAperturaDto(r.id(), r.empleados() + suma, r.afluencia(), fijos, r.lugares(), horarios);
        }
        int suma = 0;
        for (PuestoParque p : puestos.findAll()) {
            if (p.getAbre() == null || p.getTipo() != org.example.laserranitarotaciones.model.entity.TipoHorario.ABRE_TARDE
                    || p.getHasta() != null || p.getAbre().compareTo("14:00") > 0) continue;
            if (fijos.stream().anyMatch(f -> Nombres.clave(f.puesto()).equals(p.getClave()))) continue;
            Integer n = r.afluencia() == Afluencia.BAJA ? p.getPersonasBaja() : r.afluencia() == Afluencia.ALTA ? p.getPersonasAlta() : p.getPersonasMedia();
            int personas = n == null ? 1 : n;
            fijos.add(new PedidoGeneradorDto.Fijo(p.getNombre(), personas, p.getAbre()));
            suma += personas;
        }
        return new ReglaAperturaDto(r.id(), r.empleados() + suma, r.afluencia(), fijos, r.lugares());
    }

    private void leerHoja(Sheet hoja, DataFormatter formato, FormulaEvaluator evaluador,
                          List<ReglaAperturaDto> leidas, List<String> avisos, List<String> circuito) {
        int cEmpleados = -1, cAfluencia = -1, cAbiertos = -1, cCompartidos = -1;
        for (Row fila : hoja) {
            if (cEmpleados < 0) {
                for (Cell c : fila) {
                    String t = Nombres.clave(formato.formatCellValue(c, evaluador));
                    if (t.equals("empleados")) cEmpleados = c.getColumnIndex();
                    else if (t.equals("afluencia")) cAfluencia = c.getColumnIndex();
                    else if (t.startsWith("puestos abiertos")) cAbiertos = c.getColumnIndex();
                    else if (t.startsWith("puestos compartidos")) cCompartidos = c.getColumnIndex();
                }
                if (cAfluencia < 0 || cAbiertos < 0) cEmpleados = -1;
                continue;
            }
            String empleadosTexto = texto(fila, cEmpleados, formato, evaluador);
            if (!empleadosTexto.matches("\\d+")) continue;
            int empleados = Integer.parseInt(empleadosTexto);
            String afluenciaTexto = Nombres.clave(texto(fila, cAfluencia, formato, evaluador));
            Afluencia afluencia = afluenciaTexto.contains("poca") ? Afluencia.BAJA
                    : afluenciaTexto.contains("mucha") ? Afluencia.ALTA
                    : afluenciaTexto.contains("media") ? Afluencia.MEDIA : null;
            if (afluencia == null) {
                avisos.add("Fila de " + empleados + " empleados: no se entiende la afluencia \"" + afluenciaTexto + "\".");
                continue;
            }
            List<String> abiertos = new ArrayList<>();
            for (String p : texto(fila, cAbiertos, formato, evaluador).split("[·,]")) if (!p.isBlank()) abiertos.add(nombre(p));
            String compartidos = cCompartidos < 0 ? "" : Nombres.clave(texto(fila, cCompartidos, formato, evaluador));
            ReglaAperturaDto regla = armar(empleados, afluencia, abiertos, compartidos, circuito);
            int suma = regla.fijos().stream().mapToInt(PedidoGeneradorDto.Fijo::personas).sum() + regla.lugares().size();
            if (suma != empleados) {
                avisos.add("Con " + empleados + " empleados (" + afluencia.name().toLowerCase(Locale.ROOT) + ") la regla ocupa "
                        + suma + " personas.");
            }
            leidas.add(regla);
        }
    }

    /**
     * Fijos (Cajero, Casa del zorrito) y la rueda, juntando lo que comparte una misma persona. La rueda arranca en
     * el último puesto de la lista (el último que se agregó) y sigue el recorrido de las planillas.
     */
    private static ReglaAperturaDto armar(int empleados, Afluencia afluencia, List<String> abiertos, String compartidos,
                                          List<String> circuito) {
        boolean dosCajeras = compartidos.contains("2 empleados en caj");
        boolean comparteTres = compartidos.contains("comparte");
        boolean colaboran = compartidos.contains("colaboran");
        String travesia = Nombres.clave("Travesía"), busqueda = Nombres.clave("Búsqueda"), laberinto = Nombres.clave("Laberinto");
        Map<String, String> porClave = new HashMap<>();
        abiertos.forEach(p -> porClave.put(Nombres.clave(p), p));

        Set<String> sonFijos = new java.util.HashSet<>(FIJOS);
        if (empleados >= BANOS_FIJO_DESDE) sonFijos.add(BANOS);
        List<PedidoGeneradorDto.Fijo> fijos = new ArrayList<>();
        List<List<String>> lugares = new ArrayList<>();
        boolean tresAgregado = false;
        for (String p : abiertos) {
            String clave = Nombres.clave(p);
            if (sonFijos.contains(clave)) {
                fijos.add(new PedidoGeneradorDto.Fijo(p, clave.equals(Nombres.clave("Cajero")) && dosCajeras ? 2 : 1));
                continue;
            }
            boolean deLosTres = clave.equals(travesia) || clave.equals(busqueda) || clave.equals(laberinto);
            if (comparteTres && deLosTres) {
                if (!tresAgregado) {
                    lugares.add(List.of(porClave.getOrDefault(travesia, "Travesía"), porClave.getOrDefault(busqueda, "Búsqueda"),
                            porClave.getOrDefault(laberinto, "Laberinto")));
                    tresAgregado = true;
                }
                continue;
            }
            if (colaboran && clave.equals(busqueda)) continue;
            if (colaboran && (clave.equals(travesia) || clave.equals(laberinto))) {
                lugares.add(List.of(p, porClave.getOrDefault(busqueda, "Búsqueda")));
                continue;
            }
            lugares.add(List.of(p));
        }
        String ultimo = abiertos.stream().map(Nombres::clave).filter(c -> !sonFijos.contains(c)).reduce((a, b) -> b).orElse(null);
        lugares.sort(Comparator.comparingInt(l -> l.stream().anyMatch(x -> Nombres.clave(x).equals(ultimo)) ? -1 : enCircuito(l, circuito)));
        return new ReglaAperturaDto(null, empleados, afluencia, fijos, lugares);
    }

    /** El nombre como está en la lista de puestos (Cajera → Cajero); si es nuevo, se agrega a la lista. */
    private String nombre(String crudo) {
        String t = crudo.trim().replaceAll("\\s+", " ");
        String clave = Nombres.clave(t);
        if (SINONIMOS.containsKey(clave)) {
            t = SINONIMOS.get(clave);
            clave = Nombres.clave(t);
        }
        var existente = puestos.findByClave(clave);
        if (existente.isPresent()) return existente.get().getNombre();
        String nombre = t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
        PuestoParque p = new PuestoParque();
        p.setNombre(nombre);
        p.setClave(clave);
        p.setPrioridad(Prioridad.MEDIA);
        puestos.save(p);
        return nombre;
    }

    private static String texto(Row fila, int columna, DataFormatter formato, FormulaEvaluator evaluador) {
        if (columna < 0) return "";
        Cell c = fila.getCell(columna);
        return c == null ? "" : formato.formatCellValue(c, evaluador).trim();
    }

    static ReglaAperturaDto aDto(ReglaApertura r) {
        List<PedidoGeneradorDto.Fijo> fijos = r.getFijos().stream().map(f -> {
            String[] partes = f.split("\\|");
            return new PedidoGeneradorDto.Fijo(partes[0], partes.length > 1 ? Integer.parseInt(partes[1]) : 1,
                    partes.length > 2 && !partes[2].isBlank() ? partes[2] : null);
        }).toList();
        List<List<String>> lugares = r.getLugares().stream().map(l -> List.of(l.split("\\+"))).toList();
        List<ReglaAperturaDto.Horario> horarios = r.getHorarios().stream().map(h -> {
            String[] p = h.split("\\|", -1);
            return new ReglaAperturaDto.Horario(p[0], TipoHorario.valueOf(p[1]), Integer.parseInt(p[2]), p[3],
                    p.length > 4 && !p[4].isBlank() ? p[4] : null);
        }).toList();
        return new ReglaAperturaDto(r.getId(), r.getEmpleados(), r.getAfluencia(), fijos, lugares, horarios);
    }
}
