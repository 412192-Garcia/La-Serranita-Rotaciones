import { Component, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideArrowLeft, LucideLoaderCircle, LucideSave, LucideStar } from '@lucide/angular';
import { clavePuesto } from '../../models/empleado';
import { CronogramaService, mensajeDeError } from '../../services/cronograma.service';
import { EmpleadoService } from '../../services/empleado.service';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-editor-empleado',
  imports: [RouterLink, LucideArrowLeft, LucideLoaderCircle, LucideSave, LucideStar],
  templateUrl: './editor-empleado.html',
  styleUrl: './editor-empleado.css',
})
export class EditorEmpleado implements OnInit {
  private api = inject(EmpleadoService);
  private cronogramas = inject(CronogramaService);
  private toast = inject(ToastService);
  private router = inject(Router);

  /** De la ruta; sin id es uno nuevo. */
  id = input<string>();

  cargando = signal(true);
  guardando = signal(false);
  error = signal('');
  errorNombre = signal('');

  nombre = signal('');
  /** Todos los puestos de los cronogramas, más los que ya tenía cargados. */
  todos = signal<string[]>([]);
  sabe = signal<string[]>([]);
  principales = signal<string[]>([]);
  enEntrenamiento = signal(false);
  acompananteHabitualId = signal<number | null>(null);
  /** Los que pueden acompañar: el resto de los empleados que no están en entrenamiento. */
  acompanantes = signal<{ id: number; nombre: string }[]>([]);

  ngOnInit(): void {
    forkJoin({ puestos: this.cronogramas.puestos(), empleados: this.api.listar() }).subscribe({
      next: ({ puestos, empleados }) => {
        const id = this.id();
        const empleado = id ? empleados.find((e) => e.id === Number(id)) : undefined;
        if (id && !empleado) {
          this.toast.error('Ese empleado no existe.');
          this.router.navigate(['/empleados']);
          return;
        }
        const todos = [...puestos];
        for (const p of empleado?.puestos ?? []) {
          if (!todos.some((t) => clavePuesto(t) === clavePuesto(p))) todos.push(p);
        }
        this.todos.set(todos);
        this.acompanantes.set(
          empleados.filter((e) => !e.enEntrenamiento && e.id !== empleado?.id).map((e) => ({ id: e.id!, nombre: e.nombre })),
        );
        if (empleado) {
          this.enEntrenamiento.set(empleado.enEntrenamiento);
          this.acompananteHabitualId.set(empleado.acompananteHabitualId);
          this.nombre.set(empleado.nombre);
          // Se guardan con el nombre de la lista general, para que coincidan con los cronogramas.
          this.sabe.set(todos.filter((t) => empleado.puestos.some((p) => clavePuesto(p) === clavePuesto(t))));
          this.principales.set(todos.filter((t) => empleado.principales.some((p) => clavePuesto(p) === clavePuesto(t))));
        }
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.router.navigate(['/empleados']);
      },
    });
  }

  cambiarNombre(evento: Event): void {
    this.nombre.set((evento.target as HTMLInputElement).value);
    this.errorNombre.set('');
  }

  cambiarAcompanante(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.acompananteHabitualId.set(valor === '' ? null : Number(valor));
  }

  alternarSabe(puesto: string): void {
    if (this.sabe().includes(puesto)) {
      this.sabe.update((l) => l.filter((p) => p !== puesto));
      this.principales.update((l) => l.filter((p) => p !== puesto));
    } else {
      this.sabe.update((l) => [...l, puesto]);
    }
  }

  alternarPrincipal(puesto: string): void {
    if (this.principales().includes(puesto)) {
      this.principales.update((l) => l.filter((p) => p !== puesto));
      return;
    }
    if (!this.sabe().includes(puesto)) this.sabe.update((l) => [...l, puesto]);
    this.principales.update((l) => [...l, puesto]);
  }

  guardar(): void {
    this.error.set('');
    if (!this.nombre().trim()) {
      this.errorNombre.set('Poné un nombre.');
      return;
    }
    const id = this.id();
    this.guardando.set(true);
    this.api
      .guardar({
        id: id ? Number(id) : null,
        nombre: this.nombre(),
        puestos: this.sabe(),
        principales: this.principales(),
        enEntrenamiento: this.enEntrenamiento(),
        acompananteHabitualId: this.enEntrenamiento() ? this.acompananteHabitualId() : null,
      })
      .subscribe({
        next: () => {
          this.toast.exito('Empleado guardado.');
          this.router.navigate(['/empleados']);
        },
        error: (e) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e, 'No se pudo guardar.'));
        },
      });
  }
}
