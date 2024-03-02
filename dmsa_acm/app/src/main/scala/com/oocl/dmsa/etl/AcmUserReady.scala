package com.oocl.dmsa.etl

import com.oocl.dmsa.spark.common.impl.entity.{ConfigAgentFactory, S3ConfigAgent}
import com.oocl.dmsa.spark.common.impl.{BaseEtl, JoinConfig, JoinType}
import com.oocl.dmsa.spark.common.utils.CMNUtils
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions._


object AcmUserReady {

  def main(args: Array[String]): Unit = {
    val s3InstanceName = args.lift(0).getOrElse("dmsa-s3-service")
    val targetDatabase = args.lift(1).getOrElse("dmsa")
    val targetTableName = args.lift(2).getOrElse("dmsa_acm_user_state")
    val targetDir = args.lift(3).getOrElse("s3a://dmsa/datawarehouse/dm/acm/dmsa_acm_user_state")
    val sourceDatabase = args.lift(4).getOrElse("dmsa_in")
    val s3Config = ConfigAgentFactory(s3InstanceName, "s3").asInstanceOf[S3ConfigAgent]

    val config = CMNUtils.genSparkConfigWithS3(s3Config.hive_uri, s3Config.endpoint, s3Config.accessKey, s3Config.secretKey)
      .set("spark.jars.packages", "io.delta:delta-core_2.12:0.8.0")
      .set("spark.sql.inMemoryColumnarStorage.compressed", "true")


    System.setProperty("com.amazonaws.sdk.disableCertChecking", "true")

    implicit val sparkSession = SparkSession.builder()
      .config(config)
      .enableHiveSupport()
      .getOrCreate()

    sparkSession.catalog.setCurrentDatabase(sourceDatabase)

    val input = getInput()
    val result = transform(input)
    CMNUtils.saveAsTable("delta", result, targetDatabase, targetTableName, targetDir)
    sparkSession.stop()
  }

  def transform(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
    val pipeline = Function.chain {
      Seq(
        (_: DataFrame)
          .groupBy("owner_ref_num")
          .agg(
            first("mfest_info_id").alias("mfest_info_id"),
            first("readiness_state").alias("readiness_state_distinct"),
            first("rec_upd_dt").alias("rec_upd_dt")
          ),
        (_: DataFrame).withColumn("rec_cre_ts", current_timestamp())
      )
    }
    pipeline(input)
  }

  def getInput()(implicit sparkSession: SparkSession): DataFrame = {
    val inputSql =
      s"""select a.mfest_info_id, r.readiness_state, a.owner_ref_num, r.rec_upd_dt
         |from dmsa_in.acm_mfest_info a, dmsa_in.acm_mfest_ready_info r
         |where a.mfest_info_id = r.mfest_info_id and r.readiness_type = 'User' and a.submit_type not like '%IB' and a.is_deleted = 0
         |order by a.update_iodt desc
      """.stripMargin
    sparkSession.sql(inputSql)
  }
}