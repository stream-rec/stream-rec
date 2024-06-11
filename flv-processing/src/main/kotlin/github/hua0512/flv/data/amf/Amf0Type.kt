/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2024 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package github.hua0512.flv.data.amf

/**
 * AMF0 data types
 * @author hua0512
 * @date : 2024/6/9 9:58
 * @see <a href="https://en.wikipedia.org/wiki/Action_Message_Format#AMF0">AMF0 spec</a>
 */
enum class Amf0Type(val byte: Byte) {
  NUMBER(0x00),
  BOOLEAN(0x01),
  STRING(0x02),
  OBJECT(0x03),
  NULL(0x05),
  ECMA_ARRAY(0x08),
  OBJECT_END(0x09),
  STRICT_ARRAY(0x0A),
  DATE(0x0B),
  LONG_STRING(0x0C),
  XML_DOCUMENT(0x0F),
  TYPED_OBJECT(0x10),
  AMF3_OBJECT(0x11)
}