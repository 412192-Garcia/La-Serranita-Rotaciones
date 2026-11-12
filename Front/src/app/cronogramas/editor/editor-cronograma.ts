import { Component, HostListener, computed, inject, input, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  LucideArrowLeft,
  LucideBan,
  LucideChevronDown,
  LucideChevronUp,
  LucideEraser,
  LucideLoaderCircle,
  LucidePlus,
  LucideSave,
  LucideX,
} from '@lucide/angular';
import { AFLUENCIAS, Afluencia, Puesto } from '../../models/cronograma';
import { CronogramaService, mensajeDeError } from '../../services/cronograma.service';
import { colorEmpleado, esCerrado, estiloCelda } from '../../shared/colores';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { AtrasService } from '../../shared/modal/atras.service';
import { ToastService } from '../../shared/toast/toast.service';

/** Con qué se pinta al tocar una celda. */
type Herramienta = { tipo: 'numero'; numero: number } | { tipo: 'cerrado' } | { tipo: 'borrar' };

/** Lo que hace un toque (y el arrastre que sigue) sobre las celdas. */
type Accion = (celda: string) => string;

function numerosDe(celda: string): number[] {
  return celda
    .split('-')
    .map((p) => p.trim())
    .filter((p) => /^\d+$/.test(p))
    .map(Number);
}

function aMinutos(hora: string): number {
  const [h, m] = hora.split(':').map(Number);
  return h * 60 + m;
}

function aHora(minutos: number): string {
  return `${String(Math.floor(minutos / 60)).padStart(2, '0')}:${String(minutos % 60).padStart(2, '0')}`;
}

@Component({
  selector: 'app-editor-cronograma',
  imports: [

    LucideArrowLeft,
    LucideBan,
    LucideChevronDown,
    LucideChevronUp,
    LucideEraser,
    LucideLoaderCircle,
    LucidePlus,
    LucideSave,
    LucideX,
  ],
  templateUrl: './editor-cronograma.html',
  styleUrl: './editor-cronograma.css',
})
export class EditorCronograma implements OnInit {
  private api = inject(CronogramaService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);
  private router = inject(Router);
  private atras = inject(AtrasService);

  /** De la ruta; sin id es uno nuevo. */
  id = input<string>();

  readonly afluencias = AFLUENCIAS;
  readonly estiloCelda = estiloCelda;
  readonly colorEmpleado = colorEmpleado;
  readonly esCerrado = esCerrado;

  cargando = signal(false);
  guardando = signal(false);
  error = signal('');
  errorNombre = signal('');

  nombre = signal('');
  afluencia = signal<Afluencia>('MEDIA');
  horarios = signal<string[]>([]);
  puestos = signal<Puesto[]>([]);
  /** Los puestos de todos los cronogramas, para elegir el nombre sin escribirlo distinto. */
  puestosConocidos = signal<string[]>([]);

  desde = signal('11:00');
  hasta = signal('18:00');
  cada = signal(30);

  herramienta = signal<Herramienta>({ tipo: 'numero', numero: 1 });
  sumar = signal(false);
  editando = signal<{ fila: number; columna: number } | null>(null);

  private guardado = '';
  private accionArrastre: Accion | null = null;

  /** Los números que se usan, más el siguiente para sumar a alguien. */
  numeros = computed(() => {
    const usados = this.puestos().flatMap((p) => p.celdas.flatMap(numerosDe));
    const maximo = Math.max(0, ...usados);
    const elegido = this.herramienta();
    const hasta = Math.max(maximo + 1, elegido.tipo === 'numero' ? elegido.numero : 0);
    return Array.from({ length: hasta }, (_, i) => i + 1);
  });

  /** Por horario, los números que no están en ningún puesto (ni en Descanso). */
  sinPuesto = computed(() => {
    const maximo = Math.max(0, ...this.puestos().flatMap((p) => p.celdas.flatMap(numerosDe)));
    return this.horarios().map((_, k) => {
      const presentes = new Set(this.puestos().flatMap((p) => numerosDe(p.celdas[k] ?? '')));
      return Array.from({ length: maximo }, (_, i) => i + 1).filter((n) => !presentes.has(n));
    });
  });

  hayCambios = computed(() => this.estado() !== this.guardado);

  ngOnInit(): void {
    this.api.puestos().subscribe({ next: (p) => this.puestosConocidos.set(p) });
    const id = this.id();
    if (!id) {
      this.armarHorarios();
      this.puestos.set([{ nombre: '', nota: null, celdas: this.horarios().map(() => '') }]);
      this.guardado = this.estado();
      return;
    }
    this.cargando.set(true);
    this.api.obtener(Number(id)).subscribe({
      next: (c) => {
        this.nombre.set(c.nombre);
        this.afluencia.set(c.afluencia);
        this.horarios.set([...c.horarios]);
        this.puestos.set(c.puestos.map((p) => ({ ...p, nota: p.nota ?? null, celdas: [...p.celdas] })));
        if (c.horarios.length) {
          this.desde.set(c.horarios[0]);
          this.hasta.set(c.horarios[c.horarios.length - 1]);
          if (c.horarios.length > 1) this.cada.set(aMinutos(c.horarios[1]) - aMinutos(c.horarios[0]));
        }
        this.guardado = this.estado();
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.router.navigate(['/cronogramas']);
      },
    });
  }

  @HostListener('window:beforeunload', ['$event'])
  avisarAlCerrar(evento: BeforeUnloadEvent): void {
    if (this.hayCambios()) evento.preventDefault();
  }

  @HostListener('window:pointerup')
  terminarArrastre(): void {
    this.accionArrastre = null;
  }

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  // --- Datos generales

  cambiarNombre(texto: string): void {
    this.nombre.set(texto);
    this.errorNombre.set('');
  }

  /** Arma las columnas de desde a hasta; lo cargado en horarios que siguen existiendo se conserva. */
  armarHorarios(): void {
    const inicio = aMinutos(this.desde());
    const fin = aMinutos(this.hasta());
    const paso = this.cada();
    if (!(fin >= inicio) || paso <= 0) {
      this.toast.error('Revisá el horario: el final tiene que ser después del comienzo.');
      return;
    }
    const nuevos: string[] = [];
    for (let m = inicio; m <= fin; m += paso) nuevos.push(aHora(m));
    const anteriores = this.horarios();
    this.puestos.update((l) =>
      l.map((p) => ({
        ...p,
        celdas: nuevos.map((h) => {
          const k = anteriores.indexOf(h);
          return k >= 0 ? (p.celdas[k] ?? '') : '';
        }),
      })),
    );
    this.horarios.set(nuevos);
  }

  // --- Puestos

  cambiarPuesto(i: number, cambios: Partial<Puesto>): void {
    this.puestos.update((l) => l.map((p, j) => (j === i ? { ...p, ...cambios } : p)));
  }

  agregarPuesto(): void {
    this.puestos.update((l) => [...l, { nombre: '', nota: null, celdas: this.horarios().map(() => '') }]);
    setTimeout(() => {
      const inputs = document.querySelectorAll<HTMLInputElement>('.nombre-puesto');
      inputs[inputs.length - 1]?.focus();
    });
  }

  moverPuesto(i: number, delta: number): void {
    const j = i + delta;
    this.puestos.update((l) => {
      if (j < 0 || j >= l.length) return l;
      const copia = [...l];
      [copia[i], copia[j]] = [copia[j], copia[i]];
      return copia;
    });
  }

  async quitarPuesto(i: number): Promise<void> {
    const p = this.puestos()[i];
    if (p.celdas.some((c) => c.trim())) {
      const ok = await this.confirmacion.pedir({
        titulo: 'Quitar puesto',
        mensaje: `¿Quitar ${p.nombre || 'este puesto'}?`,
        aceptar: 'Quitar',
        peligro: true,
      });
      if (!ok) return;
    }
    this.puestos.update((l) => l.filter((_, j) => j !== i));
  }

  // --- Pintar

  elegir(h: Herramienta): void {
    this.herramienta.set(h);
  }

  esElegido(h: Herramienta): boolean {
    const actual = this.herramienta();
    if (actual.tipo !== h.tipo) return false;
    return actual.tipo !== 'numero' || (h.tipo === 'numero' && actual.numero === h.numero);
  }

  /** Decide qué hace el toque según la celda tocada; el arrastre repite lo mismo. */
  empezarPintar(evento: PointerEvent, fila: number, columna: number): void {
    if (this.editando() || evento.button !== 0) return;
    evento.preventDefault();
    (evento.target as HTMLElement).releasePointerCapture?.(evento.pointerId);
    const celda = this.puestos()[fila].celdas[columna] ?? '';
    const h = this.herramienta();
    let accion: Accion;
    if (h.tipo === 'borrar') {
      accion = () => '';
    } else if (h.tipo === 'cerrado') {
      accion = esCerrado(celda) ? () => '' : () => 'Cerrado';
    } else {
      const n = h.numero;
      const tiene = numerosDe(celda).includes(n);
      const sumar = this.sumar() || evento.shiftKey || evento.ctrlKey;
      if (tiene) {
        accion = (c) => numerosDe(c).filter((x) => x !== n).join('-');
      } else if (sumar) {
        accion = (c) => [...new Set([...numerosDe(c), n])].sort((a, b) => a - b).join('-');
      } else {
        accion = () => String(n);
      }
    }
    this.accionArrastre = accion;
    this.pintar(fila, columna);
  }

  seguirPintando(fila: number, columna: number): void {
    if (this.accionArrastre) this.pintar(fila, columna);
  }

  private pintar(fila: number, columna: number): void {
    const accion = this.accionArrastre!;
    this.puestos.update((l) =>
      l.map((p, i) => (i === fila ? { ...p, celdas: p.celdas.map((c, k) => (k === columna ? accion(c) : c)) } : p)),
    );
  }

  /** Doble toque: escribir a mano. */
  escribir(fila: number, columna: number): void {
    this.accionArrastre = null;
    this.editando.set({ fila, columna });
    setTimeout(() => document.querySelector<HTMLInputElement>('.celda-escrita')?.select());
  }

  terminarEscritura(texto: string): void {
    const e = this.editando();
    if (!e) return;
    this.editando.set(null);
    const limpio = texto.replace(/\s/g, '');
    this.puestos.update((l) =>
      l.map((p, i) =>
        i === e.fila ? { ...p, celdas: p.celdas.map((c, k) => (k === e.columna ? (esCerrado(limpio) ? 'Cerrado' : limpio) : c)) } : p,
      ),
    );
  }

  estaEditando(fila: number, columna: number): boolean {
    const e = this.editando();
    return !!e && e.fila === fila && e.columna === columna;
  }

  // --- Guardar y salir

  async volver(): Promise<void> {
    if (this.hayCambios()) {
      const ok = await this.confirmacion.pedir({
        titulo: 'Salir sin guardar',
        mensaje: 'Hay cambios sin guardar. ¿Salir igual?',
        aceptar: 'Salir',
        cancelar: 'Seguir editando',
        peligro: true,
      });
      if (!ok) return;
    }
    this.guardado = this.estado();
    // El diálogo saca su entrada del historial al cerrarse (un "atrás" que el router también procesa):
    // navegar recién cuando eso terminó, si no ese "atrás" pisa esta navegación.
    setTimeout(() => this.atras.cuandoTermineDeCerrar(() => setTimeout(() => this.router.navigate(['/cronogramas']))));
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
        afluencia: this.afluencia(),
        horarios: this.horarios(),
        puestos: this.puestos().filter((p) => p.nombre.trim() || p.celdas.some((c) => c.trim())),
      })
      .subscribe({
        next: (c) => {
          this.guardado = this.estado();
          this.toast.exito('Cronograma guardado.');
          this.router.navigate(['/cronogramas', c.id]);
        },
        error: (e) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e, 'No se pudo guardar.'));
        },
      });
  }

  private estado(): string {
    return JSON.stringify([this.nombre(), this.afluencia(), this.horarios(), this.puestos()]);
  }
}
