import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PuestoParque } from '../models/puesto';

@Injectable({ providedIn: 'root' })
export class PuestoService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/puestos`;

  listar(): Observable<PuestoParque[]> {
    return this.http.get<PuestoParque[]>(this.url);
  }

  cambiarPrioridad(p: PuestoParque): Observable<PuestoParque> {
    return this.http.put<PuestoParque>(this.url, p);
  }
}
