# ---------------------------------------------------------------
# Reglas de ofuscación (R8/ProGuard)
# ---------------------------------------------------------------

# NewPipeExtractor: reglas obligatorias indicadas por el propio proyecto
# (mantiene Rhino/JavaScript engine usado por el extractor de YouTube).
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# Media3 / ExoPlayer y Room ya incorporan sus propias reglas de keep.