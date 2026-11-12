import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideCircleCheck, LucideLoaderCircle, LucideSearch, LucideTriangleExclamationPoint } from '@lucide/angular';
import { AFLUENCIAS, Afluencia, Cronograma, Sugerencia, textoAfluencia } from '../models/cronograma';
import { CronogramaService, mensajeDeError } from '../services/cronograma.service';
import { ToastService } from '../shared/toast/toast.service';
import { VistaCronograma } from '../shared/vista-cronograma/vista-cronograma';

@Component({
  selector: 'app-armar-dia',
  imports: [RouterLink, VistaCronograma, LucideCircleCheck, LucideLoaderCircle, LucideSearch, LucideTriangleExclamationPoint],
  templateUrl: './armar-dia.html',
  styleUrl: './armar-dia.css',
})
export class ArmarDia implements OnInit {
  private api = inject(CronogramaService);
  private toast = inject(ToastService);

  readonly afluencias = AFLUENCIAS;
  readonly textoAfluencia = textoAfluencia;

  cargandoPuestos = signal(true);
  puestos = signal<string[]>([]);

  empleados = signal<number | null>(null);
  afluencia = signal<Afluencia>('MEDIA');
  fijos = signal<string[]>([]);
  errorEmpleados = signal('');

  buscando = signal(false);
  sugerencias = signal<Sugerencia[] | null>(null);

  elegidaId = signal<number | null>(null);
  cronograma = signal<Cronograma | null>(null);

  ngOnInit(): void {
    this.api.puestos().subscribe({
      next: (p) => {
        this.puestos.set(p);
        this.cargandoPuestos.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.cargandoPuestos.set(false);
      },
    });
  }

  cambiarEmpleados(evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.empleados.set(valor === '' ? null : Number(valor));
    this.errorEmpleados.set('');
  }

  alternarFijo(puesto: string): void {
    this.fijos.update((l) => (l.includes(puesto) ? l.filter((p) => p !== puesto) : [...l, puesto]));
  }

  buscar(): void {
    const n = this.empleados();
    if (n == null || !Number.isInteger(n) || n < 1) {
      this.errorEmpleados.set('Poné cuántos empleados hay.');
      return;
    }
    this.buscando.set(true);
    this.cronograma.set(null);
    this.elegidaId.set(null);
    this.api.sugerir({ empleados: n, afluencia: this.afluencia(), fijos: this.fijos() }).subscribe({
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

  usar(id: number): void {
    this.elegidaId.set(id);
    this.api.obtener(id).subscribe({
      next: (c) => {
        this.cronograma.set(c);
        setTimeout(() => document.getElementById('cronograma-elegido')?.scrollIntoView({ behavior: 'smooth' }));
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.elegidaId.set(null);
      },
    });
  }
}
