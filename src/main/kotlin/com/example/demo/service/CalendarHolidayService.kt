package com.example.demo.service

import com.example.demo.mapper.CalendarHolidayMapper
import com.example.demo.model.CalendarHoliday
import com.example.demo.util.dbCall
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CalendarHolidayService(
    private val calendarHolidayMapper: CalendarHolidayMapper
) {
    /**
     * 指定した年の祝日一覧を取得（キャッシュ有効）
     */
    @Cacheable("holidays", key = "#year")
    fun getHolidaysByYear(year: Int): List<CalendarHoliday> {
        return dbCall("selectByYear", year) { calendarHolidayMapper.selectByYear(year) }
    }

    /**
     * 日付範囲で祝日を取得
     */
    fun getHolidaysByRange(from: LocalDate, to: LocalDate): List<CalendarHoliday> {
        require(!from.isAfter(to)) { "from は to より後ろにできません" }
        return dbCall("selectByDateRange", from, to) { calendarHolidayMapper.selectByDateRange(from, to) }
    }

    /**
     * 年指定で祝日をMap形式で取得（フロントエンド用、キャッシュ有効）
     * キー: 日付文字列(YYYY-MM-DD)、値: 祝日名
     */
    @Cacheable("holidaysMap", key = "#year")
    fun getHolidaysMapByYear(year: Int): Map<String, String> {
        val holidays = getHolidaysByYear(year)
        return holidays.associate { it.holidayDate.toString() to it.name }
    }

    /**
     * 特定の日付が祝日かどうか確認
     */
    fun isHoliday(date: LocalDate): Boolean {
        return dbCall("selectByDate", date) { calendarHolidayMapper.selectByDate(date) } != null
    }

    /**
     * 祝日を追加（キャッシュをクリア）
     */
    @Transactional
    @CacheEvict(value = ["holidays", "holidaysMap"], allEntries = true)
    fun addHoliday(date: LocalDate, name: String): CalendarHoliday {
        val holiday = CalendarHoliday(
            holidayDate = date,
            name = name,
            year = date.year
        )
        dbCall("insert", date, name) { calendarHolidayMapper.insert(holiday) }
        return holiday
    }

    /**
     * 祝日を更新（キャッシュをクリア）
     */
    @Transactional
    @CacheEvict(value = ["holidays", "holidaysMap"], allEntries = true)
    fun updateHoliday(id: Long, name: String): Int {
        return dbCall("update", id, name) {
            calendarHolidayMapper.update(CalendarHoliday(id = id, holidayDate = LocalDate.now(), name = name, year = 0))
        }
    }

    /**
     * 祝日を削除（キャッシュをクリア）
     */
    @Transactional
    @CacheEvict(value = ["holidays", "holidaysMap"], allEntries = true)
    fun deleteHoliday(id: Long): Int {
        return dbCall("deleteById", id) { calendarHolidayMapper.deleteById(id) }
    }
}

