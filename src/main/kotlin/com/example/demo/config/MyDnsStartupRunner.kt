package com.example.demo.config

import com.example.demo.service.MyDnsUpdateService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class MyDnsStartupRunner(
    private val myDnsUpdateService: MyDnsUpdateService
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(MyDnsStartupRunner::class.java)

    override fun run(args: ApplicationArguments) {
        logger.info("Executing MyDNS update on application startup")
        val success = myDnsUpdateService.updateDynamicDns()
        if (success) {
            logger.info("MyDNS startup update completed successfully")
        } else {
            logger.warn("MyDNS startup update completed with errors")
        }
    }
}

