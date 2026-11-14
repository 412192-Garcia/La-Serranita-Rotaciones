import { Routes } from '@angular/router';
import { ArmarDia } from './armar-dia/armar-dia';

// Armar el día carga con la app; el resto, recién al entrar.
const editorCronograma = () => import('./cronogramas/editor/editor-cronograma').then((m) => m.EditorCronograma);
const editorEmpleado = () => import('./empleados/editor/editor-empleado').then((m) => m.EditorEmpleado);

export const routes: Routes = [
  { path: '', component: ArmarDia },
  { path: 'cronogramas', loadComponent: () => import('./cronogramas/lista/lista-cronogramas').then((m) => m.ListaCronogramas) },
  { path: 'cronogramas/nuevo', loadComponent: editorCronograma },
  { path: 'cronogramas/prioridades', loadComponent: () => import('./puestos/puestos').then((m) => m.Puestos) },
  { path: 'cronogramas/:id', loadComponent: () => import('./cronogramas/ver/ver-cronograma').then((m) => m.VerCronograma) },
  { path: 'cronogramas/:id/editar', loadComponent: editorCronograma },
  { path: 'empleados', loadComponent: () => import('./empleados/lista/lista-empleados').then((m) => m.ListaEmpleados) },
  { path: 'empleados/nuevo', loadComponent: editorEmpleado },
  { path: 'empleados/tareas', loadComponent: () => import('./empleados/tareas/tareas').then((m) => m.Tareas) },
  { path: 'empleados/:id', loadComponent: editorEmpleado },
  { path: 'puestos', redirectTo: 'cronogramas/prioridades' },
  { path: '**', redirectTo: '' },
];
