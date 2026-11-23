package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.BorradorDto;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.ReglaAperturaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.example.laserranitarotaciones.repository.ReglaAperturaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma un borrador de cronograma con las reglas que siguen las planillas del parque:
 * los fijos todo el día en su puesto; los que rotan bajan un lugar de la rueda cada horario (el primero
 * arranca en el lugar marcado con *); descansos de un horario, primero los que rotan y después los
 * fijos; mientras descansan los que rotan, los de algunos puestos fijos cubren lugares de la rueda.
 * Si no alcanza la gente para toda la rueda, se cierran los lugares de menor prioridad.
 * Un puesto que abre tarde (Tirolesa) tiene su propia gente: hasta que abre cubre el parque (los
 * almuerzos de los que rotan, lo cerrado por falta de gente), y desde que abre queda ahí hasta el cierre.
 * Si hay reglas de puestos abiertos para esa afluencia, el almuerzo va en tandas, como en las planillas del
 * parque: todos menos los fijos de varios (las cajeras) comen en la primera mitad de la franja, repartidos
 * parejo, y en cada tanda quedan abiertos los puestos de la regla para la gente que sigue trabajando.
 */
@Service
@RequiredArgsConstructor
public class GeneradorService {

    private static final int MAXIMO_HORARIOS = 96;

    private final RotacionService rotacion;
    private final PuestoParqueService prioridades;
    private final ReglaAperturaRepository reglas;

    public BorradorDto generar(PedidoGeneradorDto p) {
        List<String> avisos = new ArrayList<>();
        int empleados = p.empleados();
        if (empleados < 1 || empleados > 60) throw new ValidacionException("La cantidad de empleados tiene que ser de 1 a 60.");

        List<LocalTime> horas = horarios(p);
        int total = horas.size();

        // Números: primero los de los puestos fijos, después los que rotan.
        Map<String, List<Integer>> duenos = new LinkedHashMap<>();
        int numero = 1;
        for (PedidoGeneradorDto.Fijo f : p.fijos() == null ? List.<PedidoGeneradorDto.Fijo>of() : p.fijos()) {
            if (f.puesto() == null || f.puesto().isBlank()) continue;
            List<Integer> nums = duenos.computeIfAbsent(f.puesto().trim(), k -> new ArrayList<>());
            for (int i = 0; i < Math.max(1, Math.min(f.personas(), 5)); i++) nums.add(numero++);
        }
        int fijos = numero - 1;

        // Puestos que abren más tarde (Tirolesa): su gente va después de los fijos. Hasta que abre, cubre
        // el parque; desde que abre, queda ahí hasta el cierre.
        List<Tarde> tardes = new ArrayList<>();
        List<Tarde> menos = new ArrayList<>();
        for (PedidoGeneradorDto.AbreTarde a : p.abrenTarde() == null ? List.<PedidoGeneradorDto.AbreTarde>of() : p.abrenTarde()) {
            if (a.puesto() == null || a.puesto().isBlank() || a.personas() < 1) continue;
            LocalTime abre = hora(a.abre(), "13:00");
            LocalTime cierraA = a.hasta() == null || a.hasta().isBlank() ? null : hora(a.hasta(), null);
            if (cierraA != null && !cierraA.isAfter(abre)) throw new ValidacionException(a.puesto().trim() + " tiene que cerrar después de abrir.");
            int k = 0;
            while (k < total && horas.get(k).isBefore(abre)) k++;
            int cierra = k;
            while (cierra < total && (cierraA == null || horas.get(cierra).isBefore(cierraA))) cierra++;
            if (k == total) avisos.add(a.puesto().trim() + " abre después del cierre: no se usa.");
            if (a.menos()) {
                // Menos gente: los últimos de un puesto fijo salen a cubrir el parque en ese horario.
                List<Integer> suyos = duenos.entrySet().stream()
                        .filter(e -> Nombres.clave(e.getKey()).equals(Nombres.clave(a.puesto()))).map(Map.Entry::getValue)
                        .findFirst().orElse(List.of());
                if (suyos.size() <= a.personas()) {
                    avisos.add("Para que " + a.puesto().trim() + " tenga " + a.personas() + " menos, tiene que ser fijo con más de "
                            + a.personas() + (a.personas() == 1 ? " persona." : " personas."));
                    continue;
                }
                menos.add(new Tarde(a.puesto().trim(), suyos.subList(suyos.size() - a.personas(), suyos.size()), k, cierra, false));
                continue;
            }
            List<Integer> nums = new ArrayList<>();
            for (int i = 0; i < Math.min(a.personas(), 10); i++) nums.add(numero++);
            tardes.add(new Tarde(a.puesto().trim(), nums, k, cierra, a.refuerzo()));
        }
        int enTarde = numero - 1 - fijos;
        int primeroRota = numero;
        int rotan = empleados - fijos - enTarde;
        if (rotan < 0) {
            throw new ValidacionException("Hay más personas en puestos fijos" + (enTarde > 0 ? " y en los que abren tarde" : "")
                    + " (" + (fijos + enTarde) + ") que empleados (" + empleados + ").");
        }

        List<List<String>> lugares = new ArrayList<>();
        int inicio = 0;
        for (PedidoGeneradorDto.Lugar l : p.rueda() == null ? List.<PedidoGeneradorDto.Lugar>of() : p.rueda()) {
            List<String> puestos = l.puestos() == null ? List.of()
                    : l.puestos().stream().filter(x -> x != null && !x.isBlank()).map(String::trim).toList();
            if (puestos.isEmpty()) continue;
            if (l.inicia()) inicio = lugares.size();
            lugares.add(puestos);
        }
        // Un refuerzo puede estar también en la rueda o en los fijos (es el mismo puesto con más gente).
        Set<String> fuera = new LinkedHashSet<>(duenos.keySet());
        tardes.stream().filter(t -> !t.refuerzo()).forEach(t -> fuera.add(t.puesto()));
        validarNombres(fuera, lugares);
        for (Tarde t : tardes) {
            if (!t.refuerzo()) continue;
            boolean esFijo = duenos.keySet().stream().anyMatch(n -> Nombres.clave(n).equals(Nombres.clave(t.puesto())));
            if (!esFijo && lugarDe(lugares, t.puesto()) < 0) {
                avisos.add(t.puesto() + " no está en la rueda ni en los fijos: fuera de su horario queda cerrado.");
            }
        }

        // Si faltan para la rueda, los de un horario especial que están libres la mayor parte del día (Fotos
        // desde las 17, el refuerzo del zorrito de 16 a 17) rotan con los demás; mientras están en su puesto
        // se cierran ese rato los lugares de menor prioridad. Los que no hacen falta quedan en Libre.
        List<Integer> enRueda = new ArrayList<>();
        for (int j = 0; j < rotan; j++) enRueda.add(primeroRota + j);
        Set<Integer> unidos = new HashSet<>();
        Map<Integer, Tarde> tardeDe = new HashMap<>();
        tardes.forEach(t -> t.nums().forEach(n -> tardeDe.put(n, t)));
        List<Tarde> casiTodoElDia = tardes.stream().filter(t -> (total - (t.cierra() - t.abre())) * 2 > total)
                .sorted(java.util.Comparator.comparingInt(t -> t.cierra() - t.abre())).toList();
        for (Tarde t : casiTodoElDia) {
            for (int n : t.nums()) {
                if (enRueda.size() >= lugares.size()) break;
                enRueda.add(n);
                unidos.add(n);
            }
        }

        // Si no alcanza la gente, se cierran los lugares de menor prioridad. Los que tienen que estar
        // abiertos siempre, nunca: si sólo quedan esos y sigue faltando gente, se avisa.
        Map<String, Integer> pesos = prioridades.pesos();
        List<List<String>> cerrados = new ArrayList<>();
        boolean faltaParaSiempre = false;
        while (lugares.size() > enRueda.size()) {
            int peor = 0;
            for (int i = 0; i < lugares.size(); i++) {
                if (peso(lugares.get(i), pesos) <= peso(lugares.get(peor), pesos)) peor = i;
            }
            if (peso(lugares.get(peor), pesos) >= Prioridad.SIEMPRE.peso()) {
                faltaParaSiempre = true;
                break;
            }
            cerrados.add(lugares.remove(peor));
            if (peor < inicio) inicio--;
            if (inicio >= lugares.size()) inicio = 0;
        }
        for (List<String> c : cerrados) {
            avisos.add("Con " + empleados + " empleados no alcanza para " + RotacionService.enumerar(c) + ": queda cerrado.");
        }
        if (faltaParaSiempre) {
            avisos.add("Con " + empleados + " empleados no alcanza para todos los puestos que tienen que estar abiertos siempre ("
                    + RotacionService.enumerar(lugares.stream().map(l -> String.join(" y ", l)).toList())
                    + "): rotan " + enRueda.size() + " para " + lugares.size() + " lugares, así que en cada horario alguno queda vacío.");
        }
        int cantidadLugares = lugares.size();
        if (rotan > 0 && cantidadLugares == 0) avisos.add("Nadie rota: no hay puestos en la rueda.");

        // Con reglas de puestos abiertos, el almuerzo va en tandas (ver repartirTandas).
        Afluencia afluencia = p.afluencia() == null ? Afluencia.MEDIA : p.afluencia();
        List<ReglaAperturaDto> reglasDelDia = reglas.findAll().stream().filter(r -> r.getAfluencia() == afluencia)
                .map(ReglaAperturaService::aDto).toList();
        boolean porTandas = !reglasDelDia.isEmpty();

        // Descansos: la primera mitad de la franja para los que rotan, la segunda para los fijos.
        LocalTime descansoDesde = hora(p.descansoDesde(), "12:00");
        LocalTime descansoHasta = hora(p.descansoHasta(), "13:30");
        List<Integer> franja = new ArrayList<>();
        for (int k = 0; k < total; k++) {
            if (!horas.get(k).isBefore(descansoDesde) && !horas.get(k).isAfter(descansoHasta)) franja.add(k);
        }
        Map<Integer, Integer> descansa = new HashMap<>();
        List<Integer> paraRotan = List.of();
        if (franja.isEmpty()) {
            avisos.add("No hay horarios en la franja de descansos: nadie descansa.");
        } else {
            int mitad = (franja.size() + 1) / 2;
            paraRotan = franja.subList(0, mitad);
            List<Integer> paraFijos = franja.size() > 1 ? franja.subList(mitad, franja.size()) : franja;
            if (!porTandas) for (int j = 0; j < rotan; j++) descansa.put(primeroRota + j, paraRotan.get(j % paraRotan.size()));
            // Dos del mismo puesto fijo nunca descansan a la vez (si no, el puesto queda vacío): si la parte
            // de los fijos no alcanza, el segundo usa un horario anterior de la franja.
            List<Integer> preferidos = new ArrayList<>(paraFijos);
            for (int k = franja.size() - 1; k >= 0; k--) if (!preferidos.contains(franja.get(k))) preferidos.add(franja.get(k));
            int i = 0;
            for (Map.Entry<String, List<Integer>> e : duenos.entrySet()) {
                // Por tandas, los fijos de una persona comen con los demás (en la primera mitad).
                if (porTandas && e.getValue().size() == 1) continue;
                Set<Integer> usados = new HashSet<>();
                for (int n : e.getValue()) {
                    // Primero la parte de los fijos (repartida en ronda), después lo anterior de la franja.
                    List<Integer> orden = new ArrayList<>();
                    for (int x = 0; x < paraFijos.size(); x++) orden.add(paraFijos.get((i + x) % paraFijos.size()));
                    for (int k : preferidos) if (!orden.contains(k)) orden.add(k);
                    int elegido = orden.stream().filter(k -> !usados.contains(k)).findFirst().orElse(-1);
                    if (elegido < 0) {
                        elegido = paraFijos.get(i % paraFijos.size());
                        avisos.add("En " + e.getKey() + " descansan dos a la vez: la franja de descansos es corta.");
                    }
                    usados.add(elegido);
                    descansa.put(n, elegido);
                    i++;
                }
            }
            // El fijo de una sola persona descansa, si se puede, en un horario de la franja en que un fijo de
            // varios (Cajero x2) está completo: así uno de ellos lo cubre, y no alguien de la rueda que no sabe.
            // (No si ese fijo cubre a los que rotan en el almuerzo: tiene que estar en ese horario.)
            Set<String> cubren = new HashSet<>();
            for (PedidoGeneradorDto.Cobertura c : p.coberturas() == null ? List.<PedidoGeneradorDto.Cobertura>of() : p.coberturas()) {
                if (c.fijo() != null) cubren.add(Nombres.clave(c.fijo()));
            }
            for (Map.Entry<String, List<Integer>> e : duenos.entrySet()) {
                if (porTandas || e.getValue().size() != 1 || cubren.contains(Nombres.clave(e.getKey()))) continue;
                int n = e.getValue().get(0);
                java.util.function.IntPredicate hayDeVarios = k -> duenos.values().stream()
                        .anyMatch(otros -> otros.size() > 1 && otros.stream().noneMatch(o -> Integer.valueOf(k).equals(descansa.get(o))));
                Integer actual = descansa.get(n);
                if (actual != null && hayDeVarios.test(actual)) continue;
                List<Integer> candidatos = new ArrayList<>(paraFijos);
                for (int k = franja.size() - 1; k >= 0; k--) if (!candidatos.contains(franja.get(k))) candidatos.add(franja.get(k));
                candidatos.stream().filter(hayDeVarios::test).findFirst().ifPresent(k -> descansa.put(n, k));
            }
            // Los de un puesto que abre tarde descansan mientras está abierto (antes están cubriendo a los
            // que rotan), de a uno y en horarios seguidos, para que nunca quede vacío. Si la franja no cae
            // cuando está abierto, descansan en la parte de la franja que no es la de los que rotan.
            List<Integer> franjaRotan = paraRotan;
            // Por tandas: quién come en qué tanda (los de un puesto que abre tarde, antes de que abra).
            Map<Integer, List<Integer>> aTandas = new LinkedHashMap<>();
            if (porTandas) duenos.values().stream().filter(l -> l.size() == 1).forEach(l -> aTandas.put(l.get(0), franjaRotan));
            for (Tarde t : tardes) {
                if (t.abre() >= total) continue;
                if (porTandas) {
                    List<Integer> cerrado = franjaRotan.stream().filter(k -> k < t.abre() || k >= t.cierra()).toList();
                    if (!cerrado.isEmpty()) {
                        t.nums().forEach(n -> aTandas.put(n, cerrado));
                        continue;
                    }
                }
                List<Integer> abierto = franja.stream().filter(k -> k >= t.abre() && k < t.cierra()).toList();
                if (!abierto.isEmpty()) {
                    int desde = abierto.get(0);
                    int lugar = t.cierra() - desde;
                    for (int x = 0; x < t.nums().size(); x++) descansa.put(t.nums().get(x), desde + x % lugar);
                    if (t.nums().size() > lugar) avisos.add("En " + t.puesto() + " descansan dos a la vez: no hay horarios para todos.");
                    continue;
                }
                List<Integer> antes = franja.stream().filter(k -> k < t.abre() && !franjaRotan.contains(k)).toList();
                List<Integer> opciones = !antes.isEmpty() ? antes : franja;
                for (int x = 0; x < t.nums().size(); x++) descansa.put(t.nums().get(x), opciones.get(x % opciones.size()));
            }
            if (porTandas) {
                // Tandas parejas: cada uno en la que tiene menos gente (así los fijos se alternan entre tandas).
                for (int j = 0; j < rotan; j++) aTandas.put(primeroRota + j, franjaRotan);
                Map<Integer, Integer> carga = new HashMap<>();
                aTandas.forEach((n, opciones) -> {
                    int elegido = opciones.stream().min(java.util.Comparator.comparingInt(k -> carga.getOrDefault(k, 0))).orElseThrow();
                    carga.merge(elegido, 1, Integer::sum);
                    descansa.put(n, elegido);
                });
            }
        }

        // Celdas: un conjunto de personas por fila y horario.
        Map<String, List<Set<Integer>>> filasFijas = new LinkedHashMap<>();
        duenos.forEach((puesto, nums) -> {
            List<Set<Integer>> celdas = vacias(total);
            for (int k = 0; k < total; k++) {
                for (int n : nums) if (!Integer.valueOf(k).equals(descansa.get(n))) celdas.get(k).add(n);
            }
            filasFijas.put(puesto, celdas);
        });
        List<List<Set<Integer>>> rueda = new ArrayList<>();
        for (int i = 0; i < cantidadLugares; i++) rueda.add(vacias(total));
        for (int j = 0; j < enRueda.size() && cantidadLugares > 0; j++) {
            int n = enRueda.get(j);
            for (int k = 0; k < total; k++) {
                if (Integer.valueOf(k).equals(descansa.get(n))) continue;
                if (unidos.contains(n) && k >= tardeDe.get(n).abre() && k < tardeDe.get(n).cierra()) continue;
                rueda.get(Math.floorMod(inicio + j + k, cantidadLugares)).get(k).add(n);
            }
        }

        // Los puestos que abren tarde: cerrados hasta que abren; después, su gente (menos el que descansa).
        Map<String, List<Set<Integer>>> filasTarde = new LinkedHashMap<>();
        for (Tarde t : tardes) {
            List<Set<Integer>> celdas = vacias(total);
            for (int k = t.abre(); k < t.cierra(); k++) {
                for (int n : t.nums()) if (!Integer.valueOf(k).equals(descansa.get(n))) celdas.get(k).add(n);
            }
            filasTarde.put(t.puesto(), celdas);
        }

        // Mientras no está abierto su puesto (o mientras sobran en el suyo), cubren lo que quedó vacío (los
        // almuerzos de los que rotan), de mayor a menor prioridad. Si no hay nada que cubrir, quedan en Libre
        // (disponibles, sin puesto): no se los manda a un puesto cerrado ni a reforzar uno.
        List<Set<Integer>> filaLibre = vacias(total);
        Map<Integer, Set<Integer>> lugarCerradoEn = new HashMap<>();
        List<List<Set<Integer>>> celdasCerradas = new ArrayList<>();
        for (int c = 0; c < cerrados.size(); c++) celdasCerradas.add(vacias(total));
        List<Integer> porPrioridad = ordenPorPrioridad(lugares, pesos);
        for (int k = 0; k < total; k++) {
            int hora = k;
            List<Integer> libres = new ArrayList<>();
            for (Tarde t : tardes) {
                if (k >= t.abre() && k < t.cierra()) continue;
                t.nums().stream().filter(n -> !Integer.valueOf(hora).equals(descansa.get(n)) && !unidos.contains(n)).forEach(libres::add);
            }
            // Si en ese horario un puesto con menos gente puede largar a alguien y hay un puesto que abre tarde
            // abierto (Fotos a las 17), esa persona pasa ahí (la cajera sabe Fotos) y la de Fotos queda libre.
            for (Tarde m : menos) {
                if (k < m.abre() || k >= m.cierra()) continue;
                List<Set<Integer>> fila = buscarFila(filasFijas, m.puesto());
                for (int n : m.nums()) {
                    if (Integer.valueOf(hora).equals(descansa.get(n)) || !fila.get(k).contains(n)) continue;
                    Set<Integer> destino = null;
                    Integer reemplazado = null;
                    for (Tarde t : tardes) {
                        // Sólo un puesto que abre después de que sobra (Fotos a las 17), no uno que ya estaba
                        // abierto con su gente (la Tirolesa desde las 13).
                        if (t.refuerzo() || t.abre() < m.abre() || k < t.abre() || k >= t.cierra()) continue;
                        Set<Integer> celda = filasTarde.get(t.puesto()).get(k);
                        Integer propio = celda.stream().filter(t.nums()::contains).findFirst().orElse(null);
                        if (propio != null) {
                            destino = celda;
                            reemplazado = propio;
                            break;
                        }
                    }
                    if (destino == null) break;
                    destino.remove(reemplazado);
                    destino.add(n);
                    fila.get(k).remove(n);
                    libres.add(reemplazado);
                }
            }
            for (int i : porPrioridad) {
                if (libres.isEmpty()) break;
                if (rueda.get(i).get(k).isEmpty()) rueda.get(i).get(k).add(libres.remove(0));
            }
            // Un puesto con menos gente en ese horario puede largar a alguien, pero sólo si queda un lugar de
            // la rueda vacío para cubrir; si no, se queda en su puesto (no lo mandamos a algo cerrado o de más).
            for (Tarde m : menos) {
                if (k < m.abre() || k >= m.cierra()) continue;
                List<Set<Integer>> fila = buscarFila(filasFijas, m.puesto());
                for (int n : m.nums()) {
                    // Si ya no está en su puesto (pasó a uno que abre tarde, como el Show), no se mueve otra vez.
                    if (Integer.valueOf(hora).equals(descansa.get(n)) || !fila.get(k).contains(n)) continue;
                    Integer hueco = porPrioridad.stream().filter(i -> rueda.get(i).get(hora).isEmpty()).findFirst().orElse(null);
                    if (hueco == null) break;
                    fila.get(k).remove(n);
                    rueda.get(hueco).get(k).add(n);
                }
            }
            filaLibre.get(k).addAll(libres);
            // Fuera del almuerzo, si los que rotan con la rueda están en su puesto, quedan lugares sin nadie:
            // se cierran los de menor prioridad (pasa a cubrir uno de más prioridad quien estaba en uno de menos).
            boolean faltanUnidos = unidos.stream().anyMatch(n -> hora >= tardeDe.get(n).abre() && hora < tardeDe.get(n).cierra());
            if (faltanUnidos && !franja.contains(k)) {
                for (int x = 0; x < porPrioridad.size(); x++) {
                    int i = porPrioridad.get(x);
                    if (!rueda.get(i).get(k).isEmpty()) continue;
                    Integer de = null;
                    for (int y = porPrioridad.size() - 1; y > x; y--) {
                        int c = porPrioridad.get(y);
                        if (!rueda.get(c).get(k).isEmpty() && peso(lugares.get(c), pesos) <= peso(lugares.get(i), pesos)) {
                            de = c;
                            break;
                        }
                    }
                    if (de == null) break;
                    Set<Integer> celda = rueda.get(de).get(k);
                    Integer quien = celda.iterator().next();
                    celda.remove(quien);
                    rueda.get(i).get(k).add(quien);
                }
                for (int i = 0; i < cantidadLugares; i++) {
                    if (rueda.get(i).get(k).isEmpty() && peso(lugares.get(i), pesos) < Prioridad.SIEMPRE.peso()) {
                        lugarCerradoEn.computeIfAbsent(i, x -> new HashSet<>()).add(k);
                    }
                }
            }
        }

        // Coberturas mientras descansan los que rotan.
        Set<String> sinLugar = new LinkedHashSet<>();
        for (int k : new LinkedHashSet<>(paraRotan)) {
            Set<Integer> cubriendo = new HashSet<>();
            for (PedidoGeneradorDto.Cobertura c : p.coberturas() == null ? List.<PedidoGeneradorDto.Cobertura>of() : p.coberturas()) {
                if (c.fijo() == null || c.cubre() == null) continue;
                int lugar = lugarDe(lugares, c.cubre());
                String fijo = duenos.keySet().stream().filter(x -> Nombres.clave(x).equals(Nombres.clave(c.fijo()))).findFirst().orElse(null);
                if (lugar < 0 || fijo == null) {
                    sinLugar.add(c.fijo() + " → " + c.cubre());
                    continue;
                }
                if (!rueda.get(lugar).get(k).isEmpty()) continue;
                Set<Integer> enSuPuesto = filasFijas.get(fijo).get(k);
                Integer quien = enSuPuesto.stream().filter(x -> !cubriendo.contains(x)).findFirst().orElse(null);
                if (quien == null) continue;
                enSuPuesto.remove(quien);
                rueda.get(lugar).get(k).add(quien);
                cubriendo.add(quien);
            }
        }
        for (String s : sinLugar) avisos.add("La cobertura " + s + " no se usó: ese puesto no está en la rueda o no es fijo.");

        // Un fijo de una persona queda vacío mientras ella descansa: lo cubre alguien de la rueda. Primero
        // uno que esté de a dos en un lugar; si no, el del lugar de menor prioridad (si es menor que la del fijo).
        List<Integer> deMenorAMayor = new ArrayList<>(porPrioridad);
        java.util.Collections.reverse(deMenorAMayor);
        for (Map.Entry<String, List<Set<Integer>>> e : filasFijas.entrySet()) {
            int pesoFijo = pesos.getOrDefault(Nombres.clave(e.getKey()), Prioridad.MEDIA.peso());
            List<Integer> suyos = duenos.get(e.getKey());
            for (int k = 0; k < total; k++) {
                int hora = k;
                // Sólo el hueco de su descanso (si está cubriendo otro lugar, es a propósito).
                if (!e.getValue().get(k).isEmpty() || suyos.stream().noneMatch(n -> Integer.valueOf(hora).equals(descansa.get(n)))) continue;
                // Primero uno de otro fijo que esté de a varios en ese horario (la segunda cajera).
                Set<Integer> deOtroFijo = filasFijas.entrySet().stream()
                        .filter(o -> o != e && o.getValue().get(hora).size() > 1).map(o -> o.getValue().get(hora))
                        .findFirst().orElse(null);
                if (deOtroFijo != null) {
                    Integer quien = deOtroFijo.stream().reduce((a, b) -> b).orElseThrow();
                    deOtroFijo.remove(quien);
                    e.getValue().get(k).add(quien);
                    continue;
                }
                // Después uno de los que están libres.
                if (!filaLibre.get(k).isEmpty()) {
                    Integer quien = filaLibre.get(k).iterator().next();
                    filaLibre.get(k).remove(quien);
                    e.getValue().get(k).add(quien);
                    continue;
                }
                Integer lugar = deMenorAMayor.stream().filter(i -> rueda.get(i).get(hora).size() > 1).findFirst()
                        .orElse(deMenorAMayor.stream()
                                .filter(i -> !rueda.get(i).get(hora).isEmpty() && peso(lugares.get(i), pesos) < pesoFijo)
                                .findFirst().orElse(null));
                if (lugar == null) continue;
                Set<Integer> celda = rueda.get(lugar).get(k);
                Integer quien = celda.stream().reduce((a, b) -> b).orElseThrow();
                celda.remove(quien);
                e.getValue().get(k).add(quien);
            }
        }

        long huecos = 0;
        for (int k = 0; k < total; k++) {
            if (franja.contains(k)) continue;
            for (int i = 0; i < cantidadLugares; i++) {
                if (rueda.get(i).get(k).isEmpty() && !lugarCerradoEn.getOrDefault(i, Set.of()).contains(k)) huecos++;
            }
        }
        if (huecos > 0) avisos.add("Fuera de los descansos quedan " + huecos + " medias horas de la rueda sin nadie.");

        // Un puesto puede estar en dos lugares (Búsqueda con Travesía y con Laberinto): una fila con los dos.
        Map<String, List<Set<Integer>>> filasRueda = new LinkedHashMap<>();
        Map<String, Set<Integer>> cerradoEn = new HashMap<>();
        lugarCerradoEn.forEach((i, ks) -> lugares.get(i).forEach(puesto -> cerradoEn.computeIfAbsent(puesto, x -> new HashSet<>()).addAll(ks)));
        Set<String> conInicio = new HashSet<>();
        for (int i = 0; i < cantidadLugares; i++) {
            for (String puesto : lugares.get(i)) {
                List<Set<Integer>> fila = filasRueda.computeIfAbsent(puesto, x -> vacias(total));
                for (int k = 0; k < total; k++) fila.get(k).addAll(rueda.get(i).get(k));
                if (i == inicio) conInicio.add(puesto);
            }
        }
        // Los lugares cerrados por falta de gente: Cerrado, salvo donde los cubrió alguien antes de abrir la Tirolesa.
        Map<String, List<Set<Integer>>> filasCerradas = new LinkedHashMap<>();
        for (int c = 0; c < cerrados.size(); c++) {
            for (String puesto : cerrados.get(c)) {
                List<Set<Integer>> fila = filasRueda.containsKey(puesto) ? filasRueda.get(puesto)
                        : filasCerradas.computeIfAbsent(puesto, x -> vacias(total));
                for (int k = 0; k < total; k++) fila.get(k).addAll(celdasCerradas.get(c).get(k));
            }
        }
        // Un refuerzo se suma a la fila del puesto (fija, de la rueda o cerrada por falta de gente); si no
        // tiene, va en su propia fila, cerrada fuera de su horario.
        Set<String> refuerzos = new HashSet<>();
        for (Tarde t : tardes) {
            if (!t.refuerzo()) continue;
            List<Set<Integer>> fila = buscarFila(filasFijas, t.puesto());
            if (fila == null) fila = buscarFila(filasRueda, t.puesto());
            if (fila == null) fila = buscarFila(filasCerradas, t.puesto());
            if (fila == null) continue;
            for (int k = 0; k < total; k++) fila.get(k).addAll(filasTarde.get(t.puesto()).get(k));
            refuerzos.add(t.puesto());
        }
        if (porTandas) {
            repartirTandas(new LinkedHashSet<>(paraRotan), reglasDelDia, pesos, p, duenos, tardes, descansa, empleados,
                    enRueda, lugares, inicio, filasFijas, filasRueda, filasCerradas, filaLibre, cerradoEn);
        }
        List<PuestoDto> puestos = new ArrayList<>();
        filasFijas.forEach((puesto, celdas) -> puestos.add(new PuestoDto(puesto, null, false, texto(celdas, cerradoEn.get(puesto)))));
        filasTarde.forEach((puesto, celdas) -> {
            if (!refuerzos.contains(puesto)) puestos.add(new PuestoDto(puesto, null, false, textoOCerrado(celdas)));
        });
        filasRueda.forEach((puesto, celdas) -> puestos.add(new PuestoDto(puesto, null, conInicio.contains(puesto), texto(celdas, cerradoEn.get(puesto)))));
        filasCerradas.forEach((puesto, celdas) -> puestos.add(new PuestoDto(puesto, null, false, textoOCerrado(celdas))));
        List<Set<Integer>> descanso = vacias(total);
        descansa.forEach((n, k) -> descanso.get(k).add(n));
        if (filaLibre.stream().anyMatch(s -> !s.isEmpty())) puestos.add(new PuestoDto(Nombres.LIBRE, null, false, texto(filaLibre)));
        if (!descansa.isEmpty()) puestos.add(new PuestoDto("Descanso", null, false, texto(descanso)));

        String nombre = p.nombre() == null || p.nombre().isBlank() ? "Borrador de " + empleados + " empleados" : p.nombre().trim();
        CronogramaDto armado = CronogramaService.normalizar(new CronogramaDto(null, nombre,
                p.afluencia() == null ? Afluencia.MEDIA : p.afluencia(),
                horas.stream().map(RotacionService::formatear).toList(), puestos, null, null, null));
        PlanRotacion plan = PlanRotacion.de(armado.horarios(), armado.puestos());
        return new BorradorDto(new CronogramaDto(null, armado.nombre(), armado.afluencia(), armado.horarios(), armado.puestos(),
                rotacion.cantidadEmpleados(plan), rotacion.fijos(plan), rotacion.armarTarjetas(plan)), avisos);
    }

    /** Un puesto (o varios que hace la misma persona) que queda abierto en una tanda, con cuántos lleva. */
    private record Abierto(List<String> filas, int cupo, int peso, List<Integer> quienes) {
    }

    /**
     * El almuerzo por tandas: en cada horario de la tanda se cuenta la gente que trabaja (sin los que están en
     * un puesto que abre tarde y ya abrió) y quedan abiertos los puestos de la regla de esa cantidad; lo demás
     * queda cerrado en ese horario. Primero cada uno sigue en lo suyo si está abierto; lo que queda sin nadie,
     * de mayor a menor prioridad, lo cubren los demás (primero la cobertura pedida, después los que no tienen
     * puesto, los que rotan y los fijos). Los puestos que tienen que estar abiertos siempre, siempre quedan.
     */
    private static void repartirTandas(Set<Integer> tandas, List<ReglaAperturaDto> reglasDelDia, Map<String, Integer> pesos,
                                       PedidoGeneradorDto p, Map<String, List<Integer>> duenos, List<Tarde> tardes,
                                       Map<Integer, Integer> descansa, int empleados, List<Integer> enRueda,
                                       List<List<String>> lugares, int inicio, Map<String, List<Set<Integer>>> filasFijas,
                                       Map<String, List<Set<Integer>>> filasRueda, Map<String, List<Set<Integer>>> filasCerradas,
                                       List<Set<Integer>> filaLibre, Map<String, Set<Integer>> cerradoEn) {
        Map<String, List<Set<Integer>>> filas = new LinkedHashMap<>(filasFijas);
        filas.putAll(filasRueda);
        filasCerradas.forEach(filas::putIfAbsent);
        Map<String, String> porClave = new HashMap<>();
        filas.keySet().forEach(f -> porClave.put(Nombres.clave(f), f));
        Map<Integer, String> fijoDe = new HashMap<>();
        duenos.forEach((puesto, nums) -> nums.forEach(n -> fijoDe.put(n, puesto)));

        for (int k : tandas) {
            int hora = k;
            Set<Integer> enSuPuesto = new HashSet<>();
            for (Tarde t : tardes) if (k >= t.abre() && k < t.cierra()) enSuPuesto.addAll(t.nums());
            List<Integer> trabajan = new ArrayList<>();
            for (int n = 1; n <= empleados; n++) {
                if (!Integer.valueOf(hora).equals(descansa.get(n)) && !enSuPuesto.contains(n)) trabajan.add(n);
            }
            // Las reglas cuentan a todos, con la gente de la Tirolesa adentro: se elige la que, sin esa gente, entra
            // en los que están para los puestos generales. La Tirolesa la manejan sus horarios, no la regla.
            Set<String> deTarde = new HashSet<>();
            for (Tarde t : tardes) deTarde.add(Nombres.clave(t.puesto()));
            ReglaAperturaDto regla = reglaPara(reglasDelDia, trabajan.size(), deTarde);

            List<Abierto> abiertos = new ArrayList<>();
            Set<String> enRegla = new HashSet<>();
            for (PedidoGeneradorDto.Fijo f : regla.fijos()) {
                if (deTarde.contains(Nombres.clave(f.puesto()))) continue;
                String fila = porClave.get(Nombres.clave(f.puesto()));
                if (fila == null || !enRegla.add(fila)) continue;
                abiertos.add(new Abierto(List.of(fila), Math.max(1, f.personas()),
                        pesos.getOrDefault(Nombres.clave(fila), Prioridad.MEDIA.peso()), new ArrayList<>()));
            }
            Set<String> fijosDeRegla = new HashSet<>(enRegla);
            for (List<String> l : regla.lugares()) {
                // Un puesto puede estar en dos lugares (Búsqueda, mitad con Travesía y mitad con Laberinto): queda en
                // los dos, no sólo en el primero.
                List<String> deEste = l.stream().map(x -> porClave.get(Nombres.clave(x)))
                        .filter(x -> x != null && !fijosDeRegla.contains(x)).distinct().toList();
                if (deEste.isEmpty()) continue;
                enRegla.addAll(deEste);
                abiertos.add(new Abierto(deEste, 1, peso(deEste, pesos), new ArrayList<>()));
            }
            for (String fila : filas.keySet()) {
                int pesoFila = pesos.getOrDefault(Nombres.clave(fila), Prioridad.MEDIA.peso());
                if (enRegla.contains(fila) || pesoFila < Prioridad.SIEMPRE.peso()) continue;
                int cupo = duenos.containsKey(fila) ? duenos.get(fila).size() : 1;
                abiertos.add(new Abierto(List.of(fila), cupo, pesoFila, new ArrayList<>()));
            }
            // Un fijo del día no pierde gente por la regla: si están trabajando las dos cajeras, quedan las dos
            // (lo que falta se cierra en los lugares de menor prioridad).
            for (Map.Entry<String, List<Integer>> e : duenos.entrySet()) {
                int estan = (int) e.getValue().stream().filter(trabajan::contains).count();
                for (int i = 0; i < abiertos.size(); i++) {
                    Abierto a = abiertos.get(i);
                    if (a.filas().equals(List.of(e.getKey())) && a.cupo() < estan) {
                        abiertos.set(i, new Abierto(a.filas(), estan, a.peso(), a.quienes()));
                    }
                }
            }
            abiertos.sort((a, b) -> Integer.compare(b.peso(), a.peso()));
            // Si así hay más lugares que gente, se cierran los de menor prioridad (nunca los de Siempre).
            int cupos = abiertos.stream().mapToInt(Abierto::cupo).sum();
            for (int i = abiertos.size() - 1; i >= 0 && cupos > trabajan.size(); i--) {
                Abierto a = abiertos.get(i);
                if (a.peso() >= Prioridad.SIEMPRE.peso() || a.cupo() > 1) continue;
                abiertos.remove(i);
                cupos -= a.cupo();
            }

            // Dónde estaría cada uno: su puesto fijo o su lugar de la rueda en ese horario.
            Map<Integer, List<String>> suyo = new HashMap<>();
            fijoDe.forEach((n, puesto) -> suyo.put(n, List.of(puesto)));
            for (int j = 0; j < enRueda.size() && !lugares.isEmpty(); j++) {
                suyo.put(enRueda.get(j), lugares.get(Math.floorMod(inicio + j + k, lugares.size())));
            }
            List<Integer> orden = new ArrayList<>(trabajan);
            orden.sort(java.util.Comparator.comparingInt(n -> fijoDe.containsKey(n) ? 0 : suyo.containsKey(n) ? 1 : 2));
            List<Integer> sinUbicar = new ArrayList<>();
            for (int n : orden) {
                List<String> lo = suyo.getOrDefault(n, List.of());
                // Primero su lugar tal cual (Travesía y Búsqueda); si no está, uno que tenga algo de lo suyo.
                Abierto a = abiertos.stream().filter(x -> x.quienes().size() < x.cupo() && new HashSet<>(x.filas()).equals(new HashSet<>(lo)))
                        .findFirst()
                        .orElseGet(() -> abiertos.stream().filter(x -> x.quienes().size() < x.cupo() && x.filas().stream().anyMatch(lo::contains))
                                .findFirst().orElse(null));
                if (a != null) a.quienes().add(n);
                else sinUbicar.add(n);
            }
            // Los que quedan: primero los que no tienen puesto (los de la Tirolesa antes de abrir), después los
            // que rotan y al final los fijos.
            sinUbicar.sort(java.util.Comparator.comparingInt(n -> fijoDe.containsKey(n) ? 2 : suyo.containsKey(n) ? 1 : 0));
            for (Abierto a : abiertos) {
                while (a.quienes().size() < a.cupo() && !sinUbicar.isEmpty()) {
                    Integer quien = null;
                    for (PedidoGeneradorDto.Cobertura c : p.coberturas() == null ? List.<PedidoGeneradorDto.Cobertura>of() : p.coberturas()) {
                        if (c.fijo() == null || c.cubre() == null
                                || a.filas().stream().noneMatch(f -> Nombres.clave(f).equals(Nombres.clave(c.cubre())))) continue;
                        quien = sinUbicar.stream()
                                .filter(n -> fijoDe.containsKey(n) && Nombres.clave(fijoDe.get(n)).equals(Nombres.clave(c.fijo())))
                                .findFirst().orElse(null);
                        if (quien != null) break;
                    }
                    if (quien == null) quien = sinUbicar.get(0);
                    sinUbicar.remove(quien);
                    a.quienes().add(quien);
                }
            }
            // Si sobra gente (no hay regla para esa cantidad), queda libre.
            for (List<Set<Integer>> fila : filas.values()) fila.get(k).removeAll(trabajan);
            filaLibre.get(k).removeAll(trabajan);
            filaLibre.get(k).addAll(sinUbicar);
            for (Abierto a : abiertos) for (String f : a.filas()) filas.get(f).get(k).addAll(a.quienes());
            for (Map.Entry<String, List<Set<Integer>>> e : filas.entrySet()) {
                // Cerrado: lo que no está en la regla, o lo que está pero no le tocó nadie (el de menor prioridad).
                if (abiertos.stream().noneMatch(a -> !a.quienes().isEmpty() && a.filas().contains(e.getKey()))
                        && e.getValue().get(k).isEmpty()) {
                    cerradoEn.computeIfAbsent(e.getKey(), x -> new HashSet<>()).add(k);
                }
            }
        }
    }

    /**
     * La regla para esa cantidad de gente en los puestos generales. Las reglas cuentan a todos: a cada una se le resta
     * la gente de los puestos de horario (la Tirolesa) para compararla. Si no hay exacta, la más cercana por debajo, y
     * si no, por arriba.
     */
private static ReglaAperturaDto reglaPara(List<ReglaAperturaDto> reglasDelDia, int cantidad, Set<String> deTarde) {
        java.util.function.ToIntFunction<ReglaAperturaDto> generales = r -> r.empleados() - r.fijos().stream()
                .filter(f -> deTarde.contains(Nombres.clave(f.puesto()))).mapToInt(PedidoGeneradorDto.Fijo::personas).sum();
        return reglasDelDia.stream().filter(r -> generales.applyAsInt(r) <= cantidad)
                .max(java.util.Comparator.comparingInt(generales))
                .orElseGet(() -> reglasDelDia.stream().min(java.util.Comparator.comparingInt(generales)).orElseThrow());
    }

    private static List<LocalTime> horarios(PedidoGeneradorDto p) {
        LocalTime desde = hora(p.desde(), "11:00");
        LocalTime hasta = hora(p.hasta(), "18:00");
        int cada = p.cada() <= 0 ? 30 : p.cada();
        if (!hasta.isAfter(desde)) throw new ValidacionException("El horario tiene que terminar después de empezar.");
        List<LocalTime> horas = new ArrayList<>();
        for (LocalTime t = desde; !t.isAfter(hasta) && horas.size() < MAXIMO_HORARIOS; t = t.plusMinutes(cada)) {
            horas.add(t);
            if (t.plusMinutes(cada).isBefore(t)) break; // pasó la medianoche
        }
        return horas;
    }

    private static LocalTime hora(String texto, String porDefecto) {
        LocalTime h = ImportadorExcelService.parsearHora(texto == null || texto.isBlank() ? porDefecto : texto);
        if (h == null) throw new ValidacionException("\"" + texto + "\" no es un horario.");
        return h;
    }

    private static void validarNombres(Set<String> fijos, List<List<String>> lugares) {
        Set<String> vistos = new HashSet<>();
        for (String n : fijos) if (!vistos.add(Nombres.clave(n))) throw new ValidacionException(n + " está repetido.");
        // En la rueda un puesto puede repetirse entre lugares (lo hacen dos personas), pero no ser fijo.
        for (List<String> l : lugares) {
            Set<String> enEsteLugar = new HashSet<>();
            for (String n : l) {
                if (Nombres.esDescanso(n)) throw new ValidacionException("Descanso no va en la rueda.");
                if (vistos.contains(Nombres.clave(n))) throw new ValidacionException(n + " es fijo: no puede ir en la rueda.");
                if (!enEsteLugar.add(Nombres.clave(n))) throw new ValidacionException(n + " está repetido en el mismo lugar.");
            }
        }
    }

    private static int peso(List<String> lugar, Map<String, Integer> pesos) {
        return lugar.stream().mapToInt(n -> pesos.getOrDefault(Nombres.clave(n), Prioridad.MEDIA.peso())).max().orElse(0);
    }

    private static int lugarDe(List<List<String>> lugares, String puesto) {
        for (int i = 0; i < lugares.size(); i++) {
            if (lugares.get(i).stream().anyMatch(n -> Nombres.clave(n).equals(Nombres.clave(puesto)))) return i;
        }
        return -1;
    }

    private static List<Set<Integer>> vacias(int total) {
        List<Set<Integer>> l = new ArrayList<>();
        for (int k = 0; k < total; k++) l.add(new LinkedHashSet<>());
        return l;
    }

    private static List<String> texto(List<Set<Integer>> celdas) {
        return celdas.stream().map(s -> s.stream().sorted().map(String::valueOf).collect(Collectors.joining("-"))).toList();
    }

    /** Como texto(), con Cerrado en los horarios en que el puesto se cerró (en una tanda del almuerzo). */
    private static List<String> texto(List<Set<Integer>> celdas, Set<Integer> cerrado) {
        List<String> t = new ArrayList<>(texto(celdas));
        if (cerrado != null) for (int k : cerrado) if (t.get(k).isEmpty()) t.set(k, PlanRotacion.CERRADO);
        return t;
    }

    /** Las celdas sin nadie quedan Cerrado (un puesto que todavía no abrió, o que no alcanzó la gente). */
    private static List<String> textoOCerrado(List<Set<Integer>> celdas) {
        return texto(celdas).stream().map(t -> t.isEmpty() ? PlanRotacion.CERRADO : t).toList();
    }

    /** Índices de los lugares, del de más prioridad al de menos. */
    private static List<Integer> ordenPorPrioridad(List<List<String>> lugares, Map<String, Integer> pesos) {
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < lugares.size(); i++) orden.add(i);
        orden.sort((a, b) -> Integer.compare(peso(lugares.get(b), pesos), peso(lugares.get(a), pesos)));
        return orden;
    }

    /** La fila de un puesto, sin importar mayúsculas ni acentos; null si no está. */
    private static List<Set<Integer>> buscarFila(Map<String, List<Set<Integer>>> filas, String puesto) {
        return filas.entrySet().stream().filter(e -> Nombres.clave(e.getKey()).equals(Nombres.clave(puesto)))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    /**
     * Un puesto que abre tarde: su gente (números), el horario en que abre y en el que ya cerró (exclusivo).
     * refuerzo: su gente se suma a la del puesto en ese horario.
     */
    private record Tarde(String puesto, List<Integer> nums, int abre, int cierra, boolean refuerzo) {
    }
}
