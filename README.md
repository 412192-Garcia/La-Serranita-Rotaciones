# La Serranita — Rotación de puestos

Guarda los cronogramas de rotación del parque y los empleados con los puestos que saben hacer. Con
quién vino y cuánta gente se espera, sugiere el cronograma más adecuado, decide quién hace cada número
y arma las tarjetas con el nombre de cada uno para imprimir.

- `Back/La Serranita rotaciones`: Spring Boot 3.5 (Java 17). H2 en un archivo (`data/`) en dev,
  Postgres en prod.
- `Front`: Angular 21.

## Pantallas

- **Armar el día**: quién vino (todos marcados por defecto), qué puestos no puede hacer hoy cada uno
  (se apagan tocándolos, sin cambiar su lista) y cuánta gente se espera. Muestra hasta 5 cronogramas
  del más adecuado al menos, con lo que no coincide de cada uno. Al elegir uno se ve quién hace cada
  número (se puede cambiar a mano) y las tarjetas salen con nombre. Lo marcado queda guardado en el
  dispositivo hasta el otro día.
- **Cronogramas**: importar desde Excel, crear, editar, ver y borrar.
- **Empleados**: nombre, puestos que sabe hacer y, de esos, los principales (ahí tiene prioridad).
- **Puestos**: la prioridad de cada puesto (alta, media o baja; media si nunca se cambió).

## Cómo se elige

Cada diferencia suma puntos y gana el que menos tiene (`SugerenciaService` y `AsignadorService`):

- Por empleado que falta (el cronograma pide más de los que vinieron): 12. Por empleado que sobra: 6.
- Por nivel de afluencia de diferencia: 8.
- El reparto de números se hace con el método húngaro (el de menor costo total). Cada puesto que
  alguien tendría que hacer y no sabe, o hoy no puede, y cada puesto de un número que queda sin nadie,
  cuesta según su prioridad: alta 300, media 100, baja 30. Por ponerlo fuera de su puesto principal:
  3; por ocupar el principal de otro que vino: 4.

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
- Cada fila es un solo puesto. Si una persona hace dos puestos a la vez (Travesía y Búsqueda), van
  en dos filas con el mismo número; la tarjeta los junta en un renglón.
- Una aclaración va entre paréntesis después del nombre: `Canilla (consultar al encargado)`. Se guarda
  como nota del puesto, aparte del nombre, y se lee en la tarjeta. Un `*` al principio se ignora.
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
