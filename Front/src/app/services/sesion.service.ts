import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Sesion } from '../models/usuario';

const CLAVE = 'rotaciones.sesion';

function leer(): Sesion | null {
  try {
    const texto = localStorage.getItem(CLAVE);
    return texto ? (JSON.parse(texto) as Sesion) : null;
  } catch {
    return null;
  }
}

/** Quién está logueado. El token queda guardado en el navegador hasta salir o hasta que venza. */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private http = inject(HttpClient);
  private sesion = signal<Sesion | null>(leer());

  token = computed(() => this.sesion()?.token ?? null);
  usuario = computed(() => this.sesion()?.usuario ?? null);
  logueado = computed(() => this.sesion() != null);

  entrar(usuario: string, clave: string, mantener: boolean): Observable<Sesion> {
    return this.http.post<Sesion>(`${environment.apiBase}/auth/login`, { usuario, clave, mantener }).pipe(
      tap((s) => {
        this.sesion.set(s);
        localStorage.setItem(CLAVE, JSON.stringify(s));
      }),
    );
  }

  salir(): void {
    this.sesion.set(null);
    localStorage.removeItem(CLAVE);
  }
}
