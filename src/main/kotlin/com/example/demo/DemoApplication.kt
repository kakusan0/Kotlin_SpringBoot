package com.example.demo

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
@MapperScan("com.example.demo.mapper")
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
