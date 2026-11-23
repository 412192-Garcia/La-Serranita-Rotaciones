import { Routes } from '@angular/router';
import { ArmarDia } from './armar-dia/armar-dia';
import { Login } from './login/login';
import { sesionGuard } from './shared/sesion/sesion.guard';

// Armar el día carga con la app; el resto, recién al entrar.
const editorCronograma = () => import('./cronogramas/editor/editor-cronograma').then((m) => m.EditorCronograma);
const editorEmpleado = () => import('./empleados/editor/editor-empleado').then((m) => m.EditorEmpleado);

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    path: '',
    canActivateChild: [sesionGuard],
    children: [
      { path: '', pathMatch: 'full', component: ArmarDia },
      { path: 'cronogramas', loadComponent: () => import('./cronogramas/lista/lista-cronogramas').then((m) => m.ListaCronogramas) },
      { path: 'cronogramas/nuevo', loadComponent: editorCronograma },
      { path: 'cronogramas/prioridades', redirectTo: 'configuracion/puestos' },
      { path: 'cronogramas/generar', loadComponent: () => import('./cronogramas/generador/generador').then((m) => m.Generador) },
      { path: 'cronogramas/:id', loadComponent: () => import('./cronogramas/ver/ver-cronograma').then((m) => m.VerCronograma) },
      { path: 'cronogramas/:id/editar', loadComponent: editorCronograma },
      {
        path: 'configuracion',
        loadComponent: () => import('./configuracion/configuracion').then((m) => m.Configuracion),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'empleados' },
          { path: 'empleados', loadComponent: () => import('./empleados/lista/lista-empleados').then((m) => m.ListaEmpleados) },
          { path: 'empleados/nuevo', loadComponent: editorEmpleado },
          { path: 'empleados/:id', loadComponent: editorEmpleado },
          { path: 'puestos', loadComponent: () => import('./puestos/puestos').then((m) => m.Puestos) },
          { path: 'tareas', loadComponent: () => import('./empleados/tareas/tareas').then((m) => m.Tareas) },
          { path: 'puestos-abiertos', loadComponent: () => import('./configuracion/reglas/reglas').then((m) => m.Reglas) },
        ],
      },
      // Direcciones de antes.
      { path: 'empleados', pathMatch: 'full', redirectTo: 'configuracion/empleados' },
      { path: 'empleados/nuevo', redirectTo: 'configuracion/empleados/nuevo' },
      { path: 'empleados/tareas', redirectTo: 'configuracion/tareas' },
      { path: 'empleados/:id', redirectTo: 'configuracion/empleados/:id' },
      { path: 'puestos', redirectTo: 'configuracion/puestos' },
    ],
  },
  { path: '**', redirectTo: '' },
];
