# Cambios

Qué cambió en cada versión, contado para quien usa el sistema. Lo nuevo se va anotando en
**Sin publicar**.

## Sin publicar

- **La hora de la Tirolesa va en cada regla**: "Tirolesa ×3 · 13:00". En el editor, un puesto fijo puede
  "abrir más tarde" con su hora (si un día con poca gente conviene abrirla a las 14, va en esa regla). Las reglas
  que había quedaron con las 13:00. En Puestos, la Tirolesa dice "Horario en Reglas"; si una regla no la trae, ese
  día no abre.
- La pestaña "Puestos abiertos" de Configuración pasa a llamarse **Reglas**.
- **Login**: para entrar hace falta usuario y contraseña ("No cerrar sesión" la deja abierta 14 días). Es uno
  solo, para la PC del parque, y se configura en el servidor.
- **Deploy**: la app queda en rotaciones.parquelaserranita.online, en el mismo servidor que Entradas. Al pasar a
  main se arman las imágenes, y scripts/deploy.sh la actualiza en un paso (con backup de la base antes).
- **Cronograma de hoy** (Armar el día → Generar con los que vinieron), mucho más simple: se genera solo al
  entrar. Arriba, cuántos vinieron, cuánta gente se espera, el horario y el almuerzo (si cambiás algo se
  vuelve a generar), y en palabras qué abre, qué abre más tarde y qué queda cerrado hoy. Lo que necesita una
  decisión se pregunta corto ("¿Quién hace de Presentador…?", "Pedí ayuda al encargado…"); los avisos de
  cómo está cargado van plegados en "Cosas para revisar en Configuración". Fijos, rueda, coberturas y
  horarios especiales quedan en "Ajustes avanzados". El editor se abre con "Corregir a mano".
- Show del zorrito: si la Presentadora está en la Casa del zorrito y nadie del show la sabe, la cubre una
  cajera que esté libre.
- **Puestos abiertos cuenta a todos**: cada regla es para los que vinieron, sin descontar a nadie, y dice
  cuántos van a la Tirolesa (como un puesto más, "Tirolesa ×3"; su horario sigue en Puestos). Las reglas que
  había se pasaron solas sumándoles la gente de la Tirolesa, así abren los mismos puestos. Los del Show del
  zorrito y Fotos rotan con los demás y se van a su puesto cuando abre. Si una regla abre todo el día un puesto
  que tiene horario (Fotos con mucha gente), ese día manda la regla.
- **Editor de reglas nuevo** (Puestos abiertos → lápiz o "+ Regla"): en una ventana, sin escribir a mano. Los
  puestos se eligen de la lista; la gente de cada fijo se cambia con − y + (con los mismos colores que la
  tabla: verde los fijos, amarillo la Tirolesa); la rueda es una lista de personas (renglones) a las que con
  el + se les suma otro puesto que hacen a la vez, se ordenan con flechas y se elige dónde arranca. Abajo, una
  barra muestra cuántas personas ocupa ("Da justo" cuando coincide) y no deja guardar si no da la cantidad, si
  ya hay otra regla igual o si no tiene puestos. Una regla nueva arranca como copia de la más parecida.
- **Puestos abiertos se lee como una tabla**: se elige la afluencia y se ve una fila por cantidad de
  empleados y una columna por puesto, con colores: Fijo, ×3 (Tirolesa), Rota, — cerrado y * donde arranca la
  rueda. Los puestos que hace una misma persona (Travesía, Búsqueda y Laberinto) van con una barra "Rota" que
  los abarca; si Búsqueda se reparte, son dos barras que se pisan en Búsqueda. Las reglas que no cierran sus
  cuentas se marcan.
- Los refuerzos son sólo para puestos de prioridad media o más. Uno de prioridad baja (Baños) que queda sin
  nadie un rato queda Cerrado ese rato.
- Armar el día: con un cronograma que tiene la fila Libre, ya no avisa "No sabe Libre" (Libre, como Descanso,
  no es un puesto).
- **Refuerzos**: si después de probar todo un puesto queda sin nadie en algún horario, el generador lo
  marca "Refuerzo" en la planilla y avisa "pedí un refuerzo". El encargado elige a quién poner; no hace
  falta cargar a nadie.
- El Cajero (y todo puesto con prioridad "Siempre") nunca lo hace alguien que no lo sabe: se cambia con uno
  que sí sepa: de Libre o de otro puesto que no quede sin nadie (se cambian si el otro sabe ese puesto, o
  ahí queda alguien más). Si no se puede, se pide un refuerzo.
- En el almuerzo, si un puesto lo iba a cubrir alguien que no lo sabe mientras come el que sí (la Casa del
  zorrito), el que sabe come más tarde dentro de los descansos y a esa hora lo cubre alguien libre que lo
  sepa (la otra cajera).
- El que tiene estrella en un puesto cerrado por el almuerzo (Baños) ya no vuelve a él si eso deja sin nadie
  un puesto más importante (la Casa del zorrito quedaba vacía a las 12:00).
- En el almuerzo por tandas, un puesto que está en dos lugares de la rueda (Búsqueda, mitad con Travesía y
  mitad con Laberinto) ahora lo hacen los dos; antes quedaba sólo con el de Travesía.
- **Roles dentro de un puesto** (Configuración → Puestos → Roles): por ejemplo el Show del zorrito tiene
  Zorrito y Presentador, uno cada uno, y el resto son Ayudantes (cuántos depende de la afluencia). En cada
  empleado se marca para qué roles está preparado. El puesto sigue siendo una sola fila: en la planilla
  la celda lleva un renglón por persona con su rol ("14 Zorrito", "2 Presentador", "6 Ayudante") y en la
  tarjeta va al lado del puesto ("Show del zorrito (Zorrito)", "Show del zorrito (Ayudante)"). Al generar con los que vinieron, cada rol lo hace alguien preparado: si no hay en el
  puesto, se trae a uno de Libre o se lo cambia por otro que sepa lo que él hacía. Si nadie de los que
  vinieron puede, el generador pregunta quién lo cubre (cualquier empleado); también se puede dejar así.
  Si el preparado está en su puesto con estrella (CZ1 en la Casa del zorrito), igual va al show cuando
  alguien del show sabe su puesto y lo cubre (la cajera).
- **Generar borrador** (desde Cronogramas): arma un cronograma con las reglas de siempre a partir de la
  cantidad de empleados, los puestos fijos, el orden de la rueda y quién cubre qué en los descansos.
  Se puede partir de un cronograma que ya existe y cambiarle, por ejemplo, la cantidad de gente. Si no
  alcanza la gente para toda la rueda, cierra los puestos de menor prioridad y avisa. El borrador se
  abre en el editor para corregirlo y guardarlo. Dos del mismo puesto fijo (los dos cajeros) nunca
  descansan a la vez. A la rueda se le puede agregar un grupo entero (por ejemplo Parque): suma un
  lugar por cada puesto del grupo que falte. Puestos que abren más tarde, como la Tirolesa (desde las
  13:00 hasta el cierre): mientras no están abiertos, su gente cubre los almuerzos de los que rotan y si no
  hay nada que cubrir queda en Libre. Refuerzos, como la Casa
  del zorrito: abierta todo el día con una persona, y en el evento (16 a 17) se suman más; el resto del
  día esos de más cubren el parque. Menos gente, lo contrario: un fijo con varios (Cajero) pierde
  personas desde cierta hora, y esas pasan a cubrir el parque (sólo si hay un lugar vacío que cubrir; si
  no, se quedan en su puesto). Todo eso va en "Horarios especiales",
  un panel propio con cada dato rotulado, y en Puestos cada uno elige su tipo.
- **Armar el día → Generar con los que vinieron**: propone fijos (los principales de los que vinieron),
  la rueda (los puestos de sus grupos) y lo que abre tarde según la afluencia; se ajusta y se genera, y
  los números se reparten solos entre ellos. Con "Usar para hoy" queda elegido como cualquier otro.
- Generar con los que vinieron: después de repartir los números, si dos personas quedan mejor
  cambiándose de lugar en algún horario (la cajera que sabe Fotos a Fotos, el que no la sabe a Minigolf),
  se cambian. Si los dos ya están en lo que saben, la rueda no se toca.
- Un puesto fijo de una persona (Casa del zorrito) ya no queda vacío mientras ella descansa: descansa
  cuando un fijo de varios está completo (las dos cajeras) y lo cubre uno de ellos; si no se puede, alguien
  de un lugar de la rueda de menor prioridad.
- Con "menos gente" en Cajero, si abre un puesto que abre tarde (Fotos a las 17), la cajera que sobra
  pasa ahí y la persona de Fotos queda para el parque.
- Generar con los que vinieron: las tarjetas del borrador ya dicen qué tarea de apertura hace cada uno,
  repartidas igual que en Armar el día.
- Generar con los que vinieron: las estrellas (puestos principales) mandan. Nunca va otro a un puesto que podría
  hacer alguien que lo tiene con estrella y está trabajando: se cambian en ese horario (salvo mientras el de
  la estrella descansa). Si al de la estrella le tocó algo que no sabe y su puesto está vacío o cerrado (Baños
  en el almuerzo), vuelve a su puesto. En el reparto de números la estrella pesa mucho más que antes.
- Generar con los que vinieron: un puesto de prioridad media o más que quedó sin nadie en un horario lo cubre
  alguien que sobra (de Libre, o la segunda cajera). Si no lo sabe, puede ir a donde está otro que sí, y ese
  pasa al hueco: por ejemplo, la cajera cubre la Casa del zorrito y el que estaba ahí va a Laberinto. Y si
  alguien quedó en un puesto que no sabe y sobra uno que sí (la segunda cajera), ese entra y el otro va a un
  hueco que sepa o a Libre.
- Generar con los que vinieron: un puesto de prioridad baja (Flotante, Fotos) no se le da a alguien que no
  lo sabe. Primero se prueba cambiarlo con alguien que esté en Libre y lo sepa; si no hay, el puesto queda
  Cerrado ese rato, la persona pasa a Libre y se avisa ("Flotante de minigolf queda cerrado a las 12:30").
- Generador: si faltan para la rueda, los de un horario especial que están libres casi todo el día (el refuerzo
  del zorrito, la de Fotos) rotan con los demás en vez de cerrar puestos. Mientras están en su puesto, ese rato
  se cierran los lugares de menor prioridad (lo cubre primero alguien que esté libre), sin huecos que vayan
  pasando de puesto en puesto.
- Generador: los que sobran en un horario (por ejemplo la gente de la Tirolesa antes de que abra, o la de
  Fotos) van a una fila **Libre**: quedan disponibles, sin puesto. Ya no se los manda a reforzar un puesto. Salen de
  Libre para cubrir un almuerzo o un fijo vacío, o para abrir un puesto cerrado (también en el almuerzo) que
  saben; nunca uno que todavía no abrió, como la Tirolesa antes de las 13. Libre no
  es un puesto: nadie tiene que saberlo. La cajera que sobra con "menos gente" sólo pasa a un puesto que abre
  después (Fotos a las 17), no a uno que ya estaba abierto con su gente (la Tirolesa).
- Generador, almuerzo en tandas (como las planillas de 13 puestos): si hay reglas de puestos abiertos, todos
  menos las cajeras comen en la primera mitad de la franja (12:00 y 12:30), mitad y mitad; las cajeras después
  (13:00 y 13:30), y los de la Tirolesa antes de que abra. En cada tanda quedan abiertos los puestos de la regla
  para la gente que sigue trabajando (con 9: Travesía, Búsqueda y Laberinto con una persona) y lo demás queda
  Cerrado ese rato; cada uno sigue en lo suyo si está abierto y los demás cubren lo que quedó sin nadie.
  Un fijo no pierde gente por la regla: si las dos cajeras están trabajando quedan las dos, y lo que falta se
  cierra en los lugares de menor prioridad (Flotante).
- Generador: **Guardar como predeterminado** deja lo cargado como lo que se propone la próxima vez: los
  fijos y la rueda en la regla de puestos abiertos de esa cantidad y afluencia, y los horarios especiales
  en Puestos.
- Poner a alguien en un puesto que no sabe pesa cinco veces más en la búsqueda y en el reparto: antes
  conviene un cronograma más chico con gente libre.
- **Puestos abiertos** (Configuración): qué puestos funcionan según cuántos empleados hay (sin la
  Tirolesa) y la afluencia, con los fijos (Cajero y Casa del zorrito, como en las planillas; Baños también desde
  12 empleados, con menos rota) y quién comparte qué.
  La rueda arranca en el último puesto que se agregó (marcado con *, por ejemplo Pérgola o Pecera) y sigue el
  recorrido de las planillas: Carrera, Baños, Entrada, Canilla, Flotante, Minigolf, Travesía, Búsqueda,
  Laberinto y Tobogán. En el Excel, "Ayudante Tobogán" es la Pérgola. Ese orden de rotación se puede cambiar
  arriba de todo en Puestos abiertos, arrastrando los puestos (también con el dedo): al guardarlo, la rueda de
  todas las reglas lo sigue (cada una arranca en el mismo lugar), y también al importar el Excel. Se importa del Excel
  "Puestos abiertos" (poca gente = baja, mucha = alta; la media empieza igual que la baja) y cada regla se
  puede corregir. El generador arma los fijos y la rueda con la regla que corresponde, y avisa si no hay
  una exacta. Pecera y Ayudante Tobogán se agregaron a los puestos. El generador ya no propone Encargado.
- Nueva pestaña **Configuración** en el menú, con Empleados, Puestos (y sus grupos) y Tareas de
  apertura. Las direcciones de antes siguen andando.
- Prioridad **Siempre**, por encima de Alta: el puesto tiene que estar abierto sí o sí. El generador
  nunca lo cierra por falta de gente (si no alcanza ni para esos, avisa), y en el reparto pesa más que
  todo lo demás.
- **Puestos** (antes "Prioridad de puestos") es una lista propia: los de los cronogramas se agregan
  solos y los demás se agregan a mano (por ejemplo Tirolesa). Se puede borrar un puesto que no esté en
  ningún cronograma. Ahí se carga si un puesto abre tarde: desde y hasta qué hora, y cuántas personas
  lleva con afluencia baja, media y alta.
- **Grupos de puestos** (en Prioridad de puestos): por ejemplo "Parque" = toda la rueda. El empleado que
  sabe un grupo sabe todos sus puestos, también los que se le agreguen después. En el empleado y en
  Armar el día se ve el grupo, no sus puestos; con la flecha se despliega para elegir el principal o
  apagar puesto por puesto lo que no puede hacer hoy. Al borrar un grupo, los que lo
  sabían pasan a saber sus puestos uno por uno.
- En **Prioridad de puestos**, "En fijos" elige si un puesto aparece en la columna Puestos fijos de los
  cronogramas. Sirve para los que son fijos siempre, como Cajero. Es sólo lo que se ve: no cambia nada
  más.
- La lista de cronogramas ya no muestra el horario: el parque abre siempre a la misma hora.
- **Armar el día**: con gente de más ya no sugiere un cronograma mucho más chico. Dejar a alguien sin
  puesto ahora pesa casi como ponerlo en un puesto que no sabe.
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
- Un puesto cuenta como fijo aunque otro lo cubra un rato, por ejemplo en el almuerzo. Un puesto que se
  hace sólo un rato del día (Basura, Fotos a la tarde) no cuenta como fijo.
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
- Prioridad de puestos: con el lápiz se cambia el nombre de un puesto en todos los cronogramas y
  empleados; si se le pone el nombre de otro, se juntan en una sola fila (por ejemplo Cajero 2 pasa a
  ser Cajero con dos personas).
- **Prioridad de puestos** (desde Cronogramas): cada puesto tiene prioridad alta, media o baja. Si falta gente o alguien
  tiene que hacer algo que no sabe, primero se cubren los de prioridad alta.
- Si alguien hace dos puestos a la vez, en el cronograma van en dos filas con el mismo número. Cuando
  deja los dos, la tarjeta dice quiénes lo cubren en cada uno ("Al ser cubierto por 6 y 7").
- Cada cronograma puede marcar dónde arranca la rotación: en el Excel con un * delante del nombre del
  puesto, y en el editor con el botón * de cada puesto. Se ve en la planilla y en la tarjeta
  ("rote a *Travesía, Búsqueda y Laberinto"), como en la versión de Windows.
- Los puestos pueden tener una nota (por ejemplo "consultar al encargado"), que se ve en la planilla y
  en la tarjeta, sin que cuente como otro puesto.
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
- **Tareas de apertura** (desde Empleados): cosas sin horario que se hacen antes de abrir, como pasar
  la sopladora, con cuántas personas necesita cada una y si pide saber (eso se marca en cada empleado).
  En Armar el día se reparten todas entre los que vinieron; quien está en entrenamiento va con su
  acompañante y cuenta como uno más. Se pueden cambiar a mano y salen arriba en la tarjeta. Al imprimir
  queda guardado quién hizo cada una, y los días siguientes se evita repetirla.
- Armar el día, "Quién hace cada número": al elegir a alguien, primero aparecen los que no tienen número
  y después los demás con su número al lado.
- Editor de cronogramas: botones Deshacer y Rehacer (también Ctrl+Z y Ctrl+Y). Una pincelada o lo escrito
  seguido en un campo se deshace de una vez.
- Empleados en entrenamiento: se marcan en Empleados, con a quién acompañan normalmente. No ocupan un
  lugar en el cronograma: en Armar el día se elige a quién acompañan (se puede cambiar a una hora, por
  ejemplo con Ana hasta las 14:00 y después con Beto) y su tarjeta se arma con la de esa persona.
  Avisa si el acompañante no vino.

## Primera versión web

- La rotación de puestos pasa a ser una página web (antes era un programa de Windows). Se elige el
  Excel del plan, la variación del día, y se ven las tarjetas de cada empleado y la planilla con sus
  colores.
- Se puede imprimir la tarjeta de un empleado o todas juntas, de a cuatro por hoja apaisada.
- Si una celda tiene varios empleados ("1-2"), la tarjeta dice "Al ser cubierto por 1 y 2".
- Un empleado que entra a mitad del día ve sólo el puesto en su primer horario.
