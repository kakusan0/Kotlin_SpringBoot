package com.example.demo.mybatis

import com.example.demo.util.EncryptionUtils
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet

class EncryptedStringTypeHandler : BaseTypeHandler<String>() {
    override fun setNonNullParameter(
        ps: PreparedStatement,
        i: Int,
        parameter: String,
        jdbcType: JdbcType?
    ) {
        ps.setString(i, EncryptionUtils.encrypt(parameter))
    }

    override fun getNullableResult(rs: ResultSet, columnName: String): String? {
        return EncryptionUtils.decrypt(rs.getString(columnName))
    }

    override fun getNullableResult(rs: ResultSet, columnIndex: Int): String? {
        return EncryptionUtils.decrypt(rs.getString(columnIndex))
    }

    override fun getNullableResult(cs: CallableStatement, columnIndex: Int): String? {
        return EncryptionUtils.decrypt(cs.getString(columnIndex))
    }
}
