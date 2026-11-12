export interface Celda {
  texto: string;
  fondo: string | null;
  color: string | null;
}

export interface FilaPuesto {
  puesto: string;
  celdas: Celda[];
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

export interface Planilla {
  plan: string;
  hojas: string[];
  hoja: number;
  horarios: string[];
  filas: FilaPuesto[];
  tarjetas: Tarjeta[];
}
