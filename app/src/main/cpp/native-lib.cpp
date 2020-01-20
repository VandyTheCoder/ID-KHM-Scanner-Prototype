#include <jni.h>
#include <string>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/opencv.hpp>

#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_processing/render_face_detections.h>
#include <dlib/image_processing.h>

#include <android/log.h>

using namespace cv;
using namespace std;
using namespace dlib;

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhean_bestframe_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
    string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhean_bestframe_ResultActivity_returnFaceDetection(JNIEnv* env, jobject){
    string hello = "Success";

    frontal_face_detector detector = get_frontal_face_detector();
    shape_predictor sp;
    deserialize("/storage/emulated/0/shape_predictor_68_face_landmarks.dat") >> sp;
    __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ","load shape_predictor_68_face_landmarks");

    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhean_bestframe_MainActivity_returnVarLapla(JNIEnv* env, jobject, jlong matAddr){

    try {
        Mat &mat = *(Mat *) matAddr;
//        Size size(1080,720);
//        resize(mat,mat,size);

        // Crop the biggest object on Image
        const int cropSize = 480;
        const int offsetW = (mat.cols - cropSize) / 2;
        const int offsetH = (mat.rows - cropSize) / 2;
        const Rect roi(offsetW, offsetH, cropSize, cropSize);
        mat = mat(roi).clone();

        // Increase Brightness and Contrast
        Mat new_image = Mat::zeros( mat.size(), mat.type() );
        for( int y = 0; y < mat.rows; y++ ) {
            for( int x = 0; x <  mat.cols; x++ ) {
                for( int c = 0; c < mat.channels(); c++ ) {
                    new_image.at<Vec3b>(y,x)[c] = saturate_cast<uchar>( 3*mat.at<Vec3b>(y,x)[c] + 75 );
                }
            }
        }

        Mat laImg;
        Laplacian(new_image, laImg, CV_64F); // Convert to Laplacian ImageOCR
        Scalar mean, stddev;
        meanStdDev(laImg, mean, stddev, Mat());
        double variance = stddev.val[0] * stddev.val[0]; // Variance of Laplacian
        return env->NewStringUTF(to_string(variance).c_str());
    } catch (const exception& e) { // caught by reference to base
        return env->NewStringUTF(to_string(0).c_str());
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nhean_bestframe_VideoActivity_returnVarLapla(JNIEnv* env, jobject, jlong matAddr){

    try {
        Mat &mat = *(Mat *) matAddr;
        Size size(1080,720);
        resize(mat,mat,size);

        // Increase Brightness and Contrast
        Mat new_image = Mat::zeros( mat.size(), mat.type() );
        for( int y = 0; y < mat.rows; y++ ) {
            for( int x = 0; x <  mat.cols; x++ ) {
                for( int c = 0; c < mat.channels(); c++ ) {
                    new_image.at<Vec3b>(y,x)[c] = saturate_cast<uchar>( 3*mat.at<Vec3b>(y,x)[c] + 75 );
                }
            }
        }

        Mat laImg;
        Laplacian(new_image, laImg, CV_64F); // Convert to Laplacian ImageOCR
        Scalar mean, stddev;
        meanStdDev(laImg, mean, stddev, Mat());
        double variance = stddev.val[0] * stddev.val[0]; // Variance of Laplacian
        return env->NewStringUTF(to_string(variance).c_str());
    } catch (const exception& e) { // caught by reference to base
        return env->NewStringUTF(to_string(0).c_str());
    }
}