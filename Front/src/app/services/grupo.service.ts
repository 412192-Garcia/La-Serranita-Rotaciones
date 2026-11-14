import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { GrupoPuestos } from '../models/grupo';

@Injectable({ providedIn: 'root' })
export class GrupoService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/grupos`;

  listar(): Observable<GrupoPuestos[]> {
    return this.http.get<GrupoPuestos[]>(this.url);
  }

  guardar(g: GrupoPuestos): Observable<GrupoPuestos> {
    return g.id == null ? this.http.post<GrupoPuestos>(this.url, g) : this.http.put<GrupoPuestos>(`${this.url}/${g.id}`, g);
  }

  /** Los que sabían el grupo pasan a saber sus puestos uno por uno. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
