package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Interview preparation result.
 *
 * Each QAPair contains one likely interview question and a model answer
 * tailored to the candidate's resume and the target role.
 */
public record InterviewPrepResponse(
        List<QAPair> questions,
        /** General interview tips for this role. */
        String generalTips
) {
    public record QAPair(
            String question,
            String modelAnswer,
            /** Category: "behavioural" | "technical" | "situational" | "motivation" */
            String category
    ) {}
}
