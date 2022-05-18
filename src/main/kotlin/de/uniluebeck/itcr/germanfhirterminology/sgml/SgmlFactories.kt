package de.uniluebeck.itcr.germanfhirterminology.sgml

import de.gecko.medicats.*
import de.gecko.medicats.icd10.IcdNode
import de.gecko.medicats.icd10.IcdNodeWalker
import de.gecko.medicats.icd10.sgml.AbstractSgmlIcdNodeFactory
import de.gecko.medicats.icd10.sgml.AbstractSgmlIcdNodeWalker
import de.gecko.medicats.icd10.sgml.SgmlChapterReader
import de.gecko.medicats.ops.OpsNode
import de.gecko.medicats.ops.OpsNodeWalker
import de.gecko.medicats.ops.sgml.AbstractSgmlOpsNodeFactory
import de.gecko.medicats.ops.sgml.AbstractSgmlOpsNodeWalker
import de.uniluebeck.itcr.germanfhirterminology.ResourceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.io.path.name

private val logger: Logger = LoggerFactory.getLogger("SgmlFactory")

class NonValidatingZipSource(file: File) : ZipSource(file.parentFile.toPath(), file.name, 0L) {
    override fun testChecksum() {
        // left blank to disable checksum, it's annoying.
    }
}

interface MyNodeFactory<Node : VersionedNode<Node>?, Walker : NodeWalker<Node>?> : VersionedNodeFactory<Node, Walker> {
    val resourceType: ResourceType
    val theVersion: String
    override fun getName(): String = when (resourceType) {
        ResourceType.OPS -> if (version.matches(Regex("\\d*4"))) "OPS $version" else "OPS-301 Version $version"
        ResourceType.ICD10GM -> if (version.matches(Regex("\\d*4"))) "ICD-10-GM Version $version" else "ICD-10 SGB V Version $version"
        else -> throw UnsupportedOperationException("Can't create SGML factory for $resourceType")
    }

    override fun getOid(): String? = null // we (probably) don't need this

    override fun getVersion(): String = theVersion

    override fun getSortIndex(): Int = 42 // not relevant?!

    override fun supportsVersion(p0: String?): Boolean = true // don't care

    override fun getPreviousVersion(): String? = null // not supported
}


interface ZipFileChapterConsumer {
    val inputFilename: File
    val zip: NonValidatingZipSource

    //get() = NonValidatingZipSource(inputFilename)
    val zipFileSystem: FileSystem
    //get() = FileSystems.newFileSystem(inputFilename.toPath())

    fun getFilesByName(pattern: String) =
        Files.find(zipFileSystem.getPath("/"), 999, { p, _ -> p.name.lowercase().matches(Regex(pattern)) })
            .collect(Collectors.toList()).sorted().map { it to FileSource(zip, it.parent.toString(), it.name) }
}

class SgmlIcdNodeFactory(
    override val inputFilename: File, override val theVersion: String,
    override val zip: NonValidatingZipSource = NonValidatingZipSource(inputFilename),
    override val zipFileSystem: FileSystem = FileSystems.newFileSystem(inputFilename.toPath())
) : AbstractSgmlIcdNodeFactory(), MyNodeFactory<IcdNode, IcdNodeWalker>, ZipFileChapterConsumer {
    override fun createNodeWalker(): IcdNodeWalker = object : AbstractSgmlIcdNodeWalker(rootNode) {
        //default implementation
    }

    private val filterOutPrefixes = listOf("x1ses")

    private val myChapterFiles = getFilesByName("kap\\d\\d.sgm").filter { (path, _) -> filterOutPrefixes.none { path.toString().contains(it) } }
    override val resourceType: ResourceType
        get() = ResourceType.ICD10GM

    override fun supportsVersion(p0: String?): Boolean = true

    override fun getSystFile(): FileSource? = null

    override fun getTransitionFile(): FileSource? = null

    override fun getChapterCount(): Int = myChapterFiles.count()

    override fun getChapterFiles(): Stream<FileSource> = myChapterFiles
        .map { it.second }.stream()

    override fun getChapter(chapter: Int): Element {
        require(chapter in 1..chapterCount) { "The chapter count is out of bounds" }
        val desiredChapter = "kap${chapter.toString().padStart(2, '0')}.sgm"
        val (path, fileSource) = myChapterFiles.find { it.first.name.lowercase() == desiredChapter }
            ?: throw UnsupportedOperationException("The ZIP file does not contain a chapter '$desiredChapter'")
        logger.info("Reading chapter $chapter from $path")
        return SgmlChapterReader.read(fileSource.inputStream)
    }
}

class OpsSgmlNodeFactory(
    override val inputFilename: File, override val theVersion: String,
    override val zip: NonValidatingZipSource = NonValidatingZipSource(inputFilename),
    override val zipFileSystem: FileSystem = FileSystems.newFileSystem(inputFilename.toPath())
) : AbstractSgmlOpsNodeFactory(), MyNodeFactory<OpsNode, OpsNodeWalker>, ZipFileChapterConsumer {
    override fun createNodeWalker(): OpsNodeWalker = object : AbstractSgmlOpsNodeWalker(rootNode) {
        //default implementation
    }

    override val resourceType: ResourceType
        get() = ResourceType.OPS

    override fun supportsVersion(p0: String?): Boolean = true

    override fun getSystFile(): FileSource? = null

    override fun getTransitionFile(): FileSource? = null
    override fun getSgml(): FileSource = getFilesByName("OP301.sgm").first().let { (_, fs) -> fs }
}