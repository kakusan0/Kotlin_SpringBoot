package com.example.demo.job

import com.example.demo.service.MyDnsUpdateService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MyDnsUpdateJob(
    private val myDnsUpdateService: MyDnsUpdateService
) : Job {

    private val logger = LoggerFactory.getLogger(MyDnsUpdateJob::class.java)

    override fun execute(context: JobExecutionContext) {
        logger.info("MyDNS update job triggered at ${java.time.LocalDateTime.now()}")
        val success = myDnsUpdateService.updateDynamicDns()
        if (success) {
            logger.info("MyDNS update job completed successfully")
        } else {
            logger.warn("MyDNS update job completed with errors")
        }
    }
}

