package com.MiniVolume;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Acces Shizuku par reflexion : le projet compile meme sans les jars Shizuku.
 * Les fonctions reboot ne marchent qu'une fois les jars ajoutes dans libs/
 * et le bloc ShizukuProvider decommente dans le manifest.
 */
public class ShizukuHelper {

    private static Class<?> cls() {
        try {
            return Class.forName("rikka.shizuku.Shizuku");
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean libPresent() {
        return cls() != null;
    }

    public static boolean binderAlive() {
        try {
            Class<?> c = cls();
            if (c == null) return false;
            return (Boolean) c.getMethod("pingBinder").invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            Class<?> c = cls();
            if (c == null) return false;
            int r = (Integer) c.getMethod("checkSelfPermission").invoke(null);
            return r == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            Class<?> c = cls();
            if (c == null) return;
            c.getMethod("requestPermission", int.class).invoke(null, 1001);
        } catch (Throwable t) {
            // ignore
        }
    }

    public static int getUid() {
        try {
            Class<?> c = cls();
            if (c == null) return -1;
            return (Integer) c.getMethod("getUid").invoke(null);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static String status() {
        if (!libPresent()) return "lib absente (voir README)";
        if (!binderAlive()) return "Shizuku non démarré";
        if (!hasPermission()) return "autorisation manquante";
        return "prêt (" + (getUid() == 0 ? "root" : "ADB") + ")";
    }

    /** Execute une commande avec les droits Shizuku. Retourne null si OK. */
    private static String exec(String command) {
        try {
            Class<?> c = cls();
            Method m = c.getDeclaredMethod("newProcess",
                    String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null,
                    new Object[]{new String[]{"sh", "-c", command}, null, null});
            StringBuilder out = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) out.append(line).append('\n');
            int code = p.waitFor();
            if (code == 0) return null;
            return "Échec (code " + code + ") " + out.toString().trim();
        } catch (Throwable t) {
            Throwable r = t.getCause() != null ? t.getCause() : t;
            return "Erreur : " + r.getClass().getSimpleName() + " " + r.getMessage();
        }
    }

    /** action : reboot | soft | recovery | download. Retourne un message ou null. */
    public static String perform(String action) {
        if (!libPresent()) return "Lib Shizuku absente : voir README";
        if (!binderAlive()) return "Shizuku n'est pas démarré";
        if (!hasPermission()) {
            requestPermission();
            return "Autorise Mini Volume dans Shizuku puis réessaie";
        }
        String cmd;
        if ("reboot".equals(action)) {
            cmd = "reboot";
        } else if ("recovery".equals(action)) {
            cmd = "reboot recovery";
        } else if ("download".equals(action)) {
            cmd = "reboot download";
        } else if ("soft".equals(action)) {
            if (getUid() != 0) {
                return "Le soft reboot demande Shizuku en mode root (refusé en mode ADB)";
            }
            cmd = "setprop ctl.restart zygote";
        } else {
            return "Action inconnue";
        }
        return exec(cmd);
    }
}
