package de.uniluebeck.itcr.germanfhirterminology.output

import ca.uhn.fhir.context.FhirContext
import de.uniluebeck.itcr.germanfhirterminology.OutputUrlNamespace
import org.hl7.fhir.r4.model.CodeSystem
import org.springframework.stereotype.Service
import java.io.File

@Service
class FhirOutputService(
    private val fhirContext: FhirContext = FhirContext.forR4()
) {
    fun writeResources(codeSystem: CodeSystem, outputDirectory: File, outputUrlNamespace: OutputUrlNamespace) {
        TODO("Not yet implemented")
    }

}