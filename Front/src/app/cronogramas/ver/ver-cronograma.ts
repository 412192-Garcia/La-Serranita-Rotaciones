import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideLoaderCircle, LucidePencil } from '@lucide/angular';
import { Cronograma, textoAfluencia } from '../../models/cronograma';
import { CronogramaService, mensajeDeError } from '../../services/cronograma.service';
import { ToastService } from '../../shared/toast/toast.service';
import { VistaCronograma } from '../../shared/vista-cronograma/vista-cronograma';

@Component({
  selector: 'app-ver-cronograma',
  imports: [RouterLink, VistaCronograma, LucideArrowLeft, LucideLoaderCircle, LucidePencil],
  templateUrl: './ver-cronograma.html',
  styleUrl: './ver-cronograma.css',
})
export class VerCronograma implements OnInit {
  private api = inject(CronogramaService);
  private toast = inject(ToastService);

  /** De la ruta. */
  id = input.required<string>();

  readonly textoAfluencia = textoAfluencia;
  cronograma = signal<Cronograma | null>(null);

  ngOnInit(): void {
    this.api.obtener(Number(this.id())).subscribe({
      next: (c) => this.cronograma.set(c),
      error: (e) => this.toast.error(mensajeDeError(e)),
    });
  }
}
