import { Injectable, signal } from '@angular/core';
import { Afluencia, Cronograma, Presente } from '../models/cronograma';
import { Empleado } from '../models/empleado';

/** Lo que se marcó en Armar el día, para generar un cronograma con esa gente. */
export interface DiaParaGenerar {
  afluencia: Afluencia;
  /** Los que vinieron y ocupan un número, con lo que pueden hoy. */
  presentes: Presente[];
  empleados: Empleado[];
  /** Para repartir las tareas de apertura igual que en Armar el día (con los que están en entrenamiento). */
  paraTareas: { empleadoId: number; acompananteId: number | null }[];
}

/** Un cronograma generado para hoy, con quién hace cada número. */
export interface GeneradoParaHoy {
  cronograma: Cronograma;
  asignacion: Map<number, number | null>;
}

/** Lleva el día de Armar el día al generador, y el cronograma generado de vuelta. */
@Injectable({ providedIn: 'root' })
export class DiaService {
  readonly paraGenerar = signal<DiaParaGenerar | null>(null);
  readonly generado = signal<GeneradoParaHoy | null>(null);

  tomarGenerado(): GeneradoParaHoy | null {
    const g = this.generado();
    this.generado.set(null);
    return g;
  }
}
