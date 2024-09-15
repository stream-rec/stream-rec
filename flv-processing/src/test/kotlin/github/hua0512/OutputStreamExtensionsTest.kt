package github.hua0512

import github.hua0512.flv.utils.write3BytesInt
import github.hua0512.flv.utils.writeDouble
import github.hua0512.flv.utils.writeInt
import github.hua0512.flv.utils.writeShort
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.expect

class OutputStreamExtensionsTest {

  @Test
  fun write3BytesInt_writesCorrectBytes() {
    val outputStream = ByteArrayOutputStream()
    outputStream.write3BytesInt(0x123456)
    val expected = byteArrayOf(0x12, 0x34, 0x56)
    expect(expected.toList()) {
      outputStream.toByteArray().toList()
    }
  }

  @Test
  fun writeInt_writesCorrectBytes() {
    val outputStream = ByteArrayOutputStream()
    outputStream.writeInt(0x12345678)
    val expected = byteArrayOf(0x12, 0x34, 0x56, 0x78)
    expect(expected.toList()) {
      outputStream.toByteArray().toList()
    }
    expect(expected.toList()) {
      outputStream.toByteArray().toList()
    }
  }

  @Test
  fun writeShort_writesCorrectBytes() {
    val outputStream = ByteArrayOutputStream()
    outputStream.writeShort(0x1234)
    val expected = byteArrayOf(0x12, 0x34)
    expect(expected.toList()) {
      outputStream.toByteArray().toList()
    }
  }

  @Test
  fun writeDouble_writesCorrectBytes() {
    val outputStream = ByteArrayOutputStream()
    val value = 1234.5678
    outputStream.writeDouble(value)
    val expected = ByteBuffer.allocate(8).putDouble(value).array()
    expect(expected.toList()) {
      outputStream.toByteArray().toList()
    }
  }
}