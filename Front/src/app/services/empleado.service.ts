import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Empleado } from '../models/empleado';

@Injectable({ providedIn: 'root' })
export class EmpleadoService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/empleados`;

  listar(): Observable<Empleado[]> {
    return this.http.get<Empleado[]>(this.url);
  }

  guardar(e: Empleado): Observable<Empleado> {
    return e.id == null ? this.http.post<Empleado>(this.url, e) : this.http.put<Empleado>(`${this.url}/${e.id}`, e);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
