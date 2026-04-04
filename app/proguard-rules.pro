-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowobfuscation class com.kooritea.fcmfix.XposedMain { 
    public <init>(...);
}

-adaptresourcefilecontents META-INF/xposed/java_init.list

-adaptresourcefilenames
-repackageclasses
-allowaccessmodification