package de.uniluebeck.itcr.germanfhirterminology.sgml

import de.gecko.medicats.VersionedNode
import de.gecko.medicats.icd10.sgml.SgmlIcdNode
import de.gecko.medicats.ops.sgml.SgmlOpsNode
import de.uniluebeck.itcr.germanfhirterminology.ResourceType
import de.uniluebeck.itcr.germanfhirterminology.TerminologyConversionService
import org.hl7.fhir.r4.model.BooleanType
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
const val HINT_CODE = "hint"
const val PROPERTY_DEFINITION = "%s for this node. Please note that these should the valueString of this property should " +
        "not be used directly for ETL/mapping purposes, as the algorithm is currently deficient in its handling of " +
        "multi-column markup that is commonly used throughout the SGML documents."
const val IS_CURATED_CODE = "is-curated"
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
                description = PROPERTY_DEFINITION.format("An inclusion criterion")
            }
            addProperty().apply {
                code = EXCLUSION_CODE
                type = CodeSystem.PropertyType.STRING
                description = PROPERTY_DEFINITION.format("An exclusion criterion")
            }
            addProperty().apply {
                code = HINT_CODE
                type = CodeSystem.PropertyType.STRING
                description = PROPERTY_DEFINITION.format("A coding hint")
            }
            if (resourceType == ResourceType.OPS) addProperty().apply {
                code = IS_CURATED_CODE
                type = CodeSystem.PropertyType.BOOLEAN
                description = "If true, this code is/was used for billing purposes, and does not come from the 'extension" +
                        "catalog of OPS-301."
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
                        addHints(this, sgmlNode.hints)
                    }
                    ResourceType.OPS -> {
                        val sgmlNode = node as SgmlOpsNode
                        addInExclusionTexts(this, INCLUSION_CODE, sgmlNode.inclusionStrings)
                        addInExclusionTexts(this, EXCLUSION_CODE, sgmlNode.exclusionStrings)
                        addHints(this, sgmlNode.hints)
                        addAmtl(this, sgmlNode)
                    }
                }
            }
        }
        logger.info("Started with ${allNodes.size - 1} nodes (excluding root); wrote ${cs.concept.size} concepts to FHIR")
    }

    private fun addAmtl(conceptDefinitionComponent: CodeSystem.ConceptDefinitionComponent, sgmlNode: SgmlOpsNode) {
        conceptDefinitionComponent.addProperty().apply {
            code = IS_CURATED_CODE
            value = BooleanType(sgmlNode.isAmtl)
        }
    }

    private fun addHints(
        conceptDefinitionComponent: CodeSystem.ConceptDefinitionComponent,
        hints: List<String>
    ) = hints.forEach { hint ->
        conceptDefinitionComponent.addProperty().apply {
            code = HINT_CODE
            value = StringType(hint)
        }
    }

    private fun addInExclusionTexts(
        conceptDefinitionComponent: CodeSystem.ConceptDefinitionComponent,
        propertyCode: String,
        strings: List<String>
    ) = strings.forEach { s ->
        conceptDefinitionComponent.addProperty().apply {
            code = propertyCode
            value = StringType(s)
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

fun <T> Stream<T>.list(): List<T> = this.collect(Collectors.toList())