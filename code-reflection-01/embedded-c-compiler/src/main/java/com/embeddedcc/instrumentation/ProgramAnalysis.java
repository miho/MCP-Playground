package com.embeddedcc.instrumentation;

import java.util.List;

public record ProgramAnalysis(List<ArrayAccess> arrayAccesses,
                              List<CodeFunction> functions) {
}

