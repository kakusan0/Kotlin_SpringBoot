package com.example.demo.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

@Service
class GeoIpCountryService(
    @Value("\${geoip.mmdb-path:}")
    private val dbPath: String,
    @Value("\${geoip.allowed-country-codes:JP}")
    private val allowedCodesCsv: String
) {
    private val log = LoggerFactory.getLogger(GeoIpCountryService::class.java)
    private val readerRef = AtomicReference<Any?>()
    private var readerClass: Class<*>? = null

    init { tryLoad() }

    private fun tryLoad() {
        if (dbPath.isBlank()) {
            log.info("GeoIP disabled: geoip.mmdb-path is blank")
            readerRef.set(null)
            return
        }
        val file = File(dbPath)
        if (!file.exists()) {
            log.warn("GeoIP database not found at {}. Service will be disabled.", dbPath)
            readerRef.set(null)
            return
        }
        try {
            readerClass = Class.forName("com.maxmind.geoip2.DatabaseReader")
            val builderClass = Class.forName("com.maxmind.geoip2.DatabaseReader\$Builder")
            val builder = builderClass.getConstructor(File::class.java).newInstance(file)
            val buildMethod = builderClass.getMethod("build")
            val reader = buildMethod.invoke(builder)
            readerRef.set(reader)
            log.info("GeoIP database loaded: {}", dbPath)
        } catch (e: ClassNotFoundException) {
            log.info("GeoIP dependency not present. Service disabled.")
            readerRef.set(null)
        } catch (e: Exception) {
            log.warn("Failed to load GeoIP database: {}", e.toString())
            readerRef.set(null)
        }
    }

    fun isEnabled(): Boolean = readerRef.get() != null

    fun lookupCountryCode(ip: String): String? {
        val reader = readerRef.get() ?: return null
        return try {
            val clean = ip.substringBefore('%')
            val addr = InetAddress.getByName(clean)
            val countryMethod = readerClass!!.getMethod("country", InetAddress::class.java)
            val response = countryMethod.invoke(reader, addr)
            val countryObj = response.javaClass.getMethod("getCountry").invoke(response)
            val isoCode = countryObj.javaClass.getMethod("getIsoCode").invoke(countryObj) as String?
            isoCode
        } catch (e: Exception) {
            log.debug("GeoIP lookup failed for {}: {}", ip, e.toString())
            null
        }
    }

    fun isAllowedCountry(ip: String): Boolean {
        val codes = allowedCodesCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
        val code = lookupCountryCode(ip)?.uppercase() ?: return true // unknown => allow
        return codes.contains(code)
    }
}
