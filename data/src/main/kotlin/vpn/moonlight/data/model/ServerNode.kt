package vpn.moonlight.data.model

import kotlinx.serialization.Serializable

/**
 * One node from the subscription. [shareLink] is the authoritative payload — the
 * app never rebuilds it, it hands it back to Xray-core for parsing.
 */
@Serializable
data class ServerNode(
    val id: String,
    /** Display name, e.g. "Amsterdam". */
    val name: String,
    /**
     * The node's complete Xray config, exactly as the panel served it in the JSON
     * subscription. Preferred over [shareLink] when present, because a panel can
     * attach an XRAY JSON override — a balancer, custom DNS, routing rules — that
     * the share-link format cannot represent.
     */
    val panelConfigJson: String? = null,
    /** Share link, for panels that serve only base64. */
    val shareLink: String? = null,
    /** Regional emoji flag from the node's remark, when it has one. */
    val flag: String? = null,
    /** ISO 3166-1 alpha-2, derived from the flag. Localised for display in the UI. */
    val countryCode: String? = null,
    /** Remnawave squad / group, when the remark encodes one. */
    val squad: String? = null,
    val protocolLabel: String = "VLESS",
    val host: String? = null,
    val port: Int? = null,
    /** The panel's config load-balances across several outbounds. */
    val isBalancer: Boolean = false,
) {
    /** True when the panel gave us a full config rather than just a URI. */
    val hasPanelConfig: Boolean get() = panelConfigJson != null

    /**
     * Panels commonly publish a load-balancing node called "Auto" (or "Авто").
     * When one exists it is what the Auto selection should pick — the panel knows
     * more about node health than a latency probe from one phone does.
     */
    val isAutoNode: Boolean
        get() = AUTO_MARKERS.any { name.contains(it, ignoreCase = true) }

    /**
     * True when the name is *only* the auto marker, so the UI can substitute its
     * own localised wording. A name like "Auto · Europe" carries something the
     * panel meant to say and is left alone.
     */
    val isBareAutoName: Boolean
        get() = AUTO_MARKERS.any { name.trim().equals(it, ignoreCase = true) }

    private companion object {
        val AUTO_MARKERS = listOf("auto", "авто")
    }
}

/**
 * Latency for a node, kept outside [ServerNode] so a probe pass never rewrites
 * the node list itself.
 */
sealed interface Latency {
    data object Unknown : Latency
    data object Measuring : Latency
    data class Value(val ms: Int) : Latency
    data class Failed(val reason: String? = null) : Latency
}

/** Which node the user wants. `Auto` defers to the lowest measured latency. */
@Serializable
sealed interface NodeSelection {
    @Serializable
    data object Auto : NodeSelection

    @Serializable
    data class Pinned(val nodeId: String) : NodeSelection
}
