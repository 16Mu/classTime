package com.wind.ggbond.classtime.di

import com.wind.ggbond.classtime.util.extractor.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    @Binds @IntoMap @StringKey("cqepc") @Singleton
    abstract fun bindCQEPC(impl: CQEPCExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cqust") @Singleton
    abstract fun bindCQUST(impl: CQUSTExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gzgsxy") @Singleton
    abstract fun bindGZGSXY(impl: GZGSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("shsd") @Singleton
    abstract fun bindSHSD(impl: SHSDExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("zykjxy") @Singleton
    abstract fun bindZYKJXY(impl: ZYKJXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("nmgcjdx") @Singleton
    abstract fun bindNMGCJDX(impl: NMGCJDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sqgxy") @Singleton
    abstract fun bindSQGXY(impl: SQGXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sqsfxy") @Singleton
    abstract fun bindSQSFXY(impl: SQSFXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ahykdx") @Singleton
    abstract fun bindAHYKDX(impl: AHYKDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ahkjxy") @Singleton
    abstract fun bindAHKJXY(impl: AHKJXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sdslzyxy") @Singleton
    abstract fun bindSDSLZYXY(impl: SDSLZYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cdxxgcdx") @Singleton
    abstract fun bindCDXXGCDX(impl: CDXXGCDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("whzyjsxy") @Singleton
    abstract fun bindWHZYJSXY(impl: WHZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hnlgdx") @Singleton
    abstract fun bindHNLGDX(impl: HNLGDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("xahkxy") @Singleton
    abstract fun bindXAHKXY(impl: XAHKXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("lnkjxy") @Singleton
    abstract fun bindLNKJXY(impl: LNKJXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("lnzbzzzyjsxy") @Singleton
    abstract fun bindLNZBZZZYJSXY(impl: LNZBZZZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("zzdx") @Singleton
    abstract fun bindZZDX(impl: ZZDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("zzxys") @Singleton
    abstract fun bindZZXYS(impl: ZZXYSExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cqykdx") @Singleton
    abstract fun bindCQYKDX(impl: CQYKDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cjdx") @Singleton
    abstract fun bindCJDX(impl: CJDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("dhlgdx") @Singleton
    abstract fun bindDHLGDX(impl: DHLGDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("btzyjsxy") @Singleton
    abstract fun bindBTZYJSXY(impl: BTZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hebhdxy") @Singleton
    abstract fun bindHEBHDXY(impl: HEBHDXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ksdx") @Singleton
    abstract fun bindKSDX(impl: KSDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("scgsxy") @Singleton
    abstract fun bindSCGSXY(impl: SCGSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("scqhgdx") @Singleton
    abstract fun bindSCQHGDX(impl: SCQHGDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("tykjdx") @Singleton
    abstract fun bindTYKJDX(impl: TYKJDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("nxsfxy") @Singleton
    abstract fun bindNXSFXY(impl: NXSFXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ahjzdx") @Singleton
    abstract fun bindAHJZDX(impl: AHJZDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sdsfxy") @Singleton
    abstract fun bindSDSFXY(impl: SDSFXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gxwgyxy") @Singleton
    abstract fun bindGXWGYXY(impl: GXWGYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("whdx") @Singleton
    abstract fun bindWHDX(impl: WHDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hbcmxy") @Singleton
    abstract fun bindHBCMXY(impl: HBCMXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("szxxzyjsxy") @Singleton
    abstract fun bindSZXXZYJSXY(impl: SZXXZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hbjtxzyjsxy") @Singleton
    abstract fun bindHBJTZYJSXY(impl: HBJTZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("zjkjxy") @Singleton
    abstract fun bindZJKJXY(impl: ZJKJXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ycsfxy") @Singleton
    abstract fun bindYCSFXY(impl: YCSFXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gzsfdx") @Singleton
    abstract fun bindGZSFDX(impl: GZSFDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hznydx") @Singleton
    abstract fun bindHZNYDX(impl: HZNYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gzsxy") @Singleton
    abstract fun bindGZSXY(impl: GZSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cdyszydx") @Singleton
    abstract fun bindCDYSZYDX(impl: CDYSZYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hznydx_byyt") @Singleton
    abstract fun bindHZNYDXBYYT(impl: HZNYDXBYYTExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("nmgkjdx") @Singleton
    abstract fun bindNMGKJDX(impl: NMGKJDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hblgdx") @Singleton
    abstract fun bindHBLGDX(impl: HBLGDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hbnydx") @Singleton
    abstract fun bindHBNYDX(impl: HBNYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ytdx") @Singleton
    abstract fun bindYTDX(impl: YTDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ahyxgdzkxx") @Singleton
    abstract fun bindAHYXGDZKXX(impl: AHYXGDZKXXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ysdx") @Singleton
    abstract fun bindYSDX(impl: YSDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ccqcgygdzkxx") @Singleton
    abstract fun bindCCQCGYGDZKXX(impl: CCQCGYGDZKXXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("bjcmzyxy") @Singleton
    abstract fun bindBJCMZYXY(impl: BJCMZYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("nnsfdx") @Singleton
    abstract fun bindNNSFDX(impl: NNSFDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sddx") @Singleton
    abstract fun bindSDDX(impl: SDDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gdngs") @Singleton
    abstract fun bindGDNGS(impl: GDNGSExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gzhsxy") @Singleton
    abstract fun bindGZHSXY(impl: GZHSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cdgyxy") @Singleton
    abstract fun bindCDGYXY(impl: CDGYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("whxxcbzyjsxy") @Singleton
    abstract fun bindWHXXCBZYJSXY(impl: WHXXCBZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("whgckjxy") @Singleton
    abstract fun bindWHGCKJXY(impl: WHGCKJXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hnzyy") @Singleton
    abstract fun bindHNZYY(impl: HNZYYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hysfxy") @Singleton
    abstract fun bindHYSFXY(impl: HYSFXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cqrwkjxy") @Singleton
    abstract fun bindCQRWKJXY(impl: CQRWKJXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ccdx") @Singleton
    abstract fun bindCCDX(impl: CCDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("bjcmzyxy_mobile") @Singleton
    abstract fun bindBJCMZYXYMobile(impl: BJCMZYXYMobileExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("whxxcbzyjsxy_mobile") @Singleton
    abstract fun bindWHXXCBZYJSXYMobile(impl: WHXXCBZYJSXYMobileExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hnrjzyjsxy") @Singleton
    abstract fun bindHNRJZYJSXY(impl: HNRJZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hbslsd") @Singleton
    abstract fun bindHBSLSD(impl: HBSLSDExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hbslsd_old") @Singleton
    abstract fun bindHBSLSDOld(impl: HBSLSDOldExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sdnzxy") @Singleton
    abstract fun bindSDNZXY(impl: SDNZXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("xjdx") @Singleton
    abstract fun bindXJDX(impl: XJDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("kmykdx") @Singleton
    abstract fun bindKMYKDX(impl: KMYKDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("jhdx") @Singleton
    abstract fun bindJHDX(impl: JHDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hnnydx") @Singleton
    abstract fun bindHNNYDX(impl: HNNYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hxxy") @Singleton
    abstract fun bindHXXY(impl: HXXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("zjkjxy_qingguo") @Singleton
    abstract fun bindZJKJXYQingguo(impl: ZJKJXYQingguoExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ytnsxy") @Singleton
    abstract fun bindYTNSXY(impl: YTNSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("xafyxy") @Singleton
    abstract fun bindXAFYXY(impl: XAFYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("zzsdjmglxy") @Singleton
    abstract fun bindZZSDJMGLXY(impl: ZZSDJMGLXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ldxy") @Singleton
    abstract fun bindLDXY(impl: LDXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("njzyydx") @Singleton
    abstract fun bindNJZYYDX(impl: NJZYYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hebgydx") @Singleton
    abstract fun bindHEBGYDX(impl: HEBGYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sddyykdx") @Singleton
    abstract fun bindSDDYYKDX(impl: SDDYYKDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sdypzyxy") @Singleton
    abstract fun bindSDYPZYXY(impl: SDYPZYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sxgckjzydx") @Singleton
    abstract fun bindSXGCKJZYDX(impl: SXGCKJZYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("whzyjsxy_custom") @Singleton
    abstract fun bindWHZYJSXYCustom(impl: WHZYJSXYCustomExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("hnzyydx_yjs") @Singleton
    abstract fun bindHNZYYDXYJS(impl: HNZYYDXYJSExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("qzsfxy") @Singleton
    abstract fun bindQZSFXY(impl: QZSFXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("tzzyjsxy") @Singleton
    abstract fun bindTZZYJSXY(impl: TZZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("xajzkjdx") @Singleton
    abstract fun bindXAJZKJDX(impl: XAJZKJDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gzgcyyjsxy") @Singleton
    abstract fun bindGZGCYYJSXY(impl: GZGCYYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("qddx") @Singleton
    abstract fun bindQDDX(impl: QDDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("qqheyxy") @Singleton
    abstract fun bindQQHEYXY(impl: QQHEYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gdjtzyjsxy") @Singleton
    abstract fun bindGDJTZYJSXY(impl: GDJTZYJSXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("gzzyydx") @Singleton
    abstract fun bindGZZYYDX(impl: GZZYYDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("whjgzyxy") @Singleton
    abstract fun bindWHJGZYXY(impl: WHJGZYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("jzlgzyxy") @Singleton
    abstract fun bindJZLGZYXY(impl: JZLGZYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("xaoyxy") @Singleton
    abstract fun bindXAOYXY(impl: XAOYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("dytydzkjxx") @Singleton
    abstract fun bindDYTYDZKJXX(impl: DYTYDZKJXXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("ccsfdx") @Singleton
    abstract fun bindCCSFDX(impl: CCSFDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("jxslzyxy") @Singleton
    abstract fun bindJXSLZYXY(impl: JXSLZYXYExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("cddx") @Singleton
    abstract fun bindCDDX(impl: CDDXExtractor): SchoolScheduleExtractor

    @Binds @IntoMap @StringKey("sckjzyxy") @Singleton
    abstract fun bindSCKJZYXY(impl: SCKJZYXYExtractor): SchoolScheduleExtractor
}
