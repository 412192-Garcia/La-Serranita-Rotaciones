import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideLoaderCircle } from '@lucide/angular';
import { PRIORIDADES, Prioridad, PuestoParque } from '../models/puesto';
import { mensajeDeError } from '../services/cronograma.service';
import { PuestoService } from '../services/puesto.service';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-puestos',
  imports: [RouterLink, LucideArrowLeft, LucideLoaderCircle],
  templateUrl: './puestos.html',
  styleUrl: './puestos.css',
})
export class Puestos implements OnInit {
  private api = inject(PuestoService);
  private toast = inject(ToastService);

  readonly prioridades = PRIORIDADES;

  cargando = signal(true);
  puestos = signal<PuestoParque[]>([]);

  ngOnInit(): void {
    this.api.listar().subscribe({
      next: (l) => {
        this.puestos.set(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  cambiar(puesto: PuestoParque, prioridad: Prioridad): void {
    if (puesto.prioridad === prioridad) return;
    const anterior = puesto.prioridad;
    this.actualizar(puesto.nombre, prioridad);
    this.api.cambiarPrioridad({ nombre: puesto.nombre, prioridad }).subscribe({
      error: (e) => {
        this.actualizar(puesto.nombre, anterior);
        this.toast.error(mensajeDeError(e, 'No se pudo guardar.'));
      },
    });
  }

  private actualizar(nombre: string, prioridad: Prioridad): void {
    this.puestos.update((l) => l.map((p) => (p.nombre === nombre ? { ...p, prioridad } : p)));
  }
}
