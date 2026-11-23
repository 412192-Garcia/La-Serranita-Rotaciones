/** SIEMPRE: tiene que estar abierto sí o sí (el generador nunca lo cierra por falta de gente). */
export type Prioridad = 'BAJA' | 'MEDIA' | 'ALTA' | 'SIEMPRE';

export const PRIORIDADES: { valor: Prioridad; texto: string }[] = [
  { valor: 'SIEMPRE', texto: 'Siempre' },
  { valor: 'ALTA', texto: 'Alta' },
  { valor: 'MEDIA', texto: 'Media' },
  { valor: 'BAJA', texto: 'Baja' },
];

export interface PuestoParque {
  nombre: string;
  prioridad: Prioridad;
  /** Si aparece en la columna "Puestos fijos" de los cronogramas. */
  mostrarEnFijos: boolean;
  /** Si abre más tarde (Tirolesa, Casa del zorrito); null si está abierto todo el día. */
  abreTarde: AbreTardePuesto | null;
  /** Los roles que necesitan a alguien preparado, uno por persona y en orden (Zorrito, Presentador). */
  roles?: string[];
  /** Cómo se llama el resto de la gente del puesto (Ayudante). */
  rolResto?: string | null;
}

/**
 * Qué pasa con un puesto en un horario.
 * ABRE_TARDE: cerrado fuera del horario; su gente cubre el parque mientras tanto (Tirolesa).
 * REFUERZO: abierto todo el día; en el horario van personas de más (Casa del zorrito).
 * MENOS: un fijo con varios; en el horario se sacan personas, que cubren el parque (Cajero a la tarde).
 */
export type TipoHorario = 'ABRE_TARDE' | 'REFUERZO' | 'MENOS';

export const TIPOS_HORARIO: { valor: TipoHorario; texto: string; personas: string; signo: string }[] = [
  { valor: 'ABRE_TARDE', texto: 'Abre tarde', personas: 'Personas', signo: '' },
  { valor: 'REFUERZO', texto: 'Refuerzo (+)', personas: 'De más', signo: '+' },
  { valor: 'MENOS', texto: 'Menos gente (−)', personas: 'De menos', signo: '−' },
];

export function tipoHorario(tipo: TipoHorario) {
  return TIPOS_HORARIO.find((t) => t.valor === tipo) ?? TIPOS_HORARIO[0];
}

/** Un horario especial del puesto. abre / hasta: "13:00"; hasta null = hasta el cierre. baja/media/alta: personas según la afluencia. */
export interface AbreTardePuesto {
  abre: string;
  hasta: string | null;
  baja: number;
  media: number;
  alta: number;
  tipo: TipoHorario;
}

/** Cuántas personas lleva según la afluencia. */
export function personasSegun(a: AbreTardePuesto, afluencia: 'BAJA' | 'MEDIA' | 'ALTA'): number {
  return afluencia === 'BAJA' ? a.baja : afluencia === 'ALTA' ? a.alta : a.media;
}
