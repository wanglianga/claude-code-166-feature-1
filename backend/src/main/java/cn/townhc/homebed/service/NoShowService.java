package cn.townhc.homebed.service;

import cn.townhc.homebed.common.BusinessException;
import cn.townhc.homebed.common.Jsons;
import cn.townhc.homebed.domain.entity.*;
import cn.townhc.homebed.domain.enums.DomainEnums.*;
import cn.townhc.homebed.repository.*;
import cn.townhc.homebed.security.LoginUser;
import cn.townhc.homebed.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 上门未遇（空跑）改约与费用处理。
 *
 * <p>业务闭环：
 * <ol>
 *   <li>护士按计划上门但家属未开门：登记到达定位、电话拨打、等待时长、门口照片，
 *       任务置为“上门未遇”；系统给家属留出补充说明期（默认 24 小时）。</li>
 *   <li>患者临时送医：联动把家庭病床与巡诊计划置为“暂停”，后续任务挂起，
 *       等待家庭医生重新评估后恢复。</li>
 *   <li>家属可在费用核定前提交补充说明（临时急诊/手机没电/地址变更等）及佐证。</li>
 *   <li>卫生院审核：减免、保留空跑费、部分减免、扣除一次服务或调整下一次巡诊。
 *       核定收费的未遇记录在账单生成时按“上门空跑费”出账，且只出一次。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class NoShowService {

    /** 家属补充说明期限（小时），期限内卫生院不得核定空跑费用 */
    static final int EXCUSE_WINDOW_HOURS = 24;
    /** 空跑费目录编码（DataSeeder 初始化，可被同编码新版本替代） */
    static final String NO_SHOW_FEE_CODE = "KP001";

    private final NoShowRecordRepository noShowRepository;
    private final FamilyExcuseRepository excuseRepository;
    private final BedPauseRecordRepository pauseRepository;
    private final VisitRepository visitRepository;
    private final VisitPlanRepository planRepository;
    private final BedApplicationRepository applicationRepository;
    private final ChargeCatalogRepository catalogRepository;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public record CallLog(String phone, Boolean connected, Integer durationSec, String note) {}

    public record RegisterCommand(
            NoShowReason reason,
            LocalDateTime arrivedAt,
            String arrivalLocation,
            Double longitude,
            Double latitude,
            List<String> doorPhotoPaths,
            String dialedPhone,
            List<CallLog> callLogs,
            Integer waitMinutes,
            String nurseNote,
            Boolean earlyLeaveApproved,
            /** 患者已临时送医：联动暂停家庭病床 */
            Boolean hospitalized,
            String hospitalNote
    ) {}

    @Transactional
    public NoShowRecord register(Long visitId, RegisterCommand cmd) {
        LoginUser me = SecurityUtils.current();
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new BusinessException(404, "巡诊任务不存在"));
        if (me.role() == Role.NURSE && (visit.getNurse() == null
                || !visit.getNurse().getId().equals(me.id()))) {
            throw new BusinessException(403, "该任务不属于当前护士");
        }
        if (visit.getStatus() == VisitStatus.COMPLETED || visit.getStatus() == VisitStatus.NO_SHOW
                || visit.getStatus() == VisitStatus.CANCELLED) {
            throw new BusinessException("当前任务状态[" + visit.getStatus().label + "]不可登记上门未遇");
        }
        if (cmd.doorPhotoPaths() == null || cmd.doorPhotoPaths().isEmpty()) {
            throw new BusinessException("必须至少上传一张门口照片作为未遇证据");
        }
        if (cmd.waitMinutes() == null || cmd.waitMinutes() < 0) {
            throw new BusinessException("请填写现场等待时长（分钟）");
        }

        LocalDateTime arrived = cmd.arrivedAt() != null ? cmd.arrivedAt() : LocalDateTime.now();
        NoShowRecord ns = new NoShowRecord();
        ns.setNoShowNo(ApplicationService.generateNo("KP"));
        ns.setVisit(visit);
        ns.setPatient(visit.getPatient());
        ns.setNurse(visit.getNurse());
        ns.setReason(cmd.reason() == null ? NoShowReason.NO_ANSWER : cmd.reason());
        ns.setArrivedAt(arrived);
        ns.setArrivalLocation(cmd.arrivalLocation());
        ns.setArrivalLongitude(cmd.longitude());
        ns.setArrivalLatitude(cmd.latitude());
        ns.setDoorPhotoPathsJson(Jsons.write(cmd.doorPhotoPaths()));
        ns.setDialedPhone(cmd.dialedPhone());
        ns.setWaitMinutes(cmd.waitMinutes());
        ns.setWaitStartedAt(arrived);
        ns.setWaitEndedAt(arrived.plusMinutes(cmd.waitMinutes()));
        ns.setNurseNote(cmd.nurseNote());
        ns.setEarlyLeaveApproved(Boolean.TRUE.equals(cmd.earlyLeaveApproved()));

        List<CallLog> logs = cmd.callLogs() == null ? List.of() : cmd.callLogs();
        ns.setCallLogsJson(Jsons.write(logs));
        ns.setCallCount(logs.size());
        ns.setConnectedCallCount((int) logs.stream()
                .filter(l -> Boolean.TRUE.equals(l.connected())).count());

        LocalDateTime now = LocalDateTime.now();
        ns.setStatus(NoShowStatus.WAITING_EXCUSE);
        ns.setExcuseDeadline(now.plusHours(EXCUSE_WINDOW_HOURS));
        ns.setBilled(false);
        ns = noShowRepository.save(ns);

        visit.setStatus(VisitStatus.NO_SHOW);
        visit.setCompletedAt(now);
        visit.setArrangementNote("上门未遇（" + ns.getReason().label + "），等待家属补充说明/卫生院处理；单号 "
                + ns.getNoShowNo());
        visitRepository.save(visit);

        auditService.log(me, "上门未遇", "登记未遇", ns.getId(), ns.getNoShowNo(),
                visit.getPatient().getId(),
                "等待 " + ns.getWaitMinutes() + " 分钟，拨打 " + ns.getCallCount()
                        + " 次（接通 " + ns.getConnectedCallCount() + "），原因：" + ns.getReason().label
                        + "；家属是否提前请假：" + (ns.getEarlyLeaveApproved() ? "是" : "否"));

        // 患者临时送医 → 暂停家庭病床，等待医生复评
        if (Boolean.TRUE.equals(cmd.hospitalized()) || cmd.reason() == NoShowReason.PATIENT_HOSPITALIZED) {
            pauseBedInternal(visit, ns, BedPauseReason.HOSPITALIZED,
                    cmd.hospitalNote() != null ? cmd.hospitalNote()
                            : "护士上门得知患者临时送医，暂停巡诊，待医生重新评估", me);
        }
        touchNoShow(ns);
        return ns;
    }

    @Transactional(readOnly = true)
    public List<NoShowRecord> list(String status, Long patientId) {
        LoginUser me = SecurityUtils.current();
        List<NoShowRecord> list;
        if (status != null && !status.isBlank()) {
            list = noShowRepository.findByStatusOrderByCreatedAtDesc(NoShowStatus.valueOf(status));
        } else if (patientId != null) {
            list = noShowRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        } else {
            list = new ArrayList<>(noShowRepository.findAll());
            list.sort(Comparator.comparing(NoShowRecord::getCreatedAt).reversed());
        }
        List<NoShowRecord> result = new ArrayList<>();
        for (NoShowRecord ns : list) {
            if (me.role() == Role.FAMILY && (ns.getPatient().getFamilyUser() == null
                    || !ns.getPatient().getFamilyUser().getId().equals(me.id()))) {
                continue;
            }
            touchNoShow(ns);
            result.add(ns);
        }
        return result;
    }

    /** 在事务内初始化懒加载关联，保证列表/详情 JSON 能带上患者、护士、审核人与暂停单等信息 */
    private void touchNoShow(NoShowRecord ns) {
        touchPatient(ns.getPatient());
        if (ns.getNurse() != null) ns.getNurse().getName();
        if (ns.getReviewer() != null) ns.getReviewer().getName();
        if (ns.getVisit() != null) {
            ns.getVisit().getVisitNo();
            ns.getVisit().getScheduledAt();
        }
        if (ns.getAdjustedVisit() != null) ns.getAdjustedVisit().getVisitNo();
        if (ns.getBedPause() != null) {
            ns.getBedPause().getPauseNo();
            ns.getBedPause().getStatus();
            if (ns.getBedPause().getApplication() != null) ns.getBedPause().getApplication().getBedNo();
        }
    }

    private void touchPatient(Patient p) {
        if (p == null) return;
        p.getName();
        p.getAddress();
        p.getPrimaryContactPhone();
        if (p.getFamilyUser() != null) p.getFamilyUser().getId();
    }

    private void touchPause(BedPauseRecord rec) {
        touchPatient(rec.getPatient());
        if (rec.getApplication() != null) {
            rec.getApplication().getApplyNo();
            rec.getApplication().getBedNo();
            rec.getApplication().getStatus();
        }
        if (rec.getPausedBy() != null) rec.getPausedBy().getName();
        if (rec.getReevaluatedBy() != null) rec.getReevaluatedBy().getName();
        if (rec.getSourceVisit() != null) rec.getSourceVisit().getVisitNo();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        NoShowRecord ns = noShowRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "未遇记录不存在"));
        LoginUser me = SecurityUtils.current();
        if (me.role() == Role.FAMILY && (ns.getPatient().getFamilyUser() == null
                || !ns.getPatient().getFamilyUser().getId().equals(me.id()))) {
            throw new BusinessException(403, "只能查看本人相关记录");
        }
        touchNoShow(ns);
        List<FamilyExcuse> excuses = excuseRepository.findByNoShowIdOrderByCreatedAtDesc(id);
        for (FamilyExcuse ex : excuses) {
            if (ex.getSubmitUser() != null) ex.getSubmitUser().getName();
            touchPatient(ex.getPatient());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("noShow", ns);
        map.put("doorPhotoPaths", ns.getDoorPhotoPaths());
        map.put("callLogs", ns.getCallLogs());
        map.put("excuses", excuses);
        map.put("bedPause", ns.getBedPause());
        map.put("standardFee", standardNoShowFee());
        return map;
    }

    /** 家属提交补充说明（费用核定前） */
    @Transactional
    public FamilyExcuse submitExcuse(Long noShowId, FamilyExcuseReason reason, String content,
                                     List<String> evidencePaths, String expectedHandling) {
        LoginUser me = SecurityUtils.current();
        NoShowRecord ns = noShowRepository.findById(noShowId)
                .orElseThrow(() -> new BusinessException(404, "未遇记录不存在"));
        if (me.role() == Role.FAMILY && (ns.getPatient().getFamilyUser() == null
                || !ns.getPatient().getFamilyUser().getId().equals(me.id()))) {
            throw new BusinessException(403, "只能为本人相关记录提交说明");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("请填写补充说明内容");
        }
        if (ns.getStatus() == NoShowStatus.REVIEWED || ns.getStatus() == NoShowStatus.CLOSED) {
            throw new BusinessException("卫生院已完成审核，不能再补充说明，可在账单质疑环节申诉");
        }
        FamilyExcuse ex = new FamilyExcuse();
        ex.setExcuseNo(ApplicationService.generateNo("SM"));
        ex.setNoShow(ns);
        ex.setPatient(ns.getPatient());
        ex.setSubmitUser(userRepository.findById(me.id()).orElse(null));
        ex.setReason(reason == null ? FamilyExcuseReason.OTHER : reason);
        ex.setContent(content);
        ex.setEvidencePathsJson(Jsons.write(evidencePaths));
        ex.setExpectedHandling(expectedHandling);
        ex.setSubmittedAt(LocalDateTime.now());
        ex = excuseRepository.save(ex);

        ns.setStatus(NoShowStatus.EXPLAINED);
        noShowRepository.save(ns);
        auditService.log(me, "上门未遇", "家属补充说明", ex.getId(), ns.getNoShowNo(),
                ns.getPatient().getId(), "事由：" + ex.getReason().label + "；" + content);
        return ex;
    }

    public record ReviewCommand(
            NoShowDecision decision,
            BigDecimal approvedFee,
            Integer deductServiceCount,
            LocalDateTime nextVisitTime,
            Long adjustVisitId,
            String reviewNote
    ) {}

    /** 卫生院审核：减免 / 保留空跑费 / 部分减免 / 扣次 / 调整下一次巡诊 */
    @Transactional
    public NoShowRecord review(Long noShowId, ReviewCommand cmd) {
        LoginUser me = SecurityUtils.current();
        if (me.role() != Role.ADMIN && me.role() != Role.FINANCE) {
            throw new BusinessException(403, "仅卫生院可审核空跑费用");
        }
        NoShowRecord ns = noShowRepository.findById(noShowId)
                .orElseThrow(() -> new BusinessException(404, "未遇记录不存在"));
        if (ns.getStatus() == NoShowStatus.REVIEWED || ns.getStatus() == NoShowStatus.CLOSED) {
            throw new BusinessException("该未遇记录已审核");
        }
        NoShowDecision decision = cmd.decision() == null ? NoShowDecision.WAIVE : cmd.decision();
        List<FamilyExcuse> excuses = excuseRepository.findByNoShowIdOrderByCreatedAtDesc(noShowId);
        boolean deadlinePassed = ns.getExcuseDeadline() != null
                && LocalDateTime.now().isAfter(ns.getExcuseDeadline());

        // 收费/扣次类决定必须先给足家属说明机会：已说明或说明期已过
        boolean feeDecision = decision == NoShowDecision.CHARGE_FEE
                || decision == NoShowDecision.PARTIAL_FEE
                || decision == NoShowDecision.DEDUCT_SERVICE;
        if (feeDecision && excuses.isEmpty() && !deadlinePassed) {
            throw new BusinessException("家属补充说明期尚未结束（截止 " + ns.getExcuseDeadline()
                    + "），暂不能核定空跑费用/扣次；如家属已确认无异议，请在说明期结束后办理");
        }

        BigDecimal standard = standardNoShowFee();
        ns.setDecision(decision);
        ns.setReviewer(userRepository.findById(me.id()).orElse(null));
        ns.setReviewedAt(LocalDateTime.now());
        ns.setReviewNote(cmd.reviewNote());
        ns.setDeductServiceCount(0);
        ns.setApprovedFee(BigDecimal.ZERO);

        switch (decision) {
            case WAIVE -> {
                ns.setApprovedFee(BigDecimal.ZERO);
                ns.setBilled(true); // 无费用出账
            }
            case CHARGE_FEE -> {
                ns.setApprovedFee(standard);
                ns.setBilled(false); // 等待账单生成时出账
            }
            case PARTIAL_FEE -> {
                if (cmd.approvedFee() == null || cmd.approvedFee().signum() < 0) {
                    throw new BusinessException("部分减免需填写核定金额");
                }
                if (standard != null && cmd.approvedFee().compareTo(standard) > 0) {
                    throw new BusinessException("核定金额不能高于标准空跑费 " + standard + " 元");
                }
                ns.setApprovedFee(cmd.approvedFee());
                ns.setBilled(false);
            }
            case DEDUCT_SERVICE -> {
                ns.setDeductServiceCount(cmd.deductServiceCount() == null ? 1
                        : Math.max(1, cmd.deductServiceCount()));
                ns.setApprovedFee(BigDecimal.ZERO);
                ns.setBilled(true); // 扣次不直接产生费用
            }
            case ADJUST_NEXT -> {
                if (cmd.nextVisitTime() == null) {
                    throw new BusinessException("调整下一次巡诊必须指定新的上门时间");
                }
                adjustNextVisit(ns, cmd.nextVisitTime(), cmd.adjustVisitId(),
                        "空跑审核后调整下一次巡诊时间", me);
                ns.setApprovedFee(BigDecimal.ZERO);
                ns.setBilled(true);
            }
        }
        ns.setStatus(NoShowStatus.REVIEWED);
        ns = noShowRepository.save(ns);
        auditService.log(me, "上门未遇", "卫生院审核", ns.getId(), ns.getNoShowNo(),
                ns.getPatient().getId(),
                "决定：" + decision.label
                        + (ns.getApprovedFee().signum() > 0 ? "；核定空跑费 " + ns.getApprovedFee() : "")
                        + (ns.getDeductServiceCount() > 0 ? "；扣除服务 " + ns.getDeductServiceCount() + " 次" : "")
                        + "；说明期内家属说明 " + excuses.size() + " 份"
                        + (cmd.reviewNote() != null ? "；意见：" + cmd.reviewNote() : ""));
        touchNoShow(ns);
        return ns;
    }

    /** 调整该患者计划中最近的一次未完成任务（或指定任务） */
    private Visit adjustNextVisit(NoShowRecord ns, LocalDateTime newTime, Long adjustVisitId,
                                  String reason, LoginUser me) {
        Visit target;
        if (adjustVisitId != null) {
            target = visitRepository.findById(adjustVisitId)
                    .orElseThrow(() -> new BusinessException(404, "待调整的巡诊任务不存在"));
        } else {
            target = visitRepository.findByPlanIdOrderByScheduledAtAsc(ns.getVisit().getPlan().getId())
                    .stream()
                    .filter(v -> v.getStatus() == VisitStatus.SCHEDULED || v.getStatus() == VisitStatus.PAUSED)
                    .filter(v -> v.getScheduledAt() != null && !v.getScheduledAt().isBefore(LocalDateTime.now()))
                    .min(Comparator.comparing(Visit::getScheduledAt))
                    .orElseThrow(() -> new BusinessException("该计划下没有可调整的后续巡诊任务，可在巡诊计划页手工新增/改期"));
        }
        LocalDateTime old = target.getScheduledAt();
        target.setScheduledAt(newTime);
        target.setStatus(VisitStatus.SCHEDULED);
        target.setArrangementNote("上门未遇[" + ns.getNoShowNo() + "]" + reason + "：" + old + " → " + newTime);
        target = visitRepository.save(target);
        ns.setAdjustedNextVisitAt(newTime);
        ns.setAdjustedVisit(target);
        auditService.log(me, "上门未遇", "调整下次巡诊", target.getId(), target.getVisitNo(),
                ns.getPatient().getId(), target.getArrangementNote());
        return target;
    }

    private BigDecimal standardNoShowFee() {
        return catalogRepository.findByActiveTrueOrderByCategoryAscCodeAsc().stream()
                .filter(c -> c.getCategory() == CatalogCategory.NO_SHOW_FEE
                        && NO_SHOW_FEE_CODE.equals(c.getCode()))
                .map(ChargeCatalog::getUnitPrice)
                .findFirst()
                .orElse(catalogRepository.findByActiveTrueOrderByCategoryAscCodeAsc().stream()
                        .filter(c -> c.getCategory() == CatalogCategory.NO_SHOW_FEE)
                        .map(ChargeCatalog::getUnitPrice)
                        .findFirst().orElse(BigDecimal.ZERO));
    }

    // ===== 家庭病床暂停与医生复评恢复 =====

    @Transactional
    public BedPauseRecord manualPause(Long applicationId, BedPauseReason reason, String note,
                                       Long sourceVisitId) {
        LoginUser me = SecurityUtils.current();
        if (me.role() != Role.ADMIN && me.role() != Role.FAMILY_DOCTOR && me.role() != Role.NURSE) {
            throw new BusinessException(403, "仅卫生院人员可操作暂停");
        }
        BedApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(404, "建床申请不存在"));
        Visit source = sourceVisitId == null ? null : visitRepository.findById(sourceVisitId).orElse(null);
        return doPause(app, app.getPatient(), reason == null ? BedPauseReason.OTHER : reason,
                note, me, source, null);
    }

    private BedPauseRecord pauseBedInternal(Visit visit, NoShowRecord ns, BedPauseReason reason,
                                            String note, LoginUser me) {
        BedApplication app = visit != null ? visit.getPlan().getApplication()
                : ns.getVisit().getPlan().getApplication();
        Patient patient = visit != null ? visit.getPatient() : ns.getPatient();
        return doPause(app, patient, reason, note, me, visit, ns);
    }

    private BedPauseRecord doPause(BedApplication app, Patient patient, BedPauseReason reason,
                                   String note, LoginUser me, Visit visit, NoShowRecord ns) {
        BedPauseRecord rec = new BedPauseRecord();
        rec.setPauseNo(ApplicationService.generateNo("ZT"));
        rec.setApplication(app);
        rec.setPatient(patient);
        rec.setReason(reason);
        rec.setSourceVisit(visit);
        rec.setSourceNoShow(ns);
        rec.setPauseNote(note);
        rec.setPausedBy(userRepository.findById(me.id()).orElse(null));
        rec.setPausedAt(LocalDateTime.now());
        rec.setStatus(BedPauseStatus.PAUSED);
        rec = pauseRepository.save(rec);

        app.setStatus(BedApplyStatus.PAUSED);
        applicationRepository.save(app);

        // 暂停该建床下执行中的计划，挂起未来任务
        for (VisitPlan plan : planRepository.findByApplicationId(app.getId())) {
            if (plan.getStatus() == PlanStatus.ACTIVE) {
                plan.setStatus(PlanStatus.PAUSED);
                planRepository.save(plan);
                for (Visit v : visitRepository.findByPlanIdOrderByScheduledAtAsc(plan.getId())) {
                    if (v.getStatus() == VisitStatus.SCHEDULED
                            && v.getScheduledAt() != null
                            && !v.getScheduledAt().isBefore(LocalDateTime.now().minusHours(2))) {
                        v.setStatus(VisitStatus.PAUSED);
                        v.setArrangementNote(appendNote(v.getArrangementNote(),
                                "患者临时送医/暂停巡诊，待医生复评后恢复（暂停单 " + rec.getPauseNo() + "）"));
                        visitRepository.save(v);
                    }
                }
            }
        }
        if (ns != null) {
            ns.setBedPause(rec);
            noShowRepository.save(ns);
        }
        auditService.log(me, "家庭病床", "暂停巡诊", rec.getId(), rec.getPauseNo(),
                patient.getId(), "原因：" + reason.label + "；" + note);
        touchPause(rec);
        return rec;
    }

    @Transactional(readOnly = true)
    public List<BedPauseRecord> pauses(String status, Long patientId) {
        LoginUser me = SecurityUtils.current();
        List<BedPauseRecord> list;
        if (status != null && !status.isBlank()) {
            list = pauseRepository.findByStatusOrderByCreatedAtDesc(BedPauseStatus.valueOf(status));
        } else if (patientId != null) {
            list = pauseRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        } else {
            list = new ArrayList<>(pauseRepository.findAll());
            list.sort(Comparator.comparing(BedPauseRecord::getCreatedAt).reversed());
        }
        List<BedPauseRecord> result = new ArrayList<>();
        for (BedPauseRecord rec : list) {
            if (me.role() == Role.FAMILY && (rec.getPatient().getFamilyUser() == null
                    || !rec.getPatient().getFamilyUser().getId().equals(me.id()))) {
                continue;
            }
            touchPause(rec);
            result.add(rec);
        }
        return result;
    }

    /** 家庭医生重新评估（给出复评意见与建议恢复时间），暂不恢复 */
    @Transactional
    public BedPauseRecord reevaluate(Long pauseId, String note, LocalDateTime proposedNextVisitAt) {
        LoginUser me = SecurityUtils.current();
        if (me.role() != Role.FAMILY_DOCTOR && me.role() != Role.ADMIN) {
            throw new BusinessException(403, "仅家庭医生可重新评估");
        }
        BedPauseRecord rec = pauseRepository.findById(pauseId)
                .orElseThrow(() -> new BusinessException(404, "暂停记录不存在"));
        if (rec.getStatus() == BedPauseStatus.RESUMED) {
            throw new BusinessException("该暂停单已恢复");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessException("请填写重新评估意见（病情/出院情况/可否恢复巡诊）");
        }
        rec.setReevaluationNote(note);
        rec.setReevaluatedBy(userRepository.findById(me.id()).orElse(null));
        rec.setReevaluatedAt(LocalDateTime.now());
        if (proposedNextVisitAt != null) rec.setNextVisitAt(proposedNextVisitAt);
        rec.setStatus(BedPauseStatus.REEVALUATED);
        rec = pauseRepository.save(rec);
        auditService.log(me, "家庭病床", "医生复评", rec.getId(), rec.getPauseNo(),
                rec.getPatient().getId(), "复评意见：" + note
                        + (proposedNextVisitAt != null ? "；建议恢复首次上门 " + proposedNextVisitAt : ""));
        touchPause(rec);
        return rec;
    }

    /** 复评通过后恢复家庭病床与巡诊 */
    @Transactional
    public BedPauseRecord resume(Long pauseId, String note, LocalDateTime nextVisitAt) {
        LoginUser me = SecurityUtils.current();
        if (me.role() != Role.FAMILY_DOCTOR && me.role() != Role.ADMIN) {
            throw new BusinessException(403, "仅家庭医生可恢复巡诊");
        }
        BedPauseRecord rec = pauseRepository.findById(pauseId)
                .orElseThrow(() -> new BusinessException(404, "暂停记录不存在"));
        if (rec.getStatus() == BedPauseStatus.RESUMED) {
            throw new BusinessException("该暂停单已恢复");
        }
        BedApplication app = rec.getApplication();
        app.setStatus(BedApplyStatus.APPROVED);
        applicationRepository.save(app);

        Visit nearest = null;
        for (VisitPlan plan : planRepository.findByApplicationId(app.getId())) {
            if (plan.getStatus() == PlanStatus.PAUSED) {
                plan.setStatus(PlanStatus.ACTIVE);
                planRepository.save(plan);
                for (Visit v : visitRepository.findByPlanIdOrderByScheduledAtAsc(plan.getId())) {
                    if (v.getStatus() == VisitStatus.PAUSED) {
                        v.setStatus(VisitStatus.SCHEDULED);
                        v.setArrangementNote(appendNote(v.getArrangementNote(),
                                "医生复评通过，恢复巡诊（暂停单 " + rec.getPauseNo() + "）"));
                        visitRepository.save(v);
                        if (nearest == null || (v.getScheduledAt() != null
                                && (nearest.getScheduledAt() == null
                                || v.getScheduledAt().isBefore(nearest.getScheduledAt())))) {
                            nearest = v;
                        }
                    }
                }
            }
        }

        LocalDateTime firstVisitAt = nextVisitAt != null ? nextVisitAt : rec.getNextVisitAt();
        if (firstVisitAt != null && nearest != null) {
            LocalDateTime old = nearest.getScheduledAt();
            nearest.setScheduledAt(firstVisitAt);
            nearest.setArrangementNote(appendNote(nearest.getArrangementNote(),
                    "复评后首次巡诊调整：" + old + " → " + firstVisitAt));
            visitRepository.save(nearest);
            rec.setNextVisitAt(firstVisitAt);
        }

        rec.setStatus(BedPauseStatus.RESUMED);
        rec.setResumeNote(note);
        rec.setResumedAt(LocalDateTime.now());
        rec = pauseRepository.save(rec);
        auditService.log(me, "家庭病床", "恢复巡诊", rec.getId(), rec.getPauseNo(),
                rec.getPatient().getId(), "恢复说明：" + note
                        + (firstVisitAt != null ? "；首次上门 " + firstVisitAt : ""));
        touchPause(rec);
        return rec;
    }

    private String appendNote(String origin, String append) {
        if (origin == null || origin.isBlank()) return append;
        if (origin.contains(append)) return origin;
        return origin + "；" + append;
    }
}
