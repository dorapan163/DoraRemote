package com.example.model

/**
 * Fine-grained security permissions enforced on the Host (被控端) device.
 */
data class HostPermissions(
    val allowTouch: Boolean = true,
    val allowKeys: Boolean = true,
    val allowKeyboard: Boolean = true,
    val allowClipboard: Boolean = true,
    val allowAudio: Boolean = false,
    val allowQualityControl: Boolean = true,
    val requireHostApproval: Boolean = true
) {
    fun toMap(): Map<String, Boolean> = mapOf(
        "allowTouch" to allowTouch,
        "allowKeys" to allowKeys,
        "allowKeyboard" to allowKeyboard,
        "allowClipboard" to allowClipboard,
        "allowAudio" to allowAudio,
        "allowQualityControl" to allowQualityControl,
        "requireHostApproval" to requireHostApproval
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): HostPermissions {
            return HostPermissions(
                allowTouch = map["allowTouch"] as? Boolean ?: true,
                allowKeys = map["allowKeys"] as? Boolean ?: true,
                allowKeyboard = map["allowKeyboard"] as? Boolean ?: true,
                allowClipboard = map["allowClipboard"] as? Boolean ?: true,
                allowAudio = map["allowAudio"] as? Boolean ?: false,
                allowQualityControl = map["allowQualityControl"] as? Boolean ?: true,
                requireHostApproval = map["requireHostApproval"] as? Boolean ?: true
            )
        }
    }
}
