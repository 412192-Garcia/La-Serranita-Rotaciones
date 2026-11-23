import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Usuario } from '../models/usuario';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/usuarios`;

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.url);
  }

  crear(usuario: string, nombre: string, clave: string): Observable<Usuario> {
    return this.http.post<Usuario>(this.url, { usuario, nombre, clave });
  }

  /** La propia: pide la de ahora. */
  cambiarMiClave(actual: string, nueva: string): Observable<void> {
    return this.http.put<void>(`${this.url}/yo/clave`, { actual, nueva });
  }

  /** La de otro (si se la olvidó). */
  cambiarClave(id: number, nueva: string): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/clave`, { nueva });
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
