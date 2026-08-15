package com.ynu.marginx.domain.port;

import com.ynu.marginx.domain.model.margin.MarginTable;

public interface MarginResultRepository {

    void save(String baseName, MarginTable table);
}
