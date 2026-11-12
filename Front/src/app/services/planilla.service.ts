import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Planilla } from '../models/planilla';

@Injectable({ providedIn: 'root' })
export class PlanillaService {
  private http = inject(HttpClient);

  leer(archivo: File, hoja: number): Observable<Planilla> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    datos.append('hoja', String(hoja));
    return this.http.post<Planilla>(`${environment.apiBase}/planillas/leer`, datos);
  }
}

export function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'No se pudo contactar con el servidor.';
    if (typeof error.error?.mensaje === 'string') return error.error.mensaje;
  }
  return 'No se pudo leer la planilla.';
}
