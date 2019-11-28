#include <jni.h>
#include <string>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/opencv.hpp>
#include <opencv2/imgproc/imgproc.hpp>

using namespace cv;
using namespace std;

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhean_bestframe_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
    string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhean_bestframe_MainActivity_returnVarLapla(JNIEnv* env, jobject, jlong matAddr){

    Mat &mat = *(Mat *) matAddr;

    // Increase Brightness and Contrast
    Mat new_image = Mat::zeros( mat.size(), mat.type() );
    for( int y = 0; y < mat.rows; y++ ) {
        for( int x = 0; x <  mat.cols; x++ ) {
            for( int c = 0; c < mat.channels(); c++ ) {
                new_image.at<Vec3b>(y,x)[c] = saturate_cast<uchar>( 3.0*mat.at<Vec3b>(y,x)[c] + 100 );
            }
        }
    }

    Mat laImg;
    Laplacian(new_image, laImg, CV_64F); // Convert to Laplacian ImageOCR
    Scalar mean, stddev;
    meanStdDev(laImg, mean, stddev, Mat());
    double variance = stddev.val[0] * stddev.val[0]; // Variance of Laplacian

    return env->NewStringUTF(to_string(variance).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhean_bestframe_VideoActivity_returnVarLapla(JNIEnv* env, jobject, jlong matAddr){

    Mat &mat = *(Mat *) matAddr;

    // Increase Brightness and Contrast
    Mat new_image = Mat::zeros( mat.size(), mat.type() );
    for( int y = 0; y < mat.rows; y++ ) {
        for( int x = 0; x <  mat.cols; x++ ) {
            for( int c = 0; c < mat.channels(); c++ ) {
                new_image.at<Vec3b>(y,x)[c] = saturate_cast<uchar>( 3.0*mat.at<Vec3b>(y,x)[c] + 100 );
            }
        }
    }

    Mat laImg;
    Laplacian(new_image, laImg, CV_64F); // Convert to Laplacian ImageOCR
    Scalar mean, stddev;
    meanStdDev(laImg, mean, stddev, Mat());
    double variance = stddev.val[0] * stddev.val[0]; // Variance of Laplacian

    return env->NewStringUTF(to_string(variance).c_str());
}