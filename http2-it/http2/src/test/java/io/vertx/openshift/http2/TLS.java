package io.vertx.openshift.http2;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class TLS {

  public static final String KEY= "-----BEGIN PRIVATE KEY-----\n" +
    "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCaxywB+KQ9+HJA\n" +
    "Oe2UdIro1y9Z+4VFl4N/8nhbvtsS/Xs2TJJ1H6EL6+x7ni4T14GUCScKVEYIgwNi\n" +
    "HVMpXhgxlYRHnxTxOlgQZCROwNVxMMEmLrxq4NWOwGbdQywJ7hgG3ro26rM/QzRw\n" +
    "LhRbCR+gsXdsUvcD/nrtlD24z/RfBwNoeTzY7s4pqBVqexHtTwpsbrTiKtZg57kv\n" +
    "0KIlGIoB2VOeEo+WBgzZ/6B/WPibnil8oEx2TcbJh4VvzqtNgG+yAitYncmwJ5Z3\n" +
    "qFVEeE2rKSxijOLWhk0gXG8uryGFi3gL+qiUjXUFb2IZx0WLd11a3MtavH1onQyT\n" +
    "YrDlczHHAgMBAAECgf8Ujl9J47vsfxKtdQQYs2G/itS/HqiUlYrhc1KI3NA7tW6n\n" +
    "x4OsCodZDfxabqev+u86ufx1YrvqZXwNi9d/pv+Plkv7NyWQ5C7N/n7T9shiQBdm\n" +
    "+s4LY9D5JJSEzmcK6pRGVU5l/MXRoRtVysjpTkzlCqzRtjMYVLP+2bFY2kWaWR8m\n" +
    "F1BYIPjhtDJto6dXP/yukRSoR9F/q5+c90U5AqokxxQksdMLP2vlmK1J8O3yRKGi\n" +
    "u5yphkQW+82ybOZnE99Mkp35qUpDleeFp04Itlg0drxdfA9QgR1ndeczq4agRn8X\n" +
    "O8n2u6LELOELs1m1eYeeFKDcaXTTHJN7IunvBWECgYEA0mwI/m932vaDDfpK6sOt\n" +
    "grL8mDg3XNc+2V+NEPSX5jp5JNtN/HZn+cP++Ooumyaaxs/AjR80r8ttPGXj+7E6\n" +
    "Js10FU7AMCRRMMFYD3aEwvLVXcZJtr3waggyyC0C6kRjizk+0oG+xQ2Y3SxPlJYQ\n" +
    "KQL6cqs0M3a7QDcfBp/ZgC8CgYEAvE2o/uYhvmpKFYB61H0uHtNk1zZKPvbruAgP\n" +
    "vPInIp5mCbVqdvltZNIac72wQC/CzM/HS2yosHw5NNsETwXazZXpEemm5WcgbG5Z\n" +
    "Gvr/wAyYBJqaU0GDmH097VAuRBV503pp6yz+4Oozisr8PO8ws6YcuwINbc985gQT\n" +
    "hBjqKekCgYEA0IInY2C3Zq0xbi9f/0QJcmLENF44VfIgoCuz0GJfBs9YbfI2U/5M\n" +
    "x820oZkEt89IPctt5SlP/wbYZqocgLK8iei6p8aSYOIL5gEgrqnlonwYe8TaFJAg\n" +
    "ZCFdmMgphFRiQ3plSxkwHXl8yWV4MieFOe5umCQYJQr5QAee4eSSFRcCgYEAi6Xg\n" +
    "jeFn5wp2lMmqzklj2dKWd5C4sUd3+wxnd43yfhcQv2R+Z2uFuH6kxW9I9eE1y6TQ\n" +
    "PVyBIhmOZ2eCI4TJByyFJBavAnRftGqFxJ+e6fOtDcUGbHYqvP0s3wFWvoFazv56\n" +
    "7MF66JxnyyfMtvgAm0q3Be14vhZhn0gonQ/JIXkCgYEAkt9QhudWlcrqnobW3Rf9\n" +
    "/IVj7ZM5KpKmIRuZ+OlkCZfEbMuVToGWEeObMBYcfHpm8ELyCBVw0DZeYqZXXmYG\n" +
    "GdIS/1ojTDYeqriJIty8cMzBWMGcX+BcDb1XlguT4ze+yBKP3CQwm9OkKbncm8C4\n" +
    "6oJwX+C7M0xdpD8e6gBXDPM=\n" +
    "-----END PRIVATE KEY-----";

  public static String CERT = "Bag Attributes\n" +
    "    friendlyName: test-store\n" +
    "    localKeyID: 54 69 6D 65 20 31 34 33 36 32 37 36 31 36 38 39 30 30 \n" +
    "subject=/CN=localhost\n" +
    "issuer=/CN=localhost\n" +
    "-----BEGIN CERTIFICATE-----\n" +
    "MIICxzCCAa+gAwIBAgIEFC3x5TANBgkqhkiG9w0BAQsFADAUMRIwEAYDVQQDEwls\n" +
    "b2NhbGhvc3QwHhcNMTUwNzA3MTMzNTM4WhcNMTgwNzA2MTMzNTM4WjAUMRIwEAYD\n" +
    "VQQDEwlsb2NhbGhvc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCa\n" +
    "xywB+KQ9+HJAOe2UdIro1y9Z+4VFl4N/8nhbvtsS/Xs2TJJ1H6EL6+x7ni4T14GU\n" +
    "CScKVEYIgwNiHVMpXhgxlYRHnxTxOlgQZCROwNVxMMEmLrxq4NWOwGbdQywJ7hgG\n" +
    "3ro26rM/QzRwLhRbCR+gsXdsUvcD/nrtlD24z/RfBwNoeTzY7s4pqBVqexHtTwps\n" +
    "brTiKtZg57kv0KIlGIoB2VOeEo+WBgzZ/6B/WPibnil8oEx2TcbJh4VvzqtNgG+y\n" +
    "AitYncmwJ5Z3qFVEeE2rKSxijOLWhk0gXG8uryGFi3gL+qiUjXUFb2IZx0WLd11a\n" +
    "3MtavH1onQyTYrDlczHHAgMBAAGjITAfMB0GA1UdDgQWBBQxA6ZvimuLP/vf2oL6\n" +
    "abg8a7XjCTANBgkqhkiG9w0BAQsFAAOCAQEAi+ZXuQCUibdZcGFNEB2aUGNv9Ggw\n" +
    "O75RQRnHBdNcc+DiGxkjUi4OgcMVmOWdSqa3xVyI7+VHiOsKmtIp/hk6SbubBGDq\n" +
    "kiLr8D8D9yK5R4KQhTlc+Y+DZpnbwlhInia0+96DwGu26QD1JlPn/6jFWHP6u3hB\n" +
    "H8Vc6uASoNP8f1nPR+SFnFRK73XgkVrfKQeKeAESP/7DLontUP+BLgdQuBbWC75t\n" +
    "FF5ns1nmn1T9HgnGYiu2yfpSOQB1YtpMRoG9F7q5ISUhBLhfLSlVCi/1WI2ADkfS\n" +
    "9/tDd4NhFYMNuCtO8gf1oskuzg7CNa/RQ23iGI1Lnbi64kdNKv/DRmSRAw==\n" +
    "-----END CERTIFICATE-----\n";

}
