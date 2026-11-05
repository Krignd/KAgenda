# WebView 的 JS 接口不能被混淆
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.kstudio.agenda.data.KebiaoBridge { *; }

# Optional split entry points are resolved by the host contract.
-keep class com.kstudio.agenda.engines.** { *; }
-keep interface com.kstudio.agenda.data.engines.** { *; }

# org.json 为系统内置，无需保留规则

# JNI entry points and the cooperative cancellation callback.
-keep class com.kstudio.agenda.data.LocalAiNative { *; }
-keep class com.kstudio.agenda.data.LocalAiCancellation { *; }
-keep class com.kstudio.agenda.data.academic.wakeup.bean.** { *; }

# PDFBox: JPX image decoding is an optional upstream module; text extraction does not decode JPX images.
-dontwarn com.gemalto.jp2.JP2Decoder
