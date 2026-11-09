package io.ymusic.ui.playtogether.backend

/**
 * Service locator and factory for the active Play Together backend.
 * Provides instant fallback and clean abstraction over real-time transport.
 */
object PlayTogetherBackendProvider {

    private var customBackend: PlayTogetherBackend? = null
    private var activeUrl: String = ""
    private var activeKey: String = ""
    private var activeBackend: PlayTogetherBackend? = null

    /**
     * Retrieves the active [PlayTogetherBackend] instance.
     * By default, returns a robust [SupabaseRealtimeBackend] wrapping [LocalSimulatedRealtimeBackend].
     */
    val current: PlayTogetherBackend
        get() {
            customBackend?.let { return it }
            var b = activeBackend
            if (b == null) {
                b = if (activeUrl.isNotBlank() && activeKey.isNotBlank()) {
                    SupabaseRealtimeBackend(supabaseUrl = activeUrl, supabaseAnonKey = activeKey)
                } else {
                    GlobalRealtimeBackend()
                }
                activeBackend = b
            }
            return b
        }

    /**
     * Updates the Supabase Realtime endpoint and anon key dynamically.
     */
    fun configure(url: String, key: String) {
        if (url != activeUrl || key != activeKey) {
            activeUrl = url.trim()
            activeKey = key.trim()
            activeBackend = SupabaseRealtimeBackend(supabaseUrl = activeUrl, supabaseAnonKey = activeKey)
        }
    }

    /**
     * Returns true if a live Supabase Realtime backend is configured with credentials.
     */
    val isLiveConfigured: Boolean
        get() {
            val cur = current
            return (cur as? SupabaseRealtimeBackend)?.isConfigured == true
        }

    /**
     * Configures a custom backend instance (useful for unit testing or custom relay host).
     */
    fun setBackend(backend: PlayTogetherBackend) {
        customBackend = backend
    }

    /**
     * Resets backend to default.
     */
    fun reset() {
        customBackend = null
        activeUrl = ""
        activeKey = ""
        activeBackend = null
    }
}
