package de.uniluebeck.itcr.germanfhirterminology.sgml

import de.gecko.medicats.FileSource
import de.gecko.medicats.ZipSource
import de.gecko.medicats.icd10.IcdNodeWalker
import de.gecko.medicats.icd10.sgml.AbstractSgmlIcdNodeFactory
import de.gecko.medicats.icd10.sgml.AbstractSgmlIcdNodeWalker
import de.gecko.medicats.icd10.sgml.SgmlChapterReader
import de.uniluebeck.itcr.germanfhirterminology.ResourceType
import de.uniluebeck.itcr.germanfhirterminology.TerminologyConversionService
import org.hl7.fhir.r4.model.CodeSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Element
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val logger : Logger = LoggerFactory.getLogger(SgmlService::class.java)

@Service
class SgmlService : TerminologyConversionService {

    fun buildIcdNodeFactory(inputFilename: File, version: String): AbstractSgmlIcdNodeFactory {
        val zipVersion = version.replace(".", "_")
        val zipFileSystem = FileSystems.newFileSystem(inputFilename.toPath())
        return object : AbstractSgmlIcdNodeFactory() {

            private val zip = NonValidatingZipSource(inputFilename)

            override fun getName(): String = when (version.trim()) {
                in listOf("1.1", "1.2", "1.3", "2.0") -> "ICD-10 SGB V Version $version"
                else -> "ICD-10-GM Version $version"
            }

            override fun getOid(): String? = null // we don't need this

            override fun createNodeWalker(): IcdNodeWalker = object : AbstractSgmlIcdNodeWalker(rootNode) {
                //default implementation
            }

            override fun getVersion(): String = version // use as is

            override fun getSortIndex(): Int = 42 // not relevant?!

            override fun getPreviousVersion(): String? = null // don't care

            override fun getSystFile(): FileSource? {
                return null
                //TODO("Not yet implemented")
            }

            override fun getTransitionFile(): FileSource? = null // not needed

            override fun getChapterCount(): Int = chapterFiles.count().toInt()

            private fun chapterFileList() : List<Pair<Path, FileSource>> {
                val directory = listOf("x1ses$zipVersion", "x1ges$zipVersion").map {
                    zipFileSystem.getPath(it)
                }.firstOrNull { it.exists() }
                return directory?.listDirectoryEntries("KAP[0-9][0-9].sgm")?.sorted()?.map {
                   it to FileSource(zip, directory.name, it.name)
                } ?: emptyList()
            }

            override fun getChapterFiles(): Stream<FileSource> = chapterFileList().map { it.second }.stream()

            override fun getChapter(chapter: Int): Element {
                require(chapter in 1..chapterCount) { "The chapter count is out of bounds" }
                val desiredChapter = "kap${chapter.toString().padStart(2, '0')}.sgm"
                val (path, fileSource) = chapterFileList().find { it.first.name.lowercase() == desiredChapter } ?: throw UnsupportedOperationException("The ZIP file does not contain a chapter '$desiredChapter'")
                logger.info("Retrieving chapter $chapter from $path")
                return SgmlChapterReader.read(fileSource.inputStream)
            }
        }
    }

    override fun runConversion(inputFilename: File, resourceType: ResourceType, version: String): CodeSystem {
        val factory = when (resourceType) {
            ResourceType.ICD10GM -> buildIcdNodeFactory(inputFilename, version)
            ResourceType.OPS -> null //buildOpsNodeFactory(inputFilename)
            else -> throw IllegalArgumentException("Can not build a SGML converter for $resourceType, this is not supported")
        }
        val root = factory?.createNodeWalker()?.let {
            it.rootNode
        }
        TODO("Not yet implemented")
    }
}

class NonValidatingZipSource(file: File) : ZipSource(file.parentFile.toPath(), file.name, 0L) {
    override fun testChecksum() {
        // left blank to disable checksum, it's annoying.
    }
}