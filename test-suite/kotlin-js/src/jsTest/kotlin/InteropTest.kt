package test.interop

import kotlin.test.*

class InteropTest {
    @Test
    fun collectionsMatchTheWasmAbiAndRoundTrip() {
        val values = KMValues(
            byteArrayOf(0, -1), arrayOf(Int.MIN_VALUE, Int.MAX_VALUE),
            arrayOf(Long.MIN_VALUE, Long.MAX_VALUE), arrayOf(0.5, -1.25),
            hashSetOf("one", "two"), hashMapOf("numbers" to listOf(null, Long.MAX_VALUE))
        )
        val native = KMValuesToJs(values)
        assertTrue(js("native.bytes instanceof Uint8Array") as Boolean)
        assertTrue(js("native.ints instanceof Int32Array") as Boolean)
        assertTrue(js("native.longs instanceof BigInt64Array") as Boolean)
        assertTrue(js("native.doubles instanceof Float64Array") as Boolean)
        assertTrue(js("native.names instanceof Set") as Boolean)
        assertTrue(js("native.nested instanceof Map") as Boolean)
        assertTrue(js("native.nested.get('numbers')[0] === undefined") as Boolean)
        val restored = KMValuesFromJs(native)
        assertContentEquals(values.bytes, restored.bytes)
        assertContentEquals(values.ints, restored.ints)
        assertContentEquals(values.longs, restored.longs)
        assertContentEquals(values.doubles, restored.doubles)
        assertEquals(values.names, restored.names)
        assertEquals(values.nested, restored.nested)
    }
}
