package com.example.myapplication.nearby

object BleBeaconCodec {
    const val MANUFACTURER_ID = 0x4D42
    private const val PREFIX = "mb1_"
    private val pattern = Regex("mb1_[a-f0-9]{32}")

    fun encode(beaconId: String): ByteArray? {
        if (!beaconId.matches(pattern)) return null
        return beaconId.removePrefix(PREFIX).chunked(2).map { hex ->
            hex.toInt(16).toByte()
        }.toByteArray()
    }

    fun decode(payload: ByteArray?): String? {
        if (payload == null || payload.size != 16) return null
        val hex = payload.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
        return "$PREFIX$hex".takeIf(pattern::matches)
    }
}
