import { Afluencia } from './cronograma';
import { FijoGenerador } from './generador';

/**
 * Qué puestos se abren con tantos empleados (todos los que vinieron) y tal afluencia: los fijos (la Tirolesa
 * también, con su gente; su horario está en Puestos) y la rueda. lugares: en orden; cada lugar son los puestos que
 * hace la misma persona (Travesía, Búsqueda y Laberinto).
 */
export interface ReglaApertura {
  id: number | null;
  empleados: number;
  afluencia: Afluencia;
  fijos: FijoGenerador[];
  lugares: string[][];
}

/** Cuántas personas ocupa la regla: los fijos más uno por lugar. */
export function personasDe(r: ReglaApertura): number {
  return r.fijos.reduce((s, f) => s + f.personas, 0) + r.lugares.length;
}

/**
 * La regla para esa cantidad y afluencia. Si no hay exacta, la de la cantidad más cercana por debajo (o
 * por arriba, si no hay ninguna menor).
 */
export function reglaPara(reglas: ReglaApertura[], empleados: number, afluencia: Afluencia): ReglaApertura | null {
  const mismas = reglas.filter((r) => r.afluencia === afluencia);
  const exacta = mismas.find((r) => r.empleados === empleados);
  if (exacta) return exacta;
  const menores = mismas.filter((r) => r.empleados < empleados).sort((a, b) => b.empleados - a.empleados);
  if (menores.length) return menores[0];
  const mayores = mismas.filter((r) => r.empleados > empleados).sort((a, b) => a.empleados - b.empleados);
  return mayores[0] ?? null;
}
