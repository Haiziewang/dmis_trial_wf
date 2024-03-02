package com.oocl.dmsa.etl

import org.apache.spark.sql.{DataFrame,SparkSession}
import com.oocl.dmsa.spark.common.impl.{BaseEtl, JoinConfig}

object ItsScheduleState extends Enumeration {
  type ItsScheduleState = Value
  val LongTerm, Coastal, Actual = Value
}

object ItsCargoNature extends Enumeration {
  type ItsCargoNature = Value
  val DG, CFS, RF, Default, GC, VGM = Value
}

object ItsDateType extends Enumeration {
  type ItsDateType = Value
  val Availability, Cutoff = Value
}

class ItsEtl extends BaseEtl {

  import com.oocl.dmsa.etl.ItsCargoNature._
  import com.oocl.dmsa.etl.ItsDateType._
  import com.oocl.dmsa.etl.ItsScheduleState._

  def withVoyage(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val voySql =
      s"""SELECT
         |VOYAGE_CODE AS VOY_CDE,
         |SERVICE_LOOP_CDE AS SVC_CDE,
         |VESSEL_CDE AS VSL_CDE,
         |VOYAGE_NUM AS VOY_NUM,
         |DIRECTION AS DIR,
         |PREV_VOYAGE_CODE AS PREV_VOY_CDE,
         |NEXT_VOYAGE_CODE AS NEXT_VOY_CDE,
         |VOY_ID AS VOY_ID,
         |SSM_VOY_ID AS SSM_VOY_ID,
         |REMARK AS VOY_RMK,
         |SVC_LOOP_ID AS SVC_ID,
         |VSL_ID AS VSL_ID,
         |PARENT_PROFM_ID AS PARENT_PROFM_ID,
         |EXT_VOY_REF AS EXT_REF_VOY
         |FROM DMSA_IN_NRT.ITS_VOYAGE
      """.stripMargin
    val voy = sparkSession.sql(voySql)

    val keys = Set("VOY_CDE")

    join(input, voy, keys, config)
  }

  def withStop(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val stopSql =
      s"""SELECT
         |STOP_ID AS STOP_ID,
         |FACILITY_ID AS STOP_FCIL_ID,
         |PORT_ID AS STOP_PORT_ID,
         |TIMEZONE_NME AS STOP_TZ_NME,
         |ARR_VOYAGE_CODE AS ARR_VOY_CDE,
         |DEP_VOYAGE_CODE AS DEP_VOY_CDE,
         |IS_LOAD_ALLOWED AS IS_LOAD_ALLOWED,
         |IS_DISCHARGE_ALLOWED AS IS_DSCH_ALLOWED,
         |IS_PASS_ALLOWED AS IS_PASS_ALLOWED,
         |IS_TURNAROUND AS IS_TURNAROUND,
         |IS_OMITTED AS IS_OMITTED,
         |IS_TENTATIVE_SCHEDULE AS IS_TENTATIVE_SCHED,
         |DISCHARGE_COMPLETED_IODT AS DSCH_COMPL_IODT,
         |ARR_EXT_VOY_REF AS EXT_REF_ARR_VOY,
         |DEP_EXT_VOY_REF AS EXT_REF_DEP_VOY,
         |ARR_PORT_CALL_SEQ AS ARR_PORT_CALL_SEQ,
         |DEP_PORT_CALL_SEQ AS DEP_PORT_CALL_SEQ,
         |MRU AS STOP_MRU,
         |REMARK AS STOP_RMK,
         |SSM_VOYAGE_STOP_ID AS SSM_STOP_ID,
         |IS_PHASE_IN_OUT AS IS_PHASE_IN_OUT,
         |EST_BERTH_ARR_IODT AS EST_BERTH_ARR_IODT,
         |EST_BERTH_DEP_IODT AS EST_BERTH_DEP_IODT,
         |EST_BERTH_ARR_LOC AS EST_BERTH_ARR_LOC,
         |EST_BERTH_DEP_LOC AS EST_BERTH_DEP_LOC,
         |LATEST_BERTH_DEP_LOC,
         |LATEST_BERTH_DEP_IODT,
         |FIRST_CBF_CUTOFF_IODT,
         |FIRST_CBF_CUTOFF_LOC,
         |ERD_IODT,
         |ERD_LOC
         |FROM DMSA_IN_NRT.ITS_STOP
      """.stripMargin
    val stop = sparkSession.sql(stopSql)

    val keys = Set("STOP_ID")

    join(input, stop, keys, config)
  }

  def withStopArrDep(input: DataFrame, schedState: ItsScheduleState, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val prefix = schedState match {
      case ItsScheduleState.LongTerm => "LGMT"
      case ItsScheduleState.Coastal => "CSTL"
      case ItsScheduleState.Actual => "ACTL"
    }
    val arrdepSql =
      s"""SELECT
         |STOP_ID AS STOP_ID,
         |BERTH_ARRIVAL_IODT AS ${prefix}_BERTH_ARR_IODT,
         |BERTH_DEPARTURE_IODT as ${prefix}_BERTH_DEP_IODT,
         |BERTH_ARRIVAL_LOC AS ${prefix}_BERTH_ARR_LOC,
         |BERTH_DEPARTURE_LOC as ${prefix}_BERTH_DEP_LOC,
         |PILOT_ARRIVAL_IODT AS ${prefix}_PILOT_ARR_IODT,
         |PILOT_DEPARTURE_IODT as ${prefix}_PILOT_DEP_IODT,
         |PILOT_ARRIVAL_LOC AS ${prefix}_PILOT_ARR_LOC,
         |PILOT_DEPARTURE_LOC as ${prefix}_PILOT_DEP_LOC,
         |PORT_ARRIVAL_IODT as ${prefix}_PORT_ARR_IODT,
         |PORT_ARRIVAL_LOC AS ${prefix}_PORT_ARR_LOC,
         |PORT_WAITING as ${prefix}_PORT_WAITING
         |FROM DMSA_IN_NRT.ITS_STOP_ARR_DEP
         |WHERE SCHEDULE_STATE = '${schedState.toString()}'
      """.stripMargin
    val arrdep = sparkSession.sql(arrdepSql)

    val keys = Set("STOP_ID")

    join(input, arrdep, keys, config)
  }

  def withStopCutAvaCarrier(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {

    val cutoffAva =
      s"""SELECT DISTINCT
         |STOP_ID AS STOP_ID,
         |CARRIER_ID
         |FROM DMSA_IN_NRT.ITS_STOP_CUT_AVA
        """.stripMargin
    val cutAva = sparkSession.sql(cutoffAva)

    val keys = Set("STOP_ID")

    join(input, cutAva, keys, config)
  }

  def withStopCutAva(input: DataFrame, cargoNature: ItsCargoNature, dataType: ItsDateType, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val cgoNaturePrefix = cargoNature match {
      case ItsCargoNature.DG => "DG"
      case ItsCargoNature.CFS => "CFS"
      case ItsCargoNature.RF => "RF"
      case ItsCargoNature.Default => "DEFAULT"
      case ItsCargoNature.GC => "GC"
      case ItsCargoNature.VGM => "VGM"
    }

    val dataTypePrefix = dataType match {
      case ItsDateType.Cutoff => "CUT"
      case ItsDateType.Availability => "AVA"
    }

    val cutoffAva =
      s"""SELECT
         |STOP_ID AS STOP_ID,
         |STOP_CUT_AVA_ID AS STOP_CUT_AVA_ID,
         |CARGO_NATURE AS CGO_NATURE,
         |TYPE AS DATE_TYPE,
         |DATE_IODT AS ${cgoNaturePrefix}_${dataTypePrefix}_IODT,
         |DATE_LOC AS ${cgoNaturePrefix}_${dataTypePrefix}_LOC,
         |CREATE_IODT AS REC_CRE_IODT,
         |CREATE_BY AS REC_CRE_BY,
         |UPDATE_IODT AS REC_UPD_IODT,
         |UPDATE_BY AS REC_UPD_BY,
         |CARRIER_ID
         |FROM DMSA_IN_NRT.ITS_STOP_CUT_AVA
         |WHERE TYPE = '${dataType.toString()}'
         |AND CARGO_NATURE = '${cargoNature.toString()}'
        """.stripMargin
    val cutAva = sparkSession.sql(cutoffAva)

    val keys = Set("STOP_CUT_AVA_ID")

    join(input, cutAva, keys, config)
  }

  def withEffVoy(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val effVoySql =
      s"""SELECT
         |EFFECTIVE_VOY_ID                   AS EFF_VOY_ID
         |,CARRIER_CODE                       AS CARRIER_CDE
         |,SERVICE_LOOP_CODE                  AS SVC_CDE
         |,VESSEL_CODE                        AS VSL_CDE
         |,EFFECTIVE_VOY_NUM                  AS EFF_VOY_NUM
         |,SUBSTR(DIRECTION, 1, 1)            AS DIR
         |FROM DMSA_IN_NRT.ITS_EFFECTIVE_VOYAGE
      """.stripMargin

    val effVoy = sparkSession.sql(effVoySql)

    val keys = Set("EFF_VOY_ID")

    join(input, effVoy, keys, config)
  }

  def withDefaultVslCapAssn(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val defaultVslCapAssnSql =
      s"""SELECT
         |EFFECTIVE_VOY_ID                   AS EFF_VOY_ID
         |,VESSEL_CAPACITY_ID                 AS DEFAULT_VSL_CAP_ID
         |FROM DMSA_IN_NRT.ITS_VSL_CAP_DEFAULT_ASSN
      """.stripMargin

    val defaultVslCapAssn = sparkSession.sql(defaultVslCapAssnSql)

    val keys = Set("EFF_VOY_ID")

    join(input, defaultVslCapAssn, keys, config)
  }

  def withVslCapPortAssn(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val vslCapPortAssnSql =
      s"""SELECT
         |EFFECTIVE_VOY_ID                   AS EFF_VOY_ID
         |,PORT_ID                            AS PORT_ID
         |,VESSEL_CAPACITY_ID                 AS VSL_CAP_ID
         |FROM DMSA_IN_NRT.ITS_VSL_CAP_PORT_ASSN
      """.stripMargin

    val vslCapPortAssn = sparkSession.sql(vslCapPortAssnSql)

    val keys = Set("EFF_VOY_ID")

    join(input, vslCapPortAssn, keys, config)
  }

  def withVslCapTradelaneAssn(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val vslCapPortAssnSql =
      s"""SELECT
         |EFFECTIVE_VOY_ID                   AS EFF_VOY_ID
         |,TRADE_LANE_CODE                    AS TRADELANE_CDE
         |,OWNERS_MERIT                       AS OWNERS_MERIT
         |,VESSEL_CAPACITY_ID                 AS VSL_CAP_ID
         |FROM DMSA_IN_NRT.ITS_VSL_CAP_TRADELANE_ASSN
      """.stripMargin

    val vslCapPortAssn = sparkSession.sql(vslCapPortAssnSql)

    val keys = Set("EFF_VOY_ID")

    join(input, vslCapPortAssn, keys, config)
  }

  def withVslCap(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val vslCapSql =
      s"""SELECT
         |VESSEL_CAPACITY_ID                 AS VSL_CAP_ID
         |,CARRIER_CODE                       AS CARRIER_CDE
         |,SERVICE_LOOP_CODE                  AS SVC_CDE
         |,VESSEL_CODE                        AS VSL_CDE
         |,SUBSTR(DIRECTION, 1, 1)            AS DIR
         |,LADEN_VOLUME_TEU                   AS LADEN_VOL_TEU
         |,LADEN_WEIGHT_IN_TON                AS LADEN_WEIGHT_TON
         |,EMPTY_VOLUME_TEU                   AS MT_VOL_TEU
         |,EMPTY_WEIGHT_IN_TON                AS MT_WEIGHT_TON
         |FROM DMSA_IN_NRT.ITS_VESSEL_CAPACITY
      """.stripMargin

    val vslCap = sparkSession.sql(vslCapSql)

    val keys = Set("VSL_CAP_ID")

    join(input, vslCap, keys, config)
  }

  def withVslCapDtl(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {

    def transform(input: DataFrame)(implicit sparkSession: SparkSession): DataFrame = {
      val pipeline = Function.chain {
        Seq(
          (_: DataFrame).groupBy("VSL_CAP_ID").pivot("CAT_CDE", Seq("BOX_20_MIN", "BOX_20_MAX", "BOX_40_MIN", "BOX_40_MAX", "BOX_45_MIN", "BOX_45_MAX", "BOX_20RF_MAX", "BOX_40RF_MAX", "REEFER_PLUGS")).sum("QTY"),
          (_: DataFrame).withColumnRenamed("BOX_20_MIN", "MIN_20FT"),
          (_: DataFrame).withColumnRenamed("BOX_20_MAX", "MAX_20FT"),
          (_: DataFrame).withColumnRenamed("BOX_40_MIN", "MIN_40FT"),
          (_: DataFrame).withColumnRenamed("BOX_40_MAX", "MAX_40FT"),
          (_: DataFrame).withColumnRenamed("BOX_45_MIN", "MIN_45FT"),
          (_: DataFrame).withColumnRenamed("BOX_45_MAX", "MAX_45FT"),
          (_: DataFrame).withColumnRenamed("BOX_20RF_MAX", "MAX_20RF"),
          (_: DataFrame).withColumnRenamed("BOX_40RF_MAX", "MAX_40RF"),
          (_: DataFrame).withColumnRenamed("REEFER_PLUGS", "RF_PLUGS")
        )
      }
      pipeline(input)
    }

    val vslCapDtlSql =
      s"""SELECT
         |VESSEL_CAPACITY_ID                 AS VSL_CAP_ID
         |,CATEGORY                           AS CAT_CDE
         |,QUANTITY                           AS QTY
         |FROM DMSA_IN_NRT.ITS_LADEN_CAPACITY
      """.stripMargin

    val vslCapDtl = sparkSession.sql(vslCapDtlSql)

    val df = transform(vslCapDtl)

    val keys = Set("VSL_CAP_ID")

    join(input, df, keys, config)
  }

  def withItsVsl(implicit sparkSession: SparkSession): DataFrame = {
    val vslSql =
      s"""SELECT
         |CODE AS VSL_CDE,
         |OPERATOR
         |FROM DMSA_IN_NRT.ITS_VESSEL
    """.stripMargin

    sparkSession.sql(vslSql)
  }

  def withItsVslNewest(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val vslSql =
      s"""SELECT
         |CODE AS VSL_CDE,
         |OPERATOR
         |FROM DMSA_IN_NRT.ITS_VESSEL
    """.stripMargin

    val vsl = sparkSession.sql(vslSql)

    val keys = Set("VSL_CDE")

    join(input, vsl, keys, config)
  }

  def withItsProfmStop(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val itsProfmStopSql =
      s"""SELECT
        | PROFM_STOP_ID,
        |	FACILITY_ID,
        |	GMT,
        |	PORT_ID,
        |	ARR_PORT_CALL_SEQ,
        |	DEP_PORT_CALL_SEQ,
        |	ARR_VOYAGE_DIR,
        |	DEP_VOYAGE_DIR,
        |	BERTH_ARR_DAY_OFF,
        |	BERTH_ARR_L_HR,
        |	BERTH_ARR_L_MIN,
        |	BERTH_ARR_L_DOW,
        |	BERTH_DEP_DAY_OFF,
        |	BERTH_DEP_L_HR,
        |	BERTH_DEP_L_MIN,
        |	BERTH_DEP_L_DOW,
        |	PILOT_ARR_DAY_OFF,
        |	PILOT_ARR_L_HR,
        |	PILOT_ARR_L_MIN,
        |	PILOT_ARR_L_DOW,
        |	PILOT_DEP_DAY_OFF,
        |	PILOT_DEP_L_HR,
        |	PILOT_DEP_L_MIN,
        |	PILOT_DEP_L_DOW,
        |	ENTRY_HR,
        |	BERTH_HR,
        |	EXIT_HR,
        |	SEA_HR,
        |	PORT_HR,
        |	PORT_OCCURRENCE,
        |	IS_LOAD_ALLOWED,
        |	IS_DISCHARGE_ALLOWED,
        |	IS_PASS_ALLOWED,
        |	ENTRY_MIN,
        |	BERTH_MIN,
        |	EXIT_MIN,
        |	SEA_MIN,
        |	PORT_MIN,
        |	PRIVATE_CALL
        |FROM ITS_PROFM_STOP
    """.stripMargin
    val itsProfmStopDf = sparkSession.sql(itsProfmStopSql)
    val keys = Set("PROFM_STOP_ID")
    join(input, itsProfmStopDf, keys, config)
  }

  def withItsProfmStopCutAva(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {

    val cutoffAva =
      s"""SELECT
         |  profm_stop_id,
         |  max(kv_day['GC']['Cutoff']) AS gc_cut_date_day_off,
         |  max(kv_day['DG']['Cutoff']) AS dg_cut_date_day_off,
         |  max(kv_day['RF']['Cutoff']) AS rf_cut_date_day_off,
         |  max(kv_day['CFS']['Cutoff']) AS cfs_cut_date_day_off,
         |  max(kv_day['VGM']['Cutoff']) AS vgm_cut_date_day_off,
         |  max(kv_day['Default']['Cutoff']) AS default_cut_date_day_off,
         |  max(kv_day['GC']['Availability']) AS gc_ava_date_day_off,
         |  max(kv_day['DG']['Availability']) AS dg_ava_date_day_off,
         |  max(kv_day['RF']['Availability']) AS rf_ava_date_day_off,
         |  max(kv_day['CFS']['Availability']) AS cfs_ava_date_day_off,
         |  max(kv_day['VGM']['Availability']) AS vgm_ava_date_day_off,
         |  max(kv_day['Default']['Availability']) AS default_ava_date_day_off,
         |  max(kv_hr['GC']['Cutoff']) AS gc_cut_date_l_hr,
         |  max(kv_hr['DG']['Cutoff']) AS dg_cut_date_l_hr,
         |  max(kv_hr['RF']['Cutoff']) AS rf_cut_date_l_hr,
         |  max(kv_hr['CFS']['Cutoff']) AS cfs_cut_date_l_hr,
         |  max(kv_hr['VGM']['Cutoff']) AS vgm_cut_date_l_hr,
         |  max(kv_hr['Default']['Cutoff']) AS default_cut_date_l_hr,
         |  max(kv_hr['GC']['Availability']) AS gc_ava_date_l_hr,
         |  max(kv_hr['DG']['Availability']) AS dg_ava_date_l_hr,
         |  max(kv_hr['RF']['Availability']) AS rf_ava_date_l_hr,
         |  max(kv_hr['CFS']['Availability']) AS cfs_ava_date_l_hr,
         |  max(kv_hr['VGM']['Availability']) AS vgm_ava_date_l_hr,
         |  max(kv_hr['Default']['Availability']) AS default_ava_date_l_hr,
         |  max(kv_min['GC']['Cutoff']) AS gc_cut_date_l_min,
         |  max(kv_min['DG']['Cutoff']) AS dg_cut_date_l_min,
         |  max(kv_min['RF']['Cutoff']) AS rf_cut_date_l_min,
         |  max(kv_min['CFS']['Cutoff']) AS cfs_cut_date_l_min,
         |  max(kv_min['VGM']['Cutoff']) AS vgm_cut_date_l_min,
         |  max(kv_min['Default']['Cutoff']) AS default_cut_date_l_min,
         |  max(kv_min['GC']['Availability']) AS gc_ava_date_l_min,
         |  max(kv_min['DG']['Availability']) AS dg_ava_date_l_min,
         |  max(kv_min['RF']['Availability']) AS rf_ava_date_l_min,
         |  max(kv_min['CFS']['Availability']) AS cfs_ava_date_l_min,
         |  max(kv_min['VGM']['Availability']) AS vgm_ava_date_l_min,
         |  max(kv_min['Default']['Availability']) AS default_ava_date_l_min,
         |  max(kv_dow['GC']['Cutoff']) AS gc_cut_date_l_dow,
         |  max(kv_dow['DG']['Cutoff']) AS dg_cut_date_l_dow,
         |  max(kv_dow['RF']['Cutoff']) AS rf_cut_date_l_dow,
         |  max(kv_dow['CFS']['Cutoff']) AS cfs_cut_date_l_dow,
         |  max(kv_dow['VGM']['Cutoff']) AS vgm_cut_date_l_dow,
         |  max(kv_dow['Default']['Cutoff']) AS default_cut_date_l_dow,
         |  max(kv_dow['GC']['Availability']) AS gc_ava_date_l_dow,
         |  max(kv_dow['DG']['Availability']) AS dg_ava_date_l_dow,
         |  max(kv_dow['RF']['Availability']) AS rf_ava_date_l_dow,
         |  max(kv_dow['CFS']['Availability']) AS cfs_ava_date_l_dow,
         |  max(kv_dow['VGM']['Availability']) AS vgm_ava_date_l_dow,
         |  max(kv_dow['Default']['Availability']) AS default_ava_date_l_dow
         |FROM (
         |  SELECT
         |    profm_stop_id,
         |    map(cargo_nature, map(type, date_day_off)) kv_day,
         |    map(cargo_nature, map(type, date_l_hr)) kv_hr,
         |    map(cargo_nature, map(type, date_l_min)) kv_min,
         |    map(cargo_nature, map(type, date_l_dow)) kv_dow
         |   FROM its_prorm_stop_cut_ava
         |) t
         |group by profm_stop_id
        """.stripMargin
    val cutAva = sparkSession.sql(cutoffAva)
    val keys = Set("PROFM_STOP_ID")
    join(input, cutAva, keys, config)
  }

  def withItsProfm(input: DataFrame, config: JoinConfig = null)(implicit sparkSession: SparkSession): DataFrame = {
    val itsProfmSql =
      s"""SELECT
         |	PROFORMA_ID,
         |	SERVICE_LOOP_CDE AS SVC_CDE,
         |	NAME AS SVC_NME,
         |	`TYPE` AS SVC_TYPE,
         |	IS_SSO_LABELED,
         |	EFFECTIVE_START_IODT AS EFF_START_IODT,
         |	EFFECTIVE_END_IODT AS EFF_END_IODT,
         |	REFERENCE_DATE_IODT AS REF_DATE_IODT,
         |	SCHEDULE_LENGTH,
         |	SSM_PROFORMA_ID,
         |	SVC_LOOP_ID AS SVC_ID,
         |	FREQUENCY,
         |	VENDOR_CODE AS VENDOR_CDE,
         |	VENDOR_NAME AS VENDOR_NME,
         |	TERRITORY_CODE AS TERR_CDE,
         |	SCHEDULE_LENGTH_MIN,
         |	MCS
         |FROM DMSA_IN.ITS_PROFORMA
    """.stripMargin

    val itsProfmDf = sparkSession.sql(itsProfmSql)
    val keys = Set("PROFORMA_ID")
    join(input, itsProfmDf, keys, config)
  }
}