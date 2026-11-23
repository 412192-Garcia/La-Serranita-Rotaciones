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
 * Cada uno de la celda con su rol: primero los que tienen rol; los demás, en orden, los roles que faltan (con dos
 * en el Show: Zorrito y Presentador) y después el resto (Ayudante). Vacío si el puesto no tiene roles.
 */
export function rolesDeCelda(
  p: Puesto,
  i: number,
  resto: string | null = null,
  roles: string[] = [],
): { rol: string; numero: number }[] {
  const m = p.roles?.[i] ?? {};
  if (!roles.length && !Object.keys(m).length) return [];
  const numeros = (p.celdas[i] ?? '')
    .split('-')
    .filter((n) => /^\d+$/.test(n.trim()))
    .map(Number);
  const conRol = Object.entries(m)
    .filter(([, numero]) => numeros.includes(numero))
    .map(([rol, numero]) => ({ rol, numero }));
  const usados = new Set(conRol.map((r) => r.numero));
  const faltan = roles.filter((r) => !conRol.some((c) => c.rol === r));
  const demas = numeros.filter((n) => !usados.has(n)).map((numero) => ({ rol: faltan.shift() ?? resto ?? '', numero }));
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
