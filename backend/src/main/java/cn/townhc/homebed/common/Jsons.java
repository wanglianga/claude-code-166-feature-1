package cn.townhc.homebed.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/** 轻量 JSON 工具：实体中以字符串持久化的简单数组/对象读写。 */
@Slf4j
public final class Jsons {
    private static final ObjectMapper M = new ObjectMapper();

    private Jsons() {}

    public static String write(Object o) {
        if (o == null) return null;
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return M.readValue(json, M.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("JSON 解析字符串数组失败: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> readObjectList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return M.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("JSON 解析对象数组失败: {}", e.getMessage());
            return List.of();
        }
    }
}
