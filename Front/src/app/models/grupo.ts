/** Varios puestos con un nombre: el que sabe "Parque" sabe todos los de la rueda. */
export interface GrupoPuestos {
  id: number | null;
  nombre: string;
  puestos: string[];
}
