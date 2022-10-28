package org.cosmic.ide

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Process
import android.os.Build
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.itsaky.androidide.config.JavacConfigProvider
import org.cosmic.ide.activity.DebugActivity
import org.cosmic.ide.common.util.CoroutineUtil
import org.cosmic.ide.common.util.FileUtil
import org.cosmic.ide.ui.theme.DarkThemeHelper
import org.cosmic.ide.util.dpToPx
import java.io.File
import kotlin.system.exitProcess
import kotlin.concurrent.schedule
import java.util.Timer

import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        FileUtil.setDataDirectory(applicationContext.getExternalFilesDir(null)?.getAbsolutePath()!!)
        CoroutineUtil.inParallel {
            JavacConfigProvider.disableModules()
            dpToPx.initalizeResources(applicationContext.getResources())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HiddenApiBypass.addHiddenApiExemptions("Lsun/misc/Unsafe;");
            }
            DarkThemeHelper.initialize(this)
        }

        Thread.setDefaultUncaughtExceptionHandler {
            _, throwable ->
            val intent = Intent(applicationContext, DebugActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("error", throwable.stackTraceToString())
            val pendingIntent = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

            Timer().schedule(200L) {
                pendingIntent.send()
            }
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }

    companion object {
        private var instance: App? = null

        fun applicationContext() = instance!!.applicationContext

        @JvmStatic
        fun getDefaultSharedPreferences() = PreferenceManager.getDefaultSharedPreferences(applicationContext())

        fun isDarkMode(context: Context): Boolean {
            val darkModeFlag = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return darkModeFlag == Configuration.UI_MODE_NIGHT_YES
        }
    }
}