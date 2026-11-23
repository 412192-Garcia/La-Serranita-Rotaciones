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
- **Cronogramas**: importar desde Excel, crear, editar, ver y borrar. En el editor se pinta la grilla
  con el número elegido (tocando o arrastrando) y la fila "Sin puesto" muestra a quién falta ubicar.
- **Grupos de puestos** (Configuración → Puestos): un nombre para varios puestos, por ejemplo "Parque"
  para toda la rueda. Quien sabe el grupo sabe todos sus puestos.
- **Empleados** (Configuración): nombre, puestos (o grupos) que sabe hacer y, de esos, los principales (ahí tiene prioridad). Un
  empleado en entrenamiento no ocupa un número del cronograma: acompaña a otro (uno habitual, o el que
  se elija en el día, con cambios por horario) y su tarjeta se arma con la de su acompañante.
- **Generar borrador** (botón en Cronogramas): con la cantidad de empleados, los puestos fijos (y
  cuántas personas llevan), la rueda en orden (un lugar puede ser varios puestos de la misma persona,
  y un puesto puede estar en dos lugares si lo hacen dos) y las coberturas del almuerzo, arma: los fijos
  todo el día; la rueda bajando un lugar por horario, con el primero que rota arrancando en el `*`;
  descansos de un horario, la primera mitad de la franja para los que rotan y la segunda para los
  fijos; y, mientras descansan los que rotan, los fijos que cubren pasan a los lugares vacíos. Si hay
  menos gente que lugares, cierra los de menor prioridad. Se puede partir de un cronograma existente.
  Lo que hace un fijo sólo una parte del día (Fotos del segundo cajero a la tarde) todavía no lo arma.
- **Reglas** (Configuración): según cuántos vinieron (todos) y la afluencia, qué puestos
  funcionan, cuáles son fijos (Cajero, Baños), cuántos van a la Tirolesa y desde qué hora, y quién comparte qué (Travesía,
  Búsqueda y Laberinto). Se ve como una tabla por afluencia: una fila por cantidad y una columna por puesto.
  Se importa del Excel del parque (que cuenta sin la Tirolesa: se le suma su gente) y se corrige a mano. El
  generador arma con esto los fijos, la gente de la Tirolesa y la rueda.
- **Tareas de apertura** (Configuración): nombre, cuántas personas y si pide saber. Se reparten
  todas, todos los días, al elegir el cronograma en Armar el día. Al imprimir se guarda quién hizo cada
  una; al repartir se evita repetir lo de los últimos 14 días (más si fue ayer) y se reparte parejo.
- **Puestos** (Configuración): la lista de puestos del parque. Los de los cronogramas se agregan
  solos; los demás se cargan a mano. Cada uno tiene prioridad alta, media o baja (media si nunca se
  cambió), que sólo decide algo cuando el reparto no sale perfecto: qué puesto queda sin nadie o con
  alguien que no lo sabe. Ahí también se renombra o junta un puesto en todos lados, y se carga si abre
  tarde (Tirolesa desde las 13) o tiene un refuerzo (Casa del zorrito: una persona todo el día y más en
  el evento de 16 a 17), con cuántas personas según la afluencia. Con "Roles" un puesto tiene roles
  que necesitan a alguien preparado (Show del zorrito: Zorrito y Presentador; el resto, Ayudantes); en
  cada empleado se marca para cuáles está preparado.
- **Generar con los que vinieron** (en Armar el día): abre el generador con los presentes; propone
  fijos, rueda y lo que abre tarde, genera y reparte los números entre ellos.

Nombres estándar: Cajero (sin "1" ni "2": dos cajeros son dos personas en la fila Cajero), Canilla (no
"Canilla o Laberinto"), y Travesía, Búsqueda y Laberinto como puestos separados.

## Cómo se elige

Cada diferencia suma puntos y gana el que menos tiene (`SugerenciaService` y `AsignadorService`):

- Por empleado que falta (el cronograma pide más de los que vinieron): 12. Por empleado que sobra: 6.
- Por nivel de afluencia de diferencia: 8.
- El reparto de números se hace con el método húngaro (el de menor costo total). Cada puesto que
  alguien tendría que hacer y no sabe, o hoy no puede, y cada puesto de un número que queda sin nadie,
  cuesta según su prioridad: alta 300, media 100, baja 30. Por ponerlo fuera de su puesto principal:
  3; por ocupar el principal de otro que vino: 4.

Un puesto es fijo si un mismo empleado está en al menos el 75% de los horarios en que hay alguien
(así el que lo cubre en el almuerzo no le quita lo de fijo), y si hay alguien en al menos la mitad de
los horarios del día (Basura, que se hace un rato, no es fija). La cantidad de empleados son los
números distintos que aparecen en el cronograma.

## Formato del Excel para importar

Cada hoja es un cronograma.

- Fila 1: la afluencia, por ejemplo A1 `Afluencia` y B1 `Alta` (`Baja`, `Media` o `Alta`). Es
  obligatoria: si una hoja no la tiene, no se importa nada del archivo. Opcional: C1 `Nombre` y D1 el
  nombre del cronograma; si no está, se usa el de la hoja (Excel lo corta en 31 letras).
- Fila 2: en A el encabezado ("Puesto") y desde B los horarios (`10:00`, `11:00`...).
- Desde la fila 3: un puesto por fila, el nombre en A y en cada horario el número de empleado. Varios
  empleados en el mismo puesto van separados con guión (`1-2`).
- Cada fila es un solo puesto, y cada puesto va en una sola fila. Si una persona hace dos puestos a la
  vez (Travesía y Búsqueda), van en dos filas con el mismo número; la tarjeta los junta en un renglón
  ("Travesía, Búsqueda y Laberinto"). Si dos personas están en el mismo puesto, van en la misma celda:
  `6-7`.
- Una aclaración va entre paréntesis después del nombre: `Canilla (consultar al encargado)`. Se guarda
  como nota del puesto, aparte del nombre, y se lee en la tarjeta.
- Un `*` delante del nombre (`*Travesía`) marca el puesto donde arranca la rotación. Se ve en la
  planilla y en la tarjeta.
- Un puesto cerrado en un horario lleva `Cerrado` (o `C`). Si un empleado deja un puesto que cierra,
  la tarjeta dice "Rote a…".
- Los colores del Excel no se leen: la app pinta cada número de empleado con un color fijo.
- El puesto `Descanso` sale arriba en la tarjeta. Al volver del descanso la tarjeta dice sólo adónde
  va.

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

Para entrar hay un solo usuario, el de `APP_USUARIO` y `APP_CLAVE` (en desarrollo, `admin` / `admin123`).

## Producción y deploy

Va en el mismo VPS que Entradas, en `rotaciones.parquelaserranita.online`, detrás del Caddy de Entradas.

**Día a día:** se mergea a `main` (el CI corre los tests y publica las imágenes
`ghcr.io/412192-garcia/la-serranita-rotaciones-{backend,frontend}`) y se corre `scripts/deploy.sh` (doble clic
o Git Bash): espera el CI, hace backup de la base, actualiza el VPS y chequea que quede andando.

**Primera vez:**

1. DNS: un registro `A` de `rotaciones.parquelaserranita.online` a la IP del VPS.
2. En el VPS: `mkdir -p /docker/la-serranita-rotaciones` y ahí un `.env` copiado de `.env.example`, completo
   (`DB_PASSWORD`, `JWT_SECRET`, `APP_USUARIO`, `APP_CLAVE`).
3. Caddy de Entradas: copiar `caddy/rotaciones.caddy` a `/docker/la-serranita-entradas/caddy-extra/` y recargar
   (`docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile` en la carpeta de Entradas).
4. Si las imágenes de GitHub son privadas: `docker login ghcr.io` en el VPS con un token de GitHub con permiso
   `read:packages` (o hacer públicos los paquetes en GitHub).
5. En tu PC: correr `scripts/deploy.sh`. La primera vez crea `scripts/deploy.local`: completarlo (los mismos
   datos de SSH que en Entradas) y correrlo de nuevo.
6. Entrar con `APP_USUARIO` y `APP_CLAVE`.

## Docker local

Para probar el build de producción en tu PC (con `.env` completo y `docker-compose.override.yml` copiado del
`.example`):

```bash
docker network create proxy
docker compose up -d --build
```

Queda en `http://localhost:4300`; nginx reenvía `/api` al backend.
