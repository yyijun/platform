package com.yyj.platform.common.log

/**
 * Created by yangyijun on 2019/4/4.
 */
object LogFactory {
  def getLogger(clazz: Class[_]): Log = {
    new Log(clazz)
  }
}
