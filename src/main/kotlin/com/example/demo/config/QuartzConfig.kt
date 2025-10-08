package com.example.demo.config

import com.example.demo.job.MyDnsUpdateJob
import org.quartz.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QuartzConfig {

    @Bean
    fun myDnsUpdateJobDetail(): JobDetail {
        return JobBuilder.newJob(MyDnsUpdateJob::class.java)
            .withIdentity("myDnsUpdateJob")
            .withDescription("MyDNS IP address update job")
            .storeDurably()
            .build()
    }

    @Bean
    fun myDnsUpdateTrigger(myDnsUpdateJobDetail: JobDetail): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(myDnsUpdateJobDetail)
            .withIdentity("myDnsUpdateTrigger")
            .withDescription("Execute every 12 hours")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInHours(12)
                    .repeatForever()
            )
            .startNow()
            .build()
    }
}
