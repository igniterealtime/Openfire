/**
 * $Revision $
 * $Date $
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rayo.core.verb;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class Handset extends BaseVerb {

    public static final String MISSING_CRYPTO_SUITE = "Missing Crypto Suite";
    public static final String MISSING_LOCAL_CRYPTO = "Missing Local Crypto";
    public static final String MISSING_REMOTE_CRYPTO = "Missing Remote Crypto";
    public static final String MISSING_MIXER = "Missing Mixer";
    public static final String MISSING_CODEC = "Missing Codec";
    public static final String MISSING_STEREO = "Missing Stereo";

    @NotNull(message=Handset.MISSING_CRYPTO_SUITE)
    public String cryptoSuite;

    @NotNull(message=Handset.MISSING_LOCAL_CRYPTO)
    public String localCrypto;

    @NotNull(message=Handset.MISSING_REMOTE_CRYPTO)
    public String remoteCrypto;

    @NotNull(message=Handset.MISSING_CODEC)
    public String codec;

    @NotNull(message=Handset.MISSING_STEREO)
    public String stereo;

    @NotNull(message=Handset.MISSING_MIXER)
    public String mixer;

	public Handset(String cryptoSuite, String localCrypto, String remoteCrypto, String codec, String stereo, String mixer)
	{
		this.cryptoSuite = cryptoSuite;
		this.localCrypto = localCrypto;
		this.remoteCrypto = remoteCrypto;
		this.codec = codec;
		this.stereo = stereo;
		this.mixer = mixer;
	}

	@Override
    public String toString() {

    	return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
    		.append("callId", getCallId())
    		.append("verbId", getVerbId())
    		.append("cryptoSuite",cryptoSuite)
    		.append("localCrypto",localCrypto)
    		.append("remoteCrypto",remoteCrypto)
    		.append("codec",codec)
    		.append("stereo",stereo)
    		.append("stereo",mixer)
    		.toString();
    }
}
