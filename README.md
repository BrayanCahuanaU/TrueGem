# Reconocedor de dinero

App Android para reconocer billetes y monedas peruanas con la cámara, contar cada denominación
y decir el monto total en voz alta. Está pensada para personas ciegas o con baja visión: todo el
flujo se puede completar sin mirar la pantalla, con voz, vibración, señal sonora y botones
físicos. Funciona **100 % sin internet**.

> **En una frase:** apunta la cámara al dinero, la app lo detecta, cuenta las piezas y te dice
> «Total: 35 soles. Un billete de 20 soles, un billete de 10 soles, una moneda de 5 soles».

## 1. Qué hace

- Reconoce **billetes de 10, 20, 50 y 100 soles**.
- Reconoce **monedas de 1, 2 y 5 soles**, y de **10, 20 y 50 céntimos**.
- Cuenta cuántas piezas hay de cada tipo y calcula el **monto total**.
- Si no puede distinguir una moneda con seguridad, **no la cuenta** y lo avisa, para no dar un
  total equivocado.
- Lee el resultado en voz alta, con el total y el detalle pieza por pieza.
- Avisa con **señal sonora** mientras ve dinero y con **vibración** al empezar, acertar o fallar.
- Guarda los ajustes de voz, vibración y navegación en el propio teléfono.
- No usa internet, ni cuentas, ni publicidad, ni analítica.

## 2. Cómo se usa (paso a paso)

1. **Abre la app.** Escucharás un saludo y verás un botón grande: «Reconocer dinero».
2. **Toca el botón grande.** La primera vez, la app explica por voz para qué necesita la cámara
   y pide el permiso.
3. **Coloca el dinero** sobre una superficie plana, con buena luz, sin que las piezas se
   superpongan.
4. **Sujeta el teléfono unos 30 cm por encima**, con la cámara trasera apuntando al dinero.
5. Mientras ve dinero, la app **pita** de vez en cuando y muestra las detecciones en pantalla.
6. Cuando el conteo se mantiene estable durante unos instantes, **vibra**, pasa a la pantalla de
   resultado y **dice el total y el detalle en voz alta**.
7. En el resultado puedes **repetir el mensaje** o **escanear de nuevo**.
8. Si pasan **20 segundos sin un resultado estable**, la app avisa por voz («No detecto
   dinero…») y vuelve al inicio.

No hace falta tocar los controles directamente: con la navegación por botones (activada por
defecto) puedes recorrer cada pantalla con los botones de volumen. Lo explico en la
sección [6.1](#61-navegación-con-botones-físicos).

## 3. Las pantallas

| Pantalla | Qué muestra | Qué puedes hacer |
|---|---|---|
| **Inicio** | Botón grande «Reconocer dinero», accesos a Ajustes y Ayuda, aviso de que funciona sin internet y aviso si el teléfono no tiene voz instalada. | Empezar un reconocimiento o entrar a Ajustes/Ayuda. |
| **Reconocer** | Vista de la cámara, recuadros sobre el dinero detectado, tarjeta de estado («Buscando dinero…» / «Dinero detectado») y botón «Detener». | Apuntar al dinero o detener y volver. |
| **Resultado** | Monto total, lista de piezas con su cantidad, botones «Repetir mensaje» y «Escanear de nuevo». | Volver a oír el total o empezar otro escaneo. |
| **Ajustes** | Velocidad y volumen de la voz, vibración, señal sonora, navegación con botones y «Probar voz». | Cambiar cómo habla y avisa la app. Se guarda al instante. |
| **Ayuda** | Instrucciones escritas paso a paso y botón «Escuchar instrucciones». | Leer o escuchar cómo usar la app. |

## 4. Cómo funciona por dentro (sin leer el código)

La app no «manda» la foto a ningún servidor: todo el reconocimiento ocurre en el teléfono.

```
Cámara ──► fotogramas (~5 por segundo) ──► modelo de detección ──► piezas detectadas
                                                                        │
                                                        conteo por denominación
                                                                        │
                                              filtro de estabilidad ◄────┘
                                                        │
                                     (mismo conteo en 5 de los últimos 8 fotogramas)
                                                        ▼
                                   resultado + vibración + voz + pantalla de resultado
```

En palabras simples:

1. **CameraX** entrega fotogramas de la cámara. Para ahorrar batería se procesan como máximo
   **5 por segundo**.
2. Cada fotograma se convierte en una imagen y se pasa a un **modelo de detección de objetos
   tipo YOLO** (`best.tflite`, ejecutado en CPU con 4 hilos). El modelo devuelve muchas cajas
   candidatas, cada una con su clase y su confianza. El tamaño de entrada se lee del propio
   modelo (hoy 640 × 640), no está fijado en el código.
3. Las detecciones se **limpian antes de contar** (ver [4.1](#41-cómo-se-decide-qué-es-cada-pieza)):
   una sola caja por objeto, umbral de confianza según la denominación, recorte ampliado de las
   monedas, voto entre fotogramas y descarte de monedas dudosas.
4. Las detecciones se **cuentan por denominación**. Los importes se manejan en **céntimos
   enteros**, nunca en decimales, para que las sumas sean exactas.
5. Un **filtro de estabilidad** evita resultados precipitados: guarda el conteo de los últimos
   8 fotogramas y solo da el resultado por bueno cuando el mismo conteo aparece en al menos 5
   y hay al menos una pieza.
6. Al estabilizarse, se guarda el resultado, **vibra**, se navega a la pantalla de resultado y
   se lee el total con el detalle.
7. Mientras hay piezas a la vista suena una **señal sonora** como máximo cada 1,5 segundos.

### 4.1 Cómo se decide qué es cada pieza

Las monedas peruanas se parecen mucho entre sí y el modelo a veces duda entre dos
denominaciones para la misma pieza. Para no contar de más ni contar mal, la app aplica cinco
reglas:

1. **Una caja por objeto.** Si el modelo propone dos cajas solapadas para la misma moneda con
   clases distintas (por ejemplo 20 céntimos al 60 % y 50 céntimos al 55 %), se queda solo con
   la más segura. Así una moneda nunca se cuenta dos veces como dos denominaciones.
2. **Umbral según la denominación.** Las monedas de 10, 20 y 50 céntimos exigen un 50 % de
   confianza; las de 1, 2 y 5 soles, un 45 %; los billetes mantienen el 40 %, porque ya
   funcionan bien y no conviene perder detecciones.
3. **No contar dudas.** Si la clase ganadora y la segunda están a menos de un 15 % de distancia,
   la moneda se considera dudosa: **no suma al total**. La app lo avisa en la pantalla de
   resultado y por voz («No pude reconocer una moneda con seguridad…»), y si la duda impide
   estabilizar el conteo, el aviso de los 20 segundos también lo explica.
4. **Recorte ampliado.** Cuando una moneda ocupa pocos píxeles en el fotograma completo, la app
   recorta esa zona con un 20 % de margen y la vuelve a pasar por el modelo. Así la pieza ocupa
   casi todo el tensor de entrada y se aprecian el número, el relieve y el canto. La lectura del
   recorte se mezcla con la del fotograma completo, dando más peso al recorte. Para no gastar
   batería se hacen como máximo **3 recortes por fotograma**, priorizando las monedas más
   seguras.
5. **Voto temporal.** Cada moneda se sigue entre fotogramas por solapamiento de su caja (IoU) y
   se van acumulando sus probabilidades. Una predicción aislada y errónea pesa menos que la
   evidencia de varios fotogramas seguidos. Si una moneda sigue dudosa tras varios fotogramas,
   se vuelve a recortar de vez en cuando para confirmar la lectura.

Los billetes están exentos de las reglas de duda, recorte y voto temporal: se cuentan siempre que
superen su umbral, con la misma lógica que antes. Así no se pierde lo que ya funcionaba bien.

Detalles que ayudan a entender el comportamiento:

- La pantalla se mantiene encendida **solo durante el reconocimiento**.
- Toda la inferencia pasa por **un único hilo**, porque el modelo no es seguro entre hilos.
- Si el modelo falta o está dañado, la app **no se cierra**: avisa por voz y en pantalla.
- El resultado se comparte entre pantallas en memoria (no se pasa como argumento de navegación).

## 5. Ajustes

Los ajustes se aplican al instante y se guardan en el teléfono (DataStore).

| Ajuste | Valores | Para qué sirve |
|---|---|---|
| **Velocidad de voz** | 0,5 a 2,0 veces (por defecto 1,0) | Hablar más despacio o más rápido. |
| **Volumen de voz** | 0 a 100 % (por defecto 80) | Volumen propio de la voz de la app, independiente del volumen del sistema. |
| **Vibración** | Activada / desactivada (por defecto activada) | Vibración al empezar, al acertar, al fallar y al mover el cursor. |
| **Señal sonora** | Activada / desactivada (por defecto activada) | Pitido breve mientras la cámara ve dinero. |
| **Navegación con botones** | Activada / desactivada (por defecto activada) | Usar los botones de volumen para recorrer la pantalla. |
| **Probar voz** | Botón | Lee una frase de ejemplo con la velocidad y el volumen actuales. |

## 6. Accesibilidad

- Todo el texto está en `strings.xml` (español) y los botones tienen descripción para TalkBack.
- Los títulos se anuncian como encabezados y el orden de foco es lógico.
- El diseño admite **texto grande** (hasta 200 %) con desplazamiento vertical en Resultado,
  Ajustes y Ayuda.
- Los deslizadores anuncian su valor («1,2 veces», «80 por ciento»).
- Cada paso del cursor da una **vibración muy corta**, para no depender de esperar a la voz.
- La app avisa por voz de los errores importantes: falta de cámara, permiso denegado, modelo
  no disponible, tiempo agotado.

### 6.1 Navegación con botones físicos

En **Ajustes → Navegación con botones** (activada por defecto) se puede recorrer cada pantalla
sin tocar directamente los controles:

- **Volumen arriba**: elemento anterior.
- **Volumen abajo**: elemento siguiente.
- **Un toque en cualquier parte de la pantalla**: selecciona el elemento resaltado.
- **Dos toques**: vuelve a la pantalla anterior.

El elemento actual se marca con un borde amarillo y se anuncia por voz con su nombre, su valor
y su posición («Velocidad de voz: 1,0 veces. 1 de 6»). En los deslizadores de velocidad y volumen
de voz, un toque entra en **modo ajuste**: los botones de volumen cambian el valor y otro toque
sale del modo.

El **botón de encendido no se puede interceptar** en Android: el sistema lo consume antes de que
llegue a la aplicación y apaga la pantalla. La app solo puede usar los botones de volumen. Si
algún dispositivo entrega `KEYCODE_POWER`, se trata como «siguiente», pero no es lo habitual.

Con **TalkBack activo**, este modo se desactiva automáticamente para no interferir con la
exploración táctil y los gestos de TalkBack. También se puede desactivar a mano en Ajustes; al
desactivarlo, los botones de volumen vuelven a controlar el volumen del sistema y los toques
funcionan de la forma normal.

Se mantiene el mapeo volumen arriba = anterior y volumen abajo = siguiente porque coincide con
el orden visual de la pantalla. No se usa un `AccessibilityService` para el botón de encendido:
habría que activarlo a mano en los ajustes de accesibilidad, no es fiable entre fabricantes y
supondría un servicio permanente en segundo plano; el modo queda contenido dentro de la app.

## 7. Privacidad: funciona sin internet

- El permiso de **INTERNET está eliminado** del manifiesto, igual que los permisos de red y
  servicios en segundo plano que arrastran algunas librerías.
- No hay analítica, telemetría, publicidad ni cuentas de usuario.
- Los fotogramas de la cámara se procesan **en memoria** y se descartan; no se guardan ni se
  envían.
- Lo único que se guarda en el teléfono son los ajustes (voz, vibración, señal y navegación).
- **Excepción voluntaria:** el modo de captura para mejorar el modelo (sección
  [14](#14-modo-de-captura-para-mejorar-el-modelo)) guarda fotogramas en el teléfono, pero solo
  existe en compilaciones de depuración, hay que activarlo a mano por ADB y nunca se sube nada a
  internet. La versión de release que se instala normalmente no incluye esa función activa.

## 8. Requisitos

- **Android 8.0 o superior** (`minSdk 26`).
- **Cámara** (la app se instala aunque el teléfono no tenga, y avisa por voz y en pantalla).
- **Voz en español** instalada en el sistema. Si no hay motor de voz, la app lo avisa y sigue
  mostrando los resultados en pantalla.
- La vibración es opcional.

## 9. Mapa del proyecto

Para entender la app no hace falta leer todo el código; esta es la función de cada carpeta:

```
app/src/main/
├── assets/
│   ├── best.tflite              Modelo de detección incluido en la app
│   └── labels.txt               Nombres de las clases, en el orden del modelo
├── java/com/example/reconocedordinero/
│   ├── MainActivity.kt          Pantallas, navegación, botones físicos y toques
│   ├── ReconocedorApp.kt        Crea una sola vez voz, vibración, pitido, modelo y cursor
│   ├── audio/
│   │   ├── SpeechManager.kt     Voz (TextToSpeech), velocidad y volumen
│   │   ├── HapticManager.kt     Patrones de vibración (paso, inicio, éxito, error)
│   │   └── BeepPlayer.kt        Pitido mientras se detecta dinero
│   ├── data/
│   │   ├── SettingsRepository.kt  Ajustes guardados con DataStore
│   │   └── CaptureStore.kt        Modo de captura de imágenes para el dataset (solo debug)
│   ├── domain/
│   │   ├── ScanResult.kt        Resultado: conteo por pieza y total en céntimos
│   │   ├── MoneyFormatter.kt    Montos en texto y frases habladas en español
│   │   └── StabilityFilter.kt   Decide cuándo un conteo es estable
│   ├── ml/
│   │   └── ModelRunner.kt       Carga best.tflite y ejecuta la detección
│   └── ui/
│       ├── accessibility/
│       │   └── CursorController.kt  Cursor virtual y resaltado de elementos
│       ├── components/
│       │   └── ScreenTopBar.kt  Barra superior con flecha atrás y título
│       ├── home/                Pantalla de inicio
│       ├── scan/                Cámara, análisis y recuadros de detección
│       ├── result/              Monto total y detalle
│       ├── settings/            Ajustes y su ViewModel
│       ├── help/                Instrucciones escritas y habladas
│       └── theme/               Colores, tipografía y tema Material 3
├── res/values/strings.xml       Todos los textos en español
└── res/xml/                     Reglas de copia de seguridad

app/src/test/                    Pruebas unitarias (filtro de estabilidad, formato de dinero y cursor)
```

## 10. Abrir y compilar

1. Abrir la carpeta del proyecto en Android Studio (versión reciente; el proyecto usa
   AGP 9.2.1 y Gradle 9.4.1 con Kotlin integrado).
2. Esperar la sincronización de Gradle.
3. Compilar: `./gradlew assembleDebug` (o el botón *Run* de Android Studio).
4. Pruebas unitarias: `./gradlew test`.

En Windows usa `gradlew.bat` en lugar de `./gradlew`. En línea de comandos hace falta un
JDK 17 o superior en `JAVA_HOME`; Android Studio ya trae uno.

Las pruebas unitarias cubren:

- `StabilityFilterTest`: cuándo un conteo se considera estable.
- `MoneyFormatterTest`: montos, singular/plural y frases habladas.
- `CursorControllerTest`: avance circular, modo ajuste y reinicio al cambiar de pantalla.
- `ModelRunnerTest`: NMS entre clases, margen de duda, conteo de monedas dudosas, `top2` y mezcla
  de probabilidades del recorte ampliado.

## 11. El modelo `best.tflite`

El modelo **ya está incluido** en `app/src/main/assets/best.tflite` en este repositorio.

Si se reemplaza por otro entrenamiento, debe copiarse exactamente en:

```
app/src/main/assets/best.tflite
```

y respetar el orden de clases de `app/src/main/assets/labels.txt`:

```
10-soles
100-soles
20-soles
50-soles
m1
m10
m2
m20
m5
m50
```

Son **10 clases**: los billetes y las seis monedas. La antigua clase `rev` desapareció porque los
reversos de moneda ahora están etiquetados con su denominación real (ver
`PLAN_MEJORA_MONEDAS.md`). Si el modelo falta o no se puede abrir, la app **no se cierra**: dice por
voz «No se pudo iniciar el reconocimiento» y lo muestra en pantalla.

### Versión actual del modelo

| Dato | Valor |
|---|---|
| Arquitectura | YOLOv8s, 11.129.454 parámetros |
| Entrada | 640 × 640 × 3 (float32, NHWC) |
| Salida | `[1, 14, 8400]` (4 coordenadas + 10 clases) |
| Clases | 10, sin `rev` |
| Entrenamiento | dataset público PeruvianCash con etiquetas corregidas y monedas sobremuestreadas |
| Test (370 imágenes no vistas) | Precision 0,980 · Recall 0,984 · mAP50 0,991 · mAP50-95 0,860 |
| Confusiones entre monedas | 0 (diagonal 1,00 en la matriz de confusión) |

`ModelRunner` lee la forma real del tensor, así que acepta tanto `[1, 3, 640, 640]` como
`[1, 640, 640, 3]` de entrada, y tanto `[1, 4+nc, N]` como `[1, N, 4+nc]` de salida.

## 12. Instalar en un teléfono real

1. Activar *Opciones de desarrollador* y *Depuración USB* en el teléfono.
2. Conectarlo por USB y aceptar la huella de depuración.
3. Ejecutar `./gradlew installDebug` o usar *Run* en Android Studio.
4. Conceder el permiso de cámara la primera vez.

Para probar sin mirar la pantalla, activar TalkBack en el teléfono. La app está pensada para
usarse con toques simples.

Si al instalar aparece `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, el teléfono tiene una versión
anterior firmada con otra clave: hay que **desinstalarla primero** (se pierden los ajustes
guardados) y luego instalar la nueva.

## 13. Logcat

Filtrar por la etiqueta `ModelRunner`. Al arrancar el reconocimiento imprime las formas reales
del modelo y el tamaño de entrada que va a usar:

```
ModelRunner: in=[1, 640, 640, 3] out=[1, 14, 8400] entrada=640x640
```

La salida esperada es `[1, 14, 8400]` (4 coordenadas + 10 clases); la entrada puede ser
`[1, 3, 640, 640]` o `[1, 640, 640, 3]`. `ModelRunner` detecta automáticamente el orden de canales,
el tamaño de entrada y si las coordenadas vienen normalizadas.

Para ver **cada detección** (clase, confianza, segunda clase, margen y caja) hay que habilitar
el nivel DEBUG de la etiqueta en el teléfono:

```bash
adb shell setprop log.tag.ModelRunner DEBUG
```

Los mensajes se ven así:

```
ModelRunner: m20=0.62 segunda=0.31 margen=0.31 caja=[410,300,520,410]
ModelRunner: m10=0.48 segunda=0.44 margen=0.04 DUDOSA caja=[120,310,210,400]
ModelRunner: recorte 240x240: m20 -> m50 (recorte=0.71)
```

`DUDOSA` significa que la moneda no se contó por tener un margen menor que 0,15. Es la forma más
rápida de comprobar si el problema es de duplicados, de umbral o de sesgo del modelo. La línea
`recorte` muestra el tamaño del recorte ampliado y cómo cambió la clase al re-evaluar la moneda;
si aparece a menudo cambiando la denominación, el modelo está confundiendo esa pareja de clases y
hará falta más datos reales. El `setprop` se pierde al reiniciar el teléfono.

Otras etiquetas útiles: `AppContainer` (fallo al cargar el modelo), `SpeechManager` (motor de
voz), `ScanScreen`/`ScanViewModel` (cámara e inferencia), `ButtonNavigation` (pasos del cursor) y
`CaptureStore` (modo de captura).

## 14. Modo de captura para mejorar el modelo

El modelo incluido reconoce bien los billetes y, desde el reentrenamiento del 12/11/2026, también
las seis monedas (0 confusiones entre denominaciones en el test del dataset). Aun así, el dataset
público con el que se entrenó tiene **pocas monedas y en fondos limpios**, así que la escena real
(mesa, mano, luz cálida, varias piezas) sigue siendo el punto débil. Para mejorarlo hacen falta
fotos reales del teléfono. La app incluye un modo de captura que guarda los fotogramas que analiza,
**solo en compilaciones de depuración** y **sin enviar nada a internet**.

### 14.1 Activar el modo

La forma recomendada es lanzar la app con un extra de intent (no necesita permisos especiales):

```bash
adb shell am force-stop com.example.reconocedordinero
adb shell am start -n com.example.reconocedordinero/.MainActivity --ez capturar true
```

En algunos teléfonos el usuario `shell` no puede escribir ajustes globales
(`SecurityException: WRITE_SECURE_SETTINGS`). Para esos casos existe la alternativa del ajuste
global, si el dispositivo lo permite:

```bash
adb shell settings put global truegem_capturas 1
# desactivar: adb shell settings put global truegem_capturas 0
```

Al activarse, Logcat muestra:

```
CaptureStore: Capturas activadas en /storage/emulated/0/Android/data/com.example.reconocedordinero/files/capturas
MainActivity: Modo de captura activado por intent
```

El modo se desactiva al cerrar la app (`force-stop` o quitar de recientes).

### 14.2 Qué guarda

- Un JPEG por fotograma, como máximo **uno cada 800 ms** y **500 capturas** por sesión.
- `predicciones.csv` con el nombre de la imagen y la predicción de la app en ese momento
  (etiqueta y confianza de cada pieza). Sirve como punto de partida para etiquetar, pero **hay
  que revisarlo**: el propio modelo es el que se quiere corregir.
- Las imágenes quedan en el almacenamiento externo propio de la app, accesible sin root.

### 14.3 Extraer las capturas

```bash
adb pull /sdcard/Android/data/com.example.reconocedordinero/files/capturas
```

Después se etiquetan (por ejemplo en Roboflow) y se añaden al entrenamiento. Conviene capturar,
como mínimo:

1. Una moneda por denominación, por ambas caras.
2. Varias monedas juntas, separadas y ligeramente solapadas.
3. Monedas junto a billetes.
4. Mesa clara, mesa oscura, mantel con textura y mano cerca.
5. Luz natural, luz cálida, sombra y brillo directo.
6. Distancias de 20, 30 y 40 cm, y distintos ángulos.
7. Monedas parcialmente tapadas o en el borde del encuadre.

Las capturas se borran desinstalando la app o eliminando la carpeta. No se comparten ni se suben
automáticamente.

## 15. Decisiones ante ambigüedades

- **LiteRT y el import de `Interpreter`.** La dependencia es
  `com.google.ai.edge.litert:litert:2.3.0` (Google Maven), como pide el enunciado. En esa
  versión la API Java sigue en el paquete `org.tensorflow.lite`, no en
  `com.google.ai.edge.litert`; por eso `ModelRunner.kt` importa
  `org.tensorflow.lite.Interpreter` (cambio mínimo respecto al archivo fijo). Además, LiteRT
  2.3.0 publica metadatos Kotlin más nuevos que el compilador integrado de AGP; como solo se
  usa su API Java, se añadió `-Xskip-metadata-version-check` en `app/build.gradle.kts`.
- **`Dinero.resumen`** usa `labels.getOrNull(cls)` en lugar de `labels[cls]` para que una
  clase fuera de rango nunca cierre la app.
- **`uses-feature` de cámara** se declara con `required="false"`: así la app puede instalarse
  en un dispositivo sin cámara y avisar por voz y en texto, en lugar de impedir la instalación.
- **Versiones.** AGP 9.2.1, Kotlin (integrado) 2.2.0, Compose BOM 2024.10.01, Navigation
  Compose 2.8.5, CameraX 1.4.0, Lifecycle 2.8.7, DataStore 1.1.1, LiteRT 2.3.0.
  `minSdk 26`, `compileSdk`/`targetSdk 36`, Java 17.
- **Un solo hilo de inferencia.** `AppContainer` crea un `Executors.newSingleThreadExecutor()`
  que se reutiliza entre escaneos, porque `ModelRunner` no es seguro entre hilos.
- **Formato de imagen de análisis.** Se usa `OUTPUT_IMAGE_FORMAT_RGBA_8888` y se convierte a
  `Bitmap` manualmente (respetando `rowStride`) y se rota con `imageInfo.rotationDegrees`.
- **Vibración, señal y voz** leen los ajustes en el momento de usarse. La señal sonora se
  limita a una cada 1,5 s y los frames se procesan como máximo a ~5 fps (200 ms).
- **El resultado** se guarda en un `StateFlow` de `AppContainer` y no se pasa por argumentos de
  navegación.
- **Pantalla de resultado**: el monto no es *live region* (para no solapar TalkBack y la voz de
  la app). El foco inicial se pide al monto; cada fila expone un único `contentDescription`
  completo.
- **Pruebas de plantilla** (`ExampleUnitTest.java` / `ExampleInstrumentedTest.java`, del paquete
  `com.example.truegem`) se eliminaron porque no correspondían a este proyecto.

- **Recorte y voto temporal solo para monedas.** Los billetes se procesan como antes. El recorte
  se hace con 20 % de margen y se mezcla con la lectura completa (65 % recorte, 35 % completa);
  para acotar el coste se limitan a 3 recortes por fotograma y el seguimiento temporal olvida las
  pistas que llevan más de 12 fotogramas sin verse. Todo el estado de seguimiento se reinicia al
  empezar un escaneo (`resetTracks`).
- **Modo de captura.** `CaptureStore` solo se activa en builds `debug` y por un extra de intent
  (`--ez capturar true`) o por el ajuste global `truegem_capturas`. Se eligió el extra de intent
  porque en algunos teléfonos el usuario `shell` no tiene `WRITE_SECURE_SETTINGS`. Las capturas
  van al almacenamiento externo propio de la app para poder extraerlas con `adb pull` sin root.

## 16. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| No reconoce nada y vuelve al inicio | Poca luz, piezas juntas o demasiada distancia | Buena luz, separar las piezas y mantener el teléfono a unos 30 cm y quieto. |
| Dice «No pude reconocer una moneda con seguridad» | El modelo duda entre dos denominaciones parecidas | Es intencionado: la moneda no se cuenta para no dar un total equivocado. Separar las monedas, acercarse y repetir. |
| Confunde siempre la misma moneda (p. ej. 20 céntimos → 50 céntimos) | El modelo es bueno con el dataset, pero la escena real (fondo, luz, ángulo) no se parece a las fotos de entrenamiento | Capturar imágenes reales con el modo de captura (sección [14](#14-modo-de-captura-para-mejorar-el-modelo)) y reentrenar. Subir el umbral de esa clase no arregla el sesgo. |
| Dice «No se pudo iniciar el reconocimiento» | Falta `best.tflite` o `labels.txt` está mal | Revisar `app/src/main/assets/` y el orden de las clases. |
| No habla | No hay voz en español instalada, o el volumen de voz está a 0 | Instalar una voz en español y subir «Volumen de voz» en Ajustes. |
| Los botones de volumen no navegan | Modo desactivado, TalkBack activo o pantalla sin elementos | Activar «Navegación con botones» en Ajustes y desactivar TalkBack. |
| El botón de encendido no navega | Limitación de Android | Usar los botones de volumen; el sistema consume el botón de encendido. |
| Aparece un borde amarillo | Es el cursor de la navegación por botones | Normal; se oculta al desactivar el modo en Ajustes. |
| No instala la nueva versión | Firma distinta de la app instalada | Desinstalar la versión anterior e instalar de nuevo. |

## 17. Fuera de alcance (v2)

Linterna automática, detección de imagen borrosa, delegado GPU, modo claro, otros idiomas,
detección de piezas de 5 céntimos o billetes de 200 soles (el modelo no tiene esas clases).
