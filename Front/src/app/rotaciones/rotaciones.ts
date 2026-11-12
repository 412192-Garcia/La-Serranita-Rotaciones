import { Component, computed, inject, signal } from '@angular/core';
import {
  LucideFileSpreadsheet,
  LucideFileUp,
  LucideIdCard,
  LucideLoaderCircle,
  LucidePrinter,
  LucideTable,
} from '@lucide/angular';
import { Planilla, Tarjeta } from '../models/planilla';
import { PlanillaService, mensajeDeError } from '../services/planilla.service';
import { ToastService } from '../shared/toast/toast.service';
import { TarjetaEmpleado } from './tarjeta/tarjeta';

type Vista = 'tarjetas' | 'planilla';

@Component({
  selector: 'app-rotaciones',
  imports: [
    TarjetaEmpleado,
    LucideFileSpreadsheet,
    LucideFileUp,
    LucideIdCard,
    LucideLoaderCircle,
    LucidePrinter,
    LucideTable,
  ],
  templateUrl: './rotaciones.html',
  styleUrl: './rotaciones.css',
})
export class Rotaciones {
  private planillas = inject(PlanillaService);
  private toast = inject(ToastService);

  private archivo: File | null = null;
  planilla = signal<Planilla | null>(null);
  cargando = signal(false);
  arrastrando = signal(false);
  vista = signal<Vista>('tarjetas');
  seleccionada = signal(0);
  aImprimir = signal<Tarjeta[]>([]);

  variacion = computed(() => {
    const p = this.planilla();
    return p ? p.hojas[p.hoja] : '';
  });

  tarjeta = computed(() => this.planilla()?.tarjetas[this.seleccionada()] ?? null);

  elegirArchivo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (archivo) this.cargar(archivo, 0);
  }

  soltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando.set(false);
    const archivo = evento.dataTransfer?.files[0];
    if (archivo) this.cargar(archivo, 0);
  }

  arrastrar(evento: DragEvent, encima: boolean): void {
    evento.preventDefault();
    this.arrastrando.set(encima);
  }

  cambiarHoja(evento: Event): void {
    const hoja = Number((evento.target as HTMLSelectElement).value);
    if (this.archivo) this.cargar(this.archivo, hoja);
  }

  imprimir(tarjetas: Tarjeta[]): void {
    this.aImprimir.set(tarjetas);
    // Se espera a que se dibujen las tarjetas antes de abrir la impresión.
    setTimeout(() => window.print());
  }

  private cargar(archivo: File, hoja: number): void {
    this.cargando.set(true);
    this.planillas.leer(archivo, hoja).subscribe({
      next: (planilla) => {
        this.archivo = archivo;
        this.planilla.set(planilla);
        this.seleccionada.set(0);
        this.cargando.set(false);
      },
      error: (error) => {
        this.toast.error(mensajeDeError(error));
        this.cargando.set(false);
      },
    });
  }
}
