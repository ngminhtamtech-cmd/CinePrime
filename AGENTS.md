# CinePrime — hướng dẫn thực thi

- Người dùng đã duyệt Plan bằng yêu cầu `PLEASE IMPLEMENT THIS PLAN` ngày 05/11/2026. Được triển khai các mốc trong Plan.md; không cần xin lại từng mốc. Phần mở rộng vẫn cần trao đổi.
- Đọc Plan.md và UI-Template.md trước khi sửa. Plan.md ưu tiên về nghiệp vụ/stack; UI-Template.md ưu tiên về design tokens. Các đoạn Thymeleaf/React trong tài liệu tham khảo không phải stack được chọn.
- Java 25, Spring Boot MVC, Pug4J `.pug`, JPA, MySQL, Flyway, HTML/CSS/JavaScript thuần. Không thay engine, thêm frontend framework, Redis hoặc microservices.
- Chỉ dùng cấu trúc Maven `src/main/java/vn/cineprime`, `src/main/resources`, `src/test/java`, `.mvn`, file build/wrapper và các tài liệu gốc. Không tạo tools/, script Python, frontend project hay tài liệu trùng lặp.
- Upload là dữ liệu runtime tại đường dẫn cấu hình, không ghi vào source/JAR. Không đưa bí mật vào repository hoặc log.
- Tham khảo luồng Galaxy công khai trước từng nhóm UI. Ghi ngày/URL/phạm vi đã xem trong Plan.md. Không tuyên bố khảo sát phần bị chặn đăng nhập. Admin/Staff thiết kế từ nghiệp vụ.
- Giữ ghế 5 phút theo server, transaction all-or-nothing, không gia hạn khi reload. Backend tính tiền; snapshot giá. Callback và check-in phải idempotent. Không phát vé nếu thanh toán thành công muộn.
- Phân quyền và sở hữu dữ liệu tại backend; session, CSRF, password hash; khóa/đổi quyền tài khoản có hiệu lực với phiên cũ.
- Test xung đột ghế/voucher/check-in trên MySQL riêng. Không dùng H2 để kết luận khóa giao dịch. Kiểm chứng Pug UTF-8 từ executable JAR, desktop/mobile, PDF tiếng Việt.
- README và checklist Plan phản ánh kết quả đã kiểm chứng. Không gọi MoMo/email/deploy hoàn thành nếu chưa có cấu hình và kiểm thử thực tế.
- Cập nhật 05/11/2026: người dùng muốn test local trước; hoãn deploy. Vercel là nền tảng dự định, chưa tạo cấu hình container/serverless hoặc thay đổi stack. README mô tả các điều kiện cần đánh giá khi quay lại mốc triển khai.
