package com.yyj.platform.common.util

import java.io.StringReader
import java.util.regex.{Matcher, Pattern}

import com.haizhi.alg.common.base.log.LogFactory
import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.expression.operators.arithmetic.{Addition, Division, Multiplication, Subtraction}
import net.sf.jsqlparser.expression.operators.conditional.{AndExpression, OrExpression}
import net.sf.jsqlparser.expression.operators.relational.EqualsTo
import net.sf.jsqlparser.expression.{BinaryExpression, Expression, Function, Parenthesis}
import net.sf.jsqlparser.parser.CCJSqlParserManager
import net.sf.jsqlparser.schema.{Column, Table}
import net.sf.jsqlparser.statement.select._
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.StringUtils

import scala.collection.mutable._

/**
  * Created by yangyijun on 2019/8/18.
  */
object SQLUtils {

  private val logger = LogFactory.getLogger(SQLUtils.getClass)

  def getTableAndFields(sql: String): HashMap[String, Set[String]] = {
    val aliasToTable = new HashMap[String, String]()
    val tableToFields = new HashMap[String, Set[String]]()
    try {
      val parser: CCJSqlParserManager = new CCJSqlParserManager
      val select: Select = parser.parse(new StringReader(sql)).asInstanceOf[Select]
      val plain: PlainSelect = select.getSelectBody.asInstanceOf[PlainSelect]
      parseFrom(tableToFields, aliasToTable, plain)
      parseJoin(tableToFields, aliasToTable, plain)
      parseSelectItems(tableToFields, aliasToTable, plain)
      parseWhere(tableToFields, aliasToTable, plain)
      parseGroupBy(tableToFields, aliasToTable, plain)
    } catch {
      case e: JSQLParserException => {
        logger.error(s"parse SQL error,SQL:${sql}", e)
      }
    }
    return tableToFields
  }

  ///////////////////////
  // private functions
  ///////////////////////
  private def parseFrom(tableToFields: HashMap[String, Set[String]], aliasToTable: HashMap[String, String], plain: PlainSelect) {
    val table: Table = plain.getFromItem.asInstanceOf[Table]
    val fromTable: String = table.getName
    tableToFields.put(fromTable, new HashSet[String])
    if (table.getAlias != null) {
      aliasToTable.put(table.getAlias.getName, fromTable)
    }
  }

  private def parseJoin(tableToFields: HashMap[String, Set[String]], aliasToTable: HashMap[String, String], plain: PlainSelect) {
    val joins = plain.getJoins
    if (joins != null) {
      import scala.collection.JavaConversions._
      for (join <- joins) {
        val joinTable: Table = join.getRightItem.asInstanceOf[Table]
        val joinTableName: String = joinTable.getName
        tableToFields.put(joinTableName, tableToFields.getOrElse(joinTableName,new HashSet[String]))
        aliasToTable.put(joinTable.getAlias.getName, joinTableName)
        val expr: Expression = join.getOnExpression
        if (expr.isInstanceOf[Parenthesis]) {
          val expression: Expression = (expr.asInstanceOf[Parenthesis]).getExpression
          if (expression.isInstanceOf[AndExpression]) {
            val andExpression: AndExpression = expression.asInstanceOf[AndExpression]
            addFieldByBinaryOperator(tableToFields, aliasToTable, andExpression.getLeftExpression.asInstanceOf[EqualsTo])
            addFieldByBinaryOperator(tableToFields, aliasToTable, andExpression.getRightExpression.asInstanceOf[EqualsTo])
          } else if (expression.isInstanceOf[EqualsTo]) {
            addFieldByBinaryOperator(tableToFields, aliasToTable, expression.asInstanceOf[EqualsTo])
          }
        }
        else {
          addFieldByBinaryOperator(tableToFields, aliasToTable, expr.asInstanceOf[EqualsTo])
        }
      }
    }
  }

  private def parseSelectItems(tableToFields: HashMap[String, Set[String]], aliasToTable: HashMap[String, String], plain: PlainSelect) {
    val selectItems = plain.getSelectItems
    import scala.collection.JavaConversions._
    for (selectItem <- selectItems) {
      val item: SelectExpressionItem = selectItem.asInstanceOf[SelectExpressionItem]
      val expr: Expression = item.getExpression
      addField(expr, aliasToTable, tableToFields)
    }
  }

  private def parseWhere(tableToFields: HashMap[String, Set[String]], aliasToTable: HashMap[String, String], plain: PlainSelect) {
    val where = plain.getWhere
    if (where != null) {
      if (aliasToTable.isEmpty) {
        if (where.isInstanceOf[Parenthesis]) {
          val expression: Expression = where.asInstanceOf[Parenthesis].getExpression
          parseParenthesis(expression, tableToFields, aliasToTable)
        } else {
          addFieldByBinaryOperator(tableToFields, aliasToTable, where.asInstanceOf[BinaryExpression])
        }
      } else {
        addFieldByFunction(where.toString, aliasToTable, tableToFields)
      }
    }
  }

  private def parseParenthesis(expression: Expression, tableToFields: HashMap[String, Set[String]], aliasToTable: HashMap[String, String]): Unit ={
    if (expression.isInstanceOf[Parenthesis]) {
      parseParenthesis(expression.asInstanceOf[Parenthesis].getExpression, tableToFields, aliasToTable)
    } else if (expression.isInstanceOf[AndExpression]) {
      val andExpression: AndExpression = expression.asInstanceOf[AndExpression]
      if(andExpression.getLeftExpression.isInstanceOf[Parenthesis]) {
        parseParenthesis(andExpression.getLeftExpression.asInstanceOf[Parenthesis].getExpression, tableToFields, aliasToTable)
      } else if (andExpression.isInstanceOf[BinaryExpression]){
        addFieldByBinaryOperator(tableToFields, aliasToTable, andExpression.getLeftExpression.asInstanceOf[BinaryExpression])
      } else if (expression.isInstanceOf[EqualsTo]) {
        addFieldByBinaryOperator(tableToFields, aliasToTable, andExpression.getLeftExpression.asInstanceOf[EqualsTo])
      }

      if(andExpression.getRightExpression.isInstanceOf[Parenthesis]) {
        parseParenthesis(andExpression.getRightExpression.asInstanceOf[Parenthesis].getExpression, tableToFields, aliasToTable)
      } else if (andExpression.isInstanceOf[BinaryExpression]){
        addFieldByBinaryOperator(tableToFields, aliasToTable, andExpression.getRightExpression.asInstanceOf[BinaryExpression])
      } else if (expression.isInstanceOf[EqualsTo]) {
        addFieldByBinaryOperator(tableToFields, aliasToTable, andExpression.getRightExpression.asInstanceOf[EqualsTo])
      }
    } else if (expression.isInstanceOf[OrExpression]) {
      val orExpression: OrExpression = expression.asInstanceOf[OrExpression]
      if(orExpression.getLeftExpression.isInstanceOf[Parenthesis]) {
        parseParenthesis(orExpression.getLeftExpression.asInstanceOf[Parenthesis].getExpression, tableToFields, aliasToTable)
      } else if (orExpression.isInstanceOf[BinaryExpression]){
        addFieldByBinaryOperator(tableToFields, aliasToTable, orExpression.getLeftExpression.asInstanceOf[BinaryExpression])
      } else if (expression.isInstanceOf[EqualsTo]) {
        addFieldByBinaryOperator(tableToFields, aliasToTable, orExpression.getLeftExpression.asInstanceOf[EqualsTo])
      }

      if(orExpression.getRightExpression.isInstanceOf[Parenthesis]) {
        parseParenthesis(orExpression.getRightExpression.asInstanceOf[Parenthesis].getExpression, tableToFields, aliasToTable)
      }  else if (orExpression.isInstanceOf[BinaryExpression]){
        addFieldByBinaryOperator(tableToFields, aliasToTable, orExpression.getRightExpression.asInstanceOf[BinaryExpression])
      } else if (orExpression.isInstanceOf[EqualsTo]) {
        addFieldByBinaryOperator(tableToFields, aliasToTable, orExpression.getRightExpression.asInstanceOf[EqualsTo])
      }
    } else if (expression.isInstanceOf[BinaryExpression]) {
      addFieldByBinaryOperator(tableToFields, aliasToTable, expression.asInstanceOf[BinaryExpression])
    } else if (expression.isInstanceOf[EqualsTo]) {
      addFieldByBinaryOperator(tableToFields, aliasToTable, expression.asInstanceOf[EqualsTo])
    }
  }

  private def parseGroupBy(tableToFields: HashMap[String, Set[String]], aliasToTable: HashMap[String, String], plain: PlainSelect) {
    val groupBy = plain.getGroupByColumnReferences
    if (CollectionUtils.isNotEmpty(groupBy)) {
      import scala.collection.JavaConversions._
      for (expr <- groupBy) {
        addField(expr, aliasToTable, tableToFields)
      }
    }
  }

  private def addFieldByBinaryOperator(tableToFields: HashMap[String, Set[String]], aliasToTable: HashMap[String, String], binaryExpression: BinaryExpression) {
    addField(binaryExpression.getLeftExpression, aliasToTable, tableToFields)
    addField(binaryExpression.getRightExpression, aliasToTable, tableToFields)
  }

  private def addField(expr: Expression, aliasToTable: HashMap[String, String], tableToFields: HashMap[String, Set[String]]) {
    if (expr.isInstanceOf[Column]) {
      addFieldByColumn(expr.asInstanceOf[Column], aliasToTable, tableToFields)
    } else if (expr.isInstanceOf[Function]) {
      addFieldByFunction(expr.asInstanceOf[Function], aliasToTable, tableToFields)
    } else if (expr.isInstanceOf[Division]) {
      addFieldByBinaryOperator(tableToFields, aliasToTable, expr.asInstanceOf[Division])
    } else if (expr.isInstanceOf[Multiplication]) {
      addFieldByBinaryOperator(tableToFields, aliasToTable, expr.asInstanceOf[Multiplication])
    } else if (expr.isInstanceOf[Subtraction]) {
      addFieldByBinaryOperator(tableToFields, aliasToTable, expr.asInstanceOf[Subtraction])
    } else if (expr.isInstanceOf[Addition]) {
      addFieldByBinaryOperator(tableToFields, aliasToTable, expr.asInstanceOf[Addition])
    } else if (expr.isInstanceOf[Parenthesis]) {
      addField((expr.asInstanceOf[Parenthesis]).getExpression, aliasToTable, tableToFields)
    } else if (expr.isInstanceOf[EqualsTo]) {
      addFieldByBinaryOperator(tableToFields, aliasToTable, expr.asInstanceOf[BinaryExpression])
    }
  }

  private def addFieldByColumn(column: Column, aliasToTable: HashMap[String, String], tableToFields: HashMap[String, Set[String]]) {
    val tableAlias = column.getTable.getName
    val set = if (StringUtils.isBlank(tableAlias)) {
      tableToFields.getOrElse(tableToFields.keySet.head, Set())
    } else {
      tableToFields.getOrElse(aliasToTable.getOrElse(tableAlias, tableAlias), Set())
    }
    set.add(column.getColumnName)
  }

  private def addFieldByFunction(expression: String, aliasToTable: HashMap[String, String], tableToFields: HashMap[String, Set[String]]) {
    val matcher: Matcher = Pattern.compile("\\w+\\.\\w+").matcher(expression)
    while (matcher.find) {
      val str: String = matcher.group
      val tableAlias: String = StringUtils.substringBefore(str, ".")
      if(aliasToTable.get(tableAlias).isEmpty && tableToFields.get(tableAlias).isEmpty){
        tableToFields.put(tableAlias, Set())
      }
      val set: Set[String] = tableToFields.getOrElse(aliasToTable.getOrElse(tableAlias, tableAlias),Set())
      set.add(StringUtils.substringAfter(str, "."))
    }
  }

  private def addFieldByFunction(expression: Function, aliasToTable: HashMap[String, String], tableToFields: HashMap[String, Set[String]]) {
    if (aliasToTable.isEmpty) {
      val exprs = expression.getParameters.getExpressions()
      for (i <- 0 until exprs.size()) {
        addField(exprs.get(i), aliasToTable, tableToFields)
      }
    } else {
      addFieldByFunction(expression.toString, aliasToTable, tableToFields)
    }
  }
}
