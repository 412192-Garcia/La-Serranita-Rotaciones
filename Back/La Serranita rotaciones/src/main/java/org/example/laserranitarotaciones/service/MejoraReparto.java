package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.entity.Prioridad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        return respetarEstrellas(puestos, porNumero, Map.of());
    }

    /** pesos: clave del puesto → peso de su prioridad (lo que no está es Media). */
    static List<PuestoDto> respetarEstrellas(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
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
            // Baños): vuelve a su puesto. Pero no si deja sin nadie un puesto más importante que el suyo (la Casa
            // del zorrito no queda vacía para abrir Baños).
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
                int pesoSuya = pesos.getOrDefault(Nombres.clave(puestos.get(suya).nombre()), Prioridad.MEDIA.peso());
                boolean dejaVacioOtroMasImportante = esta.stream().anyMatch(f -> !Nombres.noEsPuesto(puestos.get(f).nombre())
                        && filas.get(f).get(kk).size() == 1
                        && pesos.getOrDefault(Nombres.clave(puestos.get(f).nombre()), Prioridad.MEDIA.peso()) > pesoSuya);
                if (dejaVacioOtroMasImportante) continue;
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
     * Un puesto que tiene que estar siempre (Cajero) nunca lo hace alguien que no lo sabe. En ese horario entra uno
     * que lo sepa: de Libre; si no, de otro puesto que no quede sin nadie (se cambian si el que no sabe Cajero sabe
     * ese puesto, o ahí queda otro y el que no sabe pasa a Libre). Si no se puede, el que no sabe pasa a Libre y el
     * Cajero queda sin nadie: se pide un refuerzo.
     */
    static List<PuestoDto> siempreConQuienSabe(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                               Map<String, Integer> pesos) {
        return siempreConQuienSabe(puestos, porNumero, pesos, Set.of());
    }

    /**
     * Igual, y también para los puestos de tambien (claves): los que tienen roles, como el Show del zorrito, no
     * los hace alguien que no los sabe; antes, refuerzo.
     */
    static List<PuestoDto> siempreConQuienSabe(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                               Map<String, Integer> pesos, Set<String> tambien) {
        int libre = -1;
        for (int f = 0; f < puestos.size(); f++) if (Nombres.esLibre(puestos.get(f).nombre())) libre = f;
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
        java.util.function.IntPredicate esSiempre = f -> !Nombres.noEsPuesto(puestos.get(f).nombre())
                && (pesos.getOrDefault(Nombres.clave(puestos.get(f).nombre()), Prioridad.MEDIA.peso()) >= Prioridad.SIEMPRE.peso()
                || tambien.contains(Nombres.clave(puestos.get(f).nombre())));
        boolean cambio = false;
        for (int f = 0; f < puestos.size(); f++) {
            if (!esSiempre.test(f)) continue;
            for (int k = 0; k < filas.get(f).size(); k++) {
                for (int x : new ArrayList<>(filas.get(f).get(k))) {
                    if (porNumero.get(x) == null || sabe.test(x, f)) continue;
                    // Uno de Libre que lo sepa.
                    int desde = -1, y = -1;
                    if (libre >= 0) {
                        for (int n : filas.get(libre).get(k)) if (sabe.test(n, f)) { desde = libre; y = n; break; }
                    }
                    // Si no, uno de otro puesto, siempre que ese puesto no quede sin nadie: o el que no sabe Cajero
                    // sabe ese puesto y se cambian, o ahí queda otro.
                    boolean xVaAlOtro = false;
                    for (int pase = 0; pase < 2 && y < 0; pase++) {
                        for (int g = 0; g < puestos.size() && y < 0; g++) {
                            if (g == f || g == libre || Nombres.noEsPuesto(puestos.get(g).nombre()) || esSiempre.test(g)) continue;
                            Set<Integer> ahi = filas.get(g).get(k);
                            if (pase == 0 ? !sabe.test(x, g) : ahi.size() < 2) continue;
                            for (int n : ahi) {
                                if (sabe.test(n, f) && !filas.get(f).get(k).contains(n)) {
                                    desde = g;
                                    y = n;
                                    xVaAlOtro = pase == 0;
                                    break;
                                }
                            }
                        }
                    }
                    filas.get(f).get(k).remove(x);
                    if (y >= 0) {
                        filas.get(desde).get(k).remove(y);
                        filas.get(f).get(k).add(y);
                    }
                    if (xVaAlOtro) filas.get(desde).get(k).add(x);
                    else if (libre >= 0) filas.get(libre).get(k).add(x);
                    cambio = true;
                }
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < p.celdas().size(); k++) {
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
     * En el almuerzo, si un puesto lo cubre alguien que no lo sabe mientras come el que sí (la Casa del zorrito con
     * el de Baños), el que sabe almuerza en otro horario de descansos en el que haya alguien en Libre que sepa el
     * puesto y lo cubra (la segunda cajera). El que no sabía pasa a Libre. almuerzo: posiciones de los horarios de
     * descansos.
     */
    static List<PuestoDto> correrAlmuerzos(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                           List<Integer> almuerzo) {
        int descanso = -1, libre = -1;
        for (int f = 0; f < puestos.size(); f++) {
            if (Nombres.esDescanso(puestos.get(f).nombre())) descanso = f;
            if (Nombres.esLibre(puestos.get(f).nombre())) libre = f;
        }
        if (descanso < 0 || libre < 0) return puestos;
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
        for (int k : almuerzo) {
            for (int f = 0; f < puestos.size(); f++) {
                if (Nombres.noEsPuesto(puestos.get(f).nombre()) || k >= filas.get(f).size()) continue;
                for (int x : new ArrayList<>(filas.get(f).get(k))) {
                    if (sabe.test(x, f)) continue;
                    boolean listo = false;
                    // Uno que sabe el puesto y está comiendo ahora.
                    int ff = f;
                    for (int y : new ArrayList<>(filas.get(descanso).get(k))) {
                        if (listo) break;
                        if (!sabe.test(y, f)) continue;
                        // Otro horario de descansos en que él esté en ese mismo puesto y haya en Libre alguien que lo sepa.
                        for (int k2 : almuerzo) {
                            if (k2 == k || !filas.get(f).get(k2).contains(y)) continue;
                            Integer z = filas.get(libre).get(k2).stream().filter(n -> sabe.test(n, ff)).findFirst().orElse(null);
                            if (z == null) continue;
                            filas.get(f).get(k).remove(x);
                            filas.get(libre).get(k).add(x);
                            filas.get(descanso).get(k).remove(y);
                            filas.get(f).get(k).add(y);
                            filas.get(f).get(k2).remove(y);
                            filas.get(descanso).get(k2).add(y);
                            filas.get(libre).get(k2).remove(z);
                            filas.get(f).get(k2).add(z);
                            listo = true;
                            cambio = true;
                            break;
                        }
                    }
                }
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < p.celdas().size(); k++) {
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
        return cubrirHuecos(puestos, porNumero, pesos, noAbrir, Set.of());
    }

    /**
     * conRoles: claves de los puestos con roles (Tirolesa, Show): de ahí no se saca a nadie para cubrir otra cosa.
     * De un puesto con varios sólo se saca para cubrir uno al menos igual de importante (la segunda cajera a la
     * Casa del zorrito sí; a Canilla no: el Cajero queda con los que pide la regla).
     */
    static List<PuestoDto> cubrirHuecos(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                        Map<String, Integer> pesos, Set<String> noAbrir, Set<String> conRoles) {
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
        Map<Integer, Set<Integer>> aCerrar = new HashMap<>();
        Set<String> intocables = new HashSet<>(conRoles);
        intocables.addAll(noAbrir);
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
                List<int[]> sobran = sobran(filas, puestos, k, peso(pesos, puestos.get(hueco)), pesos, conRoles).stream()
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
                // Si no sobra nadie: se cierra un puesto de prioridad baja y ese cubre (Pecera cierra, va a Entrada).
                if (!cubierto && !cerrado && !baja) {
                    cubierto = cubrirCerrandoBajo(filas, puestos, porNumero, pesos, hueco, k, aCerrar, intocables, 1);
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
                    int[] donante = sobran(filas, puestos, kk, peso(pesos, p), pesos, conRoles).stream()
                            .filter(s -> s[1] != fila && sabe.test(s[0], fila) && !dejaSinEstrella(filas, puestos, porNumero, s, kk))
                            .findFirst().orElse(null);
                    if (donante == null) {
                        if (rondaDeTres(filas, puestos, porNumero, sabe, f, n, kk)) {
                            cambio = true;
                            continue;
                        }
                        // Si no, se cierra un puesto de prioridad baja y ese cubre; el que no sabía pasa a Libre.
                        filas.get(f).get(kk).remove(n);
                        if (cubrirCerrandoBajo(filas, puestos, porNumero, pesos, f, kk, aCerrar, intocables, 1)) {
                            libresExtra.computeIfAbsent(kk, x -> new TreeSet<>()).add(n);
                            cambio = true;
                        } else {
                            filas.get(f).get(kk).add(n);
                        }
                        continue;
                    }
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
                if (s.isEmpty() && aCerrar.getOrDefault(f, Set.of()).contains(k)) celdas.add(PlanRotacion.CERRADO);
                else celdas.add(s.isEmpty() ? (PlanRotacion.empleados(antes).isEmpty() ? antes : "")
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

    /**
     * n está en la fila f sin saberla y no sobra nadie que la sepa. Se prueba un cambio entre dos (n va a la fila
     * de otro que sabe f) o una ronda de tres: n va a g, el de g va a h y el de h viene a f (Evelyn a Baños, la de
     * Baños a Minigolf y el de Minigolf a Canilla). Sólo celdas de una persona, todos terminan en algo que saben y
     * nadie deja su estrella. Devuelve si cambió.
     */
    private static boolean rondaDeTres(List<List<Set<Integer>>> filas, List<PuestoDto> puestos,
                                       Map<Integer, AsignadorService.Presente> porNumero,
                                       java.util.function.BiPredicate<Integer, Integer> sabe, int f, int n, int k) {
        java.util.function.IntPredicate sola = g -> !Nombres.noEsPuesto(puestos.get(g).nombre())
                && filas.get(g).get(k).size() == 1 && enUnaSolaFila(filas, filas.get(g).get(k).iterator().next(), k);
        // Sacar al de g de su estrella, sólo si el que entra también la tiene ahí (Nancy por Evelyn en Baños).
        java.util.function.BiPredicate<Integer, Integer> pierdeEstrella = (g, entra) -> {
            String clave = Nombres.clave(puestos.get(g).nombre());
            AsignadorService.Presente q = porNumero.get(filas.get(g).get(k).iterator().next());
            AsignadorService.Presente e = porNumero.get(entra);
            return q != null && q.principales().contains(clave) && (e == null || !e.principales().contains(clave));
        };
        for (int g = 0; g < puestos.size(); g++) {
            if (g == f || !sola.test(g) || !sabe.test(n, g) || pierdeEstrella.test(g, n)) continue;
            int a = filas.get(g).get(k).iterator().next();
            if (sabe.test(a, f)) {
                mover(filas, k, n, f, g);
                mover(filas, k, a, g, f);
                return true;
            }
            for (int h = 0; h < puestos.size(); h++) {
                if (h == f || h == g || !sola.test(h) || !sabe.test(a, h) || pierdeEstrella.test(h, a)) continue;
                int b = filas.get(h).get(k).iterator().next();
                if (!sabe.test(b, f)) continue;
                mover(filas, k, n, f, g);
                mover(filas, k, a, g, h);
                mover(filas, k, b, h, f);
                return true;
            }
        }
        return false;
    }

    private static void mover(List<List<Set<Integer>>> filas, int k, int n, int desde, int hasta) {
        filas.get(desde).get(k).remove(n);
        filas.get(hasta).get(k).add(n);
    }

    /**
     * Los que quedan en Libre no se quedan sin hacer nada. En cada horario, cada uno: si está en un puesto alguien
     * que no está en su estrella y la puede hacer ahora, el de Libre toma ese puesto y el otro vuelve a su estrella
     * (la cajera que sale va a Fotos y Nancy vuelve a Baños); si no, va a ayudar a su estrella; si no, al puesto
     * que sabe más importante y con menos gente. Nunca a algo cerrado, que no sabe o en el almuerzo.
     */
    static List<PuestoDto> libresAyudan(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                        Map<String, Integer> pesos) {
        return libresAyudan(puestos, porNumero, pesos, Set.of());
    }

    /** fijos: claves de puestos cuya gente no se cambia (Fotos con una persona hasta el cierre). */
    static List<PuestoDto> libresAyudan(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                        Map<String, Integer> pesos, Set<String> fijos) {
        int libre = -1;
        for (int f = 0; f < puestos.size(); f++) if (Nombres.esLibre(puestos.get(f).nombre())) libre = f;
        if (libre < 0) return puestos;
        List<List<Set<Integer>>> filas = new ArrayList<>();
        for (PuestoDto p : puestos) {
            List<Set<Integer>> celdas = new ArrayList<>();
            for (String c : p.celdas()) celdas.add(new LinkedHashSet<>(PlanRotacion.esCerrado(c) ? List.of() : PlanRotacion.empleados(c)));
            filas.add(celdas);
        }
        java.util.function.BiPredicate<Integer, Integer> abierto = (f, k) -> !Nombres.noEsPuesto(puestos.get(f).nombre())
                && k < puestos.get(f).celdas().size() && !PlanRotacion.esCerrado(puestos.get(f).celdas().get(k));
        java.util.function.BiPredicate<Integer, Integer> sabe = (n, f) -> {
            AsignadorService.Presente q = porNumero.get(n);
            return q != null && q.hoy().contains(Nombres.clave(puestos.get(f).nombre()));
        };
        java.util.function.BiPredicate<Integer, Integer> esEstrella = (n, f) -> {
            AsignadorService.Presente q = porNumero.get(n);
            return q != null && q.principales().contains(Nombres.clave(puestos.get(f).nombre()));
        };
        boolean cambio = false;
        int total = puestos.get(libre).celdas().size();
        for (int k = 0; k < total; k++) {
            for (int n : new ArrayList<>(filas.get(libre).get(k))) {
                if (porNumero.get(n) == null || !enUnaSolaFila(filas, n, k)) continue;
                int destino = -1, devuelto = -1, estrella = -1;
                // 1. Toma el puesto de uno que así vuelve a su estrella, si ese puesto es al menos tan importante
                // como la estrella propia (una cajera libre vuelve al Cajero; no va a Pecera).
                int pesoSuyo = -1;
                for (int f = 0; f < puestos.size(); f++) {
                    if (abierto.test(f, k) && esEstrella.test(n, f) && sabe.test(n, f)) pesoSuyo = Math.max(pesoSuyo, peso(pesos, puestos.get(f)));
                }
                for (int f = 0; f < puestos.size() && destino < 0; f++) {
                    if (!abierto.test(f, k) || !sabe.test(n, f) || filas.get(f).get(k).size() != 1
                            || fijos.contains(Nombres.clave(puestos.get(f).nombre())) || peso(pesos, puestos.get(f)) < pesoSuyo) continue;
                    int o = filas.get(f).get(k).iterator().next();
                    if (esEstrella.test(o, f) || !enUnaSolaFila(filas, o, k)) continue;
                    for (int s = 0; s < puestos.size(); s++) {
                        if (s != f && abierto.test(s, k) && esEstrella.test(o, s) && sabe.test(o, s)) {
                            destino = f;
                            devuelto = o;
                            estrella = s;
                            break;
                        }
                    }
                }
                // 2. Su estrella; 3. lo que sabe, lo más importante y con menos gente.
                if (destino < 0) {
                    int mejor = -1;
                    for (int f = 0; f < puestos.size(); f++) {
                        if (!abierto.test(f, k) || !sabe.test(n, f)) continue;
                        if (esEstrella.test(n, f)) {
                            mejor = f;
                            break;
                        }
                        if (mejor < 0 || compararAyuda(f, mejor, filas, puestos, pesos, k) < 0) mejor = f;
                    }
                    destino = mejor;
                }
                if (destino < 0) continue;
                filas.get(libre).get(k).remove(n);
                filas.get(destino).get(k).add(n);
                if (devuelto >= 0) {
                    filas.get(destino).get(k).remove(devuelto);
                    filas.get(estrella).get(k).add(devuelto);
                }
                cambio = true;
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < p.celdas().size(); k++) {
                String antes = p.celdas().get(k);
                Set<Integer> s = filas.get(f).get(k);
                celdas.add(PlanRotacion.esCerrado(antes) || (s.isEmpty() && PlanRotacion.empleados(antes).isEmpty()) ? antes
                        : s.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
            }
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas, p.roles()));
        }
        return nuevos;
    }

    /**
     * Un puesto que abre tarde con una persona y queda abierto hasta el cierre (Fotos desde las 17) lo hace la misma
     * persona todo ese rato: no rota por una media hora. Se elige a alguien que lo sabe, el que menos falta hace
     * donde está (ya en el puesto, en Libre, de a varios, en uno de prioridad baja). Si al sacarlo queda un puesto
     * vacío, lo cubre el que estaba en el puesto, si lo sabe; si no, alguien de un puesto de prioridad baja que se
     * cierra ese rato (Pecera cierra y ese va a Entrada); si no, uno de Libre. De la Tirolesa o el Show no se saca
     * a nadie. Los que quedan sin lugar pasan a Libre.
     */
    static List<PuestoDto> unoHastaElCierre(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                            Map<String, Integer> pesos, String puesto, Set<String> conRoles) {
        int fila = -1, libre = -1;
        for (int f = 0; f < puestos.size(); f++) {
            if (Nombres.clave(puestos.get(f).nombre()).equals(Nombres.clave(puesto))) fila = f;
            if (Nombres.esLibre(puestos.get(f).nombre())) libre = f;
        }
        if (fila < 0) return puestos;
        int total = puestos.get(fila).celdas().size();
        // El tramo final abierto: desde que abre hasta el cierre.
        int desde = total;
        while (desde > 0 && !PlanRotacion.esCerrado(puestos.get(fila).celdas().get(desde - 1))) desde--;
        if (desde >= total || desde == 0) return puestos;
        List<List<Set<Integer>>> filas = new ArrayList<>();
        for (PuestoDto p : puestos) {
            List<Set<Integer>> celdas = new ArrayList<>();
            for (String c : p.celdas()) celdas.add(new LinkedHashSet<>(PlanRotacion.esCerrado(c) ? List.of() : PlanRotacion.empleados(c)));
            filas.add(celdas);
        }
        java.util.function.BiPredicate<Integer, Integer> sabe = (n, f) -> {
            AsignadorService.Presente q = porNumero.get(n);
            return q != null && q.hoy().contains(Nombres.clave(puestos.get(f).nombre()));
        };
        final int fFila = fila;
        java.util.function.BiFunction<Integer, Integer, Integer> dondeEsta = (n, k) -> {
            for (int f = 0; f < puestos.size(); f++) if (filas.get(f).get(k).contains(n)) return f;
            return -1;
        };
        // Quién: el que sabe y menos falta hace donde está en ese tramo.
        Integer elegido = null;
        int mejorCosto = Integer.MAX_VALUE;
        for (int n : porNumero.keySet()) {
            if (!sabe.test(n, fila)) continue;
            int costo = 0;
            for (int k = desde; k < total && costo < Integer.MAX_VALUE; k++) {
                int f = dondeEsta.apply(n, k);
                if (f == fFila) continue;
                if (f < 0 || Nombres.esDescanso(puestos.get(f).nombre()) || conRoles.contains(Nombres.clave(puestos.get(f).nombre()))
                        || !enUnaSolaFila(filas, n, k)) {
                    costo = Integer.MAX_VALUE;
                    break;
                }
                if (Nombres.esLibre(puestos.get(f).nombre())) costo += 1;
                else if (filas.get(f).get(k).size() > 1) costo += 2;
                else costo += 3 + peso(pesos, puestos.get(f)) / 10;
            }
            if (costo < mejorCosto) {
                mejorCosto = costo;
                elegido = n;
            }
        }
        if (elegido == null) return puestos;
        int n = elegido;
        boolean cambio = false;
        Map<Integer, Set<Integer>> aCerrar = new HashMap<>();
        for (int k = desde; k < total; k++) {
            Set<Integer> enFotos = filas.get(fila).get(k);
            if (enFotos.size() == 1 && enFotos.contains(n)) continue;
            int r = dondeEsta.apply(n, k);
            List<Integer> salen = new ArrayList<>(enFotos);
            salen.remove(Integer.valueOf(n));
            enFotos.clear();
            enFotos.add(n);
            if (r >= 0) filas.get(r).get(k).remove(n);
            cambio = true;
            List<Integer> sinLugar = new ArrayList<>(salen);
            if (r >= 0 && r != libre && filas.get(r).get(k).isEmpty()) {
                int rr = r;
                // Lo cubre el que estaba en el puesto, si lo sabe.
                Integer quien = salen.stream().filter(q -> sabe.test(q, rr)).findFirst().orElse(null);
                if (quien != null) {
                    filas.get(r).get(k).add(quien);
                    sinLugar.remove(quien);
                } else if (peso(pesos, puestos.get(r)) <= Prioridad.BAJA.peso()) {
                    // Era de prioridad baja (Pecera): se cierra ese rato.
                    aCerrar.computeIfAbsent(r, x -> new HashSet<>()).add(k);
                } else {
                    // Si no, uno de Libre que lo sepa; si no, se cierra uno de prioridad baja y ese cubre (en cadena:
                    // la cajera vuelve de Entrada al Cajero y el de Pecera va a Entrada).
                    Integer deLibre = libre < 0 ? null
                            : filas.get(libre).get(k).stream().filter(q -> sabe.test(q, rr)).findFirst().orElse(null);
                    if (deLibre != null) {
                        filas.get(libre).get(k).remove(deLibre);
                        filas.get(r).get(k).add(deLibre);
                    } else {
                        Set<String> intocables = new HashSet<>(conRoles);
                        intocables.add(Nombres.clave(puesto));
                        cubrirCerrandoBajo(filas, puestos, porNumero, pesos, r, k, aCerrar, intocables, 1);
                    }
                }
            }
            if (!sinLugar.isEmpty() && libre >= 0) filas.get(libre).get(k).addAll(sinLugar);
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < p.celdas().size(); k++) {
                String antes = p.celdas().get(k);
                Set<Integer> s = filas.get(f).get(k);
                if (s.isEmpty() && aCerrar.getOrDefault(f, Set.of()).contains(k)) celdas.add(PlanRotacion.CERRADO);
                else if (PlanRotacion.esCerrado(antes) || (s.isEmpty() && PlanRotacion.empleados(antes).isEmpty())) celdas.add(antes);
                else celdas.add(s.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
            }
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas, p.roles()));
        }
        return nuevos;
    }

    /**
     * Nadie hace un puesto de prioridad baja mientras su estrella está abierta: vuelve a su estrella y el de
     * prioridad baja queda cerrado ese rato (la cajera no va a Pecera: las dos en el Cajero y Pecera cerrada).
     * intocables: de ahí no se saca a nadie (Tirolesa, Show, Fotos).
     */
    static List<PuestoDto> estrellaAntesQueBajo(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                                Map<String, Integer> pesos, Set<String> intocables) {
        List<List<String>> celdas = new ArrayList<>();
        puestos.forEach(p -> celdas.add(new ArrayList<>(p.celdas())));
        boolean cambio = false;
        for (int g = 0; g < puestos.size(); g++) {
            PuestoDto pg = puestos.get(g);
            if (Nombres.noEsPuesto(pg.nombre()) || intocables.contains(Nombres.clave(pg.nombre()))) continue;
            if (peso(pesos, pg) > Prioridad.BAJA.peso()) continue;
            for (int k = 0; k < celdas.get(g).size(); k++) {
                String c = celdas.get(g).get(k);
                if (PlanRotacion.esCerrado(c)) continue;
                List<Integer> nums = PlanRotacion.empleados(c);
                if (nums.size() != 1) continue;
                int n = nums.get(0);
                AsignadorService.Presente q = porNumero.get(n);
                if (q == null || !enUnaSolaCelda(celdas, n, k)) continue;
                int estrella = -1;
                for (int s = 0; s < puestos.size(); s++) {
                    PuestoDto ps = puestos.get(s);
                    String clave = Nombres.clave(ps.nombre());
                    if (s == g || Nombres.noEsPuesto(ps.nombre()) || !q.principales().contains(clave) || !q.hoy().contains(clave)) continue;
                    if (k >= celdas.get(s).size() || PlanRotacion.esCerrado(celdas.get(s).get(k)) || peso(pesos, ps) <= peso(pesos, pg)) continue;
                    estrella = s;
                    break;
                }
                if (estrella < 0) continue;
                List<Integer> alla = new ArrayList<>(PlanRotacion.empleados(celdas.get(estrella).get(k)));
                if (alla.contains(n)) continue;
                alla.add(n);
                celdas.get(estrella).set(k, alla.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
                celdas.get(g).set(k, PlanRotacion.CERRADO);
                cambio = true;
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas.get(f), p.roles()));
        }
        return nuevos;
    }

    /**
     * Los puestos que tienen que estar completos (Tirolesa, Cajero, Show: esperados dice cuántos van en cada horario)
     * y quedaron con menos: se cierra un puesto de prioridad baja ese rato y ese completa (Pecera cierra y Agustín
     * vuelve a la Tirolesa). intocables: de ahí no se saca a nadie (Tirolesa, Show, Fotos).
     */
    static List<PuestoDto> completarConBajos(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                             Map<String, Integer> pesos, Map<String, List<Integer>> esperados,
                                             Set<String> intocables) {
        List<List<Set<Integer>>> filas = new ArrayList<>();
        for (PuestoDto p : puestos) {
            List<Set<Integer>> celdas = new ArrayList<>();
            for (String c : p.celdas()) celdas.add(new LinkedHashSet<>(PlanRotacion.esCerrado(c) ? List.of() : PlanRotacion.empleados(c)));
            filas.add(celdas);
        }
        Map<Integer, Set<Integer>> aCerrar = new HashMap<>();
        boolean cambio = false;
        for (int f = 0; f < puestos.size(); f++) {
            List<Integer> esperado = esperados.get(puestos.get(f).nombre());
            if (esperado == null) continue;
            for (int k = 0; k < esperado.size() && k < filas.get(f).size(); k++) {
                if (PlanRotacion.esCerrado(puestos.get(f).celdas().get(k))) continue;
                while (filas.get(f).get(k).size() < esperado.get(k)
                        && cubrirCerrandoBajo(filas, puestos, porNumero, pesos, f, k, aCerrar, intocables, 0)) {
                    cambio = true;
                }
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            List<String> celdas = new ArrayList<>();
            for (int k = 0; k < p.celdas().size(); k++) {
                String antes = p.celdas().get(k);
                Set<Integer> s = filas.get(f).get(k);
                if (s.isEmpty() && aCerrar.getOrDefault(f, Set.of()).contains(k)) celdas.add(PlanRotacion.CERRADO);
                else if (PlanRotacion.esCerrado(antes) || (s.isEmpty() && PlanRotacion.empleados(antes).isEmpty())) celdas.add(antes);
                else celdas.add(s.stream().sorted().map(String::valueOf).collect(Collectors.joining("-")));
            }
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas, p.roles()));
        }
        return nuevos;
    }

    /**
     * Cubre la fila r en el horario k con alguien que la sabe y está solo en un puesto de prioridad baja, que se
     * cierra ese rato (Pecera cierra y ese va a Entrada). Si no hay, y nivel > 0, con alguien de un puesto menos
     * importante que r, que a su vez se cubre así (la cajera vuelve de Entrada al Cajero y el de Pecera va a
     * Entrada). Nadie deja su estrella ni sale de un puesto intocable (Tirolesa, Show, Fotos). Devuelve si cubrió.
     */
    private static boolean cubrirCerrandoBajo(List<List<Set<Integer>>> filas, List<PuestoDto> puestos,
                                              Map<Integer, AsignadorService.Presente> porNumero, Map<String, Integer> pesos,
                                              int r, int k, Map<Integer, Set<Integer>> aCerrar, Set<String> intocables, int nivel) {
        int pesoR = peso(pesos, puestos.get(r));
        String claveR = Nombres.clave(puestos.get(r).nombre());
        for (int pase = 0; pase < 2; pase++) {
            if (pase == 1 && nivel <= 0) break;
            for (int g = 0; g < puestos.size(); g++) {
                PuestoDto pg = puestos.get(g);
                String claveG = Nombres.clave(pg.nombre());
                if (g == r || Nombres.noEsPuesto(pg.nombre()) || intocables.contains(claveG)) continue;
                if (k >= filas.get(g).size() || filas.get(g).get(k).size() != 1) continue;
                int pesoG = peso(pesos, pg);
                if (pesoG >= pesoR || (pase == 0) != (pesoG <= Prioridad.BAJA.peso())) continue;
                int o = filas.get(g).get(k).iterator().next();
                AsignadorService.Presente q = porNumero.get(o);
                if (q == null || !q.hoy().contains(claveR) || q.principales().contains(claveG) || !enUnaSolaFila(filas, o, k)) continue;
                filas.get(g).get(k).remove(o);
                filas.get(r).get(k).add(o);
                if (pase == 0) {
                    aCerrar.computeIfAbsent(g, x -> new HashSet<>()).add(k);
                    return true;
                }
                if (cubrirCerrandoBajo(filas, puestos, porNumero, pesos, g, k, aCerrar, intocables, nivel - 1)) return true;
                filas.get(r).get(k).remove(o);
                filas.get(g).get(k).add(o);
            }
        }
        return false;
    }

    /**
     * En la rueda nadie hace el mismo puesto dos medias horas seguidas (salvo que sea su estrella): si pasa, en el
     * segundo horario se cambia con uno de otro puesto de la rueda que sepa el suyo, siempre que el cambio no deje
     * a ninguno de los dos repitiendo. rueda: claves de los puestos que rotan.
     */
    static List<PuestoDto> sinRepetir(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                      Set<String> rueda) {
        List<List<String>> celdas = new ArrayList<>();
        puestos.forEach(p -> celdas.add(new ArrayList<>(p.celdas())));
        java.util.function.IntPredicate enRueda = f -> rueda.contains(Nombres.clave(puestos.get(f).nombre()));
        java.util.function.BiPredicate<Integer, Integer> sabe = (n, f) -> {
            AsignadorService.Presente q = porNumero.get(n);
            return q != null && q.hoy().contains(Nombres.clave(puestos.get(f).nombre()));
        };
        java.util.function.BiPredicate<Integer, Integer> esEstrella = (n, f) -> {
            AsignadorService.Presente q = porNumero.get(n);
            return q != null && q.principales().contains(Nombres.clave(puestos.get(f).nombre()));
        };
        // Quién está solo en esa celda (null si está vacía, cerrada o hay más de uno).
        // Quién está solo en esa celda (null si está vacía, cerrada, hay más de uno o él hace además otro puesto a
        // esa hora: Travesía y Búsqueda unidas no se separan).
        java.util.function.BiFunction<Integer, Integer, Integer> solo = (f, k) -> {
            if (k < 0 || k >= celdas.get(f).size()) return null;
            String c = celdas.get(f).get(k);
            if (PlanRotacion.esCerrado(c)) return null;
            List<Integer> nums = PlanRotacion.empleados(c);
            if (nums.size() != 1) return null;
            int n = nums.get(0);
            return enUnaSolaCelda(celdas, n, k) ? n : null;
        };
        boolean cambio = false;
        int total = celdas.isEmpty() ? 0 : celdas.get(0).size();
        for (int k = 1; k < total; k++) {
            for (int f = 0; f < puestos.size(); f++) {
                if (!enRueda.test(f)) continue;
                Integer n = solo.apply(f, k);
                if (n == null || !n.equals(solo.apply(f, k - 1)) || esEstrella.test(n, f)) continue;
                for (int g = 0; g < puestos.size(); g++) {
                    if (g == f || !enRueda.test(g)) continue;
                    Integer m = solo.apply(g, k);
                    if (m == null || !sabe.test(n, g) || !sabe.test(m, f) || esEstrella.test(m, g)) continue;
                    // Que ninguno quede repitiendo con el cambio.
                    if (n.equals(solo.apply(g, k - 1)) || n.equals(solo.apply(g, k + 1))
                            || m.equals(solo.apply(f, k - 1)) || m.equals(solo.apply(f, k + 1))) continue;
                    celdas.get(f).set(k, String.valueOf(m));
                    celdas.get(g).set(k, String.valueOf(n));
                    cambio = true;
                    break;
                }
            }
        }
        if (!cambio) return puestos;
        List<PuestoDto> nuevos = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            PuestoDto p = puestos.get(f);
            nuevos.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas.get(f), p.roles()));
        }
        return nuevos;
    }

    /** Para ayudar: primero el puesto más importante; a igualdad, el que tiene menos gente. */
    private static int compararAyuda(int a, int b, List<List<Set<Integer>>> filas, List<PuestoDto> puestos,
                                     Map<String, Integer> pesos, int k) {
        int pa = pesos.getOrDefault(Nombres.clave(puestos.get(a).nombre()), Prioridad.MEDIA.peso());
        int pb = pesos.getOrDefault(Nombres.clave(puestos.get(b).nombre()), Prioridad.MEDIA.peso());
        if (pa != pb) return Integer.compare(pb, pa);
        return Integer.compare(filas.get(a).get(k).size(), filas.get(b).get(k).size());
    }

    /** Los que sobran en ese horario: en Libre, o en una celda con otros. {número, fila}. */
    private static List<int[]> sobran(List<List<Set<Integer>>> filas, List<PuestoDto> puestos, int k, int pesoDestino,
                                      Map<String, Integer> pesos, Set<String> conRoles) {
        List<int[]> sobran = new ArrayList<>();
        for (int f = 0; f < puestos.size(); f++) {
            if (Nombres.esDescanso(puestos.get(f).nombre())) continue;
            Set<Integer> celda = filas.get(f).get(k);
            boolean libre = Nombres.esLibre(puestos.get(f).nombre());
            if (!libre && celda.size() < 2) continue;
            // De un puesto con roles nunca; de otro, sólo para uno al menos igual de importante.
            if (!libre && (conRoles.contains(Nombres.clave(puestos.get(f).nombre())) || peso(pesos, puestos.get(f)) > pesoDestino)) continue;
            for (int n : celda) if (enUnaSolaFila(filas, n, k)) sobran.add(new int[]{n, f});
        }
        return sobran;
    }

    private static int peso(Map<String, Integer> pesos, PuestoDto p) {
        return Objects.requireNonNullElse(pesos.get(Nombres.clave(p.nombre())), Prioridad.MEDIA.peso());
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

    /** Como enUnaSolaFila, con las celdas como texto. */
    private static boolean enUnaSolaCelda(List<List<String>> celdas, int n, int k) {
        int veces = 0;
        for (List<String> fila : celdas) {
            if (k < fila.size() && !PlanRotacion.esCerrado(fila.get(k)) && PlanRotacion.empleados(fila.get(k)).contains(n)) veces++;
        }
        return veces == 1;
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
