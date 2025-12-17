#pragma once

#include <vector>
#include <complex>
#include <cstddef>

class Yin {
public:
    Yin(int sampleRate, int bufferSize);
    ~Yin() = default;

    // Returns pitch in Hz, or -1.0f if not found.
    float getPitch(const std::vector<float>& audioBuffer);

private:
    int sampleRate;
    int bufferSize;
    int paddedSize;
    float threshold;

    // FFT buffers
    std::vector<float> fftIn;                       // length paddedSize
    std::vector<std::complex<float>> spectrum;      // length paddedSize/2 + 1
    std::vector<float> ifftOut;                     // length paddedSize

    // YIN internal buffer (difference function etc.)
    std::vector<float> yinBuffer;

    void difference(const std::vector<float>& audioBuffer);
    void cumulativeMeanNormalizedDifference();
    int absoluteThreshold();
    float parabolicInterpolation(int tauEstimate);

    static int nextPow2(int n);
};
