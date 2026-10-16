import org.gradle.api.Project
import java.io.File
import java.util.Properties

/** Shared version + signing helpers for the :app and :wear modules (which must match exactly). */
object ScoreUpBuild {
    fun versionCode(project: Project): Int = 1000 + commitCount(project)

    fun versionName(project: Project): String {
        val props = Properties().apply { File(project.rootDir, "version.properties").inputStream().use { load(it) } }
        return "${props.getProperty("VERSION_MAJOR_MINOR")}.${commitCount(project)}"
    }

    private fun commitCount(project: Project): Int {
        System.getenv("SCOREUP_COMMIT_COUNT")?.toIntOrNull()?.let { return it }
        return runCatching {
            val p = ProcessBuilder("git", "rev-list", "--count", "HEAD").directory(project.rootDir)
                .redirectErrorStream(true).start()
            p.inputStream.bufferedReader().readText().trim().toInt()
        }.getOrDefault(1)
    }

    /** Release key from env (CI secrets) or the committed development key. */
    data class Signing(val file: File, val storePassword: String, val alias: String, val keyPassword: String)

    fun signing(project: Project): Signing {
        val envPath = System.getenv("SCOREUP_KEYSTORE_PATH")
        return if (!envPath.isNullOrBlank()) Signing(
            File(envPath), System.getenv("SCOREUP_KEYSTORE_PASSWORD"),
            System.getenv("SCOREUP_KEY_ALIAS"), System.getenv("SCOREUP_KEY_PASSWORD"),
        ) else Signing(File(project.rootDir, "keystore/scoreup-dev.jks"), "scoreup-dev", "scoreup", "scoreup-dev")
    }
}
