package com.memsql.spark.etl.api

import org.apache.spark.sql.{SQLContext, DataFrame, Row}
import org.apache.spark.sql.types._
import org.apache.spark.rdd.RDD
import com.memsql.spark.etl.utils.{ByteUtils, PhaseLogger}

/**
 * Pipeline Transformer interface.
 */
abstract class Transformer extends Serializable {
  /**
   * Initialization code for this Extractor
   *
   * @param sqlContext The SQLContext that is used to run this pipeline.
   *                   NOTE: If the pipeline is running in MemSQL Streamliner, this is an instance of
   *                   [[com.memsql.spark.connector.MemSQLContext]], which has additional metadata about the MemSQL cluster.
   * @param config The Transformer configuration passed from MemSQL Ops.
   * @param logger A logger instance that is integrated with MemSQL Ops.
   */
  def initialize(sqlContext: SQLContext, config: PhaseConfig, logger: PhaseLogger): Unit = {}

  /**
   * Cleanup code for your Transformer.
   * This is called after your pipeline has stopped.
   * The default implementation does nothing.
   *
   * @param sqlContext The [[org.apache.spark.sql.SQLContext]] that is used to run this pipeline.
   * @param config The Transformer configuration passed from MemSQL Ops.
   * @param logger A logger instance that is integrated with MemSQL Ops.
   */
  def cleanup(sqlContext: SQLContext, config: PhaseConfig, logger: PhaseLogger): Unit = {}

  /**
   * Transforms the incoming [[org.apache.spark.sql.DataFrame]].
   *
   * @param sqlContext The SQLContext that is used to run this pipeline.
   *                   NOTE: If the pipeline is running in MemSQL Streamliner, this is an instance of
   *                   [[com.memsql.spark.connector.MemSQLContext]], which has additional metadata about the MemSQL cluster.
   * @param df The [[org.apache.spark.sql.DataFrame]] generated by the Extractor for this batch.
   * @param config The Transformer configuration passed from MemSQL Ops.
   * @param logger A logger instance that is integrated with MemSQL Ops.
   * @return A [[org.apache.spark.sql.DataFrame]] with the transformed data to be loaded.
   */
  def transform(sqlContext: SQLContext, df: DataFrame, config: PhaseConfig, logger: PhaseLogger): DataFrame
}

/**
 * A helper object to extract the first column of a schema
 */
object ExtractFirstStructField {
  def unapply(schema: StructType): Option[(String, DataType, Boolean, Metadata)] = schema.fields match {
    case Array(first: StructField, _*) => Some((first.name, first.dataType, first.nullable, first.metadata))
  }
}

/**
 * Pipeline Transformer interface for strings, that internally also manages byte arrays.
 */
@deprecated("Transformer interface supports DataFrames", "1.2.0")
abstract class StringTransformer extends Transformer {
  final var byteUtils = ByteUtils

  final override def transform(sqlContext: SQLContext, df: DataFrame, config: PhaseConfig, logger: PhaseLogger): DataFrame = {
    val rdd = df.schema match {
      case ExtractFirstStructField(_, dataType: StringType, _, _) => df.rdd.map(_.toSeq.head.asInstanceOf[String])
      case ExtractFirstStructField(_, dataType: BinaryType, _, _) => df.rdd.map(r => byteUtils.bytesToUTF8String(r.toSeq.head.asInstanceOf[Array[Byte]]))
      case _ => throw new IllegalArgumentException("The first column of the input DataFrame should be either StringType or BinaryType")
    }
    transform(sqlContext, rdd, config, logger)
  }

  /**
   * Transforms the incoming [[org.apache.spark.rdd.RDD]] of bytes.
   *
   * @param sqlContext The SQLContext that is used to run this pipeline.
   *                   NOTE: If the pipeline is running in MemSQL Streamliner, this is an instance of
   *                   [[com.memsql.spark.connector.MemSQLContext]], which has additional metadata about the MemSQL cluster.
   * @param rdd The [[org.apache.spark.rdd.RDD]] generated by the Extractor for this batch.
   * @param config The Transformer configuration passed from MemSQL Ops.
   * @param logger A logger instance that is integrated with MemSQL Ops.
   * @return A [[org.apache.spark.sql.DataFrame]] with the transformed data to be loaded.
   */
  def transform(sqlContext: SQLContext, rdd: RDD[String], config: PhaseConfig, logger: PhaseLogger): DataFrame
}

/**
 * Pipeline Transformer interface for byte arrays.
 */
@deprecated("Transformer interface supports DataFrames", "1.2.0")
abstract class ByteArrayTransformer extends Transformer {
  final var byteUtils = ByteUtils

  final override def transform(sqlContext: SQLContext, df: DataFrame, config: PhaseConfig, logger: PhaseLogger): DataFrame = {
    val rdd = df.schema match {
      case ExtractFirstStructField(_, dataType: BinaryType, _, _) => df.rdd.map(_.toSeq.head.asInstanceOf[Array[Byte]])
      case ExtractFirstStructField(_, dataType: StringType, _, _) => df.rdd.map(r => byteUtils.utf8StringToBytes(r.toSeq.head.asInstanceOf[String]))
      case _ => throw new IllegalArgumentException("The first column of the input DataFrame should be either StringType or BinaryType")
    }
    transform(sqlContext, rdd, config, logger)
  }

  /**
   * Transforms the incoming [[org.apache.spark.rdd.RDD]] of bytes.
   *
   * @param sqlContext The SQLContext that is used to run this pipeline.
   *                   NOTE: If the pipeline is running in MemSQL Streamliner, this is an instance of
   *                   [[com.memsql.spark.connector.MemSQLContext]], which has additional metadata about the MemSQL cluster.
   * @param rdd The [[org.apache.spark.rdd.RDD]] generated by the Extractor for this batch.
   * @param config The Transformer configuration passed from MemSQL Ops.
   * @param logger A logger instance that is integrated with MemSQL Ops.
   * @return A [[org.apache.spark.sql.DataFrame]] with the transformed data to be loaded.
   */
  def transform(sqlContext: SQLContext, rdd: RDD[Array[Byte]], config: PhaseConfig, logger: PhaseLogger): DataFrame
}

/**
 * Convenience wrapper around ByteArrayExtractor for initialization and transformation of extracted [[org.apache.spark.rdd.RDD]]s.
 */
@deprecated("Transformer interface supports DataFrames", "1.2.0")
abstract class SimpleByteArrayTransformer extends ByteArrayTransformer {
  final override def initialize(sqlContext: SQLContext, config: PhaseConfig, logger: PhaseLogger): Unit = {
    val userConfig = config.asInstanceOf[UserTransformConfig]
    initialize(sqlContext, userConfig, logger)
  }

  final override def transform(sqlContext: SQLContext, rdd: RDD[Array[Byte]], config: PhaseConfig,
                               logger: PhaseLogger): DataFrame = {
    val userConfig = config.asInstanceOf[UserTransformConfig]
    transform(sqlContext, rdd, userConfig, logger)
  }

  /**
   * Initialization code for your Transformer.
   * This is called after instantiation of your Transformer and before [[Transformer.transform]].
   * The default implementation does nothing.
   *
   * @param sqlContext The SQLContext that is used to run this pipeline.
   *                   NOTE: If the pipeline is running in MemSQL Streamliner, this is an instance of
   *                   [[com.memsql.spark.connector.MemSQLContext]], which has additional metadata about the MemSQL cluster.
   * @param config The user defined configuration passed from MemSQL Ops.
   * @param logger A logger instance that is integrated with MemSQL Ops.
   */
  def initialize(sqlContext: SQLContext, config: UserTransformConfig, logger: PhaseLogger): Unit = {}

  /**
   * Convenience method for transforming [[org.apache.spark.rdd.RDD]]s into [[org.apache.spark.sql.DataFrame]]>
   * This is called once per batch on the [[org.apache.spark.rdd.RDD]] generated by the Extractor and the result
   * is passed to the Loader.
   *
   * @param sqlContext The SQLContext that is used to run this pipeline.
   *                   NOTE: If the pipeline is running in MemSQL Streamliner, this is an instance of
   *                   [[com.memsql.spark.connector.MemSQLContext]], which has additional metadata about the MemSQL cluster.
   * @param rdd The [[org.apache.spark.rdd.RDD]] for this batch generated by the Extractor.
   * @param config The user defined configuration passed from MemSQL Ops.
   * @param logger A logger instance that is integrated with MemSQL Ops.
   * @return A [[org.apache.spark.sql.DataFrame]] with the transformed data to be loaded.
   */
  def transform(sqlContext: SQLContext, rdd: RDD[Array[Byte]], config: UserTransformConfig, logger: PhaseLogger): DataFrame
}
