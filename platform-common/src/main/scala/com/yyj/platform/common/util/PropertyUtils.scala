package com.yyj.platform.common.util

import java.io.{FileInputStream, InputStreamReader}
import java.util.Properties

import com.alibaba.fastjson.JSON
import com.yyj.platform.common.log.LogFactory


/**
 * Created by yangyijun on 2019/8/11.
 */
object PropertyUtils {

  private val logger = LogFactory.getLogger(getClass)

  def loadProperties(path: String): Properties = {
    val property = new java.util.Properties
    property.load(new InputStreamReader(new FileInputStream(path), "utf-8"))
    logger.info(s"properties:\n${JSON.toJSONString(property, true)}")
    property
  }
}
