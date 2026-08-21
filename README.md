# Hướng dẫn Chạy và Triển khai ứng dụng Bahung

Tài liệu này chứa mọi thứ bạn cần để chạy ứng dụng của mình cục bộ.

Xem ứng dụng của bạn tại Bahung: https://bahung.studio/apps/410baf51-39e6-47c3-8d35-d225225ff7da

## Chạy cục bộ

**Yêu cầu:** [Android Studio](https://developer.android.com/studio)

1. Mở Android Studio
2. Chọn **Open** và chọn thư mục chứa dự án này
3. Cho phép Android Studio sửa các lỗi không tương thích khi nhập dự án.
4. Tạo một tệp có tên `.env` trong thư mục dự án và đặt `GEMINI_API_KEY` trong tệp đó thành mã khóa Gemini API của bạn (xem tệp `.env.example` để biết ví dụ)
5. Xóa dòng này khỏi tệp `build.gradle.kts` của ứng dụng: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Chạy ứng dụng trên trình giả lập hoặc thiết bị vật lý
7. Nếu bạn đã xuất bản ứng dụng của mình qua hệ thống Bahung, vui lòng [yêu cầu đặt lại khóa tải lên](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) trong Google Play Console.
