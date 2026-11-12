import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideCalendarCheck, LucideLayoutList } from '@lucide/angular';
import { ConfirmacionDialogo } from './shared/confirmacion/confirmacion-dialogo';
import { ToastHost } from './shared/toast/toast-host';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ConfirmacionDialogo, ToastHost, LucideCalendarCheck, LucideLayoutList],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
