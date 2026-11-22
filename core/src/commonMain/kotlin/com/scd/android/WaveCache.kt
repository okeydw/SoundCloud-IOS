package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object WaveCache {
    private val lock = Any()
    private var store = WaveformStore()
    private val pending = mutableMapOf<Pair<String, String?>, Deferred<List<Float>?>>()
    private val localPending = mutableMapOf<String, Deferred<List<Float>?>>()
    private val prefetches = mutableMapOf<Pair<String, String?>, Job>()
    private val failures = mutableMapOf<String, Pair<String?, Long>>()
    private val checkedSources = LinkedHashMap<String, String>()
    private val gate = Semaphore(2)
    private val localGate = Semaphore(2)
    private val backgroundGate = Semaphore(1)
    private var generation = 0L

    fun init(prefs: KvStore) {
        locked(lock) {
            store = WaveformStore(
                initial = prefs.all().mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap(),
                save = { changes ->
                    val edit = prefs.edit()
                    changes.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putString(key, value) }
                    edit.apply()
                },
                pinned = { Downloads.isDownloaded(it) },
            )
        }
    }

    fun get(urn: String?): List<Float>? = urn?.let { locked(lock) { store.get(it) } } ?: youtubeWaveform(urn)

    fun initial(urn: String?): List<Float> =
        if (urn.isNullOrBlank()) emptyList() else get(urn) ?: fallbackWaveform(urn)

    var localSamples: suspend (String) -> List<Float>? = { null }

    internal suspend fun load(urn: String, waveformUrl: String?, refresh: Boolean = false): List<Float>? {
        if (!refresh) get(urn)?.let { return it }
        if (Prefs.offline || Health.noInternet || (!isScUrn(urn) && waveformUrl == null)) return null
        val key = urn to waveformUrl
        val request = locked(lock) {
            if (checkedSources.containsKey(urn) && (waveformUrl == null || checkedSources[urn] == waveformUrl)) {
                store.get(urn)?.let { return@locked kotlinx.coroutines.CompletableDeferred(it) }
            }
            val failed = failures[urn]
            if (failed != null && failed.first == waveformUrl && nowMillis() - failed.second < 30_000L) {
                return@locked null
            }
            pending[key] ?: run {
                val version = generation
                AppScope.scope.async(IoDispatcher, start = CoroutineStart.LAZY) {
                    try {
                        gate.withPermit {
                            if (Prefs.offline || Health.noInternet) return@withPermit null
                            val url = waveformUrl ?: Api.trackByUrn(urn).waveform_url ?: return@withPermit null
                            val samples = Api.waveform(url)
                            currentCoroutineContext().ensureActive()
                            locked(lock) {
                                if (version == generation) {
                                    store.put(urn, samples)
                                    if (checkedSources.size >= 4000 && urn !in checkedSources) checkedSources.remove(checkedSources.keys.first())
                                    checkedSources[urn] = url
                                    failures.remove(urn)
                                }
                            }
                            samples
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }.also { result ->
                        locked(lock) {
                            if (version == generation && result == null && !Prefs.offline) {
                                if (failures.size >= 300) failures.remove(failures.keys.first())
                                failures[urn] = waveformUrl to nowMillis()
                            }
                        }
                    }
                }.also { job ->
                    pending[key] = job
                    job.invokeOnCompletion {
                        locked(lock) { if (pending[key] === job) pending.remove(key) }
                    }
                }
            }
        }
        return request?.await()
    }

    private suspend fun fromFile(urn: String): List<Float>? {
        val request = locked(lock) {
            localPending[urn] ?: run {
                val version = generation
                AppScope.scope.async(IoDispatcher, start = CoroutineStart.LAZY) {
                    localGate.withPermit {
                        get(urn)?.let { return@withPermit it }
                        val samples = try {
                            localSamples(urn)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        } ?: return@withPermit null
                        currentCoroutineContext().ensureActive()
                        locked(lock) {
                            store.get(urn) ?: samples.also { if (version == generation) store.put(urn, it) }
                        }
                    }
                }.also { job ->
                    localPending[urn] = job
                    job.invokeOnCompletion {
                        locked(lock) { if (localPending[urn] === job) localPending.remove(urn) }
                    }
                }
            }
        }
        return request.await()
    }

    fun display(urn: String, url: String?) = flow {
        emit(initial(urn))
        val cached = get(urn)
        if (cached != null) emit(cached) else fromFile(urn)?.let { emit(it) }
        if (!urn.startsWith("youtube:tracks:")) {
            load(urn, url ?: Downloads.savedTrack(urn)?.waveform_url, refresh = true)?.let { emit(it) }
        }
    }.distinctUntilChanged()

    fun prefetch(urn: String?, waveformUrl: String?) {
        if (urn == null || get(urn) != null) return
        val key = urn to waveformUrl
        locked(lock) {
            if (key in prefetches) return@locked
            val job = AppScope.scope.launch(IoDispatcher, start = CoroutineStart.LAZY) {
                backgroundGate.withPermit {
                    if (get(urn) == null) display(urn, waveformUrl).collect {}
                }
            }
            prefetches[key] = job
            job.invokeOnCompletion {
                locked(lock) { if (prefetches[key] === job) prefetches.remove(key) }
            }
            job.start()
        }
    }

    fun clear(keepDownloaded: Boolean = true) {
        val jobs = locked(lock) {
            generation++
            failures.clear()
            checkedSources.clear()
            store.clear(keepDownloaded)
            (prefetches.values + pending.values + localPending.values).also {
                prefetches.clear()
                pending.clear()
                localPending.clear()
            }
        }
        jobs.forEach { it.cancel() }
    }
}
