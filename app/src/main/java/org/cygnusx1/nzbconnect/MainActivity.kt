package org.cygnusx1.nzbconnect

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.cygnusx1.nzbconnect.ui.navigation.AppNavHost
import org.cygnusx1.nzbconnect.ui.theme.NZBConnectTheme
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Force a fixed dp canvas width (and a 1.0 font scale) on every device so the UI looks the
     * same regardless of each user's "Font size" / "Display size" settings or screen size. We
     * derive the density from the screen's shortest physical edge so it's orientation-independent.
     * Lower [TARGET_SMALLEST_WIDTH_DP] => everything bigger; higher => more compact.
     */
    override fun attachBaseContext(newBase: Context) {
        logMetrics("BEFORE", newBase)

        val config = Configuration(newBase.resources.configuration)
        config.fontScale = 1f
        val metrics = newBase.resources.displayMetrics
        val shortestPx = minOf(metrics.widthPixels, metrics.heightPixels)
        if (shortestPx > 0) {
            val density = shortestPx / TARGET_SMALLEST_WIDTH_DP
            config.densityDpi = (density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
            config.screenWidthDp = (metrics.widthPixels / density).roundToInt()
            config.screenHeightDp = (metrics.heightPixels / density).roundToInt()
            config.smallestScreenWidthDp = (shortestPx / density).roundToInt()
        }

        val overridden = newBase.createConfigurationContext(config)
        super.attachBaseContext(overridden)
        logMetrics("AFTER", overridden)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        logMetrics("onCreate", this)
        setContent { App() }
    }

    private fun logMetrics(label: String, ctx: Context) {
        val c = ctx.resources.configuration
        val m = ctx.resources.displayMetrics
        // scaledDensity was deprecated in API 34 because font scaling is no longer linear.
        // applyDimension reports the current px-per-sp, which is what it used to mean.
        val pxPerSp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, m)
        Log.i(
            TAG,
            "[$label] fontScale=${c.fontScale} cfgDensityDpi=${c.densityDpi} " +
                "screenWidthDp=${c.screenWidthDp} smallestWidthDp=${c.smallestScreenWidthDp} | " +
                "dm.density=${m.density} dm.densityDpi=${m.densityDpi} " +
                "dm.pxPerSp=$pxPerSp widthPx=${m.widthPixels} heightPx=${m.heightPixels}",
        )
    }

    private companion object {
        const val TAG = "NZBScale"

        // The dp width every device is normalized to, regardless of Font/Display-size settings.
        // 520 ≈ the Pixel 8 Pro's current compact look, so the Pixel 6 matches it.
        // Lower this for a bigger UI, raise for more compact.
        const val TARGET_SMALLEST_WIDTH_DP = 520f
    }
}

@Composable
private fun App() {
    NZBConnectTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavHost()
        }
    }
}
