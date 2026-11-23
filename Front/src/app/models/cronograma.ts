export type Afluencia = 'BAJA' | 'MEDIA' | 'ALTA';

export const AFLUENCIAS: { valor: Afluencia; texto: string }[] = [
  { valor: 'BAJA', texto: 'Baja' },
  { valor: 'MEDIA', texto: 'Media' },
  { valor: 'ALTA', texto: 'Alta' },
];

export function textoAfluencia(a: Afluencia): string {
  return AFLUENCIAS.find((x) => x.valor === a)?.texto ?? a;
}

export interface Puesto {
  nombre: string;
  /** Aclaración que no cambia el puesto ("consultar al encargado"). */
  nota?: string | null;
  /** Ahí arranca la rotación: se ve con un * delante del nombre. */
  iniciaRotacion?: boolean;
  /** Una por horario: vacía, "3", "1-2" o "Cerrado". */
  celdas: string[];
  /** Si el puesto tiene roles, uno por horario: qué número hace cada rol ({ Zorrito: 3 }). */
  roles?: Record<string, number>[] | null;
}

/**
 * Cada uno de la celda con su rol: primero los roles (Zorrito, Presentador) y después el resto con el nombre del
 * resto (Ayudante). Vacío si el puesto no tiene roles en esa celda.
 */
export function rolesDeCelda(p: Puesto, i: number, resto: string | null = null): { rol: string; numero: number }[] {
  const m = p.roles?.[i];
  if (!m || !Object.keys(m).length) return [];
  const conRol = Object.entries(m).map(([rol, numero]) => ({ rol, numero }));
  const usados = new Set(conRol.map((r) => r.numero));
  const demas = (p.celdas[i] ?? '')
    .split('-')
    .filter((n) => /^\d+$/.test(n.trim()) && !usados.has(Number(n)))
    .map((n) => ({ rol: resto ?? '', numero: Number(n) }));
  return [...conRol, ...demas];
}

export interface Renglon {
  hora: string;
  texto: string;
}

export interface Tarjeta {
  empleado: number;
  descanso: string | null;
  renglones: Renglon[];
}

export interface Cronograma {
  id: number | null;
  nombre: string;
  afluencia: Afluencia;
  horarios: string[];
  puestos: Puesto[];
  empleados?: number;
  fijos?: string[];
  tarjetas?: Tarjeta[];
}

export interface CronogramaResumen {
  id: number;
  nombre: string;
  afluencia: Afluencia;
  empleados: number;
  puestos: number;
  desde: string | null;
  hasta: string | null;
  fijos: string[];
}

export interface Presente {
  empleadoId: number;
  /** Lo que puede hacer hoy; null = todo lo que sabe. */
  puestosHoy: string[] | null;
}

export interface PedidoSugerencia {
  afluencia: Afluencia | null;
  presentes: Presente[];
}

export interface Asignacion {
  numero: number;
  empleadoId: number | null;
  nombre: string | null;
}

export interface Sugerencia {
  cronograma: CronogramaResumen;
  diferencias: string[];
  asignacion: Asignacion[];
}

/** Descanso y Libre son filas del cronograma pero no puestos: nadie tiene que "saberlos". */
export function noEsPuesto(nombre: string): boolean {
  return ['descanso', 'libre'].includes(nombre.trim().toLowerCase());
}

/** Puestos de cada número del cronograma, sin Descanso ni Libre. */
export function puestosPorNumero(c: Cronograma): Map<number, string[]> {
  const mapa = new Map<number, string[]>();
  for (const p of c.puestos) {
    if (noEsPuesto(p.nombre)) continue;
    for (const celda of p.celdas) {
      for (const parte of celda.split('-')) {
        if (!/^\d+$/.test(parte.trim())) continue;
        const n = Number(parte);
        const lista = mapa.get(n) ?? [];
        if (!lista.includes(p.nombre)) lista.push(p.nombre);
        mapa.set(n, lista);
      }
    }
  }
  return mapa;
}
