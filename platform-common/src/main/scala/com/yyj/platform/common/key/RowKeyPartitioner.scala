package com.yyj.platform.common.key

import java.nio.charset.Charset
import java.util

import scala.util.control.Breaks._

/**
  * Created by yangyijun on 2019/8/8.
  */
object RowKeyPartitioner {

  /**
    * Get partitions split keys.
    *
    * @param logicPartitions
    * @param physicsPartitions
    * @return
    */
  def getSplitKeysBytes(logicPartitions: Int, physicsPartitions: Int): Array[Array[Byte]] = {
    val list = getSplitKeys(logicPartitions, physicsPartitions)
    val splitKeys = new Array[Array[Byte]](list.size)
    for (i <- 0 until list.size()) {
      splitKeys(i) = list.get(i).getBytes(Charset.forName("UTF-8"))
    }
    splitKeys
  }

  /**
    * Get partitions split keys.
    *
    * @param logicPartitions
    * @param physicsPartitions
    * @return
    */
  def getSplitKeys(logicPartitions: Int, physicsPartitions: Int): util.List[String] = {
    if (logicPartitions <= 0 || physicsPartitions <= 0) throw new IllegalArgumentException
    val interval = logicPartitions / physicsPartitions
    val result = new util.ArrayList[String]
    for (i <- 0 until logicPartitions) {
      breakable {
        if (i % interval == 0) {
          if (i == 0) {
            break
          }
          result.add(KeyUtils.formatPlaceHolder(if (i == 0) 0 else i - 1, 3))
        }
      }
    }
    if (result.size == physicsPartitions) {
      result.remove(result.size - 1)
    }
    result
  }
}
