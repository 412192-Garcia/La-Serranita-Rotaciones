export interface Empleado {
  id: number | null;
  nombre: string;
  /** Cómo se lo llama en lo impreso (tarjetas y planilla). Vacío: va el nombre. */
  apodo?: string | null;
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

/** Lo que va en lo impreso: el apodo, o el nombre si no tiene. */
export function nombreCorto(e: Pick<Empleado, 'nombre' | 'apodo'>): string {
  return e.apodo?.trim() || e.nombre;
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
