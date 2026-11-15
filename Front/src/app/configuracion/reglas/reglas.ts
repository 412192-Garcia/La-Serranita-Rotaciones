import { Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  LucideFileUp,
  LucideGripVertical,
  LucideLoaderCircle,
  LucidePencil,
  LucidePlus,
  LucideTrash,
  LucideTriangleExclamationPoint,
  LucideX,
} from '@lucide/angular';
import { AFLUENCIAS, Afluencia, textoAfluencia } from '../../models/cronograma';
import { ReglaApertura, personasDe } from '../../models/regla';
import { mensajeDeError } from '../../services/cronograma.service';
import { ReglaService } from '../../services/regla.service';
import { PuestoService } from '../../services/puesto.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { ToastService } from '../../shared/toast/toast.service';

/** Lo que se está escribiendo de una regla: los fijos y la rueda como texto. */
interface Edicion {
  id: number | null;
  empleados: number;
  afluencia: Afluencia;
  fijos: string;
  lugares: string;
}

/** Qué puestos se abren según cuántos empleados hay y la afluencia. El generador arma la rueda con esto. */
@Component({
  selector: 'app-reglas',
  imports: [
    LucideFileUp,
    LucideGripVertical,
    LucideLoaderCircle,
    LucidePencil,
    LucidePlus,
    LucideTrash,
    LucideTriangleExclamationPoint,
    LucideX,
  ],
  templateUrl: './reglas.html',
  styleUrl: './reglas.css',
})
export class Reglas implements OnInit {
  private api = inject(ReglaService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);
  private puestosApi = inject(PuestoService);

  readonly afluencias = AFLUENCIAS;
  readonly textoAfluencia = textoAfluencia;
  readonly personasDe = personasDe;

  cargando = signal(true);
  importando = signal(false);
  reglas = signal<ReglaApertura[]>([]);
  editando = signal<Edicion | null>(null);

  /** Las cantidades de empleados que tienen alguna regla, de menor a mayor. */
  cantidades = computed(() => [...new Set(this.reglas().map((r) => r.empleados))].sort((a, b) => a - b));

  ngOnInit(): void {
    this.cargar();
    this.api.ordenRotacion().subscribe({ next: (o) => this.orden.set(o), error: () => this.orden.set([]) });
    this.puestosApi.listar().subscribe({ next: (l) => this.catalogo.set(l.map((p) => p.nombre)), error: () => {} });
  }

  // --- Orden de rotación (se arrastra)

  /** El orden de rotación predeterminado, como se está editando. */
  orden = signal<string[]>([]);
  catalogo = signal<string[]>([]);
  ordenCambiado = signal(false);
  guardandoOrden = signal(false);
  /** El que se está arrastrando (su posición en la lista). */
  arrastrando = signal<number | null>(null);

  /** Los puestos que todavía no están en el orden, para agregarlos. */
  fueraDelOrden = computed(() => this.catalogo().filter((p) => !this.orden().includes(p)));

  tomar(evento: PointerEvent, i: number): void {
    evento.preventDefault();
    (evento.currentTarget as HTMLElement).setPointerCapture(evento.pointerId);
    this.arrastrando.set(i);
  }

  /** Mientras se arrastra: el que se lleva pasa al lugar del que tiene debajo. */
  mover(evento: PointerEvent): void {
    const desde = this.arrastrando();
    if (desde === null) return;
    const debajo = document.elementFromPoint(evento.clientX, evento.clientY)?.closest('[data-orden]') as HTMLElement | null;
    if (!debajo) return;
    const hasta = Number(debajo.dataset['orden']);
    if (Number.isNaN(hasta) || hasta === desde) return;
    this.orden.update((l) => {
      const copia = [...l];
      const [x] = copia.splice(desde, 1);
      copia.splice(hasta, 0, x);
      return copia;
    });
    this.arrastrando.set(hasta);
    this.ordenCambiado.set(true);
  }

  soltar(): void {
    this.arrastrando.set(null);
  }

  agregarAlOrden(evento: Event): void {
    const select = evento.target as HTMLSelectElement;
    const puesto = select.value;
    select.value = '';
    if (!puesto) return;
    this.orden.update((l) => [...l, puesto]);
    this.ordenCambiado.set(true);
  }

  quitarDelOrden(i: number): void {
    this.orden.update((l) => l.filter((_, j) => j !== i));
    this.ordenCambiado.set(true);
  }

  guardarOrden(): void {
    this.guardandoOrden.set(true);
    this.api.guardarOrdenRotacion(this.orden()).subscribe({
      next: (o) => {
        this.orden.set(o);
        this.ordenCambiado.set(false);
        this.guardandoOrden.set(false);
        this.toast.exito('Orden guardado: la rueda de todas las reglas sigue este orden.');
        this.cargar();
      },
      error: (e) => {
        this.guardandoOrden.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo guardar el orden.'));
      },
    });
  }

  regla(empleados: number, afluencia: Afluencia): ReglaApertura | undefined {
    return this.reglas().find((r) => r.empleados === empleados && r.afluencia === afluencia);
  }

  textoFijos(r: ReglaApertura): string {
    return r.fijos.map((f) => (f.personas > 1 ? `${f.puesto} x${f.personas}` : f.puesto)).join(', ');
  }

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  importar(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo) return;
    this.importando.set(true);
    this.api.importar(archivo).subscribe({
      next: (r) => {
        this.importando.set(false);
        this.toast.exito(`Se cargaron ${r.reglas} reglas.`);
        r.avisos.forEach((a) => this.toast.error(a));
        this.cargar();
      },
      error: (e) => {
        this.importando.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo importar el archivo.'));
      },
    });
  }

  async confirmarImportar(selector: HTMLInputElement): Promise<void> {
    if (this.reglas().length) {
      const ok = await this.confirmacion.pedir({
        titulo: 'Importar reglas',
        mensaje: 'Las reglas del Excel reemplazan a todas las que hay ahora.',
        aceptar: 'Elegir archivo',
      });
      if (!ok) return;
    }
    selector.click();
  }

  empezar(empleados: number, afluencia: Afluencia): void {
    const r = this.regla(empleados, afluencia);
    this.editando.set({
      id: r?.id ?? null,
      empleados,
      afluencia,
      fijos: r ? this.textoFijos(r) : 'Cajero, Baños',
      lugares: r ? r.lugares.map((l) => l.join(' + ')).join('\n') : '',
    });
  }

  nueva(): void {
    const max = this.cantidades().at(-1) ?? 4;
    this.empezar(max + 1, 'MEDIA');
  }

  cambiar(cambios: Partial<Edicion>): void {
    this.editando.update((e) => (e ? { ...e, ...cambios } : e));
  }

  /** "Cajero x2, Baños" → fijos; una línea por lugar, con + para los que hace la misma persona. */
  private leer(e: Edicion): ReglaApertura {
    const fijos = e.fijos
      .split(',')
      .map((x) => x.trim())
      .filter((x) => x)
      .map((x) => {
        const m = x.match(/^(.*?)\s*x\s*(\d+)$/i);
        return m ? { puesto: m[1].trim(), personas: Number(m[2]) } : { puesto: x, personas: 1 };
      });
    const lugares = e.lugares
      .split(/\n|;/)
      .map((l) => l.split('+').map((x) => x.trim()).filter((x) => x))
      .filter((l) => l.length);
    return { id: e.id, empleados: e.empleados, afluencia: e.afluencia, fijos, lugares };
  }

  /** Cuántas personas ocupa lo que se está escribiendo. */
  ocupa = computed(() => {
    const e = this.editando();
    return e ? personasDe(this.leer(e)) : 0;
  });

  guardar(): void {
    const e = this.editando();
    if (!e) return;
    this.api.guardar(this.leer(e)).subscribe({
      next: () => {
        this.editando.set(null);
        this.toast.exito('Regla guardada.');
        this.cargar();
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo guardar.')),
    });
  }

  async borrar(r: ReglaApertura): Promise<void> {
    const ok = await this.confirmacion.pedir({
      titulo: 'Borrar regla',
      mensaje: `¿Borrar la regla de ${r.empleados} empleados con afluencia ${textoAfluencia(r.afluencia).toLowerCase()}?`,
      aceptar: 'Borrar',
      peligro: true,
    });
    if (!ok) return;
    this.api.eliminar(r.id!).subscribe({
      next: () => {
        this.editando.set(null);
        this.cargar();
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo borrar.')),
    });
  }

  private cargar(): void {
    this.api.listar().subscribe({
      next: (l) => {
        this.reglas.set(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }
}
