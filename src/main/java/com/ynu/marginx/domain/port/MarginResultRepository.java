package com.ynu.marginx.domain.port;

import com.ynu.marginx.domain.model.margin.MarginTable;

public interface MarginResultRepository {

    /**
     * Discards the table. The optimisers measure margins over and over on their way to an answer,
     * and only the circuit they settle on is worth filing.
     */
    MarginResultRepository NONE = (baseName, table) -> {
    };

    void save(String baseName, MarginTable table);
}
