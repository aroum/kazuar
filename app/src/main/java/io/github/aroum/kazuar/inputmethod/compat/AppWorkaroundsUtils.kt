package io.github.aroum.kazuar.inputmethod.compat

import android.content.pm.PackageInfo

/**
 * A class to encapsulate work-arounds specific to particular apps.
 */
class AppWorkaroundsUtils(private val mPackageInfo: PackageInfo?) {
    override fun toString(): String {
        val applicationInfo = mPackageInfo?.applicationInfo ?: return ""
        val s = StringBuilder()
        s.append("Target application : ")
                .append(applicationInfo.name)
                .append("\nPackage : ")
                .append(applicationInfo.packageName)
                .append("\nTarget app sdk version : ")
                .append(applicationInfo.targetSdkVersion)
        return s.toString()
    }

}