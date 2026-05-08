# Cấu trúc thư mục dự án QR Code

```
src/main/
├── java/com/qr_project/qrcode/
│   ├── controller/          -> Tiếp nhận request từ trình duyệt, trả về response
│   ├── service/
│   │   ├── encoding/        -> Mã hoá dữ liệu đầu vào thành bitstream
│   │   ├── error/           -> Tạo mã sửa lỗi Reed-Solomon
│   │   └── layout/          -> Dựng ma trận QR từ dữ liệu đã xử lý
│   └── model/               -> Các class đối tượng (request, response, DTO)
│
└── resources/
    ├── static/
    │   ├── js/              -> JavaScript: gọi API, vẽ QR lên canvas
    │   └── css/             -> CSS: giao diện, layout trang web
    └── templates/           -> File HTML: trang web hiển thị cho người dùng
```

## Tóm tắt

- **`java/`** — Toàn bộ logic xử lý thuật toán QR chạy phía server
- **`static/`** — File tĩnh trả thẳng về trình duyệt (JS, CSS)
- **`templates/`** — File HTML được Spring Boot render và phục vụ