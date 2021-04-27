/* (C)2021 */
package plugin.util

import java.io.File
import java.nio.file.Path

fun findUniqueFile(root: Path, name: String, ignore: String?): File = findUniqueFile(root.toFile(), name, ignore)

fun findUniqueFile(root: File, name: String, ignore: String? = null): File {

    var files = root.walk().filter { it.name == name }
        .filterNot {
            if (ignore == null)
                false
            else
                it.absolutePath.contains(ignore)
        }.toList()

    return when {
        files.isEmpty() ->
            throw IllegalArgumentException("Docbuild plugin error. Cannot find file \"$name\" under path \"$root\"")
        files.size > 1 ->
            throw IllegalArgumentException(
                makeMsg(name, files)
            )
        else -> files.first()
    }
}

private fun makeMsg(name: String, files: List<File>) =
    "Docbuild plugin error. Found more than one file named \"$name\":\n ${
    files.joinToString(
        System.lineSeparator(),
        "\t"
    )
    }"
