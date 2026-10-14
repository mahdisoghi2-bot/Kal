package com.kal

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kal.analysis.DemoAnalyzer
import com.kal.analysis.FoodAnalyzer
import com.kal.analysis.FoodDb
import com.kal.analysis.TfliteFoodAnalyzer
import com.kal.core.Calories
import com.kal.core.Meal
import com.kal.data.KalStore
import com.kal.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class Ui(
    val loaded: Boolean = false,
    val maintenance: Int = 2200,
    val deficit: Int = 500,
    val limit: Int = 1700,
    val eaten: Int = 0,
    val onboarded: Boolean = true,
    val hintUses: Int = 99,
) {
    val fraction: Float get() = Calories.fraction(eaten, limit)
    val remaining: Int get() = Calories.remaining(limit, eaten)
}

enum class Phase { Idle, Searching, Locked, NotFound }

data class ScanUi(
    val phase: Phase = Phase.Idle,
    val box: RectF? = null,            // normalised in the upright analysis frame
    val frameW: Int = 3, val frameH: Int = 4,
    val label: String = "",
    val kcal: Int = 0,
    val lowLight: Boolean = false,
    val warming: Boolean = true,
    val lockCount: Int = 0,
)

class KalViewModel(app: Application) : AndroidViewModel(app) {
    private val store = KalStore(app)
    private val db = FoodDb.load(app)
    @Volatile private var analyzer: FoodAnalyzer? = null

    private val ticker = flow { while (true) { emit(Unit); delay(60_000) } }

    val ui: StateFlow<Ui> = combine(store.flow, ticker) { s, _ -> toUi(s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ui())

    private fun toUi(s: Snapshot): Ui {
        val zone = ZoneId.systemDefault()
        val today = Calories.dayKey(LocalDateTime.now())
        val eaten = s.meals.filter {
            Calories.dayKey(LocalDateTime.ofInstant(Instant.ofEpochMilli(it.epochMs), zone)) == today
        }.sumOf { it.kcal }
        return Ui(
            loaded = s.loaded, maintenance = s.maintenance, deficit = s.deficit,
            limit = Calories.dailyLimit(s.maintenance, s.deficit), eaten = eaten,
            onboarded = s.onboarded, hintUses = s.hintUses,
        )
    }

    // ---------------- scan state machine (touched only from the analysis thread) ----------------
    private val _scan = MutableStateFlow(ScanUi())
    val scan: StateFlow<ScanUi> = _scan
    private var candidate: String? = null
    private var candidateSince = 0L
    private var lastSeen = SystemClock.elapsedRealtime()
    private var phase = Phase.Idle
    private var lockCount = 0

    init {
        viewModelScope.launch(Dispatchers.Default) {
            analyzer = TfliteFoodAnalyzer.createOrNull(app, db) ?: DemoAnalyzer(db)
            _scan.update { it.copy(warming = false) }
        }
    }

    fun resetScan() {
        candidate = null; lastSeen = SystemClock.elapsedRealtime(); phase = Phase.Idle
        _scan.update { it.copy(phase = Phase.Idle, box = null) }
    }

    fun onFrame(bmp: Bitmap) {
        val a = analyzer
        if (a == null || !a.isReady) return
        val now = SystemClock.elapsedRealtime()
        val low = meanLuma(bmp) < 0.10f
        val dets = a.analyze(bmp).filter { it.confidence >= 0.5f }
        val best = dets.maxByOrNull { it.box.width() * it.box.height() }

        if (best != null) {
            lastSeen = now
            if (best.label == candidate) {
                if (phase != Phase.Locked && now - candidateSince >= 400) { phase = Phase.Locked; lockCount++ }
            } else {
                candidate = best.label; candidateSince = now
                phase = Phase.Searching
            }
            val total = dets.sumOf { it.kcal }
            _scan.update {
                it.copy(
                    phase = phase, box = RectF(best.box), frameW = bmp.width, frameH = bmp.height,
                    label = best.label, kcal = total, lowLight = low, lockCount = lockCount,
                )
            }
        } else {
            candidate = null
            val gone = now - lastSeen
            phase = when {
                gone > 6000 -> Phase.NotFound
                phase == Phase.Locked && gone < 700 -> Phase.Locked
                else -> Phase.Idle
            }
            _scan.update {
                if (phase == Phase.Locked) it.copy(lowLight = low)
                else it.copy(phase = phase, box = null, lowLight = low)
            }
        }
    }

    private fun meanLuma(b: Bitmap): Float {
        var sum = 0f; var n = 0
        val sx = (b.width / 8).coerceAtLeast(1); val sy = (b.height / 8).coerceAtLeast(1)
        var y = sy / 2
        while (y < b.height) {
            var x = sx / 2
            while (x < b.width) {
                val p = b.getPixel(x, y)
                sum += (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
                n++; x += sx
            }
            y += sy
        }
        return if (n == 0) 1f else sum / n
    }

    // ---------------- actions ----------------
    fun logMeal(kcal: Int, name: String): Long {
        val id = System.currentTimeMillis()
        viewModelScope.launch { store.addMeal(Meal(id, id, kcal, name)) }
        return id
    }
    fun undo(id: Long) { viewModelScope.launch { store.removeMeal(id) } }
    fun setMaintenance(v: Int) { viewModelScope.launch { store.setMaintenance(v) } }
    fun setDeficit(v: Int) { viewModelScope.launch { store.setDeficit(v) } }
    fun markOnboarded() { viewModelScope.launch { store.markOnboarded() } }
    fun bumpHint() { viewModelScope.launch { store.bumpHint() } }

    override fun onCleared() { analyzer?.close() }
}
