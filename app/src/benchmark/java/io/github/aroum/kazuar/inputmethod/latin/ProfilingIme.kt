package io.github.aroum.kazuar.inputmethod.latin

/** Exposes readiness of the actual measured IME only in the profiling variant. */
class ProfilingIme : LatinIME() {
    @Volatile var mainDictionaryReady = false
        private set

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onUpdateMainDictionaryAvailability(available: Boolean) {
        super.onUpdateMainDictionaryAvailability(available)
        mainDictionaryReady = available
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    fun hasSlowConnection() = mInputLogic.mConnection.hasSlowInputConnection()

    companion object {
        @Volatile var current: ProfilingIme? = null
            private set
    }
}
