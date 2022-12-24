package info.neu.infoapp.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final RSAKey rsaPublicKey;
    private final RSAKey rsaJWK;


    public String generateToken() throws JOSEException {

        JWSSigner signer = new RSASSASigner(rsaJWK);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .expirationTime(new Date(System.currentTimeMillis()  + 8000*60*1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(),
                claimsSet);

        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    public String authorize(String authorization) {
        if (authorization == null || authorization.isEmpty()) return "NO TOKEN FOUND";

        if (!authorization.contains("Bearer ")) return "INVALID FORMAT";

        String token = authorization.split(" ")[1];



        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new RSASSAVerifier(this.rsaPublicKey);

            if (!signedJWT.verify(verifier)) return "INVALID TOKEN";

            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (new Date().after(expirationTime)) {
                return "TOKEN EXPIRED";
            }
        } catch (JOSEException | ParseException e) {
            return "INVALID TOKEN";
        }
        return "VALID TOKEN";
    }
}
