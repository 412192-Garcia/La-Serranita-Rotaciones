import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideClock,
  LucideEye,
  LucideEyeOff,
  LucideLoaderCircle,
  LucidePencil,
  LucidePlus,
  LucideTrash,
  LucideUsers,
  LucideX,
} from '@lucide/angular';
import { clavePuesto } from '../models/empleado';
import { GrupoPuestos } from '../models/grupo';
import { GrupoService } from '../services/grupo.service';
import { AbreTardePuesto, PRIORIDADES, Prioridad, PuestoParque, TIPOS_HORARIO, TipoHorario, tipoHorario } from '../models/puesto';
import { mensajeDeError } from '../services/cronograma.service';
import { PuestoService } from '../services/puesto.service';
import { ConfirmacionService } from '../shared/confirmacion/confirmacion.service';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-puestos',
  imports: [
    RouterLink,
    LucideClock,
    LucideEye,
    LucideEyeOff,
    LucideLoaderCircle,
    LucidePencil,
    LucidePlus,
    LucideTrash,
    LucideUsers,
    LucideX,
  ],
  templateUrl: './puestos.html',
  styleUrl: './puestos.css',
})
export class Puestos implements OnInit {
  private api = inject(PuestoService);
  private gruposApi = inject(GrupoService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);

  readonly prioridades = PRIORIDADES;
  readonly tiposHorario = TIPOS_HORARIO;
  readonly tipoHorario = tipoHorario;

  cargando = signal(true);
  puestos = signal<PuestoParque[]>([]);
  /** El puesto que se está renombrando y el nombre nuevo. */
  renombrando = signal<{ de: string; a: string } | null>(null);
  guardandoNombre = signal(false);

  grupos = signal<GrupoPuestos[]>([]);

  nuevoPuesto = signal('');
  /** El puesto al que se le está cargando el horario de "abre tarde". */
  editandoTarde = signal<(AbreTardePuesto & { nombre: string }) | null>(null);

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  crearPuesto(): void {
    const nombre = this.nuevoPuesto().trim();
    if (!nombre) return;
    this.api.crear(nombre).subscribe({
      next: () => {
        this.nuevoPuesto.set('');
        this.toast.exito(`${nombre} agregado.`);
        this.cargar();
      },
      error: (e) => this.toast.error(mensajeDeError(e, 'No se pudo agregar.')),
    });
  }

  async borrarPuesto(p: PuestoParque): Promise<void> {
    const ok = await this.confirmacion.pedir({
      titulo: 'Borrar puesto',
      mensaje: `¿Borrar ${p.nombre}? Sale también de lo que sabe cada empleado y de los grupos. Si está en algún cronograma, no se puede.`,
      aceptar: 'Borrar',
      peligro: true,
    });
    if (!ok) return;
    this.api.eliminar(p.nombre).subscribe({
      next: () => {
        this.toast.exito(`${p.nombre} borrado.`);
        this.cargar();
      },
      error: (e) => this.toast.error(mensajeDeError(e, 'No se pudo borrar.')),
    });
  }

  /** "13:00 a 17:00 · 3/4/5". */
  textoTarde(p: PuestoParque): string {
    const a = p.abreTarde!;
    const s = tipoHorario(a.tipo).signo;
    return `${a.abre}${a.hasta ? ' a ' + a.hasta : ''} · ${s}${a.baja}/${s}${a.media}/${s}${a.alta}`;
  }

  empezarTarde(p: PuestoParque): void {
    if (this.editandoTarde()?.nombre === p.nombre) {
      this.editandoTarde.set(null);
      return;
    }
    this.editandoTarde.set({ nombre: p.nombre, ...(p.abreTarde ?? { abre: '13:00', hasta: null, baja: 1, media: 2, alta: 3, tipo: 'ABRE_TARDE' as TipoHorario }) });
  }

  cambiarTarde(cambios: Partial<AbreTardePuesto>): void {
    this.editandoTarde.update((t) => (t ? { ...t, ...cambios } : t));
  }

  guardarTarde(): void {
    const t = this.editandoTarde();
    if (!t) return;
    const { nombre, ...abreTarde } = t;
    this.api.cambiarAbreTarde(nombre, abreTarde).subscribe({
      next: (p) => {
        this.puestos.update((l) => l.map((x) => (x.nombre === nombre ? { ...x, abreTarde: p.abreTarde } : x)));
        this.editandoTarde.set(null);
      },
      error: (e) => this.toast.error(mensajeDeError(e, 'No se pudo guardar.')),
    });
  }

  quitarTarde(p: PuestoParque): void {
    this.api.cambiarAbreTarde(p.nombre, null).subscribe({
      next: () => {
        this.puestos.update((l) => l.map((x) => (x.nombre === p.nombre ? { ...x, abreTarde: null } : x)));
        this.editandoTarde.set(null);
      },
      error: (e) => this.toast.error(mensajeDeError(e, 'No se pudo guardar.')),
    });
  }
  // --- Roles (Show del zorrito: Zorrito, Presentador; el resto, Ayudantes)

  /** El puesto al que se le están cargando los roles. */
  editandoRoles = signal<{ nombre: string; roles: string[]; rolResto: string } | null>(null);

  empezarRoles(p: PuestoParque): void {
    if (this.editandoRoles()?.nombre === p.nombre) {
      this.editandoRoles.set(null);
      return;
    }
    const roles = p.roles?.length ? [...p.roles] : [''];
    this.editandoRoles.set({ nombre: p.nombre, roles, rolResto: p.rolResto ?? '' });
  }

  cambiarRol(i: number, valor: string): void {
    this.editandoRoles.update((r) => (r ? { ...r, roles: r.roles.map((x, j) => (j === i ? valor : x)) } : r));
  }

  cambiarResto(rolResto: string): void {
    this.editandoRoles.update((r) => (r ? { ...r, rolResto } : r));
  }

  agregarRol(): void {
    this.editandoRoles.update((r) => (r ? { ...r, roles: [...r.roles, ''] } : r));
  }

  quitarRol(i: number): void {
    this.editandoRoles.update((r) => (r ? { ...r, roles: r.roles.filter((_, j) => j !== i) } : r));
  }

  guardarRoles(sin = false): void {
    const r = this.editandoRoles();
    if (!r) return;
    const roles = sin ? [] : r.roles.map((x) => x.trim()).filter((x) => x);
    this.api.cambiarRoles(r.nombre, roles, sin ? null : r.rolResto.trim() || null).subscribe({
      next: (p) => {
        this.puestos.update((l) => l.map((x) => (x.nombre === r.nombre ? { ...x, roles: p.roles, rolResto: p.rolResto } : x)));
        this.editandoRoles.set(null);
      },
      error: (e) => this.toast.error(mensajeDeError(e, 'No se pudo guardar.')),
    });
  }

  /** "Zorrito, Presentador + Ayudante". */
  textoRoles(p: PuestoParque): string {
    return (p.roles ?? []).join(', ') + (p.rolResto ? ' + ' + p.rolResto : '');
  }

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
