package com.airpodsapp.protocol

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class BeaconSelectorTest {
    private val second = 1_000_000_000L

    private fun beacon(address: String, rssi: Int, t: Long) = Beacon(address, rssi, t, ByteArray(27))

    @Test
    fun `returns strongest recent beacon`() {
        val selector = BeaconSelector()
        val mine = beacon("A", -40, 0)
        selector.offer(mine)

        // A weaker beacon from another pair: ours is still the best.
        assertSame(mine, selector.offer(beacon("B", -55, 1 * second)))
    }

    @Test
    fun `prefers the fresh beacon from the strongest address`() {
        val selector = BeaconSelector()
        selector.offer(beacon("A", -40, 0))
        val fresh = beacon("A", -45, 1 * second)

        assertSame(fresh, selector.offer(fresh))
    }

    @Test
    fun `forgets beacons older than the window`() {
        val selector = BeaconSelector()
        selector.offer(beacon("A", -30, 0))
        val later = beacon("B", -50, 11 * second)

        assertSame(later, selector.offer(later))
    }

    @Test
    fun `drops results weaker than the minimum rssi`() {
        val selector = BeaconSelector()
        assertNull(selector.offer(beacon("A", -75, 0)))
    }
}
