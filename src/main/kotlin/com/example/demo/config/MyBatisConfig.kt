package com.example.demo.config

import org.apache.ibatis.session.SqlSessionFactory
import org.mybatis.spring.SqlSessionFactoryBean
import org.mybatis.spring.SqlSessionTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import javax.sql.DataSource

@Configuration
class MyBatisConfig(
    private val dataSource: DataSource
) {
    @Bean
    fun sqlSessionFactory(): SqlSessionFactory {
        val factoryBean = SqlSessionFactoryBean()
        factoryBean.setDataSource(dataSource)
        // 明示的にクラスパス上の mapper/*.xml のみを対象にする
        val resolver = PathMatchingResourcePatternResolver()
        factoryBean.setMapperLocations(*resolver.getResources("classpath*:mapper/*.xml"))
        val configuration = org.apache.ibatis.session.Configuration()
        configuration.isMapUnderscoreToCamelCase = true
        configuration.isLazyLoadingEnabled = true
        configuration.isAggressiveLazyLoading = false
        configuration.defaultFetchSize = 100
        configuration.defaultStatementTimeout = 30
        factoryBean.setConfiguration(configuration)
        return factoryBean.`object`!!
    }

    @Bean
    fun sqlSessionTemplate(sqlSessionFactory: SqlSessionFactory): SqlSessionTemplate {
        return SqlSessionTemplate(sqlSessionFactory)
    }
}
