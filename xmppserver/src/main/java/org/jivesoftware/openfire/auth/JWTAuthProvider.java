package org.jivesoftware.openfire.auth;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;

import javax.xml.bind.DatatypeConverter;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * The JWT auth provider allows you to authenticate users with an JSON WEB TOKEN.
 * This Token has to be issued by an other server. Mostly JWT is used to implement some kind of
 * Single Sign On. 
 * 
 * The JWTAuthProvider can be used with the {@link HybridAuthProvider hybrid} auth provider.
 *
 * To enable this provider, set the following in the system properties:
 * <ul>
 * <li>{@code provider.auth.className = org.jivesoftware.openfire.auth.JWTAuthProvider}</li>
 * </ul>
 * * 
 * You'll also need to set your "Secret" and the "Issuer"
 * <ul>
 * <li>{@code xmpp.auth.jwtsecret = "A GOOD SECRET YOU CHOOSE" (minimum 256Bit String)}</li>
 * <li>{@code xmpp.auth.jwtissuer = "THE ISSUERS DOMAIN, SHOULD BE THE XMPP DOMAIN TOO"}</li>
 * <li>{@code xmpp.auth.jwtcertpath = "THE PATH TO THE CERTIFICATE (RSA) TO CHECK SIGNATURE"}</li>
 *  
 * </ul>
 * 
 * <p>The Signature algorithm should be HC256!
 * 
 *
 * @author mightymop
 */
public class JWTAuthProvider implements AuthProvider {

    final static Logger Log = LoggerFactory.getLogger(JWTAuthProvider.class);
    
    public static final SystemProperty<String> JWT_SECRET = SystemProperty.Builder.ofType(String.class)
            .setKey("xmpp.auth.jwtsecret")
            .setDefaultValue(null)
            .setDynamic(false)
            .build();

    public static final SystemProperty<String> JWT_ISSUER = SystemProperty.Builder.ofType(String.class)
            .setKey("xmpp.auth.jwtissuer")
            .setDefaultValue(null)
            .setDynamic(false)
            .build();
    public static final SystemProperty<String> JWT_CERT_PATH = SystemProperty.Builder.ofType(String.class)
            .setKey("xmpp.auth.jwtcertpath")
            .setDefaultValue(null)
            .setDynamic(false)
            .build();

    public PublicKey getPublicKeyFromFile()
    {
        try
        {
            if (JWT_CERT_PATH.getValue()==null||JWT_CERT_PATH.getValue().trim().length()==0)
            {
                return null;
            }

            byte[] keyBytes = Files.readAllBytes(Paths.get(JWT_CERT_PATH.getValue()));

            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        }
        catch (Exception e)
        {
            Log.error("JWT cert not loaded",e);
            return null;
        }
    }

    /**
     * This method checks the user with its jwt.
     * First of all the system variables will be checked.
     * There must be a secret in xmpp.auth.jwtsecret (minimum 256bit String) for HMAC Signatures! Otherwise this method checks for a certificate in xmpp.auth.jwtcertpath.
     * Then the user and token will be checked and verified. The JTW's subject must equal the user and the issuer must equal to xmpp.auth.jwtissuer.
     * Furthermore the token will be checked for expiration.
     * @param user xmpp-username
     * @param jwttoken JSON WEB TOKEN (in password field)
     */
    public void authenticate(String user, String jwttoken)
            throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {

        if ((JWT_SECRET.getValue()==null||JWT_SECRET.getValue().isEmpty())&&getPublicKeyFromFile()==null)
        {
            Log.error("user {} authentication with JWT TOKEN failed: {}", user, "JWT-secret is empty or to short.");
            throw new UnauthorizedException();
        }

        if (JWT_ISSUER.getValue()==null||JWT_ISSUER.getValue().isEmpty())
        {
            Log.error("user {} authentication with JWT TOKEN failed: {}", user, "JWT-Issuer is empty");
            throw new UnauthorizedException();
        }

        boolean isUserAuthorized = false;
        Log.debug("authenticate() - starts");

        if (user == null || jwttoken == null) {
            throw new UnauthorizedException();
        }

        Log.debug("authenticating user {} with token {}", user, jwttoken);

        String issuer = null;
        String subject = null;
        Date expiration = null;

        try 
        {
            //Parse the JWT and validate with libs build in checks
            JwtParserBuilder parserBuilder=Jwts.parserBuilder();

            Jwt<?, ?> jwt=null;
            Claims claims = null;
            Key secret = null;
            JwtParser parser=null;

            try
            {
                //first try validate / parsing with certificate if we have one...
                if ((secret=getPublicKeyFromFile())!=null)
                {
                    parser=parserBuilder.setSigningKey(secret).build();
                }
                if (parser!=null)
                {
                    jwt = parser.parse(jwttoken);
                    claims = (Claims) jwt.getBody();
                }
                else
                {
                    //we dont have a cert so try secret
                    try
                    {
                        byte[] secretBytes = DatatypeConverter.parseBase64Binary(JWT_SECRET.getValue());
                        secret = Keys.hmacShaKeyFor(secretBytes);
                        parser = parserBuilder.setSigningKey(secret).build();
                        jwt = parser.parse(jwttoken);
                        claims = (Claims) jwt.getBody();
                    }
                    catch (Exception ex)
                    {
                        //signature check failed or jwt is corrupted
                        throw new UnauthorizedException("Username {}" + user + " not allowed to login!: {}" + ex.getMessage()) ;
                    }
                }
            }
            catch (Exception e)
            {
                Log.info("The JWT could not be verified with the certificate, trying HMAC secret now:{}",e.getMessage());
                try
                {
                    byte[] secretBytes = DatatypeConverter.parseBase64Binary(JWT_SECRET.getValue());
                    secret = Keys.hmacShaKeyFor(secretBytes);
                    parser = parserBuilder.setSigningKey(secret).build();
                    jwt = parser.parse(jwttoken);
                    claims = (Claims) jwt.getBody();
                }
                catch (Exception ex)
                {
                    //signature check failed or jwt is corrupted
                    throw new UnauthorizedException("Username {}" + user + " not allowed to login!: {}" + e.getMessage()) ;
                }
            }

            //warn if the token is unsigned
            if (!parser.isSigned(jwttoken))
            {
                Log.warn("The JWT used to authenticate is not signed.");
            }

            issuer = claims.getIssuer();
            subject = claims.getSubject();
            expiration = claims.getExpiration();

            //check for empty issuer, subject and expiration date
            if (issuer==null||issuer.isEmpty()||subject==null||subject.isEmpty()||expiration==null)
            {
                throw new UnauthorizedException("Username " + user + " not allowed to login! Token is not valid (issuer, subject or expiration are empty)");
            }

            //if the issuer puts an email adress into the subject, cut the domain part
            if (subject.contains("@"))
            {
                //we only need the node part of an email
                subject=subject.substring(0,subject.indexOf("@"));
            }

            //check for valid issuer, subject must equal user and expiration date
            if (!issuer.equals(JWT_ISSUER.getValue())||!subject.equals(user)||!expiration.after(new Date()))
                {
                    throw new UnauthorizedException("Username " + user + " not allowed to login! Token is not valid (issuer!=xmpp.auth.jwtissuer, subject!=user or token is expired)");
                }

            UserManager umgr = XMPPServer.getInstance().getUserManager();

            //lastly search for user. if user is unknown it cant be authorized too. 
            umgr.getUser(subject);

            isUserAuthorized = true;
        } catch (AssertionError e) {
            Log.warn("user {} authentication failed: {}", user, e.getMessage());
            throw new UnauthorizedException("Username " + user + " not allowed to login!: " + e.getMessage());
        }
        catch (ExpiredJwtException e) {
            Log.warn("user {} authentication failed: {}", user, e.getMessage());
            throw new UnauthorizedException("Username " + user + " not allowed to login! Token expired");
        } catch (UserNotFoundException e) {
            Log.warn("user {} authentication failed: {}", user, e.getMessage());
            throw new UnauthorizedException("Username " + user + " not allowed to login! User not found");
        }

        Log.debug("user {} authenticated succesfully", user);

        if (isUserAuthorized == false) {
            throw new UnauthorizedException();
        }

        Log.debug("authenticate() - ends");
    }

    public void authenticate(String s, String token, String digest)
            throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
        Log.debug("authenticating user {} with token {} and digest {}", new Object[]{s, token, digest});
        throw new UnauthorizedException();
    }

    public String getPassword(String arg0) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public boolean isDigestSupported() {
        return false;
    }

    public boolean isPlainSupported() {
        return true;
    }

    public void setPassword(String arg0, String arg1) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }

    @Override
    public boolean isScramSupported() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getSalt(String username) throws UnsupportedOperationException, UserNotFoundException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getIterations(String username) throws UnsupportedOperationException, UserNotFoundException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getServerKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getStoredKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        // TODO Auto-generated method stub
        return null;
    }
}