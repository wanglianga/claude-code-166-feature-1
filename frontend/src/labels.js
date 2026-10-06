// 与后端 DomainEnums 保持一致的中文标签
const L = {
  Role: { FAMILY: '家属', FAMILY_DOCTOR: '家庭医生', NURSE: '护士', ADMIN: '管理者', FINANCE: '财务', INSURANCE_AUDITOR: '医保经办' },
  BedApplyStatus: { DRAFT: '草稿', SUBMITTED: '待评估', APPROVED: '批准建床', REJECTED: '驳回', SUSPENDED: '暂停建床', PAUSED: '暂停巡诊', CANCELLED: '家属撤销' },
  RiskLevel: { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '极高风险' },
  DiseaseCategory: { POST_SURGICAL: '术后康复', DIABETES: '糖尿病', HYPERTENSION: '高血压', STROKE_REHAB: '脑卒中康复', COPD: '慢阻肺', PRESSURE_ULCER: '压疮', OTHER: '其他' },
  InsuranceType: { URBAN_EMPLOYEE: '城镇职工医保', URBAN_RESIDENT: '城乡居民医保', NONE: '无医保' },
  CareItemType: { VITALS: '生命体征监测', BLOOD_PRESSURE: '血压测量', BLOOD_GLUCOSE: '血糖测量', PRESSURE_ULCER: '压疮护理', DRESSING: '伤口换药', CATHETER: '导尿护理', INJECTION: '注射/输液', MEDICATION_REMINDER: '用药提醒与核对', HEALTH_EDUCATION: '健康指导' },
  PlanStatus: { ACTIVE: '执行中', PAUSED: '已暂停', COMPLETED: '已完成', CANCELLED: '已取消' },
  VisitStatus: { SCHEDULED: '已排班', IN_PROGRESS: '上门中', COMPLETED: '已完成', EXCEPTION: '异常挂起', NO_SHOW: '上门未遇', PAUSED: '暂停待复评', CANCELLED: '已取消' },
  ExceptionType: { PATIENT_HOSPITALIZED: '患者临时住院', FAMILY_ABSENT: '家属不在家', SUPPLY_SHORTAGE: '耗材缺货', NURSE_OVERTIME: '护士上门超时', CONDITION_WORSENED: '患者病情恶化', ROUTE_BLOCKED: '道路/天气受阻', OTHER: '其他' },
  ExceptionStatus: { REPORTED: '已上报', CONFIRMED: '已核实', RESOLVED: '已处理' },
  CareResultStatus: { DONE: '已完成', SKIPPED: '未执行', ABNORMAL: '异常中止' },
  NoShowReason: { NO_ANSWER: '敲门无人应答', FAMILY_AWAY: '家属临时外出未请假', PATIENT_HOSPITALIZED: '患者临时送医', ADDRESS_CHANGED: '地址变更未更新', PHONE_UNREACHABLE: '电话联系不上', OTHER: '其他' },
  FamilyExcuseReason: { EMERGENCY: '临时急诊/送医', PHONE_DEAD: '手机没电/未接到', ADDRESS_CHANGE: '地址变更', FAMILY_EMERGENCY: '家中突发急事', WEATHER_ROUTE: '天气/道路原因', OTHER: '其他' },
  NoShowStatus: { REPORTED: '已登记未遇', EXPLAINED: '家属已说明', WAITING_EXCUSE: '待家属说明', REVIEWED: '卫生院已审核', CLOSED: '已闭环' },
  NoShowDecision: { WAIVE: '减免（不收费/不扣次）', CHARGE_FEE: '保留空跑费', DEDUCT_SERVICE: '扣除一次服务', ADJUST_NEXT: '调整下一次巡诊', PARTIAL_FEE: '部分减免（调整金额）' },
  BedPauseReason: { HOSPITALIZED: '患者临时住院/送医', FAMILY_REQUEST: '家属申请暂停', OTHER: '其他' },
  BedPauseStatus: { PAUSED: '暂停待复评', REEVALUATED: '医生已复评', RESUMED: '已恢复巡诊' },
  OrderType: { PRESCRIPTION: '调整处方', FOLLOW_UP: '安排复诊', REFERRAL: '建议转诊', CARE_PLAN_CHANGE: '调整护理方案' },
  OrderStatus: { PROPOSED: '已下达', REMOTE_REVIEWED: '远程复核通过', CONFIRMED: '已确认', EXECUTED: '已执行', VOID: '已作废' },
  CatalogCategory: { VISIT_FEE: '巡诊费', NURSING: '护理项目', CONSUMABLE: '普通耗材', COLDCHAIN_DRUG: '冷链药品', DRUG: '普通药品', NO_SHOW_FEE: '空跑费' },
  BillSourceType: { VISIT_CARE: '护理执行', MATERIAL: '耗材领用', ORDER: '医嘱费用', NO_SHOW_FEE: '上门空跑费' },
  BillStatus: { DRAFT: '待确认', CONFIRMED: '家属已确认', INSURANCE_REVIEWED: '医保复核完成', DISPUTED: '家属质疑', SETTLED: '已结算' },
  ComplianceStatus: { PENDING: '待审查', COMPLIANT: '合规', VIOLATION: '存疑/违规' },
  RiskSeverity: { LOW: '一般', MEDIUM: '较重', HIGH: '严重' },
  RiskEventStatus: { OPEN: '待处理', CONFIRMED: '已确认', CLOSED: '已闭环' },
  MaterialTxType: { OUT: '领用消耗', IN: '入库', ADJUST: '盘点调整' }
}

export function label(group, value) {
  if (value === null || value === undefined || value === '') return '-'
  return L[group]?.[value] || value
}

export function tagType(group, value) {
  const map = {
    BedApplyStatus: { SUBMITTED: 'warning', APPROVED: 'success', REJECTED: 'danger', PAUSED: 'warning', SUSPENDED: 'info', CANCELLED: 'info' },
    RiskLevel: { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' },
    VisitStatus: { SCHEDULED: 'primary', IN_PROGRESS: 'warning', COMPLETED: 'success', EXCEPTION: 'danger', NO_SHOW: 'danger', PAUSED: 'info', CANCELLED: 'info' },
    ExceptionStatus: { REPORTED: 'danger', CONFIRMED: 'warning', RESOLVED: 'success' },
    OrderStatus: { PROPOSED: 'warning', REMOTE_REVIEWED: 'primary', EXECUTED: 'success', VOID: 'info' },
    BillStatus: { DRAFT: 'warning', CONFIRMED: 'primary', INSURANCE_REVIEWED: 'success', DISPUTED: 'danger', SETTLED: 'success' },
    ComplianceStatus: { PENDING: 'info', COMPLIANT: 'success', VIOLATION: 'danger' },
    RiskSeverity: { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' },
    RiskEventStatus: { OPEN: 'danger', CONFIRMED: 'warning', CLOSED: 'success' },
    NoShowStatus: { REPORTED: 'danger', WAITING_EXCUSE: 'warning', EXPLAINED: 'primary', REVIEWED: 'success', CLOSED: 'info' },
    BedPauseStatus: { PAUSED: 'danger', REEVALUATED: 'warning', RESUMED: 'success' }
  }
  return map[group]?.[value] || 'info'
}

export const ENUMS = L
