package com.israadev.nuxlauncher.core.controls

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Penerima sentuhan mentah dari layar game. Koordinat sudah dinormalkan 0..1 terhadap area game
 * (kiri atas = 0,0), seperti yang diminta protokol mod TouchController.
 */
interface TouchSink {
    fun onDown(id: Long, x: Float, y: Float)
    fun onMove(id: Long, x: Float, y: Float)
    fun onUp(id: Long)

    /** Batalkan semua jari (mis. layar ditinggalkan, menu terbuka). */
    fun onCancelAll()
}

/**
 * Mencatat jari mana saja yang sudah dikirim ke mod pada mode berdampingan, supaya gerak dan angkat
 * hanya dikirim untuk jari yang tadinya dikirim (jari yang dipakai tombol launcher atau yang mulai
 * saat di menu tidak pernah sampai ke mod). Tanpa alokasi per kejadian; maksimum [capacity] jari.
 */
class TouchForwarder(capacity: Int = 10) {
    private val ids = LongArray(capacity)
    private var count = 0

    private fun find(id: Long): Int {
        for (i in 0 until count) if (ids[i] == id) return i
        return -1
    }

    /**
     * Mengirim jari baru ke [sink]. Mengembalikan true bila benar-benar dikirim. Id yang ternyata
     * masih tercatat (angkatnya tidak pernah terlihat, mis. menu menutup layar saat jari turun)
     * dianggap sentuhan baru, bukan dibuang.
     */
    fun down(sink: TouchSink, id: Long, x: Float, y: Float): Boolean {
        if (find(id) < 0) {
            if (count >= ids.size) return false
            ids[count++] = id
        }
        sink.onDown(id, x, y)
        return true
    }

    fun move(sink: TouchSink, id: Long, x: Float, y: Float) {
        if (find(id) >= 0) sink.onMove(id, x, y)
    }

    fun up(sink: TouchSink, id: Long) {
        val at = find(id)
        if (at < 0) return
        ids[at] = ids[--count]
        sink.onUp(id)
    }

    /** Lupakan semua jari tercatat (setelah [TouchSink.onCancelAll]). */
    fun clear() {
        count = 0
    }
}

/**
 * Dukungan mod TouchController (https://github.com/TouchController/TouchController).
 *
 * Mod itu menggambar dan menjalankan kontrol sentuhnya sendiri di dalam game; launcher hanya perlu
 * meneruskan posisi jari lewat socket Unix yang namanya diberikan lewat [ENV_SOCKET]. Seluruh
 * pekerjaan ini hanya berjalan bila modnya terpasang: tanpa mod tidak ada thread, socket, maupun
 * penelusuran kelas sama sekali.
 */
object TouchControllerSupport {
    const val ENV_SOCKET = "TOUCH_CONTROLLER_PROXY_SOCKET"

    /**
     * Apakah salah satu berkas di folder mods adalah mod TouchController. Hanya memeriksa nama
     * berkas dari daftar yang sudah dimiliki launcher (tidak membaca disk lagi). Berkas yang
     * dimatikan (.disabled) tidak dihitung. DirectTouch (pengganti proxy dari sisi mod) diabaikan
     * karena ia membawa jembatannya sendiri.
     */
    fun isModInstalled(modFileNames: List<String>): Boolean {
        for (name in modFileNames) {
            val n = name.lowercase()
            if (!n.endsWith(".jar")) continue
            if (n.contains("directtouch")) continue
            if (n.contains("touchcontroller")) return true
        }
        return false
    }

    /** Nama socket unik per peluncuran. */
    fun newSocketName(): String = "amber_tc_" + UUID.randomUUID().toString().substring(0, 8)
}

/**
 * Jembatan ke library resmi `proxy-client-android` milik TouchController.
 *
 * Library dipanggil lewat refleksi supaya launcher tetap berjalan walau nama paket atau tanda
 * tangannya berbeda antarversi: bila ada yang tidak cocok, [start] mengembalikan null dan
 * kontrol bawaan launcher dipakai seperti biasa. Metode dicari sekali saat mulai, sesudah itu tiap
 * kejadian sentuh hanya satu pemanggilan metode (tanpa pencarian ulang).
 *
 * Nomor jari ("index") yang dikirim ke mod harus selalu naik, tidak seperti id jari Android yang
 * dipakai ulang, jadi setiap sentuhan baru diberi nomor berikutnya.
 *
 * Socketnya milik launcher dan modnya yang menyambung saat game berjalan. Jalur data dibungkus
 * sehingga launcher tahu kapan mod benar-benar mengirim sesuatu ([isConnected]); selama itu belum
 * terjadi, pemanggil tidak boleh mengambil alih sentuhan dari kontrol bawaan.
 */
class TouchControllerBridge private constructor(
    private val client: Any,
    private val addPointer: Method,
    private val removePointer: Method,
    private val clearPointer: Method,
    private val log: (String) -> Unit
) : TouchSink {

    /** True setelah mod mengirim pesan pertamanya (artinya modnya hidup dan terhubung). */
    @Volatile
    var isConnected = false
        internal set

    private val ids = LongArray(MAX_POINTERS)
    private val indexes = IntArray(MAX_POINTERS)
    private var count = 0
    private var nextIndex = 1
    private var failures = 0

    @Synchronized
    override fun onDown(id: Long, x: Float, y: Float) {
        var slot = find(id)
        if (slot < 0) {
            if (count >= MAX_POINTERS) return
            slot = count++
            ids[slot] = id
            indexes[slot] = nextIndex++
        }
        sendAdd(indexes[slot], x, y)
    }

    @Synchronized
    override fun onMove(id: Long, x: Float, y: Float) {
        val slot = find(id)
        if (slot < 0) {
            onDown(id, x, y)
            return
        }
        sendAdd(indexes[slot], x, y)
    }

    @Synchronized
    override fun onUp(id: Long) {
        val slot = find(id)
        if (slot < 0) return
        val index = indexes[slot]
        count--
        ids[slot] = ids[count]
        indexes[slot] = indexes[count]
        invoke(removePointer, index)
    }

    @Synchronized
    override fun onCancelAll() {
        if (count == 0) return
        count = 0
        invoke(clearPointer)
    }

    private fun find(id: Long): Int {
        for (i in 0 until count) if (ids[i] == id) return i
        return -1
    }

    private fun sendAdd(index: Int, x: Float, y: Float) {
        invoke(addPointer, index, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    private fun invoke(method: Method, vararg args: Any) {
        if (failures >= MAX_FAILURES) return
        try {
            method.invoke(client, *args)
        } catch (t: Throwable) {
            failures++
            log("[TouchController] ${method.name} gagal (${t.cause?.message ?: t.message})" +
                if (failures >= MAX_FAILURES) "; pengiriman dihentikan" else "")
        }
    }

    /** Pencari kelas. Bagian pemindaian paket (lebih berat) hanya dipakai bila nama bawaan tidak ada. */
    fun interface ClassScanner {
        /** Kelas pertama yang nama sederhananya [simpleName], atau null. */
        fun find(simpleName: String): Class<*>?
    }

    companion object {
        private const val MAX_POINTERS = 10
        private const val MAX_FAILURES = 8

        private val CLIENT_CANDIDATES = listOf(
            "top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient",
            "top.fifthlight.touchcontroller.proxy.client.android.LauncherProxyClient",
            "top.fifthlight.touchcontroller.proxy.LauncherProxyClient"
        )
        private val TRANSPORT_CANDIDATES = listOf(
            "top.fifthlight.touchcontroller.proxy.client.android.transport.UnixSocketTransportKt",
            "top.fifthlight.touchcontroller.proxy.client.android.UnixSocketTransportKt",
            "top.fifthlight.touchcontroller.proxy.client.transport.UnixSocketTransportKt",
            "top.fifthlight.touchcontroller.proxy.client.UnixSocketTransportKt"
        )
        private const val CLIENT_SIMPLE = "LauncherProxyClient"
        private const val TRANSPORT_SIMPLE = "UnixSocketTransportKt"

        private fun load(loader: ClassLoader?, names: List<String>): Class<*>? {
            for (n in names) {
                try {
                    return Class.forName(n, false, loader)
                } catch (_: Throwable) {
                }
            }
            return null
        }

        /**
         * Membuat dan menjalankan klien proxy untuk socket bernama [socketName]. Variabel lingkungan
         * [TouchControllerSupport.ENV_SOCKET] harus sudah diisi dengan nama yang sama sebelum game
         * dimulai. Mengembalikan null (dan menulis alasannya ke [log]) bila library tidak bisa dipakai.
         */
        fun start(
            socketName: String,
            loader: ClassLoader? = TouchControllerBridge::class.java.classLoader,
            scanner: ClassScanner? = null,
            log: (String) -> Unit,
            onConnected: (TouchControllerBridge) -> Unit = {}
        ): TouchControllerBridge? {
            try {
                val clientClass = load(loader, CLIENT_CANDIDATES)
                    ?: scanner?.find(CLIENT_SIMPLE)
                if (clientClass == null) {
                    log("[TouchController] kelas klien tidak ditemukan; memakai kontrol bawaan")
                    return null
                }

                val transportClass = load(loader, TRANSPORT_CANDIDATES)
                    ?: scanner?.find(TRANSPORT_SIMPLE)
                val factory = transportClass?.methods?.firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.name == "UnixSocketTransport" &&
                        it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
                }
                if (factory == null) {
                    log("[TouchController] pembuat transport tidak ditemukan; memakai kontrol bawaan")
                    return null
                }
                val realTransport = factory.invoke(null, socketName)
                    ?: run {
                        log("[TouchController] transport kosong; memakai kontrol bawaan")
                        return null
                    }
                val transportType = factory.returnType
                if (!transportType.isInterface) {
                    log("[TouchController] jenis transport tidak dikenal (${transportType.name}); memakai kontrol bawaan")
                    return null
                }

                // Pesan pertama dari mod = mod sudah tersambung. Dihitung sekali saja.
                val self = AtomicReference<TouchControllerBridge?>()
                val fired = AtomicBoolean(false)
                val transport = Proxy.newProxyInstance(
                    transportType.classLoader ?: TouchControllerBridge::class.java.classLoader,
                    arrayOf(transportType)
                ) { _, m, args ->
                    try {
                        val result = if (args == null) m.invoke(realTransport) else m.invoke(realTransport, *args)
                        if (result == true && m.name == "receive" && fired.compareAndSet(false, true)) {
                            self.get()?.let {
                                it.isConnected = true
                                try { onConnected(it) } catch (_: Throwable) {}
                            }
                        }
                        result
                    } catch (e: InvocationTargetException) {
                        throw e.targetException ?: e
                    }
                }

                val ctor = clientClass.constructors.firstOrNull {
                    it.parameterCount == 1 && it.parameterTypes[0].isInstance(transport)
                }
                if (ctor == null) {
                    log("[TouchController] konstruktor klien tidak cocok; memakai kontrol bawaan")
                    return null
                }
                val client = ctor.newInstance(transport)

                val methods = clientClass.methods
                val runMethod = methods.firstOrNull { it.name == "run" && it.parameterCount == 0 }
                val add = methods.firstOrNull {
                    it.name == "addPointer" && it.parameterCount == 3 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        it.parameterTypes[1] == Float::class.javaPrimitiveType &&
                        it.parameterTypes[2] == Float::class.javaPrimitiveType
                }
                val remove = methods.firstOrNull {
                    it.name == "removePointer" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
                }
                val clear = methods.firstOrNull { it.name == "clearPointer" && it.parameterCount == 0 }
                if (runMethod == null || add == null || remove == null || clear == null) {
                    val found = methods.filter { it.declaringClass == clientClass }
                        .joinToString { m -> m.name + m.parameterTypes.joinToString(",", "(", ")") { it.simpleName } }
                    log("[TouchController] metode klien tidak cocok ($found); memakai kontrol bawaan")
                    return null
                }

                val bridge = TouchControllerBridge(client, add, remove, clear, log)
                self.set(bridge)
                // run() bisa berupa perulangan yang tidak kembali: jalankan di thread sendiri.
                Thread({
                    try {
                        runMethod.invoke(client)
                    } catch (t: Throwable) {
                        log("[TouchController] klien berhenti: ${t.cause?.message ?: t.message}")
                    }
                }, "TouchController-Proxy").apply { isDaemon = true }.start()
                log("[TouchController] klien proxy menunggu mod (socket $socketName)")
                return bridge
            } catch (t: Throwable) {
                log("[TouchController] gagal memulai: ${t.cause?.message ?: t.message}; memakai kontrol bawaan")
                return null
            }
        }
    }
}
