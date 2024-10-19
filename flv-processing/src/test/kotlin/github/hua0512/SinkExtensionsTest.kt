package github.hua0512

import github.hua0512.flv.utils.writeI24
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeDouble
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.expect

class SinkExtensionsTest {

  @Test
  fun write3BytesInt_writesCorrectBytes() {
    val buffer = Buffer()
    buffer.writeI24(0x123456)
    val expected = byteArrayOf(0x12, 0x34, 0x56)
    expect(expected.toList()) {
      buffer.readByteArray().toList()
    }
  }

  @Test
  fun writeInt_writesCorrectBytes() {
    val buffer = Buffer()
    buffer.writeInt(0x12345678)
    val expected = byteArrayOf(0x12, 0x34, 0x56, 0x78)

    expect(expected.toList()) {
      buffer.readByteArray().toList()
    }
  }

  @Test
  fun writeShort_writesCorrectBytes() {
    val buffer = Buffer()
    buffer.writeShort(0x1234)
    val expected = byteArrayOf(0x12, 0x34)
    expect(expected.toList()) {
      buffer.readByteArray().toList()
    }
  }

  @Test
  fun writeDouble_writesCorrectBytes() {
    val buffer = Buffer()
    val value = 1234.5678
    buffer.writeDouble(value)
    val expected = ByteBuffer.allocate(8).putDouble(value).array()
    expect(expected.toList()) {
      buffer.readByteArray().toList()
    }
  }
}