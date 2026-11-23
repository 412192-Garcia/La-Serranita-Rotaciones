import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionService } from '../../services/sesion.service';

/** Sin sesión, al ingreso (y después vuelve a donde quería ir). */
export const sesionGuard: CanActivateFn = (_ruta, estado) => {
  if (inject(SesionService).logueado()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { volver: estado.url } });
};
