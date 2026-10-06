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

    public enum BedApplyStatus {
        DRAFT("草稿"),
        SUBMITTED("待评估"),
        APPROVED("批准建床"),
        REJECTED("驳回"),
        SUSPENDED("暂停建床"),
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
        EXCEPTION("异常挂起"), CANCELLED("已取消");
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
        EMPTY_RUN_FEE("空跑费"),
        NURSING("护理项目"),
        CONSUMABLE("普通耗材"),
        COLDCHAIN_DRUG("冷链药品"),
        DRUG("普通药品");
        public final String label; CatalogCategory(String l) { this.label = l; } }

    public enum BillSourceType { VISIT_CARE("护理执行"), MATERIAL("耗材领用"), ORDER("医嘱费用"), EMPTY_RUN("上门未遇空跑");
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

    /** 上门未遇原因（护士现场初判） */
    public enum NoShowReason {
        NO_ANSWER("敲门无人应答"),
        FAMILY_FORGOT("家属忘记/未提前请假"),
        PHONE_UNREACHABLE("电话无法接通"),
        PATIENT_EMERGENCY("患者临时急诊/送医"),
        ADDRESS_CHANGED("地址变更/搬迁"),
        OTHER("其他");
        public final String label; NoShowReason(String l) { this.label = l; } }

    /**
     * 未遇工单流转状态：
     * PENDING_FAMILY 已上报待家属补充说明 → FAMILY_EXPLAINED 已说明待卫生院审核
     * → REVIEWED 审核处理完毕（减免/空跑费/扣次/调整下次）；
     * 疑似患者临时送医时 BED_PAUSED（家庭病床暂停、等待医生重新评估）→ REASSESSED 复评后恢复巡诊。
     */
    public enum NoShowStatus {
        PENDING_FAMILY("待家属说明"),
        FAMILY_EXPLAINED("家属已说明"),
        BED_PAUSED("病床暂停待复评"),
        REVIEWED("卫生院已审核"),
        REASSESSED("医生复评已恢复"),
        WAIVED("已减免关闭");
        public final String label; NoShowStatus(String l) { this.label = l; } }

    /** 卫生院审核决定 */
    public enum NoShowDecision {
        WAIVE("减免（情况属实不收空跑费）"),
        CHARGE_EMPTY_RUN("计空跑费"),
        DEDUCT_SERVICE("扣除一次服务（不计空跑费）"),
        ADJUST_NEXT("调整/顺延下一次巡诊"),
        SUSPEND_BED("暂停家庭病床（等待医生复评）");
        public final String label; NoShowDecision(String l) { this.label = l; } }

    /** 家属补充说明中选择的未遇事由分类 */
    public enum NoShowFamilyReason {
        EMERGENCY_TEMP("临时急诊/送医"),
        PHONE_DEAD("手机没电/未接到电话"),
        ADDRESS_CHANGE("地址变更/临时不在原址"),
        FORGOT("忘记预约"),
        OTHER("其他");
        public final String label; NoShowFamilyReason(String l) { this.label = l; } }
}
