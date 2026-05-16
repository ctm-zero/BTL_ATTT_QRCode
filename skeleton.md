# Cấu trúc thư mục dự án QR Code

```
+---src
|   \---main
|       +---java
|       |   \---com
|       |       \---qr_project
|       |           \---qrcode
|       |               |   QrcodeApplication.java          <-- File chạy chính (Spring Boot Entry Point)
|       |               |   
|       |               +---controller
|       |               |       QRRestController.java       <-- Tiếp nhận yêu cầu tạo QR từ giao diện (API)
|       |               |       
|       |               +---service                         [TẦNG XỬ LÝ THUẬT TOÁN CHÍNH]
|       |               |   +---encoding
|       |               |   |       DataEncoding.java       <-- Chuyển văn bản thành chuỗi bit (Binary)
|       |               |   |       
|       |               |   +---error
|       |               |   |       ErrorCorrection.java    <-- Tính toán mã sửa lỗi (Reed-Solomon)
|       |               |   |       
|       |               |   \---layout
|       |               |           FormatAndVersion.java   
|       |               |           MatrixGenerator.java
|       |               |           MatrixMask.java
|       |               |           QRImageRenderer.java
|       |               |           
|       |               \---utils                           [TIỆN ÍCH & THÔNG SỐ]
|       |                       QRConstants.java            <-- Định nghĩa Version, Mask, Kích thước
|       |                       QRTable.java                <-- Bảng tra cứu Log/Antilog cho thuật toán
|       |                       
|       \---resources                                       [GIAO DIỆN NGƯỜI DÙNG]
|           \---static
|               |   index.html                              <-- Trang chủ giao diện nhập liệu
|               |   
|               +---css                                     <-- Thiết kế giao diện
|               |       style-pinky.css
|               |       style-techno.css
|               |       
|               \---js
|                       app.js                              <-- Xử lý gọi API, hiển thị ảnh QR và tải xuống QR
```

## Tóm tắt

- **`java/`** — Toàn bộ logic xử lý thuật toán QR chạy phía server
- **`static/`** — Các file giao diện, gọi xử lý API và trả ảnh QR về trình duyệt (JS, CSS, HTML)
- **`framework`** : **Spring Boot**