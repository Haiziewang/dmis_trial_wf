package com.oocl.dmsa.etl

import com.oocl.dmsa.spark.common.impl.JoinConfig
import com.oocl.dmsa.spark.common.impl.entity.{ConfigAgentFactory, S3ConfigAgent}
import com.oocl.dmsa.spark.common.utils.CMNUtils
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{coalesce, count, current_timestamp, min, row_number}
import org.apache.spark.storage.StorageLevel

object ItsSchedule {

  val itsEtl = new ItsEtl()

  def getInput()(implicit sparkSession: SparkSession): DataFrame = {
    val inputSql =
      s"""
         |SELECT
         |VOYAGE_STOP_ASSN_ID AS VOY_STOP_ASSN_ID,
         |VOYAGE_CODE AS VOY_CDE,
         |STOP_ID AS STOP_ID,
         |SEQ_IN_VOYAGE AS VOY_SEQ
         |FROM ITS_VOYAGE_STOP_ASSN
      """.stripMargin

    sparkSession.sql(inputSql)
  }

  def transform(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
    Function.chain {
      Seq(
        transformIts(_: DataFrame)
        ,operatorTransform(_: DataFrame)
        ,(_: DataFrame).withColumn("REC_CRE_TS",current_timestamp())
      )
    }(input)
  }

  def transformIts(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
    import sparkSession.implicits._

    val pipeline = Function.chain {
      Seq(itsEtl.withVoyage(_: DataFrame),
        itsEtl.withStop(_: DataFrame),
        (_: DataFrame)
          .withColumn("ARR_PORT_CALL_SEQ_FIX", row_number().over(Window.partitionBy("ARR_VOY_CDE", "STOP_PORT_ID").orderBy("VOY_CDE", "VOY_SEQ", "STOP_ID")))
          .withColumn("DEP_PORT_CALL_SEQ_FIX", row_number().over(Window.partitionBy("DEP_VOY_CDE", "STOP_PORT_ID").orderBy("VOY_CDE", "VOY_SEQ", "STOP_ID")))
          .withColumn("PORT_CALL_SEQ", row_number().over(Window.partitionBy("VOY_CDE", "STOP_PORT_ID").orderBy("VOY_SEQ", "STOP_ID"))),
        itsEtl.withStopArrDep(_: DataFrame, ItsScheduleState.LongTerm),
        itsEtl.withStopArrDep(_: DataFrame, ItsScheduleState.Coastal),
        itsEtl.withStopArrDep(_: DataFrame, ItsScheduleState.Actual),
        (_: DataFrame)
          .withColumn("LTT_BERTH_ARR_IODT", coalesce($"ACTL_BERTH_ARR_IODT", $"CSTL_BERTH_ARR_IODT", $"LGMT_BERTH_ARR_IODT"))
          .withColumn("LTT_BERTH_DEP_IODT", coalesce($"ACTL_BERTH_DEP_IODT", $"CSTL_BERTH_DEP_IODT", $"LGMT_BERTH_DEP_IODT"))
          .withColumn("LTT_BERTH_ARR_LOC", coalesce($"ACTL_BERTH_ARR_LOC", $"CSTL_BERTH_ARR_LOC", $"LGMT_BERTH_ARR_LOC"))
          .withColumn("LTT_BERTH_DEP_LOC", coalesce($"ACTL_BERTH_DEP_LOC", $"CSTL_BERTH_DEP_LOC", $"LGMT_BERTH_DEP_LOC"))
          .withColumn("LTT_PILOT_ARR_IODT", coalesce($"ACTL_PILOT_ARR_IODT", $"CSTL_PILOT_ARR_IODT", $"LGMT_PILOT_ARR_IODT"))
          .withColumn("LTT_PILOT_DEP_IODT", coalesce($"ACTL_PILOT_DEP_IODT", $"CSTL_PILOT_DEP_IODT", $"LGMT_PILOT_DEP_IODT"))
          .withColumn("LTT_PILOT_ARR_LOC", coalesce($"ACTL_PILOT_ARR_LOC", $"CSTL_PILOT_ARR_LOC", $"LGMT_PILOT_ARR_LOC"))
          .withColumn("LTT_PILOT_DEP_LOC", coalesce($"ACTL_PILOT_DEP_LOC", $"CSTL_PILOT_DEP_LOC", $"LGMT_PILOT_DEP_LOC")),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.DG, ItsDateType.Cutoff, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID"), withColumns = Set("DG_CUT_IODT", "DG_CUT_LOC", "CARRIER_ID"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.CFS, ItsDateType.Cutoff, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("CFS_CUT_IODT", "CFS_CUT_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.RF, ItsDateType.Cutoff, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("RF_CUT_IODT", "RF_CUT_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.Default, ItsDateType.Cutoff, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("DEFAULT_CUT_IODT", "DEFAULT_CUT_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.GC, ItsDateType.Cutoff, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("GC_CUT_IODT", "GC_CUT_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.VGM, ItsDateType.Cutoff, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("VGM_CUT_IODT", "VGM_CUT_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.DG, ItsDateType.Availability, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("DG_AVA_IODT", "DG_AVA_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.CFS, ItsDateType.Availability, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("CFS_AVA_IODT", "CFS_AVA_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.RF, ItsDateType.Availability, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("RF_AVA_IODT", "RF_AVA_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.Default, ItsDateType.Availability, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("DEFAULT_AVA_IODT", "DEFAULT_AVA_LOC"))),
        itsEtl.withStopCutAva(_: DataFrame, ItsCargoNature.GC, ItsDateType.Availability, JoinConfig(withJoinColumns = Map("STOP_ID" -> "STOP_ID", "CARRIER_ID" -> "CARRIER_ID"), withColumns = Set("GC_AVA_IODT", "GC_AVA_LOC")))
      )
    }
    pipeline(input)
  }

  def extractFirstDepPerVoy(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
    val dfFirstDepVoy = input.groupBy("VOY_CDE", "SVC_CDE", "VSL_CDE", "CARRIER_ID").agg(min("CSTL_BERTH_DEP_IODT").as("F_BERTH_DEP_IODT")).distinct()
      .persist(StorageLevel.MEMORY_ONLY)
    dfFirstDepVoy.createOrReplaceTempView("TBVOYWITHFDEP")

    dfFirstDepVoy
  }

  def extractSvcOperator(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
    val voyDep = extractFirstDepPerVoy(input)
    val svcOperatorSql =
      s"""
         |SELECT DISTINCT VOY_CDE, NVL(SVC_OPERATOR,VSL_OPERATOR) OPERATOR, CARRIER_ID
         |FROM
         |(
         |SELECT VOY_CDE
         |, CASE
         |    WHEN O1.EFF_END_IODT IS NULL THEN
         |        CASE WHEN O1.EFF_START_IODT <= V.F_BERTH_DEP_IODT THEN O1.OPERATOR ELSE NULL END
         |    ELSE
         |        CASE WHEN O1.EFF_START_IODT <= V.F_BERTH_DEP_IODT AND V.F_BERTH_DEP_IODT <= O1.EFF_END_IODT THEN O1.OPERATOR ELSE NULL END
         |    END SVC_OPERATOR
         |, CASE
         |    WHEN O2.EFF_END_IODT IS NULL THEN
         |        CASE
         |            WHEN O2.EFF_START_IODT IS NULL THEN O2.OPERATOR
         |            ELSE
         |                CASE WHEN O2.EFF_START_IODT <= V.F_BERTH_DEP_IODT THEN O2.OPERATOR ELSE NULL END
         |            END
         |    ELSE
         |        CASE
         |            WHEN O2.EFF_START_IODT IS NULL THEN
         |                CASE WHEN V.F_BERTH_DEP_IODT <= O2.EFF_END_IODT THEN O2.OPERATOR ELSE NULL END
         |            ELSE
         |                CASE WHEN O2.EFF_START_IODT <= V.F_BERTH_DEP_IODT AND V.F_BERTH_DEP_IODT <= O2.EFF_END_IODT THEN O2.OPERATOR ELSE NULL END
         |        END
         |    END VSL_OPERATOR
         |, CARRIER_ID
         |FROM TBVOYWITHFDEP V
         |LEFT JOIN DMSA_IN_NRT.ITS_SVC_OPERATOR_ASSN O1
         |ON O1.SVC_LOOP_CODE IS NOT NULL
         |AND O1.SVC_LOOP_CODE = V.SVC_CDE
         |AND O1.VSL_CODE = V.VSL_CDE
         |LEFT JOIN DMSA_IN_NRT.ITS_SVC_OPERATOR_ASSN O2
         |ON O2.SVC_LOOP_CODE IS NULL
         |AND O2.VSL_CODE = V.VSL_CDE
         |) A
         |WHERE SVC_OPERATOR IS NOT NULL OR VSL_OPERATOR IS NOT NULL
      """.stripMargin

    var dfSvcOperator = sparkSession.sql(svcOperatorSql).persist(StorageLevel.MEMORY_ONLY)
    dfSvcOperator.createOrReplaceTempView("TBSVCOPERATOR")

    dfSvcOperator
  }

  def extractVoyOperator(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
    import sparkSession.implicits._

    val vsl = itsEtl.withItsVsl
    //Get voyage and svcOperator tmp tables
    val voy = extractFirstDepPerVoy(input)
    val svcOperator = extractSvcOperator(input)

    //Filter out voyage code which have multiple operators
    val voyCdeList = svcOperator.groupBy("VOY_CDE", "CARRIER_ID").agg(count("*").alias("cnt")).where($"cnt" === 1)
      .select("VOY_CDE", "CARRIER_ID").distinct().map(r => r.getString(0)).collect.toList

    var dfVoySvcOperator = voy.join(svcOperator, Seq("VOY_CDE", "CARRIER_ID"), "left").select("VOY_CDE", "CARRIER_ID", "OPERATOR")
      .filter($"VOY_CDE".isin(voyCdeList: _*))

    var dfVoyVslOperator = voy.join(vsl, Seq("VSL_CDE"), "left").select("VOY_CDE", "CARRIER_ID", "OPERATOR")
      .filter(!$"VOY_CDE".isin(voyCdeList: _*))

    val dfVoyOperator = dfVoySvcOperator.unionAll(dfVoyVslOperator)
      .persist(StorageLevel.MEMORY_ONLY)
    dfVoyOperator.createOrReplaceTempView("TBVOYOPERATOR")

    dfVoyOperator
  }

  def operatorTransform(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
    val dfVoyOperator = extractVoyOperator(input)
    val dfSchFinal = input.join(dfVoyOperator, Seq("VOY_CDE", "CARRIER_ID"), "left")

    dfSchFinal
  }

  def main(args: Array[String]): Unit = {

    val s3InstanceName = args.lift(0).getOrElse("dmsa-s3-hive-service")
    val targetDatabase = args.lift(1).getOrElse("dmsa_nrt")
    val targetTableName = args.lift(2).getOrElse("dmsa_its_schedule")
    val targetDir = args.lift(3).getOrElse("s3a://dmsa/datawarehouse/dm/its_nrt/dmsa_its_schedule")
    val sourceDatabase = args.lift(4).getOrElse("dmsa_in_nrt")
    val s3Config = ConfigAgentFactory(s3InstanceName,"s3").asInstanceOf[S3ConfigAgent]

    val config = CMNUtils.genSparkConfigWithS3(s3Config.hive_uri, s3Config.endpoint, s3Config.accessKey, s3Config.secretKey)
      .set("spark.jars.packages", "io.delta:delta-core_2.12:0.8.0")
      .set("spark.sql.inMemoryColumnarStorage.compressed", "true")

    System.setProperty("com.amazonaws.sdk.disableCertChecking", "true")

    implicit val sparkSession = SparkSession.builder()
      .config(config)
      .enableHiveSupport()
      .getOrCreate()

    sparkSession.catalog.setCurrentDatabase(sourceDatabase)

    val inputDf = getInput
    val df = transform(inputDf)

    CMNUtils.saveAsTable("delta",df,targetDatabase,targetTableName,targetDir)

    sparkSession.stop()
  }

}
