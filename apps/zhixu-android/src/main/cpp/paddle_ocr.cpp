// Copyright 2025 Tencent
// SPDX-License-Identifier: BSD-3-Clause
//
// This implementation is adapted from ncnn example `examples/ppocrv5.cpp`,
// refactored into a reusable library and wired for Android JNI usage.

#include "paddle_ocr.h"

#include <float.h>
#include <stdio.h>

#include <string>
#include <vector>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#include <ncnn/datareader.h>
#include <ncnn/layer.h>
#include <ncnn/net.h>

namespace zhixu_ocr
{
struct Character
{
    int id;
    float prob;
};

struct Object
{
    cv::RotatedRect rrect;
    int orientation;
    float prob;
    std::vector<Character> text;
};

static double contour_score(const cv::Mat& binary, const std::vector<cv::Point>& contour)
{
    cv::Rect rect = cv::boundingRect(contour);
    if (rect.x < 0)
        rect.x = 0;
    if (rect.y < 0)
        rect.y = 0;
    if (rect.x + rect.width > binary.cols)
        rect.width = binary.cols - rect.x;
    if (rect.y + rect.height > binary.rows)
        rect.height = binary.rows - rect.y;

    cv::Mat binROI = binary(rect);

    cv::Mat mask = cv::Mat::zeros(rect.height, rect.width, CV_8U);
    std::vector<cv::Point> roiContour;
    roiContour.reserve(contour.size());
    for (size_t i = 0; i < contour.size(); i++)
    {
        cv::Point pt = cv::Point(contour[i].x - rect.x, contour[i].y - rect.y);
        roiContour.push_back(pt);
    }

    std::vector<std::vector<cv::Point> > roiContours = {roiContour};
    cv::fillPoly(mask, roiContours, cv::Scalar(255));

    double score = cv::mean(binROI, mask).val[0];
    return score / 255.f;
}

static cv::Mat get_rotate_crop_image(const cv::Mat& bgr, const Object& object)
{
    const int orientation = object.orientation;
    const float rw = object.rrect.size.width;
    const float rh = object.rrect.size.height;

    const int target_height = 48;
    const float target_width = rh * target_height / rw;

    cv::Mat dst;

    cv::Point2f corners[4];
    object.rrect.points(corners);

    if (orientation == 0)
    {
        // horizontal text
        std::vector<cv::Point2f> src_pts(3);
        src_pts[0] = corners[0];
        src_pts[1] = corners[1];
        src_pts[2] = corners[3];

        std::vector<cv::Point2f> dst_pts(3);
        dst_pts[0] = cv::Point2f(0, 0);
        dst_pts[1] = cv::Point2f(target_width, 0);
        dst_pts[2] = cv::Point2f(0, target_height);

        cv::Mat tm = cv::getAffineTransform(src_pts, dst_pts);
        cv::warpAffine(bgr, dst, tm, cv::Size(target_width, target_height), cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    }
    else
    {
        // vertical text
        std::vector<cv::Point2f> src_pts(3);
        src_pts[0] = corners[2];
        src_pts[1] = corners[3];
        src_pts[2] = corners[1];

        std::vector<cv::Point2f> dst_pts(3);
        dst_pts[0] = cv::Point2f(0, 0);
        dst_pts[1] = cv::Point2f(target_width, 0);
        dst_pts[2] = cv::Point2f(0, target_height);

        cv::Mat tm = cv::getAffineTransform(src_pts, dst_pts);
        cv::warpAffine(bgr, dst, tm, cv::Size(target_width, target_height), cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    }

    return dst;
}

class PPOCRv5
{
public:
    bool init(
        const unsigned char* det_param, int det_param_len,
        const unsigned char* det_bin, int det_bin_len,
        const unsigned char* rec_param, int rec_param_len,
        const unsigned char* rec_bin, int rec_bin_len,
        bool use_vulkan);

    void detect(const cv::Mat& bgr, std::vector<Object>& objects);
    void recognize(const cv::Mat& bgr, Object& object);

    std::vector<std::string> dict;

private:
    ncnn::Net ppocrv5_det;
    ncnn::Net ppocrv5_rec;
};

bool PPOCRv5::init(
    const unsigned char* det_param, int det_param_len,
    const unsigned char* det_bin, int det_bin_len,
    const unsigned char* rec_param, int rec_param_len,
    const unsigned char* rec_bin, int rec_bin_len,
    bool use_vulkan)
{
    if (!det_param || det_param_len <= 0 || !det_bin || det_bin_len <= 0 ||
        !rec_param || rec_param_len <= 0 || !rec_bin || rec_bin_len <= 0)
    {
        return false;
    }

    ppocrv5_det.opt.use_vulkan_compute = use_vulkan;
    ppocrv5_rec.opt.use_vulkan_compute = use_vulkan;
    ppocrv5_det.opt.num_threads = 4;
    ppocrv5_rec.opt.num_threads = 4;

    {
        ncnn::DataReaderFromMemory dr_param(det_param);
        if (ppocrv5_det.load_param(dr_param) != 0) return false;
        ncnn::DataReaderFromMemory dr_bin(det_bin);
        if (ppocrv5_det.load_model(dr_bin) != 0) return false;
    }
    {
        ncnn::DataReaderFromMemory dr_param(rec_param);
        if (ppocrv5_rec.load_param(dr_param) != 0) return false;
        ncnn::DataReaderFromMemory dr_bin(rec_bin);
        if (ppocrv5_rec.load_model(dr_bin) != 0) return false;
    }

    return true;
}

void PPOCRv5::detect(const cv::Mat& bgr, std::vector<Object>& objects)
{
    const int img_w = bgr.cols;
    const int img_h = bgr.rows;

    const int target_size = 320;
    const float max_size = (float)std::max(img_w, img_h);
    const float scale = target_size / max_size;
    const int new_w = (int)(img_w * scale);
    const int new_h = (int)(img_h * scale);

    cv::Mat resized;
    cv::resize(bgr, resized, cv::Size(new_w, new_h));

    // pad to square
    cv::Mat padded;
    const int wpad = target_size - new_w;
    const int hpad = target_size - new_h;
    cv::copyMakeBorder(resized, padded, 0, hpad, 0, wpad, cv::BORDER_CONSTANT, 0.f);

    ncnn::Mat in = ncnn::Mat::from_pixels(padded.data, ncnn::Mat::PIXEL_BGR2RGB, target_size, target_size);
    const float mean_vals[3] = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};
    const float norm_vals[3] = {1.f / (0.229f * 255.f), 1.f / (0.224f * 255.f), 1.f / (0.225f * 255.f)};
    in.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = ppocrv5_det.create_extractor();
    ex.input("in0", in);

    ncnn::Mat out;
    ex.extract("out0", out);

    // out: 1x1x320x320
    cv::Mat pred(target_size, target_size, CV_32F, (void*)out.channel(0));

    cv::Mat mask;
    cv::threshold(pred, mask, 0.3, 255, cv::THRESH_BINARY);
    mask.convertTo(mask, CV_8U);

    std::vector<std::vector<cv::Point> > contours;
    cv::findContours(mask, contours, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

    objects.clear();

    for (size_t i = 0; i < contours.size(); i++)
    {
        const double score = contour_score(mask, contours[i]);
        if (score < 0.6)
            continue;

        cv::RotatedRect box = cv::minAreaRect(contours[i]);
        if (box.size.width < 3 || box.size.height < 3)
            continue;

        // scale back to original image size
        box.center.x = box.center.x / scale;
        box.center.y = box.center.y / scale;
        box.size.width = box.size.width / scale;
        box.size.height = box.size.height / scale;

        Object obj;
        obj.rrect = box;
        obj.prob = (float)score;

        // decide orientation
        const float rw = obj.rrect.size.width;
        const float rh = obj.rrect.size.height;
        obj.orientation = (rw >= rh) ? 0 : 1;
        if (obj.orientation == 0 && obj.rrect.angle < -45.f)
        {
            std::swap(obj.rrect.size.width, obj.rrect.size.height);
            obj.rrect.angle += 90.f;
        }
        if (obj.orientation == 1 && obj.rrect.angle > -45.f)
        {
            std::swap(obj.rrect.size.width, obj.rrect.size.height);
            obj.rrect.angle -= 90.f;
        }

        objects.push_back(obj);
    }

    // sort top-to-bottom, then left-to-right
    std::sort(objects.begin(), objects.end(), [](const Object& a, const Object& b) {
        const float dy = a.rrect.center.y - b.rrect.center.y;
        if (fabsf(dy) > 10.f) return dy < 0.f;
        return a.rrect.center.x < b.rrect.center.x;
    });
}

void PPOCRv5::recognize(const cv::Mat& bgr, Object& object)
{
    cv::Mat crop = get_rotate_crop_image(bgr, object);
    if (crop.empty())
        return;

    const int target_h = 48;
    const int max_w = 256;
    const float ratio = (float)crop.cols / (float)crop.rows;
    int target_w = (int)(target_h * ratio);
    if (target_w < 16) target_w = 16;
    if (target_w > max_w) target_w = max_w;

    cv::Mat resized;
    cv::resize(crop, resized, cv::Size(target_w, target_h));

    ncnn::Mat in = ncnn::Mat::from_pixels(resized.data, ncnn::Mat::PIXEL_BGR2RGB, target_w, target_h);
    const float mean_vals[3] = {0.5f * 255.f, 0.5f * 255.f, 0.5f * 255.f};
    const float norm_vals[3] = {1.f / (0.5f * 255.f), 1.f / (0.5f * 255.f), 1.f / (0.5f * 255.f)};
    in.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = ppocrv5_rec.create_extractor();
    ex.input("in0", in);

    ncnn::Mat out;
    ex.extract("out0", out); // (T, 6625)

    object.text.clear();

    int last_token = 0;
    for (int t = 0; t < out.h; t++)
    {
        const float* scores = out.row(t);
        int max_index = 0;
        float max_score = -FLT_MAX;
        for (int c = 0; c < out.w; c++)
        {
            const float s = scores[c];
            if (s > max_score)
            {
                max_score = s;
                max_index = c;
            }
        }

        const int index = max_index;

        // ctc blank token = 0
        if (index == 0)
        {
            last_token = 0;
            continue;
        }

        // merge repeated tokens
        if (index == last_token)
            continue;
        last_token = index;

        Character ch;
        ch.id = index - 1;
        ch.prob = max_score;
        object.text.push_back(ch);
    }
}

struct PaddleOcr::Impl
{
    PPOCRv5 ppocr;
    bool ready = false;
};

PaddleOcr::PaddleOcr() : impl_(new Impl()) {}
PaddleOcr::~PaddleOcr()
{
    delete impl_;
}

bool PaddleOcr::load(
    const unsigned char* det_param, int det_param_len,
    const unsigned char* det_bin, int det_bin_len,
    const unsigned char* rec_param, int rec_param_len,
    const unsigned char* rec_bin, int rec_bin_len,
    const unsigned char* dict_txt, int dict_txt_len,
    bool use_vulkan)
{
    impl_->ppocr.dict.clear();
    if (dict_txt && dict_txt_len > 0)
    {
        std::string all((const char*)dict_txt, (size_t)dict_txt_len);
        size_t start = 0;
        while (start < all.size())
        {
            size_t end = all.find('\n', start);
            if (end == std::string::npos) end = all.size();
            std::string line = all.substr(start, end - start);
            if (!line.empty() && line.back() == '\r') line.pop_back();
            impl_->ppocr.dict.push_back(line);
            start = end + 1;
        }
    }

    impl_->ready = impl_->ppocr.init(det_param, det_param_len, det_bin, det_bin_len, rec_param, rec_param_len, rec_bin, rec_bin_len, use_vulkan);
    return impl_->ready;
}

std::vector<OcrLine> PaddleOcr::recognize_rgba8888(const unsigned char* rgba, int width, int height)
{
    std::vector<OcrLine> out;
    if (!impl_->ready || !rgba || width <= 0 || height <= 0)
        return out;

    cv::Mat rgbaMat(height, width, CV_8UC4, (void*)rgba);
    cv::Mat bgr;
    cv::cvtColor(rgbaMat, bgr, cv::COLOR_RGBA2BGR);

    std::vector<Object> objects;
    impl_->ppocr.detect(bgr, objects);
    for (size_t i = 0; i < objects.size(); i++)
    {
        impl_->ppocr.recognize(bgr, objects[i]);

        std::string text;
        float text_prob_sum = 0.f;
        int text_prob_n = 0;
        for (size_t j = 0; j < objects[i].text.size(); j++)
        {
            const Character& ch = objects[i].text[j];
            if (ch.id >= 0 && ch.id < (int)impl_->ppocr.dict.size())
            {
                text += impl_->ppocr.dict[ch.id];
                text_prob_sum += ch.prob;
                text_prob_n += 1;
            }
        }
        if (text.empty())
            continue;

        cv::Point2f corners[4];
        objects[i].rrect.points(corners);
        float l = corners[0].x, t = corners[0].y, r = corners[0].x, b = corners[0].y;
        for (int k = 1; k < 4; k++)
        {
            l = std::min(l, corners[k].x);
            t = std::min(t, corners[k].y);
            r = std::max(r, corners[k].x);
            b = std::max(b, corners[k].y);
        }

        OcrLine line;
        line.text = text;
        line.left = (int)std::max(0.f, l);
        line.top = (int)std::max(0.f, t);
        line.right = (int)std::min((float)width, r);
        line.bottom = (int)std::min((float)height, b);
        line.confidence = text_prob_n > 0 ? (text_prob_sum / (float)text_prob_n) : objects[i].prob;
        out.push_back(line);
    }
    return out;
}
} // namespace zhixu_ocr
