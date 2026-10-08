package com.aalamstudio.compiler;

import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

/** Built-in DEBUG signing key. Sirf testing ke liye - release ke liye apna keystore use karna. */
public class DebugKey {

    private static final String KEY =
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCdMzdOuianXXw0" +
        "3x67QMVQYLotV2cr1J+F8Ev5RliA508ZCDjrDJFNJOg0EdukW6E8gOVBqRB64tjQ" +
        "ZB/myf+tFcnNbCKBupFWKRr9aTIUmsrfFNv8RpbGJyLddo7TclBttra3tDVc6PaG" +
        "930loSHVHQJJaXPzaDJ/xczmmrdsu0fi0rU6CN7Xh5sWY7lBP0naCchEMai7sLz1" +
        "cAAkGSbGXnQgOU/o6jm/NygtiPxACwVE3rRtkp2k1VegjUFwVSuB8s5On9fmp2ze" +
        "DsbZnJDLc2Q6XTdsNAymvAE/EggdEhl6o36txb4KhP2yXsbpd9K+y7cPR1NeNpRY" +
        "T1i00PyXAgMBAAECggEAAtVhmbuVDWI/GWYS+m9KvzRAvUCvw7EwjB9oA/iNItgV" +
        "UgJ9O1tkIrHxT4yQDkPdwaa77lv0rjDFlYGgg5TAVSd14wwgCRrKtA6Un0NUqENs" +
        "BOsuCSH6mMLFOzZ/95BX1WQJCIGo9PsSaoQugmc61WN3xZWVhfcqv3Vz+1ohlHk6" +
        "e4Y2IuH/wuL1JeriSF1oHbXBdqAd8sDvhPobn1K2LnFU2uBOPqO+Lo8X6vUPUbkq" +
        "qyLlVUShp3K44wnGQqolyqoYEHmwLdK8JOTKfuUKyCTr0DtzYRlRwWVyzhn24W8E" +
        "n1gT1x3PEIX8WrRejRLVryKhb6BIw+Jqa6NecbPCsQKBgQDTsMO/1qqv/IaMG7lN" +
        "xby/Ba/i22S5SDJJ1pzwFvEi+P84NF5HQh3V3Ncmnn2cens9PhicSyPHHZxrLoqO" +
        "yJ/t0b4VrOge6kPfyYOfXcW+QBkD+dq4zEpyTmp2psSvgGBmVyqSl71Gu9I9CRkD" +
        "uOLT0jOxBABT16HoN+IhnPhgpwKBgQC+GqIimF4bxDO9U/yeObgNX0pSB1mhI2xG" +
        "BS6DvxF0TnauwSaH+xRriKP+QPfwthdxn5WDshkUtPQRL7SeDyRhKUNzh4cUvRWx" +
        "srZ/pQcROE8YNLTxO2cLduVAOxR2q5Uafvet9N2gY4NicAi/FatvOuNbvaAnmizu" +
        "mgv7KEWSkQKBgQC0VS/cO626L8AamP95bkqE2/5ing0m/YbBeg7FRHX1GfKo2Yu6" +
        "3mr2JaQu5PMSmxBjMoPamPQIioQWllY4nlYHTRbF9j7jHyPk/xCecU6j7Iyi43Tc" +
        "2kNNLtno69Y/v3fflr/Qk20NVbEah6aEkjrAwZ+BW2xAAECPxxNfGQ5JnwKBgDpM" +
        "bKBzXZjiSIOtN/FF/h2LtX0GaAQ0msW2XRdmBvJGBbt93FUjOJXpp3EXd3HIRdRA" +
        "fleOzIY/IAINQTSfrCZnwxmw3EoN2pNOHQ10DIDJZegkccDw2J75bUCPXa0u3WDs" +
        "wwQD7dt76RTNCsGe4Z4QzxyZQH4kl9bsQvRY200hAoGBAMTd1TpLOX1SCpPEvoFe" +
        "/oGZSf6Hx3NroxM++NqooNCJ1XiOn/3cbIP9S8AQUgaHc2V3ZtgGj1mVMhPulYTH" +
        "MYLV6sJSL1Y5H0LqxKIky39PpzXT1iN22voBhdnf/JTtmO3uQ8oecvqfrdhyHuv+" +
        "59YNyM2/LQF6BbFg/iHkpvvV";

    private static final String CERT =
        "MIIDVzCCAj+gAwIBAgIUEef3suPSrRbdFTtmUxBJIkti+OQwDQYJKoZIhvcNAQEL" +
        "BQAwOjEUMBIGA1UEAwwLQWFsYW0gRGVidWcxFTATBgNVBAoMDEFhbGFtIFN0dWRp" +
        "bzELMAkGA1UEBhMCSU4wIBcNMjYxMDA4MDk1ODMwWhgPMjA1NDAyMjMwOTU4MzBa" +
        "MDoxFDASBgNVBAMMC0FhbGFtIERlYnVnMRUwEwYDVQQKDAxBYWxhbSBTdHVkaW8x" +
        "CzAJBgNVBAYTAklOMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnTM3" +
        "Tromp118NN8eu0DFUGC6LVdnK9SfhfBL+UZYgOdPGQg46wyRTSToNBHbpFuhPIDl" +
        "QakQeuLY0GQf5sn/rRXJzWwigbqRVika/WkyFJrK3xTb/EaWxici3XaO03JQbba2" +
        "t7Q1XOj2hvd9JaEh1R0CSWlz82gyf8XM5pq3bLtH4tK1Ogje14ebFmO5QT9J2gnI" +
        "RDGou7C89XAAJBkmxl50IDlP6Oo5vzcoLYj8QAsFRN60bZKdpNVXoI1BcFUrgfLO" +
        "Tp/X5qds3g7G2ZyQy3NkOl03bDQMprwBPxIIHRIZeqN+rcW+CoT9sl7G6XfSvsu3" +
        "D0dTXjaUWE9YtND8lwIDAQABo1MwUTAdBgNVHQ4EFgQU0WES2u+GFmbs4cvz8KYW" +
        "dv8tm7cwHwYDVR0jBBgwFoAU0WES2u+GFmbs4cvz8KYWdv8tm7cwDwYDVR0TAQH/" +
        "BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAdf/zZBNubdmRk/7IzZjn+SsHMY28" +
        "GrHQ5rJIuPsLrJi36P76fCwxcQoA2fLuf2L1//yWxYmJt6DgQLqtyrI24eyTuTzf" +
        "ppY1aixL+EebPQcUuwJt5GmUW80CMkG9wA05Dr5vOCB96RhpINCxRsEWxBu6gkjA" +
        "FZCXXxXp4SYuAB34u+HrQuQnZFR2errOcPZoyYaHbVDfhLB7bF86vRldFKJRo/lW" +
        "UtypJlrH+3rAQUm3hrWoZeir6w5tMbnI3sgO3YLEzVSzLAFlOPv0664k4faI0oj1" +
        "QL5KIwH1tPMfb22NUglPIb9QTFobHkNKnyQG4uflpoOv0jCcPnrzhD4ZzA==";

    public static PrivateKey privateKey() throws Exception {
        byte[] der = Base64.decode(KEY, Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    public static X509Certificate certificate() throws Exception {
        byte[] der = Base64.decode(CERT, Base64.DEFAULT);
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }
}
