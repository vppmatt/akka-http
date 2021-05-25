import javax.net.ssl.*;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class UntrustedServerCertificateUtility {

    public static SSLContext getSSLContext() {

        try {
             SSLContext sslContext = SSLContext.getInstance("TLS");

            sslContext.init(null, new TrustManager[]{new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                        throws CertificateException {
                    // empty
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                        throws CertificateException {
                    // empty
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                        throws CertificateException {
                    // empty
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                        throws CertificateException {
                    // empty
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    // empty
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    // empty
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);

            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
            return null;
        }

    }

}
