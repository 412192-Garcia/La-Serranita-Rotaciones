import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideLoaderCircle, LucidePencil, LucidePlus, LucideStar, LucideTrash } from '@lucide/angular';
import { forkJoin } from 'rxjs';
import { Empleado, clavePuesto } from '../../models/empleado';
import { GrupoPuestos } from '../../models/grupo';
import { GrupoService } from '../../services/grupo.service';
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
  private gruposApi = inject(GrupoService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);

  cargando = signal(true);
  empleados = signal<Empleado[]>([]);
  grupos = signal<GrupoPuestos[]>([]);

  ngOnInit(): void {
    forkJoin({ empleados: this.api.listar(), grupos: this.gruposApi.listar() }).subscribe({
      next: ({ empleados: l, grupos }) => {
        this.grupos.set(grupos);
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

  /** Lo que sabe como se cargó (los grupos por nombre). Si es un grupo, con sus principales: "Parque · Tobogán". */
  chips(e: Empleado): { texto: string; principal: boolean }[] {
    return e.puestos.map((p) => {
      const g = this.grupos().find((x) => clavePuesto(x.nombre) === clavePuesto(p));
      if (!g) return { texto: p, principal: e.principales.includes(p) };
      const suyos = e.principales.filter((x) => g.puestos.some((y) => clavePuesto(y) === clavePuesto(x)));
      return { texto: suyos.length ? `${p} · ${suyos.join(', ')}` : p, principal: suyos.length > 0 };
    });
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
