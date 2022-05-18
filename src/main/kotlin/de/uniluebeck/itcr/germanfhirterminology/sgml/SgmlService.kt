package de.uniluebeck.itcr.germanfhirterminology.sgml

import de.gecko.medicats.VersionedNode
import de.gecko.medicats.icd10.sgml.SgmlIcdNode
import de.gecko.medicats.ops.OpsNode
import de.gecko.medicats.ops.sgml.SgmlOpsNode
import de.uniluebeck.itcr.germanfhirterminology.ResourceType
import de.uniluebeck.itcr.germanfhirterminology.TerminologyConversionService
import org.hl7.fhir.r4.model.CodeSystem
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.StringType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.stream.Collectors
import java.util.stream.Stream

private val logger: Logger = LoggerFactory.getLogger(SgmlService::class.java)

const val INCLUSION_CODE = "inclusion"
const val EXCLUSION_CODE = "exclusion"
const val PARENT_CODE = "parent"
const val ROOT = "ROOT"

@Service
class SgmlService : TerminologyConversionService {

    override fun runConversion(inputFilename: File, resourceType: ResourceType, version: String): CodeSystem {
        val factory = when (resourceType) {
            ResourceType.ICD10GM -> SgmlIcdNodeFactory(inputFilename, version)
            ResourceType.OPS -> OpsSgmlNodeFactory(inputFilename, version)
            else -> throw IllegalArgumentException("Can not build a SGML converter for $resourceType, this is not supported")
        }
        val rootNode: VersionedNode<*> = factory.createNodeWalker()?.rootNode
            ?: throw UnsupportedOperationException("No root node could be built for ${inputFilename.absoluteFile}, $resourceType version $version")
        val cs = CodeSystem().apply {
            this.name = factory.name
            this.version = factory.version
            addProperty().apply {
                code = INCLUSION_CODE
                type = CodeSystem.PropertyType.STRING
            }
            addProperty().apply {
                code = EXCLUSION_CODE
                type = CodeSystem.PropertyType.STRING
            }
        }
        logger.info("Walking nodes...")
        addAllNodes(cs, resourceType, rootNode)
        return cs
    }

    private fun addAllNodes(cs: CodeSystem, resourceType: ResourceType, rootNode: VersionedNode<*>) {
        val allNodes = rootNode.childrenRecursive().list()
        allNodes.forEach { node ->
            if (node.code == ROOT) return@forEach
            cs.addConcept().apply {
                code = node.code
                display = node.label
                node.parent?.let { parent ->
                    if (parent.code == ROOT) return@let
                    addProperty().apply {
                        code = PARENT_CODE
                        value = CodeType(parent.code)
                    }
                }
                @Suppress("NON_EXHAUSTIVE_WHEN_STATEMENT") when (resourceType) {
                    ResourceType.ICD10GM -> {
                        val sgmlNode = node as SgmlIcdNode
                        addInExclusion(this, INCLUSION_CODE, sgmlNode.inclusionCodes, sgmlNode.inclusionStrings)
                        addInExclusion(this, EXCLUSION_CODE, sgmlNode.exclusionCodes, sgmlNode.exclusionStrings)
                    }
                    ResourceType.OPS -> {
                        val sgmlNode = node as SgmlOpsNode
                        addOpsProperty(INCLUSION_CODE, sgmlNode.getInclusions { code ->
                            allNodes.filter { it.code == code }.map { it as OpsNode }
                        })
                    }
                }
            }
        }
    }
}

fun addInExclusion(
    concept: CodeSystem.ConceptDefinitionComponent, propertyCode: String, codes: List<String?>, strings: List<String>
) {
    require(strings.size == codes.size)
    val props = strings.zip(codes).map { (s, c) ->
        when (c) {
            null -> s
            else -> "$s ($c)"
        }
    }
    props.forEach { prop ->
        concept.addProperty().apply {
            code = propertyCode
            value = StringType(prop)
        }
    }
}

fun CodeSystem.ConceptDefinitionComponent.addOpsProperty(propCode: String, nodes: Stream<OpsNode>) {
    nodes.forEach { node ->
        this.addProperty().apply {
            code = propCode
            value = CodeType(node.code)
        }
    }
}

fun <T> Stream<T>.list(): List<T> = this.collect(Collectors.toList())