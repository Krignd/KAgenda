# WebView 的 JS 接口不能被混淆
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.kstudio.agenda.data.KebiaoBridge { *; }

# org.json 为系统内置，无需保留规则
