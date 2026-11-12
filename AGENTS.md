# CinePrime — hướng dẫn thực thi

- Người dùng đã duyệt Plan bằng yêu cầu `PLEASE IMPLEMENT THIS PLAN` ngày 05/11/2026. Được triển khai các mốc trong Plan.md; không cần xin lại từng mốc. Phần mở rộng vẫn cần trao đổi.
- Đọc Plan.md và UI-Template.md trước khi sửa. Plan.md ưu tiên về nghiệp vụ/stack; UI-Template.md ưu tiên về design tokens. Các đoạn Thymeleaf/React trong tài liệu tham khảo không phải stack được chọn.
- Java 25, Spring Boot MVC, Pug4J `.pug`, JPA, MySQL, Flyway, HTML/CSS/JavaScript thuần. Không thay engine, thêm frontend framework, Redis hoặc microservices.
- Chỉ dùng cấu trúc Maven `src/main/java/vn/cineprime`, `src/main/resources`, `src/test/java`, `.mvn`, file build/wrapper và các tài liệu gốc. Không tạo tools/, script Python, frontend project hay tài liệu trùng lặp.
- Upload là dữ liệu runtime tại đường dẫn cấu hình, không ghi vào source/JAR. Không đưa bí mật vào repository hoặc log.
- Tham khảo luồng Galaxy công khai trước từng nhóm UI. Ghi ngày/URL/phạm vi đã xem trong Plan.md. Không tuyên bố khảo sát phần bị chặn đăng nhập. Admin/Staff thiết kế từ nghiệp vụ.
- Giữ ghế 5 phút theo server, transaction all-or-nothing, không gia hạn khi reload. Backend tính tiền; snapshot giá. Callback và check-in phải idempotent. Không phát vé nếu thanh toán thành công muộn.
- Phân quyền và sở hữu dữ liệu tại backend; session, CSRF, password hash; khóa/đổi quyền tài khoản có hiệu lực với phiên cũ.
- Test xung đột ghế/voucher/check-in trên MySQL riêng. Không dùng H2 để kết luận khóa giao dịch. Kiểm chứng Pug UTF-8 từ executable JAR, PDF tiếng Việt; giao diện khách xem cả desktop/mobile, Admin/Staff chỉ desktop.
- README và checklist Plan phản ánh kết quả đã kiểm chứng. Không gọi MoMo/email/deploy hoàn thành nếu chưa có cấu hình và kiểm thử thực tế.
- Cập nhật 05/11/2026: người dùng muốn test local trước; hoãn deploy. Vercel là nền tảng dự định, chưa tạo cấu hình container/serverless hoặc thay đổi stack. README mô tả các điều kiện cần đánh giá khi quay lại mốc triển khai.
- Cập nhật 12/11/2026 — responsive: chỉ giao diện khách (User/Customer) cần responsive desktop/mobile. Admin và Staff chỉ hỗ trợ desktop (mốc 1366×768 trở lên); không làm hoặc nghiệm thu mobile/breakpoint cho màn điều hành, không thêm drawer/ngăn mobile mới.
- Cập nhật 12/11/2026 — database: vẫn chạy test và thử GUI trên `cineprime_test` trước. Khi bộ test đầy đủ (MySQL, 0 failures/errors/skipped) đã qua, áp dụng luôn thay đổi vào database chuẩn `cineprime` (migration Flyway mới, dữ liệu cấu hình cần thiết) mà không cần hỏi lại. Test thất bại thì không đụng `cineprime`. Không reset/xóa dữ liệu `cineprime`; trước migration có DDL hoặc sửa dữ liệu hàng loạt thì sao lưu (mysqldump) trước. Ghi lại việc đã áp dụng trong Plan.md/README.
