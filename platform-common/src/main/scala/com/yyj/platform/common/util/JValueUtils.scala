package com.yyj.platform.common.util

import java.text.{DateFormat, SimpleDateFormat}
import java.util.Date
import java.util.regex.Pattern

import com.alibaba.fastjson.JSON
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.yyj.platform.common.log.LogFactory
import org.json4s.JsonAST.{JField, JNothing, JObject, JString}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{compact, parse, render}
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Diff, JValue, ShortTypeHints}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by yangyijun on 2018/8/30.
  */
object JValueUtils {
  private val logger = LogFactory.getLogger(getClass)

  def checkParameter(args: Array[String]): JValue = {
    try {
      logger.info(s"start check input parameter,length [${args.length}],content [${args(0)}].")
      val inputParam = args(0)
      var jv: JValue = null
      if (inputParam.startsWith("/")) {
        logger.info(s"test model input parameters:$inputParam")
        jv = FileUtils.readFile2JValue(inputParam)
      } else if (inputParam.startsWith("{")) {
        logger.info(s"online model input parameters:${JSON.toJSONString(inputParam, false)}")
        jv = JValueUtils.parseJson(inputParam)
        logger.info("parse jValue finished.")
      }
      logger.info("check input parameter finished.")
      return jv
    } catch {
      case e: Exception => {
        logger.error("check input parameter error.", e)
        throw e
      }
    }
  }

  def getJsonValue(obj: JValue, key: String, default: String = ""): String = {
    var rs = default
    try {
      val find_field = obj \ key
      if (find_field != JNothing) {
        rs = find_field.values.toString
      }
    } catch {
      case e: Throwable => logger.warn("Invalid json [%s] value return default".format(jsonString(obj)))
    }
    rs
  }

  def checkJsonKey(obj: JValue, key: String): Boolean = {
    var rs = false
    obj findField { case (k, v) => k == key
    } match {
      case Some(v) => rs = true
      case None => rs = false
    }
    rs
  }

  def toJValue(array: Array[(String, String)]): JValue = {
    implicit val formats = Serialization.formats(ShortTypeHints(List()))
    var str = JObject()
    array.foreach(a => {
      if ("true".equals(a._2) || "false".equals(a._2))
        str ~= a._1 -> a._2.toBoolean
      else
        str ~= a
    })
    render(str)
  }

  def jsonString(obj: JValue): String = {
    compact(render(obj))
  }

  def jsonString(array: Array[(String, String)]): String = compact(toJValue(array))

  def parseJson(json_str: String): JValue = {
    parse(json_str)
  }

  def jsonMerge(jsonStr: String, key: String, value: String): String = {
    var newJson = jsonStr
    try {
      val jv = parse(jsonStr)
      val newStr = compact(render(key -> value))
      val lastJson = jv merge parse(newStr)
      newJson = compact(render(lastJson))
    }
    catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }


  def addJValue(jv: JValue, keyValue: (String, String)*): JValue = {
    var newJv = jv
    keyValue.foreach { case (key, value) => {
      val newStr = compact(render(key -> value))
      newJv = newJv merge parse(newStr)
    }
    }
    newJv
  }

  def removeField(jsonStr: String, key: String*): String = {
    var newJson = jsonStr
    try {
      val jv = parse(jsonStr)
      val newStr = jv.removeField(x => key.contains(x._1))
      newJson = compact(render(newStr))
    } catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }

  def selectField(jsonStr: String, key: String*): String = {
    var newJson = jsonStr
    try {
      val jv = parse(jsonStr)
      val newStr = jv.filterField(f => key.contains(f._1))
      newJson = compact(render(newStr))
    } catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }

  def selectField(jsonValue: JValue, key: String*): String = {
    val newStr = jsonValue.filterField(f => key.contains(f._1))
    compact(render(newStr))
  }

  def jsonMerge(jsonStr: String, keyValue: (String, String)*): String = {
    var newJson = jsonStr
    try {
      keyValue.foreach { case (k, v) => newJson = jsonMerge(newJson, k, v) }
    }
    catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }

  def hasJsonKey(jv: JValue, key: String): Boolean = !(jv \ key).equals(JNothing)

  def buildTupleJson[B <% JValue](tuple: (String, B)): JValue = {
    JObject(JField(tuple._1, tuple._2) :: Nil)
  }

  def jsonEqual(left: JValue, right: JValue): Boolean = {
    val Diff(changed, added, deleted) = left diff right
    if (changed != JNothing) {
      false
    } else if (added != JNothing) {
      false
    } else if (deleted != JNothing) {
      false
    } else {
      true
    }
  }

  // string formatted
  def moneyFormatted(money_str: String, default: Double): JValue = {
    val default_formatted = ("value" -> default) ~ ("unit" -> "元")
    if (money_str.trim() == "") {
      return default_formatted
    }
    try {
      val items = money_str.split('(')
      val money_num = items(0).toDouble
      var money_unit = "元"
      if (items.length >= 2) {
        val unit_items = items(1).split(')')
        money_unit = unit_items(0)
      }
      return ("value" -> money_num) ~ ("unit" -> money_unit)
    } catch {
      case e: Throwable => logger.warn("invalid money string format %s".format(money_str))
    }
    default_formatted
  }

  val format: DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  def getCurrentTime: String = format.format(new Date())

  def getCompanyKey(companyName: JValue): String = MD5Utils.md5(companyName.values.toString.trim())

  def getCompanyKey(company_name: String): String = MD5Utils.md5(company_name)

  def normCompanyName(companyName: String): String = {
    companyName.replace("(", "（").replace(")", "）")
  }

  def isNumeric(numberString: String): Boolean = {
    val numberPattern = Pattern.compile("-?[0-9]+\\.?[0-9]*")
    val matchs = numberPattern.matcher(numberString)
    matchs.matches()
    if (!matchs.matches()) false else true

  }

  def normCaseId(caseId: String): String = {
    var rs = caseId.replace('(', '（')
      .replace(')', '）')
      .replace('[', '（')
      .replace(']', '）')
      .replace('｛', '（')
      .replace('｝', '）')
      .replace('「', '（')
      .replace('」', '）')
    val num = "([0-9]+)".r
    val iter = num findAllIn rs
    while (iter.hasNext) {
      val term = iter.next()
      val ind = rs.indexOf(term)
      if (ind != -1) {
        val left_ind = ind
        val right_ind = ind + term.length
        if (right_ind <= rs.length) {
          val n = term.toLong.toString
          rs = rs.substring(0, left_ind) + n + rs.substring(right_ind)
        }
      }
    }
    rs
  }

  def trim(str: String): String = {
    str.replaceAll("\u00A0", " ").trim
  }

  def getJValue(obj: JValue, key: String): JValue = {
    try {
      val find_field = obj \ key
      if (find_field != JNothing) find_field else JNothing
    } catch {
      case e: Throwable => logger.warn("invalid json [%s] value return default".format(jValueToJson(obj)))
        JNothing
    }
  }

  def existsJValueKey(obj: JValue, key: String): Boolean = {
    var exists = false
    obj findField { case (k, v) => k == key
    } match {
      case Some(v) => exists = true
      case None => exists = false
    }
    exists
  }

  def hasJValueKey(jv: JValue, key: String): Boolean = {
    !(jv \ key).equals(JNothing)
  }

  def hasJsonKey(json: String, key: String): Boolean = {
    val jv: JValue = jsonToJValue(json)
    !(jv \ key).equals(JNothing)
  }

  def jValueToJson(obj: JValue): String = {
    compact(render(obj))
  }

  def jsonToJValue(jsonStr: String): JValue = {
    parse(jsonStr)
  }

  def tupleToJValue[B <% JValue](tuple: (String, B)): JValue = {
    JObject(JField(tuple._1, tuple._2) :: Nil)
  }

  def tupleToJson[B <% JValue](tuple: (String, B)): String = {
    compact(JObject(JField(tuple._1, tuple._2) :: Nil))
  }

  def isJValueEqual(left: JValue, right: JValue): Boolean = {
    val Diff(changed, added, deleted) = left diff right
    if (changed != JNothing) {
      false
    } else if (added != JNothing) {
      false
    } else if (deleted != JNothing) {
      false
    } else {
      true
    }
  }

  def arrayToJValue(array: Array[(String, String)]): JValue = {
    implicit val formats = Serialization.formats(ShortTypeHints(List()))
    var str = JObject()
    array.foreach(a => {
      if ("true".equals(a._2) || "false".equals(a._2))
        str ~= a._1 -> a._2.toBoolean
      else
        str ~= a
    })
    render(str)
  }

  def jsonMergeTuple(jsonStr: String, key: String, value: String): String = {
    var newJson = jsonStr
    try {
      val jv = parse(jsonStr)
      val newStr = compact(render(key -> value))
      val lastJson = jv merge parse(newStr)
      newJson = compact(render(lastJson))
    }
    catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }

  def jsonMergeTuples(jsonStr: String, keyValue: (String, String)*): String = {
    var newJson = jsonStr
    try {
      keyValue.foreach { case (k, v) => newJson = jsonMergeTuple(newJson, k, v) }
    }
    catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }

  def jValueMergeTuples(jv: JValue, keyValue: (String, String)*): JValue = {
    var newJv = jv
    keyValue.foreach { case (key, value) => {
      val newStr = compact(render(key -> value))
      newJv = newJv merge parse(newStr)
    }
    }
    newJv
  }

  def removeJsonField(jsonStr: String, key: String*): String = {
    var newJson = jsonStr
    try {
      val jv = parse(jsonStr)
      val newStr = jv.removeField(x => key.contains(x._1))
      newJson = compact(render(newStr))
    }
    catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }

  def removeJValueField(jv: JValue, key: String*): String = {
    try {
      val newStr: JValue = jv.removeField(x => key.contains(x._1))
      compact(render(newStr))
    }
    catch {
      case ex: Exception => ex.printStackTrace()
        "{}"
    }
  }

  def selectJsonField(jsonStr: String, key: String*): String = {
    var newJson = jsonStr
    try {
      val jv = parse(jsonStr)
      val newStr = jv.filterField(f => key.contains(f._1))
      newJson = compact(render(newStr))
    }
    catch {
      case ex: Exception => ex.printStackTrace()
    }
    newJson
  }

  def selectJValueField(jsonValue: JValue, key: String*): String = {
    val newStr = jsonValue.filterField(f => key.contains(f._1))
    compact(render(newStr))
  }

  def jsonStringToMap(json: String): Map[String, Object] = {
    implicit val formats = DefaultFormats
    jsonToJValue(json).asInstanceOf[Map[String, Object]]
  }

  def jsonStringToMutableMap(json: String): mutable.HashMap[String, String] = {
    implicit val formats = DefaultFormats
    jsonToJValue(json).extract[mutable.HashMap[String, String]]
  }

  def jsonToVo[T: Manifest](json: String): T = {
    implicit val formats = DefaultFormats
    JValueUtils.jsonToJValue(json).extract[T]
  }

  def parseJValue(j: JValue): String = {
    j match {
      case JString(l) => l.values.toString
      case JObject(l) => compact(render(l))
      case _ => j.values.toString
    }
  }

  def scalaComplexMapToJson(map: Map[String, Object]): String = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.writeValueAsString(map)
  }

  def jsonIsEmpty(json: String): Boolean = {
    jsonToJValue(json).values.asInstanceOf[Map[String, Object]].isEmpty
  }

  def jArrayToSet(jValue: JValue): Set[String] = {
    var set = Set[String]()
    jValue.children.foreach(jv => set = set ++ Set(jv.values.toString))
    set
  }

  /**
    * 生成Json格式
    *
    * @param map
    * @return
    */
  def mapToJson(map: Seq[(String, Any)]): String = {
    var jsonStr: JObject = JObject()
    map.foreach(x => {
      try {
        jsonStr = jsonStr ~ (x._1 -> parse(x._2.toString))
      }
      catch {
        case ex: Exception =>
          jsonStr = jsonStr ~ (x._1 -> x._2.toString)
          println(ex)
      }
    })
    compact(render(jsonStr))
  }

  /**
    * 生成Json格式
    *
    * @param map
    * @return
    */
  def seqToJson(map: Seq[(String, Any)]): String = {
    var jsonStr: JObject = JObject()
    map.foreach(x => {
      try {
        jsonStr = jsonStr ~ (x._1 -> x._2.toString)
      }
      catch {
        case ex: Exception =>
          println(ex)
      }
    })
    compact(render(jsonStr))
  }

  /**
    * json转List
    *
    * @param inJson
    * @return
    */
  def jsonToList(inJson: String, keyName: String): List[String] = {

    val resuBuffer = new ArrayBuffer[String]()
    try {
      val jsonMap = (parse(inJson) \ keyName).values.asInstanceOf[List[String]]
      resuBuffer ++= jsonMap
    }
    catch {
      case ex: Exception =>
        println(ex.printStackTrace())
    }
    resuBuffer.toList
  }

  /**
    * json转map
    *
    * @param inJson
    * @return
    */
  def jsonToMap(inJson: String): mutable.HashMap[String, String] = {
    val resuMap = new mutable.HashMap[String, String]()
    try {
      val temp = parse(inJson)
      val jsonMap = temp.values.asInstanceOf[Map[String, Any]]
      jsonMap.foreach(x => {
        val str = if (x._2.isInstanceOf[String] || x._2.isInstanceOf[BigInt]
          || x._2.isInstanceOf[Boolean] || x._2.isInstanceOf[Double]) x._2.toString
        else mapToJson(x._2.asInstanceOf[Map[String, String]].toSeq)
        resuMap.put(x._1, str)
      })
    }
    catch {
      case ex: Exception =>
        println(ex.printStackTrace())
        println(inJson)
    }
    resuMap
  }

  //  /**
  //    * Json字符串拼接
  //    *
  //    * @param line
  //    * @param repStr
  //    * @param key
  //    * @return
  //    */
  //  def jsonMerge(line: String, repStr: String, key: String): String = {
  //
  //    var newLine = line
  //    try {
  //      val jv = parse(line)
  //      val newTup = key -> repStr
  //      val newStr = compact(render(newTup))
  //      val lastJson = jv merge parse(newStr)
  //      newLine = compact(render(lastJson))
  //    }
  //    catch {
  //      case ex: Exception =>
  //        ex.printStackTrace()
  //    }
  //    newLine
  //  }

  /**
    * 输入时List
    *
    * @param line
    * @param repStr
    * @param key
    * @return
    */
  def jsonMerge(line: String, repStr: List[String], key: String): String = {

    var newLine = line
    try {
      val jv = parse(line)
      val newTup = key -> repStr
      val newStr = compact(render(newTup))
      val lastJson = jv merge parse(newStr)
      newLine = compact(render(lastJson))
    }
    catch {
      case ex: Exception =>
        ex.printStackTrace()
    }
    newLine
  }

  //
  //  /**
  //    * 提取Json中所需字段
  //    *
  //    * @param line
  //    * @param key
  //    * @return
  //    */
  //  def selectField(line: String, key: String*): String = {
  //    val newStr = parse(line).filterField(f => key.contains(f._1))
  //    compact(render(newStr))
  //  }

  /**
    * 提取Json中所需字段
    *
    * @param line
    * @param keyList
    * @return
    */
  def selectField(line: String, keyList: List[String]): String = {
    val newStr = parse(line).filterField(f => keyList.contains(f._1))
    compact(render(newStr))
  }

  def main(args: Array[String]): Unit = {
    val a = Array("name" -> "canyu", "is_person" -> "true")
    val b = jsonString(a)
    println(b)

    println(jsonMerge(b, "name", "zhoujiamu"))

    println(removeField(b, "name"))
    println(selectField(b, "name", "is_person"))

    println(MD5Utils.md5("长生生物股份有限公司"))

    println(MD5Utils.md5("高俊芳长生生物股份有限公司"))

  }

}
