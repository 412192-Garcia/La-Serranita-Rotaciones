import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Cronograma, CronogramaResumen, PedidoSugerencia, Sugerencia } from '../models/cronograma';

@Injectable({ providedIn: 'root' })
export class CronogramaService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/cronogramas`;

  listar(): Observable<CronogramaResumen[]> {
    return this.http.get<CronogramaResumen[]>(this.url);
  }

  obtener(id: number): Observable<Cronograma> {
    return this.http.get<Cronograma>(`${this.url}/${id}`);
  }

  puestos(): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/puestos`);
  }

  guardar(c: Cronograma): Observable<Cronograma> {
    return c.id == null ? this.http.post<Cronograma>(this.url, c) : this.http.put<Cronograma>(`${this.url}/${c.id}`, c);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  importar(archivo: File): Observable<CronogramaResumen[]> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<CronogramaResumen[]>(`${this.url}/importar`, datos);
  }

  sugerir(pedido: PedidoSugerencia): Observable<Sugerencia[]> {
    return this.http.post<Sugerencia[]>(`${this.url}/sugerir`, pedido);
  }
}

export function mensajeDeError(error: unknown, porDefecto = 'Algo salió mal. Probá de nuevo.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'No se pudo contactar con el servidor.';
    if (typeof error.error?.mensaje === 'string') return error.error.mensaje;
  }
  return porDefecto;
}
