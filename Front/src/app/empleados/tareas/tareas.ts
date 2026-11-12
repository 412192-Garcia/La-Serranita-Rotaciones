import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideLoaderCircle, LucidePlus, LucideTrash } from '@lucide/angular';
import { Tarea } from '../../models/tarea';
import { mensajeDeError } from '../../services/cronograma.service';
import { TareaService } from '../../services/tarea.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { ToastService } from '../../shared/toast/toast.service';

/** Tareas de antes de abrir: se reparten todas, todos los días. */
@Component({
  selector: 'app-tareas',
  imports: [RouterLink, LucideArrowLeft, LucideLoaderCircle, LucidePlus, LucideTrash],
  templateUrl: './tareas.html',
  styleUrl: './tareas.css',
})
export class Tareas implements OnInit {
  private api = inject(TareaService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);

  cargando = signal(true);
  tareas = signal<Tarea[]>([]);

  nueva = signal<Tarea>({ id: null, nombre: '', cantidad: 1, requiereSaber: false });
  errorNueva = signal('');
  agregando = signal(false);

  ngOnInit(): void {
    this.api.listar().subscribe({
      next: (l) => {
        this.tareas.set(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  cambiarNueva(cambios: Partial<Tarea>): void {
    this.nueva.update((t) => ({ ...t, ...cambios }));
    this.errorNueva.set('');
  }

  agregar(): void {
    const t = this.nueva();
    if (!t.nombre.trim()) {
      this.errorNueva.set('Poné un nombre.');
      return;
    }
    this.agregando.set(true);
    this.api.guardar(t).subscribe({
      next: (creada) => {
        this.tareas.update((l) => [...l, creada].sort((a, b) => a.nombre.localeCompare(b.nombre)));
        this.nueva.set({ id: null, nombre: '', cantidad: 1, requiereSaber: false });
        this.agregando.set(false);
      },
      error: (e) => {
        this.errorNueva.set(mensajeDeError(e, 'No se pudo guardar.'));
        this.agregando.set(false);
      },
    });
  }

  /** Se guarda apenas se cambia; si el servidor no lo acepta, vuelve a como estaba. */
  cambiar(t: Tarea, cambios: Partial<Tarea>): void {
    const nueva = { ...t, ...cambios };
    if (!nueva.nombre.trim() || nueva.cantidad < 1) return;
    this.reemplazar(nueva);
    this.api.guardar(nueva).subscribe({
      error: (e) => {
        this.reemplazar(t);
        this.toast.error(mensajeDeError(e, 'No se pudo guardar.'));
      },
    });
  }

  async eliminar(t: Tarea): Promise<void> {
    const ok = await this.confirmacion.pedir({
      titulo: 'Borrar tarea',
      mensaje: `¿Borrar "${t.nombre}"?`,
      aceptar: 'Borrar',
      peligro: true,
    });
    if (!ok) return;
    this.api.eliminar(t.id!).subscribe({
      next: () => this.tareas.update((l) => l.filter((x) => x.id !== t.id)),
      error: (e) => this.toast.error(mensajeDeError(e)),
    });
  }

  private reemplazar(t: Tarea): void {
    this.tareas.update((l) => l.map((x) => (x.id === t.id ? t : x)));
  }
}
