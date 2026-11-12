import { Component, computed, effect, input, signal } from '@angular/core';
import { LucideIdCard, LucidePrinter, LucideTable } from '@lucide/angular';
import { Cronograma, Tarjeta } from '../../models/cronograma';
import { TarjetaEmpleado } from '../tarjeta/tarjeta';
import { estiloCelda } from '../colores';

type Vista = 'tarjetas' | 'planilla';

/** Tarjetas de los empleados y planilla de un cronograma, con impresión. */
@Component({
  selector: 'app-vista-cronograma',
  imports: [TarjetaEmpleado, LucideIdCard, LucidePrinter, LucideTable],
  templateUrl: './vista-cronograma.html',
  styleUrl: './vista-cronograma.css',
})
export class VistaCronograma {
  cronograma = input.required<Cronograma>();

  readonly estiloCelda = estiloCelda;

  vista = signal<Vista>('tarjetas');
  seleccionada = signal(0);
  aImprimir = signal<Tarjeta[]>([]);

  tarjetas = computed(() => this.cronograma().tarjetas ?? []);
  tarjeta = computed(() => this.tarjetas()[this.seleccionada()] ?? null);

  constructor() {
    effect(() => {
      this.cronograma();
      this.seleccionada.set(0);
    });
  }

  imprimir(tarjetas: Tarjeta[]): void {
    this.aImprimir.set(tarjetas);
    // Se espera a que se dibujen las tarjetas antes de abrir la impresión.
    setTimeout(() => window.print());
  }
}
