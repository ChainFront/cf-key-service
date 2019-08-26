/*
 * Copyright (c) 2019 ChainFront LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pcrypto.cf.docs

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.OperationBuilderPlugin
import springfox.documentation.spi.service.contexts.OperationContext
import springfox.documentation.swagger.common.SwaggerPluginSupport
import java.util.*


/**
 * Processor to handle @ApiCodeSamples and @ApiCodeSample annotations, and add the resulting lang+code to an 'x-code-samples'
 * object which is rendered by ReDoc.
 */
@Component
@Order(SwaggerPluginSupport.SWAGGER_PLUGIN_ORDER + 1002)
class ApiCodeSampleExtensionProcessor : OperationBuilderPlugin {

    override fun supports(documentationType: DocumentationType): Boolean {
        // Only supports SWAGGER version 2.0
        return DocumentationType.SWAGGER_2 == documentationType
    }

    override fun apply(operationContext: OperationContext) {

        val annotations = operationContext.findAllAnnotations(ApiCodeSamples::class.java)

        if (annotations.isNotEmpty()) {
            val apiCodeSamples = annotations[0]
            val samples = apiCodeSamples.samples

            val list = ArrayList<ApiCodeSamplesVendorExtension.Sample>()
            for (sample in samples) {
                val lang = sample.lang
                val source = sample.source
                list.add(ApiCodeSamplesVendorExtension.Sample(lang, source))
            }

            val vendorExtension = ApiCodeSamplesVendorExtension(
                "x-code-samples",
                list
            )

            operationContext.operationBuilder().extensions(listOf(vendorExtension))
        }
    }
}
