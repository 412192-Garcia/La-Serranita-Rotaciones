import { Component, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideLoaderCircle, LucidePlus, LucideSave, LucideX } from '@lucide/angular';
import { AFLUENCIAS, Afluencia, Puesto } from '../../models/cronograma';
import { CronogramaService, mensajeDeError } from '../../services/cronograma.service';
import { estiloCelda } from '../../shared/colores';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-editor-cronograma',
  imports: [RouterLink, LucideArrowLeft, LucideLoaderCircle, LucidePlus, LucideSave, LucideX],
  templateUrl: './editor-cronograma.html',
  styleUrl: './editor-cronograma.css',
})
export class EditorCronograma implements OnInit {
  private api = inject(CronogramaService);
  private toast = inject(ToastService);
  private router = inject(Router);

  /** De la ruta; sin id es uno nuevo. */
  id = input<string>();

  readonly afluencias = AFLUENCIAS;
  readonly estiloCelda = estiloCelda;

  cargando = signal(false);
  guardando = signal(false);
  error = signal('');
  errorNombre = signal('');

  nombre = signal('');
  afluencia = signal<Afluencia>('MEDIA');
  horarios = signal<string[]>(['10:00', '11:00', '12:00']);
  puestos = signal<Puesto[]>([{ nombre: '', celdas: ['', '', ''] }]);

  ngOnInit(): void {
    const id = this.id();
    if (!id) return;
    this.cargando.set(true);
    this.api.obtener(Number(id)).subscribe({
      next: (c) => {
        this.nombre.set(c.nombre);
        this.afluencia.set(c.afluencia);
        this.horarios.set([...c.horarios]);
        this.puestos.set(c.puestos.map((p) => ({ ...p, celdas: [...p.celdas] })));
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.router.navigate(['/cronogramas']);
      },
    });
  }

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  cambiarNombre(texto: string): void {
    this.nombre.set(texto);
    this.errorNombre.set('');
  }

  cambiarHorario(i: number, hora: string): void {
    this.horarios.update((l) => l.map((h, j) => (j === i ? hora : h)));
  }

  agregarHorario(): void {
    const ultimo = this.horarios().at(-1);
    let siguiente = '10:00';
    if (ultimo) {
      const [h, m] = ultimo.split(':').map(Number);
      siguiente = `${String(Math.min(h + 1, 23)).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
    }
    this.horarios.update((l) => [...l, siguiente]);
    this.puestos.update((l) => l.map((p) => ({ ...p, celdas: [...p.celdas, ''] })));
  }

  quitarHorario(i: number): void {
    this.horarios.update((l) => l.filter((_, j) => j !== i));
    this.puestos.update((l) => l.map((p) => ({ ...p, celdas: p.celdas.filter((_, j) => j !== i) })));
  }

  cambiarPuesto(i: number, cambios: Partial<Puesto>): void {
    this.puestos.update((l) => l.map((p, j) => (j === i ? { ...p, ...cambios } : p)));
  }

  cambiarCelda(i: number, k: number, texto: string): void {
    this.puestos.update((l) =>
      l.map((p, j) => (j === i ? { ...p, celdas: p.celdas.map((c, n) => (n === k ? texto : c)) } : p)),
    );
  }

  agregarPuesto(): void {
    this.puestos.update((l) => [...l, { nombre: '', celdas: this.horarios().map(() => '') }]);
  }

  quitarPuesto(i: number): void {
    this.puestos.update((l) => l.filter((_, j) => j !== i));
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
        puestos: this.puestos(),
      })
      .subscribe({
        next: (c) => {
          this.toast.exito('Cronograma guardado.');
          this.router.navigate(['/cronogramas', c.id]);
        },
        error: (e) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(e, 'No se pudo guardar.'));
        },
      });
  }
}
