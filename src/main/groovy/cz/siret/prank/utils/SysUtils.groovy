package cz.siret.prank.utils

import groovy.transform.CompileStatic

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.lang.management.ManagementFactory
import java.lang.management.OperatingSystemMXBean

/**
 *
 */
@CompileStatic
class SysUtils {

    static boolean isInstanceOfAny(@Nullable Object obj, @Nonnull List<Class> classes) {
        return classes.contains(obj?.class)
    }

    static String getJavaRuntimeNameVersionVendor() {
        return "${System.getProperty('java.vm.name')} ${System.getProperty('java.vm.version')} ${System.getProperty('java.vm.vendor')}"
    }

    static String getAvailableProcessors() {
        Runtime.getRuntime().availableProcessors()
    }


    static String getOsInfo() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean()

        String osName = osBean.getName();
        String osVersion = osBean.getVersion();
        String arch = osBean.getArch();

        return String.format("%s (Version: %s, Arch: %s)", osName, osVersion, arch)
    }

    static long getMaxMemoryGB() {
        Runtime.getRuntime().maxMemory() / (1024L*1024L*1024L) as long
    }


}
