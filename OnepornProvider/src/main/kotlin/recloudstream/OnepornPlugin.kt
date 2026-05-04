package recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class OnepornPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added here.
        registerMainAPI(OnePorn())
    }
}
