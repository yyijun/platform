package com.yyj.platform.common.util

import com.yyj.platform.common.log.LogFactory
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}

/**
  * Created by yangyijun on 2019/9/6.
  */
object SparkUtils {

  private val logger = LogFactory.getLogger(SparkUtils.getClass)

  def writeDF2Csv(df: DataFrame, header: Boolean = true, delimiter: String = ",", path: String): Unit = {
    try {
      df.write.mode(SaveMode.Overwrite)
        .option("header", header)
        .option("delimiter", delimiter)
        .option("nullValue", "\\N")
        .option("quote", "").csv(path)
    } catch {
      case e: Exception => {
        logger.error(s"SparkUtils writeDF2Csv exception! path=[${path}]", e)
        throw e
      }
    }
    logger.info(s"SparkUtils writeDF2Csv finished! path=[${path}]")
  }

  def dF2CsvArrayHandle(df: DataFrame, delimiter: String = "&"): DataFrame = {
    import org.apache.spark.sql.types._
    val arrayCols = df.schema.fields.collect {
      case StructField(name, ArrayType(_, _), _, _) => name
    }
    if (null == arrayCols || arrayCols.length == 0) {
      return df
    }
    import org.apache.spark.sql.functions._
    var df2 = df
    val arrayColsHandleUDF = udf((vs: Seq[String]) => vs match {
      case null => null
      case _ => s"""[${vs.mkString(delimiter)}]"""
    })
    arrayCols.foreach(c => df2 = df2.withColumn(c, arrayColsHandleUDF(col(c))))
    df2
  }

  def getOrCreateSpark(appName: String, parallelism: String): SparkSession = {
    var spark: SparkSession = null
    try {
      logger.info("start create spark session.")
      spark = SparkSession.builder()
        .appName(appName)
        .config("spark.rdd.compress", "true")
        .config("spark.network.timeout", "300")
        .config("spark.default.parallelism", parallelism)
        .config("spark.sql.warehouse.dir", "/user/hive/warehouse")
        .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .config("spark.io.compression.codec", "org.apache.spark.io.SnappyCompressionCodec")
        .enableHiveSupport()
        .getOrCreate()
      logger.info("create spark session succeed.")
    } catch {
      case e: Exception => {
        logger.error("create spark session error", e)
        throw e
      }
    }
    spark
  }

  def clearCheckpoint(spark: SparkSession, checkpointDir: String): Boolean = {
    logger.info(s"delete checkpoint dir [${checkpointDir}]")
    HDFSUtils.deleteFile(spark.sparkContext.hadoopConfiguration, checkpointDir)
  }

  def setCheckpoint(spark: SparkSession, checkpointDir: String): Unit = {
    logger.info(s"set checkpoint dir [${checkpointDir}]")
    spark.sparkContext.setCheckpointDir(checkpointDir)
  }

  def closeResource(spark: SparkSession, checkpointDir: String): Unit = {
    logger.info(s"start clear checkpoint [${checkpointDir}] and close spark session")
    clearCheckpoint(spark, checkpointDir)
    spark.sparkContext.stop()
    spark.close()
    logger.info(s"clear checkpoint [${checkpointDir}] and close spark session finished.")
  }

  def writeFile(spark: SparkSession, content: String, path: String): Unit = {
    spark.sparkContext.parallelize(Seq(content), 1).saveAsTextFile(path)
    HDFSUtils.deleteAndReNameFileAsLog(spark.sparkContext, path)
  }

}
