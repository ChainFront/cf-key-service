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

package pcrypto.cf.config.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import pcrypto.cf.exception.BadRequestException
import java.io.IOException


@Configuration
class JacksonConfig {

    @Primary
    @Bean
    @Autowired
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper {
        val objectMapper = builder.createXmlMapper(false).build<ObjectMapper>()

        objectMapper.registerModule(Jdk8Module())
        objectMapper.registerModule(JavaTimeModule())

        // Ignore invalid property names, and instead rely on bean validation. This is
        // to ensure that we have proper error messages, rather than the ugly Jackson databind
        // exceptions.
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false)
        objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)

        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

        // Suppress null value fields from the output
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

        val problemHandler = CustomDeserializationProblemHandler()
        objectMapper.addHandler(problemHandler)

        return objectMapper
    }


    internal class CustomDeserializationProblemHandler : DeserializationProblemHandler() {
        @Throws(IOException::class, BadRequestException::class)
        override fun handleUnknownProperty(
            ctxt: DeserializationContext?,
            p: JsonParser?,
            deserializer: JsonDeserializer<*>?,
            beanOrClass: Any?,
            propertyName: String?
        ): Boolean {
            throw BadRequestException(String.format("Unknown property: %s", propertyName))
        }

        @Throws(IOException::class, BadRequestException::class)
        override fun handleInstantiationProblem(
            ctxt: DeserializationContext?,
            instClass: Class<*>?,
            argument: Any?,
            t: Throwable?
        ): Any {
            throw BadRequestException(t?.message ?: "")
        }
    }
}
