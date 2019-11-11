package com.yyj.platform.common.util

import org.apache.log4j.{Level, Logger}

/**
 * Created by yangyijun on 2019/10/9.
 */
object LogUtils {
  def setLevel(): Unit = {
    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
  }

  def setLevel(level: Level): Unit = {
    Logger.getLogger("org").setLevel(level)
    Logger.getLogger("org.apache.spark").setLevel(level)
  }
}
