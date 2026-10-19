# Reconocedor de dinero

App Android (Kotlin + Jetpack Compose, Material 3) para reconocer billetes y monedas peruanas
con la cámara, contar cada denominación y decir el monto total en voz alta. Está pensada para
personas ciegas o con baja visión: todo el flujo se completa con TalkBack, voz, vibración y
señal sonora. Funciona 100% sin internet.

## 1. Abrir y compilar

1. Abrir la carpeta del proyecto en Android Studio (versión reciente; el proyecto usa
   AGP 9.2.1 y Gradle 9.4.1 con Kotlin integrado).
2. Esperar la sincronización de Gradle.
3. Compilar: `./gradlew assembleDebug` (o el botón *Run* de Android Studio).
4. Pruebas unitarias: `./gradlew test`.

En línea de comandos hace falta un JDK 17+ en `JAVA_HOME`. Android Studio ya trae uno.

## 2. El modelo `best.tflite`

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
rev
```

Si el modelo falta o no se puede abrir, la app **no se cierra**: dice por voz
«No se pudo iniciar el reconocimiento» y lo muestra en pantalla.

## 3. Instalar en un teléfono real

1. Activar *Opciones de desarrollador* y *Depuración USB* en el teléfono.
2. Conectarlo por USB y aceptar la huella de depuración.
3. Ejecutar `./gradlew installDebug` o usar *Run* en Android Studio.
4. Conceder el permiso de cámara la primera vez.

Para probar sin mirar la pantalla, activar TalkBack en el teléfono. La app está pensada para
usarse con toques simples.

## 4. Logcat

Filtrar por la etiqueta `ModelRunner`. Al arrancar el reconocimiento imprime las formas reales
del modelo:

```
ModelRunner: in=[1, 3, 640, 640] out=[1, 15, 8400]
```

La salida esperada es `[1, 15, 8400]`; la entrada puede ser `[1, 3, 640, 640]` o
`[1, 640, 640, 3]`. `ModelRunner` detecta automáticamente el orden de canales y si las
coordenadas vienen normalizadas.

Otras etiquetas útiles: `AppContainer` (fallo al cargar el modelo), `SpeechManager` (motor de
voz), `ScanScreen`/`ScanViewModel` (cámara e inferencia).

## 5. Decisiones ante ambigüedades

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

## 6. Accesibilidad

- Todo el texto está en `strings.xml` (español).
- Títulos como `heading`, botones con `contentDescription`, orden de foco lógico y overlay
  decorativo invisible para TalkBack.
- El layout respeta `fontScale` alto (hasta 200 %) con scroll vertical en Resultado, Ajustes y
  Ayuda.
- Los sliders exponen `stateDescription` («1,2 veces», «80 por ciento»).
- La cámara se libera al salir, rotar o pasar a segundo plano. La pantalla permanece encendida
  solo durante el escaneo.

## 7. Fuera de alcance (v2)

Linterna automática, detección de imagen borrosa, delegado GPU, modo claro, otros idiomas,
detección de piezas de 5 céntimos o billetes de 200 soles (el modelo no tiene esas clases).
