package cn.townhc.homebed.domain.enums;

/**
 * 平台领域枚举集中定义。JPA 统一以 EnumType.STRING 持久化。
 */
public final class DomainEnums {
    private DomainEnums() {}

    public enum Role {
        FAMILY("家属"),
        FAMILY_DOCTOR("家庭医生"),
        NURSE("护士"),
        ADMIN("卫生院管理者"),
        FINANCE("财务"),
        INSURANCE_AUDITOR("医保经办");

        public final String label;
        Role(String label) { this.label = label; }
    }

    /** 建床状态：PAUSED 表示患者临时住院等原因暂停巡诊，复评通过后可恢复 */
    public enum BedApplyStatus {
        DRAFT("草稿"),
        SUBMITTED("待评估"),
        APPROVED("批准建床"),
        REJECTED("驳回"),
        SUSPENDED("暂停建床"),
        PAUSED("暂停巡诊"),
        CANCELLED("家属撤销");

        public final String label;
        BedApplyStatus(String label) { this.label = label; }
    }

    public enum RiskLevel {
        LOW("低风险"), MEDIUM("中风险"), HIGH("高风险"), CRITICAL("极高风险");
        public final String label;
        RiskLevel(String label) { this.label = label; }
    }

    public enum DiseaseCategory {
        POST_SURGICAL("术后康复"),
        DIABETES("糖尿病"),
        HYPERTENSION("高血压"),
        STROKE_REHAB("脑卒中康复"),
        COPD("慢阻肺"),
        PRESSURE_ULCER("压疮"),
        OTHER("其他");
        public final String label;
        DiseaseCategory(String label) { this.label = label; }
    }

    public enum InsuranceType {
        URBAN_EMPLOYEE("城镇职工医保"),
        URBAN_RESIDENT("城乡居民医保"),
        NONE("无医保");
        public final String label;
        InsuranceType(String label) { this.label = label; }
    }

    /** 评估时勾选的护理需求类型 */
    public enum CareItemType {
        VITALS("生命体征监测"),
        BLOOD_PRESSURE("血压测量"),
        BLOOD_GLUCOSE("血糖测量"),
        PRESSURE_ULCER("压疮护理"),
        DRESSING("伤口换药"),
        CATHETER("导尿护理"),
        INJECTION("注射/输液"),
        MEDICATION_REMINDER("用药提醒与核对"),
        HEALTH_EDUCATION("健康指导");
        public final String label;
        CareItemType(String label) { this.label = label; }
    }

    public enum PlanStatus { ACTIVE("执行中"), PAUSED("已暂停"), COMPLETED("已完成"), CANCELLED("已取消");
        public final String label; PlanStatus(String l) { this.label = l; } }

    public enum VisitStatus {
        SCHEDULED("已排班"), IN_PROGRESS("上门中"), COMPLETED("已完成"),
        EXCEPTION("异常挂起"), NO_SHOW("上门未遇"), PAUSED("暂停待复评"), CANCELLED("已取消");
        public final String label; VisitStatus(String l) { this.label = l; } }

    public enum ExceptionType {
        PATIENT_HOSPITALIZED("患者临时住院"),
        FAMILY_ABSENT("家属不在家"),
        SUPPLY_SHORTAGE("耗材缺货"),
        NURSE_OVERTIME("护士上门超时"),
        CONDITION_WORSENED("患者病情恶化"),
        ROUTE_BLOCKED("道路/天气受阻"),
        OTHER("其他");
        public final String label; ExceptionType(String l) { this.label = l; } }

    public enum ExceptionStatus { REPORTED("已上报"), CONFIRMED("已核实"), RESOLVED("已处理");
        public final String label; ExceptionStatus(String l) { this.label = l; } }

    public enum CareResultStatus { DONE("已完成"), SKIPPED("未执行"), ABNORMAL("异常中止");
        public final String label; CareResultStatus(String l) { this.label = l; } }

    public enum OrderType {
        PRESCRIPTION("调整处方"),
        FOLLOW_UP("安排复诊"),
        REFERRAL("建议转诊"),
        CARE_PLAN_CHANGE("调整护理方案");
        public final String label; OrderType(String l) { this.label = l; } }

    public enum OrderStatus {
        PROPOSED("已下达"), REMOTE_REVIEWED("远程复核通过"), CONFIRMED("已确认"),
        EXECUTED("已执行"), VOID("已作废");
        public final String label; OrderStatus(String l) { this.label = l; } }

    public enum CatalogCategory {
        VISIT_FEE("巡诊费"),
        NURSING("护理项目"),
        CONSUMABLE("普通耗材"),
        COLDCHAIN_DRUG("冷链药品"),
        DRUG("普通药品"),
        NO_SHOW_FEE("空跑费");
        public final String label; CatalogCategory(String l) { this.label = l; } }

    public enum BillSourceType { VISIT_CARE("护理执行"), MATERIAL("耗材领用"), ORDER("医嘱费用"),
        NO_SHOW_FEE("上门空跑费");
        public final String label; BillSourceType(String l) { this.label = l; } }

    public enum BillStatus {
        DRAFT("待确认"), CONFIRMED("家属已确认"), INSURANCE_REVIEWED("医保复核完成"),
        DISPUTED("家属质疑"), SETTLED("已结算");
        public final String label; BillStatus(String l) { this.label = l; } }

    public enum ComplianceStatus { PENDING("待审查"), COMPLIANT("合规"), VIOLATION("存疑/违规");
        public final String label; ComplianceStatus(String l) { this.label = l; } }

    public enum RiskSeverity { LOW("一般"), MEDIUM("较重"), HIGH("严重");
        public final String label; RiskSeverity(String l) { this.label = l; } }

    public enum RiskEventStatus { OPEN("待处理"), CONFIRMED("已确认"), CLOSED("已闭环");
        public final String label; RiskEventStatus(String l) { this.label = l; } }

    public enum MaterialTxType { OUT("领用消耗"), IN("入库"), ADJUST("盘点调整");
        public final String label; MaterialTxType(String l) { this.label = l; } }

    /** 上门未遇原因（护士现场判定） */
    public enum NoShowReason {
        NO_ANSWER("敲门无人应答"),
        FAMILY_AWAY("家属临时外出未请假"),
        PATIENT_HOSPITALIZED("患者临时送医"),
        ADDRESS_CHANGED("地址变更未更新"),
        PHONE_UNREACHABLE("电话联系不上"),
        OTHER("其他");
        public final String label; NoShowReason(String l) { this.label = l; } }

    /** 家属补充说明的事由分类 */
    public enum FamilyExcuseReason {
        EMERGENCY("临时急诊/送医"),
        PHONE_DEAD("手机没电/未接到"),
        ADDRESS_CHANGE("地址变更"),
        FAMILY_EMERGENCY("家中突发急事"),
        WEATHER_ROUTE("天气/道路原因"),
        OTHER("其他");
        public final String label; FamilyExcuseReason(String l) { this.label = l; } }

    /** 空跑费用/扣次审核状态 */
    public enum NoShowStatus {
        REPORTED("已登记未遇"),
        EXPLAINED("家属已说明"),
        WAITING_EXCUSE("待家属说明"),
        REVIEWED("卫生院已审核"),
        CLOSED("已闭环");
        public final String label; NoShowStatus(String l) { this.label = l; } }

    /** 卫生院审核处置决定 */
    public enum NoShowDecision {
        WAIVE("减免（不收费/不扣次）"),
        CHARGE_FEE("保留空跑费"),
        DEDUCT_SERVICE("扣除一次服务"),
        ADJUST_NEXT("调整下一次巡诊"),
        PARTIAL_FEE("部分减免（调整金额）");
        public final String label; NoShowDecision(String l) { this.label = l; } }

    /** 家庭病床暂停原因 */
    public enum BedPauseReason {
        HOSPITALIZED("患者临时住院/送医"),
        FAMILY_REQUEST("家属申请暂停"),
        OTHER("其他");
        public final String label; BedPauseReason(String l) { this.label = l; } }

    /** 家庭病床暂停/复评状态 */
    public enum BedPauseStatus {
        PAUSED("暂停待复评"), REEVALUATED("医生已复评"), RESUMED("已恢复巡诊");
        public final String label; BedPauseStatus(String l) { this.label = l; } }
}
