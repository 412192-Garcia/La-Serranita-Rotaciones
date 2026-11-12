import { Component, computed, effect, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideBan,
  LucideCheck,
  LucideCircleCheck,
  LucideLoaderCircle,
  LucideSearch,
  LucideStar,
  LucideTriangleExclamationPoint,
} from '@lucide/angular';
import { AFLUENCIAS, Afluencia, Cronograma, Renglon, Sugerencia, puestosPorNumero, textoAfluencia } from '../models/cronograma';
import { Empleado, clavePuesto } from '../models/empleado';
import { CronogramaService, mensajeDeError } from '../services/cronograma.service';
import { EmpleadoService } from '../services/empleado.service';
import { ToastService } from '../shared/toast/toast.service';
import { TarjetaAcompanante, VistaCronograma } from '../shared/vista-cronograma/vista-cronograma';

/** Lo que se marcó hoy: queda guardado en este dispositivo hasta mañana. */
interface DiaGuardado {
  ausentes: number[];
  /** Por empleado, las claves de los puestos que hoy no puede hacer. */
  noHoy: Record<number, string[]>;
  afluencia: Afluencia;
  /** Por empleado en entrenamiento, con quién va en cada tramo del día. */
  acompanan?: Record<number, Tramo[]>;
}

/** Desde qué hora (null = desde el principio) acompaña a quién. */
interface Tramo {
  desde: string | null;
  acompananteId: number | null;
}

function hoyLocal(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const CLAVE_DIA = `rotaciones.dia.${hoyLocal()}`;

function leerDia(): DiaGuardado | null {
  try {
    const texto = localStorage.getItem(CLAVE_DIA);
    return texto ? (JSON.parse(texto) as DiaGuardado) : null;
  } catch {
    return null;
  }
}

@Component({
  selector: 'app-armar-dia',
  imports: [
    RouterLink,
    VistaCronograma,
    LucideBan,
    LucideCheck,
    LucideCircleCheck,
    LucideLoaderCircle,
    LucideSearch,
    LucideStar,
    LucideTriangleExclamationPoint,
  ],
  templateUrl: './armar-dia.html',
  styleUrl: './armar-dia.css',
})
export class ArmarDia implements OnInit {
  private api = inject(CronogramaService);
  private empleadosApi = inject(EmpleadoService);
  private toast = inject(ToastService);

  readonly afluencias = AFLUENCIAS;
  readonly textoAfluencia = textoAfluencia;
  readonly clavePuesto = clavePuesto;

  cargando = signal(true);
  empleados = signal<Empleado[]>([]);

  private guardado = leerDia();
  ausentes = signal<number[]>(this.guardado?.ausentes ?? []);
  noHoy = signal<Record<number, string[]>>(this.guardado?.noHoy ?? {});
  afluencia = signal<Afluencia>(this.guardado?.afluencia ?? 'MEDIA');
  acompanan = signal<Record<number, Tramo[]>>(this.guardado?.acompanan ?? {});

  regulares = computed(() => this.empleados().filter((e) => !e.enEntrenamiento));
  enEntrenamiento = computed(() => this.empleados().filter((e) => e.enEntrenamiento));
  /** Los que vinieron y ocupan un número (sin los que están en entrenamiento). */
  presentes = computed(() => this.regulares().filter((e) => !this.ausentes().includes(e.id!)));
  aprendicesPresentes = computed(() => this.enEntrenamiento().filter((e) => !this.ausentes().includes(e.id!)));

  buscando = signal(false);
  sugerencias = signal<Sugerencia[] | null>(null);

  elegidaId = signal<number | null>(null);
  cronograma = signal<Cronograma | null>(null);
  /** Número del cronograma → id del empleado que lo hace. */
  asignacion = signal<Map<number, number | null>>(new Map());

  numeros = computed(() => [...this.asignacion().keys()].sort((a, b) => a - b));
  private puestosDeNumero = computed(() => {
    const c = this.cronograma();
    return c ? puestosPorNumero(c) : new Map<number, string[]>();
  });
  nombres = computed(() => {
    const porId = new Map(this.empleados().map((e) => [e.id!, e.nombre]));
    const mapa = new Map<number, string>();
    this.asignacion().forEach((id, numero) => {
      const nombre = id == null ? undefined : porId.get(id);
      if (nombre) mapa.set(numero, nombre);
    });
    return mapa;
  });

  constructor() {
    effect(() => {
      const dia: DiaGuardado = {
        ausentes: this.ausentes(),
        noHoy: this.noHoy(),
        afluencia: this.afluencia(),
        acompanan: this.acompanan(),
      };
      try {
        localStorage.setItem(CLAVE_DIA, JSON.stringify(dia));
      } catch {
        // Sin almacenamiento sólo se pierde lo marcado al recargar.
      }
    });
  }

  ngOnInit(): void {
    this.empleadosApi.listar().subscribe({
      next: (l) => {
        this.empleados.set(l);
        this.descartarMarcasViejas(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  /** Lo marcado de empleados que ya no están, o de puestos que ya no sabe, no se arrastra. */
  private descartarMarcasViejas(empleados: Empleado[]): void {
    const porId = new Map(empleados.map((e) => [e.id!, e.puestos.map(clavePuesto)]));
    this.ausentes.update((l) => l.filter((id) => porId.has(id)));
    const regulares = new Set(empleados.filter((e) => !e.enEntrenamiento).map((e) => e.id!));
    const aprendices = new Set(empleados.filter((e) => e.enEntrenamiento).map((e) => e.id!));
    this.acompanan.update((m) => {
      const limpio: Record<number, Tramo[]> = {};
      for (const [id, tramos] of Object.entries(m)) {
        if (!aprendices.has(Number(id))) continue;
        limpio[Number(id)] = tramos.map((t) => ({
          ...t,
          acompananteId: t.acompananteId != null && regulares.has(t.acompananteId) ? t.acompananteId : null,
        }));
      }
      return limpio;
    });
    this.noHoy.update((m) => {
      const limpio: Record<number, string[]> = {};
      for (const [id, claves] of Object.entries(m)) {
        const sabe = porId.get(Number(id));
        const vigentes = sabe ? claves.filter((c) => sabe.includes(c)) : [];
        if (vigentes.length) limpio[Number(id)] = vigentes;
      }
      return limpio;
    });
  }

  // --- En entrenamiento

  tramos(e: Empleado): Tramo[] {
    return this.acompanan()[e.id!] ?? [{ desde: null, acompananteId: e.acompananteHabitualId }];
  }

  private cambiarTramos(e: Empleado, cambio: (t: Tramo[]) => Tramo[]): void {
    const nuevos = cambio(this.tramos(e).map((t) => ({ ...t })));
    this.acompanan.update((m) => ({ ...m, [e.id!]: nuevos }));
  }

  cambiarAcompanante(e: Empleado, i: number, evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.cambiarTramos(e, (t) => {
      t[i].acompananteId = valor === '' ? null : Number(valor);
      return t;
    });
  }

  cambiarDesde(e: Empleado, i: number, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.cambiarTramos(e, (t) => {
      t[i].desde = valor || null;
      return t;
    });
  }

  agregarTramo(e: Empleado): void {
    this.cambiarTramos(e, (t) => [...t, { desde: '14:00', acompananteId: null }]);
  }

  quitarTramo(e: Empleado, i: number): void {
    this.cambiarTramos(e, (t) => t.filter((_, j) => j !== i));
  }

  nombreDe(id: number | null): string {
    return this.empleados().find((e) => e.id === id)?.nombre ?? '';
  }

  /** Qué falta para armar su tarjeta (con quién va, o si esa persona no vino). */
  avisoAprendiz(e: Empleado): string | null {
    for (const t of this.tramos(e)) {
      if (t.acompananteId == null) return 'Elegí con quién va.';
      if (!this.presentes().some((p) => p.id === t.acompananteId)) return `${this.nombreDe(t.acompananteId)} no vino hoy.`;
    }
    return null;
  }

  /** La tarjeta de cada uno en entrenamiento: la de su acompañante en cada tramo. */
  tarjetasAcompanantes = computed<TarjetaAcompanante[]>(() => {
    const c = this.cronograma();
    if (!c) return [];
    const numeroDe = new Map<number, number>();
    this.asignacion().forEach((id, numero) => {
      if (id != null) numeroDe.set(id, numero);
    });
    const tarjetaDe = new Map((c.tarjetas ?? []).map((t) => [t.empleado, t]));
    const resultado: TarjetaAcompanante[] = [];
    for (const e of this.aprendicesPresentes()) {
      const tramos = [...this.tramos(e)].sort((a, b) => (a.desde ?? '').localeCompare(b.desde ?? ''));
      if (tramos.some((t) => t.acompananteId == null || !numeroDe.has(t.acompananteId))) continue;
      const renglones: Renglon[] = [];
      let descanso: string | null = null;
      tramos.forEach((t, i) => {
        const desde = t.desde ?? '';
        const hasta = tramos[i + 1]?.desde ?? '99:99';
        const base = tarjetaDe.get(numeroDe.get(t.acompananteId!)!);
        if (!base) return;
        const delTramo = base.renglones.filter((r) => r.hora >= desde && r.hora < hasta);
        // Al cambiar de acompañante, el primer renglón es el cambio (lo de "cubierto por" es del otro).
        if (i > 0 && delTramo.length) {
          const destino = delTramo[0].texto.match(/rote a (.+)$/i)?.[1] ?? delTramo[0].texto;
          delTramo[0] = { hora: delTramo[0].hora, texto: `Pase a acompañar a ${this.nombreDe(t.acompananteId)} en ${destino}` };
        }
        renglones.push(...delTramo);
        const inicioDescanso = base.descanso?.slice(0, 5);
        if (!descanso && inicioDescanso && inicioDescanso >= desde && inicioDescanso < hasta) descanso = base.descanso;
      });
      const nombres = [...new Set(tramos.map((t) => this.nombreDe(t.acompananteId)))];
      resultado.push({
        nombre: e.nombre,
        detalle: `acompaña a ${nombres.join(' y a ')}`,
        tarjeta: { empleado: 0, descanso, renglones },
      });
    }
    return resultado;
  });

  /** Número → nombres de los que lo acompañan en algún momento. */
  acompanantesDe(numero: number): string {
    const id = this.asignacion().get(numero);
    if (id == null) return '';
    return this.aprendicesPresentes()
      .filter((e) => this.tramos(e).some((t) => t.acompananteId === id))
      .map((e) => e.nombre)
      .join(', ');
  }

  vino(e: Empleado): boolean {
    return !this.ausentes().includes(e.id!);
  }

  alternarAsistencia(e: Empleado): void {
    this.ausentes.update((l) => (l.includes(e.id!) ? l.filter((id) => id !== e.id) : [...l, e.id!]));
  }

  todosVinieron(): void {
    this.ausentes.set([]);
  }

  puedeHoy(e: Empleado, puesto: string): boolean {
    return !(this.noHoy()[e.id!] ?? []).includes(clavePuesto(puesto));
  }

  alternarPuestoHoy(e: Empleado, puesto: string): void {
    const clave = clavePuesto(puesto);
    this.noHoy.update((m) => {
      const actual = m[e.id!] ?? [];
      const nuevo = actual.includes(clave) ? actual.filter((c) => c !== clave) : [...actual, clave];
      return { ...m, [e.id!]: nuevo };
    });
  }

  buscar(): void {
    const presentes = this.presentes();
    if (presentes.length === 0) {
      this.toast.error('Marcá quién vino hoy.');
      return;
    }
    this.buscando.set(true);
    this.cronograma.set(null);
    this.elegidaId.set(null);
    const pedido = {
      afluencia: this.afluencia(),
      presentes: presentes.map((e) => {
        const no = this.noHoy()[e.id!] ?? [];
        return { empleadoId: e.id!, puestosHoy: no.length ? e.puestos.filter((p) => !no.includes(clavePuesto(p))) : null };
      }),
    };
    this.api.sugerir(pedido).subscribe({
      next: (s) => {
        this.sugerencias.set(s);
        this.buscando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.buscando.set(false);
      },
    });
  }

  usar(s: Sugerencia): void {
    const id = s.cronograma.id;
    this.elegidaId.set(id);
    this.api.obtener(id).subscribe({
      next: (c) => {
        this.asignacion.set(new Map(s.asignacion.map((a) => [a.numero, a.empleadoId])));
        this.cronograma.set(c);
        setTimeout(() => document.getElementById('cronograma-elegido')?.scrollIntoView({ behavior: 'smooth' }));
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.elegidaId.set(null);
      },
    });
  }

  /** Cambia quién hace un número; si esa persona tenía otro, se intercambian. */
  reasignar(numero: number, evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    const nuevo = valor === '' ? null : Number(valor);
    this.asignacion.update((actual) => {
      const mapa = new Map(actual);
      const anterior = mapa.get(numero) ?? null;
      if (nuevo != null) {
        for (const [n, id] of mapa) {
          if (id === nuevo && n !== numero) mapa.set(n, anterior);
        }
      }
      mapa.set(numero, nuevo);
      return mapa;
    });
  }

  avisos(numero: number): string[] {
    const id = this.asignacion().get(numero);
    if (id == null) return ['Nadie hace este número.'];
    const e = this.empleados().find((x) => x.id === id);
    if (!e) return [];
    const sabe = e.puestos.map(clavePuesto);
    return (this.puestosDeNumero().get(numero) ?? []).flatMap((p) => {
      if (!sabe.includes(clavePuesto(p))) return [`No sabe ${p}.`];
      if (!this.puedeHoy(e, p)) return [`Hoy no puede ${p}.`];
      return [];
    });
  }

  sinPuesto = computed(() => {
    const asignados = new Set(this.asignacion().values());
    return this.presentes().filter((e) => !asignados.has(e.id!));
  });
}
