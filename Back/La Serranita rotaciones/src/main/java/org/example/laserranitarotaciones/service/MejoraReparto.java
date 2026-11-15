package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.entity.Prioridad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Después de repartir los números entre los que vinieron: en cada horario, si dos personas quedan mejor
 * cambiándose de lugar (la cajera que sabe Fotos a Fotos, y el de Fotos que no sabe a Minigolf, que sí
 * sabe), se cambian. Sólo cuando mejora: si los dos ya están en lo que saben, la rueda queda como está.
 * También con los que están en Libre: si uno libre sabe el puesto, lo toma y el otro queda libre.
 */
final class MejoraReparto {

    private MejoraReparto() {
    }

    /** porNumero: número del cronograma → quién lo hace. pesos: clave del puesto → peso de su prioridad. */
    static List<PuestoDto> mejorar(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                   Map<String, Integer> pesos) {
        int total = puestos.isEmpty() ? 0 : puestos.get(0).celdas().size();
        List<List<Set<Integer>>> filas = new ArrayList<>();
        for (PuestoDto p : puestos) {
            List<Set<Integer>> celdas = new ArrayList<>();
            for (String c : p.celdas()) celdas.add(new LinkedHashSet<>(PlanRotacion.empleados(c)));
            filas.add(celdas);
        }
        boolean cambio = false;
        for (int k = 0; k < total; k++) {
            for (int vuelta = 0; vuelta < 3; vuelta++) {
                // En qué filas está cada número en este horario (sin Descanso; Libre sí, no cuesta nada).
                Map<Integer, List<Integer>> donde = new LinkedHashMap<>();
                for (int f = 0; f < puestos.size(); f++) {
                    if (Nombres.esDescanso(puestos.get(f).nombre())) continue;
                    for (int n : filas.get(f).get(k)) donde.computeIfAbsent(n, x -> new ArrayList<>()).add(f);
                }
                List<Integer> numeros = new ArrayList<>(donde.keySet());
                boolean esta = false;
                for (int i = 0; i < numeros.size() && !esta; i++) {
                    for (int j = i + 1; j < numeros.size() && !esta; j++) {
                        int a = numeros.get(i), b = numeros.get(j);
                        AsignadorService.Presente pa = porNumero.get(a), pb = porNumero.get(b);
                        if (pa == null || pb == null) continue;
                        List<Integer> fa = donde.get(a), fb = donde.get(b);
                        if (fa.equals(fb)) continue;
                        int antes = costo(pa, fa, puestos, pesos) + costo(pb, fb, puestos, pesos);
                        int despues = costo(pa, fb, puestos, pesos) + costo(pb, fa, puestos, pesos);
                        if (despues >= antes) continue;
                        for (int f : fa) {
                            filas.get(f).get(k).remove(a);
                            filas.get(f).get(k).add(b);
                        }
                        for (int f : fb) {
                            filas.get(f).get(k).remove(b);
                            filas.get(f).get(k).add(a);
                        }
                        esta = true;
                        cambio = true;
                    }
                }
                if (!esta) break;
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < total; k++) {
                Set<Integer> s = filas.get(f).get(k);
                celdas.add(s.isEmpty() ? p.celdas().get(k)
                        : s.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
            }
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas));
        }
        return nuevos;
    }

    /**
     * Las estrellas mandan: en cada horario, si un puesto lo hace alguien que no lo tiene como principal y hay otro
     * trabajando (o en Libre) que sí lo tiene y no está en uno de sus principales, se cambian. Así nunca va otro a
     * un puesto que podría hacer el de la estrella (salvo mientras él descansa).
     */
    static List<PuestoDto> respetarEstrellas(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero) {
        int total = puestos.isEmpty() ? 0 : puestos.get(0).celdas().size();
        List<List<Set<Integer>>> filas = new ArrayList<>();
        for (PuestoDto p : puestos) {
            List<Set<Integer>> celdas = new ArrayList<>();
            for (String c : p.celdas()) celdas.add(new LinkedHashSet<>(PlanRotacion.empleados(c)));
            filas.add(celdas);
        }
        boolean cambio = false;
        for (int k = 0; k < total; k++) {
            for (int vuelta = 0; vuelta < puestos.size(); vuelta++) {
                Map<Integer, List<Integer>> donde = new LinkedHashMap<>();
                for (int f = 0; f < puestos.size(); f++) {
                    if (Nombres.esDescanso(puestos.get(f).nombre())) continue;
                    for (int n : filas.get(f).get(k)) donde.computeIfAbsent(n, x -> new ArrayList<>()).add(f);
                }
                boolean esta = false;
                for (int f = 0; f < puestos.size() && !esta; f++) {
                    String clave = Nombres.clave(puestos.get(f).nombre());
                    if (Nombres.noEsPuesto(puestos.get(f).nombre())) continue;
                    for (int n : new ArrayList<>(filas.get(f).get(k))) {
                        AsignadorService.Presente quien = porNumero.get(n);
                        if (quien == null || quien.principales().contains(clave)) continue;
                        // Alguien con estrella en este puesto que esté en otra cosa (no en otra de sus estrellas).
                        Integer estrella = null;
                        for (Map.Entry<Integer, List<Integer>> d : donde.entrySet()) {
                            AsignadorService.Presente otro = porNumero.get(d.getKey());
                            if (otro == null || d.getKey() == n || !otro.principales().contains(clave) || !otro.hoy().contains(clave)) continue;
                            boolean enSuEstrella = d.getValue().stream()
                                    .anyMatch(g -> otro.principales().contains(Nombres.clave(puestos.get(g).nombre())));
                            if (!enSuEstrella) {
                                estrella = d.getKey();
                                break;
                            }
                        }
                        if (estrella == null) continue;
                        List<Integer> fa = donde.get(n), fb = donde.get(estrella);
                        for (int g : fa) {
                            filas.get(g).get(k).remove(n);
                            filas.get(g).get(k).add(estrella);
                        }
                        for (int g : fb) {
                            filas.get(g).get(k).remove(estrella);
                            filas.get(g).get(k).add(n);
                        }
                        esta = true;
                        cambio = true;
                        break;
                    }
                }
                if (!esta) break;
            }
            // El de la estrella que quedó en algo que no sabe, con su puesto vacío o cerrado (el almuerzo cerró
            // Baños): vuelve a su puesto.
            for (int n = 1; n <= porNumero.keySet().stream().mapToInt(Integer::intValue).max().orElse(0); n++) {
                AsignadorService.Presente quien = porNumero.get(n);
                if (quien == null || quien.principales().isEmpty()) continue;
                List<Integer> esta = new ArrayList<>();
                for (int f = 0; f < puestos.size(); f++) if (filas.get(f).get(k).contains(n)) esta.add(f);
                if (esta.isEmpty() || esta.stream().anyMatch(f -> Nombres.esDescanso(puestos.get(f).nombre()))) continue;
                boolean noSabe = esta.stream().anyMatch(f -> !Nombres.noEsPuesto(puestos.get(f).nombre())
                        && !quien.hoy().contains(Nombres.clave(puestos.get(f).nombre())));
                if (!noSabe) continue;
                int kk = k;
                Integer suya = null;
                for (int f = 0; f < puestos.size(); f++) {
                    String c = Nombres.clave(puestos.get(f).nombre());
                    if (quien.principales().contains(c) && quien.hoy().contains(c) && filas.get(f).get(kk).isEmpty()) suya = f;
                }
                if (suya == null) continue;
                for (int f : esta) filas.get(f).get(k).remove(n);
                filas.get(suya).get(k).add(n);
                cambio = true;
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < total; k++) {
                Set<Integer> s = filas.get(f).get(k);
                String antes = p.celdas().get(k);
                celdas.add(s.isEmpty() ? (PlanRotacion.empleados(antes).isEmpty() ? antes : "")
                        : s.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
            }
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas));
        }
        return nuevos;
    }

    /**
     * Un puesto (de prioridad media o más) que quedó sin nadie en un horario lo cubre alguien que sobra: uno de
     * Libre o de un puesto donde están de a varios (la segunda cajera). Si ese no lo sabe, puede ir a donde está
     * otro que sí lo sabe, y ese pasa al hueco (la cajera a la Casa del zorrito, el de la rueda a Laberinto).
     */
    static List<PuestoDto> cubrirHuecos(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                        Map<String, Integer> pesos) {
        return cubrirHuecos(puestos, porNumero, pesos, Set.of());
    }

    /**
     * noAbrir: claves de los puestos que abren tarde (Tirolesa, Fotos): cerrados hasta su hora, nadie los abre.
     * Un puesto cerrado (por falta de gente o en el almuerzo) o de prioridad baja sólo lo abre alguien de Libre.
     */
    static List<PuestoDto> cubrirHuecos(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                        Map<String, Integer> pesos, Set<String> noAbrir) {
        int total = puestos.isEmpty() ? 0 : puestos.get(0).celdas().size();
        List<List<Set<Integer>>> filas = new ArrayList<>();
        for (PuestoDto p : puestos) {
            List<Set<Integer>> celdas = new ArrayList<>();
            for (String c : p.celdas()) celdas.add(new LinkedHashSet<>(PlanRotacion.empleados(c)));
            filas.add(celdas);
        }
        java.util.function.BiPredicate<Integer, Integer> sabe = (n, f) -> {
            AsignadorService.Presente q = porNumero.get(n);
            return q != null && q.hoy().contains(Nombres.clave(puestos.get(f).nombre()));
        };
        boolean cambio = false;
        Map<Integer, Set<Integer>> libresExtra = new TreeMap<>();
        for (int k = 0; k < total; k++) {
            int kk = k;
            for (int hueco = 0; hueco < puestos.size(); hueco++) {
                PuestoDto h = puestos.get(hueco);
                if (Nombres.noEsPuesto(h.nombre()) || noAbrir.contains(Nombres.clave(h.nombre()))) continue;
                if (!filas.get(hueco).get(k).isEmpty()) continue;
                boolean cerrado = PlanRotacion.CERRADO.equalsIgnoreCase(h.celdas().get(k).trim());
                boolean baja = Objects.requireNonNullElse(pesos.get(Nombres.clave(h.nombre())), Prioridad.MEDIA.peso()) <= Prioridad.BAJA.peso();
                // Los que sobran: en Libre, o en una celda con otros (que no quede sola por sacarlo). Para abrir
                // uno cerrado o de prioridad baja, sólo los de Libre (no se deja una sola cajera por Flotante).
                List<int[]> sobran = sobran(filas, puestos, k).stream()
                        .filter(s -> !(cerrado || baja) || Nombres.esLibre(puestos.get(s[1]).nombre())).toList();
                boolean cubierto = false;
                for (int[] s : sobran) {
                    if (!sabe.test(s[0], hueco)) continue;
                    filas.get(s[1]).get(k).remove(s[0]);
                    filas.get(hueco).get(k).add(s[0]);
                    cubierto = true;
                    break;
                }
                for (int i = 0; i < sobran.size() && !cubierto; i++) {
                    int[] s = sobran.get(i);
                    for (int f = 0; f < puestos.size() && !cubierto; f++) {
                        if (f == s[1] || Nombres.noEsPuesto(puestos.get(f).nombre()) || filas.get(f).get(kk).size() != 1) continue;
                        int otro = filas.get(f).get(kk).iterator().next();
                        if (!enUnaSolaFila(filas, otro, kk) || !sabe.test(s[0], f) || !sabe.test(otro, hueco)) continue;
                        AsignadorService.Presente q = porNumero.get(otro);
                        if (q != null && q.principales().contains(Nombres.clave(puestos.get(f).nombre()))) continue;
                        filas.get(s[1]).get(kk).remove(s[0]);
                        filas.get(f).get(kk).remove(otro);
                        filas.get(f).get(kk).add(s[0]);
                        filas.get(hueco).get(kk).add(otro);
                        cubierto = true;
                    }
                }
                cambio |= cubierto;
            }
            // Alguien en un puesto que no sabe, y sobra uno que sí lo sabe (la segunda cajera mientras almuerza
            // la de la Casa del zorrito): ese entra, y el otro va a un hueco que sepa o a Libre.
            for (int f = 0; f < puestos.size(); f++) {
                PuestoDto p = puestos.get(f);
                if (Nombres.noEsPuesto(p.nombre())) continue;
                for (int n : new ArrayList<>(filas.get(f).get(kk))) {
                    if (sabe.test(n, f) || !enUnaSolaFila(filas, n, kk)) continue;
                    int fila = f;
                    int[] donante = sobran(filas, puestos, kk).stream()
                            .filter(s -> s[1] != fila && sabe.test(s[0], fila) && !dejaSinEstrella(filas, puestos, porNumero, s, kk))
                            .findFirst().orElse(null);
                    if (donante == null) continue;
                    filas.get(donante[1]).get(kk).remove(donante[0]);
                    filas.get(f).get(kk).remove(n);
                    filas.get(f).get(kk).add(donante[0]);
                    Integer destino = null;
                    for (int g = 0; g < puestos.size() && destino == null; g++) {
                        PuestoDto q = puestos.get(g);
                        if (Nombres.noEsPuesto(q.nombre()) || PlanRotacion.CERRADO.equalsIgnoreCase(q.celdas().get(kk).trim())) continue;
                        if (filas.get(g).get(kk).isEmpty() && sabe.test(n, g)) destino = g;
                    }
                    if (destino != null) filas.get(destino).get(kk).add(n);
                    else libresExtra.computeIfAbsent(kk, x -> new TreeSet<>()).add(n);
                    cambio = true;
                }
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        int filaLibre = -1;
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            if (Nombres.esLibre(p.nombre())) {
                filaLibre = f;
                for (Map.Entry<Integer, Set<Integer>> e : libresExtra.entrySet()) filas.get(f).get(e.getKey()).addAll(e.getValue());
            }
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < total; k++) {
                Set<Integer> s = filas.get(f).get(k);
                String antes = p.celdas().get(k);
                celdas.add(s.isEmpty() ? (PlanRotacion.empleados(antes).isEmpty() ? antes : "")
                        : s.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
            }
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas));
        }
        if (filaLibre < 0 && !libresExtra.isEmpty()) {
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < total; k++) {
                celdas.add(libresExtra.getOrDefault(k, Set.of()).stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
            }
            int descanso = 0;
            while (descanso < nuevos.size() && !Nombres.esDescanso(nuevos.get(descanso).nombre())) descanso++;
            nuevos.add(descanso, new PuestoDto(Nombres.LIBRE, null, false, celdas));
        }
        return nuevos;
    }

    /** Los que sobran en ese horario: en Libre, o en una celda con otros. {número, fila}. */
    private static List<int[]> sobran(List<List<Set<Integer>>> filas, List<PuestoDto> puestos, int k) {
        List<int[]> sobran = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            if (Nombres.esDescanso(puestos.get(f).nombre())) continue;
            Set<Integer> celda = filas.get(f).get(k);
            if (!Nombres.esLibre(puestos.get(f).nombre()) && celda.size() < 2) continue;
            for (int n : celda) if (enUnaSolaFila(filas, n, k)) sobran.add(new int[]{n, f});
        }
        return sobran;
    }

    /** Si sacar a ese de su celda la deja sin nadie que tenga estrella ahí (y él la tiene). */
    private static boolean dejaSinEstrella(List<List<Set<Integer>>> filas, List<PuestoDto> puestos,
                                           Map<Integer, AsignadorService.Presente> porNumero, int[] s, int k) {
        String clave = Nombres.clave(puestos.get(s[1]).nombre());
        AsignadorService.Presente el = porNumero.get(s[0]);
        if (el == null || !el.principales().contains(clave)) return false;
        return filas.get(s[1]).get(k).stream().filter(o -> o != s[0])
                .noneMatch(o -> porNumero.get(o) != null && porNumero.get(o).principales().contains(clave));
    }

    /** Si el número está en una sola fila en ese horario (no hace dos puestos a la vez). */
    private static boolean enUnaSolaFila(List<List<Set<Integer>>> filas, int n, int k) {
        return filas.stream().filter(f -> f.get(k).contains(n)).count() == 1;
    }

    /** Cómo quedó después de cerrar: las filas y, por puesto, en qué horarios se cerró. */
    record Cierre(List<PuestoDto> puestos, Map<String, Set<Integer>> cerrados) {
    }

    /**
     * Un puesto de prioridad baja (Flotante) no se le da a alguien que no lo sabe o que hoy no puede: esa
     * persona pasa a Libre y, si no queda nadie, el puesto queda Cerrado en ese horario.
     */
    static Cierre cerrarBajos(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                              Map<String, Integer> pesos) {
        int total = puestos.isEmpty() ? 0 : puestos.get(0).celdas().size();
        List<PuestoDto> nuevos = new ArrayList<>(puestos);
        Map<String, Set<Integer>> cerrados = new TreeMap<>();
        List<Set<Integer>> libres = new ArrayList<>();
        int filaLibre = -1;
        for (int f = 0; f < puestos.size(); f++) if (Nombres.esLibre(puestos.get(f).nombre())) filaLibre = f;
        for (int k = 0; k < total; k++) {
            libres.add(new TreeSet<>(filaLibre < 0 ? List.of() : PlanRotacion.empleados(puestos.get(filaLibre).celdas().get(k))));
        }
        boolean cambio = false;
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            String clave = Nombres.clave(p.nombre());
            if (Nombres.noEsPuesto(p.nombre()) || Objects.requireNonNullElse(pesos.get(clave), Prioridad.MEDIA.peso()) > Prioridad.BAJA.peso()) continue;
            List<String> celdas = new ArrayList<>(p.celdas());
            for (int k = 0; k < total; k++) {
                List<Integer> nums = PlanRotacion.empleados(celdas.get(k));
                List<Integer> quedan = new ArrayList<>();
                for (int n : nums) {
                    AsignadorService.Presente quien = porNumero.get(n);
                    if (quien != null && !quien.hoy().contains(clave)) libres.get(k).add(n);
                    else quedan.add(n);
                }
                if (quedan.size() == nums.size()) continue;
                cambio = true;
                if (quedan.isEmpty()) {
                    celdas.set(k, PlanRotacion.CERRADO);
                    cerrados.computeIfAbsent(p.nombre(), x -> new TreeSet<>()).add(k);
                } else {
                    celdas.set(k, quedan.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
                }
            }
            nuevos.set(f, new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas));
        }
        if (!cambio) return new Cierre(puestos, cerrados);
        List<String> textoLibre = libres.stream().map(s -> s.stream().map(String::valueOf).collect(Collectors.joining("-"))).toList();
        PuestoDto libre = new PuestoDto(Nombres.LIBRE, null, false, textoLibre);
        if (filaLibre >= 0) {
            nuevos.set(filaLibre, libre);
        } else {
            int descanso = 0;
            while (descanso < nuevos.size() && !Nombres.esDescanso(nuevos.get(descanso).nombre())) descanso++;
            nuevos.add(descanso, libre);
        }
        return new Cierre(nuevos, cerrados);
    }

    /** Lo que cuesta que esa persona haga esas filas: el peso de cada puesto que no sabe o hoy no puede. */
    private static int costo(AsignadorService.Presente p, List<Integer> filas, List<PuestoDto> puestos, Map<String, Integer> pesos) {
        int c = 0;
        for (int f : filas) {
            if (Nombres.noEsPuesto(puestos.get(f).nombre())) continue;
            String clave = Nombres.clave(puestos.get(f).nombre());
            if (!p.hoy().contains(clave)) c += Objects.requireNonNullElse(pesos.get(clave), Prioridad.MEDIA.peso());
        }
        return c;
    }
}
