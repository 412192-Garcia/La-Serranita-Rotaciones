package org.example.laserranitarotaciones.service;

import java.util.Arrays;

/** Método húngaro: reparto de menor costo total en una matriz cuadrada. */
final class Hungaro {

    private Hungaro() {
    }

    /** resultado[fila] = columna asignada. */
    static int[] resolver(int[][] costo) {
        int n = costo.length;
        long[] u = new long[n + 1];
        long[] v = new long[n + 1];
        int[] p = new int[n + 1];
        int[] camino = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            long[] minimo = new long[n + 1];
            Arrays.fill(minimo, Long.MAX_VALUE);
            boolean[] usado = new boolean[n + 1];
            do {
                usado[j0] = true;
                int i0 = p[j0];
                long delta = Long.MAX_VALUE;
                int j1 = 0;
                for (int j = 1; j <= n; j++) {
                    if (usado[j]) continue;
                    long actual = costo[i0 - 1][j - 1] - u[i0] - v[j];
                    if (actual < minimo[j]) {
                        minimo[j] = actual;
                        camino[j] = j0;
                    }
                    if (minimo[j] < delta) {
                        delta = minimo[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (usado[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minimo[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = camino[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] resultado = new int[n];
        for (int j = 1; j <= n; j++) {
            if (p[j] > 0) resultado[p[j] - 1] = j - 1;
        }
        return resultado;
    }
}
