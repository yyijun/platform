package com.yyj.platform.common.util

import java.math.BigInteger

/**
  * Created by yangyijun on 2019/8/11.
  */
object MD5Utils {

  type VertexId2 = (Long, Long) // 两段 Long 表示节点的id

  def md5ToVertexId2(md5: String): VertexId2 = {
    val md51 = md5.substring(0, 16)
    val md52 = md5.substring(16, 32)
    val long1 = new BigInteger(md51, 16).longValue()
    val long2 = new BigInteger(md52, 16).longValue()
    (long1, long2)
  }

  def md5ToLong(md5: String): Long = BigInt(md5.substring(0, 32), 32).toLong

  def vertexId2ToMd5(vid: VertexId2): String = longToMd5(vid._1) + longToMd5(vid._2)

  def md5(s: String): String = {
    val bytes = java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"))
    val hex_digest = Array[Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    val rs = new StringBuffer()
    for (i <- 0.to(15)) {
      rs.append(hex_digest(bytes(i) >>> 4 & 0xf))
      rs.append(hex_digest(bytes(i) & 0xf))
    }
    rs.toString
  }

  def longToMd5(long: Long): String = {
    val s = long.toHexString
    ("0" * (16 - s.length) + s).toUpperCase()
  }

  def longToMd5(long: Any): String = {
    long match {
      case tuple: (Long, Long) => longToMd5(tuple._1) + longToMd5(tuple._2)
      case l: Long => longToMd5(l)
      case _ => ""
    }
  }

}
