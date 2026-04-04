package com.cvcraft.ai.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    public String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<String> toStringList(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
