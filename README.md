# La Serranita — Rotación de puestos

Arma las tarjetas de rotación de cada empleado a partir del Excel del plan y las imprime.

- `Back/La Serranita rotaciones`: Spring Boot 3.5 (Java 17). Lee el Excel con Apache POI y arma las tarjetas.
- `Front`: Angular 21.

No guarda nada: cada vez se sube el Excel.

## Formato del Excel

Cada hoja es una variación del plan.

- Fila 1: título (se ignora).
- Fila 2: en A el encabezado ("Puesto") y desde B los horarios (`10:00`, `11:00`...).
- Desde la fila 3: un puesto por fila, el nombre en A y en cada horario el número de empleado. Varios
  empleados en el mismo puesto van separados con guión (`1-2`).
- El puesto `Descanso` sale arriba en la tarjeta.

## Levantarlo en desarrollo

Backend en `http://localhost:8082` (Swagger en `/swagger-ui.html`):

```bash
cd "Back/La Serranita rotaciones" && ./mvnw spring-boot:run
```

Front en `http://localhost:4300`:

```bash
npm install --prefix Front && npm run start --prefix Front
```

Los puertos son distintos a los de La Serranita Entradas (8080 / 4200) para poder correr los dos a la vez.

## Docker

```bash
docker compose up -d --build
```

Queda en `http://localhost:4300`; nginx reenvía `/api` al backend.
