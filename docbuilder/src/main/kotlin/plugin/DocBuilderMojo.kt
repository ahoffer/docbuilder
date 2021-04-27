/* (C)2021 */
package plugin

import com.connecta.reflex.mqdocs.AsciidoctorFragmentGenerator
import com.ongres.process.FluentProcess
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.relativeTo
import org.apache.commons.io.FileUtils
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.MavenResourcesFiltering
import org.codehaus.plexus.archiver.manager.ArchiverManager
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import plugin.util.MavenFacade
import plugin.util.MavenOp
import plugin.util.findUniqueFile

@Mojo(name = "build")
class DocBuilderMojo : AbstractMojo() {
    lateinit var resourcesDir: File
    lateinit var mavenRoot: Path

    @Component(role = MavenResourcesFiltering::class, hint = "default")
    lateinit var mavenResourcesFiltering: MavenResourcesFiltering

    @Component
    lateinit var archiverManager: ArchiverManager

    @Component
    lateinit var repositorySystem: RepositorySystem

    @Parameter(defaultValue = "\${session}", readonly = true)
    lateinit var mavenSession: MavenSession

    @Parameter(defaultValue = "\${project}", readonly = true)
    lateinit var project: MavenProject

    @Parameter(defaultValue = "\${repositorySystemSession}")
    lateinit var systemSession: RepositorySystemSession

    @Parameter(defaultValue = "\${project.build.directory}")
    lateinit var buildDir: File

    @Parameter(defaultValue = "\${project.basedir}")
    lateinit var baseDir: File

    @Parameter(defaultValue = "\${project.version}")
    lateinit var projectVersion: String

    @Parameter(defaultValue = "\${project.remotePluginRepositories}", readonly = true)
    lateinit var remoteRepos: List<RemoteRepository>
    lateinit var mavenFacade: MavenFacade

    private fun initialize() {
        // This method has to be called from within execute()...
        // ...because the dependencies (RepositorySystem, etc.) are injected after the constructor is
        // called.
        mavenFacade = MavenFacade(
            systemSession,
            remoteRepos,
            repositorySystem,
            archiverManager,
            mavenSession,
            project,
            mavenResourcesFiltering
        )
        resourcesDir = baseDir.resolve("src/main/resources")
        mavenRoot = mavenFacade.rootDir().toPath()
    }

    @ExperimentalPathApi
    override fun execute() {
        initialize()
        setMavenProperties()
        assembleResources()
        renderDocumentation()
        cleanup()
    }

    private fun cleanup() {
        listOf("temp", "work", "build", "public").forEach {
            log.info("Cleaning up \"$it\"")
            try {
                FileUtils.deleteDirectory(File(it))
            } catch (e: Exception) {
                log.warn(String.format("Could not remove \"$it\" folder"))
            } catch (e: IllegalArgumentException) {
                log.warn(String.format("Could not remove \"$it\" folder"))
            }
        }
    }

    fun renderDocumentation() {
        transformBrokerFileToAdocFile()
        log.info("********** BEGIN NPM **********")
        npmRun("basic-install")
        npmRun("gulp")
        npmRun("build")
        log.info("********** END NPM **********")
    }

    fun assembleResources() {

        // Get enums properties file
        val propertiesFile = MavenOp(
            mavenFacade,
            mavenCoords = "com.connexta.reflex:doc-content-generation:properties:enum:$projectVersion"
        ).getFile()

        // Copy and filter the site-contents resources. Use the enum.properties file for filtering.
        MavenOp(
            mavenFacade,
            sourceDir = resourcesDir,
            destDir = (File(buildDir, "doc-contents-$projectVersion")),
            includePatterns = listOf("$SITE_CONTENT/**", "$CONTENT/**"),
            filterFiles = listOf(propertiesFile)
        )
            .run()

        // Copy playbook to docs
        MavenOp(
            mavenFacade,
            sourceDir = File(resourcesDir, "playbooks"),
            destDir = File(buildDir, "docs")
        )
            .run()

        // Copy CSS framework
        MavenOp(
            mavenFacade, sourceDir = (File(resourcesDir, "ui")),
            destDir = (File(baseDir, "temp/src"))
        )
            .run()
    }

    @ExperimentalPathApi
    fun setMavenProperties() {
        val currentYear = LocalDate.now().year
        mavenFacade.defineProperty("current-year", currentYear.toString())
        mavenFacade.defineProperty("sources-url", mavenRoot.absolutePathString())
        val docContents = buildDir.toPath().resolve("doc-contents-$projectVersion")
        val siteContents = docContents.resolve(SITE_CONTENT).relativeTo(mavenRoot)
        val contents = docContents.resolve(CONTENT).relativeTo(mavenRoot)
        mavenFacade.defineProperty(SITE_CONTENT, siteContents.toString())
        mavenFacade.defineProperty(CONTENT, contents.toString())
    }

    fun transformBrokerFileToAdocFile() {
        val brokerFile = findUniqueFile(mavenRoot, "artemis.xml", ignore = "/target")
        val freemarkerTemplate = findUniqueFile(resourcesDir, "topic-table-entries.ftl")
        val output = findUniqueFile(buildDir, "topics-roles-tables.adoc")
        AsciidoctorFragmentGenerator.main(
            arrayOf(
                brokerFile.getAbsolutePath(),
                freemarkerTemplate.getAbsolutePath(),
                output.getAbsolutePath()
            )
        )
    }

    fun npmRun(command: String?): FluentProcess {
        val workPath = project.basedir.toPath()
        val fp = FluentProcess.builder("npm", "run-script", command).workPath(workPath).start()
        fp.stream().forEach(log::info)
        return fp
    }

    companion object {
        const val SITE_CONTENT = "site-content"
        const val CONTENT = "content"
    }
}
