package com.yyj.platform.common.util

import java.util

import com.alibaba.fastjson.{JSON, TypeReference}

import scala.collection.mutable._

/**
 * Created by yangyijun on 2019/4/13.
 */
object JSONUtils {
  def toMap[T](bean: T): Map[String, AnyRef] = {
    if (null == bean) {
      return new HashMap[String, AnyRef]
    }
    val text: String = JSON.toJSONString(bean, false)
    JSON.parseObject(text, new TypeReference[Map[String, AnyRef]]() {})
  }

  def toListMap[T](beanList: List[T]): List[Map[String, AnyRef]] = {
    val text: String = JSON.toJSONString(beanList, false)
    JSON.parseObject(text, new TypeReference[List[Map[String, AnyRef]]]() {})
  }

  def jsonToMap(json: String): Map[String, AnyRef] = {
    JSON.parseObject(json, new TypeReference[Map[String, AnyRef]]() {})
  }

  def jsonToListMap(json: String): List[Map[String, AnyRef]] = {
    JSON.parseObject(json, new TypeReference[List[Map[String, AnyRef]]]() {})
  }

  def toListMap(obj: Any): util.ArrayList[Map[String, AnyRef]] = {
    if (null == obj) {
      return new util.ArrayList[Map[String, AnyRef]]
    }
    val json: String = JSON.toJSONString(obj, false)
    JSON.parseObject(json, new TypeReference[util.ArrayList[Map[String, AnyRef]]]() {})
  }

  def println[T](obj: T) {
    System.out.println(JSON.toJSONString(obj, true))
  }
}
