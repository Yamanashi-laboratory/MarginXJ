package com.ynu.marginx.infrastructure.judgement;

import com.ynu.marginx.domain.model.judge.JudgementRule;
import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.shared.exception.JudgementFileException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the judgement file: a designator line (b01, e02, ...) opens a block, and every following
 * "begin end phase [anti]" line adds a rule to that block.
 */
public final class JudgementSpecParser {

    public JudgementSpec parse(List<String> lines) {
        List<List<JudgementRule>> rulesByElement = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            JudgementRule rule = readRule(line);
            if (rule != null) {
                if (rulesByElement.isEmpty()) {
                    throw new JudgementFileException(
                            "Judgement window appears before any element designator: " + line);
                }
                rulesByElement.get(rulesByElement.size() - 1).add(rule);
            } else if (isDesignator(line)) {
                rulesByElement.add(new ArrayList<>());
            }
        }
        if (rulesByElement.isEmpty()) {
            throw new JudgementFileException("Judgement file contains no element designator.");
        }
        return new JudgementSpec(rulesByElement);
    }

    private JudgementRule readRule(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            return null;
        }
        try {
            int begin = Integer.parseInt(tokens[0]);
            int end = Integer.parseInt(tokens[1]);
            double phase = Double.parseDouble(tokens[2]);
            boolean inverted = tokens.length > 3 && Integer.parseInt(tokens[3]) == 1;
            return end == 0 ? null : new JudgementRule(begin, end, phase, inverted);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isDesignator(String line) {
        char first = Character.toLowerCase(line.charAt(0));
        return first == 'b' || first == 'e';
    }
}
