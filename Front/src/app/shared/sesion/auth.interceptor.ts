import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SesionService } from '../../services/sesion.service';
import { ToastService } from '../toast/toast.service';

/** Manda el token en cada pedido; si el back dice 401 (venció o el usuario ya no existe), vuelve al ingreso. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const toast = inject(ToastService);
  const token = sesion.token();
  const pedido = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(pedido).pipe(
    catchError((error) => {
      if (error.status === 401 && token) {
        sesion.salir();
        toast.error('Tu sesión venció: entrá de nuevo.');
        router.navigate(['/login'], { queryParams: { volver: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
