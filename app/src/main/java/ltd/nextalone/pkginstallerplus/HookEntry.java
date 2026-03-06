package ltd.nextalone.pkginstallerplus;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import ltd.nextalone.pkginstallerplus.hook.InstallerHookB;
import ltd.nextalone.pkginstallerplus.hook.InstallerHookN;
import ltd.nextalone.pkginstallerplus.hook.InstallerHookQ;

import static ltd.nextalone.pkginstallerplus.utils.HookUtilsKt.isV2InstallerAvailable;

public class HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    public static ClassLoader myClassLoader;
    public static ClassLoader lpClassLoader;
    private static boolean sInitialized = false;
    private static String sModulePath = null;
    private static long sResInjectBeginTime = 0;
    private static long sResInjectEndTime = 0;

    private static void initializeHookInternal(LoadPackageParam lpparam) {
        lpClassLoader = lpparam.classLoader;
        if (isV2InstallerAvailable()) {
            try {
                InstallerHookB.INSTANCE.initOnce();
            } catch (Exception e) {
            }
        }
        try {
            if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                InstallerHookQ.INSTANCE.initOnce();
            }
        } catch (Exception e) {
            try {
                InstallerHookN.INSTANCE.initOnce();
            } catch (Exception e1) {
            }
        }
    }

    public static void injectModuleResources(Resources res) {
        if (res == null) {
            return;
        }
        try {
            res.getString(R.string.IPP_res_inject_success);
            return;
        } catch (Resources.NotFoundException ignored) {
        }
        try {
            if (myClassLoader == null) {
                myClassLoader = HookEntry.class.getClassLoader();
            }
            if (sModulePath == null) {
                // should not happen
                throw new IllegalStateException("sModulePath is null");
            }
            if (sResInjectBeginTime == 0) {
                sResInjectBeginTime = System.currentTimeMillis();
            }
            AssetManager assets = res.getAssets();
            @SuppressLint("DiscouragedPrivateApi")
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (int) addAssetPath.invoke(assets, sModulePath);
            try {
                if (sResInjectEndTime == 0) {
                    sResInjectEndTime = System.currentTimeMillis();
                }
            } catch (Resources.NotFoundException e) {
                long length = -1;
                boolean read = false;
                boolean exist = false;
                boolean isDir = false;
                try {
                    File f = new File(sModulePath);
                    exist = f.exists();
                    isDir = f.isDirectory();
                    length = f.length();
                    read = f.canRead();
                } catch (Throwable e2) {
                }
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.google.android.packageinstaller".equals(lpparam.packageName)
            || "com.android.packageinstaller".equals(lpparam.packageName)) {
            if (!sInitialized) {
                sInitialized = true;
                initializeHookInternal(lpparam);
            }
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        String modulePath = startupParam.modulePath;
        assert modulePath != null;
        sModulePath = modulePath;
    }
}
