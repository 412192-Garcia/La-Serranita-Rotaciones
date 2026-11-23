import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  LucideArrowLeft,
  LucideChevronDown,
  LucideChevronUp,
  LucideLoaderCircle,
  LucideCheck,
  LucidePencil,
  LucidePlus,
  LucideSave,
  LucideTriangleExclamationPoint,
  LucideWandSparkles,
  LucideX,
} from '@lucide/angular';
import { AFLUENCIAS, CronogramaResumen } from '../../models/cronograma';
import { AbreTardeGenerador, Borrador, FijoGenerador, PedidoGenerador, pedidoDesde } from '../../models/generador';
import { PuestoParque, TIPOS_HORARIO, personasSegun, tipoHorario } from '../../models/puesto';
import { PuestoService } from '../../services/puesto.service';
import { DiaParaGenerar, DiaService } from '../../services/dia.service';
import { TareaService } from '../../services/tarea.service';
import { ReglaApertura, puestosDeReglasConHora, reglaPara } from '../../models/regla';
import { ReglaService } from '../../services/regla.service';
import { textoAfluencia } from '../../models/cronograma';
import { hoyLocal } from '../../shared/fecha';
import { BorradorService } from '../../services/borrador.service';
import { GrupoService } from '../../services/grupo.service';
import { GrupoPuestos } from '../../models/grupo';
import { clavePuesto, puedeHacer } from '../../models/empleado';
import { CronogramaService, mensajeDeError } from '../../services/cronograma.service';
import { ToastService } from '../../shared/toast/toast.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { AbreTardePuesto } from '../../models/puesto';
import { VistaCronograma } from '../../shared/vista-cronograma/vista-cronograma';

const VACIO: PedidoGenerador = {
  nombre: '',
  afluencia: 'MEDIA',
  empleados: 10,
  desde: '11:00',
  hasta: '18:00',
  cada: 30,
  descansoDesde: '12:00',
  descansoHasta: '13:30',
  // Si hay reglas de puestos abiertos, los fijos y la rueda salen de ahí.
  fijos: [
    { puesto: 'Cajero', personas: 2 },
    { puesto: 'Baños', personas: 1 },
  ],
  rueda: [],
  coberturas: [],
  // Los que abren tarde salen de Puestos (Tirolesa, Casa del zorrito) al cargar.
  abrenTarde: [],
};

/** Arma un borrador de cronograma para después corregirlo en el editor. */
@Component({
  selector: 'app-generador',
  imports: [
    RouterLink,
    NgTemplateOutlet,
    VistaCronograma,
    LucideArrowLeft,
    LucideChevronDown,
    LucideChevronUp,
    LucideLoaderCircle,
    LucideCheck,
    LucidePencil,
    LucidePlus,
    LucideSave,
    LucideTriangleExclamationPoint,
    LucideWandSparkles,
    LucideX,
  ],
  templateUrl: './generador.html',
  styleUrl: './generador.css',
})
export class Generador implements OnInit {
  private api = inject(CronogramaService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);
  private borradores = inject(BorradorService);
  private router = inject(Router);
  private gruposApi = inject(GrupoService);
  private puestosApi = inject(PuestoService);
  private dias = inject(DiaService);
  private reglasApi = inject(ReglaService);
  private ruta = inject(ActivatedRoute);

  readonly afluencias = AFLUENCIAS;
  readonly tiposHorario = TIPOS_HORARIO;
  readonly tipoHorario = tipoHorario;

  cronogramas = signal<CronogramaResumen[]>([]);
  puestosConocidos = signal<string[]>([]);
  grupos = signal<GrupoPuestos[]>([]);
  /** La lista de puestos, con los que abren tarde y su horario. */
  catalogo = signal<PuestoParque[]>([]);
  /** Con los que vinieron hoy (desde Armar el día): la cantidad de empleados es la de ellos. */
  dia = signal<DiaParaGenerar | null>(null);
  private tareasApi = inject(TareaService);
  /** Número → tareas de apertura ("Pasar la sopladora (con Beto)"), si se generó con los que vinieron. */
  tareas = signal<Map<number, string>>(new Map());
  /** Las reglas de puestos abiertos (Configuración). */
  reglas = signal<ReglaApertura[]>([]);
  /** La regla con la que se armaron los fijos y la rueda, y para cuántos empleados generales se buscó. */
  reglaUsada = signal<{ regla: ReglaApertura; generales: number } | null>(null);
  /** Se tocaron a mano los fijos o la rueda después de aplicar la regla. */
  aMano = signal(false);
  readonly textoAfluencia = textoAfluencia;
  pedido = signal<PedidoGenerador>(structuredClone(VACIO));
  generando = signal(false);
  error = signal('');
  borrador = signal<Borrador | null>(null);

  // --- Roles que nadie de los que vinieron puede hacer (Zorrito): se pregunta quién lo cubre.

  /** Los que se dejaron así ("puesto|rol"). */
  private rolesDejados = signal<Set<string>>(new Set());
  /** El primer rol sin nadie que falta resolver. */
  faltante = computed(() => (this.borrador()?.faltantes ?? []).find((f) => !this.rolesDejados().has(`${f.puesto}|${f.rol}`)) ?? null);
  /** A quién se puede elegir: cualquiera con número hoy. */
  elegibles = computed(() =>
    (this.borrador()?.asignacion ?? [])
      .filter((a) => a.empleadoId != null)
      .map((a) => ({ id: a.empleadoId!, texto: `${a.nombre} (${a.numero})` }))
      .sort((x, y) => x.texto.localeCompare(y.texto)),
  );
  elegidoParaRol = signal<number | null>(null);
  /** Puesto → horarios sin nadie (se pide un refuerzo). */
  refuerzos = computed(() => {
    const m = new Map<string, Set<number>>();
    for (const r of this.borrador()?.refuerzos ?? []) m.set(r.puesto, new Set(r.celdas));
    return m;
  });
  cubriendoRol = signal(false);

  elegirParaRol(evento: Event): void {
    const v = (evento.target as HTMLSelectElement).value;
    this.elegidoParaRol.set(v ? Number(v) : null);
  }

  cubrirRol(): void {
    const b = this.borrador();
    const f = this.faltante();
    const dia = this.dia();
    const empleadoId = this.elegidoParaRol();
    if (!b || !f || !dia || empleadoId == null) return;
    this.cubriendoRol.set(true);
    this.api
      .cubrirRol({
        cronograma: b.cronograma,
        avisos: b.avisos,
        asignacion: b.asignacion,
        presentes: dia.presentes,
        puesto: f.puesto,
        rol: f.rol,
        celdas: f.celdas,
        empleadoId,
      })
      .subscribe({
        next: (nuevo) => {
          this.borrador.set({ ...nuevo, deConfiguracion: b.deConfiguracion ?? [] });
          this.elegidoParaRol.set(null);
          this.cubriendoRol.set(false);
        },
        error: (e) => {
          this.cubriendoRol.set(false);
          this.toast.error(mensajeDeError(e, 'No se pudo cambiar.'));
        },
      });
  }

  dejarRol(): void {
    const f = this.faltante();
    if (!f) return;
    this.rolesDejados.update((s) => new Set([...s, `${f.puesto}|${f.rol}`]));
    this.elegidoParaRol.set(null);
  }

  // --- Modo simple (con los que vinieron): se genera solo y se cuenta en palabras.

  /** Lo que se ve arriba: qué abre, qué abre más tarde y qué queda cerrado hoy. */
  resumen = computed(() => {
    const p = this.pedido();
    const vistos = new Set<string>();
    const abiertos: string[] = [];
    for (const nombre of [...p.fijos.map((f) => f.puesto), ...p.rueda.flatMap((l) => l.puestos)]) {
      const clave = clavePuesto(nombre);
      if (!nombre.trim() || vistos.has(clave)) continue;
      vistos.add(clave);
      abiertos.push(nombre.trim());
    }
    const tarde = p.abrenTarde
      .filter((a) => a.puesto.trim() && a.tipo === 'ABRE_TARDE' && !vistos.has(clavePuesto(a.puesto)))
      .map((a) => {
        vistos.add(clavePuesto(a.puesto));
        return `${a.puesto.trim()} (${a.hasta ? `de ${a.abre} a ${a.hasta}` : `desde las ${a.abre}`})`;
      });
    const cerrados = this.catalogo()
      .map((x) => x.nombre)
      .filter((n) => !vistos.has(clavePuesto(n)))
      .sort((a, b) => a.localeCompare(b));
    return { abiertos, tarde, cerrados };
  });

  /** Los avisos del día que no son preguntas ni refuerzos (esos se muestran aparte). */
  avisosDelDia = computed(() =>
    (this.borrador()?.avisos ?? []).filter((a) => !a.startsWith('Nadie de los que vinieron puede ser') && !a.endsWith('pedí un refuerzo.')),
  );

  /** "Cajero a las 12:30" por cada puesto que pide refuerzo. */
  textosRefuerzo = computed(() => (this.borrador()?.refuerzos ?? []).map((r) => `${r.puesto} a las ${unir(r.horas)}`));

  /** Un cambio de arriba (horario, almuerzo) vuelve a generar. */
  cambiarYGenerar(cambios: Partial<PedidoGenerador>): void {
    this.cambiar(cambios);
    this.generar(false);
  }

  /** La afluencia cambia lo que abre (la regla) y cuántos van a lo que abre tarde; se vuelve a generar. */
  cambiarAfluenciaYGenerar(afluencia: PedidoGenerador['afluencia']): void {
    this.cambiarAfluencia(afluencia);
    if (!this.aMano() && this.reglas().length) this.pedido.set(this.conRegla(this.pedido()));
    this.generar(false);
  }

  /** Personas en puestos fijos: el resto rota. */
  enFijos = computed(() => this.pedido().fijos.reduce((s, f) => s + (f.puesto.trim() ? f.personas : 0), 0));
  /** Personas en los puestos que abren tarde. */
  enTarde = computed(() =>
    this.pedido().abrenTarde.reduce((s, a) => s + (a.puesto.trim() && a.tipo !== 'MENOS' ? a.personas : 0), 0),
  );
  puestosDeLaRueda = computed(() => this.pedido().rueda.flatMap((l) => l.puestos));

  ngOnInit(): void {
    const dia = this.ruta.snapshot.queryParamMap.get('dia') ? this.dias.paraGenerar() : null;
    this.dia.set(dia);
    this.api.listar().subscribe({ next: (l) => this.cronogramas.set(l) });
    this.api.puestos().subscribe({ next: (p) => this.puestosConocidos.set(p) });
    forkJoin({ grupos: this.gruposApi.listar(), catalogo: this.puestosApi.listar(), reglas: this.reglasApi.listar() }).subscribe({
      next: ({ grupos, catalogo, reglas }) => {
        this.grupos.set(grupos);
        this.catalogo.set(catalogo);
        this.reglas.set(reglas);
        this.pedido.set(this.propuesta(dia ? this.pedidoDelDia(dia) : structuredClone(VACIO)));
        // Con los que vinieron se genera solo: lo técnico queda en "Ajustes avanzados".
        if (dia) this.generar(false);
      },
      error: (e) => this.toast.error(mensajeDeError(e)),
    });
  }

  /** Los puestos que en alguna regla abren tarde (Tirolesa, Show, Fotos): no se proponen como fijos ni en la rueda. */
  private puestosDeTarde(): Set<string> {
    const s = puestosDeReglasConHora(this.reglas());
    for (const r of this.reglas()) for (const h of r.horarios ?? []) if (h.tipo === 'ABRE_TARDE') s.add(clavePuesto(h.puesto));
    return s;
  }

  /** Con reglas, los fijos y la rueda salen de la que corresponde; sin reglas, se completa el refuerzo como antes. */
  private propuesta(p: PedidoGenerador): PedidoGenerador {
    return this.reglas().length ? this.conRegla(p) : this.conFijosDeRefuerzo(p);
  }

  /**
   * Fijos y rueda según la regla de puestos abiertos para todos los que vinieron y esa afluencia. Lo que la regla
   * pone en un puesto que abre tarde (Tirolesa: 3) va a su horario, no a los fijos. Un refuerzo cuyo puesto esa
   * regla no abre queda sólo para su horario (abre tarde).
   */
  private conRegla(p: PedidoGenerador): PedidoGenerador {
    const regla = reglaPara(this.reglas(), p.empleados, p.afluencia);
    if (!regla) {
      this.reglaUsada.set(null);
      return this.conFijosDeRefuerzo(p);
    }
    this.reglaUsada.set({ regla, generales: p.empleados });
    this.aMano.set(false);
    const abiertos = new Set([...regla.fijos.map((f) => clavePuesto(f.puesto)), ...regla.lugares.flat().map(clavePuesto)]);
    // Todos los horarios salen de la regla: la Tirolesa (fijo con hora, con su gente) y los del día (Show, Fotos,
    // un cajero menos). Si la regla abre todo el día un puesto que tiene horario (Fotos con mucha gente), manda eso.
    const todoElDia = new Set(regla.lugares.flat().map(clavePuesto));
    const abrenTarde: AbreTardeGenerador[] = [
      ...regla.fijos
        .filter((f) => f.abre)
        .map((f) => ({ puesto: f.puesto, personas: f.personas, abre: f.abre!, hasta: null, tipo: 'ABRE_TARDE' as const })),
      ...(regla.horarios ?? [])
        .filter((h) => !(h.tipo === 'ABRE_TARDE' && todoElDia.has(clavePuesto(h.puesto))))
        .map((h) => ({
          puesto: h.puesto,
          personas: h.personas,
          abre: h.desde,
          hasta: h.hasta,
          // Un refuerzo de un puesto que la regla no abre queda sólo para su horario.
          tipo: h.tipo === 'REFUERZO' && !abiertos.has(clavePuesto(h.puesto)) ? ('ABRE_TARDE' as const) : h.tipo,
        })),
    ];
    return {
      ...p,
      fijos: regla.fijos.filter((f) => !f.abre).map((f) => ({ puesto: f.puesto, personas: f.personas })),
      rueda: regla.lugares.map((l, i) => ({ puestos: [...l], inicia: i === 0 })),
      abrenTarde,
    };
  }

  guardandoPredeterminado = signal(false);

  /**
   * Lo que está cargado pasa a ser la regla de esta cantidad y afluencia: fijos, rueda y horarios (la Tirolesa, que
   * abre tarde y sigue hasta el cierre, va como fijo con su hora).
   */
  async guardarPredeterminado(): Promise<void> {
    const p = this.pedido();
    const generales = p.empleados;
    const af = textoAfluencia(p.afluencia).toLowerCase();
    // En la regla, la Tirolesa (abre tarde y sigue hasta el cierre) va con su gente, como un puesto más.
    const tardeEnRegla = p.abrenTarde.filter((a) => a.puesto.trim() && a.tipo === 'ABRE_TARDE' && !a.hasta && a.abre <= '14:00');
    const horarios = p.abrenTarde.filter((a) => a.puesto.trim() && !tardeEnRegla.includes(a));
    const ok = await this.confirmacion.pedir({
      titulo: 'Guardar como predeterminado',
      mensaje: `Los fijos, la rueda y los horarios quedan en la regla de ${generales} empleados con afluencia ${af} (Configuración → Reglas).`,
      aceptar: 'Guardar',
    });
    if (!ok) return;

    const pedidos = [
      this.reglasApi.guardar({
        id: null,
        empleados: generales,
        afluencia: p.afluencia,
        fijos: [
          ...p.fijos.filter((f) => f.puesto.trim()).map((f) => ({ puesto: f.puesto.trim(), personas: f.personas })),
          ...tardeEnRegla.map((a) => ({ puesto: a.puesto.trim(), personas: a.personas, abre: a.abre })),
        ],
        lugares: p.rueda.map((l) => l.puestos).filter((l) => l.length),
        horarios: horarios.map((a) => ({ puesto: a.puesto.trim(), tipo: a.tipo, personas: a.personas, desde: a.abre, hasta: a.hasta })),
      }),
    ];
    this.guardandoPredeterminado.set(true);
    forkJoin(pedidos).subscribe({
      next: () => {
        this.reglasApi.listar().subscribe((reglas) => {
          this.reglas.set(reglas);
          const regla = reglaPara(reglas, generales, p.afluencia);
          if (regla) this.reglaUsada.set({ regla, generales });
          this.aMano.set(false);
          this.guardandoPredeterminado.set(false);
          this.toast.exito('Guardado: la próxima vez se propone así.');
        });
      },
      error: (e) => {
        this.guardandoPredeterminado.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo guardar.'));
      },
    });
  }

  /** Vuelve a armar fijos y rueda con la regla (después de cambiar la cantidad o la afluencia). */
  aplicarRegla(): void {
    this.pedido.set(this.conRegla(this.pedido()));
    this.borrador.set(null);
  }

  /** El puesto de un refuerzo que no está en la rueda ni en los fijos queda fijo con una persona (la de todo el día). */
  private conFijosDeRefuerzo(p: PedidoGenerador): PedidoGenerador {
    const ya = new Set([...p.fijos.map((f) => clavePuesto(f.puesto)), ...p.rueda.flatMap((l) => l.puestos.map(clavePuesto))]);
    const nuevos = p.abrenTarde.filter((a) => a.tipo === 'REFUERZO' && !ya.has(clavePuesto(a.puesto))).map((a) => ({ puesto: a.puesto, personas: 1 }));
    return { ...p, fijos: [...p.fijos, ...nuevos] };
  }

  /**
   * Lo que se propone con los que vinieron: fijos, los principales de cada uno que no rotan (dos cajeras
   * son Cajero x2); la rueda, los puestos de los grupos que saben (Parque) que no sean fijos ni abran tarde.
   */
  private pedidoDelDia(dia: DiaParaGenerar): PedidoGenerador {
    // Los horarios salen de la regla (propuesta()). Los que abren tarde no se proponen como fijos ni en la rueda.
    const abrenTarde: AbreTardeGenerador[] = [];
    const tarde = this.puestosDeTarde();
    const deGrupos = new Set(this.grupos().flatMap((g) => g.puestos.map(clavePuesto)));
    const porId = new Map(dia.empleados.map((e) => [e.id!, e]));
    const fijos = new Map<string, { puesto: string; personas: number }>();
    for (const p of dia.presentes) {
      const e = porId.get(p.empleadoId);
      for (const principal of e?.principales ?? []) {
        const clave = clavePuesto(principal);
        if (deGrupos.has(clave) || tarde.has(clave)) continue;
        if (p.puestosHoy && !p.puestosHoy.some((x) => clavePuesto(x) === clave)) continue;
        const f = fijos.get(clave) ?? { puesto: principal, personas: 0 };
        f.personas++;
        fijos.set(clave, f);
      }
    }
    const fijosClaves = new Set(fijos.keys());
    const sabenGrupo = new Set(dia.presentes.flatMap((p) => porId.get(p.empleadoId)?.puestos.map(clavePuesto) ?? []));
    const rueda: string[] = [];
    for (const g of this.grupos()) {
      if (!sabenGrupo.has(clavePuesto(g.nombre))) continue;
      for (const p of g.puestos) {
        const clave = clavePuesto(p);
        if (fijosClaves.has(clave) || tarde.has(clave) || rueda.some((x) => clavePuesto(x) === clave)) continue;
        rueda.push(p);
      }
    }
    const [anio, mes, d] = hoyLocal().split('-');
    return {
      ...structuredClone(VACIO),
      nombre: `Día ${d}/${mes}/${anio}`,
      afluencia: dia.afluencia,
      empleados: dia.presentes.length,
      fijos: [...fijos.values()],
      rueda: rueda.map((p, i) => ({ puestos: [p], inicia: i === 0 })),
      abrenTarde,
    };
  }

  /** Los que saben cada puesto, entre los que vinieron (para ayudar a decidir). */
  sabenHoy(puesto: string): number {
    const dia = this.dia();
    if (!dia || !puesto.trim()) return 0;
    const clave = clavePuesto(puesto);
    const porId = new Map(dia.empleados.map((e) => [e.id!, e]));
    return dia.presentes.filter((p) => {
      const e = porId.get(p.empleadoId);
      const puede = p.puestosHoy ?? (e ? puedeHacer(e) : []);
      return puede.some((x) => clavePuesto(x) === clave);
    }).length;
  }

  /** Número → nombre, si se generó con los que vinieron. */
  nombres = computed(() => {
    const mapa = new Map<number, string>();
    for (const a of this.borrador()?.asignacion ?? []) if (a.nombre) mapa.set(a.numero, a.nombre);
    return mapa;
  });

  /** Lleva el cronograma generado (con quién hace cada número) a Armar el día. */
  usarHoy(): void {
    const b = this.borrador();
    if (!b) return;
    this.dias.generado.set({ cronograma: b.cronograma, asignacion: new Map(b.asignacion.map((a) => [a.numero, a.empleadoId])) });
    this.router.navigate(['/']);
  }

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  partirDe(evento: Event): void {
    const id = Number((evento.target as HTMLSelectElement).value);
    if (!id) {
      this.pedido.set(this.propuesta(structuredClone(VACIO)));
      return;
    }
    this.api.obtener(id).subscribe({
      next: (c) => {
        this.reglaUsada.set(null);
        this.pedido.set(pedidoDesde(c));
        this.borrador.set(null);
      },
      error: (e) => this.toast.error(mensajeDeError(e)),
    });
  }

  cambiar(cambios: Partial<PedidoGenerador>): void {
    this.pedido.update((p) => ({ ...p, ...cambios }));
  }

  /** La afluencia elige otra regla (con sus fijos, rueda y horarios): "Aplicar la regla" o el modo simple la aplican. */
  cambiarAfluencia(afluencia: PedidoGenerador['afluencia']): void {
    this.pedido.update((p) => ({ ...p, afluencia }));
  }

  // --- Abren más tarde

  cambiarTarde(i: number, cambios: Partial<AbreTardeGenerador>): void {
    this.pedido.update((p) => ({ ...p, abrenTarde: p.abrenTarde.map((a, j) => (j === i ? { ...a, ...cambios } : a)) }));
  }

  agregarTarde(): void {
    this.pedido.update((p) => ({ ...p, abrenTarde: [...p.abrenTarde, { puesto: '', personas: 1, abre: '13:00', hasta: null, tipo: 'ABRE_TARDE' }] }));
  }

  quitarTarde(i: number): void {
    this.pedido.update((p) => ({ ...p, abrenTarde: p.abrenTarde.filter((_, j) => j !== i) }));
  }

  // --- Fijos

  cambiarFijo(i: number, cambios: Partial<{ puesto: string; personas: number }>): void {
    this.aMano.set(true);
    this.pedido.update((p) => ({ ...p, fijos: p.fijos.map((f, j) => (j === i ? { ...f, ...cambios } : f)) }));
  }

  agregarFijo(): void {
    this.aMano.set(true);
    this.pedido.update((p) => ({ ...p, fijos: [...p.fijos, { puesto: '', personas: 1 }] }));
  }

  quitarFijo(i: number): void {
    this.aMano.set(true);
    this.pedido.update((p) => ({ ...p, fijos: p.fijos.filter((_, j) => j !== i) }));
  }

  // --- Rueda

  textoLugar(puestos: string[]): string {
    return puestos.join(', ');
  }

  cambiarLugar(i: number, texto: string): void {
    this.aMano.set(true);
    const puestos = texto
      .split(',')
      .map((x) => x.trim())
      .filter((x) => x);
    this.pedido.update((p) => ({ ...p, rueda: p.rueda.map((l, j) => (j === i ? { ...l, puestos } : l)) }));
  }

  marcarInicio(i: number): void {
    this.aMano.set(true);
    this.pedido.update((p) => ({ ...p, rueda: p.rueda.map((l, j) => ({ ...l, inicia: j === i })) }));
  }

  moverLugar(i: number, delta: number): void {
    this.aMano.set(true);
    this.pedido.update((p) => {
      const j = i + delta;
      if (j < 0 || j >= p.rueda.length) return p;
      const rueda = [...p.rueda];
      [rueda[i], rueda[j]] = [rueda[j], rueda[i]];
      return { ...p, rueda };
    });
  }

  agregarLugar(): void {
    this.aMano.set(true);
    this.pedido.update((p) => ({ ...p, rueda: [...p.rueda, { puestos: [], inicia: p.rueda.length === 0 }] }));
    setTimeout(() => {
      const inputs = document.querySelectorAll<HTMLInputElement>('.lugar-puestos');
      inputs[inputs.length - 1]?.focus();
    });
  }

  /** Un lugar por cada puesto del grupo que todavía no esté en la rueda. */
  agregarGrupo(evento: Event): void {
    this.aMano.set(true);
    const select = evento.target as HTMLSelectElement;
    const g = this.grupos().find((x) => x.id === Number(select.value));
    select.value = '';
    if (!g) return;
    const ya = new Set(this.puestosDeLaRueda().map(clavePuesto));
    const nuevos = g.puestos.filter((p) => !ya.has(clavePuesto(p)));
    if (!nuevos.length) {
      this.toast.error(`Los puestos de ${g.nombre} ya están en la rueda.`);
      return;
    }
    this.pedido.update((p) => ({
      ...p,
      rueda: [...p.rueda, ...nuevos.map((puesto, k) => ({ puestos: [puesto], inicia: p.rueda.length === 0 && k === 0 }))],
    }));
  }

  quitarLugar(i: number): void {
    this.aMano.set(true);
    this.pedido.update((p) => {
      const rueda = p.rueda.filter((_, j) => j !== i);
      if (rueda.length && !rueda.some((l) => l.inicia)) rueda[0] = { ...rueda[0], inicia: true };
      return { ...p, rueda };
    });
  }

  // --- Coberturas

  cambiarCobertura(i: number, cambios: Partial<{ fijo: string; cubre: string }>): void {
    this.pedido.update((p) => ({ ...p, coberturas: p.coberturas.map((c, j) => (j === i ? { ...c, ...cambios } : c)) }));
  }

  agregarCobertura(): void {
    const p = this.pedido();
    this.pedido.update((x) => ({
      ...x,
      coberturas: [...x.coberturas, { fijo: p.fijos[0]?.puesto ?? '', cubre: this.puestosDeLaRueda()[0] ?? '' }],
    }));
  }

  quitarCobertura(i: number): void {
    this.pedido.update((p) => ({ ...p, coberturas: p.coberturas.filter((_, j) => j !== i) }));
  }

  // --- Generar

  /** desplazar: bajar hasta el resultado (cuando se tocó "Generar"; no cuando se genera solo). */
  generar(desplazar = true): void {
    this.error.set('');
    if (!this.pedido().rueda.some((l) => l.puestos.length)) {
      this.error.set('Agregá al menos un puesto a la rueda.');
      return;
    }
    this.generando.set(true);
    const dia = this.dia();
    const pedido$ = dia ? this.api.generarDia({ pedido: this.pedido(), presentes: dia.presentes }) : this.api.generar(this.pedido());
    pedido$.subscribe({
      next: (b) => {
        this.rolesDejados.set(new Set());
        this.elegidoParaRol.set(null);
        this.borrador.set(b);
        this.generando.set(false);
        this.repartirTareas(b.asignacion);
        if (desplazar) setTimeout(() => document.getElementById('borrador')?.scrollIntoView({ behavior: 'smooth' }));
      },
      error: (e) => {
        this.generando.set(false);
        // No queda a la vista el de antes, como si fuera el de ahora.
        if (!desplazar) this.borrador.set(null);
        this.error.set(mensajeDeError(e, 'No se pudo generar.'));
      },
    });
  }

  /** Las tareas de apertura, repartidas igual que en Armar el día, para que salgan en las tarjetas. */
  private repartirTareas(asignacion: { numero: number; empleadoId: number | null }[]): void {
    const dia = this.dia();
    this.tareas.set(new Map());
    if (!dia || !asignacion.length) return;
    const nombre = new Map(dia.empleados.map((e) => [e.id!, e.nombre]));
    const numeroDe = new Map(asignacion.filter((a) => a.empleadoId != null).map((a) => [a.empleadoId!, a.numero]));
    this.tareasApi.repartir(hoyLocal(), dia.paraTareas ?? dia.presentes.map((p) => ({ empleadoId: p.empleadoId, acompananteId: null }))).subscribe({
      next: (lista) => {
        const porNumero = new Map<number, string[]>();
        for (const t of lista) {
          for (const id of t.empleadoIds) {
            const numero = numeroDe.get(id);
            if (numero == null) continue;
            const otros = t.empleadoIds.filter((x) => x !== id).map((x) => nombre.get(x) ?? '');
            const texto = otros.length ? `${t.nombre} (con ${otros.join(', ')})` : t.nombre;
            porNumero.set(numero, [...(porNumero.get(numero) ?? []), texto]);
          }
        }
        this.tareas.set(new Map([...porNumero].map(([n, l]) => [n, l.join(' y ')])));
      },
      error: () => this.tareas.set(new Map()),
    });
  }

  abrirEnEditor(): void {
    const b = this.borrador();
    if (!b) return;
    this.borradores.pendiente.set(b.cronograma);
    this.router.navigate(['/cronogramas/nuevo']);
  }
}

/** "12:00", "12:00 y 12:30", "12:00, 12:30 y 13:00". */
function unir(partes: string[]): string {
  return partes.length <= 1 ? partes.join('') : `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`;
}
