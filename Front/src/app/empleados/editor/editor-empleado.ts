import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideArrowLeft, LucideChevronDown, LucideLoaderCircle, LucideSave, LucideStar } from '@lucide/angular';
import { clavePuesto } from '../../models/empleado';
import { CronogramaService, mensajeDeError } from '../../services/cronograma.service';
import { EmpleadoService } from '../../services/empleado.service';
import { TareaService } from '../../services/tarea.service';
import { GrupoService } from '../../services/grupo.service';
import { GrupoPuestos } from '../../models/grupo';
import { Tarea } from '../../models/tarea';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-editor-empleado',
  imports: [RouterLink, LucideArrowLeft, LucideChevronDown, LucideLoaderCircle, LucideSave, LucideStar],
  templateUrl: './editor-empleado.html',
  styleUrl: './editor-empleado.css',
})
export class EditorEmpleado implements OnInit {
  private api = inject(EmpleadoService);
  private tareasApi = inject(TareaService);
  private gruposApi = inject(GrupoService);
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
  /** Lo que sabe: puestos sueltos y grupos. */
  sabe = signal<string[]>([]);
  grupos = signal<GrupoPuestos[]>([]);
  /** Clave del puesto → nombre del grupo marcado que lo cubre. */
  private porGrupo = computed(() => {
    const m = new Map<string, string>();
    for (const g of this.grupos()) {
      if (!this.sabe().includes(g.nombre)) continue;
      g.puestos.forEach((p) => m.has(clavePuesto(p)) || m.set(clavePuesto(p), g.nombre));
    }
    return m;
  });
  /** Los puestos que no son de ningún grupo. Los de un grupo se ven al desplegarlo. */
  sueltos = computed(() => {
    const deGrupos = new Set(this.grupos().flatMap((g) => g.puestos.map(clavePuesto)));
    return this.todos().filter((p) => !deGrupos.has(clavePuesto(p)));
  });
  /** El grupo desplegado para ver sus puestos. */
  abierto = signal<string | null>(null);
  principales = signal<string[]>([]);
  /** Las tareas que piden saber, y de esas cuáles sabe. */
  tareasConSaber = signal<Tarea[]>([]);
  tareas = signal<number[]>([]);
  enEntrenamiento = signal(false);
  acompananteHabitualId = signal<number | null>(null);
  /** Los que pueden acompañar: el resto de los empleados que no están en entrenamiento. */
  acompanantes = signal<{ id: number; nombre: string }[]>([]);

  ngOnInit(): void {
    forkJoin({
      puestos: this.cronogramas.puestos(),
      empleados: this.api.listar(),
      tareas: this.tareasApi.listar(),
      grupos: this.gruposApi.listar(),
    }).subscribe({
      next: ({ puestos, empleados, tareas, grupos }) => {
        this.grupos.set(grupos);
        const esGrupo = (p: string) => grupos.some((g) => clavePuesto(g.nombre) === clavePuesto(p));
        this.tareasConSaber.set(tareas.filter((t) => t.requiereSaber));
        const id = this.id();
        const empleado = id ? empleados.find((e) => e.id === Number(id)) : undefined;
        if (id && !empleado) {
          this.toast.error('Ese empleado no existe.');
          this.router.navigate(['/configuracion/empleados']);
          return;
        }
        const todos = [...puestos];
        for (const p of [...(empleado?.puestos ?? []), ...(empleado?.principales ?? []), ...grupos.flatMap((g) => g.puestos)]) {
          if (!esGrupo(p) && !todos.some((t) => clavePuesto(t) === clavePuesto(p))) todos.push(p);
        }
        this.todos.set(todos);
        this.acompanantes.set(
          empleados.filter((e) => !e.enEntrenamiento && e.id !== empleado?.id).map((e) => ({ id: e.id!, nombre: e.nombre })),
        );
        if (empleado) {
          this.enEntrenamiento.set(empleado.enEntrenamiento);
          this.tareas.set(empleado.tareas ?? []);
          this.acompananteHabitualId.set(empleado.acompananteHabitualId);
          this.nombre.set(empleado.nombre);
          // Se guardan con el nombre de la lista general, para que coincidan con los cronogramas.
          this.sabe.set([
            ...grupos.filter((g) => empleado.puestos.some((p) => clavePuesto(p) === clavePuesto(g.nombre))).map((g) => g.nombre),
            ...todos.filter((t) => empleado.puestos.some((p) => clavePuesto(p) === clavePuesto(t))),
          ]);
          this.principales.set(todos.filter((t) => empleado.principales.some((p) => clavePuesto(p) === clavePuesto(t))));
        }
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.router.navigate(['/configuracion/empleados']);
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

  alternarTarea(id: number): void {
    this.tareas.update((l) => (l.includes(id) ? l.filter((x) => x !== id) : [...l, id]));
  }

  /** Los principales que están dentro del grupo. */
  principalesDe(g: GrupoPuestos): string[] {
    return this.principales().filter((p) => g.puestos.some((x) => clavePuesto(x) === clavePuesto(p)));
  }

  /** Sin el grupo marcado: cuántos de sus puestos sabe sueltos. */
  sueltosDe(g: GrupoPuestos): number {
    return g.puestos.filter((p) => this.loSabe(p)).length;
  }

  /** El grupo marcado que cubre este puesto (null si lo sabe suelto o no lo sabe). */
  grupoDe(puesto: string): string | null {
    return this.porGrupo().get(clavePuesto(puesto)) ?? null;
  }

  /** Lo sabe, suelto o por un grupo. */
  loSabe(puesto: string): boolean {
    return this.sabe().includes(puesto) || this.grupoDe(puesto) != null;
  }

  alternarSabe(puesto: string): void {
    if (this.sabe().includes(puesto)) {
      this.sabe.update((l) => l.filter((p) => p !== puesto));
      if (!this.loSabe(puesto)) this.principales.update((l) => l.filter((p) => p !== puesto));
    } else if (this.grupoDe(puesto) == null) {
      this.sabe.update((l) => [...l, puesto]);
    }
  }

  alternarGrupo(g: GrupoPuestos): void {
    if (this.sabe().includes(g.nombre)) {
      this.sabe.update((l) => l.filter((p) => p !== g.nombre));
      // Los principales que sólo sabía por el grupo dejan de serlo.
      this.principales.update((l) => l.filter((p) => this.loSabe(p)));
    } else {
      // Los puestos sueltos que ya cubre el grupo no hace falta guardarlos aparte.
      const delGrupo = new Set(g.puestos.map(clavePuesto));
      this.sabe.update((l) => [...l.filter((p) => !delGrupo.has(clavePuesto(p))), g.nombre]);
    }
  }

  alternarPrincipal(puesto: string): void {
    if (this.principales().includes(puesto)) {
      this.principales.update((l) => l.filter((p) => p !== puesto));
      return;
    }
    if (!this.loSabe(puesto)) this.sabe.update((l) => [...l, puesto]);
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
        tareas: this.tareas(),
      })
      .subscribe({
        next: () => {
          this.toast.exito('Empleado guardado.');
          this.router.navigate(['/configuracion/empleados']);
        },
        error: (e) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e, 'No se pudo guardar.'));
        },
      });
  }
}
