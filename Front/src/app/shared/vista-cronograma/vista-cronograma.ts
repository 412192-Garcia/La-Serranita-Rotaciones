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
  /** Número del cronograma → nombre de quién lo hace hoy. */
  nombres = input<Map<number, string>>(new Map());

  readonly estiloCelda = estiloCelda;

  vista = signal<Vista>('tarjetas');
  seleccionada = signal(0);
  aImprimir = signal<Tarjeta[]>([]);

  /** Con nombres, "cubierto por 2 y 5" pasa a "cubierto por Ana y Beto". */
  tarjetas = computed(() => {
    const nombres = this.nombres();
    return (this.cronograma().tarjetas ?? []).map((t) => ({
      ...t,
      renglones: t.renglones.map((r) => ({
        ...r,
        texto: r.texto.replace(/cubierto por ([\d ,y]+) rote/, (_, numeros: string) => {
          const nombresDe = numeros.split(/, | y /).map((n) => nombres.get(Number(n)) ?? n);
          const lista = nombresDe.length > 1 ? `${nombresDe.slice(0, -1).join(', ')} y ${nombresDe.at(-1)}` : nombresDe[0];
          return `cubierto por ${lista} rote`;
        }),
      })),
    }));
  });
  tarjeta = computed(() => this.tarjetas()[this.seleccionada()] ?? null);

  constructor() {
    effect(() => {
      this.cronograma();
      this.seleccionada.set(0);
    });
  }

  nombresCelda(celda: string): string {
    return celda
      .split('-')
      .map((n) => this.nombres().get(Number(n)))
      .filter((n) => !!n)
      .join(', ');
  }

  imprimir(tarjetas: Tarjeta[]): void {
    this.aImprimir.set(tarjetas);
    // Se espera a que se dibujen las tarjetas antes de abrir la impresión.
    setTimeout(() => window.print());
  }
}
