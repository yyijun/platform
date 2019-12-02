package com.yyj.platform.common.util

import java.io.IOException

import com.yyj.platform.common.key.RowKeyGetter
import com.yyj.platform.common.log.LogFactory
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.{HBaseConfiguration, HColumnDescriptor, HConstants, HTableDescriptor, NamespaceDescriptor, TableName}
import org.apache.hadoop.hbase.client.{BufferedMutatorParams, Connection, ConnectionFactory, Delete, Get, HBaseAdmin, Put, Result, Table}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.json4s.JsonAST.{JField, JObject, JString}
import org.json4s.JsonDSL._
import org.json4s.ShortTypeHints
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, HashMap}

/**
 * Created by yangyijun on 2019/8/18.
 */
object HBaseUtils {

  val logger = LogFactory.getLogger(this.getClass)

  def createHBaseClient(hosts: String, port: String): Connection = {
    val conf = HBaseConfiguration.create();
    conf.set(HConstants.ZOOKEEPER_QUORUM, hosts)
    conf.set(HConstants.ZOOKEEPER_CLIENT_PORT, port)
    conf.set(HConstants.ZOOKEEPER_ZNODE_PARENT, "/hbase")
    conf.setInt(HConstants.HBASE_RPC_TIMEOUT_KEY, 200000)
    conf.setInt(HConstants.HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD, 200000)
    conf.setInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, 30000)
    ConnectionFactory.createConnection(conf)
  }

  def createDatabase(connection: Connection, database: String): Boolean = {
    var success: Boolean = false
    var admin: HBaseAdmin = null
    try {
      admin = connection.getAdmin.asInstanceOf[HBaseAdmin]
      val namespaceDescriptor: NamespaceDescriptor = NamespaceDescriptor.create(database).build
      admin.createNamespace(namespaceDescriptor)
      success = true
      logger.info(s"Success to create database[${database}]")
    }
    catch {
      case e: IOException => {
        logger.error(s"Failed to create database[${database}]\n", e)
      }
    } finally {
      admin.close()
    }
    success
  }

  def deleteDatabase(connection: Connection, database: String) {
    var success: Boolean = false
    var admin: HBaseAdmin = null
    try {
      admin = connection.getAdmin.asInstanceOf[HBaseAdmin]
      val namespaceDescriptor: NamespaceDescriptor = NamespaceDescriptor.create(database).build
      admin.deleteNamespace(namespaceDescriptor.getName)
      success = true
      logger.info(s"Success to delete database[${database}]")
    }
    catch {
      case e: IOException => {
        logger.error(s"Failed to delete database[${database}]\n", e)
      }
    } finally {
      admin.close()
    }
    success
  }

  def deleteTable(connection: Connection, tablename: String): Unit = {
    val tableName = TableName.valueOf(tablename)
    val admin = connection.getAdmin
    if (admin.tableExists(tableName)) {
      if (admin.isTableEnabled(tableName)) {
        admin.disableTable(tableName)
      }
      admin.deleteTable(tableName)
    }
  }

  def createTable(conn: Connection, database: String, table: String, family: String): Boolean = {
    val admin = conn.getAdmin.asInstanceOf[HBaseAdmin]
    val exists: Boolean = admin.tableExists(TableName.valueOf(database, table))
    if (exists) {
      return true
    }
    val tableDesc = new HTableDescriptor(TableName.valueOf(database, table))
    val columnDescriptor: HColumnDescriptor = new HColumnDescriptor(family)
    columnDescriptor.setMaxVersions(1)
    tableDesc.addFamily(columnDescriptor)
    admin.createTable(tableDesc)
    true
  }

  def deleteRecord(table: Table, rowkey: String): Unit = {
    try {
      val d = new Delete(RowKeyGetter.getRowKey(rowkey).getBytes())
      val d2 = new Delete(rowkey.getBytes())
      val deletes = Array(d, d2)
      table.delete(deletes.toList)
      logger.info("delete record done.")
    } catch {
      case e: Exception => {
        logger.info("delete record error.", e)
      }
    }
  }

  def getByRowKeyAndFields(conn: Connection, namespace: String, tableName: String, rowKey: String, fields: Set[String]): mutable.HashMap[String, String] = {
    var hTable: Table = null
    hTable = conn.getTable(TableName.valueOf(namespace, tableName))
    val get = new Get(rowKey.getBytes());
    val r = hTable.get(get)
    val result = getRowByFields(r, fields)
    hTable.close()
    result
  }

  def getByRowKeyAndFields(hTable: Table, rowKey: String, fields: Set[String]): mutable.HashMap[String, String] = {
    val get = new Get(rowKey.getBytes());
    val r = hTable.get(get)
    getRowByFields(r, fields)
  }

  def closeTable(tables: HashMap[String, Table]): Unit = {
    tables.foreach(t => t._2.close())
  }


  def getHTable(conn: Connection, namespace: String, tableName: String): Table = {
    conn.getTable(TableName.valueOf(namespace, tableName))
  }

  def getHTableMap(conn: Connection, namespace: String, tables: Set[String]): mutable.HashMap[String, Table] = {
    val htableMap = new mutable.HashMap[String, Table]()
    tables.foreach(t => htableMap.put(t, getHTable(conn, namespace, t)))
    htableMap
  }

  def getByRowKey(conn: Connection, namespace: String, tableName: String, rowKey: String): mutable.HashMap[String, String] = {
    var hTable: Table = null
    hTable = conn.getTable(TableName.valueOf(namespace, tableName))
    val get = new Get(rowKey.getBytes());
    val r = hTable.get(get)
    val result = if (r == null || r.isEmpty) {
      val get2 = new Get(RowKeyGetter.getRowKey(rowKey).getBytes());
      val r2 = hTable.get(get2)
      getRow(r2)
    } else {
      getRow(r)
    }
    hTable.close()
    result
  }

  def getByRowKey(hTable: Table, rowKey: String): mutable.HashMap[String, String] = {
    val get = new Get(rowKey.getBytes());
    val r = hTable.get(get)
    if (r == null || r.isEmpty) {
      val get2 = new Get(RowKeyGetter.getRowKey(rowKey).getBytes());
      val r2 = hTable.get(get2)
      getRow(r2)
    } else {
      getRow(r)
    }
  }

  def getRowByFields(result: Result, fields: Set[String]): mutable.HashMap[String, String] = {
    val cellMap = new mutable.HashMap[String, String]()
    if (result.isEmpty) {
      return cellMap
    }
    for (cell <- result.listCells) {
      val qf: String = Bytes.toString(cell.getQualifierArray, cell.getQualifierOffset, cell.getQualifierLength)
      if (fields.contains(qf)) {
        val value: String = Bytes.toString(cell.getValueArray, cell.getValueOffset, cell.getValueLength)
        cellMap.put(qf, value)
      }
    }
    cellMap
  }

  def getRow(result: Result): mutable.HashMap[String, String] = {
    val cellMap = new mutable.HashMap[String, String]()
    if (result.isEmpty) {
      return cellMap
    }
    for (cell <- result.listCells) {
      val qf: String = Bytes.toString(cell.getQualifierArray, cell.getQualifierOffset, cell.getQualifierLength)
      val value: String = Bytes.toString(cell.getValueArray, cell.getValueOffset, cell.getValueLength)
      cellMap.put(qf, value)
    }
    cellMap
  }

  def bulkUpsert(conn: Connection, tableName: String, rows: Seq[HashMap[String, String]]) {
    bulkUpsert(conn, tableName, "objects", rows)
  }

  def bulkUpsert(conn: Connection, tableName: String, family: String, rows: Seq[HashMap[String, String]]) {
    var puts = mutable.LinkedList[Put]()
    for (row <- rows) {
      val put = new Put(RowKeyGetter.getRowKey(row.get("object_key").get).getBytes)
      for (entry <- row.entrySet) {
        val field = entry.getKey
        val value = entry.getValue
        put.addColumn(family.getBytes, Bytes.toBytes(field), value.toString.getBytes)
      }
      puts = puts.+:(put)
    }
    val params: BufferedMutatorParams = new BufferedMutatorParams(TableName.valueOf(tableName))
    val mutator = conn.getBufferedMutator(params)
    mutator.mutate(puts)
    mutator.flush
  }

  def insert(conn: Connection, tableName: String, family: String, row: HashMap[String, String]) {
    val put = new Put(row.get("object_key").get.getBytes())
    for (entry <- row.entrySet) {
      val field = entry.getKey
      val value = entry.getValue
      put.addColumn(family.getBytes, Bytes.toBytes(field), value.toString.getBytes)
    }
    val params: BufferedMutatorParams = new BufferedMutatorParams(TableName.valueOf(tableName))
    val mutator = conn.getBufferedMutator(params)
    mutator.mutate(put)
    mutator.flush
  }

  def fetchHBaseData(sc: SparkContext, conf: Configuration, tableName: String, column: Seq[String], family: String = "objects"): RDD[String] = {
    try {
      conf.set(TableInputFormat.INPUT_TABLE, tableName)
      val hBaseRDD = sc.newAPIHadoopRDD(conf, classOf[TableInputFormat], classOf[ImmutableBytesWritable], classOf[Result])
      val resultRDD = hBaseRDD.map {
        case (_, result) => {
          val ast_list = column.map(f => {
            implicit val formats = Serialization.formats(ShortTypeHints(List()))
            val value = bytes2String(result.getValue(family.getBytes, f.getBytes))
            JObject(List(JField(f, JString(value))))
          }).reduce(_ ~ _)
          compact(render(ast_list))
        }
      }
      resultRDD
    } catch {
      case e: Exception => {
        throw new Exception("HAIZHI TAG CALCULATE LOG ========> Fetch hbase data error.Error detail: " + e.getMessage)
      }
    }
  }

  def fetchHBaseData(sc: SparkContext, conf: Configuration, tableName: String, family: String): RDD[String] = {
    try {
      conf.set(TableInputFormat.INPUT_TABLE, tableName)
      val hBaseRDD = sc.newAPIHadoopRDD(conf, classOf[TableInputFormat], classOf[ImmutableBytesWritable], classOf[Result])
      val resultRDD = hBaseRDD.map { case (res, result) => {
        val columnMap = result.getFamilyMap(family.getBytes)
        val keys = columnMap.keySet().iterator()
        val column = ArrayBuffer.empty[String]
        while (keys.hasNext) {
          val key = bytes2String(keys.next())
          column += key
        }
        val ast_list = column.map(f => {
          implicit val formats = Serialization.formats(ShortTypeHints(List()))
          val value = bytes2String(result.getValue(family.getBytes, f.getBytes))
          val jv = JString(value)
          JObject(List(JField(f, jv)))
        }).reduce(_ ~ _)
        compact(render(ast_list))
      }
      }
      resultRDD
    } catch {
      case e: Exception => {
        logger.error("fetch hbase data error.", e)
        throw e
      }
    }
  }

  def asString(obj: Any): String = {
    if (obj != null & obj != None) obj.toString else ""
  }

  def bytes2String(byte: Array[Byte]): String = asString(Bytes.toString(byte))

}
