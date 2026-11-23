import { Afluencia, Asignacion, Cronograma, Presente } from './cronograma';
import { TipoHorario } from './puesto';

export interface FijoGenerador {
  puesto: string;
  personas: number;
  /** En una regla: desde qué hora abre ("13:00") un puesto que abre tarde (Tirolesa); vacío = todo el día. */
  abre?: string | null;
}

/** Un lugar de la rueda: uno o varios puestos que hace la misma persona. */
export interface LugarGenerador {
  puestos: string[];
  inicia: boolean;
}

/** Un horario especial: un puesto que abre tarde (Tirolesa), con refuerzo (Casa del zorrito) o con menos gente (Cajero a la tarde). */
export interface AbreTardeGenerador {
  puesto: string;
  personas: number;
  /** "13:00" */
  abre: string;
  /** null = hasta el cierre. */
  hasta: string | null;
  /** Abre tarde, refuerzo (personas de más) o menos gente (se sacan de un fijo). */
  tipo: TipoHorario;
}

/** En los descansos de los que rotan, la persona de un puesto fijo cubre otro. */
export interface CoberturaGenerador {
  fijo: string;
  cubre: string;
}

export interface PedidoGenerador {
  nombre: string;
  afluencia: Afluencia;
  empleados: number;
  desde: string;
  hasta: string;
  cada: number;
  descansoDesde: string;
  descansoHasta: string;
  fijos: FijoGenerador[];
  rueda: LugarGenerador[];
  coberturas: CoberturaGenerador[];
  abrenTarde: AbreTardeGenerador[];
}

export interface Borrador {
  cronograma: Cronograma;
  avisos: string[];
  /** Si se generó con los que vinieron: quién hace cada número. */
  asignacion: Asignacion[];
  /** Roles que nadie de los que vinieron puede hacer: hay que elegir quién los cubre. */
  faltantes?: FaltanteRol[];
  /** Puestos que quedaron sin nadie aunque se probó todo: hay que pedir un refuerzo. */
  refuerzos?: Refuerzo[];
  /** Con los que vinieron: avisos de cómo está cargado (reglas, horarios especiales), aparte de los del día. */
  deConfiguracion?: string[];
}

export interface Refuerzo {
  puesto: string;
  /** Posiciones de los horarios. */
  celdas: number[];
  horas: string[];
}

export interface PedidoCubrirRol {
  cronograma: Cronograma;
  avisos: string[];
  asignacion: Asignacion[];
  presentes: Presente[];
  puesto: string;
  rol: string;
  celdas: number[];
  empleadoId: number;
  /** Los refuerzos que se pedían: donde faltaba gente, se sigue pidiendo si no entró nadie. */
  refuerzos?: Refuerzo[];
}

export interface FaltanteRol {
  puesto: string;
  rol: string;
  /** Posiciones de los horarios. */
  celdas: number[];
  horas: string[];
}

function numeros(celda: string): number[] {
  return celda
    .split('-')
    .map((p) => p.trim())
    .filter((p) => /^\d+$/.test(p))
    .map(Number);
}

/** Saca de un cronograma armado los datos para generar otro parecido. */
export function pedidoDesde(c: Cronograma): PedidoGenerador {
  const esAparte = (n: string) => ['descanso', 'libre', 'basura'].includes(n.trim().toLowerCase());
  const fijosNombres = new Set(c.fijos ?? []);
  const total = c.horarios.length;

  // Fijos: cuántas personas están en el puesto en al menos un tercio del día.
  const duenos = new Map<number, string>();
  const fijos: FijoGenerador[] = [];
  for (const p of c.puestos) {
    if (!fijosNombres.has(p.nombre)) continue;
    const cuenta = new Map<number, number>();
    p.celdas.forEach((cel) => numeros(cel).forEach((n) => cuenta.set(n, (cuenta.get(n) ?? 0) + 1)));
    const habituales = [...cuenta.entries()].filter(([, v]) => v >= total / 3).sort((a, b) => b[1] - a[1]);
    habituales.forEach(([n]) => duenos.set(n, p.nombre));
    fijos.push({ puesto: p.nombre, personas: Math.max(1, habituales.length) });
  }

  // Rueda: en un horario normal (fuera de los descansos, el que más puestos tiene cubiertos), lo que
  // hace cada uno de los que rotan es un lugar. Así Búsqueda puede estar en dos lugares (con Travesía
  // y con Laberinto), y lo que hace un fijo a la tarde (Fotos del cajero) no entra en la rueda.
  const descansoFila = c.puestos.find((p) => p.nombre.trim().toLowerCase() === 'descanso');
  const enDescanso = new Set(
    descansoFila ? descansoFila.celdas.map((cel, k) => (numeros(cel).length ? k : -1)).filter((k) => k >= 0) : [],
  );
  const filasRueda = c.puestos.filter((p) => !fijosNombres.has(p.nombre) && !esAparte(p.nombre));
  const rotadores = (k: number) => (p: (typeof filasRueda)[number]) => numeros(p.celdas[k] ?? '').filter((n) => !duenos.has(n));
  let mejor = -1;
  let cubiertos = -1;
  for (let k = 0; k < total; k++) {
    if (enDescanso.has(k)) continue;
    const cuantos = filasRueda.filter((p) => rotadores(k)(p).length > 0).length;
    if (cuantos > cubiertos) {
      cubiertos = cuantos;
      mejor = k;
    }
  }
  const rueda: LugarGenerador[] = [];
  if (mejor >= 0) {
    const porPersona = new Map<number, { puestos: string[]; marcados: number; orden: number }>();
    filasRueda.forEach((p, i) => {
      for (const n of rotadores(mejor)(p)) {
        const lugar = porPersona.get(n) ?? { puestos: [], marcados: 0, orden: i };
        lugar.puestos.push(p.nombre);
        if (p.iniciaRotacion) lugar.marcados++;
        porPersona.set(n, lugar);
      }
    });
    // El * va en el lugar con más puestos marcados (Búsqueda puede estar en dos lugares).
    const masMarcados = Math.max(0, ...[...porPersona.values()].map((l) => l.marcados));
    const vistos = new Set<string>();
    [...porPersona.values()]
      .sort((a, b) => a.orden - b.orden)
      .forEach((l) => {
        const firma = l.puestos.join('|');
        if (vistos.has(firma)) return;
        vistos.add(firma);
        rueda.push({ puestos: l.puestos, inicia: masMarcados > 0 && l.marcados === masMarcados });
      });
    // Un solo lugar con *: el primero que lo tenga.
    let yaHay = false;
    rueda.forEach((l) => {
      if (l.inicia && yaHay) l.inicia = false;
      if (l.inicia) yaHay = true;
    });
  }

  // Franja de descansos y quién de un puesto fijo cubre algo de la rueda en ese rato.
  const conGente = [...enDescanso].sort((a, b) => a - b);
  const coberturas: CoberturaGenerador[] = [];
  for (const lugar of rueda) {
    const fila = c.puestos.find((p) => p.nombre === lugar.puestos[0])!;
    for (const k of conGente) {
      for (const n of numeros(fila.celdas[k] ?? '')) {
        const fijo = duenos.get(n);
        if (fijo && !coberturas.some((x) => x.fijo === fijo && x.cubre === lugar.puestos[0])) {
          coberturas.push({ fijo, cubre: lugar.puestos[0] });
        }
      }
    }
  }

  const minutos = (h: string) => Number(h.slice(0, 2)) * 60 + Number(h.slice(3));
  return {
    nombre: `${c.nombre} (borrador)`,
    afluencia: c.afluencia,
    empleados: c.empleados ?? 0,
    desde: c.horarios[0] ?? '11:00',
    hasta: c.horarios[total - 1] ?? '18:00',
    cada: total > 1 ? minutos(c.horarios[1]) - minutos(c.horarios[0]) : 30,
    descansoDesde: conGente.length ? c.horarios[conGente[0]] : '12:00',
    descansoHasta: conGente.length ? c.horarios[conGente[conGente.length - 1]] : '13:30',
    fijos,
    rueda: rueda.some((l) => l.inicia) || !rueda.length ? rueda : rueda.map((l, i) => ({ ...l, inicia: i === 0 })),
    coberturas,
    // Los cronogramas cargados no tienen Tirolesa: se agrega a mano si hace falta.
    abrenTarde: [],
  };
}

/** Generar con los que vinieron: lo que se respondió y quiénes son. */
export interface PedidoGeneradorDia {
  pedido: PedidoGenerador;
  presentes: Presente[];
}
