export interface Tarea {
  id: number | null;
  nombre: string;
  /** Cuántas personas hacen falta. */
  cantidad: number;
  /** Sólo la hacen los que la tienen marcada en su ficha. */
  requiereSaber: boolean;
}

export interface TareaAsignada {
  tareaId: number;
  nombre: string;
  cantidad: number;
  empleadoIds: number[];
  aviso: string | null;
}
