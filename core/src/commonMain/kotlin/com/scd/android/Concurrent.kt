package com.scd.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object AppScope {
    val scope = CoroutineScope(SupervisorJob() + IoDispatcher)
}

class SyncSet<T> {
    private val set = HashSet<T>()

    fun add(item: T): Boolean = locked(set) { set.add(item) }
    fun addAll(items: Collection<T>) { locked(set) { set.addAll(items) } }
    fun remove(item: T): Boolean = locked(set) { set.remove(item) }
    operator fun contains(item: T): Boolean = locked(set) { item in set }
    operator fun plusAssign(item: T) { add(item) }
    operator fun plusAssign(items: Collection<T>) { addAll(items) }
    val size: Int get() = locked(set) { set.size }
    fun clear() { locked(set) { set.clear() } }
    fun isEmpty(): Boolean = locked(set) { set.isEmpty() }
    fun toList(): List<T> = locked(set) { set.toList() }
}

class SyncMap<K, V> {
    private val map = HashMap<K, V>()

    operator fun get(key: K): V? = locked(map) { map[key] }
    operator fun set(key: K, value: V) { locked(map) { map[key] = value } }
    fun remove(key: K): V? = locked(map) { map.remove(key) }
    fun containsKey(key: K): Boolean = locked(map) { map.containsKey(key) }
    fun putAll(from: Map<K, V>) { locked(map) { map.putAll(from) } }
    fun getOrPut(key: K, make: () -> V): V = locked(map) { map.getOrPut(key, make) }
    val size: Int get() = locked(map) { map.size }
    fun clear() { locked(map) { map.clear() } }
    fun snapshot(): Map<K, V> = locked(map) { HashMap(map) }
    fun filterKeys(predicate: (K) -> Boolean): Map<K, V> = snapshot().filterKeys(predicate)
}

class SyncInt(initial: Int = 0) {
    private val lock = Any()
    private var value = initial

    fun get(): Int = locked(lock) { value }
    fun set(v: Int) { locked(lock) { value = v } }
    fun incrementAndGet(): Int = locked(lock) { ++value }
    fun getAndSet(v: Int): Int = locked(lock) { val old = value; value = v; old }
}

class SyncFlag(initial: Boolean = false) {
    private val lock = Any()
    private var value = initial

    fun get(): Boolean = locked(lock) { value }
    fun set(v: Boolean) { locked(lock) { value = v } }
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean = locked(lock) {
        if (value == expect) {
            value = update
            true
        } else false
    }
}

fun HttpEngine.fetchBlocking(req: HttpRequest): Pair<Int, ByteArray> = stream(req) { s ->
    if (s.code !in 200..299) return@stream s.code to ByteArray(0)
    val buf = ByteArray(16 * 1024)
    val parts = mutableListOf<ByteArray>()
    var total = 0
    while (true) {
        val n = s.read(buf)
        if (n < 0) break
        if (n == 0) continue
        parts.add(buf.copyOf(n))
        total += n
    }
    val all = ByteArray(total)
    var pos = 0
    for (p in parts) {
        p.copyInto(all, pos)
        pos += p.size
    }
    s.code to all
}
