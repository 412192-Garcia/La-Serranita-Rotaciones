export type Prioridad = 'BAJA' | 'MEDIA' | 'ALTA';

export const PRIORIDADES: { valor: Prioridad; texto: string }[] = [
  { valor: 'ALTA', texto: 'Alta' },
  { valor: 'MEDIA', texto: 'Media' },
  { valor: 'BAJA', texto: 'Baja' },
];

export interface PuestoParque {
  nombre: string;
  prioridad: Prioridad;
  /** Si aparece en la columna "Puestos fijos" de los cronogramas. */
  mostrarEnFijos: boolean;
}
