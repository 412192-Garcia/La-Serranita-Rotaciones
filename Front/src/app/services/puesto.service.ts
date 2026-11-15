import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AbreTardePuesto, PuestoParque } from '../models/puesto';

@Injectable({ providedIn: 'root' })
export class PuestoService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/puestos`;

  listar(): Observable<PuestoParque[]> {
    return this.http.get<PuestoParque[]>(this.url);
  }

  /** Agrega un puesto a la lista (por ejemplo uno que todavía no está en ningún cronograma). */
  crear(nombre: string): Observable<PuestoParque> {
    return this.http.post<PuestoParque>(this.url, { nombre });
  }

  /** Sólo si ningún cronograma lo usa; sale también de los empleados y los grupos. */
  eliminar(nombre: string): Observable<void> {
    return this.http.delete<void>(this.url, { params: { nombre } });
  }

  /** null = abierto todo el día. */
  cambiarAbreTarde(nombre: string, abreTarde: AbreTardePuesto | null): Observable<PuestoParque> {
    return this.http.put<PuestoParque>(`${this.url}/abre-tarde`, { nombre, abreTarde });
  }

  cambiarPrioridad(p: PuestoParque): Observable<PuestoParque> {
    return this.http.put<PuestoParque>(this.url, p);
  }

  /** Sólo cambia si aparece en la columna "Puestos fijos"; la prioridad no se toca. */
  cambiarMostrarEnFijos(nombre: string, mostrarEnFijos: boolean): Observable<PuestoParque> {
    return this.http.put<PuestoParque>(`${this.url}/fijos`, { nombre, mostrarEnFijos });
  }

  /** Cambia el nombre en cronogramas, empleados y prioridad; si ya existe "a", se juntan. */
  renombrar(de: string, a: string): Observable<{ cronogramas: number; empleados: number }> {
    return this.http.post<{ cronogramas: number; empleados: number }>(`${this.url}/renombrar`, { de, a });
  }
}
