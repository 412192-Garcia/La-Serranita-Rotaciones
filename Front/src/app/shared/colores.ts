/** Un color fijo por número de empleado, igual en todos los cronogramas. Del 1 al 11 son los de las planillas del parque. */
const PALETA = [
  '#00FF00', '#FFFF00', '#000064', '#FF9600', '#00FFFF', '#C89600', '#960000', '#FF0000', '#006400', '#FF64FF',
  '#FF0064', '#0096FF', '#96FF96', '#9664FF', '#FFC896', '#00968C', '#C8C800', '#646464', '#FF96C8', '#64C8FF',
];

export interface EstiloCelda {
  fondo: string | null;
  texto: string | null;
}

const SIN_ESTILO: EstiloCelda = { fondo: null, texto: null };

export function esCerrado(valor: string): boolean {
  const v = valor.trim().toLowerCase();
  return v === 'cerrado' || v === 'c';
}

export function colorEmpleado(numero: number): string {
  return PALETA[(numero - 1) % PALETA.length];
}

/** Una celda con un solo empleado lleva su color; cerrada, gris; vacía o con varios, sin color. */
export function estiloCelda(valor: string): EstiloCelda {
  if (esCerrado(valor)) return { fondo: '#d1d5db', texto: '#4b5563' };
  const numeros = valor.split('-').map((p) => p.trim()).filter((p) => /^\d+$/.test(p));
  if (numeros.length !== 1 || Number(numeros[0]) < 1) return SIN_ESTILO;
  const fondo = colorEmpleado(Number(numeros[0]));
  return { fondo, texto: esOscuro(fondo) ? '#ffffff' : '#111827' };
}

function esOscuro(hex: string): boolean {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return 0.299 * r + 0.587 * g + 0.114 * b < 140;
}
