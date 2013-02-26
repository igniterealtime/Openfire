/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.oscar;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import net.kano.joscar.BinaryTools;
import net.kano.joscar.ByteBlock;
import net.kano.joscar.DefensiveTools;
import net.kano.joscar.flap.FlapCommand;
import net.kano.joscar.snaccmd.auth.ClientVersionInfo;
import net.kano.joscar.tlv.Tlv;

import org.apache.log4j.Logger;

/**
 * Representation of an ICQ auth request.
 *
 * @author Daniel Henninger
 * Heavily inspired by AuthRequest.java from the joscar project.
 */
public class LoginICQFlapCmd extends FlapCommand {

    static Logger Log = Logger.getLogger(LoginICQFlapCmd.class);

    /**
     * The FLAP channel on which this command resides.
     */
    public static final int CHANNEL_LOGIN = 0x0001;

    /**
     * I guess this is the FLAP protocol version joscar implements; this is
     * what should be sent in a FLAP version command. <code>1</code> is the only
     * publically known FLAP protocol version.
     */
    public static final long VERSION_DEFAULT = 0x00000001;

    /**
     * The type of the TLV that contains the cookie block.
     */
    private static final int TYPE_COOKIE = 0x0006;

    /**
     * The version of the FLAP protocol in use.
     */
    private final long version;

    /**
     * The connection cookie.
     */
    private final ByteBlock cookie;

    /** A TLV type containing the user's uin. */
    private static final int TYPE_UIN = 0x0001;
    /** A TLV type containing the user's two-letter country code. */
    private static final int TYPE_COUNTRY = 0x000e;
    /** A TLV type containing the user's two-letter language code. */
    private static final int TYPE_LANG = 0x000f;
    /** A TLV type containing the user's password, xored. */
    private static final int TYPE_XORPASS = 0x0002;

    /** The user's UIN (user identification number). */
    private final String uin;

    /** The user's client version information. */
    private final ClientVersionInfo clVersion;

    /** The user's locale. */
    private final Locale locale;

    /** The user's password, encrypted. */
    private final ByteBlock encryptedPass;

    /**
     * Creates a <code>LoginCookieCmd</code> with the {@linkplain
     * #VERSION_DEFAULT default FLAP version} and no login cookie.
     * @param uin UIN of the username logging in
     * @param pass Password of the user logging in
     * @param clVersion version for the ICQ client
     * @param locale Locale for the connection.
     */
    public LoginICQFlapCmd(String uin, String pass, ClientVersionInfo clVersion, Locale locale) {
        this(VERSION_DEFAULT, null, uin, pass, clVersion, locale);
    }

    /**
     * Creates a <code>LoginCookieCmd</code> with the given FLAP version and
     * no login cookie. This constructor is useful for server developers because
     * no login cookie is ever sent by the server; also, no login cookie is sent
     * by the client or the server upon initial connection to the login ("auth")
     * server.
     *
     * @param version the FLAP protocol version in use on the FLAP connection on
     *        which this command will be sent
     * @param uin UIN of the username logging in
     * @param pass Password of the user logging in
     * @param clVersion version for the ICQ client
     * @param locale Locale for the connection.
     */
    public LoginICQFlapCmd(long version, String uin, String pass, ClientVersionInfo clVersion, Locale locale) {
        this(version, null, uin, pass, clVersion, locale);
    }

    /**
     * Creates a <code>LoginCookieCmd</code> with the {@linkplain
     * #VERSION_DEFAULT default FLAP version} and the given login cookie.
     *
     * @param cookie the login cookie for the connection on which this command
     *        will be sent
     * @param uin UIN of the username logging in
     * @param pass Password of the user logging in
     * @param clVersion version for the ICQ client
     * @param locale Locale for the connection.
     */
    public LoginICQFlapCmd(ByteBlock cookie, String uin, String pass, ClientVersionInfo clVersion, Locale locale) {
        this(VERSION_DEFAULT, cookie, uin, pass, clVersion, locale);
    }

    /**
     * Creates a new <code>LoginCookieCmd</code> with the given FLAP protocol
     * version and with the given cookie.
     *
     * @param version the FLAP protocol version in use
     * @param cookie a login "cookie" provided by another OSCAR connection
     * @param uin UIN of the username logging in
     * @param pass Password of the user logging in
     * @param clVersion version for the ICQ client
     * @param locale Locale for the connection.
     */
    public LoginICQFlapCmd(long version, ByteBlock cookie, String uin, String pass, ClientVersionInfo clVersion, Locale locale) {
        super(CHANNEL_LOGIN);

        DefensiveTools.checkRange(version, "version", 0);

        this.version = version;
        this.cookie = cookie;
        this.uin = uin;
        this.clVersion = clVersion;
        this.locale = locale;
        this.encryptedPass = ByteBlock.wrap(encryptICQPassword(pass));
        Log.debug("Non-encrypted password is "+pass+", Encrypted password is "+encryptedPass);
    }

    /**
     * Encrypts the given password with ICQ's xor key.
     * This code is borrowed from JOscarLib (http://sourceforge.net/projects/ooimlib/)
     *
     * @param pass the user's password
     * @return the user's password, encrypted
     */
    private byte[] encryptICQPassword(String pass) {
        //char charPass[] = new char[pass.length()];
        byte encPass[] = new byte[pass.length()];
        byte bytePassword[] = pass.getBytes();
        final byte[] xorValues = {
            (byte) 0xF3, (byte) 0x26, (byte) 0x81, (byte) 0xC4,
            (byte) 0x39, (byte) 0x86, (byte) 0xDB, (byte) 0x92
        };

        for (int i = 0, j; i < bytePassword.length; i++) {
            j = i % xorValues.length;
            //charPass[i] = (char) ( (byte) (bytePassword[i]) ^ (byte) (xorValues[j]));
            encPass[i] = (byte)((bytePassword[i]) ^ (xorValues[j]));
        }
        return encPass;
    }

    /**
     * Returns the UIN whose login is being attempted.
     *
     * @return the user's UIN
     */
    public final String getUIN() { return uin; }

    /**
     * Returns the user's client information block.
     *
     * @return the user's client version information
     */
    public final ClientVersionInfo getVersionInfo() { return clVersion; }

    /**
     * Returns the user's locale.
     *
     * @return the user's locale
     */
    public final Locale getLocale() { return locale; }

    /**
     * The raw encrypted password sent in this authorization request.
     *
     * @return the user's password, encrypted
     */
    public final ByteBlock getEncryptedPass() { return encryptedPass; }

    /**
     * Returns the FLAP protocol version declared in this command.
     *
     * @return the FLAP version of the FLAP connection on which this packet was
     *         received
     */
    public final long getVersion() { return version; }

    /**
     * Returns the login cookie associated with this command.
     *
     * @return this command's login cookie
     */
    public final ByteBlock getCookie() { return cookie; }

    @Override
    public void writeData(OutputStream out) throws IOException {
        BinaryTools.writeUInt(out, version);
        if (cookie != null) new Tlv(TYPE_COOKIE, cookie).write(out);
        if (uin != null) {
            Tlv.getStringInstance(TYPE_UIN, uin).write(out);
        }
        if (encryptedPass != null) {
            new Tlv(TYPE_XORPASS, encryptedPass).write(out);
        }

        // write the version TLV's
        if (clVersion != null) clVersion.write(out);

        if (locale != null) {
            String language = locale.getLanguage();
            if (!language.equals("")) {
                Tlv.getStringInstance(TYPE_LANG, language).write(out);
            }

            String country = locale.getCountry();
            if (!country.equals("")) {
                Tlv.getStringInstance(TYPE_COUNTRY, country).write(out);
            }
        }
    }

    @Override
    public String toString() {
        return "LoginICQFlapCmd: "
                + "version=" + version
                + ", cookie=" + cookie + ", uin='" + uin + "'" +
                ", version='" + version + "'" + ", locale=" + locale;
    }

}
