#pragma once

#include <string>
#include <vector>

namespace zhixu_ocr
{
struct OcrLine
{
    std::string text;
    int left;
    int top;
    int right;
    int bottom;
    float confidence;
};

class PaddleOcr
{
public:
    PaddleOcr();
    ~PaddleOcr();

    bool load(
        const unsigned char* det_param, int det_param_len,
        const unsigned char* det_bin, int det_bin_len,
        const unsigned char* rec_param, int rec_param_len,
        const unsigned char* rec_bin, int rec_bin_len,
        const unsigned char* dict_txt, int dict_txt_len,
        bool use_vulkan);

    std::vector<OcrLine> recognize_rgba8888(const unsigned char* rgba, int width, int height);

private:
    struct Impl;
    Impl* impl_;
};
} // namespace zhixu_ocr
