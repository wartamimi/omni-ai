# OmniAI release rules. Most libraries ship their own consumer rules.

# kotlinx.serialization: keep generated serializers for @Serializable classes (incl. navigation routes).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.omniai.**$$serializer { *; }

# Enum names are persisted in Room/DataStore and exported files.
-keepclassmembers enum com.omniai.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# PdfBox-Android optional JP2/JBIG2 decoders are not bundled.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# OkHttp / Okio platform classes
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
