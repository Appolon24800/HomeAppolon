# Version-matched official rules are also embedded in the dependencies. Keep local
# copies so the release contract is explicit without keeping entire libraries.
# https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/rules/common.pro
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$* Companion;
}

-keepnames @kotlinx.serialization.internal.NamedCompanion class *
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembernames class * {
    static <1> *;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# https://github.com/square/retrofit/blob/3.0.0/retrofit/src/main/resources/META-INF/proguard/retrofit2.pro
# Generic suspend return types and proxy interfaces must survive R8 full mode.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# https://github.com/square/okhttp/blob/parent-5.1.0/okhttp/okhttp3.pro
# Optional JVM TLS providers are not part of the Android runtime.
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# https://github.com/square/okio/blob/master/README.md#r8--proguard
# Current Okio needs no keep rules. Older artifacts reference this annotation.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Retrofit's default @Query/@Field converter uses Enum.toString()/name(). Keep
# API enum constant names, not unrelated UI enums or whole DTO classes.
-keepclassmembers enum dev.appolon.homelab.arr.api.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# SignalR 8.0.12 has no consumer rules. Its Gson protocol reflects these fields
# without @SerializedName. Keep field names and prevent model removal in full mode.
# https://github.com/dotnet/aspnetcore/tree/v8.0.12/src/SignalR/clients/java/signalr
-keep,allowoptimization class com.microsoft.signalr.HandshakeRequestMessage {
    <fields>;
}
-keep,allowoptimization class com.microsoft.signalr.HandshakeResponseMessage {
    <fields>;
    <init>();
}
-keep,allowoptimization class * extends com.microsoft.signalr.HubMessage {
    <fields>;
}
-keep,allowoptimization,allowobfuscation class com.microsoft.signalr.TypeReference
-keep,allowoptimization,allowobfuscation class * extends com.microsoft.signalr.TypeReference

# SLF4J 1.7 discovers these Android binding classes by their fixed binary names.
# https://www.slf4j.org/manual.html
-keep class org.slf4j.impl.Static*Binder {
    *;
}

# Application DTOs use generated kotlinx.serialization serializers. SignalR
# callbacks take Map, so no application data classes need Java reflection keeps.
