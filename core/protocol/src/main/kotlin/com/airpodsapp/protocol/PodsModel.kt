package com.airpodsapp.protocol

/**
 * Known Apple/Beats models, identified by bytes 3-4 of the beacon.
 *
 * Some models are only known by the low nibble of byte 3 ([shortId]); see [fromBeacon].
 */
enum class PodsModel(
    val displayName: String,
    val isSingle: Boolean,
    private val fullIds: Set<Int> = emptySet(),
    private val shortId: Int? = null,
) {
    AIRPODS_1("AirPods (1st gen)", isSingle = false, fullIds = setOf(0x0220)),
    AIRPODS_2("AirPods (2nd gen)", isSingle = false, fullIds = setOf(0x0F20)),
    AIRPODS_3("AirPods (3rd gen)", isSingle = false, fullIds = setOf(0x1320)),
    AIRPODS_PRO("AirPods Pro", isSingle = false, fullIds = setOf(0x0E20)),
    AIRPODS_PRO_2("AirPods Pro 2", isSingle = false, fullIds = setOf(0x1420, 0x2420)),
    AIRPODS_PRO_3("AirPods Pro 3", isSingle = false, fullIds = setOf(0x2720)),
    AIRPODS_MAX("AirPods Max", isSingle = true, shortId = 0xA),
    POWERBEATS_PRO("Powerbeats Pro", isSingle = false, shortId = 0xB),
    BEATS_X("BeatsX", isSingle = true, fullIds = setOf(0x0520)),
    BEATS_FLEX("Beats Flex", isSingle = true, fullIds = setOf(0x1020)),
    BEATS_SOLO_3("Beats Solo 3", isSingle = true, fullIds = setOf(0x0620)),
    BEATS_STUDIO_3("Beats Studio 3", isSingle = true, shortId = 0x9),
    POWERBEATS_3("Powerbeats 3", isSingle = true, fullIds = setOf(0x0320)),

    /** Unrecognised id: decoded as earbuds + case, like OpenPods does. */
    UNKNOWN("AirPods", isSingle = false);

    companion object {
        /**
         * Full ids are matched before short ids. None of the known full ids has
         * 0x9, 0xA or 0xB as its second hex digit, so this gives the same result
         * as OpenPods' interleaved checks.
         */
        fun fromBeacon(modelId: Int): PodsModel {
            entries.firstOrNull { modelId in it.fullIds }?.let { return it }
            val nibble = (modelId shr 8) and 0x0F
            return entries.firstOrNull { it.shortId == nibble } ?: UNKNOWN
        }
    }
}
