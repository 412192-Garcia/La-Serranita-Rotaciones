import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideCalendarCheck, LucideLayoutList, LucideLogOut, LucideSettings } from '@lucide/angular';
import { SesionService } from './services/sesion.service';
import { ConfirmacionService } from './shared/confirmacion/confirmacion.service';
import { ConfirmacionDialogo } from './shared/confirmacion/confirmacion-dialogo';
import { ToastHost } from './shared/toast/toast-host';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ConfirmacionDialogo,
    ToastHost,
    LucideCalendarCheck,
    LucideLayoutList,
    LucideLogOut,
    LucideSettings,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  sesion = inject(SesionService);
  private router = inject(Router);
  private confirmacion = inject(ConfirmacionService);

  async salir(): Promise<void> {
    const ok = await this.confirmacion.pedir({ titulo: 'Salir', mensaje: '¿Cerrar la sesión en este dispositivo?', aceptar: 'Salir' });
    if (!ok) return;
    this.sesion.salir();
    this.router.navigate(['/login']);
  }
}
