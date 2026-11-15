import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ReglaApertura } from '../models/regla';

@Injectable({ providedIn: 'root' })
export class ReglaService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/reglas-apertura`;

  listar(): Observable<ReglaApertura[]> {
    return this.http.get<ReglaApertura[]>(this.url);
  }

  /** Crea o cambia la regla de esa cantidad y afluencia. */
  guardar(r: ReglaApertura): Observable<ReglaApertura> {
    return this.http.put<ReglaApertura>(this.url, r);
  }

  /** El orden de rotación predeterminado (la rueda de cada regla lo sigue). */
  ordenRotacion(): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/orden-rotacion`);
  }

  /** Cambia el orden de rotación y reordena la rueda de todas las reglas. */
  guardarOrdenRotacion(nombres: string[]): Observable<string[]> {
    return this.http.put<string[]>(`${this.url}/orden-rotacion`, nombres);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Reemplaza todas las reglas con la tabla "Puestos abiertos" del Excel. */
  importar(archivo: File): Observable<{ reglas: number; avisos: string[] }> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<{ reglas: number; avisos: string[] }>(`${this.url}/importar`, datos);
  }
}
