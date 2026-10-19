# Prompt para el agente: construir la app "Reconocedor de dinero" desde cero

Eres un desarrollador Android senior con experiencia en accesibilidad (TalkBack) y en inferencia de modelos en el dispositivo. Tu tarea es crear DESDE CERO, en una carpeta vacía, un proyecto de Android Studio completo que compile y funcione: una app que reconoce billetes y monedas peruanos con la cámara, cuenta cada denominación y dice el monto total en voz alta. Los usuarios son personas ciegas o con baja visión, así que todo debe funcionar por voz, vibración y TalkBack. Funciona 100% sin internet. Es una primera versión: lo mínimo funcional, pero fiel al diseño descrito.

## 0. Situación inicial

- La carpeta del proyecto está vacía. No existe ningún archivo previo. Crea todo, incluyendo Gradle, manifest, recursos, código y pruebas.
- El único archivo que NO puedes crear es el modelo `best.tflite` (binario de unos 12 MB entrenado por el usuario). El usuario lo copiará después a `app/src/main/assets/best.tflite`. Por eso:
  1. El proyecto debe compilar sin ese archivo.
  2. Si el modelo no está en tiempo de ejecución, la app no debe cerrarse: debe decir por voz "No se pudo iniciar el reconocimiento" y mostrar ese mensaje en pantalla.
  3. En el README indica este paso de forma destacada.
- Si en tu entorno no hay Android SDK o no puedes ejecutar Gradle, escribe igualmente todos los archivos, revísalos con cuidado y dime exactamente qué comandos debo ejecutar.
- Si el usuario adjuntó una imagen con el diseño (4 pantallas: Inicio, Reconocimiento, Resultado, Ajustes), úsala como referencia visual principal. Si no, la sección 5 describe el diseño con el detalle suficiente.
- Si algo es ambiguo, elige la opción más simple y accesible, anótala en el README y no me preguntes.

## 1. Stack y configuración

- Kotlin, Jetpack Compose (Material 3), ViewModel + StateFlow, Navigation Compose, CameraX (camera-core, camera-camera2, camera-lifecycle, camera-view), DataStore Preferences (solo para ajustes), LiteRT (`com.google.ai.edge.litert:litert`, la documentación de Google usa 2.1.0 como ejemplo; usa la última versión estable). Sin Hilt, sin Room, sin Accompanist.
- No inventes versiones: consulta las versiones estables vigentes de Android Gradle Plugin, Kotlin, Compose BOM, Navigation Compose, CameraX, Lifecycle, DataStore y LiteRT, y usa un conjunto compatible. Defínelas en `gradle/libs.versions.toml`. Usa el plugin `org.jetbrains.kotlin.plugin.compose`.
- Gradle Kotlin DSL, Java 17, `applicationId` y namespace `com.example.reconocedordinero`, nombre de app "Reconocedor de dinero", compileSdk y targetSdk los más recientes estables, minSdk 26.
- En `app/build.gradle.kts` es obligatorio: `android { androidResources { noCompress += "tflite" } }` (sin esto el modelo no se puede abrir desde assets).
- Manifest: permiso CAMERA, `uses-feature` de cámara, orientación vertical bloqueada, NO declares el permiso INTERNET, sin analítica ni telemetría.
- Ícono de app adaptable y simple (vectorial: fondo amarillo #FFD84D y símbolo "S/" oscuro).
- Inyección de dependencias manual: una clase `Application` con un `AppContainer` que crea una sola vez `SettingsRepository`, `SpeechManager`, `HapticManager`, `BeepPlayer` y el `ModelRunner` (este último dentro de try/catch, porque puede fallar si falta el modelo).

## 2. Estructura de archivos a crear

```
ReconocedorDinero/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/libs.versions.toml
  README.md
  app/
    build.gradle.kts
    proguard-rules.pro
    src/main/AndroidManifest.xml
    src/main/assets/labels.txt        (contenido fijo en 3.2)
    src/main/assets/                  (aquí pondrá el usuario best.tflite)
    src/main/res/values/strings.xml, colors.xml, themes.xml
    src/main/res/...                  (ícono adaptable)
    src/main/java/com/example/reconocedordinero/
      ReconocedorApp.kt               (Application + AppContainer)
      MainActivity.kt                 (NavHost)
      ml/ModelRunner.kt               (contenido fijo en 3.1)
      domain/MoneyFormatter.kt
      domain/StabilityFilter.kt
      audio/SpeechManager.kt
      audio/HapticManager.kt
      audio/BeepPlayer.kt
      data/SettingsRepository.kt
      ui/theme/                       (Color, Type, Theme)
      ui/home/HomeScreen.kt
      ui/scan/ScanScreen.kt, ScanViewModel.kt, DetectionOverlay.kt
      ui/result/ResultScreen.kt, ResultViewModel.kt
      ui/settings/SettingsScreen.kt, SettingsViewModel.kt
      ui/help/HelpScreen.kt
    src/test/java/com/example/reconocedordinero/
      MoneyFormatterTest.kt
      StabilityFilterTest.kt
```

## 3. Archivos con contenido fijo (cópialos tal cual)

### 3.1 `app/src/main/java/com/example/reconocedordinero/ml/ModelRunner.kt`

Este archivo ya está probado en su lógica. Cópialo EXACTAMENTE. Si te da un error de compilación, corrígelo con el cambio mínimo y menciónalo en el README. Contrato:

- `ModelRunner(context)` lanza excepción si `best.tflite` o `labels.txt` no existen en assets.
- `detect(bitmap)` recibe un bitmap YA rotado con la orientación correcta y devuelve cajas en coordenadas de ese mismo bitmap.
- No es seguro entre hilos: llámalo siempre desde un único hilo dedicado.
- `Dinero.resumen(dets, labels)` devuelve el conteo por etiqueta y el total en céntimos, e ignora etiquetas fuera del mapa (como `rev`).

```kotlin
package com.example.reconocedordinero.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.google.ai.edge.litert.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Caja en coordenadas del bitmap original. */
data class Detection(
    val cls: Int,
    val score: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/** Valores en céntimos para evitar errores de coma flotante. Etiquetas fuera del mapa (ej. "rev") se ignoran. */
object Dinero {
    val CENTIMOS = mapOf(
        "10-soles" to 1000, "20-soles" to 2000, "50-soles" to 5000, "100-soles" to 10000,
        "m1" to 100, "m2" to 200, "m5" to 500,
        "m10" to 10, "m20" to 20, "m50" to 50,
    )

    /** Devuelve (conteo por etiqueta, total en céntimos). */
    fun resumen(dets: List<Detection>, labels: List<String>): Pair<Map<String, Int>, Int> {
        val conteo = dets.map { labels[it.cls] }
            .filter { it in CENTIMOS }
            .groupingBy { it }
            .eachCount()
        val total = conteo.entries.sumOf { CENTIMOS.getValue(it.key) * it.value }
        return conteo to total
    }
}

/**
 * Requiere en assets/: best.tflite y labels.txt (una clase por línea, en el orden de model.names).
 * Dependencia: implementation("com.google.ai.edge.litert:litert:<última versión>")
 * En build.gradle.kts: android { androidResources { noCompress += "tflite" } }
 */
class ModelRunner(context: Context, modelName: String = "best.tflite") {

    val labels: List<String> = context.assets.open("labels.txt")
        .bufferedReader().readLines().filter { it.isNotBlank() }

    private val size = 640
    private val interpreter: Interpreter
    private val outShape: IntArray        // [1, 4+nc, N] o [1, N, 4+nc]
    private val channelsFirst: Boolean
    private val inputChannelsFirst: Boolean   // true si la entrada es [1, 3, 640, 640]

    init {
        val fd = context.assets.openFd(modelName)
        val model = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        outShape = interpreter.getOutputTensor(0).shape()
        channelsFirst = outShape[1] < outShape[2]
        inputChannelsFirst = interpreter.getInputTensor(0).shape()[1] == 3
        Log.d(TAG, "in=${interpreter.getInputTensor(0).shape().toList()} out=${outShape.toList()}")
    }

    fun detect(bitmap: Bitmap, confThr: Float = 0.4f, iouThr: Float = 0.5f): List<Detection> {
        // 1. Letterbox a 640x640 con relleno gris 114 (igual que Ultralytics en inferencia)
        val scale = minOf(size / bitmap.width.toFloat(), size / bitmap.height.toFloat())
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        val padX = (size - w) / 2f
        val padY = (size - h) / 2f
        val boxed = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(boxed).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(Bitmap.createScaledBitmap(bitmap, w, h, true), padX, padY, null)
        }

        // 2. RGB float32 normalizado 0..1, en el orden que pida el tensor de entrada
        val input = ByteBuffer.allocateDirect(4 * size * size * 3).order(ByteOrder.nativeOrder())
        val px = IntArray(size * size)
        boxed.getPixels(px, 0, size, 0, 0, size, size)
        if (inputChannelsFirst) {            // [1, 3, 640, 640]: un plano por canal (R, G, B)
            for (shift in intArrayOf(16, 8, 0)) {
                for (p in px) input.putFloat(((p shr shift) and 0xFF) / 255f)
            }
        } else {                             // [1, 640, 640, 3]: píxeles intercalados
            for (p in px) {
                input.putFloat(((p shr 16) and 0xFF) / 255f)
                input.putFloat(((p shr 8) and 0xFF) / 255f)
                input.putFloat((p and 0xFF) / 255f)
            }
        }
        input.rewind()

        // 3. Inferencia
        val n = if (channelsFirst) outShape[2] else outShape[1]
        val ch = if (channelsFirst) outShape[1] else outShape[2]
        val raw = Array(1) {
            if (channelsFirst) Array(ch) { FloatArray(n) } else Array(n) { FloatArray(ch) }
        }
        interpreter.run(input, raw)
        fun v(c: Int, i: Int) = if (channelsFirst) raw[0][c][i] else raw[0][i][c]

        // 4. Decodificar. Si las coordenadas vienen normalizadas (0..1) se escalan a 640.
        var maxW = 0f
        for (i in 0 until n) maxW = maxOf(maxW, v(2, i))
        val k = if (maxW <= 1.5f) size.toFloat() else 1f
        val nc = ch - 4

        val cand = ArrayList<Detection>()
        for (i in 0 until n) {
            var best = 0
            var score = 0f
            for (c in 0 until nc) {
                val s = v(4 + c, i)
                if (s > score) { score = s; best = c }
            }
            if (score < confThr) continue
            val cx = v(0, i) * k
            val cy = v(1, i) * k
            val bw = v(2, i) * k
            val bh = v(3, i) * k
            // Deshacer letterbox y volver a coordenadas del bitmap original
            cand += Detection(
                best, score,
                ((cx - bw / 2) - padX) / scale,
                ((cy - bh / 2) - padY) / scale,
                ((cx + bw / 2) - padX) / scale,
                ((cy + bh / 2) - padY) / scale,
            )
        }
        return nms(cand, iouThr).take(50)
    }

    private fun nms(dets: List<Detection>, iouThr: Float): List<Detection> {
        val keep = mutableListOf<Detection>()
        for (d in dets.sortedByDescending { it.score }) {
            if (keep.none { it.cls == d.cls && iou(it, d) > iouThr }) keep += d
        }
        return keep
    }

    private fun iou(a: Detection, b: Detection): Float {
        val ix = maxOf(0f, minOf(a.x2, b.x2) - maxOf(a.x1, b.x1))
        val iy = maxOf(0f, minOf(a.y2, b.y2) - maxOf(a.y1, b.y1))
        val inter = ix * iy
        val union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun close() = interpreter.close()

    private companion object {
        const val TAG = "ModelRunner"
    }
}
```

### 3.2 `app/src/main/assets/labels.txt`

Exactamente estas 11 líneas, en este orden (es el orden de las clases del modelo; no lo cambies ni dejes líneas vacías en medio):

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

Significado: `10-soles`, `20-soles`, `50-soles` y `100-soles` son billetes; `m1`, `m2` y `m5` son monedas de 1, 2 y 5 soles; `m10`, `m20` y `m50` son monedas de 10, 20 y 50 céntimos. `rev` se ignora siempre (no se cuenta, no se dibuja, no se dice). Una etiqueta es billete si termina en `-soles`; si no, es moneda.

## 4. Lógica de dominio

### MoneyFormatter (con pruebas unitarias)

Todos los valores en céntimos (`Int`), nunca `Double`. Funciones:

- Texto en pantalla: `S/ 35,00` (coma decimal, siempre dos decimales).
- Frase de total hablada: "Total: 35 soles", "Total: 35 soles con 50 céntimos", "Total: 30 céntimos", "Total: 1 sol", "Total: 1 sol con 10 céntimos".
- Frase de detalle con singular y plural correctos y artículos: "Un billete de 20 soles, una moneda de 5 soles, dos monedas de 1 sol, una moneda de 50 céntimos, tres monedas de 10 céntimos".
- Nombre de fila en Resultado: "Billete S/ 20", "Moneda S/ 5", "Moneda S/ 0,50".
- Etiqueta corta del overlay: "Billete S/ 20" para billetes; "S/ 1", "S/ 5", "S/ 0,50" para monedas.
- Orden: billetes primero y luego monedas, cada grupo de mayor a menor valor.
- Pruebas unitarias de totales como 0,30, 1,00, 1,10, 35,00 y 35,50, y de singular y plural.

### StabilityFilter (con pruebas unitarias)

Guarda el conteo por etiqueta (ignorando `rev`) de los últimos 8 frames procesados. Es estable cuando el mismo conteo, con al menos una pieza, aparece en 5 o más de esos 8. Devuelve el conteo ganador. Se reinicia al empezar un nuevo escaneo. Pruebas: estable con conteo repetido, no estable con conteos cambiantes, no estable sin piezas.

## 5. Diseño visual (copiar la imagen adjunta)

- Tema oscuro fijo; ignora el modo claro del sistema. Colores: fondo `#12161F`, superficie (tarjetas, vista de cámara, barra de estado) `#2A3042`, borde de botones outline y divisorias `#3B4358`, amarillo `#FFD84D` con texto oscuro `#12161F` encima, texto blanco, texto secundario `#9AA3B5`.
- Esquinas redondeadas de 20 dp en botones y tarjetas, margen lateral de 24 dp, tipografía sans-serif del sistema, títulos en negrita. Respeta los insets de las barras del sistema.
- Iconos de Material Icons: CameraAlt, Settings, HelpOutline, WifiOff, ArrowBack, CheckCircle, Stop, VolumeUp, Refresh, PlayArrow.
- Botones principales de al menos 72 dp de alto y texto de al menos 20 sp.

### 5.1 Inicio
- Sin barra superior. Arriba a la izquierda el título "Reconocedor de dinero" (28 sp, negrita, blanco).
- Botón gigante amarillo que ocupa cerca del 55% de la altura: icono de cámara grande centrado sobre el texto "Reconocer dinero" (negrita, texto oscuro).
- Debajo, dos botones outline a ancho completo con icono a la izquierda y texto centrado: "Ajustes" y "Ayuda".
- Pie centrado: icono WifiOff pequeño + "Funciona sin internet" en gris.
- Voz una sola vez al abrir la app: "Reconocedor de dinero. Toque el botón grande para reconocer."

### 5.2 Reconocimiento
- Barra superior: flecha atrás + título "Reconocer".
- Vista de cámara trasera (PreviewView con ScaleType FIT_CENTER) dentro de una tarjeta de superficie redondeada que ocupa casi todo el alto disponible.
- Overlay (Canvas) sobre la vista: por cada detección válida dibuja un recuadro de borde amarillo de 3 dp con esquinas redondeadas y una etiqueta amarilla pegada a la esquina superior izquierda, con texto oscuro (etiquetas cortas de la sección 4). No dibujes `rev`. El overlay es decorativo e invisible para TalkBack.
- Debajo, tarjeta de estado (color superficie): con detecciones muestra icono CheckCircle y texto amarillo en negrita "Dinero detectado"; sin detecciones muestra "Buscando dinero..." en gris, sin icono.
- Debajo, botón outline "Detener" con icono Stop. El botón Atrás hace lo mismo: vuelve a Inicio.

### 5.3 Resultado
- Barra superior: flecha atrás + "Resultado".
- Centrado: "Monto total" en gris (20 sp) y debajo el monto en amarillo, negrita y enorme (56 sp o más), con formato `S/ 35,00`.
- Lista: una fila por denominación con el nombre a la izquierda ("Billete S/ 20") y la cantidad a la derecha ("× 1"), separadas por una línea fina `#3B4358`. La cantidad va en una columna de ancho fijo, alineada a la derecha y centrada verticalmente en su fila (en el diseño de referencia estaba desalineada: no lo copies).
- Abajo: botón amarillo "Repetir mensaje" (icono VolumeUp) y debajo botón outline "Escanear de nuevo" (icono Refresh).
- Al entrar lee automáticamente el total y el detalle. El foco inicial de TalkBack va al monto. Cada fila tiene un solo contentDescription completo ("Un billete de 20 soles").

### 5.4 Ajustes
- Barra superior: flecha atrás + "Ajustes".
- "Velocidad de voz": slider con pista activa amarilla, inactiva `#3B4358` y control amarillo. Rango 0,5x a 2,0x, paso 0,1, por defecto 1,0.
- "Volumen de voz": slider de 0 a 100%, por defecto 80%.
- "Vibración": fila con Switch amarillo, activado por defecto, y divisoria debajo.
- "Señal sonora": fila con Switch amarillo, activado por defecto, y divisoria debajo.
- Abajo: botón amarillo "Probar voz" (icono PlayArrow) que lee "Total: 35 soles. Un billete de 20 soles, un billete de 10 soles, una moneda de 5 soles." con los ajustes actuales.
- Cada cambio se guarda al instante en DataStore y se aplica de inmediato. Los sliders exponen `stateDescription` ("1,2 veces", "80 por ciento").

### 5.5 Ayuda (no está en la imagen, pero el botón existe en Inicio: hazla mínima)
- Misma barra y estilo. Texto grande con las instrucciones y un botón amarillo "Escuchar instrucciones" que las lee en voz alta: usar la cámara trasera, unos 30 cm sobre una superficie plana, piezas separadas sin superponerse, buena luz, y cómo repetir el resultado.

### 5.6 Navegación
Inicio -> Reconocimiento -> Resultado (automático al estabilizarse). "Escanear de nuevo" vuelve a Reconocimiento reemplazando Resultado. Atrás desde Resultado vuelve a Inicio. Inicio -> Ajustes e Inicio -> Ayuda, con flecha atrás a Inicio. El resultado se comparte entre pantallas mediante un `StateFlow` guardado en `AppContainer`, no con argumentos de navegación.

## 6. Flujo de escaneo (ScanViewModel + ScanScreen)

- Permiso de cámara con `rememberLauncherForActivityResult(RequestPermission())`. Antes de mostrar el diálogo del sistema, di por voz por qué se necesita la cámara. Si lo niega, dilo por voz y muestra un botón para abrir los ajustes de la app.
- Al entrar: vibración corta y voz "Coloque el dinero sobre una superficie plana, a unos 30 centímetros del teléfono. Buscando."
- CameraX con `Preview` e `ImageAnalysis`, ambos en proporción 4:3 (ResolutionSelector, resolución de análisis cercana a 1280x960), formato de salida RGBA_8888, backpressure `KEEP_ONLY_LATEST` y un Executor de un solo hilo dedicado. Nunca inferencia en el hilo principal.
- Procesa como máximo unos 5 frames por segundo (descarta los que lleguen antes de 200 ms desde el último procesado).
- Por cada frame: `ImageProxy.toBitmap()`, rótalo con `imageInfo.rotationDegrees`, llama a `ModelRunner.detect()` y cierra el `ImageProxy` siempre (usa `use` o `finally`).
- Publica en el estado: las detecciones del último frame, el tamaño del bitmap (para el overlay) y si hay piezas.
- Overlay: mapea las cajas del bitmap al preview con escala = `min(anchoVista / anchoBitmap, altoVista / altoBitmap)` y desplazamiento centrado en ambos ejes (equivale a FIT_CENTER).
- Si aparecen detecciones y el ajuste está activo, reproduce una señal sonora breve (ToneGenerator o SoundPool), sin repetirla en cada frame (máximo una cada 1,5 s).
- Alimenta `StabilityFilter` en cada frame procesado. Al estabilizarse: vibración de éxito, detén y libera la cámara, guarda el resultado (conteo y total en céntimos) y navega a Resultado automáticamente.
- Si pasan 20 s sin resultado estable: voz "No detecto dinero. Acerque o aleje un poco el teléfono y revise que haya luz", vibración de error y vuelve a Inicio.
- Pantalla siempre encendida mientras escanea (`keepScreenOn`). Libera cámara y recursos al salir, rotar o pasar a segundo plano, sin fugas.
- Si el `ModelRunner` no se pudo crear: voz "No se pudo iniciar el reconocimiento", mensaje visible en pantalla, vibración de error y no abras la cámara.

## 7. Voz, vibración y sonido

- `SpeechManager`: `TextToSpeech` con locale es-PE; si no está disponible, es-419 y luego es-ES. Aplica velocidad (`setSpeechRate`) y volumen (parámetro `KEY_PARAM_VOLUME`) de los ajustes. Usa audio focus transitorio con ducking y retrasa unos 500 ms el primer mensaje tras navegar para no pisar el anuncio de TalkBack. Cola con `QUEUE_FLUSH` para mensajes nuevos. Si no hay motor TTS instalado, muestra un mensaje en pantalla.
- No marques el monto de Resultado como live region (evita que TalkBack y tu voz hablen a la vez).
- `HapticManager`: patrones distintos con `VibrationEffect` para inicio de escaneo, éxito y error. Respeta el ajuste de vibración.
- `BeepPlayer`: señal breve. Respeta el ajuste de señal sonora.

## 8. Accesibilidad (obligatoria)

- Todo el flujo se completa con TalkBack activo sin mirar la pantalla: contentDescription y roles en español, títulos de pantalla como heading, orden de foco lógico, nada decorativo enfocable.
- Soporta `fontScale` hasta 200% sin truncar ni solapar (scroll vertical si hace falta). Nunca dependas solo del color.
- Solo toques simples, sin gestos complejos y sin tiempos que cierren una pantalla sin avisar (excepto el límite de 20 s del escaneo, que avisa por voz).
- Todas las cadenas de texto en `strings.xml` en español.

## 9. Fuera de alcance en esta versión (deja solo un comentario `// TODO v2`)

Linterna automática, detección de imagen borrosa, delegado GPU, modo claro, otros idiomas, detección del valor de piezas de 5 céntimos o billetes de 200 soles (el modelo no tiene esas clases).

## 10. Orden de trabajo

Después de cada paso compila (`./gradlew assembleDebug`) y corrige errores antes de seguir.

1. Esqueleto de Gradle, manifest, recursos, tema y las 5 pantallas estáticas fieles a la sección 5, con la navegación.
2. Copia `ModelRunner.kt` y `labels.txt` (sección 3) e intégralos en `AppContainer`. Comprueba que el proyecto compila sin el modelo y que la app maneja su ausencia sin cerrarse.
3. `MoneyFormatter` y `StabilityFilter` con sus pruebas (`./gradlew test` debe pasar).
4. Flujo de escaneo completo (sección 6) con overlay, estabilización y permisos.
5. Resultado con voz.
6. Ajustes persistidos y aplicados, y pantalla Ayuda.
7. Revisión de accesibilidad (sección 8) y README.

## 11. Entrega

- Proyecto completo que compile con `./gradlew assembleDebug` y cuyas pruebas pasen con `./gradlew test`.
- `README.md` corto con: cómo abrir el proyecto en Android Studio, el paso manual de copiar `best.tflite` a `app/src/main/assets/`, cómo instalar la app en un teléfono real, qué buscar en Logcat (etiqueta `ModelRunner`, que imprime las formas de entrada y salida; la salida esperada es `[1, 15, 8400]` y la entrada `[1, 3, 640, 640]` o `[1, 640, 640, 3]`) y las decisiones que tomaste ante ambigüedades.
- Al final, dame un resumen de 10 líneas como máximo: qué quedó hecho, qué no pudiste verificar (por ejemplo, la ejecución en un dispositivo) y cualquier cambio que hayas hecho a `ModelRunner.kt`.

## 12. Criterios de aceptación

- Las pantallas Inicio, Reconocimiento, Resultado y Ajustes se ven como la imagen de referencia: colores, textos, orden de elementos, iconos y cajas amarillas con etiqueta sobre la cámara.
- Con TalkBack activo se completa abrir, escanear, escuchar el total, repetir, cambiar ajustes y volver a escanear sin mirar la pantalla.
- Con un billete de 20, uno de 10 y una moneda de 5 juntos, la app dice 35 soles y lista cada pieza.
- Funciona en modo avión y no hay fugas de cámara al salir o rotar.
- Sin `best.tflite` o sin motor de voz o cámara, la app no se cierra: avisa por voz y en texto.
