@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package test.interop

import kotlin.js.*
import kotlin.test.*

private fun hasExpectedTypes(value: JsAny): Boolean = js("value.bytes instanceof Uint8Array && value.ints instanceof Int32Array && value.longs instanceof BigInt64Array && value.doubles instanceof Float64Array && value.names instanceof Set && value.nested instanceof Map && value.nested.get('numbers')[0] === undefined")
class WasmInteropTest {
    @Test fun collectionsMatchTheWasmAbiAndRoundTrip() {
        val values = KMValues(byteArrayOf(0, -1), arrayOf(Int.MIN_VALUE, Int.MAX_VALUE), arrayOf(Long.MIN_VALUE, Long.MAX_VALUE), arrayOf(0.5, -1.25), hashSetOf("one", "two"), hashMapOf("numbers" to listOf(null, Long.MAX_VALUE)))
        val native = KMValuesToJs(values)
        assertTrue(hasExpectedTypes(native))
        val restored = KMValuesFromJs(native)
        assertContentEquals(values.bytes, restored.bytes)
        assertContentEquals(values.ints, restored.ints)
        assertContentEquals(values.longs, restored.longs)
        assertContentEquals(values.doubles, restored.doubles)
        assertEquals(values.names, restored.names)
        assertEquals(values.nested, restored.nested)
    }
}
