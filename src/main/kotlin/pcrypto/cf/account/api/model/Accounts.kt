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

package pcrypto.cf.account.api.model

import java.util.*


/**
 * Accounts
 */
class Accounts : ArrayList<Account>() {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return !(other == null || javaClass != other.javaClass)
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode())
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("class Accounts {\n")
        sb.append("    ").append(toIndentedString(super.toString())).append("\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private fun toIndentedString(o: Any?): String {
        return o?.toString()?.replace("\n", "\n    ") ?: "null"
    }
}

