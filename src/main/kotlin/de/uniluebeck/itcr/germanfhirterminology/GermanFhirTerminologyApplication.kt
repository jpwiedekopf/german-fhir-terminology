package de.uniluebeck.itcr.germanfhirterminology

import de.uniluebeck.itcr.germanfhirterminology.output.FhirOutputService
import de.uniluebeck.itcr.germanfhirterminology.sgml.SgmlService
import org.hl7.fhir.r4.model.CodeSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.Callable

private val logger: Logger = LoggerFactory.getLogger(GermanFhirTerminology::class.java)

@SpringBootApplication
class GermanFhirTerminologyApplication(
    @Autowired private val iFactory: IFactory, @Autowired private val germanFhirTerminology: GermanFhirTerminology
) : CommandLineRunner, ExitCodeGenerator {

    private var exitCode: Int = 0

    override fun run(vararg args: String?) {
        exitCode = CommandLine(germanFhirTerminology, iFactory).execute(*args)
    }

    override fun getExitCode(): Int = exitCode
}

fun main(args: Array<String>) {
    runApplication<GermanFhirTerminologyApplication>(*args)
}

@Component
@Command
class GermanFhirTerminology(
    @Autowired val sgmlService: SgmlService,
    //@Autowired val clamlService: FhirClamlService,
    @Autowired val outputService: FhirOutputService
) : Callable<Int> {

    @Spec
    lateinit var spec: CommandSpec

    private lateinit var inputFilename: File
    private lateinit var outputDirectory: File

    @Option(names = ["--input", "-i"], description = ["ZIP file to process"], required = true)
    fun setInputFilename(value: File) = when {
        !value.exists() -> "The file does not exist"
        !value.canRead() -> "The file is not readable"
        !isArchive(value) -> "The file is not recognized as a ZIP file"
        else -> null
    }?.let { validationMessage ->
        throw ParameterException(spec.commandLine(), "Invalid value '$value' for filename. $validationMessage")
    } ?: run {
        inputFilename = value
    }

    @Option(
        names = ["--version", "-v"], description = ["The version of the terminology, e.g. 2004 or 1.3"], required = true
    )
    lateinit var version: String

    @Option(
        names = ["--routine", "-r"],
        description = ["Conversion routine to call (\${COMPLETION-CANDIDATES})"],
        required = true
    )
    lateinit var routine: ConversionRoutine

    @Option(
        names = ["--url", "-u"],
        description = ["Output URL of the generated CodeSystem(-s) (\${COMPLETION-CANDIDATES})"],
        defaultValue = "BOTH"
    )
    lateinit var outputUrlNamespace: OutputUrlNamespace

    @Option(
        names = ["--type", "-t"], description = ["Resource type (\${COMPLETION-CANDIDATES})"], required = true
    )
    lateinit var resourceType: ResourceType

    @Option(
        names = ["--output", "-o"],
        description = ["Output directory, will be created if it doesn't exist"],
        required = true
    )
    fun setOutputDirectory(value: File) = when {
        value.isFile -> "The provided value is a file, not a directory."
        value.isDirectory && !value.canWrite() -> "Can't write to the provided directory"
        !value.exists() -> if (value.mkdir()) null else "Could not create the provided directory"
        else -> null
    }?.let { validationMessage ->
        throw ParameterException(spec.commandLine(), "Invalid value '$value' for output directory. $validationMessage")
    } ?: run {
        outputDirectory = value
    }


    override fun call(): Int {
        logger.info("Running with arguments: ${printArgs()}")
        val converter = when (routine) {
            ConversionRoutine.SGML -> sgmlService
            ConversionRoutine.CLAML -> TODO()
        }
        val codeSystem = converter.runConversion(inputFilename, resourceType, version)
        outputService.writeResources(codeSystem, outputDirectory, outputUrlNamespace)
        return 0
    }

    fun printArgs(): String = """
        inputFilename=$inputFilename
        outputDirectory=$outputDirectory
        outputUrlNamespace=$outputUrlNamespace
        resourceType=$resourceType
        routine=$routine
        version=$version
    """

}

interface TerminologyConversionService {
    fun runConversion(inputFilename: File, resourceType: ResourceType, version: String): CodeSystem
}

enum class ResourceType {
    ICD10GM, OPS, ALPHAID, ICDO
}

enum class ConversionRoutine {
    SGML, CLAML
}

enum class OutputUrlNamespace {
    DIMDI, BFARM, BOTH
}

private fun isArchive(f: File): Boolean {
    var fileSignature = 0
    try {
        RandomAccessFile(f, "r").use { raf -> fileSignature = raf.readInt() }
    } catch (e: IOException) {
        return false
    }
    return fileSignature == 0x504B0304 || fileSignature == 0x504B0506 || fileSignature == 0x504B0708
}