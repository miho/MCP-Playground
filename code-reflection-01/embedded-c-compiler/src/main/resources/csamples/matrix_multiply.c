#include <stdio.h>
#include <stdlib.h>

#define N 128

int main(void) {
    double* A = malloc(sizeof(double) * N * N);
    double* B = malloc(sizeof(double) * N * N);
    double* C = malloc(sizeof(double) * N * N);

    if (!A || !B || !C) {
        fprintf(stderr, "Allocation failed\n");
        return 1;
    }

    for (int i = 0; i < N; ++i) {
        for (int j = 0; j < N; ++j) {
            A[i * N + j] = (double)(i + j);
            B[i * N + j] = (double)(i == j);
            C[i * N + j] = 0.0;
        }
    }

    for (int i = 0; i < N; ++i) {
        for (int k = 0; k < N; ++k) {
            double aik = A[i * N + k];
            for (int j = 0; j < N; ++j) {
                C[i * N + j] += aik * B[k * N + j];
            }
        }
    }

    printf("C[0]=%.3f C[%d]=%.3f\n", C[0], N * N - 1, C[N * N - 1]);

    free(A);
    free(B);
    free(C);
    return 0;
}

