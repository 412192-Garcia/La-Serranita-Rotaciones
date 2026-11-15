import { Injectable, signal } from '@angular/core';
import { Cronograma } from '../models/cronograma';

/** Lleva un cronograma generado hasta el editor, que lo abre como nuevo (sin guardar). */
@Injectable({ providedIn: 'root' })
export class BorradorService {
  readonly pendiente = signal<Cronograma | null>(null);

  tomar(): Cronograma | null {
    const c = this.pendiente();
    this.pendiente.set(null);
    return c;
  }
}
