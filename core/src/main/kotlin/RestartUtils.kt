
import net.fabricmc.loader.api.FabricLoader
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

object RestartUtils {
    var runtimeMxBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean()

    fun generateUnixRestartCommand(): List<String> {
        val jre = System.getProperty("java.home") + "/bin/java"
        val cp = runtimeMxBean.classPath
        val mainClass = runtimeMxBean.systemProperties["sun.java.command"]
        val arguments = runtimeMxBean.inputArguments.toMutableList()

        require(!mainClass.isNullOrEmpty())
        require(cp.isNotEmpty())

        val isDev = FabricLoader.getInstance().isDevelopmentEnvironment

        if (isDev) {
            arguments.removeAll {
                it.startsWith("-agentlib:") ||
                it.startsWith("-javaagent:")
            }
        }

        return buildList {
            add(jre)
            add("-cp")
            add(cp)
            addAll(arguments)
            addAll(mainClass.split(" "))
        }
    }

    fun generateWindowsRestartCommand(): List<String> {
        val jre = System.getProperty("java.home") + "\\bin\\java.exe"
        val cp = runtimeMxBean.classPath
        val mainClass = runtimeMxBean.systemProperties["sun.java.command"]
        val arguments = runtimeMxBean.inputArguments.toMutableList()

        require(!mainClass.isNullOrEmpty())
        require(cp.isNotEmpty())

        val isDev = FabricLoader.getInstance().isDevelopmentEnvironment

        if (isDev) {
            arguments.removeAll {
                it.startsWith("-agentlib:") ||
                it.startsWith("-javaagent:")
            }
        }

        return buildList {
            add(jre)
            add("-cp")
            add(cp)
            addAll(arguments)
            addAll(mainClass.split(" "))
        }
    }
}
