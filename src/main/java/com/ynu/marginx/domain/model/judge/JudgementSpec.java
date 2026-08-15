package com.ynu.marginx.domain.model.judge;

import java.util.List;

public record JudgementSpec(List<List<JudgementRule>> rulesByElement) {

    public JudgementSpec {
        rulesByElement = rulesByElement.stream().map(List::copyOf).toList();
    }

    public int elementCount() {
        return rulesByElement.size();
    }

    public List<JudgementRule> rulesFor(int elementIndex) {
        return elementIndex < rulesByElement.size() ? rulesByElement.get(elementIndex) : List.of();
    }

    public int totalRules() {
        return rulesByElement.stream().mapToInt(List::size).sum();
    }
}
