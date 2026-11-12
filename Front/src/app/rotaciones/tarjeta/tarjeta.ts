import { Component, input } from '@angular/core';
import { Tarjeta } from '../../models/planilla';

/** La tarjeta que se le da a cada empleado; se ve igual en pantalla y en papel. */
@Component({
  selector: 'app-tarjeta',
  templateUrl: './tarjeta.html',
  styleUrl: './tarjeta.css',
})
export class TarjetaEmpleado {
  tarjeta = input.required<Tarjeta>();
  plan = input.required<string>();
  variacion = input.required<string>();
}
