package cn.townhc.homebed.service;

import cn.townhc.homebed.common.BusinessException;
import cn.townhc.homebed.domain.entity.*;
import cn.townhc.homebed.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 服务全链还原：对一次家庭病床服务，把申请、评估、排班、每次上门的
 * 签到/体征/照片/耗材/异常/医嘱/家属签字、账单、合规审查、风险事件
 * 按时间线组装，用于处理收费质疑、医保核查与纠纷举证。
 */
@Service
@RequiredArgsConstructor
public class TraceService {

    private final BedApplicationRepository applicationRepository;
    private final CareNeedRepository careNeedRepository;
    private final VisitPlanRepository planRepository;
    private final VisitRepository visitRepository;
    private final VisitCareResultRepository careResultRepository;
    private final VisitPhotoRepository photoRepository;
    private final VisitExceptionRepository exceptionRepository;
    private final MaterialTransactionRepository materialTxRepository;
    private final MedicalOrderRepository orderRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final ComplianceRecordRepository complianceRepository;
    private final RiskEventRepository riskRepository;
    private final NoShowRecordRepository noShowRepository;
    private final BedPauseRecordRepository bedPauseRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> byApplication(Long applicationId) {
        BedApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(404, "申请单不存在"));
        Long patientId = app.getPatient().getId();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("patient", app.getPatient());
        root.put("application", app);
        root.put("careNeeds", careNeedRepository.findByApplicationId(applicationId));

        List<VisitPlan> plans = planRepository.findByApplicationId(applicationId);
        root.put("plans", plans);

        List<Map<String, Object>> visitChain = new ArrayList<>();
        for (VisitPlan plan : plans) {
            for (Visit v : visitRepository.findByPlanIdOrderByScheduledAtAsc(plan.getId())) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("visit", v);
                node.put("careResults", careResultRepository.findByVisitId(v.getId()));
                node.put("photos", photoRepository.findByVisitId(v.getId()));
                node.put("exceptions", exceptionRepository.findByVisitId(v.getId()));
                node.put("noShows", noShowRepository.findByVisitId(v.getId()));
                node.put("materialTransactions", materialTxRepository.findByVisitId(v.getId()));
                node.put("orders", orderRepository.findByVisitId(v.getId()));
                visitChain.add(node);
            }
        }
        root.put("visitChain", visitChain);

        List<Map<String, Object>> bills = new ArrayList<>();
        for (Bill b : billRepository.findByApplicationId(applicationId)) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("bill", b);
            bm.put("items", billItemRepository.findByBillId(b.getId()));
            bm.put("compliance", complianceRepository.findByBillId(b.getId()).orElse(null));
            bills.add(bm);
        }
        root.put("bills", bills);
        root.put("riskEvents", riskRepository.findByPatientIdOrderByReportedAtDesc(patientId));

        // 审计时间线（申请单维度 + 患者维度合并）
        List<AuditLog> logs = new ArrayList<>();
        logs.addAll(auditLogRepository.findByBizNoOrderByOccurredAtAsc(app.getApplyNo()));
        Set<Long> seen = new HashSet<>();
        for (AuditLog l : logs) seen.add(l.getId());
        for (AuditLog l : auditLogRepository.findByPatientIdOrderByOccurredAtDesc(patientId)) {
            if (seen.add(l.getId())) logs.add(l);
        }
        logs.sort(Comparator.comparing(AuditLog::getOccurredAt));
        root.put("timeline", buildTimeline(app, visitChain, bills, logs));
        root.put("auditLogs", logs);
        return root;
    }

    /** 输出结构化时间线（阶段/时间/角色/事件/证据） */
    private List<Map<String, Object>> buildTimeline(BedApplication app,
                                                    List<Map<String, Object>> visitChain,
                                                    List<Map<String, Object>> bills,
                                                    List<AuditLog> logs) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        add(timeline, app.getCreatedAt(), "申请", "家属", "提交家庭病床申请 " + app.getApplyNo(),
                app.getApplyReason(), null);
        if (app.getAssessedAt() != null) {
            add(timeline, app.getAssessedAt(), "评估", "家庭医生·" + safeName(app.getDoctor()),
                    "评估" + app.getStatus().label + "，风险" + (app.getRiskLevel() == null ? "-" : app.getRiskLevel().label),
                    "距离 " + app.getDistanceMeters() + " 米；照护人：" + app.getCaregiverCondition()
                            + "；医保资格：" + (Boolean.TRUE.equals(app.getInsuranceEligible()) ? "符合" : "不符合")
                            + "；结论：" + app.getAssessmentConclusion(), null);
        }
        for (Map<String, Object> node : visitChain) {
            Visit v = (Visit) node.get("visit");
            if (v.getStartedAt() != null) {
                add(timeline, v.getStartedAt(), "上门", "护士·" + safeName(v.getNurse()),
                        v.getVisitNo() + " 签到上门",
                        "位置：" + v.getNurseLocationSnapshot(), null);
            }
            if (v.getCompletedAt() != null) {
                @SuppressWarnings("unchecked")
                List<VisitPhoto> photos = (List<VisitPhoto>) node.get("photos");
                @SuppressWarnings("unchecked")
                List<VisitCareResult> results = (List<VisitCareResult>) node.get("careResults");
                @SuppressWarnings("unchecked")
                List<MaterialTransaction> txs = (List<MaterialTransaction>) node.get("materialTransactions");
                add(timeline, v.getCompletedAt(), "巡诊记录", "护士·" + safeName(v.getNurse()),
                        v.getVisitNo() + " 完成护理 " + results.size() + " 项、领用耗材 " + txs.size() + " 项",
                        "血压 " + v.getSystolicPressure() + "/" + v.getDiastolicPressure()
                                + "；血糖 " + v.getBloodGlucose()
                                + "；压疮：" + nz(v.getPressureUlcerNote())
                                + "；换药：" + nz(v.getDressingNote())
                                + "；导尿：" + nz(v.getCatheterNote())
                                + "；用药：" + nz(v.getMedicationNote()),
                        photos.stream().map(VisitPhoto::getFilePath).toList());
                add(timeline, v.getFamilySignedAt(), "家属签字", app.getPatient().getPrimaryCaregiver(),
                        v.getVisitNo() + " 家属确认签字：" + v.getFamilySignName(),
                        "家属对本次服务项目与耗材现场确认",
                        v.getFamilySignPath() == null ? List.of() : List.of(v.getFamilySignPath()));
            }
            @SuppressWarnings("unchecked")
            List<MedicalOrder> orders = (List<MedicalOrder>) node.get("orders");
            for (MedicalOrder o : orders) {
                add(timeline, o.getCreatedAt(), "医嘱", "医生·" + safeName(o.getDoctor()),
                        o.getOrderNo() + " " + o.getOrderType().label,
                        o.getContent(), null);
                if (o.getReviewedAt() != null) {
                    add(timeline, o.getReviewedAt(), "远程复核", "医生·" + safeName(o.getReviewer()),
                            o.getOrderNo() + " 远程复核" + o.getStatus().label, o.getReviewComment(), null);
                }
            }
            @SuppressWarnings("unchecked")
            List<VisitException> exs = (List<VisitException>) node.get("exceptions");
            for (VisitException e : exs) {
                add(timeline, e.getCreatedAt(), "异常", e.getType().label,
                        v.getVisitNo() + " " + e.getType().label, e.getDescription()
                                + (e.getHandleConclusion() != null ? "；处理：" + e.getHandleConclusion() : ""),
                        e.getEvidencePath() == null ? List.of() : List.of(e.getEvidencePath()));
            }
            @SuppressWarnings("unchecked")
            List<NoShowRecord> noShows = (List<NoShowRecord>) node.get("noShows");
            for (NoShowRecord ns : noShows) {
                add(timeline, ns.getArrivedAt(), "上门未遇", "护士·" + safeName(ns.getNurse()),
                        ns.getNoShowNo() + " " + ns.getReason().label,
                        "到达定位：" + nz(ns.getArrivalLocation())
                                + "；等待 " + ns.getWaitMinutes() + " 分钟；拨打 " + ns.getCallCount()
                                + " 次（接通 " + ns.getConnectedCallCount() + "）；家属提前请假："
                                + (Boolean.TRUE.equals(ns.getEarlyLeaveApproved()) ? "是" : "否")
                                + (ns.getDecision() != null ? "；审核：" + ns.getDecision().label
                                + (ns.getApprovedFee() != null && ns.getApprovedFee().signum() > 0
                                ? " " + ns.getApprovedFee() + " 元" : "") : ""),
                        ns.getDoorPhotoPaths());
            }
        }
        // 病床暂停/复评/恢复时间线
        for (BedPauseRecord pr : bedPauseRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId())) {
            add(timeline, pr.getPausedAt(), "病床暂停", pr.getReason().label,
                    pr.getPauseNo() + " 家庭病床暂停", pr.getPauseNote(), null);
            if (pr.getReevaluatedAt() != null) {
                add(timeline, pr.getReevaluatedAt(), "医生复评", "医生·" + safeName(pr.getReevaluatedBy()),
                        pr.getPauseNo() + " 重新评估" + pr.getStatus().label, pr.getReevaluationNote(), null);
            }
            if (pr.getResumedAt() != null) {
                add(timeline, pr.getResumedAt(), "恢复巡诊", "医生·" + safeName(pr.getReevaluatedBy()),
                        pr.getPauseNo() + " 恢复家庭病床巡诊", pr.getResumeNote()
                                + (pr.getNextVisitAt() != null ? "；首次上门 " + pr.getNextVisitAt() : ""), null);
            }
        }
        for (Map<String, Object> bm : bills) {
            Bill b = (Bill) bm.get("bill");
            add(timeline, b.getCreatedAt(), "账单", "财务",
                    b.getBillNo() + " 统一账单生成（" + b.getStatus().label + "）",
                    "总额 " + b.getTotalAmount() + "，医保 " + b.getInsuranceAmount()
                            + "，自费 " + b.getSelfPayAmount(), null);
            if (b.getFamilyConfirmedAt() != null) {
                add(timeline, b.getFamilyConfirmedAt(), "家属确认", "家属·" + nz(b.getFamilyConfirmNote()),
                        b.getBillNo() + " 家属确认费用明细", b.getFamilyConfirmNote(), null);
            }
            if (b.getDisputedAt() != null) {
                add(timeline, b.getDisputedAt(), "收费质疑", "家属",
                        b.getBillNo() + " 家属质疑收费", b.getDisputeNote(), null);
            }
            if (b.getSettledAt() != null) {
                add(timeline, b.getSettledAt(), "结算", "医保经办",
                        b.getBillNo() + " 医保结算，批次 " + b.getInsuranceSettleNo(), null, null);
            }
        }
        timeline.sort(Comparator.comparing(m -> (java.time.LocalDateTime) m.get("time")));
        return timeline;
    }

    private String nz(String s) { return s == null || s.isBlank() ? "-" : s; }
    private String safeName(SysUser u) { return u == null ? "-" : u.getName(); }

    private void add(List<Map<String, Object>> timeline, java.time.LocalDateTime time,
                     String stage, String actor, String title, String detail, List<String> evidences) {
        if (time == null) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", time);
        m.put("stage", stage);
        m.put("actor", actor);
        m.put("title", title);
        m.put("detail", detail);
        m.put("evidences", evidences == null ? List.of() : evidences);
        timeline.add(m);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> auditLogs(String module) {
        if (module != null && !module.isBlank()) {
            return auditLogRepository.findByModuleOrderByOccurredAtDesc(module);
        }
        return auditLogRepository.findTop200ByOrderByOccurredAtDesc();
    }
}
