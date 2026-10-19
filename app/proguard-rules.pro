# Reglas de R8/ProGuard.
# La build de release tiene la minificación desactivada, pero se dejan reglas por si se activa.

# LiteRT / TensorFlow Lite usa JNI y reflexión.
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**

# Vistas y componentes usados desde XML/Compose.
-keep class androidx.camera.** { *; }
