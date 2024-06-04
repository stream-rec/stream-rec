import github.hua0512.app.App
import github.hua0512.data.config.AppConfig
import github.hua0512.data.config.HuyaConfigGlobal
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.huya.danmu.HuyaDanmu
import github.hua0512.plugins.huya.download.Huya
import github.hua0512.plugins.huya.download.HuyaExtractor
import github.hua0512.plugins.huya.download.HuyaExtractorV2
import io.exoquery.pprint
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Duration

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

class HuyaTest {


  @Test
  fun testUrl() = runTest {
    val url = "https://www.huya.com/geminiii?from=live"
    val matchResult = HuyaExtractor.URL_REGEX.toRegex().find(url) ?: throw IllegalArgumentException("Invalid url")
    assertEquals(matchResult.groupValues.last(), "geminiii")
  }

  @Test
  fun testAvatarRegex() = runTest {

    val content = """ var TT_META_DATA = {"time":1709163109};
        var TT_ROOM_DATA = {"type":"MATCH","state":"REPLAY","isOn":false,"isOff":false,"isReplay":true,"isPayRoom":0,"isSecret":0,"roomPayPassword":"","id":0,"sid":0,"channel":0,"liveChannel":0,"liveId":0,"shortChannel":0,"isBluRay":1,"gameFullName":"CS2","gameHostName":"862","screenType":1,"startTime":1709106603,"totalCount":4097722,"cameraOpen":0,"liveCompatibleFlag":0,"bussType":1,"isPlatinum":1,"isAutoBitrate":0,"screenshot":"http://live-cover.msstatic.com/huyalive/333393123-333393123-1431912559996305408-666909702-10057-A-0-1-imgplus/20240228164334.jpg","previewUrl":"","gameId":0,"liveSourceType":0,"privateHost":"dank1ng","profileRoom":610742,"recommendStatus":0,"popular":0,"gid":862,"introduction":"龙一样潜于水志于胸力于蓄不鸣则已一鸣惊人","isRedirectHuya":0,"isShowMmsProgramList":0};
        var TT_PROFILE_INFO = {"sex":1,"lp":333393123,"aid":0,"yyid":244905860,"nick":"DANK1NG","avatar":"https://huyaimg.msstatic.com/avatar/1009/21/d479da7839241ade1e136d7324df4f_180_135.jpg?1671605310","fans":1250268,"freezeLevel":0,"host":"dank1ng","profileRoom":610742};
        var TT_PLAYER_CFG = {"flashAdvanceVersion":"-1","advanceChannel":"1029443883_1029443883","flashDomain":"//hyplayer.msstatic.com/","h5Advance":"-1","h5domain":"https://a.msstatic.com/huya/h5player/room/","delOriginalRate":"-1","homePageH5Domain":"https://a.msstatic.com/huya/h5player/home/","h5PlayerP2PVersion":"-1","h5ShareEntry":"//a.msstatic.com/huya/h5player/outstation/guide4.js","replayUseH5":"true","yqkPlayerVer":"2008131707","homePagePlayerIncludeSDK":"2308081431","defaultFlashPlayer":"false","h5sdk":"//a.msstatic.com/huya/h5player/sdk/","h5outstationVer":"2201181143","h5homeSdk":"1703061730","h5AdvanceVersion":"-1","playerConfig":"{&quot;danmu&quot;:&quot;1&quot;}","h5sdkver":"1706131454","h5ver":"1802021544","flashAdvance":"-1","h5gopChannel":"-1","h5hlssdkver":"hlssdk_1801261608","h5PlayerP2PChannel":"-1","h5outstationDomain":"//a.msstatic.com/huya/h5player/outstation","homePageH5Ver":"1808091448","flashVersion":"v3.2_20110603","useflash":"-1","playerdata":"{&quot;usehttps&quot;:&quot;1&quot;,&quot;sdk&quot;:{}}","yqkPlayerDomain":"//a.msstatic.com/huya/h5player/yqk/","homePageH5":"true","h5PlayerAndHuyaSDKChannel":"-1","deleteOriginalPainting":"-1","h5PlayerAndHuyaSDKVersion":"-1","homepage":"hp_76","h5PlayerIncludeSDK":"2212151455"};
        var TT_PLAYER_CONF = {"app":"live","grayGameIdVersion":"{\"gid\":[{\"gid\":\"-1\",\"ver\":\"-1\"}]}","grayPidVersion":"{\"pid\":[{\"pid\":\"1834091104,1503392699,2364556832,307702434,17363578\",\"ver\":\"2402281433\",\"desc\":\"no_render\"},{\"pid\":\"1099511667849\",\"ver\":\"2402280947\",\"desc\":\"pread\"},{\"pid\":\"1199581087493,1524439853,1524418108,1199582997225\",\"ver\":\"2402281438\",\"desc\":\"timer_worker\"}]}","h5playerDomian":"https://a.msstatic.com/huya/h5player/room/","h5playerVersion":"2401311422","module":"common","playerConfig":"{\"closeHdr\":\"0\",\"gzip\":\"1\",\"gzipExp\":3,\"adVolume\":\"0.2\",\"danmuMaskOpen\":\"1\",\"danmu\":\"1\",\"openMFVideo\":\"1\",\"lowLatencyGameId\":\"6613,7001\",\"lowLatencyPid\":\"1544844522,50075521,50043174\",\"hlsLivePid\":\"\",\"ob\":{\"x\":\"0.36\",\"y\":\"0.3\",\"scale\":\"0.25\",\"scaleMin\":\"0.25\",\"scaleMax\":\"1\",\"renderMod\":\"1\"}}","playerdata":"{\"danmuP2P\":\"1\",\"isAutoNoPicture\":0,\"videoDns\":\"1\",\"danmuMask\":\"1\",\"flacSwitch\":\"1\",\"p2pLineClose\":\"\",\"usehttps\":\"1\",\"sdk\":{\"statLv\":[1,1,0,0,0,0,0,1,1,0,2,1,0,1,1,1,0],\"isForceUseWorker\":1,\"wcs265BlackUids\":[1656754951,1344550525,1579609842,2233781281,1259523555338,1259520179264,2315326689,1467338235,2369473704,1199554450502,1199639682681,1199524191795],\"signalRttUids\":[1199629649413,1279546440890,1259515288802,50013676],\"notUsePcdnUids\":[\"1447756350\",\"1099531840902\",\"1099531840903\",\"1099531840904\",\"1099531840907\",\"1099531840909\",\"1099531840910\",\"1571856353\",\"1199563097230\",\"1279529316879\",\"2309272535\"],\"closeSubRooms\":[136152],\"closeAllUids\":[1199620812769,1259518098973,1691592144,878924459,1199635224176,1199635204861,1199635489744,1199635199657,1199635491142,1199536199401,1567578875,1647705781],\"notUseFlvPcdnUsers\":[1199629649413,1279546440890],\"notUsePcdnUsers\":[1199629649413,1279546440890,1259515288802,1199522152648],\"autoReportUsers\":[1199629649413,1279546440890],\"closePunchUsers\":[1199629649413,1279546440890,1199522152648],\"autoReportTime\":1000,\"vodAv1Cfg\":\"all|all:100_line6:0\",\"p2pWssLines\":\"1,3,5,8\",\"quickAccessLines\":\"1,3,5,6,7,8,9\",\"pushBufferLen\":4000,\"crossLineSub\":\"all\",\"crossClientPer\":\"all|all:100\",\"p2pSubSdkCfg\":\"all|all:0,1069843731|all:100,1539218884|all:100\",\"pcdnStun\":[\"1144904762\",\"-server.va.huya.com\",3493],\"resendAudioCfg\":\"all|all:500,1346609715|all:300\",\"rtPeerPRN\":1,\"rtPeerTimeout\":300,\"pcdnRetryCnt\":2,\"fastTimeCfg\":\"all|all:0\",\"signalCntMax\":10,\"urgentBuffer\":201,\"rtPeerBeforeHand\":0,\"rtPeerRandom\":1,\"rtPeerUgMin\":300,\"rtPeerUgMinCfg\":[[1099531728402,[[0,700]]],[1571856353,[[0,700]]]],\"peerRtoCfg\":[[1,[[0,-200]]]],\"audioDtsCfg\":\"all|all:100,1199639682681|all:0\",\"dtsJumpCfg\":\"all|all:100\",\"mediaRangeGap\":\"all|all:90\",\"rtPeerCfg\":[[0,3,0]],\"rangeJit\":0,\"openPcdnAB\":0,\"signalOpenExp\":\"1259515661837|all:10\",\"deleteH264Aud\":[1279522147815],\"mediaBaseIndexMode\":\"2\",\"useChangeRate\":[],\"p2pConfig\":{\"swapdomain\":{\"line_5\":[\"txtest.p2p.huya.com|tx.p2p.huya.com|tx3.p2p.huya.com\",\"tx2.p2p.huya.com\"]}},\"xp2pConfig\":[],\"s4kConfig\":[],\"h265Config\":[[[2000,4000],[120000,80000]],[4000,2000,1300,1000,500,350,17200],75,[[350,500,1000,1300,2000,4000,17200],[3,3,3,3,3,3,5]],30,3,[true,true]],\"h265Config2\":[6,1],\"h265MseConfig\":[1,[86],[500,1000,1300,2000,4000,17200],[],[[500,1000,1300,2000,4000,8000,10000,17200,17100],[3,3,3,3,5,11,11,11,11],[],[]],0],\"h265MseWhiteBlackUids\":[[],[2240935230]],\"isAutoH265\":0,\"enableAiMosaic\":1,\"aiBlackUids\":[1099531627889,50043344],\"aiRequestInterval\":2000,\"aiP2PFlvConfig\":[5000,10],\"aiRandomPercent\":100000,\"h265PercentConfig\":100000,\"setVideoCTCfg\":[-100,1000,10000,1,3,1500],\"isUseWebWorkerTick\":0,\"danmuPercent\":-1,\"av1PercentConfig\":100000,\"h265BlackUids\":[],\"autoDownTimer\":1,\"autoDownMaxBitrate\":0,\"autoReportCfg\":[0,3,0,0],\"continueBufferDeltaStart\":4500,\"aiABRandomPercent\":0,\"p2p302Config\":[[[6,1,1,1],[66,1,1,1],[1,1,1,1],[3,1,1,1],[5,1,1,1],[14,1,1,1]],1],\"aiBlackLines\":[],\"vodPcdnOpenBuffer\":4000,\"vodPcdnCloseBuffer\":3000,\"vodPcdnCoolTime\":60000,\"vodPcdnBufferCfg\":[[4000,3000,30000],[],[]],\"isMuteAct\":0,\"isReplayConfigSupportH265\":0,\"h265MseChromeConfig\":[107,30000,4,0,1],\"fakeHdrCfg\":[{\"auid\":[1346609715,1099531728402,1560173863,1544838388,1544838475,1099531728421,1259530482764,1544850348,1259515661837,1571877666,1456953785,1541541294,1560173900,1099531728419,1346618745,2179608815,1853167339,1239543150311,2179609130,1560173861],\"bitrateMap\":{\"4100\":17200,\"4200\":17100,\"20100\":19200,\"4300\":17300,\"14100\":14200}}],\"renderStat\":[1],\"pause500Cfg\":[300,[]],\"isAutoWcsReport\":0,\"webCodecCfg\":[4,20000,2000,1,1500,[5000,10000,20000]],\"webCodecAVDeltaCfg\":[5000,2000],\"webCodecBlackUids\":[2385292509,1099531768232,1182368555,1199611344355,1279513816099,1571905909,1199639682681,1199552896543,1555556780,1199591052064],\"isVodSupportH265\":0,\"h265HardBlackBrows\":[],\"wcsSoft264Uids\":[878924459],\"popSize0Brows\":[[\"firefox\",115]],\"wcsSoftBrows\":[],\"enhanceVCfg\":[1,4000,6,1080],\"mse265BlackBrowVers\":[],\"eHBlackAnchoruids\":[],\"jumpBufferCfg\":[30,1000,10000,5000,30,25],\"enableEdgeBroMseCfg\":[],\"fpsBitrateCfg\":[100,[2179608815,1853167339,1346609715,1560173863],{\"4200\":\"17300\"},120],\"enhanceVodVCfg\":[1,4000,6,1080],\"payRoomCfg\":[1,5],\"eHBlackBrowVers\":[122],\"wcs265BlackBrowVers\":[122]}}","pushMsgControl":"https://hd2.huya.com/fedbasic/huyabaselibs/push-msg-control/push-msg-control.global.0.0.3.prod.js","tafSignal":"https://hd2.huya.com/fedbasic/huyabaselibs/taf-signal/taf-signal.global.0.0.9.prod.js","version":"20240228182852","homePageH5Domain":"https://a.msstatic.com/huya/h5player/home/","homePagePlayerIncludeSDK":"2401241658","homePagePlayerConfig":"{\"test\":123}"};
        var TT_PROFILE_P2P_OPT = "";
"""
    val matchResult = HuyaExtractor.AVATAR_REGEX.toRegex().find(content) ?: throw IllegalArgumentException("Invalid content")
    assertEquals(matchResult.groupValues.last(), "https://huyaimg.msstatic.com/avatar/1009/21/d479da7839241ade1e136d7324df4f_180_135.jpg?1671605310")
  }

  private val streamingUrl = "https://www.huya.com/991111"

  private val app = App(Json).apply {
    updateConfig(AppConfig(huyaConfig = HuyaConfigGlobal(sourceFormat = VideoFormat.hls, primaryCdn = "HW")))
  }

  @Test
  fun testLive() = runTest {
    val client = app.client
    val extractor = HuyaExtractor(client, app.json, streamingUrl).apply {
      prepare()
    }
    val mediaInfo = extractor.extract()
    assertNotNull(mediaInfo)
    println(pprint(mediaInfo))
  }

  @Test
  fun testLive2() = runTest {
    val client = app.client
    val extractor = HuyaExtractorV2(client, app.json, streamingUrl).apply {
      prepare()
    }
    val mediaInfo = extractor.extract()
    assertNotNull(mediaInfo)
    println(pprint(mediaInfo))
  }

  @Test
  fun testFlv() = runTest {
    val client = app.client
    val extractor = HuyaExtractor(client, app.json, streamingUrl)
    val downloader = Huya(app, HuyaDanmu(app), extractor).apply {
      init(Streamer(0, "test", streamingUrl))
    }
    val streamInfo = downloader.shouldDownload()
    println(streamInfo)
    println(downloader.downloadUrl)
    assertNotNull(streamInfo)
  }

  @Test
  fun testDanmu() = runTest(timeout = Duration.INFINITE) {
    val danmu = HuyaDanmu(app).apply {
      enableWrite = false
      filePath = "huya_danmu.txt"
      ayyuid = 35184452693589
      topsid = 1199536199401
      subid = 1199536199401
    }
    val init = danmu.init(Streamer(0, "test", streamingUrl))
    danmu.fetchDanmu()
    assertNotNull(danmu)
  }
}