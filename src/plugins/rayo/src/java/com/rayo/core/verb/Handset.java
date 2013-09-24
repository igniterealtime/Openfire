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

    @NotNull(message=Handset.MISSING_MIXER)
    public String mixer;

    @NotNull(message=Handset.MISSING_CODEC)
    public String codec;

    @NotNull(message=Handset.MISSING_STEREO)
    public String stereo;

	public Handset(String cryptoSuite, String localCrypto, String remoteCrypto, String mixer, String codec, String stereo)
	{
		this.cryptoSuite = cryptoSuite;
		this.localCrypto = localCrypto;
		this.remoteCrypto = remoteCrypto;
		this.mixer = mixer;
		this.codec = codec;
		this.stereo = stereo;
	}

	@Override
    public String toString() {

    	return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
    		.append("callId", getCallId())
    		.append("verbId", getVerbId())
    		.append("cryptoSuite",cryptoSuite)
    		.append("localCrypto",localCrypto)
    		.append("remoteCrypto",remoteCrypto)
    		.append("mixer",mixer)
    		.append("codec",codec)
    		.append("stereo",stereo)
    		.toString();
    }
}
