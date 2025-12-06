package ltd.nextalone.pkginstallerplus.hook

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.UserManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import ltd.nextalone.pkginstallerplus.HookEntry.injectModuleResources
import ltd.nextalone.pkginstallerplus.R
import ltd.nextalone.pkginstallerplus.utils.*

/**
 * Хук для Android 15/16+ (Baklava)
 * Использует динамический поиск View вместо жестких ID.
 */
@RequiresApi(35)
object InstallerHookB {
    fun initOnce() {
        // Пробуем найти класс активности. В разных прошивках он может быть в разных пакетах.
        val targetClass = "com.android.packageinstaller.PackageInstallerActivity".clazz 
            ?: "com.google.android.packageinstaller.PackageInstallerActivity".clazz
        
        targetClass?.method("startInstallConfirm")?.hookAfter {
            val ctx: Activity = it.thisObject as Activity
            injectModuleResources(ctx.resources)
            // Запускаем в UI потоке с небольшой задержкой, чтобы View успело создаться
            Thread {
                try { Thread.sleep(100) } catch (e: InterruptedException) {}
                ctx.runOnUiThread {
                    addInstallDetails(ctx)
                }
            }.start()
        }

        // Хук диалога удаления
        "com.android.packageinstaller.UninstallerActivity".clazz?.method("showConfirmationDialog")?.hookBefore {
            val ctx: Activity = it.thisObject as Activity
            injectModuleResources(ctx.resources)
            
            val fragmentClass = "com.android.packageinstaller.handheld.UninstallAlertDialogFragment".clazz
            fragmentClass?.method("onCreateDialog")?.hookAfter { param ->
                val dialog = param.result as? AlertDialog
                if (dialog != null) {
                    addUninstallDetails(ctx, dialog)
                }
            }
        }
    }

    private fun addInstallDetails(activity: Activity) {
        val newPkgInfo: PackageInfo = try {
            activity.get("mPkgInfo") as PackageInfo
        } catch (e: Exception) {
            // Если поле не найдено, выходим без краша
            return
        }

        val usrManager: UserManager = activity.getSystemService(UserManager::class.java)
        val pkgName = newPkgInfo.packageName

        // Получение PackageInfo с поддержкой API 33+ (Tiramisu+) флаги
        val oldPkgInfo = try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.packageManager.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()))
            } else {
                activity.packageManager.getPackageInfo(pkgName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        // Создаем View с информацией
        val infoView = createInfoLayout(activity, newPkgInfo, oldPkgInfo, usrManager, pkgName)

        // --- ЛОГИКА ВСТАВКИ (Эвристический поиск) ---
        
        // 1. Сначала пробуем найти стандартный ScrollView, который используется в диалогах
        val rootView = activity.window.decorView
        val targetContainer = findInjectionContainer(rootView)

        if (targetContainer != null) {
            // Если нашли контейнер, добавляем нашу инфу.
            // Индекс 1 обычно идет после иконки приложения/заголовка.
            // Если элементов мало, добавляем в конец.
            val count = targetContainer.childCount
            val index = if (count > 0) 1 else 0
            val safeIndex = if (index > count) count else index
            
            targetContainer.addView(infoView, safeIndex, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        // 2. Fallback: если ScrollView не найден, пробуем найти по старым ID (на случай, если Google вернет их)
        val legacyView: View? = activity.findHostView("install_confirm_question") ?:
                                activity.get("mDialog")?.findHostView("install_confirm_question") ?:
                                activity.findHostView("install_confirm_question_update")
        
        if (legacyView != null && legacyView.parent is ViewGroup) {
            val parent = legacyView.parent as ViewGroup
            val idx = parent.indexOfChild(legacyView)
            parent.addView(infoView, idx + 1, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /**
     * Рекурсивно ищет LinearLayout внутри ScrollView.
     * Это самая надежная структура диалогов Android.
     */
    private fun findInjectionContainer(view: View): ViewGroup? {
        if (view is ScrollView) {
            if (view.childCount > 0 && view.getChildAt(0) is LinearLayout) {
                return view.getChildAt(0) as LinearLayout
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val res = findInjectionContainer(view.getChildAt(i))
                if (res != null) return res
            }
        }
        return null
    }

    private fun createInfoLayout(activity: Activity, newPkgInfo: PackageInfo, oldPkgInfo: PackageInfo?, usrManager: UserManager, pkgName: String): View {
        val textView = TextView(activity)
        textView.setTextIsSelectable(true)
        textView.typeface = Typeface.MONOSPACE
        
        val padding = activity.dip2px(20f)
        textView.setPadding(padding, 10, padding, 10)

        val layout = LinearLayout(activity)
        layout.orientation = LinearLayout.VERTICAL

        val sb = SpannableStringBuilder()
        
        if (oldPkgInfo == null) {
            // Новая установка
            val newVersionStr = (newPkgInfo.versionName ?: "???") + "(" + newPkgInfo.longVersionCode + ")"
            val newSdkStr = newPkgInfo.applicationInfo!!.targetSdkVersion.toString()

            sb.append(activity.getString(R.string.IPP_info_user) + ": ")
                .append(usrManager.userName)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_package) + ": ")
                .append(pkgName)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_version) + ": ")
                .append(newVersionStr)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_sdk) + ": ")
                .append(newSdkStr)
        } else {
            // Обновление
            val oldVersionStr = """${oldPkgInfo.versionName ?: "???"}(${oldPkgInfo.longVersionCode})"""
            val newVersionStr = """${newPkgInfo.versionName ?: "???"}(${newPkgInfo.longVersionCode})"""
            val oldSdkStr = oldPkgInfo.applicationInfo!!.targetSdkVersion.toString()
            val newSdkStr = newPkgInfo.applicationInfo!!.targetSdkVersion.toString()

            sb.append(activity.getString(R.string.IPP_info_user) + ": ")
                .append(usrManager.userName)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_package) + ": ")
                .append(pkgName)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_version1) + ": ")
                .append(oldVersionStr)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_version2) + ": ")
                .append(newVersionStr, ForegroundColorSpan(ThemeUtil.colorGreen), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_sdk) + ": ")
                .append(oldSdkStr)
                .append(" ➞ ")
                .append(newSdkStr, ForegroundColorSpan(ThemeUtil.colorGreen), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        textView.text = sb
        layout.addView(textView)
        return layout
    }

    private fun addUninstallDetails(activity: Activity, dialog: AlertDialog) {
        val textView = TextView(activity)
        textView.setTextIsSelectable(true)
        textView.typeface = Typeface.MONOSPACE

        val layout = LinearLayout(activity)
        if (activity.taskId == -1) return
        
        // Безопасное получение данных из mDialogInfo
        val dialogInfo = activity.get("mDialogInfo")
        val appInfo = dialogInfo?.get("appInfo")
        val packageName = appInfo?.get("packageName") as? String ?: return

        val oldPkgInfo = try {
             if (Build.VERSION.SDK_INT >= 33) {
                activity.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()))
            } else {
                activity.packageManager.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        if (oldPkgInfo != null) {
            val sb = SpannableStringBuilder()
            val oldVersionStr = (oldPkgInfo.versionName ?: "???") + "(" + oldPkgInfo.longVersionCode + ")"
            val oldSdkStr = oldPkgInfo.applicationInfo!!.targetSdkVersion.toString()

            sb.append(activity.getString(R.string.IPP_info_package) + ": ")
                .append(packageName)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_version) + ": ")
                .append(oldVersionStr)
                .append('\n')
                .append(activity.getString(R.string.IPP_info_sdk) + ": ")
                .append(oldSdkStr)

            val padding = activity.dip2px(24f)
            layout.setPadding(padding, 0, padding, 0)
            textView.text = sb
            layout.addView(textView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            
            // В новых версиях AlertDialog может потребоваться добавить View в контейнер сообщения
            dialog.setView(layout)
        }
    }
}