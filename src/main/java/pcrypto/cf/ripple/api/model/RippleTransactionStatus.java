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

package pcrypto.cf.ripple.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.StringUtils;
import pcrypto.cf.exception.BadRequestException;

import java.util.Arrays;


public enum RippleTransactionStatus
{
    PENDING( 1 ),
    COMPLETE( 2 ),
    TIMEOUT( 3 );


    private int id;

    RippleTransactionStatus( final int id )
    {
        this.id = id;
    }

    public static RippleTransactionStatus valueOfIgnoreCase( final String typeString )
    {
        try
        {
            return valueOf( StringUtils.upperCase( typeString ) );
        }
        catch ( final IllegalArgumentException e )
        {
            return null;
        }
    }

    /**
     * Control Jackson serialization to do case-insensitive serialization.
     *
     * @param string original value
     * @return the Enum
     * @throws BadRequestException if an invalid value was sent
     */
    @JsonCreator
    public static RippleTransactionStatus fromString( final String string )
    {
        final RippleTransactionStatus enumType = valueOfIgnoreCase( string );
        if ( enumType == null )
        {
            throw new IllegalArgumentException( string + " must be one of " + Arrays.toString( RippleTransactionStatus.values() ) );
        }
        return enumType;
    }

    public static RippleTransactionStatus fromId( final int id )
    {
        switch ( id )
        {
            case 1:
                return RippleTransactionStatus.PENDING;

            case 2:
                return RippleTransactionStatus.COMPLETE;

            case 3:
                return RippleTransactionStatus.TIMEOUT;

            default:
                throw new IllegalArgumentException( "RippleTransactionStatus id [" + id + "] not supported." );
        }
    }

    public int getId()
    {
        return id;
    }
}
