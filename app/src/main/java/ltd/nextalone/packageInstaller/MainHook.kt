package ltd.nextalone.packageInstaller

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {
    private val name = "carlos"

    fun printlnName() {
        Log.d(TAG, "printlnName: $name")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d(TAG, "handleLoadPackage: ${lpparam.packageName}")
        if (lpparam.packageName != "ltd.nextalone.packageInstaller") return
        hookPackageInstaller(lpparam)
    }

    private fun hookPackageInstaller(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d(TAG, "hookPackageInstaller: ")
        val hookClass =
            lpparam.classLoader.loadClass("ltd.nextalone.packageInstaller.MainHook") ?: return
        // 通过XposedHelpers调用静态方法printlnHelloWorld
        XposedHelpers.callStaticMethod(hookClass, "printlnHelloWorld")
        val demoClass = hookClass.newInstance()
        val field = hookClass.getDeclaredField("name")
        field.isAccessible = true
        field.set(demoClass, "xbd")
        XposedHelpers.callMethod(hookClass.newInstance(), "printlnName")
    }

    companion object {
        const val TAG: String = "NextAlone"
    }
}
