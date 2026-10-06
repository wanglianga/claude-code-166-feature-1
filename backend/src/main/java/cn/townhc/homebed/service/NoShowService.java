package cn.townhc.homebed.service;

import cn.townhc.homebed.common.BusinessException;
import cn.townhc.homebed.domain.entity.*;
import cn.townhc.homebed.domain.enums.DomainEnums.*;
import cn.townhc.homebed.repository.*;
import cn.townhc.homebed.security.LoginUser;
import cn.townhc.homebed.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 上门未遇（家属未开门）处理：
 * 1) 护士固化现场证据——到达定位、电话拨打记录、等待时长、门口照片，任务挂起；
 * 2) 未提前请假的，先允许家属提交补充说明（临时急诊/手机没电/地址变更等）；
 * 3) 卫生院审核决定：减免 / 计空跑费 / 扣除一次服务 / 调整下一次巡诊 / 暂停病床；
 * 4) 患者临时送医时家庭病床改为暂停，家庭医生重新评估后才恢复巡诊。
 * 审核确认的空跑费由统一账单按账期计入（见 BillingService）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowService {

    private final NoShowCaseRepository noShowRepository;
    private final VisitRepository visitRepository;
    private final VisitPlanRepository planRepository;
    private final BedApplicationRepository applicationRepository;
    private final ChargeCatalogRepository catalogRepository;
    private final SysUserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public record PhoneCall(String contact, String phone, LocalDateTime at,
                            Integer durationSeconds, String result) {}

    public record ReportCommand(
            LocalDateTime arrivedAt, LocalDateTime leftAt,
            String arrivalLocationSnapshot, Double longitude, Double latitude,
            NoShowReason reason, Boolean advanceLeaveNotified,
            Integer waitMinutes, List<PhoneCall> phoneCalls,
            List<String> doorPhotoPaths, String nurseNote) {}

    public record FamilyExplanationCommand(NoShowFamilyReason familyReason, String familyNote,
                                           String familyEvidencePath) {}

    public record ReviewCommand(NoShowDecision decision, String decisionNote,
                                BigDecimal emptyRunFee, LocalDateTime rescheduleTime) {}

    public record ReassessCommand(String reassessNote, LocalDateTime resumeTime) {}

    @Transactional
    public NoShowCase report(Long visitId, ReportCommand cmd) {
        LoginUser me = SecurityUtils.current();
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new BusinessException(404, "巡诊任务不存在"));
        if (me.role() == Role.NURSE && (visit.getNurse() == null
                || !visit.getNurse().getId().equals(me.id()))) {
            throw new BusinessException(403, "该任务不属于当前护士");
        }
        if (visit.getStatus() == VisitStatus.COMPLETED) {
            throw new BusinessException("巡诊已完成，无需登记上门未遇");
        }
        if (!noShowRepository.findByVisitId(visitId).isEmpty()) {
            throw new BusinessException("该任务已登记过上门未遇，请到“上门未遇处理”中继续办理");
        }
        if (cmd.reason() == null) throw new BusinessException("请选择未遇现场原因");
        if (cmd.waitMinutes() == null || cmd.waitMinutes() < 0) {
            throw new BusinessException("请填写门口等待时长（分钟）");
        }
        if (cmd.arrivalLocationSnapshot() == null || cmd.arrivalLocationSnapshot().isBlank()) {
            throw new BusinessException("请记录到达定位");
        }
        if (cmd.phoneCalls() == null || cmd.phoneCalls().isEmpty()) {
            throw new BusinessException("请至少登记一条电话拨打记录（联系人/电话/结果）");
        }
        if (cmd.doorPhotoPaths() == null || cmd.doorPhotoPaths().isEmpty()) {
            throw new BusinessException("请至少上传一张门口照片作为未遇证据");
        }

        LocalDateTime now = LocalDateTime.now();
        NoShowCase c = new NoShowCase();
        c.setCaseNo(ApplicationService.generateNo("WY"));
        c.setVisit(visit);
        c.setPatient(visit.getPatient());
        c.setNurse(visit.getNurse() != null ? visit.getNurse()
                : userRepository.findById(me.id()).orElse(null));
        c.setArrivedAt(cmd.arrivedAt() != null ? cmd.arrivedAt()
                : (visit.getStartedAt() != null ? visit.getStartedAt() : now.minusMinutes(cmd.waitMinutes())));
        c.setLeftAt(cmd.leftAt() != null ? cmd.leftAt() : now);
        c.setArrivalLocationSnapshot(cmd.arrivalLocationSnapshot());
        c.setArrivalLongitude(cmd.longitude());
        c.setArrivalLatitude(cmd.latitude());
        c.setReason(cmd.reason());
        c.setAdvanceLeaveNotified(Boolean.TRUE.equals(cmd.advanceLeaveNotified()));
        c.setWaitMinutes(cmd.waitMinutes());
        c.setPhoneCallsJson(writeJson(cmd.phoneCalls() == null ? List.of() : cmd.phoneCalls()));
        c.setDoorPhotoPathsJson(writeJson(cmd.doorPhotoPaths() == null ? List.of() : cmd.doorPhotoPaths()));
        c.setNurseNote(cmd.nurseNote());

        // 患者临时送医：直接暂停家庭病床，等待医生重新评估；其余先等家属补充说明
        boolean emergency = cmd.reason() == NoShowReason.PATIENT_EMERGENCY;
        c.setStatus(emergency ? NoShowStatus.BED_PAUSED : NoShowStatus.PENDING_FAMILY);
        c = noShowRepository.save(c);

        visit.setStatus(VisitStatus.EXCEPTION);
        visit.setArrangementNote("上门未遇（" + cmd.reason().label + "），等待"
                + (emergency ? "医生重新评估后恢复" : "家属补充说明与卫生院审核"));
        visitRepository.save(visit);

        if (emergency) {
            pauseBed(c, "护士上门未遇，疑似患者临时送医，先行暂停家庭病床等待医生复评");
        }

        auditService.log(me, "上门未遇", emergency ? "登记未遇并暂停病床" : "登记上门未遇",
                c.getId(), c.getCaseNo(), visit.getPatient().getId(),
                "原因=" + cmd.reason().label + "；等待 " + cmd.waitMinutes() + " 分钟；提前请假="
                        + c.getAdvanceLeaveNotified() + "；定位=" + cmd.arrivalLocationSnapshot()
                        + "；拨打电话 " + (cmd.phoneCalls() == null ? 0 : cmd.phoneCalls().size())
                        + " 通；门口照片 " + (cmd.doorPhotoPaths() == null ? 0 : cmd.doorPhotoPaths().size()) + " 张");
        init(c.getPatient());
        init(c.getNurse());
        return c;
    }

    @Transactional
    public NoShowCase submitFamilyExplanation(Long caseId, FamilyExplanationCommand cmd) {
        LoginUser me = SecurityUtils.current();
        NoShowCase c = mustGet(caseId);
        if (me.role() == Role.FAMILY) {
            if (c.getPatient().getFamilyUser() == null
                    || !c.getPatient().getFamilyUser().getId().equals(me.id())) {
                throw new BusinessException(403, "只能为本人绑定的患者提交说明");
            }
        }
        if (cmd.familyReason() == null) throw new BusinessException("请选择未遇事由");
        if (cmd.familyNote() == null || cmd.familyNote().isBlank()) {
            throw new BusinessException("请填写补充说明内容");
        }
        c.setFamilyReason(cmd.familyReason());
        c.setFamilyNote(cmd.familyNote());
        c.setFamilyEvidencePath(cmd.familyEvidencePath());
        c.setFamilyUser(userRepository.findById(me.id()).orElse(null));
        c.setFamilySubmittedAt(LocalDateTime.now());
        // 病床暂停中的工单仍以“医生复评”为主线，说明供医生与卫生院参考，状态不变
        if (c.getStatus() != NoShowStatus.BED_PAUSED) {
            c.setStatus(NoShowStatus.FAMILY_EXPLAINED);
        }
        c = noShowRepository.save(c);
        auditService.log(me, "上门未遇", "家属提交补充说明", c.getId(), c.getCaseNo(),
                c.getPatient().getId(), "事由=" + cmd.familyReason().label + "；" + cmd.familyNote());
        return c;
    }

    @Transactional
    public NoShowCase review(Long caseId, ReviewCommand cmd) {
        LoginUser me = SecurityUtils.current();
        NoShowCase c = mustGet(caseId);
        if (me.role() != Role.ADMIN && me.role() != Role.FINANCE) {
            throw new BusinessException(403, "仅卫生院管理者/财务可审核未遇费用");
        }
        if (cmd.decision() == null) throw new BusinessException("请选择审核决定");
        NoShowDecision decision = cmd.decision();
        if (cmd.decisionNote() == null || cmd.decisionNote().isBlank()) {
            throw new BusinessException("请填写审核意见");
        }
        c.setDecision(decision);
        c.setDecisionNote(cmd.decisionNote());
        c.setReviewer(userRepository.findById(me.id()).orElse(null));
        c.setReviewedAt(LocalDateTime.now());

        Visit visit = c.getVisit();
        // 病床已因“患者临时送医”暂停：费用类决定可先行记录（空跑费仍按账期出账），
        // 但不改变暂停状态与任务，巡诊恢复只能走家庭医生“重新评估”。
        boolean bedAlreadyPaused = c.getStatus() == NoShowStatus.BED_PAUSED;
        if (bedAlreadyPaused && decision != NoShowDecision.SUSPEND_BED) {
            c.setStatus(NoShowStatus.BED_PAUSED);
            if (decision == NoShowDecision.CHARGE_EMPTY_RUN) {
                BigDecimal fee = cmd.emptyRunFee() != null ? cmd.emptyRunFee() : defaultEmptyRunFee();
                if (fee.signum() < 0) throw new BusinessException("空跑费金额不能为负");
                c.setEmptyRunFee(fee);
            } else {
                c.setEmptyRunFee(BigDecimal.ZERO);
            }
            c.setServiceDeducted(decision == NoShowDecision.DEDUCT_SERVICE);
            visit.setArrangementNote("病床暂停待医生复评；卫生院费用决定已先行记录："
                    + decision.label + "。" + cmd.decisionNote());
            visitRepository.save(visit);
        } else switch (decision) {
            case WAIVE -> {
                c.setStatus(NoShowStatus.WAIVED);
                c.setEmptyRunFee(BigDecimal.ZERO);
                c.setServiceDeducted(false);
                applyVisitClose(visit, cmd.rescheduleTime(),
                        "上门未遇经核实予以减免，不收空跑费");
            }
            case CHARGE_EMPTY_RUN -> {
                BigDecimal fee = cmd.emptyRunFee() != null ? cmd.emptyRunFee() : defaultEmptyRunFee();
                if (fee.signum() < 0) throw new BusinessException("空跑费金额不能为负");
                c.setEmptyRunFee(fee);
                c.setServiceDeducted(false);
                c.setStatus(NoShowStatus.REVIEWED);
                applyVisitClose(visit, cmd.rescheduleTime(),
                        "上门未遇审核计空跑费 " + fee + " 元"
                                + (Boolean.TRUE.equals(c.getAdvanceLeaveNotified()) ? "（家属曾提前请假，请复核）" : ""));
            }
            case DEDUCT_SERVICE -> {
                c.setEmptyRunFee(BigDecimal.ZERO);
                c.setServiceDeducted(true);
                c.setStatus(NoShowStatus.REVIEWED);
                applyVisitClose(visit, cmd.rescheduleTime(),
                        "上门未遇审核扣除一次巡诊服务，不计空跑费");
            }
            case ADJUST_NEXT -> {
                c.setEmptyRunFee(BigDecimal.ZERO);
                c.setServiceDeducted(false);
                c.setStatus(NoShowStatus.REVIEWED);
                String note = "上门未遇审核通过，调整/顺延下一次巡诊";
                if (cmd.rescheduleTime() != null) {
                    rescheduleVisit(visit, cmd.rescheduleTime(), note);
                } else {
                    visit.setStatus(VisitStatus.CANCELLED);
                    visit.setArrangementNote(note + "（本次任务作废，并入下次巡诊）");
                    visitRepository.save(visit);
                }
            }
            case SUSPEND_BED -> {
                c.setStatus(NoShowStatus.BED_PAUSED);
                c.setEmptyRunFee(BigDecimal.ZERO);
                pauseBed(c, cmd.decisionNote());
                visit.setStatus(VisitStatus.EXCEPTION);
                visit.setArrangementNote("上门未遇：患者临时送医，家庭病床暂停，等待医生重新评估");
                visitRepository.save(visit);
            }
        }
        c = noShowRepository.save(c);
        auditService.log(me, "上门未遇", "卫生院审核", c.getId(), c.getCaseNo(),
                c.getPatient().getId(), "决定=" + decision.label + "；空跑费="
                        + c.getEmptyRunFee() + "；扣次=" + c.getServiceDeducted()
                        + "；意见=" + cmd.decisionNote());
        init(c.getPatient());
        init(c.getNurse());
        return c;
    }

    /** 家庭医生重新评估：恢复家庭病床与巡诊计划，可同时重新安排本次巡诊时间 */
    @Transactional
    public NoShowCase reassess(Long caseId, ReassessCommand cmd) {
        LoginUser me = SecurityUtils.current();
        NoShowCase c = mustGet(caseId);
        if (me.role() != Role.FAMILY_DOCTOR && me.role() != Role.ADMIN) {
            throw new BusinessException(403, "仅家庭医生可做重新评估");
        }
        if (c.getStatus() != NoShowStatus.BED_PAUSED) {
            throw new BusinessException("仅暂停待复评的工单可执行复评，当前状态：" + c.getStatus().label);
        }
        if (cmd.reassessNote() == null || cmd.reassessNote().isBlank()) {
            throw new BusinessException("请填写重新评估结论（出院/病情稳定等）");
        }

        // 恢复家庭病床与计划
        BedApplication app = c.getVisit().getPlan().getApplication();
        app.setStatus(BedApplyStatus.APPROVED);
        applicationRepository.save(app);
        VisitPlan plan = c.getVisit().getPlan();
        plan.setStatus(PlanStatus.ACTIVE);
        planRepository.save(plan);

        c.setReassessDoctor(userRepository.findById(me.id()).orElse(null));
        c.setReassessNote(cmd.reassessNote());
        c.setReassessedAt(LocalDateTime.now());
        c.setStatus(NoShowStatus.REASSESSED);

        Visit visit = c.getVisit();
        if (cmd.resumeTime() != null) {
            rescheduleVisit(visit, cmd.resumeTime(), "医生复评后恢复巡诊，重新安排至 " + cmd.resumeTime());
            c.setResumedVisit(visit);
        } else {
            // 未指定时间：本次未遇任务作废，按既有计划的后续任务恢复
            visit.setStatus(VisitStatus.CANCELLED);
            visit.setArrangementNote("医生复评恢复病床，本次未遇任务并入后续计划巡诊：" + cmd.reassessNote());
            visitRepository.save(visit);
        }
        c = noShowRepository.save(c);
        auditService.log(me, "上门未遇", "医生复评恢复", c.getId(), c.getCaseNo(),
                c.getPatient().getId(), "复评结论=" + cmd.reassessNote()
                        + (cmd.resumeTime() != null ? "；恢复巡诊时间=" + cmd.resumeTime() : ""));
        init(c.getPatient());
        init(c.getNurse());
        return c;
    }

    @Transactional(readOnly = true)
    public List<NoShowCase> list(String status, Long patientId) {
        LoginUser me = SecurityUtils.current();
        List<NoShowCase> list;
        if (status != null && !status.isBlank()) {
            list = noShowRepository.findByStatusOrderByCreatedAtDesc(NoShowStatus.valueOf(status));
        } else if (patientId != null) {
            list = noShowRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        } else {
            list = noShowRepository.findAllByOrderByCreatedAtDesc();
        }
        List<NoShowCase> result = new ArrayList<>();
        for (NoShowCase c : list) {
            // 列表需要展示患者/护士姓名，事务内显式初始化懒加载关联
            init(c.getPatient());
            init(c.getNurse());
            result.add(c);
        }
        if (me.role() == Role.FAMILY) {
            return result.stream()
                    .filter(c -> c.getPatient().getFamilyUser() != null
                            && c.getPatient().getFamilyUser().getId().equals(me.id()))
                    .toList();
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        NoShowCase c = mustGet(id);
        LoginUser me = SecurityUtils.current();
        if (me.role() == Role.FAMILY && (c.getPatient().getFamilyUser() == null
                || !c.getPatient().getFamilyUser().getId().equals(me.id()))) {
            throw new BusinessException(403, "只能查看本人相关工单");
        }
        // 显式初始化详情需要的关联图
        init(c.getPatient());
        init(c.getPatient().getFamilyUser());
        init(c.getNurse());
        init(c.getFamilyUser());
        init(c.getReviewer());
        init(c.getReassessDoctor());
        Visit visit = c.getVisit();
        init(visit);
        init(visit.getPlan());
        init(visit.getPlan().getApplication());
        init(visit.getNurse());
        init(c.getResumedVisit());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("noShow", c);
        m.put("application", visit.getPlan().getApplication());
        m.put("plan", visit.getPlan());
        m.put("phoneCalls", readCalls(c.getPhoneCallsJson()));
        m.put("doorPhotoPaths", readStrings(c.getDoorPhotoPathsJson()));
        return m;
    }

    private void init(Object proxy) {
        if (proxy != null) org.hibernate.Hibernate.initialize(proxy);
    }

    // ===== internal =====

    private NoShowCase mustGet(Long id) {
        return noShowRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "上门未遇工单不存在"));
    }

    private void pauseBed(NoShowCase c, String reason) {
        BedApplication app = c.getVisit().getPlan().getApplication();
        app.setStatus(BedApplyStatus.SUSPENDED);
        applicationRepository.save(app);
        VisitPlan plan = c.getVisit().getPlan();
        plan.setStatus(PlanStatus.PAUSED);
        planRepository.save(plan);
        c.setBedPausedAt(LocalDateTime.now());
        noShowRepository.save(c);
        auditService.log(null, "家庭病床", "暂停病床", app.getId(), app.getApplyNo(),
                c.getPatient().getId(), reason);
    }

    private void applyVisitClose(Visit visit, LocalDateTime rescheduleTime, String note) {
        if (rescheduleTime != null) {
            rescheduleVisit(visit, rescheduleTime, note + "；改期至 " + rescheduleTime);
        } else {
            visit.setStatus(VisitStatus.CANCELLED);
            visit.setArrangementNote(note + "（本次未遇任务关闭）");
            visitRepository.save(visit);
        }
    }

    private void rescheduleVisit(Visit visit, LocalDateTime time, String note) {
        visit.setScheduledAt(time);
        visit.setStatus(VisitStatus.SCHEDULED);
        visit.setArrangementNote(note);
        visitRepository.save(visit);
    }

    /** 默认空跑费：取当前有效收费目录中的“空跑费”条目 */
    private BigDecimal defaultEmptyRunFee() {
        return catalogRepository.findByActiveTrueOrderByCategoryAscCodeAsc().stream()
                .filter(x -> x.getCategory() == CatalogCategory.EMPTY_RUN_FEE)
                .map(ChargeCatalog::getUnitPrice)
                .findFirst()
                .orElseThrow(() -> new BusinessException("收费目录中未配置“空跑费”，请联系财务先在收费医保目录中维护"));
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("未遇证据序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    private List<PhoneCall> readCalls(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
