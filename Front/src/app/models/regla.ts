import { Afluencia } from './cronograma';
import { FijoGenerador } from './generador';
import { clavePuesto } from './empleado';
import { TipoHorario } from './puesto';

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
  /** Lo que cambia en el día (Show del zorrito de 16 a 17 con 3, un cajero menos desde las 16). */
  horarios?: HorarioRegla[];
}

/**
 * Un horario especial de la regla. ABRE_TARDE: abre en ese horario (Show, Fotos); REFUERZO: van personas de más;
 * MENOS: un fijo con varios pierde personas. Esa gente sale de la rueda o vuelve a ella. hasta: null = hasta el cierre.
 */
export interface HorarioRegla {
  puesto: string;
  tipo: TipoHorario;
  personas: number;
  desde: string;
  hasta: string | null;
}

/** "Show del zorrito 16:00 a 17:00 · 3", "Cajero −1 desde 16:00". */
export function textoHorario(h: HorarioRegla): string {
  const cuando = h.hasta ? `${h.desde} a ${h.hasta}` : `desde ${h.desde}`;
  if (h.tipo === 'MENOS') return `${h.puesto} −${h.personas} ${cuando}`;
  if (h.tipo === 'REFUERZO') return `${h.puesto} +${h.personas} ${cuando}`;
  return `${h.puesto} ${cuando} · ${h.personas}`;
}

/** Los puestos que en alguna regla abren tarde (Tirolesa): su gente y su hora salen de las reglas. */
export function puestosDeReglasConHora(reglas: ReglaApertura[]): Set<string> {
  const claves = new Set<string>();
  for (const r of reglas) for (const f of r.fijos) if (f.abre) claves.add(clavePuesto(f.puesto));
  return claves;
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
