import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideListChecks, LucideMapPin, LucideTable, LucideUsers } from '@lucide/angular';

/** Lo que se carga una vez y se usa todos los días: empleados, puestos, reglas y tareas de apertura. */
@Component({
  selector: 'app-configuracion',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideListChecks, LucideMapPin, LucideTable, LucideUsers],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.css',
})
export class Configuracion {}
