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
        int uid = packageUidAsUser(packageName, userId);
        if (uid >= 0) {
            if (userId != 0) {
                XposedModule.printLog("uid resolved for user " + userId + ": " + packageName + "=" + uid);
            }
            return uid;
        }
        if (userId != 0) {
            XposedModule.printLog("error: Package not found: " + packageName + " (user " + userId + ")");
        }
        // Fall back to the primary user so a single-install target keeps working.
        try {
            return XposedModule.context.getPackageManager().getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            XposedModule.printLog("error: Package not found: " + packageName);
        } catch (Throwable e) {
            XposedModule.printLog("error: uid lookup failed: " + packageName + ": " + e);
        }
        return -1;
    }

    /**
     * PackageManager#getPackageUidAsUser is a hidden API, so it is resolved reflectively.
     * Returns -1 when the package is not installed for that user.
     */
    private static int packageUidAsUser(String packageName, int userId) {
        if (XposedModule.context == null) return -1;
        try {
            PackageManager pm = XposedModule.context.getPackageManager();
            Method method = PackageManager.class.getMethod("getPackageUidAsUser", String.class, int.class);
            Object result = method.invoke(pm, packageName, userId);
            return result instanceof Integer ? (Integer) result : -1;
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * Every UID the package is installed under, primary user first. The FCM delivery window is
     * keyed by UID, so covering all installs keeps the clone working without having to recover
     * the broadcast's target user from the framework signature.
     */
    static int[] getTargetUidsFromPackageName(String packageName) {
        if (packageName == null || XposedModule.context == null) return new int[0];
        java.util.LinkedHashSet<Integer> uids = new java.util.LinkedHashSet<>();
        // Primary user first so single-install targets keep their previous behaviour.
        int primary = packageUidAsUser(packageName, 0);
        if (primary >= 0) uids.add(primary);
        try {
            int[] userIds = userIds();
            for (int userId : userIds) {
                if (userId == 0) continue;
                int uid = packageUidAsUser(packageName, userId);
                if (uid >= 0) uids.add(uid);
            }
        } catch (Throwable e) {
            XposedModule.printLog("error: user enumeration failed for " + packageName + ": " + e);
        }
        if (uids.isEmpty()) {
            try {
                uids.add(XposedModule.context.getPackageManager().getPackageUid(packageName, 0));
            } catch (Throwable ignored) {
                return new int[0];
            }
        }
        int[] result = new int[uids.size()];
        int i = 0;
        for (int uid : uids) result[i++] = uid;
        return result;
    }

    /** UserManager#getUsers is a hidden API on some ColorOS builds; resolve it reflectively. */
    private static int[] userIds() {
        Object userManager = XposedModule.context.getSystemService("user");
        if (userManager == null) throw new IllegalStateException("no UserManager");
        Method getUsers = userManager.getClass().getMethod("getUsers");
        Object users = getUsers.invoke(userManager);
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (users instanceof Iterable) {
            for (Object user : (Iterable<?>) users) {
                Integer id = userIdOf(user);
                if (id != null && !ids.contains(id)) ids.add(id);
            }
        }
        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) result[i] = ids.get(i);
        return result;
    }

    private static Integer userIdOf(Object userInfo) {
        try {
            Object id = userInfo.getClass().getField("id").get(userInfo);
            if (id instanceof Integer) return (Integer) id;
        } catch (Throwable ignored) {
            // Fall through to the accessor.
        }
        try {
            Object handle = userInfo.getClass().getMethod("getUserHandle").invoke(userInfo);
            if (handle instanceof android.os.UserHandle) {
                return ((android.os.UserHandle) handle).getIdentifier();
            }
        } catch (Throwable ignored) {
            // Unknown user shape; skip it.
        }
        return null;
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
