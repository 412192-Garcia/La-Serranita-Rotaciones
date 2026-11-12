export interface Empleado {
  id: number | null;
  nombre: string;
  /** Puestos que sabe hacer. */
  puestos: string[];
  /** De los que sabe, en cuáles tiene prioridad. */
  principales: string[];
  /** Está aprendiendo: no ocupa un número del cronograma, acompaña a otro. */
  enEntrenamiento: boolean;
  /** A quién acompaña normalmente mientras está en entrenamiento. */
  acompananteHabitualId: number | null;
  /** Ids de las tareas que piden saber y sabe hacer. */
  tareas: number[];
}

/** Para comparar nombres de puestos sin que importen mayúsculas, acentos ni espacios. */
export function clavePuesto(nombre: string): string {
  return nombre
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .trim()
    .replace(/\s+/g, ' ')
    .toLowerCase();
}
