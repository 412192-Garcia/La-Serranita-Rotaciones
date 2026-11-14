import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideEye, LucideEyeOff, LucideLoaderCircle, LucidePencil, LucidePlus, LucideTrash } from '@lucide/angular';
import { clavePuesto } from '../models/empleado';
import { GrupoPuestos } from '../models/grupo';
import { GrupoService } from '../services/grupo.service';
import { PRIORIDADES, Prioridad, PuestoParque } from '../models/puesto';
import { mensajeDeError } from '../services/cronograma.service';
import { PuestoService } from '../services/puesto.service';
import { ConfirmacionService } from '../shared/confirmacion/confirmacion.service';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-puestos',
  imports: [RouterLink, LucideArrowLeft, LucideEye, LucideEyeOff, LucideLoaderCircle, LucidePencil, LucidePlus, LucideTrash],
  templateUrl: './puestos.html',
  styleUrl: './puestos.css',
})
export class Puestos implements OnInit {
  private api = inject(PuestoService);
  private gruposApi = inject(GrupoService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);

  readonly prioridades = PRIORIDADES;

  cargando = signal(true);
  puestos = signal<PuestoParque[]>([]);
  /** El puesto que se está renombrando y el nombre nuevo. */
  renombrando = signal<{ de: string; a: string } | null>(null);
  guardandoNombre = signal(false);

  grupos = signal<GrupoPuestos[]>([]);
  /** El grupo que se está creando o cambiando. */
  editandoGrupo = signal<GrupoPuestos | null>(null);
  guardandoGrupo = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  cambiar(puesto: PuestoParque, prioridad: Prioridad): void {
    if (puesto.prioridad === prioridad) return;
    const anterior = puesto.prioridad;
    this.actualizar(puesto.nombre, prioridad);
    this.api.cambiarPrioridad({ ...puesto, prioridad }).subscribe({
      error: (e) => {
        this.actualizar(puesto.nombre, anterior);
        this.toast.error(mensajeDeError(e, 'No se pudo guardar.'));
      },
    });
  }

  alternarFijos(puesto: PuestoParque): void {
    const nuevo = !puesto.mostrarEnFijos;
    this.puestos.update((l) => l.map((p) => (p.nombre === puesto.nombre ? { ...p, mostrarEnFijos: nuevo } : p)));
    this.api.cambiarMostrarEnFijos(puesto.nombre, nuevo).subscribe({
      error: (e) => {
        this.puestos.update((l) => l.map((p) => (p.nombre === puesto.nombre ? { ...p, mostrarEnFijos: !nuevo } : p)));
        this.toast.error(mensajeDeError(e, 'No se pudo guardar.'));
      },
    });
  }

  empezarRenombre(p: PuestoParque): void {
    this.renombrando.set({ de: p.nombre, a: p.nombre });
    setTimeout(() => document.querySelector<HTMLInputElement>('.nombre-nuevo')?.select());
  }

  cambiarNombreNuevo(evento: Event): void {
    const a = (evento.target as HTMLInputElement).value;
    this.renombrando.update((r) => (r ? { ...r, a } : r));
  }

  async confirmarRenombre(): Promise<void> {
    const r = this.renombrando();
    if (!r) return;
    const a = r.a.trim();
    if (!a || clavePuesto(a) === clavePuesto(r.de)) {
      this.renombrando.set(null);
      return;
    }
    const existe = this.puestos().some((p) => clavePuesto(p.nombre) === clavePuesto(a));
    const ok = await this.confirmacion.pedir({
      titulo: existe ? 'Unificar puestos' : 'Renombrar puesto',
      mensaje: existe
        ? `"${r.de}" pasa a ser "${a}" en todos los cronogramas y empleados. Donde estén los dos, quedan en una sola fila.`
        : `"${r.de}" pasa a llamarse "${a}" en todos los cronogramas y empleados.`,
      aceptar: existe ? 'Unificar' : 'Renombrar',
    });
    if (!ok) return;
    this.guardandoNombre.set(true);
    this.api.renombrar(r.de, a).subscribe({
      next: (res) => {
        this.guardandoNombre.set(false);
        this.renombrando.set(null);
        this.toast.exito(`Listo: ${res.cronogramas} cronogramas y ${res.empleados} empleados.`);
        this.cargar();
      },
      error: (e) => {
        this.guardandoNombre.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo cambiar el nombre.'));
      },
    });
  }

  // --- Grupos de puestos

  empezarGrupo(g?: GrupoPuestos): void {
    this.editandoGrupo.set(g ? { ...g, puestos: [...g.puestos] } : { id: null, nombre: '', puestos: [] });
    setTimeout(() => document.querySelector<HTMLInputElement>('.nombre-grupo')?.focus());
  }

  cambiarNombreGrupo(evento: Event): void {
    const nombre = (evento.target as HTMLInputElement).value;
    this.editandoGrupo.update((g) => (g ? { ...g, nombre } : g));
  }

  alternarEnGrupo(puesto: string): void {
    this.editandoGrupo.update((g) => {
      if (!g) return g;
      const esta = g.puestos.some((p) => clavePuesto(p) === clavePuesto(puesto));
      return { ...g, puestos: esta ? g.puestos.filter((p) => clavePuesto(p) !== clavePuesto(puesto)) : [...g.puestos, puesto] };
    });
  }

  enGrupo(puesto: string): boolean {
    return this.editandoGrupo()?.puestos.some((p) => clavePuesto(p) === clavePuesto(puesto)) ?? false;
  }

  guardarGrupo(): void {
    const g = this.editandoGrupo();
    if (!g) return;
    if (!g.nombre.trim()) {
      this.toast.error('Poné un nombre al grupo.');
      return;
    }
    this.guardandoGrupo.set(true);
    this.gruposApi.guardar(g).subscribe({
      next: () => {
        this.guardandoGrupo.set(false);
        this.editandoGrupo.set(null);
        this.toast.exito('Grupo guardado.');
        this.cargarGrupos();
      },
      error: (e) => {
        this.guardandoGrupo.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo guardar el grupo.'));
      },
    });
  }

  async borrarGrupo(g: GrupoPuestos): Promise<void> {
    const ok = await this.confirmacion.pedir({
      titulo: 'Borrar grupo',
      mensaje: `Los que saben "${g.nombre}" pasan a saber sus puestos uno por uno: no pierden nada.`,
      aceptar: 'Borrar',
    });
    if (!ok) return;
    this.gruposApi.eliminar(g.id!).subscribe({
      next: () => {
        this.toast.exito('Grupo borrado.');
        this.cargarGrupos();
      },
      error: (e) => this.toast.error(mensajeDeError(e, 'No se pudo borrar el grupo.')),
    });
  }

  private cargarGrupos(): void {
    this.gruposApi.listar().subscribe({
      next: (l) => this.grupos.set(l),
      error: (e) => this.toast.error(mensajeDeError(e)),
    });
  }

  private cargar(): void {
    this.cargarGrupos();
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

  private actualizar(nombre: string, prioridad: Prioridad): void {
    this.puestos.update((l) => l.map((p) => (p.nombre === nombre ? { ...p, prioridad } : p)));
  }
}
