package com.example.demo.mapper

import com.example.demo.model.AccessLog
import com.example.demo.model.IpLatestPath
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface AccessLogMapper {
    fun insert(record: AccessLog): Int

    // 指定したIP一覧に対する最新アクセスのパスを取得
    fun selectLatestPathByIps(@Param("ips") ips: List<String>): List<IpLatestPath>
}
