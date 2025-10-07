package com.embeddedcc.instrumentation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayAccessDetectorTest {

    private final ArrayAccessDetector detector = new ArrayAccessDetector();

    @Test
    void detectsLoadsAndStoresInAssignment() {
        String code = """
                void transpose(int A[4][4], int B[4][4]) {
                    for (int i = 0; i < 4; ++i) {
                        for (int j = 0; j < 4; ++j) {
                            B[j][i] = A[i][j];
                        }
                    }
                }
                """;

        List<ArrayAccess> accesses = detector.detect(code);
        assertEquals(2, accesses.size());

        ArrayAccess store = accesses.stream()
                .filter(a -> a.getAccessType() == ArrayAccess.AccessType.STORE)
                .findFirst()
                .orElseThrow();
        assertEquals("B[j][i]", store.getExpression());

        ArrayAccess load = accesses.stream()
                .filter(a -> a.getAccessType() == ArrayAccess.AccessType.LOAD)
                .findFirst()
                .orElseThrow();
        assertEquals("A[i][j]", load.getExpression());
    }

    @Test
    void ignoresCommentedAccesses() {
        String code = """
                int main() {
                    int A[4][4];
                    // A[1][2] = 5;
                    /*
                     * B[3][2] = A[2][3];
                     */
                    return A[0][0];
                }
                """;

        List<ArrayAccess> accesses = detector.detect(code);
        assertEquals(1, accesses.size());
        assertTrue(accesses.get(0).getExpression().contains("A[0][0]"));
    }

    @Test
    void ignoresStringLiterals() {
        String code = """
                int main() {
                    int A[8];
                    printf("Value: C[%d]\\n", 3);
                    return A[3];
                }
                """;

        List<ArrayAccess> accesses = detector.detect(code);
        assertEquals(1, accesses.size());
        assertEquals("A[3]", accesses.get(0).getExpression());
    }
}
