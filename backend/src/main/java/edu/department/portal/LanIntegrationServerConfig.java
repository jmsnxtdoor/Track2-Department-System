package edu.department.portal;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(name = "track2.integration.lan.enabled", havingValue = "true")
class LanIntegrationServerConfig {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> lanIntegrationConnector(
            @Value("${track2.integration.lan.bind-address}") String bindAddress,
            @Value("${track2.integration.lan.port}") int port,
            @Value("${track2.integration.lan.keystore}") String keyStorePath,
            @Value("${track2.integration.lan.keystore-password}") String keyStorePassword) {
        validateConfiguration(bindAddress, port, keyStorePath, keyStorePassword);
        return factory -> factory.addAdditionalTomcatConnectors(
                createHttpsConnector(bindAddress, port, keyStorePath, keyStorePassword));
    }

    @Bean
    FilterRegistrationBean<LanIntegrationRouteFilter> lanIntegrationRouteFilter(
            @Value("${track2.integration.lan.port}") int port) {
        FilterRegistrationBean<LanIntegrationRouteFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LanIntegrationRouteFilter(port));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static boolean isPrivateIpv4(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                return false;
            }
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 10
                    || first == 172 && second >= 16 && second <= 31
                    || first == 192 && second == 168;
        } catch (java.net.UnknownHostException invalidAddress) {
            return false;
        }
    }

    private static void validateConfiguration(String bindAddress, int port, String keyStorePath, String password) {
        if (!isPrivateIpv4(bindAddress)) {
            throw new IllegalStateException("LAN integration must bind to an explicitly configured private IPv4 address.");
        }
        if (port < 1024 || port > 65535) {
            throw new IllegalStateException("LAN integration port must be between 1024 and 65535.");
        }
        if (keyStorePath == null || keyStorePath.isBlank() || !Files.isRegularFile(Path.of(keyStorePath))) {
            throw new IllegalStateException("A readable PKCS12 certificate keystore is required for LAN integration.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("A keystore password is required for LAN integration.");
        }
    }

    private static Connector createHttpsConnector(
            String bindAddress, int port, String keyStorePath, String keyStorePassword) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(port);
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setProperty("address", bindAddress);
        connector.setProperty("SSLEnabled", "true");

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setProtocols("TLSv1.3,TLSv1.2");
        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        certificate.setCertificateKeystoreFile(keyStorePath);
        certificate.setCertificateKeystorePassword(keyStorePassword);
        certificate.setCertificateKeystoreType("PKCS12");
        sslHostConfig.addCertificate(certificate);
        connector.addSslHostConfig(sslHostConfig);
        return connector;
    }
}
