/** Lo que vuelve al entrar: el token y con qué usuario (el único, el del .env). */
export interface Sesion {
  token: string;
  usuario: string;
}
