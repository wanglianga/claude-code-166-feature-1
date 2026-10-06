package cn.townhc.homebed.controller;

import cn.townhc.homebed.common.ApiResponse;
import cn.townhc.homebed.domain.entity.BedPauseRecord;
import cn.townhc.homebed.domain.entity.FamilyExcuse;
import cn.townhc.homebed.domain.entity.NoShowRecord;
import cn.townhc.homebed.domain.enums.DomainEnums.BedPauseReason;
import cn.townhc.homebed.domain.enums.DomainEnums.FamilyExcuseReason;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowDecision;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowReason;
import cn.townhc.homebed.service.NoShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "上门未遇（空跑）改约与费用处理")
@RestController
@RequestMapping("/api/no-show")
@RequiredArgsConstructor
public class NoShowController {

    private final NoShowService noShowService;

    @Operation(summary = "护士登记上门未遇：到达定位/电话拨打/等待时长/门口照片；可选联动暂停病床")
    @PostMapping("/visits/{visitId}/register")
    public ApiResponse<NoShowRecord> register(@PathVariable Long visitId,
                                              @RequestBody NoShowService.RegisterCommand cmd) {
        return ApiResponse.ok(noShowService.register(visitId, cmd));
    }

    @GetMapping
    public ApiResponse<List<NoShowRecord>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long patientId) {
        return ApiResponse.ok(noShowService.list(status, patientId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(noShowService.detail(id));
    }

    @Operation(summary = "家属提交补充说明（临时急诊/手机没电/地址变更等，费用核定前）")
    @PostMapping("/{id}/excuse")
    public ApiResponse<FamilyExcuse> submitExcuse(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> evidence = body.get("evidencePaths") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        return ApiResponse.ok(noShowService.submitExcuse(id,
                body.get("reason") == null ? null
                        : FamilyExcuseReason.valueOf(body.get("reason").toString()),
                (String) body.get("content"), evidence,
                (String) body.get("expectedHandling")));
    }

    @Operation(summary = "卫生院审核：减免/保留空跑费/部分减免/扣除一次服务/调整下一次巡诊")
    @PostMapping("/{id}/review")
    public ApiResponse<NoShowRecord> review(@PathVariable Long id,
                                            @RequestBody NoShowService.ReviewCommand cmd) {
        return ApiResponse.ok(noShowService.review(id, cmd));
    }

    // ===== 家庭病床暂停 / 医生复评 / 恢复 =====

    @GetMapping("/pauses")
    public ApiResponse<List<BedPauseRecord>> pauses(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long patientId) {
        return ApiResponse.ok(noShowService.pauses(status, patientId));
    }

    @Operation(summary = "手工暂停家庭病床（其他特殊情况）")
    @PostMapping("/pauses")
    public ApiResponse<BedPauseRecord> manualPause(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(noShowService.manualPause(
                Long.valueOf(body.get("applicationId").toString()),
                body.get("reason") == null ? BedPauseReason.OTHER
                        : BedPauseReason.valueOf(body.get("reason").toString()),
                (String) body.getOrDefault("note", ""),
                body.get("sourceVisitId") == null ? null
                        : Long.valueOf(body.get("sourceVisitId").toString())));
    }

    @Operation(summary = "家庭医生对暂停病床重新评估（等待复评通过后恢复）")
    @PostMapping("/pauses/{id}/reevaluate")
    public ApiResponse<BedPauseRecord> reevaluate(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        LocalDateTime proposed = body.get("proposedNextVisitAt") == null ? null
                : LocalDateTime.parse(body.get("proposedNextVisitAt").toString());
        return ApiResponse.ok(noShowService.reevaluate(id,
                (String) body.getOrDefault("note", ""), proposed));
    }

    @Operation(summary = "复评通过后恢复家庭病床与巡诊")
    @PostMapping("/pauses/{id}/resume")
    public ApiResponse<BedPauseRecord> resume(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        LocalDateTime next = body.get("nextVisitAt") == null ? null
                : LocalDateTime.parse(body.get("nextVisitAt").toString());
        return ApiResponse.ok(noShowService.resume(id,
                (String) body.getOrDefault("note", ""), next));
    }

    /** 供前端枚举下拉 */
    @GetMapping("/meta/enums")
    public ApiResponse<Map<String, Object>> enums() {
        return ApiResponse.ok(Map.of(
                "NoShowReason", NoShowReason.values(),
                "FamilyExcuseReason", FamilyExcuseReason.values(),
                "NoShowDecision", NoShowDecision.values(),
                "BedPauseReason", BedPauseReason.values()));
    }
}
