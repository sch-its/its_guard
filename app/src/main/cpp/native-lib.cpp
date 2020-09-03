#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <iostream>
#include <cstdlib>
#include <vector>
#include "opencv2/imgproc/imgproc.hpp"
#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/opencv.hpp>
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "opencv2/highgui.hpp"

using namespace cv;
using namespace std;

#define EPSILON 1E-5

///////////////new meth

//! Return the maximum of the provided numbers
static double maximum(double number1, double number2, double number3) {
    return std::max(std::max(number1, number2), number3);
}

//! Check if the two numbers are equal (almost)
/*!
* The expression for determining if two real numbers are equal is:
* if (Abs(x - y) <= EPSILON * Max(1.0f, Abs(x), Abs(y))).
*
* @param number1 First number
* @param number2 Second number
*/
static bool almostEqual(double number1, double number2) {
    return (std::abs(number1 - number2) <= (EPSILON * maximum(1.0, std::abs(number1), std::abs(number2))));
}

//! Determine the intersection point of two lines, if this point exists
/*! Two lines intersect if they are not parallel (Parallel lines intersect at
* +/- infinity, but we do not consider this case here).
*
* The lines are specified by a pair of points each. If they intersect, then
* the function returns true, else it returns false.
*
* Lines can be specified in the following form:
*      A1x + B1x = C1
*      A2x + B2x = C2
*
* If det (= A1*B2 - A2*B1) == 0, then lines are parallel
*                                else they intersect
*
* If they intersect, then let us denote the intersection point with P(x, y) where:
*      x = (C1*B2 - C2*B1) / (det)
*      y = (C2*A1 - C1*A2) / (det)
*
* @param a1 First point for determining the first line
* @param b1 Second point for determining the first line
* @param a2 First point for determining the second line
* @param b2 Second point for determining the second line
* @param intersection The intersection point, if this point exists
*/
static bool lineIntersection(const cv::Point2f &a1, const cv::Point2f &b1, const cv::Point2f &a2,
                             const cv::Point2f &b2, cv::Point2f &intersection) {
    double A1 = b1.y - a1.y;
    double B1 = a1.x - b1.x;
    double C1 = (a1.x * A1) + (a1.y * B1);

    double A2 = b2.y - a2.y;
    double B2 = a2.x - b2.x;
    double C2 = (a2.x * A2) + (a2.y * B2);

    double det = (A1 * B2) - (A2 * B1);

    if (!almostEqual(det, 0)) {
        intersection.x = static_cast<float>(((C1 * B2) - (C2 * B1)) / (det));
        intersection.y = static_cast<float>(((C2 * A1) - (C1 * A2)) / (det));

        return true;
    }

    return false;
}

struct vector_sorter
{
    bool operator ()(const std::vector<cv::Point>& a, const std::vector<cv::Point> & b)
    {
        double dist_a = norm(a[0] - a[1]);
        double dist_b = norm(b[0] - b[1]);
        return dist_a > dist_b;
    }
};

void sortCorners(std::vector<cv::Point2f>& corners)
{
    std::vector<cv::Point2f> top, bot;
    cv::Point2f center;
    // Get mass center
    for (int i = 0; i < corners.size(); i++)
        center += corners[i];
    center *= (1. / corners.size());

    for (int i = 0; i < corners.size(); i++)
    {
        if (corners[i].y < center.y)
            top.push_back(corners[i]);
        else
            bot.push_back(corners[i]);
    }
    corners.clear();

    if (top.size() == 2 && bot.size() == 2) {
        cv::Point2f tl = top[0].x > top[1].x ? top[1] : top[0];
        cv::Point2f tr = top[0].x > top[1].x ? top[0] : top[1];
        cv::Point2f bl = bot[0].x > bot[1].x ? bot[1] : bot[0];
        cv::Point2f br = bot[0].x > bot[1].x ? bot[0] : bot[1];

        corners.push_back(tl);
        corners.push_back(tr);
        corners.push_back(br);
        corners.push_back(bl);
    }
}

/////////////////////// new meth end

int IMAGE_SIZE = 1800;

class Point;

double angle(cv::Point pt1, cv::Point pt2, cv::Point pt0) {
    double dx1 = pt1.x - pt0.x;
    double dy1 = pt1.y - pt0.y;
    double dx2 = pt2.x - pt0.x;
    double dy2 = pt2.y - pt0.y;
    return (dx1*dx2 + dy1*dy2)/sqrt((dx1*dx1 + dy1*dy1)*(dx2*dx2 + dy2*dy2) + 1e-10);
}

void find_squares(Mat& image, vector<vector<cv::Point>>& squares) {
    // blur will enhance Edge detection
    Mat blurred(image);
    medianBlur(image, blurred, 9);

    Mat gray0(blurred.size(), CV_8U), gray;
    vector<vector<cv::Point> > contours;

    // find squares in every color plane of the image
    for (int c = 0; c < 3; c++)
    {
        int ch[] = {c, 0};
        mixChannels(&blurred, 1, &gray0, 1, ch, 1);

        // try several threshold levels
        const int threshold_level = 2;
        for (int l = 0; l < threshold_level; l++)
        {
            // Use Canny instead of zero threshold level!
            // Canny helps to catch squares with gradient shading
            if (l == 0)
            {
                Canny(gray0, gray, 10, 20, 3); //

                // Dilate helps to remove potential holes between Edge segments
                dilate(gray, gray, Mat(), cv::Point(-1,-1));
            }
            else
            {
                gray = gray0 >= (l+1) * 255 / threshold_level;
            }

            // Find contours and store them in a list
            findContours(gray, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);

            // Test contours
            vector<cv::Point> approx;
            for (size_t i = 0; i < contours.size(); i++)
            {
                // approximate contour with accuracy proportional
                // to the contour perimeter
                approxPolyDP(Mat(contours[i]), approx, arcLength(Mat(contours[i]), true)*0.02, true);

                // Note: absolute value of an area is used because
                // area may be positive or negative - in accordance with the
                // contour orientation
                if (approx.size() == 4 &&
                    fabs(contourArea(Mat(approx))) > 1000 &&
                    isContourConvex(Mat(approx)))
                {
                    double maxCosine = 0;

                    for (int j = 2; j < 5; j++)
                    {
                        double cosine = fabs(angle(approx[j%4], approx[j-2], approx[j-1]));
                        maxCosine = MAX(maxCosine, cosine);
                    }

                    if (maxCosine < 0.3)
                        squares.push_back(approx);
                }
            }
        }
    }
}

// comparison function object
bool compareContourAreas ( std::vector<cv::Point> contour1, std::vector<cv::Point> contour2 ) {
    double i = fabs( contourArea(cv::Mat(contour1)) );
    double j = fabs( contourArea(cv::Mat(contour2)) );
    return ( i > j );
}


cv::Mat debugSquares( std::vector<std::vector<cv::Point> > squares, cv::Mat image ) {
    /*for ( int i = 0; i< squares.size(); i++ ) {
     draw contour*/

    std::sort(squares.begin(), squares.end(), compareContourAreas);
    cv::drawContours(image, squares, 0, cv::Scalar(255,0,0), 1, 8, std::vector<cv::Vec4i>(), 0, cv::Point());
    /*
    // draw bounding rect
    cv::Rect rect = boundingRect(cv::Mat(squares[i]));
    cv::rectangle(image, rect.tl(), rect.br(), cv::Scalar(0,255,0), 2, 8, 0);

    // draw rotated rect
    cv::RotatedRect minRect = minAreaRect(cv::Mat(squares[i]));
    cv::Point2f rect_points[4];
    minRect.points( rect_points );
    for ( int j = 0; j < 4; j++ ) {
        cv::line( image, rect_points[j], rect_points[(j+1)%4], cv::Scalar(0,0,255), 1, 8 ); // blue
    }*/
    //}

    return image;
}

static std::vector<cv::Point> extremePoints(std::vector<cv::Point>pts) {
    int  xmin = 0, ymin = 0, xmax = -1, ymax = -1, i;
    cv::Point ptxmin, ptymin, ptxmax, ptymax;

    cv::Point pt = pts[0];

    ptxmin = ptymin = ptxmax = ptymax = pt;
    xmin = xmax = pt.x;
    ymin = ymax = pt.y;

    for (size_t i = 1; i < pts.size(); i++)
    {
        pt = pts[i];

        if (xmin > pt.x)
        {
            xmin = pt.x;
            ptxmin = pt;
        }


        if (xmax < pt.x)
        {
            xmax = pt.x;
            ptxmax = pt;
        }

        if (ymin > pt.y)
        {
            ymin = pt.y;
            ptymin = pt;
        }

        if (ymax < pt.y)
        {
            ymax = pt.y;
            ptymax = pt;
        }
    }
    std::vector<cv::Point> res;
    res.push_back(ptxmin);
    res.push_back(ptxmax);
    res.push_back(ptymin);
    res.push_back(ptymax);

    return res;
}
/*
void sortCorners(std::vector<cv::Point2f>& corners) {

    std::vector<cv::Point2f> top, bot;
    cv::Point2f center;
    // Get mass center
    for (int i = 0; i < corners.size(); i++)
        center += corners[i];
    center *= (1. / corners.size());

    for (int i = 0; i < corners.size(); i++)
    {
        if (corners[i].y < center.y)
            top.push_back(corners[i]);
        else
            bot.push_back(corners[i]);
    }
    corners.clear();

    if (top.size() == 2 && bot.size() == 2) {
        cv::Point2f tl = top[0].x > top[1].x ? top[1] : top[0];
        cv::Point2f tr = top[0].x > top[1].x ? top[0] : top[1];
        cv::Point2f bl = bot[0].x > bot[1].x ? bot[1] : bot[0];
        cv::Point2f br = bot[0].x > bot[1].x ? bot[0] : bot[1];


        corners.push_back(tl);
        corners.push_back(tr);
        corners.push_back(br);
        corners.push_back(bl);
    }
}*/



std::vector<std::vector<cv::Point>> findSquaresInImage(cv::Mat _image) {
    std::vector<std::vector<cv::Point>> squares;
    cv::Mat pyr, timg, gray0(_image.size(), CV_8U), gray;
    int thresh = 50, N = 11;
    cv::pyrDown(_image, pyr, cv::Size(_image.cols/2, _image.rows/2));
    cv::pyrUp(pyr, timg, _image.size());
    std::vector<std::vector<cv::Point> > contours;
    for( int c = 0; c < 3; c++ ) {
        int ch[] = {c, 0};
        mixChannels(&timg, 1, &gray0, 1, ch, 1);
        for( int l = 0; l < N; l++ ) {
            if( l == 0 ) {
                cv::Canny(gray0, gray, 0, thresh, 5);
                cv::dilate(gray, gray, cv::Mat(), cv::Point(-1,-1));
            }
            else {
                gray = gray0 >= (l+1)*255/N;
            }
            cv::findContours(gray, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);
            std::vector<cv::Point> approx;
            for( size_t i = 0; i < contours.size(); i++ )
            {
                cv::approxPolyDP(cv::Mat(contours[i]), approx, arcLength(cv::Mat(contours[i]), true)*0.02, true);
                if( approx.size() == 4 && fabs(contourArea(cv::Mat(approx))) > 1000 && cv::isContourConvex(cv::Mat(approx))) {
                    double maxCosine = 0;
                    for( int j = 2; j < 5; j++ ) {
                        double cosine = fabs(angle(approx[j%4], approx[j-2], approx[j-1]));
                        maxCosine = MAX(maxCosine, cosine);
                    }
                    if( maxCosine < 0.3 ) {
                        squares.push_back(approx);
                    }
                }
            }
        }
    }
    return squares;
}









///////////////////////////////////////////////////////////////  preprocess
Mat set_image_dpi(Mat &mat) {
    Mat result_image;
    Mat temp;

    int length_x = mat.cols;
    int width_y = mat.rows;

    int factor = max(1, int(IMAGE_SIZE / length_x));
    //size = (1800, 1800)
    resize(mat, result_image, Size( factor * length_x, factor * width_y), 0, 0, INTER_LINEAR);
    //src 이미지를 원본의 행과 열 절반 크기로 조정하여 temp에 저장
    //temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.jpg')
    //temp_filename = temp_file.name
    //im_resized.save(temp_filename, dpi=(300, 300))
    return result_image;
}

Mat image_smoothening(Mat &mat) {
    Mat result_image;
    Mat temp;

    cvtColor(mat,temp, COLOR_BGR2GRAY);

    //ret1, th1 = cv::threshold(mat, BINARY_THREHOLD, 255, cv2.THRESH_BINARY)
    //threshold( input_gray_image, result_binary_image, 127, 255, THRESH_BINARY );
    //ret2, th2 = cv2.threshold(th1, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    threshold(temp, temp, 0, 255, THRESH_BINARY | THRESH_OTSU);
    //blur = cv2.GaussianBlur(th2, (1, 1), 0)
    GaussianBlur(temp, temp, Size(1,1), 0);

    threshold(temp, result_image, 0, 255, THRESH_BINARY | THRESH_OTSU);
    //ret3, th3 = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return result_image;
}

Mat remove_noise_and_smooth(Mat &mat) {
    Mat result_image;
    Mat closing;
    Mat temp;
    //img = cv2.imread(file_name, 0)
    cvtColor(mat,temp, COLOR_BGR2GRAY);
    //filtered = cv2.adaptiveThreshold(img.astype(np.uint8), 255, cv2.ADAPTIVE_THRESH_MEAN_C,THRESH_BINARY, 41,3);
    adaptiveThreshold(temp, temp, 255, ADAPTIVE_THRESH_MEAN_C, THRESH_BINARY, 41, 3);

    Mat element(1,1,CV_8U,cv::Scalar(1));
    //kernel = np.ones((1, 1), np.uint8)

    //opening = cv2.morphologyEx(filtered, cv2.MORPH_OPEN, kernel)
    cv::morphologyEx(temp,temp,cv::MORPH_OPEN,element);
    //closing = cv2.morphologyEx(opening, cv2.MORPH_CLOSE, kernel)
    cv::morphologyEx(temp,closing,cv::MORPH_CLOSE,element);

    temp = image_smoothening(mat);
    bitwise_or(temp, closing, result_image);
    return result_image;
}
/////////////////////////////////////////////////////////////////////////////////////////////



/*
extern "C"
JNIEXPORT void JNICALL
Java_com_example_its_1ocr_MainActivity_detectRoI(JNIEnv *env, jobject thiz,
                                                        jlong mat_addr_input,
                                                        jlong mat_addr_result) {
    // TODO: implement ConvertRGBtoGray()
    // process

    Mat &matInput = *(Mat *)mat_addr_input;
    Mat &matResult = *(Mat *)mat_addr_result;

    int largest_area = 0;
    int largest_contour_index = 0;
    cv::Rect bounding_rect;
    Mat src, edges;
    src = matInput;

    vector<vector<cv::Point>> contours; // Vector for storing contour
    find_squares(src, contours);
    RotatedRect minRect;


    for (int i = 0; i< contours.size(); i++) // iterate through each contour.
    {
        double a = contourArea(contours[i], false);  //  Find the area of contour
        if (a>largest_area) {
            largest_area = a;
            largest_contour_index = i;                //Store the index of largest contour
            bounding_rect = boundingRect(contours[i]); // Find the bounding rectangle for biggest contour
            minRect = minAreaRect(contours[i]);
        }

    };

    std::vector<cv::Point> corner_points = extremePoints(contours[largest_contour_index]);
    std::vector<cv::Point2f> corners;

    corners.push_back(corner_points[0]);
    corners.push_back(corner_points[1]);
    corners.push_back(corner_points[2]);
    corners.push_back(corner_points[3]);

    sortCorners(corners);

    cv::Mat quad = cv::Mat::zeros(norm(corners[1] - corners[2]), norm(corners[2] - corners[3]), CV_8UC3);

    std::vector<cv::Point2f> quad_pts;
    quad_pts.push_back(cv::Point2f(0, 0));
    quad_pts.push_back(cv::Point2f(quad.cols, 0));
    quad_pts.push_back(cv::Point2f(quad.cols, quad.rows));
    quad_pts.push_back(cv::Point2f(0, quad.rows));

    cv::Mat transmtx = cv::getPerspectiveTransform(corners, quad_pts);
    cv::warpPerspective(src, quad, transmtx, quad.size());
    resize(quad, quad, Size(), 0.25, 0.25); // you can remove this line to keep the image original size
    //cv::imshow("quad", quad);

    polylines(src, contours[largest_contour_index], true, Scalar(0, 0, 255), 2);

    resize(src, src, Size(), 0.5, 0.5); // you can remove this line to keep the image original size
    matResult = src;
}*/


extern "C" JNIEXPORT void JNICALL
Java_com_example_its_1ocr_MainActivity_detectRoI(JNIEnv *env, jobject thiz,
                                                 jlong mat_addr_input,
                                                 jlong mat_addr_result,
                                                 jlong mat_addr_prepross) {

    Mat &matInput = *(Mat *) mat_addr_input;
    Mat &matResult = *(Mat *) mat_addr_result;
    Mat &matPrepross = *(Mat *) mat_addr_prepross;


    bool showsteps = true; // set it to false to see only result;
    Mat src, src_copy, edges;
    src = matInput;
    if (src.empty()) {
        src = Mat(400, 400, CV_8UC3, Scalar(127, 127, 127));
        rectangle(src, Rect(20, 200, 150, 50), Scalar(0, 0, 255), 8);
        rectangle(src, Rect(200, 200, 50, 50), Scalar(0, 0, 255), 8);
    }
    src_copy = src.clone();

    cvtColor(src, edges, COLOR_BGR2GRAY);
    GaussianBlur(edges, edges, Size(5, 5), 1.5, 1.5);

    erode(edges, edges, Mat());// these lines may need to be optimized
    dilate(edges, edges, Mat());
    dilate(edges, edges, Mat());
    erode(edges, edges, Mat());

    Canny(edges, edges, 50, 150, 3); // canny parameters may need to be optimized

    //if (showsteps) imshow("edges", edges);

    //cv::waitKey(1); // add this line

    vector<cv::Point> selected_points;
    vector<vector<cv::Point> > contours;

    std::cout << "1. findcontours\n"; // add this line

    findContours(edges, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);

    std::cout << "1. findcontours...OK\n"; // add this line

    for (size_t i = 0; i < contours.size(); i++) {
        Rect minRect = boundingRect(contours[i]);

        if ((minRect.width > src.cols / 6) |
            (minRect.height > src.rows / 6)) // this line also need to be optimized
        {
            selected_points.insert(selected_points.end(), contours[i].begin(), contours[i].end());


            if (showsteps) {
                drawContours(src_copy, contours, i, Scalar(0, 0, 255), 3);
            }
        }
    }

    //if (showsteps) imshow("Selected contours", src_copy );
    //waitKey(1);
    vector<Point2f> selected_points_f;
    vector<Point2f> corners;
    Mat(selected_points).convertTo(selected_points_f, CV_32F);
    Mat hull;
    convexHull(selected_points_f, hull, true, true);


    RotatedRect RRect = minAreaRect(hull);
    std::vector<cv::Point2f> RR_corners;
    Point2f four_points[4];
    RRect.points(four_points);
    RR_corners.push_back(four_points[0]);
    RR_corners.push_back(four_points[1]);
    RR_corners.push_back(four_points[2]);
    RR_corners.push_back(four_points[3]);

    for (int j = 0; j < 4; j++) {
        Point2f pt = RR_corners[j];
        Point2f nearest_pt = hull.at<Point2f>(j, 0);
        float dist = norm(pt - nearest_pt);
        for (int k = 1; k < hull.rows; k++) {
            Point2f hull_point = hull.at<Point2f>(k, 0);
            if (norm(pt - hull_point) < dist) {
                dist = norm(pt - hull_point);
                nearest_pt = hull_point;
            }
        }
        corners.push_back(nearest_pt);
    }
    sortCorners(corners);

    Mat(corners).convertTo(selected_points, CV_32S);

    Rect r = boundingRect(corners);
    cv::Mat quad = cv::Mat::zeros(norm(corners[1] - corners[2]), norm(corners[2] - corners[3]),
                                  CV_8UC3);

    std::vector<cv::Point2f> quad_pts;
    quad_pts.push_back(cv::Point2f(0, 0));
    quad_pts.push_back(cv::Point2f(quad.cols, 0));
    quad_pts.push_back(cv::Point2f(quad.cols, quad.rows));
    quad_pts.push_back(cv::Point2f(0, quad.rows));

    if (quad_pts.size() == 4) {
        cv::Mat transmtx = cv::getPerspectiveTransform(corners, quad_pts);
        cv::warpPerspective(src, quad, transmtx, quad.size());

        quad = remove_noise_and_smooth(quad);
    } else {
        quad = remove_noise_and_smooth(src_copy);
    }
    matPrepross = quad;


    if (showsteps)
    {
        src_copy = src.clone();
        polylines(src_copy, selected_points, true, Scalar(0,255,0), 10);
        matResult = src_copy;
    }

    cv::resize(matPrepross, matPrepross, Size(src.cols, src.rows), 0, 0, INTER_LINEAR);
}

/////////////////////////////////////////////////////////////////////////////////////
/*

extern "C"
JNIEXPORT void JNICALL
Java_com_example_its_1ocr_MainActivity_ConvertRGBtoGray(JNIEnv *env, jobject thiz,
                                                        jlong mat_addr_input,
                                                        jlong mat_addr_result) {
    // TODO: implement ConvertRGBtoGray()
    // process

    Mat &matInput = *(Mat *)mat_addr_input;
    Mat &matResult = *(Mat *)mat_addr_result;

    std::vector<std::vector<cv::Point>> squares;
    std::vector<cv::Point> approx;



    //squares = findSquaresInImage(matInput);
    find_squares(matInput, squares);
    matResult = debugSquares(squares, matInput);


    //edge detection
    Mat temp, input, output;
    cvtColor(matInput,temp,COLOR_BGR2GRAY);
    GaussianBlur(temp,temp,Size(3,3),0);
    Canny(temp,temp,75,200);
    //matResult = temp;


    //Find Contours
    std::vector<std::vector<cv::Point>> contours, cnt;
    std::vector<Vec4i> hierarchy;
    std::vector<cv::Point> approx, screenCnt, reset;
    double peri, contourSize;
    RNG rng(12345);
    Scalar color = Scalar(rng.uniform(0, 255), rng.uniform(0, 255), rng.uniform(0, 255));

    cv::findContours(temp, contours, hierarchy, RETR_LIST, CHAIN_APPROX_SIMPLE,Point(0,0));

    std::sort(contours.begin(), contours.end(), compareContourAreas);
    //std::vector<cv::Point> smallestContour = contours[contours.size()-1];
    //std::vector<cv::Point> biggestContour = contours[0];
    std::copy(contours.begin(), contours.begin()+ 5, cnt.begin());

    for(int i=0; i<cnt.size(); i++) {
        peri = cv::arcLength(cnt[i],true);
        cv::approxPolyDP(cnt[i], approx, 0.02*peri, true);
        screenCnt=reset;
        if(approx.size() == 4) {
            //사각형 모양일 경우
            //contourSize = contourArea(approx);
            //camSize =
            screenCnt = approx;
        }

        if (screenCnt.size() == 0) {
            //추출 영역이 없을 경우
            printf("not found");
        } else {
            printf("detected square");
            cv::drawContours(temp, screenCnt, -1, color, 2, 8, hierarchy, 8, Point(0,0));
        }
    }
    cv::cvtColor(temp, matResult, COLOR_BGR2GRAY);


    //image 전처리
    // dpi
    // remove noise and smooth
    //dpiResult = set_image_dpi(matInput);
    //matResult = remove_noise_and_smooth(dpiResult);

}*/