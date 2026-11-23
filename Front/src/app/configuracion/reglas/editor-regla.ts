import { Component, computed, input, OnInit, output, signal } from '@angular/core';
import { LucideChevronDown, LucideChevronUp, LucideMinus, LucidePlus, LucideX } from '@lucide/angular';
import { AFLUENCIAS, Afluencia, textoAfluencia } from '../../models/cronograma';
import { clavePuesto } from '../../models/empleado';
import { PuestoParque, TIPOS_HORARIO, TipoHorario } from '../../models/puesto';
import { HorarioRegla, ReglaApertura } from '../../models/regla';
import { Modal } from '../../shared/modal/modal';

/**
 * Crear o cambiar una regla de puestos abiertos, sin escribir: los puestos se eligen de la lista, la gente de
 * los fijos con − y +, y la rueda en orden (el primero es donde arranca). Muestra cuántas personas ocupa y no deja
 * guardar si no da la cantidad, si un puesto se repite o si ya hay otra regla igual.
 */
@Component({
  selector: 'app-editor-regla',
  imports: [Modal, LucideChevronDown, LucideChevronUp, LucideMinus, LucidePlus, LucideX],
  templateUrl: './editor-regla.html',
  styleUrl: './editor-regla.css',
})
export class EditorRegla implements OnInit {
  /** La regla de partida (con id si se está cambiando una que existe). */
  regla = input.required<ReglaApertura>();
  catalogo = input.required<PuestoParque[]>();
  /** Todas las reglas, para avisar si ya hay una con esa cantidad y afluencia. */
  reglas = input<ReglaApertura[]>([]);
  guardando = input(false);

  guardar = output<ReglaApertura>();
  cancelar = output<void>();

  readonly afluencias = AFLUENCIAS;
  readonly textoAfluencia = textoAfluencia;

  empleados = signal(1);
  afluencia = signal<Afluencia>('MEDIA');
  /** abre: si abre tarde (Tirolesa), desde qué hora; vacío = todo el día. */
  fijos = signal<{ puesto: string; personas: number; abre?: string | null }[]>([]);
  lugares = signal<string[][]>([]);
  horarios = signal<HorarioRegla[]>([]);
  readonly tiposHorario = TIPOS_HORARIO;

  esNueva = computed(() => this.regla().id == null);

  ngOnInit(): void {
    const r = this.regla();
    this.empleados.set(r.empleados);
    this.afluencia.set(r.afluencia);
    this.fijos.set(r.fijos.map((f) => ({ ...f })));
    this.lugares.set(r.lugares.map((l) => [...l]));
    this.horarios.set((r.horarios ?? []).map((h) => ({ ...h })));
  }

  /** Los puestos que todavía no están en la regla. */
  disponibles = computed(() => {
    const usados = new Set([...this.fijos().map((f) => f.puesto), ...this.lugares().flat()].map(clavePuesto));
    return this.catalogo()
      .map((p) => p.nombre)
      .filter((n) => !usados.has(clavePuesto(n)))
      .sort((a, b) => a.localeCompare(b));
  });

  /** Para un lugar: puede sumar los libres y también los de otro lugar (Búsqueda, mitad y mitad). */
  paraSumar(i: number): string[] {
    const fijos = new Set(this.fijos().map((f) => clavePuesto(f.puesto)));
    const enEste = new Set(this.lugares()[i].map(clavePuesto));
    return this.catalogo()
      .map((p) => p.nombre)
      .filter((n) => !fijos.has(clavePuesto(n)) && !enEste.has(clavePuesto(n)))
      .sort((a, b) => a.localeCompare(b));
  }

  /** La hora con que se propone abrir tarde: la que tiene en Puestos, o las 13. */
  private horaSugerida(puesto: string): string {
    const p = this.catalogo().find((x) => clavePuesto(x.nombre) === clavePuesto(puesto));
    return p?.abreTarde?.tipo === 'ABRE_TARDE' ? p.abreTarde.abre : '13:00';
  }

  /** Desde qué hora abre ese fijo; vacío = todo el día. */
  cambiarAbre(i: number, abre: string | null): void {
    this.fijos.update((l) => l.map((f, j) => (j === i ? { ...f, abre: abre || null } : f)));
  }

  abreTarde(i: number): void {
    this.cambiarAbre(i, this.horaSugerida(this.fijos()[i].puesto));
  }

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  enFijos = computed(() => this.fijos().reduce((s, f) => s + f.personas, 0));
  ocupa = computed(() => this.enFijos() + this.lugares().length);
  /** Cuánto se llena la barra de abajo (0 a 100). */
  lleno = computed(() => Math.min(100, Math.round((this.ocupa() / Math.max(1, this.empleados() || 1)) * 100)));

  /** El renglón de la rueda en el que se está sumando otro puesto (se abre con el +). */
  sumandoEn = signal<number | null>(null);


  // --- Horarios (lo que cambia en el día)

  /** Todos los puestos, para elegir el de un horario. */
  todosLosPuestos = computed(() => this.catalogo().map((p) => p.nombre).sort((a, b) => a.localeCompare(b)));

  agregarHorario(evento: Event): void {
    const select = evento.target as HTMLSelectElement;
    const puesto = select.value;
    select.value = '';
    if (!puesto) return;
    // Un puesto fijo con varios suele perder gente (Cajero); el resto, abrir en un horario (Show, Fotos).
    const fijo = this.fijos().find((f) => clavePuesto(f.puesto) === clavePuesto(puesto));
    const tipo: TipoHorario = fijo && fijo.personas > 1 ? 'MENOS' : 'ABRE_TARDE';
    this.horarios.update((l) => [...l, { puesto, tipo, personas: 1, desde: '16:00', hasta: null }]);
  }

  cambiarHorario(i: number, cambios: Partial<HorarioRegla>): void {
    this.horarios.update((l) => l.map((h, j) => (j === i ? { ...h, ...cambios } : h)));
  }

  personasHorario(i: number, delta: number): void {
    this.horarios.update((l) => l.map((h, j) => (j === i ? { ...h, personas: Math.min(10, Math.max(1, h.personas + delta)) } : h)));
  }

  quitarHorario(i: number): void {
    this.horarios.update((l) => l.filter((_, j) => j !== i));
  }

  /** Lo que falta arreglar para poder guardar. */
  problemas = computed(() => {
    const lista: string[] = [];
    const n = this.empleados();
    if (!Number.isInteger(n) || n < 1 || n > 60) lista.push('La cantidad de empleados tiene que ser de 1 a 60.');
    const otra = this.reglas().find((r) => r.id !== this.regla().id && r.empleados === n && r.afluencia === this.afluencia());
    if (otra) lista.push(`Ya hay una regla para ${n} con afluencia ${textoAfluencia(this.afluencia()).toLowerCase()}.`);
    if (!this.fijos().length && !this.lugares().length) lista.push('Agregá algún puesto.');
    for (const h of this.horarios()) {
      if (!h.desde) lista.push(`${h.puesto}: falta desde qué hora.`);
      else if (h.hasta && h.hasta <= h.desde) lista.push(`${h.puesto}: tiene que terminar después de empezar.`);
      if (h.tipo === 'MENOS') {
        const fijo = this.fijos().find((f) => clavePuesto(f.puesto) === clavePuesto(h.puesto));
        if (!fijo || fijo.personas - h.personas < 1) lista.push(`${h.puesto}: para sacarle ${h.personas}, tiene que ser fijo con más gente.`);
      }
    }
    const ocupa = this.ocupa();
    if (ocupa !== n) {
      const dif = Math.abs(ocupa - n);
      lista.push(
        ocupa > n
          ? `Ocupa ${ocupa} personas y vinieron ${n}: sobran ${dif} ${dif === 1 ? 'lugar' : 'lugares'}.`
          : `Ocupa ${ocupa} personas y vinieron ${n}: ${dif === 1 ? 'queda 1 persona' : `quedan ${dif} personas`} sin lugar.`,
      );
    }
    return lista;
  });

  // --- Cantidad y afluencia (sólo en una regla nueva)

  cambiarEmpleados(evento: Event): void {
    this.empleados.set(Number((evento.target as HTMLInputElement).value));
  }

  // --- Fijos

  agregarFijo(evento: Event): void {
    const select = evento.target as HTMLSelectElement;
    const puesto = select.value;
    select.value = '';
    if (!puesto) return;
    // Un puesto que abre tarde (Tirolesa) entra ya con su hora.
    const p = this.catalogo().find((x) => clavePuesto(x.nombre) === clavePuesto(puesto));
    const abre = p?.abreTarde?.tipo === 'ABRE_TARDE' ? p.abreTarde.abre : null;
    this.fijos.update((l) => [...l, { puesto, personas: 1, abre }]);
  }

  personas(i: number, delta: number): void {
    this.fijos.update((l) => l.map((f, j) => (j === i ? { ...f, personas: Math.min(5, Math.max(1, f.personas + delta)) } : f)));
  }

  quitarFijo(i: number): void {
    this.fijos.update((l) => l.filter((_, j) => j !== i));
  }

  // --- Rueda

  agregarLugar(evento: Event): void {
    const select = evento.target as HTMLSelectElement;
    const puesto = select.value;
    select.value = '';
    if (puesto) this.lugares.update((l) => [...l, [puesto]]);
  }

  /** Otro puesto que hace la misma persona de ese lugar. */
  sumarAlLugar(i: number, evento: Event): void {
    const select = evento.target as HTMLSelectElement;
    const puesto = select.value;
    select.value = '';
    if (puesto) this.lugares.update((l) => l.map((x, j) => (j === i ? [...x, puesto] : x)));
    this.sumandoEn.set(null);
  }

  quitarDelLugar(i: number, puesto: string): void {
    this.lugares.update((l) => l.map((x, j) => (j === i ? x.filter((p) => p !== puesto) : x)).filter((x) => x.length));
  }

  mover(i: number, delta: number): void {
    this.lugares.update((l) => {
      const j = i + delta;
      if (j < 0 || j >= l.length) return l;
      const copia = [...l];
      [copia[i], copia[j]] = [copia[j], copia[i]];
      return copia;
    });
  }

  /** La rueda arranca en este lugar: se gira la lista sin cambiar el orden en que se recorre. */
  arrancarAca(i: number): void {
    this.lugares.update((l) => [...l.slice(i), ...l.slice(0, i)]);
  }

  confirmar(): void {
    if (this.problemas().length) return;
    this.guardar.emit({
      id: this.regla().id,
      empleados: this.empleados(),
      afluencia: this.afluencia(),
      fijos: this.fijos(),
      lugares: this.lugares(),
      horarios: this.horarios(),
    });
  }
}
