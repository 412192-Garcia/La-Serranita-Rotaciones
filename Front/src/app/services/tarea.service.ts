import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Tarea, TareaAsignada } from '../models/tarea';

@Injectable({ providedIn: 'root' })
export class TareaService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/tareas`;

  listar(): Observable<Tarea[]> {
    return this.http.get<Tarea[]>(this.url);
  }

  guardar(t: Tarea): Observable<Tarea> {
    return t.id == null ? this.http.post<Tarea>(this.url, t) : this.http.put<Tarea>(`${this.url}/${t.id}`, t);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** fecha: yyyy-MM-dd local. */
  repartir(fecha: string, presentes: { empleadoId: number; acompananteId: number | null }[]): Observable<TareaAsignada[]> {
    return this.http.post<TareaAsignada[]>(`${this.url}/repartir`, { fecha, presentes });
  }

  guardarHechas(fecha: string, hechas: { tareaId: number; empleadoIds: number[] }[]): Observable<void> {
    return this.http.put<void>(`${this.url}/hechas/${fecha}`, hechas);
  }
}
