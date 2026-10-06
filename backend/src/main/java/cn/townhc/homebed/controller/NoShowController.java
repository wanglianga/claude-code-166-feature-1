package cn.townhc.homebed.controller;

import cn.townhc.homebed.common.ApiResponse;
import cn.townhc.homebed.domain.entity.NoShowCase;
import cn.townhc.homebed.service.NoShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "上门未遇处理（改约/空跑费/病床暂停复评）")
@RestController
@RequestMapping("/api/no-show")
@RequiredArgsConstructor
public class NoShowController {

    private final NoShowService noShowService;

    @Operation(summary = "上门未遇工单列表（可按状态/患者过滤，家属仅见本人）")
    @GetMapping
    public ApiResponse<List<NoShowCase>> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long patientId) {
        return ApiResponse.ok(noShowService.list(status, patientId));
    }

    @Operation(summary = "工单详情（含电话拨打记录与门口照片）")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(noShowService.detail(id));
    }

    @Operation(summary = "护士登记上门未遇：到达定位/电话拨打/等待时长/门口照片，任务挂起")
    @PostMapping("/visits/{visitId}/report")
    public ApiResponse<NoShowCase> report(@PathVariable Long visitId,
                                          @RequestBody NoShowService.ReportCommand cmd) {
        return ApiResponse.ok(noShowService.report(visitId, cmd));
    }

    @Operation(summary = "家属提交补充说明（临时急诊/手机没电/地址变更等，可传凭证）")
    @PostMapping("/{id}/family-explanation")
    public ApiResponse<NoShowCase> familyExplanation(@PathVariable Long id,
                                                     @RequestBody NoShowService.FamilyExplanationCommand cmd) {
        return ApiResponse.ok(noShowService.submitFamilyExplanation(id, cmd));
    }

    @Operation(summary = "卫生院审核：减免/计空跑费/扣除一次服务/调整下一次/暂停病床（可同时改期）")
    @PostMapping("/{id}/review")
    public ApiResponse<NoShowCase> review(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        NoShowService.ReviewCommand cmd = new NoShowService.ReviewCommand(
                body.get("decision") == null ? null
                        : cn.townhc.homebed.domain.enums.DomainEnums.NoShowDecision
                        .valueOf(body.get("decision").toString()),
                (String) body.get("decisionNote"),
                body.get("emptyRunFee") == null ? null : new BigDecimal(body.get("emptyRunFee").toString()),
                parseTime(body.get("rescheduleTime")));
        return ApiResponse.ok(noShowService.review(id, cmd));
    }

    @Operation(summary = "家庭医生重新评估：恢复家庭病床与巡诊（可指定恢复后的上门时间）")
    @PostMapping("/{id}/reassess")
    public ApiResponse<NoShowCase> reassess(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        NoShowService.ReassessCommand cmd = new NoShowService.ReassessCommand(
                (String) body.get("reassessNote"),
                parseTime(body.get("resumeTime")));
        return ApiResponse.ok(noShowService.reassess(id, cmd));
    }

    private LocalDateTime parseTime(Object v) {
        return v == null || v.toString().isBlank() ? null : LocalDateTime.parse(v.toString());
    }
}
