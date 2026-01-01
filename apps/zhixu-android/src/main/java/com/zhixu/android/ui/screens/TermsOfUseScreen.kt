package com.zhixu.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhixu.android.R
import com.zhixu.android.ui.Ionicons

private const val TERMS_OF_USE_ZH = """知序（Zhixu）使用条款

更新日期：2026 年 1 月 1 日
生效日期：2026 年 1 月 1 日

欢迎您使用 知序（Zhixu） 产品及相关服务。

知序的所有权及运营权归 【寒士杰克（www.hansjack.com）】 所有，在本使用条款中简称“我们”。
当您下载、安装、注册、登录或以其他方式使用知序产品及服务，即视为您已阅读、理解并同意接受本使用条款的全部内容。

一、服务说明与使用限制

知序是一款提供 笔记记录、待办管理、文件管理、同步与扩展能力 的效率类产品，仅供用户个人或其所属的团队、组织在合法范围内使用。

未经我们书面许可，用户不得以任何形式对知序进行以下行为：

出售、出租、转让、再授权知序产品或相关服务；
基于知序进行商业化分发、二次运营或类似行为；
利用知序从事任何违反法律法规的活动。

用户可向我们反馈产品建议、意见或改进方案。您理解并同意：

您提交的建议为自愿行为；
我们有权在不向您支付任何费用的情况下，在知序或相关产品中使用、修改或采纳该建议。

二、知识产权声明

知序产品及其相关内容（包括但不限于程序代码、界面设计、交互逻辑、文档、商标、图标等）的知识产权均归我们或相关权利人所有。

未经许可，用户不得：

修改、复制、反编译、反向工程、反汇编知序产品；
制作知序的衍生作品；
删除、遮挡或篡改知序中的版权、商标或权利声明。

用户在知序中创建或上传的内容（包括但不限于文本、图片、音频、视频、Markdown 文件等），其著作权仍归用户或原权利人所有。

三、用户内容与数据权利

用户在知序中创建、上传或同步的内容，仅用于向用户提供产品功能和服务。

为实现同步、备份、分享等功能，用户同意我们在合理范围内对其内容进行存储、处理和传输。

除非出现以下情况之一，我们不会主动公开或披露用户的非公开内容：

法律法规或司法机关依法要求；
为维护我们或用户的合法权益；
出现紧急情况，为保护用户或公众的人身、财产安全；
其他依法合理的情形。

四、隐私保护

我们高度重视用户隐私保护，并将按照《知序隐私政策》处理和保护用户的个人信息。
关于我们如何收集、使用、存储和保护您的信息，请查阅隐私政策页面：

隐私政策地址：【zhixu.app/about/privacy】

您使用知序服务即表示您同意我们依据隐私政策处理您的相关信息。

五、服务变更、中断与终止

我们有权根据产品发展需要，随时对服务内容进行调整、升级或优化。

在合理情况下，我们可能会中断或终止部分或全部服务（如系统维护、不可抗力等）。

对于因服务调整、中断或终止造成的影响，我们将在法律允许的范围内免于承担责任。

六、责任限制与免责

用户理解并同意，使用知序产品及服务的风险由用户自行承担。

在法律允许的最大范围内，我们不对以下情况承担责任：

因网络、设备、系统故障造成的数据丢失或服务中断；
第三方服务、插件、链接或内容导致的问题；
用户自身操作不当导致的损失。

我们不对服务的适用性、连续性、准确性、无错误性作出任何明示或默示保证。

七、用户保障义务

如因用户违反本条款或相关法律法规，导致我们遭受损失、索赔、处罚的，用户应依法承担相应责任，并赔偿我们因此产生的合理费用（包括但不限于律师费、诉讼费等）。

八、法律适用与争议解决

本使用条款的订立、执行与解释均适用 中华人民共和国法律。

因本条款产生的争议，双方应协商解决；协商不成的，提交 我们所在地有管辖权的人民法院 解决。

九、联系我们

如您对本使用条款或知序产品有任何疑问，可通过以下方式联系我们：

联系邮箱：【support@zhixu.app】

官方网站：【zhixu.app】

© 2026 知序 Zhixu"""

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun TermsOfUseScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(enabled = true, onBack = onBack)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { Text(stringResource(R.string.terms_of_use_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Ionicons.ArrowBack),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
            HorizontalDivider(color = dividerColor)
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Text(
            text = TERMS_OF_USE_ZH,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        )
    }
}
