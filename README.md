Reconocedor de dinero

Proyecto Android (Kotlin + Jetpack Compose) para reconocer billetes y monedas peruanas. Sigue estos pasos para abrir y compilar:

1. Abrir en Android Studio (Arctic Fox o posterior). Si falta el Gradle wrapper, Android Studio descargará la versión compatible.
2. Copiar el fichero del modelo best.tflite en: app/src/main/assets/best.tflite
3. Ejecutar: ./gradlew assembleDebug
4. Ejecutar pruebas unitarias: ./gradlew test

Notas importantes:
- Si falta best.tflite la app mostrará y dirá "No se pudo iniciar el reconocimiento" y no cerrará.
- Buscar en Logcat la etiqueta ModelRunner para ver shapes de entrada/salida.

Decisiones y supuestos mínimos están documentados en PROMPT_Reconocedor_de_dinero.md (archivo original).