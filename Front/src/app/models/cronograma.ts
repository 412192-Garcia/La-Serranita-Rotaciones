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

/** Puestos de cada número del cronograma, sin Descanso. */
export function puestosPorNumero(c: Cronograma): Map<number, string[]> {
  const mapa = new Map<number, string[]>();
  for (const p of c.puestos) {
    if (p.nombre.trim().toLowerCase() === 'descanso') continue;
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
