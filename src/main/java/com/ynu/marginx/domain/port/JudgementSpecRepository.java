package com.ynu.marginx.domain.port;

import com.ynu.marginx.domain.model.judge.JudgementSpec;

public interface JudgementSpecRepository {

    JudgementSpec load(String baseName);
}
