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
import { ReglaApertura, personasDe, textoHorario } from '../../models/regla';
import { clavePuesto } from '../../models/empleado';
import { PuestoParque } from '../../models/puesto';
import { mensajeDeError } from '../../services/cronograma.service';
import { ReglaService } from '../../services/regla.service';
import { PuestoService } from '../../services/puesto.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { ToastService } from '../../shared/toast/toast.service';
import { EditorRegla } from './editor-regla';

/** Una celda de la tabla de reglas: qué es ese puesto con esa cantidad de gente. */
interface Celda {
  texto: string;
  clase: 'fijo' | 'tarde' | 'rota' | 'junto' | 'cerrado';
  inicia: boolean;
  /** Para leerlo al pasar el mouse. */
  detalle: string;
}

interface FilaRegla {
  regla: ReglaApertura;
  /**
   * Lo de cada columna. Los puestos que hace una misma persona van en una celda ancha (span) con una barra "Rota"
   * por persona, que abarca sus puestos (desde/hasta: columnas dentro de la celda, desde 1).
   */
  segmentos: { span: number; celda: Celda; puestos: string; barras?: { desde: number; hasta: number; inicia: boolean; detalle: string }[] }[];
  mal: boolean;
  /** Lo que cambia en el día, por hora: "16:00" "Cajero −1". */
  horarios: { hora: string; texto: string; tipo: string; detalle: string }[];
}


/** Qué puestos se abren según cuántos empleados hay y la afluencia. El generador arma la rueda con esto. */
@Component({
  selector: 'app-reglas',
  imports: [
    EditorRegla,
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
  /** La regla que se está creando o cambiando (en el editor). */
  editando = signal<ReglaApertura | null>(null);

  /** Las cantidades de empleados que tienen alguna regla, de menor a mayor. */
  cantidades = computed(() => [...new Set(this.reglas().map((r) => r.empleados))].sort((a, b) => a - b));

  ngOnInit(): void {
    this.cargar();
    this.api.ordenRotacion().subscribe({ next: (o) => this.orden.set(o), error: () => this.orden.set([]) });
    this.puestosApi.listar().subscribe({
      next: (l) => {
        this.puestosParque.set(l);
        this.catalogo.set(l.map((p) => p.nombre));
      },
      error: () => {},
    });
  }

  // --- Orden de rotación (se arrastra)

  /** El orden de rotación predeterminado, como se está editando. */
  orden = signal<string[]>([]);
  catalogo = signal<string[]>([]);
  ordenCambiado = signal(false);
  guardandoOrden = signal(false);
  /** El que se está arrastrando (su posición en la lista). */
  arrastrando = signal<number | null>(null);

  /** "Entrada → Canilla → Minigolf…", para verlo con el orden plegado. */
  resumenOrden = computed(() => this.orden().join(' → '));

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

  // --- Tabla: una afluencia a la vez, una fila por cantidad de empleados y una columna por puesto.

  vista = signal<Afluencia>('MEDIA');
  private puestosParque = signal<PuestoParque[]>([]);
  /** Los puestos con su horario, para el editor. */
  catalogoCompleto = computed(() => this.puestosParque());

  /** Si el puesto abre tarde (Tirolesa): en la regla va con su gente. */
  private esDeTarde(puesto: string): boolean {
    return this.puestosParque().some((p) => clavePuesto(p.nombre) === clavePuesto(puesto) && p.abreTarde?.tipo === 'ABRE_TARDE');
  }

  /** Las columnas: primero los fijos, después la Tirolesa, después la rueda en el orden de rotación. */
  columnas = computed(() => {
    const reglas = this.reglas().filter((r) => r.afluencia === this.vista());
    const fijos: string[] = [];
    const tarde: string[] = [];
    const rueda: string[] = [];
    const vistos = new Set<string>();
    const sumar = (lista: string[], p: string) => {
      if (vistos.has(clavePuesto(p))) return;
      vistos.add(clavePuesto(p));
      lista.push(p);
    };
    for (const r of reglas) for (const f of r.fijos) sumar(f.abre || this.esDeTarde(f.puesto) ? tarde : fijos, f.puesto);
    for (const r of reglas) for (const l of r.lugares) for (const p of l) sumar(rueda, p);
    const orden = this.orden().map(clavePuesto);
    const lugar = (p: string) => {
      const i = orden.indexOf(clavePuesto(p));
      return i < 0 ? orden.length : i;
    };
    rueda.sort((a, b) => lugar(a) - lugar(b) || a.localeCompare(b));
    return [...fijos, ...tarde, ...rueda];
  });

  filas = computed<FilaRegla[]>(() =>
    this.reglas()
      .filter((r) => r.afluencia === this.vista())
      .sort((a, b) => a.empleados - b.empleados)
      .map((r) => {
        const celdas = new Map<string, Celda>();
        for (const f of r.fijos) {
          const tarde = !!f.abre || this.esDeTarde(f.puesto);
          celdas.set(clavePuesto(f.puesto), {
            texto: tarde ? `×${f.personas}${f.abre ? ' · ' + f.abre : ''}` : f.personas > 1 ? `Fijo ×${f.personas}` : 'Fijo',
            clase: tarde ? 'tarde' : 'fijo',
            inicia: false,
            detalle: tarde
              ? `${f.personas} en ${f.puesto}${f.abre ? ' desde las ' + f.abre : ' cuando abre'}; antes cubren el parque`
              : `${f.personas === 1 ? 'Una persona' : f.personas + ' personas'} todo el día`,
          });
        }
        // Los lugares de un solo puesto: "Rota".
        r.lugares.forEach((l, i) => {
          if (l.length !== 1 || celdas.has(clavePuesto(l[0]))) return;
          celdas.set(clavePuesto(l[0]), { texto: 'Rota', clase: 'rota', inicia: i === 0, detalle: 'Entra en la rueda' });
        });
        // Los que hace una misma persona se juntan en bloques; si dos lugares comparten un puesto (Búsqueda, mitad
        // con Travesía y mitad con Laberinto), van en el mismo bloque: "2 personas" para los tres.
        const bloques: { puestos: Set<string>; nombres: string[]; lugares: string[][]; inicia: boolean; arrancan: boolean[] }[] = [];
        r.lugares.forEach((l, i) => {
          if (l.length < 2) return;
          const claves = l.map(clavePuesto);
          const tocan = bloques.filter((b) => claves.some((c) => b.puestos.has(c)));
          const b = { puestos: new Set(claves), nombres: [...l], lugares: [l], inicia: i === 0, arrancan: [i === 0] };
          for (const t of tocan) {
            t.puestos.forEach((c) => b.puestos.add(c));
            t.nombres.forEach((n) => b.nombres.includes(n) || b.nombres.push(n));
            b.lugares.push(...t.lugares);
            b.arrancan.push(...t.arrancan);
            b.inicia ||= t.inicia;
            bloques.splice(bloques.indexOf(t), 1);
          }
          bloques.push(b);
        });
        const columnas = this.columnas();
        const enColumnas = columnas.map(clavePuesto);
        const segmentos: FilaRegla['segmentos'] = [];
        for (let i = 0; i < columnas.length; i++) {
          const clave = enColumnas[i];
          const b = bloques.find((x) => x.puestos.has(clave));
          if (b) {
            // Las columnas del bloque tienen que estar seguidas para ir en una sola celda.
            const span = b.puestos.size;
            const seguidas = enColumnas.slice(i, i + span).every((c) => b.puestos.has(c));
            const personas = b.lugares.length;
            const detalle =
              personas === 1
                ? `Una persona hace ${unir(b.nombres)}`
                : `${personas} personas: ` + b.lugares.map((l) => unir(l)).join('; ');
            if (seguidas) {
              // Una barra por persona, sobre las columnas de sus puestos (en orden de columnas).
              const delBloque = enColumnas.slice(i, i + span);
              const barras = b.lugares
                .map((l, j) => {
                  const pos = l.map((p) => delBloque.indexOf(clavePuesto(p)) + 1).filter((x) => x > 0);
                  return { desde: Math.min(...pos), hasta: Math.max(...pos), inicia: b.arrancan[j], detalle: `Una persona hace ${unir(l)}` };
                })
                .sort((x, y) => x.desde - y.desde || x.hasta - y.hasta);
              segmentos.push({ span, celda: { texto: 'Rota', clase: 'junto', inicia: b.inicia, detalle }, puestos: unir(b.nombres), barras });
              i += span - 1;
              continue;
            }
            const otros = b.nombres.filter((n) => clavePuesto(n) !== clave);
            segmentos.push({ span: 1, celda: { texto: `Rota con ${otros.join(', ')}`, clase: 'rota', inicia: b.inicia, detalle }, puestos: columnas[i] });
            continue;
          }
          segmentos.push({ span: 1, celda: celdas.get(clave) ?? this.cerrado, puestos: columnas[i] });
        }
        const horarios = [...(r.horarios ?? [])]
          .sort((a, b) => a.desde.localeCompare(b.desde))
          .map((h) => ({
            hora: h.hasta ? `${h.desde}–${h.hasta}` : h.desde,
            texto: `${h.puesto} ${h.tipo === 'MENOS' ? '−' + h.personas : h.tipo === 'REFUERZO' ? '+' + h.personas : '· ' + h.personas}`,
            tipo: h.tipo.toLowerCase(),
            detalle: textoHorario(h),
          }));
        return { regla: r, segmentos, mal: personasDe(r) !== r.empleados, horarios };
      }),
  );

  readonly cerrado: Celda = { texto: '—', clase: 'cerrado', inicia: false, detalle: 'Cerrado' };

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

  /**
   * Abre el editor. Una regla que no existe arranca como copia de la más parecida (la misma afluencia y la
   * cantidad más cercana), así sólo se cambia lo que haga falta.
   */
  empezar(empleados: number, afluencia: Afluencia): void {
    const r = this.regla(empleados, afluencia);
    if (r) {
      this.editando.set(structuredClone(r));
      return;
    }
    const parecida = this.reglas()
      .filter((x) => x.afluencia === afluencia)
      .sort((x, y) => Math.abs(x.empleados - empleados) - Math.abs(y.empleados - empleados))[0];
    this.editando.set({
      id: null,
      empleados,
      afluencia,
      fijos: parecida ? parecida.fijos.map((f) => ({ ...f })) : [{ puesto: 'Cajero', personas: 1 }],
      lugares: parecida ? parecida.lugares.map((l) => [...l]) : [],
    });
  }

  nueva(): void {
    const max = this.filas().at(-1)?.regla.empleados ?? this.cantidades().at(-1) ?? 4;
    this.empezar(max + 1, this.vista());
  }

  guardando = signal(false);

  guardar(r: ReglaApertura): void {
    this.guardando.set(true);
    this.api.guardar(r).subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(null);
        this.vista.set(r.afluencia);
        this.toast.exito('Regla guardada.');
        this.cargar();
      },
      error: (err) => {
        this.guardando.set(false);
        this.toast.error(mensajeDeError(err, 'No se pudo guardar.'));
      },
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

/** "A", "A y B", "A, B y C". */
function unir(partes: string[]): string {
  return partes.length <= 1 ? partes.join('') : `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`;
}
