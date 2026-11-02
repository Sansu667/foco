# Foco

Foco bloquea Instagram, WhatsApp, Facebook, TikTok y YouTube desde las 4:00 a. m. y las libera cuando terminas tus pendientes exigentes del día. Los pendientes solo se pueden marcar desde las 11:00 a. m., que es cuando arranca tu bloque de trabajo. Si un día no alcanzas a terminar, a las 9:00 p. m. las redes se liberan igual, para que el bloqueo no se te pase al día siguiente. Las tres horas se cambian dentro de la app.

## Cómo funciona en el día a día

En la noche, antes de dormir, abres Foco y anotas lo que tienes que sacar mañana: "escribir 2 páginas de la tesis", "subir la evidencia del SENA". Cuando te levantas, las redes ya están bloqueadas. Si abres Instagram, el teléfono vuelve al inicio y te aparece una pantalla con lo que te falta.

A las 11 empiezas a trabajar. Cada vez que terminas algo, lo marcas en Foco, que te pregunta "¿lo terminaste?" para que no se marque por error. Con el último pendiente marcado, las redes quedan libres hasta las 4 de la mañana siguiente. Los pendientes terminados se borran solos al empezar el día, y los que quedaron sin hacer pasan al día siguiente.

Para que no sea tan fácil hacerse trampa, mientras dura el bloqueo no puedes borrar pendientes, cambiar el horario, apagar el bloqueo ni quitar apps de la lista. Sí puedes agregar pendientes y apps. Si no anotas ningún pendiente, el bloqueo sigue hasta la hora límite.

Para urgencias hay una pausa de 5 minutos, dos veces al día. Antes de abrirla toca esperar 20 segundos, que es justo el rato que hace falta para darse cuenta de si de verdad era urgente. Pensé sobre todo en WhatsApp: si alguien te escribe algo importante, puedes responder sin tener que desinstalar nada.

## Compilar el APK

No necesitas instalar Android Studio. GitHub lo compila por ti.

1. En GitHub, crea un repositorio nuevo (puede ser privado), por ejemplo `foco`.
2. Sube todo el contenido de esta carpeta, incluida la carpeta oculta `.github`. Lo más fácil es con git desde la terminal:
   ```
   cd foco
   git init
   git add .
   git commit -m "Primera versión de Foco"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/foco.git
   git push -u origin main
   ```
3. Entra a la pestaña **Actions** del repositorio. Ahí verás "Compilar APK" corriendo; tarda unos 4 minutos.
4. Cuando salga en verde, entra a esa ejecución y abajo, en **Artifacts**, descarga `Foco-apk`. Es un .zip con `app-debug.apk` adentro.

Si prefieres Android Studio, abre la carpeta `foco` como proyecto, espera a que sincronice y usa **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Instalarlo en el POCO X7 Pro

1. Pasa `app-debug.apk` al celular (por Drive, por cable o mandándotelo a ti mismo) y ábrelo. HyperOS te va a pedir permiso para instalar apps de esa fuente; acéptalo. Puede que también te diga que la app no se reconoce y te pida esperar unos segundos antes de dejarte continuar, es normal en apps que no vienen de la tienda.
2. Abre Foco. Arriba te va a salir en rojo que falta el permiso de accesibilidad. Toca **Abrir accesibilidad**, entra a **Apps descargadas** y activa **Foco: bloqueo de redes**.
3. Como la app no viene de la Play Store, Android puede bloquear ese interruptor con un aviso de "ajuste restringido". En ese caso vuelve a Foco, toca **Info de la app**, luego los tres puntos ⋮ de arriba a la derecha y **Permitir ajustes restringidos**. Después repite el paso 2.
4. En la tarjeta "Para que HyperOS no la apague", toca **Quitar restricción de batería** y acepta, y en **Inicio automático** activa Foco.
5. Abre Recientes, mantén presionada la tarjeta de Foco y toca el candado.

Los pasos 4 y 5 importan bastante en un Xiaomi. HyperOS cierra apps en segundo plano con mucha agresividad, y si cierra Foco el bloqueo deja de funcionar sin avisarte. Si algún día notas que pudiste abrir Instagram durante el bloqueo, revisa primero que el permiso de accesibilidad siga encendido.

## Lo que Foco no hace

Foco bloquea las apps, no las páginas web, así que instagram.com o youtube.com en el navegador siguen funcionando. Si eso se vuelve tu ruta de escape, puedes agregar el navegador a la lista con **Agregar otra app**.

Tampoco es imposible de saltar. Quien de verdad quiera puede apagar el permiso de accesibilidad o desinstalar la app. Lo que hace es ponerle suficiente fricción al impulso automático de abrir una red como para que tengas que decidirlo a conciencia, que es casi siempre lo que hace falta.

Mientras el bloqueo está activo, las notificaciones de WhatsApp siguen llegando y la respuesta rápida desde la notificación sigue sirviendo. Las llamadas de WhatsApp también entran y se pueden contestar, porque Foco deja pasar las pantallas de llamada. Lo que se bloquea es abrir los chats. Eso último lo reconoce por el nombre interno de la pantalla, así que conviene que hagas una llamada de prueba el primer día para confirmar que en tu versión de WhatsApp funciona.

## Qué hay en el proyecto

- `Rules.java`: las reglas del bloqueo (horario, cuándo se liberan las redes, la pausa de emergencia).
- `Prefs.java`: lo que la app guarda en el teléfono. Nada sale del celular.
- `BlockerService.java`: el servicio de accesibilidad que detecta qué app se abre y la cierra si toca.
- `BlockActivity.java`: la pantalla de "Ahora no." que aparece al intentar abrir una red.
- `MainActivity.java`: la pantalla principal con los pendientes, el horario y las apps.

Para cambiar los valores por defecto (las horas, los minutos de la pausa, cuántas pausas hay al día), están al principio de `Prefs.java` y de `Rules.java`.
