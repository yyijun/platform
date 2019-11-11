package com.yyj.platform.common.util

import org.json4s.JValue

import scala.io.Source
import scala.reflect.ClassTag

/**
  * Created by yangyijun on 2019/5/23.
  */
object FileUtils {
  def readFile2String(path: String): String = {
    val source = Source.fromFile(path, "UTF-8")
    val lines = source.getLines()
    val sb = new StringBuilder()
    lines.foreach(line => {
      sb.append(line)
    })
    source.close()
    sb.toString()
  }

  def readFile2JValue(path: String): JValue = {
    JValueUtils.parseJson(readFile2String(path))
  }

  def usingWithClose[A <: {def close() : Unit}, B](resource: A)(f: A => B): B =
    try {
      f(resource)
    } finally {
      resource.close()
    }

  def fromResourceFile[T: ClassTag](name: String, classTag: T, encoder: String, pretty: Boolean): String ={
    if(pretty){
      Source.fromFile(classTag.getClass.getClassLoader.getResource(name).getFile, encoder).getLines().mkString("\n")
    } else {
      Source.fromFile(classTag.getClass.getClassLoader.getResource(name).getFile, encoder).getLines().mkString
    }
  }
}
