package com.example.demo.service;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GeoIpCountryService {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(GeoIpCountryService.class);

    private final String dbPath;
    private final String allowedCodesCsv;

    private final AtomicReference<Object> readerRef = new AtomicReference<>();
    private Class<?> readerClass;

    public GeoIpCountryService(
            @Value("${geoip.mmdb-path:}") String dbPath,
            @Value("${geoip.allowed-country-codes:JP}") String allowedCodesCsv
    ) {
        this.dbPath = dbPath;
        this.allowedCodesCsv = allowedCodesCsv;
        tryLoad();
    }

    private void tryLoad() {
        if (dbPath == null || dbPath.isBlank()) {
            log.info("GeoIP disabled: geoip.mmdb-path is blank");
            readerRef.set(null);
            return;
        }
        File file = new File(dbPath);
        if (!file.exists()) {
            log.warn("GeoIP database not found at {}. Service will be disabled.", dbPath);
            readerRef.set(null);
            return;
        }
        try {
            readerClass = Class.forName("com.maxmind.geoip2.DatabaseReader");
            Class<?> builderClass = Class.forName("com.maxmind.geoip2.DatabaseReader$Builder");
            Object builder = builderClass.getConstructor(File.class).newInstance(file);
            Object reader = builderClass.getMethod("build").invoke(builder);
            readerRef.set(reader);
            log.info("GeoIP database loaded: {}", dbPath);
        } catch (ClassNotFoundException e) {
            log.info("GeoIP dependency not present. Service disabled.");
            readerRef.set(null);
        } catch (Exception e) {
            log.warn("Failed to load GeoIP database: {}", e.toString());
            readerRef.set(null);
        }
    }

    public boolean isEnabled() {
        return readerRef.get() != null;
    }

    public String lookupCountryCode(String ip) {
        Object reader = readerRef.get();
        if (reader == null) {
            return null;
        }
        try {
            String clean = ip.split("%", 2)[0];
            InetAddress addr = InetAddress.getByName(clean);
            Object response = readerClass.getMethod("country", InetAddress.class).invoke(reader, addr);
            Object countryObj = response.getClass().getMethod("getCountry").invoke(response);
            Object isoCode = countryObj.getClass().getMethod("getIsoCode").invoke(countryObj);
            return isoCode != null ? isoCode.toString() : null;
        } catch (Exception e) {
            log.debug("GeoIP lookup failed for {}: {}", ip, e.toString());
            return null;
        }
    }

    public boolean isAllowedCountry(String ip) {
        Set<String> codes = Stream.of(allowedCodesCsv.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        String code = lookupCountryCode(ip);
        if (code == null) {
            return true;
        }
        return codes.contains(code.toUpperCase());
    }
}
