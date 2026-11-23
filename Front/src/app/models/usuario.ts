export interface Usuario {
  id: number;
  usuario: string;
  nombre: string;
}

export interface Sesion {
  token: string;
  usuario: Usuario;
}
