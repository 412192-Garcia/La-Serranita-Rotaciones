import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideEye, LucideEyeOff, LucideLoaderCircle } from '@lucide/angular';
import { mensajeDeError } from '../services/cronograma.service';
import { SesionService } from '../services/sesion.service';

@Component({
  selector: 'app-login',
  imports: [LucideEye, LucideEyeOff, LucideLoaderCircle],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private sesion = inject(SesionService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  usuario = signal('');
  clave = signal('');
  mantener = signal(false);
  verClave = signal(false);
  entrando = signal(false);
  error = signal('');

  valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  entrar(evento?: Event): void {
    evento?.preventDefault();
    if (!this.usuario().trim() || !this.clave() || this.entrando()) return;
    this.error.set('');
    this.entrando.set(true);
    this.sesion.entrar(this.usuario(), this.clave(), this.mantener()).subscribe({
      next: () => {
        const volver = this.ruta.snapshot.queryParamMap.get('volver');
        this.router.navigateByUrl(volver && volver.startsWith('/') && !volver.startsWith('/login') ? volver : '/');
      },
      error: (e) => {
        this.entrando.set(false);
        this.error.set(mensajeDeError(e, 'No se pudo entrar.'));
      },
    });
  }
}
