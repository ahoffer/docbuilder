/* (C)2021 */
package plugin.util

import java.io.File
import java.util.function.Consumer
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.apache.maven.shared.filtering.MavenResourcesFiltering
import org.codehaus.plexus.archiver.UnArchiver
import org.codehaus.plexus.archiver.manager.ArchiverManager
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest

class MavenFacade(
    val systemSession: RepositorySystemSession,
    val remoteRepos: List<RemoteRepository>,
    val system: RepositorySystem,
    val archiverManager: ArchiverManager,
    val session: MavenSession,
    val project: MavenProject,
    val mavenResourcesFiltering: MavenResourcesFiltering
) {

    /**
     *  Coords format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version> </version></classifier></extension></artifactId></groupId>
     *
     */
    fun resolveArtifact(coords: String): Artifact {
        val request = ArtifactRequest().apply {
            setRepositories(remoteRepos)
            setArtifact(DefaultArtifact(coords))
        }
        return system.resolveArtifact(systemSession, request).artifact
    }

    /**
     * If the artifact is compressed, return the object to decompress it.
     * If the artifact is a single (uncompressed) file, return null.
     */
    fun getUnarchiver(file: File): UnArchiver? {
        return try {
            archiverManager.getUnArchiver(file)
        } catch (e: NoSuchArchiverException) {
            null
        }
    }

    fun createFilterRequest(
        patterns: List<String>,
        sourceDir: File,
        destDir: File,
        filterFiles: List<File>
    ): MavenResourcesExecution {

        // Create the resource object
        val r = Resource().apply {
            filtering = "true"
            directory = sourceDir.absolutePath
            with(patterns) { forEach(Consumer { pattern -> addInclude(pattern) }) }
        }

        // Create request
        return MavenResourcesExecution(
            listOf(r),
            destDir,
            project,
            "UTF-8",
            filterFiles.map { it.absolutePath },
            emptyList(),
            session
        ).apply { isOverwrite = true }
    }

    fun executeFilterRequest(executable: MavenResourcesExecution) = mavenResourcesFiltering.filterResources(executable)

    fun rootProject(node: MavenProject = this.project): MavenProject =
        when (node.parent) {
            null -> node
            else -> rootProject(node.parent)
        }

    fun rootDir(): File = rootProject().file.parentFile

    fun defineProperty(name: String, value: String) {
        project.properties[name] = value
    }
}
