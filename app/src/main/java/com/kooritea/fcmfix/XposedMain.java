package com.kooritea.fcmfix;

import androidx.annotation.NonNull;

import com.kooritea.fcmfix.libxposed.XposedBridge;
import com.kooritea.fcmfix.xposed.AutoStartFix;
import com.kooritea.fcmfix.xposed.BroadcastFix;
import com.kooritea.fcmfix.xposed.KeepNotification;
import com.kooritea.fcmfix.xposed.MiuiLocalNotificationFix;
import com.kooritea.fcmfix.xposed.OplusProxyFix;
import com.kooritea.fcmfix.xposed.PowerkeeperFix;
import com.kooritea.fcmfix.xposed.ReconnectManagerFix;
import com.kooritea.fcmfix.xposed.XposedModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

public class XposedMain extends io.github.libxposed.api.XposedModule {

    private ClassLoader systemServerClassLoader;

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        XposedBridge.init(this);
        XposedModule.setSelfPackageName("android");

        systemServerClassLoader = param.getClassLoader();
        registerSystemServerHooks(systemServerClassLoader);
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        XposedBridge.init(this);

        if ("com.google.android.gms".equals(param.getPackageName()) && param.isFirstPackage()) {
            XposedModule.setSelfPackageName("com.google.android.gms");
            XposedBridge.log("[fcmfix] start hook com.google.android.gms");
            new ReconnectManagerFix(param.getClassLoader());
        }

        if ("com.miui.powerkeeper".equals(param.getPackageName()) && param.isFirstPackage()) {
            XposedModule.setSelfPackageName("com.miui.powerkeeper");
            XposedBridge.log("[fcmfix] start hook com.miui.powerkeeper");
            new PowerkeeperFix(param.getClassLoader());
        }
    }

    @Override
    public boolean onHotReloading(@NonNull XposedModuleInterface.HotReloadingParam param) {
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull XposedModuleInterface.HotReloadedParam param) {
        param.getOldHookHandles().forEach(XposedInterface.HookHandle::unhook);

        XposedBridge.init(this);

        if (param.isSystemServer() && systemServerClassLoader != null) {
            XposedModule.setSelfPackageName("android");
            registerSystemServerHooks(systemServerClassLoader);
        }
    }

    private void registerSystemServerHooks(ClassLoader classLoader) {
        XposedBridge.log("[fcmfix] start hook com.android.server.am.ActivityManagerService/com.android.server.am.BroadcastController");
        new BroadcastFix(classLoader);

        XposedBridge.log("[fcmfix] start hook com.android.server.notification.NotificationManagerServiceInjector");
        new MiuiLocalNotificationFix(classLoader);

        XposedBridge.log("[fcmfix] com.android.server.am.BroadcastQueueInjector.checkApplicationAutoStart");
        new AutoStartFix(classLoader);

        XposedBridge.log("[fcmfix] com.android.server.notification.NotificationManagerService");
        new KeepNotification(classLoader);

        XposedBridge.log("[fcmfix] start hook com.android.server.power.OplusProxyWakeLock");
        new OplusProxyFix(classLoader);
    }
}
