package com.markq.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.markq.R
import com.markq.core.FileSha256
import java.io.File
import java.util.jar.JarFile

object ApkIntegrity {
    fun verifyOrThrow(context: Context, apk: File, expectedSha256: String?) {
        if (!apk.exists() || apk.length() == 0L) {
            apk.delete()
            error(context.getString(R.string.error_update_empty))
        }
        if (!UpdateChecker.isApkFile(apk)) {
            apk.delete()
            error(context.getString(R.string.error_update_not_apk))
        }
        if (expectedSha256 != null) {
            val actual = FileSha256.of(apk)
            if (!FileSha256.matches(actual, expectedSha256)) {
                apk.delete()
                error(context.getString(R.string.error_update_sha256_mismatch))
            }
        }
        when (signingCheck(context, apk)) {
            CertResult.Match -> Unit
            CertResult.WrongPackage -> {
                apk.delete()
                error(context.getString(R.string.error_update_package_mismatch))
            }
            CertResult.Mismatch -> {
                apk.delete()
                error(context.getString(R.string.error_update_cert_mismatch))
            }
            CertResult.Unverified -> {
                apk.delete()
                error(context.getString(R.string.error_update_cert_unverified))
            }
        }
    }

    internal fun signingCheck(context: Context, apk: File): CertResult {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return CertResult.Unverified
        val archivePkg = archive.packageName
        if (archivePkg.isNullOrBlank() || archivePkg != context.packageName) {
            return CertResult.WrongPackage
        }
        val installed = installedInfo(pm, context.packageName) ?: return CertResult.Unverified
        val apkCerts = certDigests(archive).ifEmpty { certsFromJar(apk) }
        val installedCerts = certDigests(installed)
        if (apkCerts.isEmpty() || installedCerts.isEmpty()) return CertResult.Unverified
        return if (apkCerts == installedCerts) CertResult.Match else CertResult.Mismatch
    }

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, signingFlags())
        }
        info?.applicationInfo?.apply {
            sourceDir = apk.absolutePath
            publicSourceDir = apk.absolutePath
        }
        return info
    }

    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, signingFlags())
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun signingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    private fun certDigests(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.apkContentsSigners ?: signing.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return sigs.orEmpty().map { FileSha256.of(it.toByteArray()) }.toSet()
    }

    private fun certsFromJar(apk: File): Set<String> {
        return try {
            JarFile(apk, true).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    jar.getInputStream(entry).use { input ->
                        val buf = ByteArray(8192)
                        while (input.read(buf) >= 0) {
                            // Drain so the JAR verifies and fills certificates.
                        }
                    }
                    val certs = entry.certificates ?: continue
                    if (certs.isNotEmpty()) {
                        return certs.map { FileSha256.of(it.encoded) }.toSet()
                    }
                }
                emptySet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    enum class CertResult {
        Match,
        Mismatch,
        Unverified,
        WrongPackage,
    }
}
