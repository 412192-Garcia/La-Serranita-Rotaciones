# Cambios

Qué cambió en cada versión, contado para quien usa el sistema. Lo nuevo se va anotando en
**Sin publicar**.

## Sin publicar

- Los cronogramas quedan guardados: ya no hace falta elegir el Excel cada vez.
- Nueva pantalla **Armar el día**: se responde cuántos empleados hay, cuánta gente se espera y qué
  puestos tienen que tener el mismo empleado todo el día, y sugiere los cronogramas más adecuados,
  avisando en qué no coincide cada uno. Al elegir uno se ven e imprimen las tarjetas.
- Nueva pantalla **Cronogramas**: se importan desde Excel (cada hoja es un cronograma), se crean y se
  corrigen en la app, y se pueden borrar.
- Cada cronograma dice para cuánta gente es: baja, media o alta. En el Excel va en la primera fila.
- Un puesto puede estar cerrado en un horario (se escribe C). Si alguien deja un puesto que cierra, la
  tarjeta dice "Rote a…".
- Cada número de empleado tiene siempre el mismo color en la planilla y en el editor.
- Un puesto cuenta como fijo aunque otro lo cubra un rato, por ejemplo en el almuerzo.
- Entre dos cronogramas que cumplen lo pedido, se recomienda el que deja rotar más puestos.
- El pie de la tarjeta impresa lleva el nombre del cronograma.
- Nueva pantalla **Empleados**: cada uno con los puestos que sabe hacer y en cuáles tiene prioridad.
- **Armar el día** ahora pregunta quién vino y qué no puede hacer hoy cada uno (por ejemplo, alguien
  lastimado sólo en Minigolf), en vez de la cantidad de empleados y los puestos fijos. Lo marcado se
  guarda hasta el otro día.
- La app decide quién hace cada número del cronograma: nadie queda en un puesto que no sabe o que hoy
  no puede si hay otra forma, y cada uno tiene prioridad en su puesto principal. Si no hay forma,
  avisa. El reparto se puede cambiar a mano.
- Las tarjetas salen con el nombre del empleado, y dicen "Al ser cubierto por Ana" en vez del número.
- Nueva pantalla **Puestos**: cada puesto tiene prioridad alta, media o baja. Si falta gente o alguien
  tiene que hacer algo que no sabe, primero se cubren los de prioridad alta.
- Si alguien hace dos puestos a la vez, en el cronograma van en dos filas con el mismo número. Cuando
  deja los dos, la tarjeta dice quiénes lo cubren en cada uno ("Al ser cubierto por 6 y 7").
- Los puestos pueden tener una nota (por ejemplo "consultar al encargado"), que se ve en la planilla y
  en la tarjeta, sin que cuente como otro puesto.
- En el celular, el menú se ve en dos filas.
- En el Excel, la primera fila puede decir el nombre del cronograma (Excel corta los nombres de hoja
  largos).
- Al volver del descanso la tarjeta dice sólo adónde va, sin "Al ser cubierto por…".
- Cuando alguien hace tres puestos a la vez, la tarjeta dice "Travesía, Búsqueda y Laberinto".
- Armar el día: lo marcado de empleados que ya no existen o de puestos que ya no saben se descarta solo.
- Editor de cronogramas nuevo: se elige un número (con su color), Cerrado o Borrar, y se pinta tocando o
  arrastrando sobre la grilla; tocar un número que ya está lo saca, y con "Sumar a la celda" quedan
  varios (6-7). Los horarios se arman con "de 11:00 a 18:00 cada 30 min". Los puestos se eligen de los
  que ya existen, se suben, bajan o quitan, y la nota sólo aparece si tiene algo. Una fila "Sin puesto"
  muestra en cada horario qué números no están en ningún lado. Avisa antes de salir sin guardar.

## Primera versión web

- La rotación de puestos pasa a ser una página web (antes era un programa de Windows). Se elige el
  Excel del plan, la variación del día, y se ven las tarjetas de cada empleado y la planilla con sus
  colores.
- Se puede imprimir la tarjeta de un empleado o todas juntas, de a cuatro por hoja apaisada.
- Si una celda tiene varios empleados ("1-2"), la tarjeta dice "Al ser cubierto por 1 y 2".
- Un empleado que entra a mitad del día ve sólo el puesto en su primer horario.
