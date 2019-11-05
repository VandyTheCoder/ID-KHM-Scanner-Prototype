# ID-KHM-Scanner-Prototype
This is the prototype that can extract text from your Khmer ID card. To put it simply as developers, it is the OCR-Android-Application that
read the text on Khmer ID Card.

The processing flow of this app is:
- Get each frame from streaming camera
- Find the frame which clear and not blurred
- Apply google mobile vision to extract text from that frame
- Output the results.

## Required Library
- [OpenCV](https://opencv.org/)(4.1.1): It is used to extract the frame and find the frame which is clear.
- [Mobile Vision](https://developers.google.com/vision)(18.0.0): It is used to do the OCR(read text) from the frame.

## Programming Language
- Kotlin: User interface, Camera, OCR and Validataion
- C++: Calculation

## Additional Information
In order to find the frame which is not blurred, we do the calculation of varaince of Laplacian method.
We also set the threshold value in order to let the system decide it blur or not.
We have done some experiment on it and this result is described in the picture below

![Experiment Output](./OpenCV-Threshold.png?raw=true "Optional Title")
