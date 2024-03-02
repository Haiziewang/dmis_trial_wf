package com.oocl.dmsa.etl

import com.oocl.dmsa.spark.common.impl.JoinConfig
import com.oocl.dmsa.spark.common.impl.entity.{ConfigAgentFactory, S3ConfigAgent}
import com.oocl.dmsa.spark.common.utils.CMNUtils
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, SparkSession}

//2022.11 domain table creation per DDA request

object ItsProformaSchedule {

  val itsEtl = new ItsEtl()

  def getInput()(implicit sparkSession: SparkSession): DataFrame = {
    val inputSql =
      s"""
         |SELECT
         |  PROFM_STOP_ASSN_ID,
         |  PROFM_STOP_ID,
         |  SEQ_IN_PROFM AS PROFM_SEQ,
         |  PROFORMA_ID
         |FROM DMSA_IN.ITS_PROFM_STOP_ASSN
      """.stripMargin
    sparkSession.sql(inputSql)
  }

  def transform(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {

    val pipeline = Function.chain {
      Seq(
        itsEtl.withItsProfmStop(_: DataFrame, JoinConfig(Map("PROFM_STOP_ID" -> "PROFM_STOP_ID"))),
        itsEtl.withItsProfmStopCutAva(_: DataFrame, JoinConfig(Map("PROFM_STOP_ID" -> "PROFM_STOP_ID"))),
        itsEtl.withItsProfm(_: DataFrame, JoinConfig(Map("PROFORMA_ID" -> "PROFORMA_ID"))),
        (_: DataFrame).withColumn("REC_CRE_TS",current_timestamp())
      )
    }
    pipeline(input)
  }

  def main(args: Array[String]): Unit = {

    val s3InstanceName = args.lift(0).getOrElse("dmsa-s3-hive-service")
    val targetDataBase = args.lift(1).getOrElse("dmsa")
    val targerTableName = args.lift(2).getOrElse("dmsa_its_profm_schedule")
    val targetDir = args.lift(3).getOrElse("s3a://dmsa/datawarehouse/dm/its/dmsa_its_profm_schedule")
    val sourceDatabase = args.lift(4).getOrElse("dmsa_in")
    val s3Config = ConfigAgentFactory(s3InstanceName, "s3").asInstanceOf[S3ConfigAgent]

    val config = CMNUtils.genSparkConfigWithS3(s3Config.hive_uri, s3Config.endpoint, s3Config.accessKey, s3Config.secretKey)
      .set("spark.jars.packages", "io.delta:delta-core_2.12:0.8.0")
      .set("spark.sql.inMemoryColumnarStorage.compressed", "true")

    System.setProperty("com.amazonaws.sdk.disableCertChecking", "true")

    implicit val sparkSession: SparkSession = SparkSession.builder()
      .config(config)
      .enableHiveSupport()
      .getOrCreate()

    sparkSession.sparkContext.setLogLevel("ERROR")
    sparkSession.catalog.setCurrentDatabase(sourceDatabase)

    val profmStopAssnDf = getInput()
    val df = transform(profmStopAssnDf).repartition(5)

    CMNUtils.saveAsTable("delta", df, targetDataBase, targerTableName, targetDir)

    sparkSession.stop()
  }

}
