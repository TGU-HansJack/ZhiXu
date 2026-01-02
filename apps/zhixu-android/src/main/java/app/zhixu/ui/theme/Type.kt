package app.zhixu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.zhixu.R

val SourceSansProLightDefaultFamily =
    FontFamily(
        Font(R.font.source_sans_pro_light, weight = FontWeight.Normal),
        Font(R.font.source_sans_pro_light, weight = FontWeight.Light),
    )

val SourceSansProRegularDefaultFamily =
    FontFamily(
        Font(R.font.source_sans_pro_regular, weight = FontWeight.Normal),
        Font(R.font.source_sans_pro_light, weight = FontWeight.Light),
    )

val LxgwWenKaiMonoLightDefaultFamily =
    FontFamily(
        Font(R.font.lxgw_wenkai_mono_light, weight = FontWeight.Normal),
        Font(R.font.lxgw_wenkai_mono_light, weight = FontWeight.Light),
    )

val Typography = Typography()
