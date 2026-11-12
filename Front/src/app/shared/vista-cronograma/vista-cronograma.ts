import { Component, computed, effect, input, output, signal } from '@angular/core';
import { LucideIdCard, LucidePrinter, LucideTable } from '@lucide/angular';
import { Cronograma, Tarjeta } from '../../models/cronograma';
import { TarjetaEmpleado } from '../tarjeta/tarjeta';
import { estiloCelda } from '../colores';

type Vista = 'tarjetas' | 'planilla';

/** La tarjeta de alguien en entrenamiento: se arma con la de quien acompaña. */
export interface TarjetaAcompanante {
  nombre: string;
  /** "acompaña a Ana", "acompaña a Ana y a Beto". */
  detalle: string;
  tarjeta: Tarjeta;
  tarea?: string | null;
}

/** Una tarjeta para mostrar o imprimir. etiqueta reemplaza al número (acompañantes). */
interface Item {
  clave: string;
  numero: number | null;
  etiqueta: string | null;
  nombre: string | null;
  tarea: string | null;
  tarjeta: Tarjeta;
}

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
  /** Los que están en entrenamiento hoy, con su tarjeta ya armada. */
  acompanantes = input<TarjetaAcompanante[]>([]);
  /** Número → lo que hace antes de abrir. */
  tareas = input<Map<number, string>>(new Map());
  /** Se abrió la impresión (para guardar lo que se hizo hoy). */
  imprimio = output<void>();

  readonly estiloCelda = estiloCelda;

  vista = signal<Vista>('tarjetas');
  seleccionada = signal(0);
  aImprimir = signal<Item[]>([]);

  items = computed<Item[]>(() => {
    const nombres = this.nombres();
    const numerados = (this.cronograma().tarjetas ?? []).map((t) => ({
      clave: `n${t.empleado}`,
      numero: t.empleado,
      etiqueta: null,
      nombre: nombres.get(t.empleado) ?? null,
      tarea: this.tareas().get(t.empleado) ?? null,
      tarjeta: this.conNombres(t),
    }));
    const acompanantes = this.acompanantes().map((a) => ({
      clave: `a${a.nombre}`,
      numero: null,
      etiqueta: a.nombre,
      nombre: a.detalle,
      tarea: a.tarea ?? null,
      tarjeta: this.conNombres(a.tarjeta),
    }));
    return [...numerados, ...acompanantes];
  });
  item = computed(() => this.items()[this.seleccionada()] ?? null);
  hayInicio = computed(() => this.cronograma().puestos.some((p) => p.iniciaRotacion));

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

  imprimir(items: Item[]): void {
    this.aImprimir.set(items);
    this.imprimio.emit();
    // Se espera a que se dibujen las tarjetas antes de abrir la impresión.
    setTimeout(() => window.print());
  }

  /** Con nombres, "cubierto por 2 y 5" pasa a "cubierto por Ana y Beto". */
  private conNombres(t: Tarjeta): Tarjeta {
    const nombres = this.nombres();
    return {
      ...t,
      renglones: t.renglones.map((r) => ({
        ...r,
        texto: r.texto.replace(/cubierto por ([\d ,y]+) rote/, (_, numeros: string) => {
          const nombresDe = numeros.split(/, | y /).map((n) => nombres.get(Number(n)) ?? n);
          const lista = nombresDe.length > 1 ? `${nombresDe.slice(0, -1).join(', ')} y ${nombresDe.at(-1)}` : nombresDe[0];
          return `cubierto por ${lista} rote`;
        }),
      })),
    };
  }
}
