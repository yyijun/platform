package com.yyj.platform.common.log

import java.text.MessageFormat

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Logger

/**
 * Created by yangyijun on 2019/4/4.
 */
class Log extends Serializable {
  var logger: Logger = null

  def this(clazz: Class[_]) {
    this()
    this.logger = LogManager.getLogger(clazz).asInstanceOf[Logger]
  }

  def warn(s: String) {
    logger.warn(s)
  }

  def warn(pattern: String, arguments: AnyRef*) {
    logger.warn(format(pattern, arguments: _*))
  }

  def info(s: String) {
    logger.info(s)
  }

  def info(pattern: String, arguments: AnyRef*) {
    logger.info(format(pattern, arguments: _*))
  }

  def error(e: Throwable) {
    logger.error(e)
  }

  def error(s: String, e: Throwable) {
    logger.error(s, e)
  }

  def error(pattern: String, arguments: AnyRef*) {
    logger.error(format(pattern, arguments: _*))
  }

  def error(pattern: String, e: Throwable, arguments: AnyRef*) {
    logger.error(format(pattern, arguments), e)
  }

  private def format(pattern: String, arguments: AnyRef*): String = {
    MessageFormat.format(pattern, arguments: _*)
  }
}
