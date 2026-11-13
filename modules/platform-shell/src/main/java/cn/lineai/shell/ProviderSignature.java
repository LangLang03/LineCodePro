package cn.lineai.shell;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Compares the signing certificates of an extension package with this application.
 *
 * <p>LineCode and its extension packages are normally released with the same certificate. A
 * locally built or third-party provider usually has a different one and is only loaded when the
 * user opts in through the security setting.</p>
 */
final class ProviderSignature {

    private ProviderSignature() {
    }

    static boolean sameSignature(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            Set<String> own = digests(context, context.getPackageName());
            Set<String> other = digests(context, packageName);
            for (String digest : own) {
                if (other.contains(digest)) {
                    return true;
                }
            }
        } catch (Exception error) {
            // Package removed, signature unavailable or platform API mismatch.
            return false;
        }
        return false;
    }

    private static Set<String> digests(Context context, String packageName) throws Exception {
        PackageManager manager = context.getPackageManager();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = manager.getPackageInfo(packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signing = info.signingInfo;
            if (signing == null) {
                return new HashSet<>();
            }
            signatures = signing.hasMultipleSigners()
                    ? signing.getApkContentsSigners()
                    : signing.getSigningCertificateHistory();
        } else {
            PackageInfo info = manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures == null) {
            return result;
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            result.add(hex(digest.digest(signature.toByteArray())));
        }
        return result;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }
}
