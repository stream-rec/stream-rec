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

package github.hua0512.plugins.douyu.danmu

/**
 * Douyu serialized text transfer
 *
 * based on: https://open.douyu.com/source/api/63
 * @author hua0512
 * @date : 2024/3/23 13:21
 */
internal class DouyuSTT {
  companion object {

    private fun escape(v: Any): String {
      return v.toString().replace("@", "@A").replace("/", "@S")
    }

    private fun unescape(v: String): String {
      return v.replace("@S", "/").replace("@A", "@")
    }

    fun serialize(raw: Any): String {
      return when (raw) {
        is Map<*, *> -> {
          raw.entries.joinToString("") { "${it.key}@=${serialize(it.value ?: "")}" }
        }

        is List<*> -> {
          raw.joinToString("") { serialize(it!!) }
        }

        else -> {
          "${escape(raw.toString())}/"
        }
      }
    }

    fun deserialize(raw: String): Any {
      return when {
        raw.contains("//") -> {
          raw.split("//")
            .filter { it.isNotEmpty() }
            .map { deserialize(it) }
        }

        raw.contains("@=") -> {
          raw.split("/")
            .filter { it.isNotEmpty() }
            .associate {
              val (k, v) = it.split("@=")
              k to if (v.isNotEmpty()) deserialize(v) else ""
            }
        }

        else -> unescape(raw)
      }
    }
  }
}
