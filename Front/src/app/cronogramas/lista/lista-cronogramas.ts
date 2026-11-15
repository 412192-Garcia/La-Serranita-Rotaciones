import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideEye, LucideFileUp, LucideLoaderCircle, LucidePencil, LucidePlus, LucideTrash, LucideWandSparkles } from '@lucide/angular';
import { CronogramaResumen, textoAfluencia } from '../../models/cronograma';
import { CronogramaService, mensajeDeError } from '../../services/cronograma.service';
import { PuestoService } from '../../services/puesto.service';
import { clavePuesto } from '../../models/empleado';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-lista-cronogramas',
  imports: [RouterLink, LucideEye, LucideFileUp, LucideLoaderCircle, LucidePencil, LucidePlus, LucideTrash, LucideWandSparkles],
  templateUrl: './lista-cronogramas.html',
  styleUrl: './lista-cronogramas.css',
})
export class ListaCronogramas implements OnInit {
  private api = inject(CronogramaService);
  private puestosApi = inject(PuestoService);
  private toast = inject(ToastService);
  private confirmacion = inject(ConfirmacionService);

  readonly textoAfluencia = textoAfluencia;

  cargando = signal(true);
  importando = signal(false);
  cronogramas = signal<CronogramaResumen[]>([]);
  /** Claves de los puestos que no se muestran en la columna de fijos (se elige en Prioridad de puestos). */
  private ocultos = signal<Set<string>>(new Set());

  /** Los puestos fijos del cronograma, sin los que se eligió no mostrar. */
  fijosVisibles(c: CronogramaResumen): string[] {
    return c.fijos.filter((f) => !this.ocultos().has(clavePuesto(f)));
  }

  ngOnInit(): void {
    this.cargar();
    this.puestosApi.listar().subscribe({
      next: (l) => this.ocultos.set(new Set(l.filter((p) => !p.mostrarEnFijos).map((p) => clavePuesto(p.nombre)))),
      // Sin la lista, se muestran todos los fijos.
      error: () => this.ocultos.set(new Set()),
    });
  }

  importar(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo) return;
    this.importando.set(true);
    this.api.importar(archivo).subscribe({
      next: (nuevos) => {
        this.importando.set(false);
        this.toast.exito(nuevos.length === 1 ? 'Se agregó 1 cronograma.' : `Se agregaron ${nuevos.length} cronogramas.`);
        this.cargar();
      },
      error: (e) => {
        this.importando.set(false);
        this.toast.error(mensajeDeError(e, 'No se pudo importar el archivo.'));
      },
    });
  }

  async eliminar(c: CronogramaResumen): Promise<void> {
    const ok = await this.confirmacion.pedir({
      titulo: 'Borrar cronograma',
      mensaje: `¿Borrar "${c.nombre}"?`,
      aceptar: 'Borrar',
      peligro: true,
    });
    if (!ok) return;
    this.api.eliminar(c.id).subscribe({
      next: () => {
        this.cronogramas.update((l) => l.filter((x) => x.id !== c.id));
        this.toast.exito('Cronograma borrado.');
      },
      error: (e) => this.toast.error(mensajeDeError(e)),
    });
  }

  private cargar(): void {
    this.api.listar().subscribe({
      next: (l) => {
        this.cronogramas.set(l);
        this.cargando.set(false);
      },
      error: (e) => {
        this.toast.error(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }
}
