package com.ynu.marginx.infrastructure.judgement;

import com.ynu.marginx.domain.model.judge.JudgementSpec;
import com.ynu.marginx.domain.port.JudgementSpecRepository;
import com.ynu.marginx.shared.exception.JudgementFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileJudgementSpecRepository implements JudgementSpecRepository {

    private final Path workingDirectory;
    private final JudgementSpecParser parser;

    public FileJudgementSpecRepository(Path workingDirectory, JudgementSpecParser parser) {
        this.workingDirectory = workingDirectory;
        this.parser = parser;
    }

    @Override
    public JudgementSpec load(String baseName) {
        Path source = workingDirectory.resolve(baseName + ".txt");
        try {
            return parser.parse(Files.readAllLines(source));
        } catch (IOException e) {
            throw new JudgementFileException("Cannot read judgement file " + source, e);
        }
    }
}
