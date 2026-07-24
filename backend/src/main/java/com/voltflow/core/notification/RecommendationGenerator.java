package com.voltflow.core.notification;

public interface RecommendationGenerator {
    GeneratedRecommendation generate(RecommendationContext context);

    record GeneratedRecommendation(String text, String modelName, boolean fallbackUsed) {}
}
