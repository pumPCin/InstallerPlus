package ltd.nextalone.pkginstallerplus.hook

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.UserManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi

import ltd.nextalone.pkginstallerplus.HookEntry.injectModuleResources
import ltd.nextalone.pkginstallerplus.R
import ltd.nextalone.pkginstallerplus.utils.*

@RequiresApi(29)
object InstallerHookQ {
    fun initOnce() {
        "com.android.packageinstaller.PackageInstallerActivity".clazz?.method("startInstallConfirm")?.hookAfter {
            val ctx: Activity = it.thisObject as Activity
            injectModuleResources(ctx.resources)
            Thread {
                Thread.sleep(100)
                ctx.runOnUiThread {
                    addInstallDetails(ctx)
                }
            }.start()
        }

        "com.android.packageinstaller.UninstallerActivity".clazz?.method("showConfirmationDialog")?.hookBefore {
            val ctx: Activity = it.thisObject as Activity
            injectModuleResources(ctx.resources)
            "com.android.packageinstaller.handheld.UninstallAlertDialogFragment".clazz?.method("onCreateDialog")?.hookAfter { it2 ->
                val dialog = it2.result as AlertDialog
                addUninstallDetails(ctx, dialog)
            }
        }
    }

    private fun addInstallDetails(activity: Activity) {
        val textView = TextView(activity)
        textView.setTextIsSelectable(true)
        textView.typeface = Typeface.MONOSPACE

        val layout = LinearLayout(activity)
        val newPkgInfo: PackageInfo = activity.get("mPkgInfo") as PackageInfo
        val usrManager: UserManager = activity.get("mUserManager") as UserManager
        val pkgName = newPkgInfo.packageName
        val oldPkgInfo = try {
            activity.packageManager.getPackageInfo(pkgName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        val sb = SpannableStringBuilder()
        if (oldPkgInfo == null) {
            val install: View? = activity.findHostView("install_confirm_question") ?:
            activity.get("mDialog")?.findHostView("install_confirm_question") // QPR2+
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

            if (install != null) {
                layout.setPadding(0, install.height, 0, 0)
                textView.text = sb
                layout.addView(textView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                (install.parent as ViewGroup).addView(layout)
            }
        } else {
            val update: View? = activity.findHostView("install_confirm_question_update") ?:
            activity.get("mDialog")?.findHostView("install_confirm_question_update") // QPR2+
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

            if (update != null) {
                layout.setPadding(0, update.height, 0, 0)
                textView.text = sb
                layout.addView(textView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                (update.parent as ViewGroup).addView(layout)
            }
        }
    }

    private fun addUninstallDetails(activity: Activity, dialog: AlertDialog) {
        val textView = TextView(activity)
        textView.setTextIsSelectable(true)
        textView.typeface = Typeface.MONOSPACE

        val layout = LinearLayout(activity)
        if (activity.taskId == -1) return
        val packageName = activity.get("mDialogInfo")?.get("appInfo")?.get("packageName") as String
        val oldPkgInfo = try {
            activity.packageManager.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        val sb = SpannableStringBuilder()
        if (oldPkgInfo != null) {
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

            layout.setPadding(activity.dip2px(24f), 0, activity.dip2px(24f), 0)
            textView.text = sb
            layout.addView(textView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            dialog.setView(layout)
        }
    }
}