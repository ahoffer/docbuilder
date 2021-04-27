/* (C)2021 */
package plugin.util

import java.io.File
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.codehaus.plexus.archiver.UnArchiver
import org.codehaus.plexus.components.io.filemappers.FileMapper
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector
import org.eclipse.aether.artifact.Artifact

class MavenOp(
    val mavenFacade: MavenFacade,
    val filterFiles: List<File> = emptyList(),
    val includePatterns: List<String> = emptyList(),
    val sourceDir: File? = null,
    val destDir: File? = null,
    val mavenCoords: String? = null,
    val fileMapper: FileMapper? = null
) {

    fun getFile() = mavenCoords?.let { mavenFacade.resolveArtifact(it).file }!!

    fun run() {
        destDir?.mkdir() ?: throw IllegalArgumentException("MavenOp error. Destination directory must be set.")
        val request = if (mavenCoords != null) {
            if (isCompressed)
                compressedArtifactRequest()
            else singleFileRequest()
        } else {
            requireNotNull(sourceDir)
            mavenFacade.createFilterRequest(includePatterns, sourceDir, destDir, filterFiles)
        }
        mavenFacade.executeFilterRequest(request)
    }

    private val artifact: Artifact
        get() {
            return mavenCoords?.let { mavenFacade.resolveArtifact(it) }
                ?: throw IllegalArgumentException("MavenOp says Maven Artifact Coordinates are missing.")
        }

    private val unarchiver: UnArchiver?
        get() {
            return mavenFacade.getUnarchiver(artifact.file)
        }

    private val isCompressed: Boolean
        get() {
            // A null unArchivers means the artifact is not compressed
            return unarchiver != null
        }

    private fun singleFileRequest() = mavenFacade.createFilterRequest(
        // Filter and move files
        listOf(artifact.file.name),
        artifact.file.parentFile,
        destDir!!,
        filterFiles
    )

    private fun compressedArtifactRequest(): MavenResourcesExecution {
        unarchiver?.apply {
            sourceFile = artifact.getFile()
            destDirectory = destDir
            fileSelectors = arrayOf(
                IncludeExcludeFileSelector().also {
                    it.includes = includePatterns.toTypedArray()
                }
            )
            fileMappers = (listOfNotNull(fileMapper)).toTypedArray()
            extract()
        }
        // Filter in place
        return mavenFacade.createFilterRequest(includePatterns, destDir!!, destDir, filterFiles)
    }
}
