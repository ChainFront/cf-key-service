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

package pcrypto.cf.stellar.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.StringUtils;
import pcrypto.cf.exception.BadRequestException;

import java.util.Arrays;


public enum StellarTransactionStatus
{
    PENDING( 1 ),
    COMPLETE( 2 ),
    TIMEOUT( 3 );


    private int id;

    StellarTransactionStatus( final int id )
    {
        this.id = id;
    }

    public static StellarTransactionStatus valueOfIgnoreCase( final String typeString )
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
    public static StellarTransactionStatus fromString( final String string )
    {
        final StellarTransactionStatus enumType = valueOfIgnoreCase( string );
        if ( enumType == null )
        {
            throw new IllegalArgumentException( string + " must be one of " + Arrays.toString( StellarTransactionStatus.values() ) );
        }
        return enumType;
    }

    public static StellarTransactionStatus fromId( final int id )
    {
        switch ( id )
        {
            case 1:
                return StellarTransactionStatus.PENDING;

            case 2:
                return StellarTransactionStatus.COMPLETE;

            case 3:
                return StellarTransactionStatus.TIMEOUT;

            default:
                throw new IllegalArgumentException( "StellarTransactionStatus id [" + id + "] not supported." );
        }
    }

    public int getId()
    {
        return id;
    }
}
