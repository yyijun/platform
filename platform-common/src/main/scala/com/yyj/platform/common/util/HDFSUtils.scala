package com.yyj.platform.common.util

import java.io._

import com.yyj.platform.common.log.LogFactory
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, _}
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession
import org.json4s.JValue

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.control.Breaks._

/**
  * Created by zhoujiamu on 2017/8/23.
  */

object HDFSUtils {

  private val logger = LogFactory.getLogger(HDFSUtils.getClass)

  private val hdfs: FileSystem = FileSystem.get(new Configuration)

  def isDir(name: String): Boolean = hdfs.isDirectory(new Path(name))

  def isDir(path: Path): Boolean = hdfs.isDirectory(path)

  def isDir(hdfs: FileSystem, name: String): Boolean = hdfs.isDirectory(new Path(name))

  def isDir(hdfs: FileSystem, name: Path): Boolean = hdfs.isDirectory(name)

  def isFile(hdfs: FileSystem, name: String): Boolean = hdfs.isFile(new Path(name))

  def isFile(hdfs: FileSystem, name: Path): Boolean = hdfs.isFile(name)

  def createFile(hdfs: FileSystem, name: String): Boolean = hdfs.createNewFile(new Path(name))

  def createFile(name: Path): Boolean = hdfs.createNewFile(name)

  def createFolder(name: String): Boolean = hdfs.mkdirs(new Path(name))

  def createFolder(name: Path): Boolean = hdfs.mkdirs(name)

  def exists(name: String): Boolean = hdfs.exists(new Path(name))

  def exists(name: Path): Boolean = hdfs.exists(name)

  def rename(oldName: String, NewName: String): Boolean = hdfs.rename(new Path(oldName), new Path(NewName))

  def rename(oldName: Path, NewName: String): Boolean = hdfs.rename(oldName, new Path(NewName))

  def rename(oldName: String, NewName: Path): Boolean = hdfs.rename(new Path(oldName), NewName)

  def rename(oldName: Path, NewName: Path): Boolean = hdfs.rename(oldName, NewName)

  def rename(conf: Configuration, oldName: String, newName: String): Boolean = {
    val fs = FileSystem.get(conf)
    val oldPath = new Path(oldName);
    val newPath = new Path(newName);
    fs.rename(oldPath, newPath)
  }

  def rename(conf: Configuration, oldFile: Path, newName: String): Boolean = {
    val fs = FileSystem.get(conf)
    val newPath = new Path(newName);
    fs.rename(oldFile, newPath)
  }

  def renameDirFile(conf: Configuration, dir: String, newName: String): Boolean = {
    val fs = FileSystem.get(conf)
    val oldPath = new Path(dir);
    val files = fs.listStatus(oldPath)
    files.foreach(f => rename(conf, f.getPath, newName))
    true
  }

  def transport(inputStream: InputStream, outputStream: OutputStream): Unit = {
    val buffer = new Array[Byte](64 * 1000)
    var len = inputStream.read(buffer)
    while (len != -1) {
      outputStream.write(buffer, 0, len - 1)
      len = inputStream.read(buffer)
    }
    outputStream.flush()
    inputStream.close()
    outputStream.close()
  }

  def concat(conf: Configuration, target: String, srcs: String*): Unit = {
    val fs = FileSystem.get(conf)
    val targetPath = new Path(target);
    val srcPaths = new ArrayBuffer[Path]()
    srcs.foreach(src => srcPaths.+=(new Path(src)))
    fs.concat(targetPath, srcPaths.toArray)
  }

  class MyPathFilter extends PathFilter {
    override def accept(path: Path): Boolean = true
  }

  /**
    * create a target file and provide parent folder if necessary
    */
  def createLocalFile(fullName: String): File = {
    val target: File = new File(fullName)
    if (!target.exists) {
      val index = fullName.lastIndexOf(File.separator)
      val parentFullName = fullName.substring(0, index)
      val parent: File = new File(parentFullName)

      if (!parent.exists)
        parent.mkdirs
      else if (!parent.isDirectory)
        parent.mkdir

      target.createNewFile
    }
    target
  }

  /**
    * delete file in hdfs
    *
    * @return true: success, false: failed
    */
  def deleteFile(hdfs: FileSystem, path: String): Boolean = {
    if (isDir(hdfs, path))
      hdfs.delete(new Path(path), true) //true: delete files recursively
    else
      hdfs.delete(new Path(path), false)
  }

  def deleteFile(conf: Configuration, path: String): Boolean = {
    logger.info(s"Start deleting HDFS files [${path}]")
    val fs = FileSystem.get(conf)
    if (isDir(fs, path))
      fs.delete(new Path(path), true) //true: delete files recursively
    else
      fs.delete(new Path(path), false)
    logger.info(s"HDFS file [${path}] has been deleted")
    true
  }

  def deleteEmptyFile(conf: Configuration, path: String): Boolean = {
    logger.info(s"Start deleting hdfs directory [${path}] empty files ")
    val fs = FileSystem.get(conf)
    val files = fs.listStatus(new Path(path))
    for (f <- files) {
      val len = f.getLen
      if (0 == len) {
        deleteFile(conf, f.getPath.toString)
      }
    }
    val nonEmptyFile = fs.listStatus(new Path(path))
    if (nonEmptyFile.length == 0) {
      fs.deleteOnExit(new Path(path))
    }
    logger.info(s"directory [${path}] empty file has been deleted")
    true
  }


  /**
    * get all file children's full name of a hdfs dir, not include dir children
    *
    * @param fullName the hdfs dir's full name
    */
  def listChildren(hdfs: FileSystem, fullName: String, holder: ListBuffer[String]): ListBuffer[String] = {
    val filesStatus = hdfs.listStatus(new Path(fullName), new MyPathFilter)
    for (status <- filesStatus) {
      val filePath: Path = status.getPath
      if (isFile(hdfs, filePath))
        holder += filePath.toString
      else
        listChildren(hdfs, filePath.toString, holder)
    }
    holder
  }

  def copyFile(hdfs: FileSystem, source: String, target: String): Unit = {

    val sourcePath = new Path(source)
    val targetPath = new Path(target)

    if (!exists(targetPath))
      createFile(targetPath)

    val inputStream: FSDataInputStream = hdfs.open(sourcePath)
    val outputStream: FSDataOutputStream = hdfs.create(targetPath)
    transport(inputStream, outputStream)
  }

  def copyFolder(hdfs: FileSystem, sourceFolder: String, targetFolder: String): Unit = {
    val holder: ListBuffer[String] = new ListBuffer[String]
    val children: List[String] = listChildren(hdfs, sourceFolder, holder).toList
    for (child <- children)
      copyFile(hdfs, child, child.replaceFirst(sourceFolder, targetFolder))
  }

  def copyFileFromLocal(hdfs: FileSystem, localSource: String, hdfsTarget: String): Unit = {
    val targetPath = new Path(hdfsTarget)
    if (!exists(targetPath))
      createFile(targetPath)

    val inputStream: FileInputStream = new FileInputStream(localSource)
    val outputStream: FSDataOutputStream = hdfs.create(targetPath)
    transport(inputStream, outputStream)
  }

  def copyFileToLocal(hdfs: FileSystem, hdfsSource: String, localTarget: String): Unit = {
    val localFile: File = createLocalFile(localTarget)

    val inputStream: FSDataInputStream = hdfs.open(new Path(hdfsSource))
    val outputStream: FileOutputStream = new FileOutputStream(localFile)
    transport(inputStream, outputStream)
  }

  def copyFolderFromLocal(hdfs: FileSystem, localSource: String, hdfsTarget: String): Unit = {
    val localFolder: File = new File(localSource)
    val allChildren: Array[File] = localFolder.listFiles
    for (child <- allChildren) {
      val fullName = child.getAbsolutePath
      val nameExcludeSource: String = fullName.substring(localSource.length)
      val targetFileFullName: String = hdfsTarget + Path.SEPARATOR + nameExcludeSource
      if (child.isFile)
        copyFileFromLocal(hdfs, fullName, targetFileFullName)
      else
        copyFolderFromLocal(hdfs, fullName, targetFileFullName)
    }
  }

  def copyFolderToLocal(hdfs: FileSystem, hdfsSource: String, localTarget: String): Unit = {
    val holder: ListBuffer[String] = new ListBuffer[String]
    val children: List[String] = listChildren(hdfs, hdfsSource, holder).toList
    val hdfsSourceFullName = hdfs.getFileStatus(new Path(hdfsSource)).getPath.toString
    val index = hdfsSourceFullName.length
    for (child <- children) {
      val nameExcludeSource: String = child.substring(index + 1)
      val targetFileFullName: String = localTarget + File.separator + nameExcludeSource
      copyFileToLocal(hdfs, child, targetFileFullName)
    }
  }

  def read(spark: SparkSession, path: String): String = {
    val requestParam = spark.sparkContext.textFile(path)
    val sb = new StringBuilder()
    requestParam.collect().foreach(line => {
      sb.append(line)
    })
    sb.toString()
  }

  def read2JValue(spark: SparkSession, path: String): JValue = {
    JValueUtils.parseJson(read(spark, path))
  }

  /**
    * 读取本地的Json文件
    *
    * @param fileName
    */
  def readLocalJsonFile(fileName: String): JValue = {
    var jsonStr = "{}"
    try {
      val jsonFile = new File(fileName)
      val inputStream = new FileInputStream(jsonFile)
      val bufReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))
      val strBuffer = new StringBuffer

      breakable {
        while (true) {
          val line = bufReader.readLine()
          if (line == null) break()
          val word = line.replace("\n", "")
          if (word != null && word.nonEmpty)
            strBuffer.append(word)
        }
      }
      bufReader.close()
      jsonStr = strBuffer.toString
    } catch {
      case e: IOException =>
        e.printStackTrace()
    }
    JValueUtils.parseJson(jsonStr)
  }

  def shutdown(): Unit = {
    try {
      hdfs.close()
    } catch {
      case e: Exception =>
        hdfs.close()
    }
  }

  def deleteAndReNameFileAsTxt(sc: SparkContext, path: String): Unit = {
    deleteAndRename(sc, path, "txt")
  }

  def deleteAndReNameFileAsLog(sc: SparkContext, path: String): Unit = {
    deleteAndRename(sc, path, "log")
  }

  def deleteAndRename(sc: SparkContext, path: String, suffix: String): Unit = {
    HDFSUtils.deleteEmptyFile(sc.hadoopConfiguration, s"${path}")
    HDFSUtils.renameDirFile(sc.hadoopConfiguration, s"${path}", s"${path}.${suffix}")
    HDFSUtils.deleteFile(sc.hadoopConfiguration, s"${path}")
  }

}
