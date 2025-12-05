package vn.hoidanit.jobhunter.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AnalysisDataService {

    @Value("${hoidanit.ai-server.url:http://localhost:3005}")
    private String aiServerUrl;

    private final RestTemplate restTemplate;

    public AnalysisDataService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Lấy matching score cho một user và job cụ thể
     */
    public Integer getMatchingScore(Long userId, Long jobId) {
        if (userId == null || jobId == null) {
            return null;
        }

        try {
            String url = String.format("%s/api/v1/analysis_datas?userId=%d&jobId=%d", 
                aiServerUrl, userId, jobId);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(url, 
                (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object successObj = body.get("success");
                
                if (successObj instanceof Boolean && Boolean.TRUE.equals(successObj)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    if (data != null) {
                        Object matchingScore = data.get("matching_score");
                        if (matchingScore instanceof Number) {
                            return ((Number) matchingScore).intValue();
                        }
                    }
                }
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch matching score for userId={}, jobId={}: {}", 
                userId, jobId, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching matching score: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Lấy danh sách matching scores cho một job (grouped by user)
     * Trả về Map<userId, matchingScore>
     */
    public Map<Long, Integer> getMatchingScoresByJob(Long jobId) {
        Map<Long, Integer> result = new HashMap<>();
        
        if (jobId == null) {
            return result;
        }

        try {
            String url = String.format("%s/api/v1/analysis_datas/users?jobId=%d", 
                aiServerUrl, jobId);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(url, 
                (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object successObj = body.get("success");
                
                if (successObj instanceof Boolean && Boolean.TRUE.equals(successObj)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) body.get("data");
                    if (dataList != null) {
                        for (Map<String, Object> item : dataList) {
                            Object userIdObj = item.get("user_id");
                            Object matchingScoreObj = item.get("matching_score");
                            
                            if (userIdObj instanceof Number && matchingScoreObj instanceof Number) {
                                Long userId = ((Number) userIdObj).longValue();
                                Integer matchingScore = ((Number) matchingScoreObj).intValue();
                                result.put(userId, matchingScore);
                            }
                        }
                    }
                }
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch matching scores for jobId={}: {}", jobId, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching matching scores: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Lấy matching scores cho nhiều jobs cùng lúc
     * Trả về Map<jobId, Map<userId, matchingScore>>
     */
    public Map<Long, Map<Long, Integer>> getMatchingScoresByJobs(List<Long> jobIds) {
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        
        if (jobIds == null || jobIds.isEmpty()) {
            return result;
        }

        // Fetch cho từng job (có thể optimize sau bằng batch API nếu có)
        for (Long jobId : jobIds) {
            Map<Long, Integer> scores = getMatchingScoresByJob(jobId);
            if (!scores.isEmpty()) {
                result.put(jobId, scores);
            }
        }

        return result;
    }
}

