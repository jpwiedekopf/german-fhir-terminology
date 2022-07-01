package de.uniluebeck.itcr.germanfhirterminology.output

import ca.uhn.fhir.context.FhirContext
import de.uniluebeck.itcr.germanfhirterminology.OutputUrlNamespace
import de.uniluebeck.itcr.germanfhirterminology.ResourceType
import org.hl7.fhir.r4.model.CodeSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

private val logger = LoggerFactory.getLogger(FhirOutputService::class.java)
private const val DIMDI = "dimdi"
private const val BFARM = "bfarm"
private const val URL_OPS_DIMDI = "http://fhir.de/CodeSystem/dimdi/ops"
private const val URL_OPS_BFARM = "http://fhir.de/CodeSystem/bfarm/ops"
private const val URL_ICD_DIMDI = "http://fhir.de/CodeSystem/dimdi/icd-10-gm"
private const val URL_ICD_BFARM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm"
private val ICD10MAP = mapOf(DIMDI to URL_ICD_DIMDI, BFARM to URL_ICD_BFARM)
private val OPSMAP = mapOf(DIMDI to URL_OPS_DIMDI, BFARM to URL_OPS_BFARM)

@Service
class FhirOutputService(
    private val fhirContext: FhirContext = FhirContext.forR4()
) {
    fun writeResources(
        codeSystem: CodeSystem,
        resourceType: ResourceType,
        version: String,
        outputDirectory: File,
        outputUrlNamespace: OutputUrlNamespace
    ) {
        val parser = fhirContext.newJsonParser().setPrettyPrint(true)
        val urlMap = when (resourceType) {
            ResourceType.ICD10GM -> ICD10MAP
            ResourceType.OPS -> OPSMAP
            else -> TODO()
        }
        val urls = when (outputUrlNamespace) {
            OutputUrlNamespace.BOTH -> urlMap
            OutputUrlNamespace.DIMDI -> urlMap.filter { it.key == DIMDI }
            OutputUrlNamespace.BFARM -> urlMap.filter { it.key == BFARM }
        }

        val cleanedCodeSystem = codeSystem.apply {
            val groupedConcepts = concept.groupBy { it.code }
            val duplicateConcepts = groupedConcepts.filter { (_, concepts) ->
                concepts.size > 1
            }.filterNot { it.key == "DUMMY" }
            this.concept.clear()
            groupedConcepts.forEach { (code, concepts) ->
                if (code in duplicateConcepts.keys) {
                    logger.warn("Duplicate concept $code (#${concepts.size}: ${concepts.joinToString { "'${it.code}'='${it.display}' (${it.property.count()} props)" }}")
                }
                this.concept.add(concepts.first())
            }
        }

        urls.forEach { (namespace, url) ->
            codeSystem.url = url
            val outputFilename = outputDirectory.resolve("$resourceType-$namespace-$version.json")
            parser.encodeResourceToWriter(cleanedCodeSystem, outputFilename.writer())
            logger.info("Wrote $resourceType ($namespace) to $outputFilename")
        }
    }

}