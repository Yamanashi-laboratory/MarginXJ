package com.ynu.marginx.domain.model.circuit;

public enum ShuntMode {
    /** No shunt resistor is emitted for the junction. */
    UNSHUNTED,
    /** Shunt resistance derived from the IcRs product (*SHUNT). */
    IC_RS,
    /** Shunt resistance derived from the Stewart-McCumber parameter (*Bc). */
    BC,
    /** Shunt resistance itself is registered as a margin target (*calc). */
    CALCULATED
}
