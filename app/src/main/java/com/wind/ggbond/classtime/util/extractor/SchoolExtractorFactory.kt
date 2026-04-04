package com.wind.ggbond.classtime.util.extractor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学校提取器工厂
 * 
 * 根据学校配置返回对应的课表提取器
 */
@Singleton
class SchoolExtractorFactory @Inject constructor(
    private val cqepcExtractor: CQEPCExtractor,
    private val cqustExtractor: CQUSTExtractor,
    private val gzgsxyExtractor: GZGSXYExtractor,
    private val shsdExtractor: SHSDExtractor,
    private val zykjxyExtractor: ZYKJXYExtractor,
    private val nmgcjdxExtractor: NMGCJDXExtractor,
    private val sqgxyExtractor: SQGXYExtractor,
    private val sqsfxyExtractor: SQSFXYExtractor,
    private val ahykdxExtractor: AHYKDXExtractor,
    private val ahkjxyExtractor: AHKJXYExtractor,
    private val sdslzyxyExtractor: SDSLZYXYExtractor,
    private val cdxxgcdxExtractor: CDXXGCDXExtractor,
    private val whzyjsxyExtractor: WHZYJSXYExtractor,
    private val hnlgdxExtractor: HNLGDXExtractor,
    private val xahkxyExtractor: XAHKXYExtractor,
    private val lnkjxyExtractor: LNKJXYExtractor,
    private val lnzbzzzyjsxyExtractor: LNZBZZZYJSXYExtractor,
    private val zzdxExtractor: ZZDXExtractor,
    private val zzxysExtractor: ZZXYSExtractor,
    private val cqykdxExtractor: CQYKDXExtractor,
    private val cjdxExtractor: CJDXExtractor,
    // === Agent 1: 正方教务 + URP教务 + 金智教务 ===
    private val dhlgdxExtractor: DHLGDXExtractor,
    private val btzyjsxyExtractor: BTZYJSXYExtractor,
    private val hebhdxyExtractor: HEBHDXYExtractor,
    private val ksdxExtractor: KSDXExtractor,
    private val scgsxyExtractor: SCGSXYExtractor,
    private val scqhgdxExtractor: SCQHGDXExtractor,
    private val tykjdxExtractor: TYKJDXExtractor,
    private val nxsfxyExtractor: NXSFXYExtractor,
    private val ahjzdxExtractor: AHJZDXExtractor,
    private val sdsfxyExtractor: SDSFXYExtractor,
    private val gxwgyxyExtractor: GXWGYXYExtractor,
    private val whdxExtractor: WHDXExtractor,
    private val hbcmxyExtractor: HBCMXYExtractor,
    private val szxxzyjsxyExtractor: SZXXZYJSXYExtractor,
    private val hbjtxzyjsxyExtractor: HBJTZYJSXYExtractor,
    private val zjkjxyExtractor: ZJKJXYExtractor,
    private val ycsfxyExtractor: YCSFXYExtractor,
    private val gzsfdxExtractor: GZSFDXExtractor,
    private val hznydxExtractor: HZNYDXExtractor,
    private val gzsxyExtractor: GZSXYExtractor,
    private val cdyszydxExtractor: CDYSZYDXExtractor,
    private val hznydxByytExtractor: HZNYDXBYYTExtractor,
    private val nmgkjdxExtractor: NMGKJDXExtractor,
    private val hblgdxExtractor: HBLGDXExtractor,
    private val hbnydxExtractor: HBNYDXExtractor,
    private val ytdxExtractor: YTDXExtractor,
    private val ahyxgdzkxxExtractor: AHYXGDZKXXExtractor,
    private val ysdxExtractor: YSDXExtractor,
    private val ccqcgygdzkxxExtractor: CCQCGYGDZKXXExtractor,
    // === Agent 2: 强智教务系统 ===
    private val bjcmzyxyExtractor: BJCMZYXYExtractor,
    private val nnsfdxExtractor: NNSFDXExtractor,
    private val sddxExtractor: SDDXExtractor,
    private val gdngsExtractor: GDNGSExtractor,
    private val gzhsxyExtractor: GZHSXYExtractor,
    private val cdgyxyExtractor: CDGYXYExtractor,
    private val whxxcbzyjsxyExtractor: WHXXCBZYJSXYExtractor,
    private val whgckjxyExtractor: WHGCKJXYExtractor,
    private val hnzyyExtractor: HNZYYExtractor,
    private val hysfxyExtractor: HYSFXYExtractor,
    private val cqrwkjxyExtractor: CQRWKJXYExtractor,
    private val ccdxExtractor: CCDXExtractor,
    private val bjcmzyxyMobileExtractor: BJCMZYXYMobileExtractor,
    private val whxxcbzyjsxyMobileExtractor: WHXXCBZYJSXYMobileExtractor,
    private val hnrjzyjsxyExtractor: HNRJZYJSXYExtractor,
    // === Agent 2: 青果教务系统 ===
    private val hbslsdExtractor: HBSLSDExtractor,
    private val hbslsdOldExtractor: HBSLSDOldExtractor,
    private val sdnzxyExtractor: SDNZXYExtractor,
    private val xjdxExtractor: XJDXExtractor,
    private val kmykdxExtractor: KMYKDXExtractor,
    private val jhdxExtractor: JHDXExtractor,
    private val hnnydxExtractor: HNNYDXExtractor,
    private val hxxyExtractor: HXXYExtractor,
    private val zjkjxyQingguoExtractor: ZJKJXYQingguoExtractor,
    private val ytnsxyExtractor: YTNSXYExtractor,
    private val xafyxyExtractor: XAFYXYExtractor,
    private val zzsdjmglxyExtractor: ZZSDJMGLXYExtractor,
    private val ldxyExtractor: LDXYExtractor,
    // === Agent 3 添加区域开始 ===
    // 自研教务系统 (13所)
    private val njzyydxExtractor: NJZYYDXExtractor,
    private val hebgydxExtractor: HEBGYDXExtractor,
    private val sddyykdxExtractor: SDDYYKDXExtractor,
    private val sdypzyxyExtractor: SDYPZYXYExtractor,
    private val sxgckjzydxExtractor: SXGCKJZYDXExtractor,
    private val whzyjsxyCustomExtractor: WHZYJSXYCustomExtractor,
    private val hnzyydxYjsExtractor: HNZYYDXYJSExtractor,
    private val qzsfxyExtractor: QZSFXYExtractor,
    private val tzzyjsxyExtractor: TZZYJSXYExtractor,
    private val xajzkjdxExtractor: XAJZKJDXExtractor,
    private val gzgcyyjsxyExtractor: GZGCYYJSXYExtractor,
    private val qddxExtractor: QDDXExtractor,
    private val qqheyxyExtractor: QQHEYXYExtractor,
    // 乘方教务系统 (2所)
    private val gdjtzyjsxyExtractor: GDJTZYJSXYExtractor,
    private val gzzyydxExtractor: GZZYYDXExtractor,
    // 其他系统 (8所)
    private val whjgzyxyExtractor: WHJGZYXYExtractor,
    private val jzlgzyxyExtractor: JZLGZYXYExtractor,
    private val xaoyxyExtractor: XAOYXYExtractor,
    private val dytydzkjxxExtractor: DYTYDZKJXXExtractor,
    private val ccsfdxExtractor: CCSFDXExtractor,
    private val jxslzyxyExtractor: JXSLZYXYExtractor,
    private val cddxExtractor: CDDXExtractor,
    private val sckjzyxyExtractor: SCKJZYXYExtractor
    // === Agent 3 添加区域结束 ===
) {
    
    /**
     * 获取指定学校的提取器
     * 
     * @param schoolId 学校标识符
     * @return 对应的提取器，如果不存在则返回null
     */
    fun getExtractor(schoolId: String): SchoolScheduleExtractor? {
        return when (schoolId.lowercase()) {
            // 重庆电力高等专科学校 - 正方系统
            "cqepc", "重庆电力高等专科学校", "jwxt.cqepc.edu.cn", "11848" -> cqepcExtractor
            
            // ========== 树维教务系统 (20所学校) ==========
            // 重庆科技学院
            "cqust", "重庆科技学院" -> cqustExtractor
            
            // 广州工商学院
            "gzgsxy", "广州工商学院", "广工商" -> gzgsxyExtractor
            
            // 上海杉达学院
            "shsd", "上海杉达学院", "上海杉达", "杉达" -> shsdExtractor
            
            // 中原科技学院
            "zykjxy", "中原科技学院", "中原科技" -> zykjxyExtractor
            
            // 内蒙古财经大学
            "nmgcjdx", "内蒙古财经大学", "内财大" -> nmgcjdxExtractor
            
            // 商丘工学院
            "sqgxy", "商丘工学院", "商工" -> sqgxyExtractor
            
            // 商丘师范学院
            "sqsfxy", "商丘师范学院", "商师" -> sqsfxyExtractor
            
            // 安徽医科大学
            "ahykdx", "安徽医科大学", "安医大" -> ahykdxExtractor
            
            // 安徽科技学院
            "ahkjxy", "安徽科技学院", "安科" -> ahkjxyExtractor
            
            // 山东水利职业学院
            "sdslzyxy", "山东水利职业学院", "山水院" -> sdslzyxyExtractor
            
            // 成都信息工程大学
            "cdxxgcdx", "成都信息工程大学", "成信大" -> cdxxgcdxExtractor
            
            // 武汉职业技术学院
            "whzyjsxy", "武汉职业技术学院", "武职" -> whzyjsxyExtractor
            
            // 河南理工大学
            "hnlgdx", "河南理工大学", "河理工" -> hnlgdxExtractor
            
            // 西安航空学院
            "xahkxy", "西安航空学院", "西航" -> xahkxyExtractor
            
            // 辽宁科技学院
            "lnkjxy", "辽宁科技学院", "辽科院" -> lnkjxyExtractor
            
            // 辽宁装备制造职业技术学院
            "lnzbzzzyjsxy", "辽宁装备制造职业技术学院", "辽装院" -> lnzbzzzyjsxyExtractor
            
            // 郑州大学
            "zzdx", "郑州大学", "郑大" -> zzdxExtractor
            
            // 郑州西亚斯学院
            "zzxys", "郑州西亚斯学院", "郑州西亚斯", "西亚斯" -> zzxysExtractor
            
            // 重庆医科大学
            "cqykdx", "重庆医科大学", "重医" -> cqykdxExtractor
            
            // 长江大学
            "cjdx", "长江大学", "长大" -> cjdxExtractor
            
            // ========== 强智教务系统 (Agent 2) ==========
            // 北京财贸职业学院
            "bjcmzyxy", "北京财贸职业学院", "北京财贸" -> bjcmzyxyExtractor
            // 南宁师范大学
            "nnsfdx", "南宁师范大学", "南宁师大" -> nnsfdxExtractor
            // 山东大学
            "sddx", "山东大学", "山大" -> sddxExtractor
            // 广东农工商
            "gdngs", "广东农工商职业技术学院", "广东农工商" -> gdngsExtractor
            // 广州华商学院
            "gzhsxy", "广州华商学院", "华商学院" -> gzhsxyExtractor
            // 成都工业学院
            "cdgyxy", "成都工业学院", "成工院" -> cdgyxyExtractor
            // 武汉信息传播职业技术学院
            "whxxcbzyjsxy", "武汉信息传播职业技术学院", "武信" -> whxxcbzyjsxyExtractor
            // 武汉工程科技学院
            "whgckjxy", "武汉工程科技学院", "武工科" -> whgckjxyExtractor
            // 湖南中医药大学
            "hnzyy", "湖南中医药大学", "湖南中医药" -> hnzyyExtractor
            // 衡阳师范学院
            "hysfxy", "衡阳师范学院", "衡阳师院" -> hysfxyExtractor
            // 重庆人文科技学院
            "cqrwkjxy", "重庆人文科技学院", "重庆人文" -> cqrwkjxyExtractor
            // 长春大学
            "ccdx", "长春大学" -> ccdxExtractor
            // 北京财贸职业学院-手机端
            "bjcmzyxy_mobile", "北京财贸职业学院(手机端)" -> bjcmzyxyMobileExtractor
            // 武汉信息传播职业技术学院-手机端
            "whxxcbzyjsxy_mobile", "武汉信息传播职业技术学院(手机端)" -> whxxcbzyjsxyMobileExtractor
            // 湖南软件职业技术学院
            "hnrjzyjsxy", "湖南软件职业技术学院", "湖南软件" -> hnrjzyjsxyExtractor
            
            // ========== 青果教务系统 (Agent 2) ==========
            // 华北水利水电大学
            "hbslsd", "华北水利水电大学", "华水" -> hbslsdExtractor
            // 华北水利水电大学-旧版
            "hbslsd_old", "华北水利水电大学(旧版)" -> hbslsdOldExtractor
            // 山东女子学院
            "sdnzxy", "山东女子学院", "山东女院" -> sdnzxyExtractor
            // 新疆大学
            "xjdx", "新疆大学", "新大" -> xjdxExtractor
            // 昆明医科大学
            "kmykdx", "昆明医科大学", "昆医大" -> kmykdxExtractor
            // 江汉大学
            "jhdx", "江汉大学", "江大" -> jhdxExtractor
            // 河南农业大学
            "hnnydx", "河南农业大学", "河南农大" -> hnnydxExtractor
            // 河西学院
            "hxxy", "河西学院", "河西" -> hxxyExtractor
            // 湛江科技学院(青果)
            "zjkjxy_qingguo", "湛江科技学院(青果)" -> zjkjxyQingguoExtractor
            // 烟台南山学院
            "ytnsxy", "烟台南山学院", "南山学院" -> ytnsxyExtractor
            // 西安翻译学院
            "xafyxy", "西安翻译学院", "西翻" -> xafyxyExtractor
            // 郑州升达经贸管理学院
            "zzsdjmglxy", "郑州升达经贸管理学院", "郑州升达" -> zzsdjmglxyExtractor
            // 陇东学院
            "ldxy", "陇东学院", "陇东" -> ldxyExtractor
            
            // === Agent 1 学校映射开始 ===
            // 新正方教务系统 (19所)
            "dhlgdx", "东华理工大学" -> dhlgdxExtractor
            "btzyjsxy", "包头职业技术学院" -> btzyjsxyExtractor
            "hebhdxy", "哈尔滨华德学院" -> hebhdxyExtractor
            "ksdx", "喀什大学" -> ksdxExtractor
            "scgsxy", "四川工商学院" -> scgsxyExtractor
            "scqhgdx", "四川轻化工大学" -> scqhgdxExtractor
            "tykjdx", "太原科技大学" -> tykjdxExtractor
            "nxsfxy", "宁夏师范学院" -> nxsfxyExtractor
            "ahjzdx", "安徽建筑大学" -> ahjzdxExtractor
            "sdsfxy", "山东师范大学" -> sdsfxyExtractor
            "gxwgyxy", "广西外国语学院" -> gxwgyxyExtractor
            "whdx", "武汉大学" -> whdxExtractor
            "hbcmxy", "河北传媒学院" -> hbcmxyExtractor
            "szxxzyjsxy", "深圳信息职业技术学院" -> szxxzyjsxyExtractor
            "hbjtxzyjsxy", "湖北交通职业技术学院" -> hbjtxzyjsxyExtractor
            "zjkjxy", "湛江科技学院" -> zjkjxyExtractor
            "ycsfxy", "盐城师范学院" -> ycsfxyExtractor
            "gzsfdx", "贵州师范大学" -> gzsfdxExtractor
            "hznydx", "华中农业大学" -> hznydxExtractor
            // 正方教务系统 (2所)
            "gzsxy", "广州商学院" -> gzsxyExtractor
            "cdyszydx", "成都艺术职业大学" -> cdyszydxExtractor
            // 个性化正方 (1所)
            "hznydx_byyt", "华中农业大学-本研一体化" -> hznydxByytExtractor
            // URP教务系统 (4所)
            "nmgkjdx", "内蒙古科技大学" -> nmgkjdxExtractor
            "hblgdx", "华北理工大学" -> hblgdxExtractor
            "hbnydx", "河北农业大学" -> hbnydxExtractor
            "ytdx", "烟台大学" -> ytdxExtractor
            // 金智教务系统 (3所)
            "ahyxgdzkxx", "安徽医学高等专科学校" -> ahyxgdzkxxExtractor
            "ysdx", "燕山大学" -> ysdxExtractor
            "ccqcgygdzkxx", "长春汽车工业高等专科学校" -> ccqcgygdzkxxExtractor
            // === Agent 1 学校映射结束 ===
            
            // === Agent 3 学校映射开始 ===
            // 自研教务系统 (13所)
            "njzyydx", "南京中医药大学" -> njzyydxExtractor
            "hebgydx", "哈尔滨工业大学", "哈工大" -> hebgydxExtractor
            "sddyykdx", "山东第一医科大学" -> sddyykdxExtractor
            "sdypzyxy", "山东药品职业学院" -> sdypzyxyExtractor
            "sxgckjzydx", "山西工程科技职业大学" -> sxgckjzydxExtractor
            "whzyjsxy_custom", "武汉职业技术学院(自研)", "武职(自研)" -> whzyjsxyCustomExtractor
            "hnzyydx_yjs", "河南中医药大学(研究生)", "河南中医药大学研究生" -> hnzyydxYjsExtractor
            "qzsfxy", "泉州师范学院" -> qzsfxyExtractor
            "tzzyjsxy", "泰州职业技术学院" -> tzzyjsxyExtractor
            "xajzkjdx", "西安建筑科技大学" -> xajzkjdxExtractor
            "gzgcyyjsxy", "贵州工程应用技术学院" -> gzgcyyjsxyExtractor
            "qddx", "青岛大学" -> qddxExtractor
            "qqheyxy", "齐齐哈尔医学院" -> qqheyxyExtractor
            // 乘方教务系统 (2所)
            "gdjtzyjsxy", "广东交通职业技术学院" -> gdjtzyjsxyExtractor
            "gzzyydx", "广州中医药大学" -> gzzyydxExtractor
            // 其他系统 (8所)
            "whjgzyxy", "武汉警官职业学院" -> whjgzyxyExtractor
            "jzlgzyxy", "荆州理工职业学院" -> jzlgzyxyExtractor
            "xaoyxy", "西安欧亚学院" -> xaoyxyExtractor
            "dytydzkjxx", "德阳通用电子科技学校" -> dytydzkjxxExtractor
            "ccsfdx", "长春师范大学" -> ccsfdxExtractor
            "jxslzyxy", "江西水利职业学院" -> jxslzyxyExtractor
            "cddx", "成都大学" -> cddxExtractor
            "sckjzyxy", "四川科技职业学院" -> sckjzyxyExtractor
            // === Agent 3 学校映射结束 ===
            
            else -> {
                android.util.Log.w("SchoolExtractorFactory", "❌ 未找到学校 '$schoolId' 的提取器")
                null
            }
        }
    }
    
    /**
     * 根据URL自动检测学校
     * 
     * @param url 页面URL
     * @return 匹配的提取器，如果无法识别则返回null
     */
    fun detectExtractorByUrl(url: String): SchoolScheduleExtractor? {
        return when {
            // 重庆电力高专 - 正方系统
            url.contains("jwxt.cqepc.edu.cn", ignoreCase = true) -> cqepcExtractor
            url.contains("cqepc.edu.cn", ignoreCase = true) -> cqepcExtractor
            
            // ========== 树维系统学校URL匹配 ==========
            url.contains("web.cqust.edu.cn", ignoreCase = true) -> cqustExtractor
            url.contains("eams.gzgs.edu.cn", ignoreCase = true) -> gzgsxyExtractor
            url.contains("jwgl.sandau.edu.cn", ignoreCase = true) -> shsdExtractor
            url.contains("jwxt.zykj.edu.cn", ignoreCase = true) -> zykjxyExtractor
            url.contains("jwxt.imufe.edu.cn", ignoreCase = true) -> nmgcjdxExtractor
            url.contains("jwgl.sqgxy.edu.cn", ignoreCase = true) -> sqgxyExtractor
            url.contains("jw.sqnu.edu.cn", ignoreCase = true) -> sqsfxyExtractor
            url.contains("jxgl.ahmu.edu.cn", ignoreCase = true) -> ahykdxExtractor
            url.contains("jw.ahstu.edu.cn", ignoreCase = true) -> ahkjxyExtractor
            url.contains("jw.sdwcvc.edu.cn", ignoreCase = true) -> sdslzyxyExtractor
            url.contains("jwgl.cuit.edu.cn", ignoreCase = true) -> cdxxgcdxExtractor
            url.contains("jxgl.wtc.edu.cn", ignoreCase = true) -> whzyjsxyExtractor
            url.contains("adms.hpu.edu.cn", ignoreCase = true) -> hnlgdxExtractor
            url.contains("jiaowu.xaau.edu.cn", ignoreCase = true) -> xahkxyExtractor
            url.contains("jwc.lnist.edu.cn", ignoreCase = true) -> lnkjxyExtractor
            url.contains("xsc.ltcem.com", ignoreCase = true) -> lnzbzzzyjsxyExtractor
            url.contains("jw.zzu.edu.cn", ignoreCase = true) -> zzdxExtractor
            url.contains("ems.sias.edu.cn", ignoreCase = true) -> zzxysExtractor
            url.contains("jwc.cqmu.edu.cn", ignoreCase = true) -> cqykdxExtractor
            url.contains("jw.yangtzeu.edu.cn", ignoreCase = true) -> cjdxExtractor
            
            // ========== 强智教务系统学校URL匹配 (Agent 2) ==========
            url.contains("jwgl.bjczy.edu.cn", ignoreCase = true) -> bjcmzyxyExtractor
            url.contains("jwc.nnnu.edu.cn", ignoreCase = true) -> nnsfdxExtractor
            url.contains("bkjws.sdu.edu.cn", ignoreCase = true) -> sddxExtractor
            url.contains("jwmis.gdaib.edu.cn", ignoreCase = true) -> gdngsExtractor
            url.contains("jwc.gdhsc.edu.cn", ignoreCase = true) -> gzhsxyExtractor
            url.contains("jwglxt.cdtu.edu.cn", ignoreCase = true) -> cdgyxyExtractor
            url.contains("jwgl.whxxcb.com", ignoreCase = true) -> whxxcbzyjsxyExtractor
            url.contains("jwgl.whgc.edu.cn", ignoreCase = true) -> whgckjxyExtractor
            url.contains("jwgl.hnucm.edu.cn", ignoreCase = true) -> hnzyyExtractor
            url.contains("jwgl.hynu.edu.cn", ignoreCase = true) -> hysfxyExtractor
            url.contains("jwgl.cqrk.edu.cn", ignoreCase = true) -> cqrwkjxyExtractor
            url.contains("jwgl.ccu.edu.cn", ignoreCase = true) -> ccdxExtractor
            url.contains("/bjcmzyxy/", ignoreCase = true) -> bjcmzyxyMobileExtractor
            url.contains("bzb_njwhd", ignoreCase = true) -> whxxcbzyjsxyMobileExtractor
            url.contains("hnrjzyxyhd", ignoreCase = true) -> hnrjzyjsxyExtractor
            
            // ========== 青果教务系统学校URL匹配 (Agent 2) ==========
            url.contains("hsjw.ncwu.edu.cn", ignoreCase = true) -> hbslsdExtractor
            url.contains("jwxt.sdwu.edu.cn", ignoreCase = true) -> sdnzxyExtractor
            url.contains("portal.xju.edu.cn", ignoreCase = true) -> xjdxExtractor
            url.contains("jwxt.kmmu.edu.cn", ignoreCase = true) -> kmykdxExtractor
            url.contains("jwgl.jhun.edu.cn", ignoreCase = true) -> jhdxExtractor
            url.contains("jwgl.henau.edu.cn", ignoreCase = true) -> hnnydxExtractor
            url.contains("jwxt.hxu.edu.cn", ignoreCase = true) -> hxxyExtractor
            url.contains("jwgl.zjkjedu.cn", ignoreCase = true) -> zjkjxyQingguoExtractor
            url.contains("jwxt.nanshan.edu.cn", ignoreCase = true) -> ytnsxyExtractor
            url.contains("jwgl.xafy.edu.cn", ignoreCase = true) -> xafyxyExtractor
            url.contains("jwgl.shengda.edu.cn", ignoreCase = true) -> zzsdjmglxyExtractor
            url.contains("jwgl.ldxy.edu.cn", ignoreCase = true) -> ldxyExtractor
            
            // === Agent 1 URL匹配开始 ===
            // 新正方教务系统
            url.contains("jwc.ecut.edu.cn", ignoreCase = true) -> dhlgdxExtractor
            url.contains("jwgl.btvtc.edu.cn", ignoreCase = true) -> btzyjsxyExtractor
            url.contains("jwgl.hdhxy.edu.cn", ignoreCase = true) -> hebhdxyExtractor
            url.contains("jwgl.ksu.edu.cn", ignoreCase = true) -> ksdxExtractor
            url.contains("jwgl.stbu.edu.cn", ignoreCase = true) -> scgsxyExtractor
            url.contains("jwgl.suse.edu.cn", ignoreCase = true) -> scqhgdxExtractor
            url.contains("jwgl.tyust.edu.cn", ignoreCase = true) -> tykjdxExtractor
            url.contains("jwgl.nxnu.edu.cn", ignoreCase = true) -> nxsfxyExtractor
            url.contains("jwgl.ahjzu.edu.cn", ignoreCase = true) -> ahjzdxExtractor
            url.contains("bkjx.sdnu.edu.cn", ignoreCase = true) -> sdsfxyExtractor
            url.contains("jwgl.gxufl.com", ignoreCase = true) -> gxwgyxyExtractor
            url.contains("210.42.121.241", ignoreCase = true) -> whdxExtractor
            url.contains("jwgl.hebic.cn", ignoreCase = true) -> hbcmxyExtractor
            url.contains("jwgl.sziit.edu.cn", ignoreCase = true) -> szxxzyjsxyExtractor
            url.contains("jwgl.hbctc.edu.cn", ignoreCase = true) -> hbjtxzyjsxyExtractor
            url.contains("jwgl.zjkjxy.edu.cn", ignoreCase = true) -> zjkjxyExtractor
            url.contains("jwgl.yctc.edu.cn", ignoreCase = true) -> ycsfxyExtractor
            url.contains("jwgl.gznu.edu.cn", ignoreCase = true) -> gzsfdxExtractor
            url.contains("jwgl.hzau.edu.cn", ignoreCase = true) && url.contains("index_initMenu", ignoreCase = true) -> hznydxByytExtractor
            url.contains("jwgl.hzau.edu.cn", ignoreCase = true) -> hznydxExtractor
            // 正方教务系统（旧版）
            url.contains("jw.gzcc.cn", ignoreCase = true) -> gzsxyExtractor
            url.contains("jwgl.cdau.edu.cn", ignoreCase = true) -> cdyszydxExtractor
            // URP教务系统
            url.contains("jwgl.imust.edu.cn", ignoreCase = true) -> nmgkjdxExtractor
            url.contains("jwgl.ncst.edu.cn", ignoreCase = true) -> hblgdxExtractor
            url.contains("jwgl.hebau.edu.cn", ignoreCase = true) -> hbnydxExtractor
            url.contains("jwgl.ytu.edu.cn", ignoreCase = true) -> ytdxExtractor
            // 金智教务系统
            url.contains("jwgl.ahyz.cn", ignoreCase = true) -> ahyxgdzkxxExtractor
            url.contains("jwgl.ysu.edu.cn", ignoreCase = true) -> ysdxExtractor
            url.contains("jwgl.caii.edu.cn", ignoreCase = true) -> ccqcgygdzkxxExtractor
            // === Agent 1 URL匹配结束 ===
            
            // === Agent 3 URL匹配开始 ===
            // 自研教务系统 (13所)
            url.contains("njucm.edu.cn", ignoreCase = true) -> njzyydxExtractor
            url.contains("hit.edu.cn", ignoreCase = true) || url.contains("jwts.hit.edu.cn", ignoreCase = true) -> hebgydxExtractor
            url.contains("sddfvc.cn", ignoreCase = true) || url.contains("sdfmu.edu.cn", ignoreCase = true) -> sddyykdxExtractor
            url.contains("sddfvc.cn", ignoreCase = true) && url.contains("sdyp", ignoreCase = true) -> sdypzyxyExtractor
            url.contains("sxgy.cn", ignoreCase = true) -> sxgckjzydxExtractor
            url.contains("wtc.edu.cn", ignoreCase = true) && url.contains("M1402", ignoreCase = true) -> whzyjsxyCustomExtractor
            url.contains("hactcm.edu.cn", ignoreCase = true) && url.contains("yjsy", ignoreCase = true) -> hnzyydxYjsExtractor
            url.contains("qztc.edu.cn", ignoreCase = true) && url.contains("cx_kb_bjkb_bj", ignoreCase = true) -> qzsfxyExtractor
            url.contains("tzpc.edu.cn", ignoreCase = true) -> tzzyjsxyExtractor
            url.contains("xauat.edu.cn", ignoreCase = true) && url.contains("course-table", ignoreCase = true) -> xajzkjdxExtractor
            url.contains("gues.edu.cn", ignoreCase = true) -> gzgcyyjsxyExtractor
            url.contains("qdu.edu.cn", ignoreCase = true) -> qddxExtractor
            url.contains("qmu.edu.cn", ignoreCase = true) -> qqheyxyExtractor
            // 乘方教务系统 (2所)
            url.contains("gdcp.cn", ignoreCase = true) -> gdjtzyjsxyExtractor
            url.contains("gzucm.edu.cn", ignoreCase = true) -> gzzyydxExtractor
            // 其他系统 (8所)
            url.contains("whpa.edu.cn", ignoreCase = true) -> whjgzyxyExtractor
            url.contains("jzlgedu.cn", ignoreCase = true) -> jzlgzyxyExtractor
            url.contains("eurasia.edu", ignoreCase = true) && url.contains("OuterStudWeekOfTimeTable", ignoreCase = true) -> xaoyxyExtractor
            url.contains("aixiaoyuan.cn", ignoreCase = true) && url.contains("dytyzj", ignoreCase = true) -> dytydzkjxxExtractor
            url.contains("ccsfu.edu.cn", ignoreCase = true) -> ccsfdxExtractor
            url.contains("jxssly.cn", ignoreCase = true) -> jxslzyxyExtractor
            (url.contains("cdu.edu.cn", ignoreCase = true) && url.contains("jw", ignoreCase = true)) || url.contains("chaoxing.com", ignoreCase = true) -> cddxExtractor
            url.contains("scstc.cn", ignoreCase = true) -> sckjzyxyExtractor
            // === Agent 3 URL匹配结束 ===
            
            // 树维系统通用特征
            url.contains("/eams/", ignoreCase = true) && url.contains("courseTableForStd", ignoreCase = true) -> cqustExtractor
            
            else -> null
        }
    }
    
    /**
     * 根据HTML内容自动检测学校
     * 
     * @param html 页面HTML
     * @param url 页面URL
     * @return 匹配的提取器，如果无法识别则返回null
     */
    fun detectExtractorByContent(html: String, url: String): SchoolScheduleExtractor? {
        // 首先尝试URL匹配
        detectExtractorByUrl(url)?.let { return it }
        
        // 然后依次检查每个提取器是否能识别此页面
        return when {
            cqepcExtractor.isSchedulePage(html, url) -> cqepcExtractor
            cqustExtractor.isSchedulePage(html, url) -> cqustExtractor
            gzgsxyExtractor.isSchedulePage(html, url) -> gzgsxyExtractor
            shsdExtractor.isSchedulePage(html, url) -> shsdExtractor
            zykjxyExtractor.isSchedulePage(html, url) -> zykjxyExtractor
            nmgcjdxExtractor.isSchedulePage(html, url) -> nmgcjdxExtractor
            sqgxyExtractor.isSchedulePage(html, url) -> sqgxyExtractor
            sqsfxyExtractor.isSchedulePage(html, url) -> sqsfxyExtractor
            ahykdxExtractor.isSchedulePage(html, url) -> ahykdxExtractor
            ahkjxyExtractor.isSchedulePage(html, url) -> ahkjxyExtractor
            sdslzyxyExtractor.isSchedulePage(html, url) -> sdslzyxyExtractor
            cdxxgcdxExtractor.isSchedulePage(html, url) -> cdxxgcdxExtractor
            whzyjsxyExtractor.isSchedulePage(html, url) -> whzyjsxyExtractor
            hnlgdxExtractor.isSchedulePage(html, url) -> hnlgdxExtractor
            xahkxyExtractor.isSchedulePage(html, url) -> xahkxyExtractor
            lnkjxyExtractor.isSchedulePage(html, url) -> lnkjxyExtractor
            lnzbzzzyjsxyExtractor.isSchedulePage(html, url) -> lnzbzzzyjsxyExtractor
            zzdxExtractor.isSchedulePage(html, url) -> zzdxExtractor
            zzxysExtractor.isSchedulePage(html, url) -> zzxysExtractor
            cqykdxExtractor.isSchedulePage(html, url) -> cqykdxExtractor
            cjdxExtractor.isSchedulePage(html, url) -> cjdxExtractor
            bjcmzyxyExtractor.isSchedulePage(html, url) -> bjcmzyxyExtractor
            nnsfdxExtractor.isSchedulePage(html, url) -> nnsfdxExtractor
            sddxExtractor.isSchedulePage(html, url) -> sddxExtractor
            gdngsExtractor.isSchedulePage(html, url) -> gdngsExtractor
            gzhsxyExtractor.isSchedulePage(html, url) -> gzhsxyExtractor
            cdgyxyExtractor.isSchedulePage(html, url) -> cdgyxyExtractor
            whxxcbzyjsxyExtractor.isSchedulePage(html, url) -> whxxcbzyjsxyExtractor
            whgckjxyExtractor.isSchedulePage(html, url) -> whgckjxyExtractor
            hnzyyExtractor.isSchedulePage(html, url) -> hnzyyExtractor
            hysfxyExtractor.isSchedulePage(html, url) -> hysfxyExtractor
            cqrwkjxyExtractor.isSchedulePage(html, url) -> cqrwkjxyExtractor
            ccdxExtractor.isSchedulePage(html, url) -> ccdxExtractor
            bjcmzyxyMobileExtractor.isSchedulePage(html, url) -> bjcmzyxyMobileExtractor
            whxxcbzyjsxyMobileExtractor.isSchedulePage(html, url) -> whxxcbzyjsxyMobileExtractor
            hnrjzyjsxyExtractor.isSchedulePage(html, url) -> hnrjzyjsxyExtractor
            // === Agent 2: 青果教务系统内容检测 ===
            hbslsdExtractor.isSchedulePage(html, url) -> hbslsdExtractor
            hbslsdOldExtractor.isSchedulePage(html, url) -> hbslsdOldExtractor
            sdnzxyExtractor.isSchedulePage(html, url) -> sdnzxyExtractor
            xjdxExtractor.isSchedulePage(html, url) -> xjdxExtractor
            kmykdxExtractor.isSchedulePage(html, url) -> kmykdxExtractor
            jhdxExtractor.isSchedulePage(html, url) -> jhdxExtractor
            hnnydxExtractor.isSchedulePage(html, url) -> hnnydxExtractor
            hxxyExtractor.isSchedulePage(html, url) -> hxxyExtractor
            zjkjxyQingguoExtractor.isSchedulePage(html, url) -> zjkjxyQingguoExtractor
            ytnsxyExtractor.isSchedulePage(html, url) -> ytnsxyExtractor
            xafyxyExtractor.isSchedulePage(html, url) -> xafyxyExtractor
            zzsdjmglxyExtractor.isSchedulePage(html, url) -> zzsdjmglxyExtractor
            ldxyExtractor.isSchedulePage(html, url) -> ldxyExtractor
            // === Agent 1 内容检测开始 ===
            dhlgdxExtractor.isSchedulePage(html, url) -> dhlgdxExtractor
            btzyjsxyExtractor.isSchedulePage(html, url) -> btzyjsxyExtractor
            hebhdxyExtractor.isSchedulePage(html, url) -> hebhdxyExtractor
            ksdxExtractor.isSchedulePage(html, url) -> ksdxExtractor
            scgsxyExtractor.isSchedulePage(html, url) -> scgsxyExtractor
            scqhgdxExtractor.isSchedulePage(html, url) -> scqhgdxExtractor
            tykjdxExtractor.isSchedulePage(html, url) -> tykjdxExtractor
            nxsfxyExtractor.isSchedulePage(html, url) -> nxsfxyExtractor
            ahjzdxExtractor.isSchedulePage(html, url) -> ahjzdxExtractor
            sdsfxyExtractor.isSchedulePage(html, url) -> sdsfxyExtractor
            gxwgyxyExtractor.isSchedulePage(html, url) -> gxwgyxyExtractor
            whdxExtractor.isSchedulePage(html, url) -> whdxExtractor
            hbcmxyExtractor.isSchedulePage(html, url) -> hbcmxyExtractor
            szxxzyjsxyExtractor.isSchedulePage(html, url) -> szxxzyjsxyExtractor
            hbjtxzyjsxyExtractor.isSchedulePage(html, url) -> hbjtxzyjsxyExtractor
            zjkjxyExtractor.isSchedulePage(html, url) -> zjkjxyExtractor
            ycsfxyExtractor.isSchedulePage(html, url) -> ycsfxyExtractor
            gzsfdxExtractor.isSchedulePage(html, url) -> gzsfdxExtractor
            hznydxExtractor.isSchedulePage(html, url) -> hznydxExtractor
            gzsxyExtractor.isSchedulePage(html, url) -> gzsxyExtractor
            cdyszydxExtractor.isSchedulePage(html, url) -> cdyszydxExtractor
            hznydxByytExtractor.isSchedulePage(html, url) -> hznydxByytExtractor
            nmgkjdxExtractor.isSchedulePage(html, url) -> nmgkjdxExtractor
            hblgdxExtractor.isSchedulePage(html, url) -> hblgdxExtractor
            hbnydxExtractor.isSchedulePage(html, url) -> hbnydxExtractor
            ytdxExtractor.isSchedulePage(html, url) -> ytdxExtractor
            ahyxgdzkxxExtractor.isSchedulePage(html, url) -> ahyxgdzkxxExtractor
            ysdxExtractor.isSchedulePage(html, url) -> ysdxExtractor
            ccqcgygdzkxxExtractor.isSchedulePage(html, url) -> ccqcgygdzkxxExtractor
            // === Agent 1 内容检测结束 ===
            // === Agent 3 内容检测开始 ===
            // 自研教务系统 (13所)
            njzyydxExtractor.isSchedulePage(html, url) -> njzyydxExtractor
            hebgydxExtractor.isSchedulePage(html, url) -> hebgydxExtractor
            sddyykdxExtractor.isSchedulePage(html, url) -> sddyykdxExtractor
            sdypzyxyExtractor.isSchedulePage(html, url) -> sdypzyxyExtractor
            sxgckjzydxExtractor.isSchedulePage(html, url) -> sxgckjzydxExtractor
            whzyjsxyCustomExtractor.isSchedulePage(html, url) -> whzyjsxyCustomExtractor
            hnzyydxYjsExtractor.isSchedulePage(html, url) -> hnzyydxYjsExtractor
            qzsfxyExtractor.isSchedulePage(html, url) -> qzsfxyExtractor
            tzzyjsxyExtractor.isSchedulePage(html, url) -> tzzyjsxyExtractor
            xajzkjdxExtractor.isSchedulePage(html, url) -> xajzkjdxExtractor
            gzgcyyjsxyExtractor.isSchedulePage(html, url) -> gzgcyyjsxyExtractor
            qddxExtractor.isSchedulePage(html, url) -> qddxExtractor
            qqheyxyExtractor.isSchedulePage(html, url) -> qqheyxyExtractor
            // 乘方教务系统 (2所)
            gdjtzyjsxyExtractor.isSchedulePage(html, url) -> gdjtzyjsxyExtractor
            gzzyydxExtractor.isSchedulePage(html, url) -> gzzyydxExtractor
            // 其他系统 (8所)
            whjgzyxyExtractor.isSchedulePage(html, url) -> whjgzyxyExtractor
            jzlgzyxyExtractor.isSchedulePage(html, url) -> jzlgzyxyExtractor
            xaoyxyExtractor.isSchedulePage(html, url) -> xaoyxyExtractor
            dytydzkjxxExtractor.isSchedulePage(html, url) -> dytydzkjxxExtractor
            ccsfdxExtractor.isSchedulePage(html, url) -> ccsfdxExtractor
            jxslzyxyExtractor.isSchedulePage(html, url) -> jxslzyxyExtractor
            cddxExtractor.isSchedulePage(html, url) -> cddxExtractor
            sckjzyxyExtractor.isSchedulePage(html, url) -> sckjzyxyExtractor
            // === Agent 3 内容检测结束 ===
            else -> null
        }
    }
    
    /**
     * 获取所有支持的学校列表
     */
    fun getSupportedSchools(): List<SchoolScheduleExtractor> {
        return listOf(
            cqepcExtractor,
            cqustExtractor,
            gzgsxyExtractor,
            shsdExtractor,
            zykjxyExtractor,
            nmgcjdxExtractor,
            sqgxyExtractor,
            sqsfxyExtractor,
            ahykdxExtractor,
            ahkjxyExtractor,
            sdslzyxyExtractor,
            cdxxgcdxExtractor,
            whzyjsxyExtractor,
            hnlgdxExtractor,
            xahkxyExtractor,
            lnkjxyExtractor,
            lnzbzzzyjsxyExtractor,
            zzdxExtractor,
            zzxysExtractor,
            cqykdxExtractor,
            cjdxExtractor,
            // === Agent 2: 强智教务系统 ===
            bjcmzyxyExtractor,
            nnsfdxExtractor,
            sddxExtractor,
            gdngsExtractor,
            gzhsxyExtractor,
            cdgyxyExtractor,
            whxxcbzyjsxyExtractor,
            whgckjxyExtractor,
            hnzyyExtractor,
            hysfxyExtractor,
            cqrwkjxyExtractor,
            ccdxExtractor,
            bjcmzyxyMobileExtractor,
            whxxcbzyjsxyMobileExtractor,
            hnrjzyjsxyExtractor,
            // === Agent 2: 青果教务系统 ===
            hbslsdExtractor,
            hbslsdOldExtractor,
            sdnzxyExtractor,
            xjdxExtractor,
            kmykdxExtractor,
            jhdxExtractor,
            hnnydxExtractor,
            hxxyExtractor,
            zjkjxyQingguoExtractor,
            ytnsxyExtractor,
            xafyxyExtractor,
            zzsdjmglxyExtractor,
            ldxyExtractor,
            // === Agent 1 提取器列表开始 ===
            dhlgdxExtractor,
            btzyjsxyExtractor,
            hebhdxyExtractor,
            ksdxExtractor,
            scgsxyExtractor,
            scqhgdxExtractor,
            tykjdxExtractor,
            nxsfxyExtractor,
            ahjzdxExtractor,
            sdsfxyExtractor,
            gxwgyxyExtractor,
            whdxExtractor,
            hbcmxyExtractor,
            szxxzyjsxyExtractor,
            hbjtxzyjsxyExtractor,
            zjkjxyExtractor,
            ycsfxyExtractor,
            gzsfdxExtractor,
            hznydxExtractor,
            gzsxyExtractor,
            cdyszydxExtractor,
            hznydxByytExtractor,
            nmgkjdxExtractor,
            hblgdxExtractor,
            hbnydxExtractor,
            ytdxExtractor,
            ahyxgdzkxxExtractor,
            ysdxExtractor,
            ccqcgygdzkxxExtractor,
            // === Agent 1 提取器列表结束 ===
            // === Agent 3 提取器列表开始 ===
            // 自研教务系统 (13所)
            njzyydxExtractor,
            hebgydxExtractor,
            sddyykdxExtractor,
            sdypzyxyExtractor,
            sxgckjzydxExtractor,
            whzyjsxyCustomExtractor,
            hnzyydxYjsExtractor,
            qzsfxyExtractor,
            tzzyjsxyExtractor,
            xajzkjdxExtractor,
            gzgcyyjsxyExtractor,
            qddxExtractor,
            qqheyxyExtractor,
            // 乘方教务系统 (2所)
            gdjtzyjsxyExtractor,
            gzzyydxExtractor,
            // 其他系统 (8所)
            whjgzyxyExtractor,
            jzlgzyxyExtractor,
            xaoyxyExtractor,
            dytydzkjxxExtractor,
            ccsfdxExtractor,
            jxslzyxyExtractor,
            cddxExtractor,
            sckjzyxyExtractor
            // === Agent 3 提取器列表结束 ===
        )
    }
}







