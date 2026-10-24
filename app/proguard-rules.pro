# ==== Aturan R8 untuk AmberLauncher ====
# Tujuan: memangkas kode pustaka yang tidak terpakai (terutama ribuan ikon
# material-icons-extended) supaya APK lebih kecil dan aplikasi lebih ringan dimuat.
# Kode aplikasi sendiri TIDAK diubah/dibuang/diganti namanya, karena kode native
# (JNI) memanggil Java lewat nama dan Gson membaca field lewat refleksi.

# Nama kelas & metode tidak diacak (JNI mencarinya lewat nama; log crash tetap terbaca).
-dontobfuscate
-dontwarn **

# Info yang dibutuhkan refleksi (Gson, enum, kelas dalam) dan stack trace yang terbaca.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable,Exceptions

# Seluruh kode aplikasi dibiarkan utuh (model JSON Gson, jembatan JNI, refleksi).
-keep class com.israadev.nuxlauncher.** { *; }

# Kelas Java yang dipanggil dari pustaka native (libpojavexec, SDL, bytehook, dll.).
-keep class com.movtery.** { *; }
-keep class org.lwjgl.** { *; }
-keep class org.libsdl.** { *; }
-keep class com.oracle.** { *; }
-keep class com.bytedance.** { *; }
-keep class androidx.graphics.path.** { *; }

# Klien proxy TouchController hanya dipanggil lewat refleksi; tanpa ini R8 membuangnya.
-keep class top.fifthlight.touchcontroller.** { *; }

# Semua metode native tidak boleh dihapus atau diganti namanya.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
