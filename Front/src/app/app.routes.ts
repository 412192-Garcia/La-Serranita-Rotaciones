import { Routes } from '@angular/router';
import { Rotaciones } from './rotaciones/rotaciones';

export const routes: Routes = [
  { path: '', component: Rotaciones },
  { path: '**', redirectTo: '' },
];
