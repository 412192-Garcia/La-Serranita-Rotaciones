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

export interface PedidoSugerencia {
  empleados: number | null;
  afluencia: Afluencia | null;
  fijos: string[];
}

export interface Sugerencia {
  cronograma: CronogramaResumen;
  diferencias: string[];
}
