package com.interviewer.ai.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档块实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("text")
    private String text;
    
    @JsonProperty("embedding")
    private List<Double> embedding;
    
    @JsonProperty("score")
    private double score;
    
    @JsonProperty("source")
    private String source;

    private String position;  // java / ai
}
