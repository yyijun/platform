package com.yyj.platform.common.util

import java.text.{DateFormat, SimpleDateFormat}
import java.util.Date

/**
 * Created by yangyijun on 2019/8/11.
 */
object DateUtils {

  val format: DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  def currentTime: String = format.format(new Date())

  def currentTimestamp: Long = new Date().getTime

  def string2date(string: String): Date = format.parse(string)

  def date2string(date: Date): String = format.format(date)

}
