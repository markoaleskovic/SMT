#include "yin.h"
#include <cmath>
#include <algorithm>
#include <numeric>
#include "third_party/pocketfft_hdronly.h"

using pocketfft::shape_t;
using pocketfft::stride_t;

int Yin::nextPow2(int n) {
    int p = 1;
    while (p < n) p <<= 1;
    return p;
}

Yin::Yin(int sampleRate_, int bufferSize_)
: sampleRate(sampleRate_),
bufferSize(bufferSize_),
paddedSize(nextPow2(bufferSize_)),
threshold(0.10f),
fftIn(paddedSize, 0.0f),
spectrum(paddedSize / 2 + 1),
ifftOut(paddedSize, 0.0f),
yinBuffer(bufferSize / 2, 0.0f) {}

float Yin::getPitch(const std::vector<float>& audioBuffer) {
    if (static_cast<int>(audioBuffer.size()) != bufferSize) return -1.0f;

    difference(audioBuffer);
    cumulativeMeanNormalizedDifference();

    int tauEstimate = absoluteThreshold();
    if (tauEstimate == -1) return -1.0f;

    float refinedTau = parabolicInterpolation(tauEstimate);
    if (refinedTau <= 0.0f) return -1.0f;

    return static_cast<float>(sampleRate) / refinedTau;
}

void Yin::difference(const std::vector<float>& buffer) {
    // 1) Copy and zero-pad input
    std::fill(fftIn.begin(), fftIn.end(), 0.0f);
    std::copy(buffer.begin(), buffer.end(), fftIn.begin());

    // 2) Forward real FFT: fftIn (N) -> spectrum (N/2+1)
    shape_t shape{ static_cast<size_t>(paddedSize) };
    shape_t axes{ 0 };

    stride_t stride_in{ sizeof(float) };
    stride_t stride_out{ sizeof(std::complex<float>) };

    // No scaling on forward.
    pocketfft::r2c<float>(
        shape, stride_in, stride_out, axes,
        /*forward=*/true,
        fftIn.data(),
                          spectrum.data(),
                          /*fct=*/1.0f,
                          /*nthreads=*/1
    );

    // 3) Power spectrum: spectrum[k] = spectrum[k] * conj(spectrum[k])
    for (auto &c : spectrum) {
        float re = c.real();
        float im = c.imag();
        c = std::complex<float>(re*re + im*im, 0.0f);
    }

    // 4) Inverse FFT: spectrum -> ifftOut
    // For a “true inverse” producing time-domain correlation values,
    // scale by 1/N.
    float invScale = 1.0f / static_cast<float>(paddedSize);

    pocketfft::c2r<float>(
        shape, stride_out, stride_in, axes,
        /*forward=*/false,
        spectrum.data(),
                          ifftOut.data(),
                          /*fct=*/invScale,
                          /*nthreads=*/1
    );

    // 5) Prefix sums of squares for difference computation
    // prefixSumSq[i] = sum_{0..i-1} buffer[j]^2
    std::vector<float> prefixSumSq(bufferSize + 1, 0.0f);
    for (int i = 0; i < bufferSize; i++) {
        prefixSumSq[i + 1] = prefixSumSq[i] + buffer[i] * buffer[i];
    }

    yinBuffer[0] = 0.0f;
    int maxTau = bufferSize / 2;
    for (int tau = 1; tau < maxTau; tau++) {
        float term1 = prefixSumSq[bufferSize - tau] - prefixSumSq[0];
        float term2 = prefixSumSq[bufferSize] - prefixSumSq[tau];
        float term3 = 2.0f * ifftOut[tau];
        yinBuffer[tau] = term1 + term2 - term3;
    }
}

void Yin::cumulativeMeanNormalizedDifference() {
    yinBuffer[0] = 1.0f;
    float runningSum = 0.0f;
    for (size_t tau = 1; tau < yinBuffer.size(); tau++) {
        runningSum += yinBuffer[tau];
        if (runningSum > 0.0f) {
            yinBuffer[tau] = yinBuffer[tau] * static_cast<float>(tau) / runningSum;
        } else {
            yinBuffer[tau] = 1.0f;
        }
    }
}

int Yin::absoluteThreshold() {
    for (size_t tau = 2; tau < yinBuffer.size(); tau++) {
        if (yinBuffer[tau] < threshold) {
            while (tau + 1 < yinBuffer.size() && yinBuffer[tau + 1] < yinBuffer[tau]) {
                tau++;
            }
            return static_cast<int>(tau);
        }
    }
    return -1;
}

float Yin::parabolicInterpolation(int tauEstimate) {
    if (tauEstimate <= 0 || tauEstimate >= static_cast<int>(yinBuffer.size()) - 1)
        return static_cast<float>(tauEstimate);

    float s0 = yinBuffer[tauEstimate - 1];
    float s1 = yinBuffer[tauEstimate];
    float s2 = yinBuffer[tauEstimate + 1];

    return tauEstimate + (s2 - s0) / (2.0f * (2.0f * s1 - s2 - s0));
}
