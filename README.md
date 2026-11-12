# La Serranita — Rotación de puestos

Guarda los cronogramas de rotación del parque y, con unas preguntas del día, sugiere el más adecuado
y arma las tarjetas de cada empleado para imprimir.

- `Back/La Serranita rotaciones`: Spring Boot 3.5 (Java 17). H2 en un archivo (`data/`) en dev,
  Postgres en prod.
- `Front`: Angular 21.

## Pantallas

- **Armar el día**: cuántos empleados hay, cuánta gente se espera (baja, media o alta) y qué puestos
  tienen que tener el mismo empleado todo el día. Muestra hasta 5 cronogramas del más adecuado al
  menos, con lo que no coincide de cada uno, y al elegir uno se ven e imprimen las tarjetas.
- **Cronogramas**: importar desde Excel, crear, editar, ver y borrar.

## Cómo se elige

Cada diferencia suma puntos y gana el que menos tiene (`SugerenciaService`):

- Por empleado que falta (el cronograma pide más de los que hay): 12. Por empleado que sobra: 6.
- Por nivel de afluencia de diferencia: 8.
- Por puesto pedido como fijo en el que el empleado rota: 8; si el cronograma no tiene ese puesto: 10.
- Por puesto fijo que no se pidió: 1 (desempate: entre los que cumplen, gana el que deja rotar más).

Un puesto es fijo si un mismo empleado está en al menos el 75% de los horarios en que hay alguien
(así el que lo cubre en el almuerzo no le quita lo de fijo). La cantidad de empleados son los
números distintos que aparecen en el cronograma.

## Formato del Excel para importar

Cada hoja es un cronograma, con el nombre de la hoja.

- Fila 1: la afluencia, por ejemplo A1 `Afluencia` y B1 `Alta` (`Baja`, `Media` o `Alta`). Es
  obligatoria: si una hoja no la tiene, no se importa nada del archivo.
- Fila 2: en A el encabezado ("Puesto") y desde B los horarios (`10:00`, `11:00`...).
- Desde la fila 3: un puesto por fila, el nombre en A y en cada horario el número de empleado. Varios
  empleados en el mismo puesto van separados con guión (`1-2`).
- Un puesto cerrado en un horario lleva `Cerrado` (o `C`). Si un empleado deja un puesto que cierra,
  la tarjeta dice "Rote a…".
- Los colores del Excel no se leen: la app pinta cada número de empleado con un color fijo.
- El puesto `Descanso` sale arriba en la tarjeta.

## Levantarlo en desarrollo

Backend en `http://localhost:8082` (Swagger en `/swagger-ui.html`, consola H2 en `/h2-console`):

```bash
cd "Back/La Serranita rotaciones" && ./mvnw spring-boot:run
```

Front en `http://localhost:4300`:

```bash
npm install --prefix Front && npm run start --prefix Front
```

Los puertos son distintos a los de La Serranita Entradas (8080 / 4200) para poder correr los dos a la vez.

## Docker

Con `DB_PASSWORD` en un `.env` al lado del `docker-compose.yml`:

```bash
docker compose up -d --build
```

Queda en `http://localhost:4300`; nginx reenvía `/api` al backend.
