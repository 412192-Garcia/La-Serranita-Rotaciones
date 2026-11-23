export interface Empleado {
  id: number | null;
  nombre: string;
  /** Puestos que sabe hacer; puede tener grupos ("Parque"). */
  puestos: string[];
  /** Lo mismo con cada grupo cambiado por sus puestos. Lo calcula el back. */
  puedeHacer?: string[];
  /** De los que sabe, en cuáles tiene prioridad. */
  principales: string[];
  /** Está aprendiendo: no ocupa un número del cronograma, acompaña a otro. */
  enEntrenamiento: boolean;
  /** A quién acompaña normalmente mientras está en entrenamiento. */
  acompananteHabitualId: number | null;
  /** Ids de las tareas que piden saber y sabe hacer. */
  tareas: number[];
  /** Los roles de puestos para los que está preparado (Zorrito en Show del zorrito). */
  roles?: RolSabido[];
}

export interface RolSabido {
  puesto: string;
  rol: string;
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

/** Los puestos uno por uno (sin grupos). */
export function puedeHacer(e: Empleado): string[] {
  return e.puedeHacer ?? e.puestos;
}
