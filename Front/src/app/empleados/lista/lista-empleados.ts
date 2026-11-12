import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideLoaderCircle, LucidePencil, LucidePlus, LucideStar, LucideTrash } from '@lucide/angular';
import { Empleado } from '../../models/empleado';
import { mensajeDeError } from '../../services/cronograma.service';
import { EmpleadoService } from '../../services/empleado.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-lista-empleados',
  imports: [RouterLink, LucideLoaderCircle, LucidePencil, LucidePlus, LucideStar, LucideTrash],
  templateUrl: './lista-empleados.html',
  styleUrl: './lista-empleados.css',
})
export class ListaEmpleados implements OnInit {
  private api = inject(EmpleadoService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);

  cargando = signal(true);
  empleados = signal<Empleado[]>([]);

  ngOnInit(): void {
    this.api.listar().subscribe({
      next: (l) => {
        this.empleados.set(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  nombreDe(id: number | null): string | null {
    return id == null ? null : (this.empleados().find((e) => e.id === id)?.nombre ?? null);
  }

  esPrincipal(e: Empleado, puesto: string): boolean {
    return e.principales.includes(puesto);
  }

  async eliminar(e: Empleado): Promise<void> {
    const ok = await this.confirmacion.pedir({
      titulo: 'Borrar empleado',
      mensaje: `¿Borrar a ${e.nombre}?`,
      aceptar: 'Borrar',
      peligro: true,
    });
    if (!ok) return;
    this.api.eliminar(e.id!).subscribe({
      next: () => {
        this.empleados.update((l) => l.filter((x) => x.id !== e.id));
        this.toast.exito('Empleado borrado.');
      },
      error: (err) => this.toast.error(mensajeDeError(err)),
    });
  }
}
