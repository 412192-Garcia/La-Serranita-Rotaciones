import { Component, input } from '@angular/core';
import { Tarjeta } from '../../models/cronograma';

/** La tarjeta que se le da a cada empleado; se ve igual en pantalla y en papel. */
@Component({
  selector: 'app-tarjeta',
  templateUrl: './tarjeta.html',
  styleUrl: './tarjeta.css',
})
export class TarjetaEmpleado {
  tarjeta = input.required<Tarjeta>();
  cronograma = input.required<string>();
  /** Quién hace este número hoy; vacío = sólo el número. */
  nombre = input<string | null>(null);
  /** Va en lugar del número (alguien en entrenamiento no tiene número). */
  etiqueta = input<string | null>(null);
  /** Lo que hace antes de abrir. */
  tarea = input<string | null>(null);
}
