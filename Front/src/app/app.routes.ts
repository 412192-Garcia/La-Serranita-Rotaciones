import { Routes } from '@angular/router';
import { ArmarDia } from './armar-dia/armar-dia';
import { ListaCronogramas } from './cronogramas/lista/lista-cronogramas';
import { EditorCronograma } from './cronogramas/editor/editor-cronograma';
import { VerCronograma } from './cronogramas/ver/ver-cronograma';
import { ListaEmpleados } from './empleados/lista/lista-empleados';
import { EditorEmpleado } from './empleados/editor/editor-empleado';
import { Puestos } from './puestos/puestos';

export const routes: Routes = [
  { path: '', component: ArmarDia },
  { path: 'cronogramas', component: ListaCronogramas },
  { path: 'cronogramas/nuevo', component: EditorCronograma },
  { path: 'cronogramas/:id', component: VerCronograma },
  { path: 'cronogramas/:id/editar', component: EditorCronograma },
  { path: 'empleados', component: ListaEmpleados },
  { path: 'empleados/nuevo', component: EditorEmpleado },
  { path: 'empleados/:id', component: EditorEmpleado },
  { path: 'puestos', component: Puestos },
  { path: '**', redirectTo: '' },
];
