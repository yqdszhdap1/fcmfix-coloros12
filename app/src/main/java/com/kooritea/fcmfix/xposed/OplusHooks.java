package com.kooritea.fcmfix.xposed;

import android.content.Intent;
import android.content.pm.PackageManager;

import com.kooritea.fcmfix.libxposed.XposedHelpers;
import com.kooritea.fcmfix.util.HookStatus;

import java.lang.reflect.Method;

/** Reflection helpers shared by the ColorOS system_server hooks. */
final class OplusHooks {
    /** Hook groups installed in this process; reported to the module app on request. */
    static final HookStatus STATUS = new HookStatus();

    private OplusHooks() {
    }

    interface HookAction {
        void run() throws Throwable;
    }

    /** Only for a positively identified firmware variant without the relevant restriction. */
    static final class NotApplicable extends Exception {
        NotApplicable(String reason) {
            super(reason);
        }
    }

    /**
     * Installs one hook group; a firmware mismatch is logged and leaves the system behavior.
     * A group must throw when nothing hooked. A known absent feature uses NotApplicable;
     * unknown classes/signatures and installation errors remain failures.
     */
    static void runHook(String name, HookAction action) {
        try {
            action.run();
            STATUS.recordActive(name);
        } catch (NotApplicable e) {
            STATUS.recordSkipped(name, e.getMessage());
            XposedModule.printLog("hook not applicable " + name + ": " + e.getMessage());
        } catch (Throwable e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            STATUS.recordFailed(name, reason);
            XposedModule.printLog("hook error " + name + ": " + reason);
        }
    }

    /** uid of the package in the primary user only; use the user-aware overload for FCM targets. */
    static int getTargetUidFromPackageName(String packageName) {
        return getTargetUidFromPackageName(packageName, 0);
    }

    /**
     * Resolve the UID a package runs as inside one user. ColorOS MultiApp installs the same
     * package in a clone user (999), so a package name alone is ambiguous: resolving it against
     * user 0 hands the FCM delivery window and the Hans unfreeze to the owner profile while the
     * broadcast is being delivered to the clone, which then stays frozen and drops the push.
     */
    static int getTargetUidFromPackageName(String packageName, int userId) {
        if (packageName == null || XposedModule.context == null) return -1;
        PackageManager pm = XposedModule.context.getPackageManager();
        try {
            int uid = pm.getPackageUidAsUser(packageName, userId);
            if (userId != 0) {
                XposedModule.printLog("uid resolved for user " + userId + ": " + packageName + "=" + uid);
            }
            return uid;
        } catch (Throwable e) {
            if (userId != 0) {
                XposedModule.printLog("error: Package not found: " + packageName
                        + " (user " + userId + "): " + e);
            }
        }
        // Fall back to the primary user so a single-install target keeps working.
        try {
            return pm.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            XposedModule.printLog("error: Package not found: " + packageName);
        } catch (Throwable e) {
            XposedModule.printLog("error: uid lookup failed: " + packageName + ": " + e);
        }
        return -1;
    }

    /**
     * Every UID the package is installed under, primary user first. The FCM delivery window is
     * keyed by UID, so covering all installs keeps the clone working without having to recover
     * the broadcast's target user from the framework signature.
     */
    static int[] getTargetUidsFromPackageName(String packageName) {
        if (packageName == null || XposedModule.context == null) return new int[0];
        PackageManager pm = XposedModule.context.getPackageManager();
        java.util.LinkedHashSet<Integer> uids = new java.util.LinkedHashSet<>();
        try {
            int primary = pm.getPackageUid(packageName, 0);
            uids.add(primary);
        } catch (Throwable ignored) {
            // Not installed for user 0; other users are still worth checking.
        }
        try {
            for (android.content.pm.UserInfo user : ((android.os.UserManager)
                    XposedModule.context.getSystemService(android.content.Context.USER_SERVICE))
                    .getUsers()) {
                if (user.id == 0) continue;
                try {
                    uids.add(pm.getPackageUidAsUser(packageName, user.id));
                } catch (Throwable ignored) {
                    // Not installed for this user.
                }
            }
        } catch (Throwable e) {
            XposedModule.printLog("error: user enumeration failed for " + packageName + ": " + e);
        }
        int[] result = new int[uids.size()];
        int i = 0;
        for (int uid : uids) result[i++] = uid;
        return result;
    }

    static String getIntentTarget(Intent intent) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getPackageName();
        }
        return intent.getPackage();
    }

    static boolean isBooleanType(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    static Intent findIntentArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Intent) {
                return (Intent) arg;
            }
        }
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                Object nestedIntent = XposedHelpers.getObjectField(arg, "intent");
                if (nestedIntent instanceof Intent) {
                    return (Intent) nestedIntent;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static String describeMethod(Method method) {
        StringBuilder result = new StringBuilder(method.getDeclaringClass().getName())
                .append('#').append(method.getName()).append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) result.append(',');
            result.append(types[i].getSimpleName());
        }
        return result.append("): ").append(method.getReturnType().getSimpleName()).toString();
    }
}
