/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
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

package github.hua0512.flv.data.video.hevc.nal

import github.hua0512.flv.data.video.nal.NalUnitType

// HEVC NAL unit types as defined in ITU-T H.265 specification
private const val NAL_UNIT_TYPE_TRAIL_N = 0
private const val NAL_UNIT_TYPE_TRAIL_R = 1
private const val NAL_UNIT_TYPE_TSA_N = 2
private const val NAL_UNIT_TYPE_TSA_R = 3
private const val NAL_UNIT_TYPE_STSA_N = 4
private const val NAL_UNIT_TYPE_STSA_R = 5
private const val NAL_UNIT_TYPE_RADL_N = 6
private const val NAL_UNIT_TYPE_RADL_R = 7
private const val NAL_UNIT_TYPE_RASL_N = 8
private const val NAL_UNIT_TYPE_RASL_R = 9
private const val NAL_UNIT_TYPE_RSV_VCL_N10 = 10
private const val NAL_UNIT_TYPE_RSV_VCL_N11 = 11
private const val NAL_UNIT_TYPE_RSV_VCL_N12 = 12
private const val NAL_UNIT_TYPE_RSV_VCL_N13 = 13
private const val NAL_UNIT_TYPE_RSV_VCL_N14 = 14
private const val NAL_UNIT_TYPE_RSV_VCL_N15 = 15
private const val NAL_UNIT_TYPE_BLA_W_LP = 16
private const val NAL_UNIT_TYPE_BLA_W_RADL = 17
private const val NAL_UNIT_TYPE_BLA_N_LP = 18
private const val NAL_UNIT_TYPE_IDR_W_RADL = 19
private const val NAL_UNIT_TYPE_IDR_N_LP = 20
private const val NAL_UNIT_TYPE_CRA_NUT = 21
private const val NAL_UNIT_TYPE_RSV_IRAP_VCL22 = 22
private const val NAL_UNIT_TYPE_RSV_IRAP_VCL23 = 23
private const val NAL_UNIT_TYPE_RSV_VCL24 = 24
private const val NAL_UNIT_TYPE_RSV_VCL25 = 25
private const val NAL_UNIT_TYPE_RSV_VCL26 = 26
private const val NAL_UNIT_TYPE_RSV_VCL27 = 27
private const val NAL_UNIT_TYPE_RSV_VCL28 = 28
private const val NAL_UNIT_TYPE_RSV_VCL29 = 29
private const val NAL_UNIT_TYPE_RSV_VCL30 = 30
private const val NAL_UNIT_TYPE_RSV_VCL31 = 31
private const val NAL_UNIT_TYPE_VPS = 32
private const val NAL_UNIT_TYPE_SPS = 33
private const val NAL_UNIT_TYPE_PPS = 34
private const val NAL_UNIT_TYPE_AUD = 35
private const val NAL_UNIT_TYPE_EOS = 36
private const val NAL_UNIT_TYPE_EOB = 37
private const val NAL_UNIT_TYPE_FD = 38
private const val NAL_UNIT_TYPE_PREFIX_SEI = 39
private const val NAL_UNIT_TYPE_SUFFIX_SEI = 40

sealed class HEVCNalUnitType(override val value: Int, override val msg: String) : NalUnitType {

  companion object {
    fun valueOf(value: Int): HEVCNalUnitType {
      return when (value) {
        NAL_UNIT_TYPE_TRAIL_N -> TrailN
        NAL_UNIT_TYPE_TRAIL_R -> TrailR
        NAL_UNIT_TYPE_TSA_N -> TsaN
        NAL_UNIT_TYPE_TSA_R -> TsaR
        NAL_UNIT_TYPE_STSA_N -> StsaN
        NAL_UNIT_TYPE_STSA_R -> StsaR
        NAL_UNIT_TYPE_RADL_N -> RadlN
        NAL_UNIT_TYPE_RADL_R -> RadlR
        NAL_UNIT_TYPE_RASL_N -> RaslN
        NAL_UNIT_TYPE_RASL_R -> RaslR
        NAL_UNIT_TYPE_BLA_W_LP -> BlaWLp
        NAL_UNIT_TYPE_BLA_W_RADL -> BlaWRadl
        NAL_UNIT_TYPE_BLA_N_LP -> BlaNLp
        NAL_UNIT_TYPE_IDR_W_RADL -> IdrWRadl
        NAL_UNIT_TYPE_IDR_N_LP -> IdrNLp
        NAL_UNIT_TYPE_CRA_NUT -> CraNut
        NAL_UNIT_TYPE_VPS -> VPS
        NAL_UNIT_TYPE_SPS -> SPS
        NAL_UNIT_TYPE_PPS -> PPS
        NAL_UNIT_TYPE_AUD -> AUD
        NAL_UNIT_TYPE_EOS -> EOS
        NAL_UNIT_TYPE_EOB -> EOB
        NAL_UNIT_TYPE_FD -> FD
        NAL_UNIT_TYPE_PREFIX_SEI -> PrefixSEI
        NAL_UNIT_TYPE_SUFFIX_SEI -> SuffixSEI
        in NAL_UNIT_TYPE_RSV_VCL_N10..NAL_UNIT_TYPE_RSV_VCL_N15 -> Reserved("Reserved VCL", value)
        in NAL_UNIT_TYPE_RSV_IRAP_VCL22..NAL_UNIT_TYPE_RSV_VCL31 -> Reserved("Reserved", value)
        else -> Unknown(value)
      }
    }
  }

  // VCL NAL units are 0-31
  override fun isVcl(): Boolean = value <= 31

  // IDR pictures are 19-20, BLA is 16-18, CRA is 21
  override fun isIdr(): Boolean = value in 16..21

  override fun isVps(): Boolean = this == VPS

  override fun isSps(): Boolean = this == SPS

  override fun isPps(): Boolean = this == PPS

  data object TrailN : HEVCNalUnitType(NAL_UNIT_TYPE_TRAIL_N, "Trailing, non-reference")
  data object TrailR : HEVCNalUnitType(NAL_UNIT_TYPE_TRAIL_R, "Trailing, reference")
  data object TsaN : HEVCNalUnitType(NAL_UNIT_TYPE_TSA_N, "Temporal Sub-layer Access, non-reference")
  data object TsaR : HEVCNalUnitType(NAL_UNIT_TYPE_TSA_R, "Temporal Sub-layer Access, reference")
  data object StsaN : HEVCNalUnitType(NAL_UNIT_TYPE_STSA_N, "Step-wise Temporal Sub-layer Access, non-reference")
  data object StsaR : HEVCNalUnitType(NAL_UNIT_TYPE_STSA_R, "Step-wise Temporal Sub-layer Access, reference")
  data object RadlN : HEVCNalUnitType(NAL_UNIT_TYPE_RADL_N, "Random Access Decodable Leading, non-reference")
  data object RadlR : HEVCNalUnitType(NAL_UNIT_TYPE_RADL_R, "Random Access Decodable Leading, reference")
  data object RaslN : HEVCNalUnitType(NAL_UNIT_TYPE_RASL_N, "Random Access Skipped Leading, non-reference")
  data object RaslR : HEVCNalUnitType(NAL_UNIT_TYPE_RASL_R, "Random Access Skipped Leading, reference")
  data object BlaWLp : HEVCNalUnitType(NAL_UNIT_TYPE_BLA_W_LP, "Broken Link Access with Leading Pictures")
  data object BlaWRadl : HEVCNalUnitType(NAL_UNIT_TYPE_BLA_W_RADL, "Broken Link Access with RADL")
  data object BlaNLp : HEVCNalUnitType(NAL_UNIT_TYPE_BLA_N_LP, "Broken Link Access without Leading Pictures")
  data object IdrWRadl : HEVCNalUnitType(NAL_UNIT_TYPE_IDR_W_RADL, "Instantaneous Decoding Refresh with RADL")
  data object IdrNLp : HEVCNalUnitType(NAL_UNIT_TYPE_IDR_N_LP, "Instantaneous Decoding Refresh without Leading Pictures")
  data object CraNut : HEVCNalUnitType(NAL_UNIT_TYPE_CRA_NUT, "Clean Random Access")
  data object VPS : HEVCNalUnitType(NAL_UNIT_TYPE_VPS, "Video Parameter Set")
  data object SPS : HEVCNalUnitType(NAL_UNIT_TYPE_SPS, "Sequence Parameter Set")
  data object PPS : HEVCNalUnitType(NAL_UNIT_TYPE_PPS, "Picture Parameter Set")
  data object AUD : HEVCNalUnitType(NAL_UNIT_TYPE_AUD, "Access Unit Delimiter")
  data object EOS : HEVCNalUnitType(NAL_UNIT_TYPE_EOS, "End of Sequence")
  data object EOB : HEVCNalUnitType(NAL_UNIT_TYPE_EOB, "End of Bitstream")
  data object FD : HEVCNalUnitType(NAL_UNIT_TYPE_FD, "Filler Data")
  data object PrefixSEI : HEVCNalUnitType(NAL_UNIT_TYPE_PREFIX_SEI, "Supplemental Enhancement Information (Prefix)")
  data object SuffixSEI : HEVCNalUnitType(NAL_UNIT_TYPE_SUFFIX_SEI, "Supplemental Enhancement Information (Suffix)")
  data class Reserved(val type: String, override val value: Int) : HEVCNalUnitType(value, "Reserved: $type")
  data class Unknown(override val value: Int) : HEVCNalUnitType(value, "Unknown")
} 