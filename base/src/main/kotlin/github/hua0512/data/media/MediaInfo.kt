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

package github.hua0512.data.media

import github.hua0512.data.stream.StreamInfo

/**
 * A data class representing the media information
 * @property site the site where the media is from
 * @property title the title of the media
 * @property artist the artist of the media
 * @property coverUrl the cover image url of the media
 * @property artistImageUrl the artist image url of the media
 * @property live whether the media is live
 * @property streams the list of stream information
 * @property extras the extra information
 * @author hua0512
 * @date : 2024/3/15 20:29
 */
data class MediaInfo(
  val site: String,
  val title: String,
  val artist: String,
  val coverUrl: String,
  val artistImageUrl: String,
  val live: Boolean = false,
  val streams: List<StreamInfo> = emptyList(),
  val extras: Map<String, String> = emptyMap(),
)