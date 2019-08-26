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

package pcrypto.cf.ripple.client.dto;

import com.ripple.core.coretypes.Amount;
import com.ripple.core.coretypes.Currency;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class RippleAccountLineDto
{
    public Amount balance;
    public Amount limit_peer;
    public Amount limit;

    public Currency currency;

    public boolean freeze = false;
    public boolean freeze_peer = false;

    public boolean authorized = false;
    public boolean authorized_peer = false;

    public boolean no_ripple = false;
    public boolean no_ripple_peer = false;

    public int quality_in = 0;
    public int quality_out = 0;
}
