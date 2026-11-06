# CinePrime — đặc tả và kế hoạch triển khai

**Trạng thái: APPROVED — người dùng yêu cầu triển khai ngày 05/11/2026.**

## 1. Mục tiêu và tài liệu

Đồ án website đặt vé hoàn chỉnh cho Customer, Admin, Staff; phát triển trên Windows và chuẩn bị demo HTTPS. Toàn bộ chức năng cơ bản thuộc bản đầu. Google Login, VNPay, hoàn tiền, đổi điểm và gợi ý cá nhân thuộc lộ trình sau.

Ba tài liệu gốc: Plan.md (đặc tả và nghiệm thu), README.MD (cách chạy và hiện trạng), AGENTS.md (hướng dẫn AI). Ban đầu dự án chỉ có UI-Template.md; không có các file demo được nhắc trong tài liệu đó. Không thêm cổng duyệt từng mốc.

## 2. Chức năng

### Customer
- Tài khoản: đăng ký/đăng nhập/đăng xuất, quên/reset password qua email, hồ sơ/avatar/đổi password.
- Phim: đang/sắp chiếu, tìm theo tên, lọc thể loại/ngày/tuổi; poster/banner/trailer, thời lượng, diễn viên/đạo diễn, điểm/bình luận.
- Lịch theo phim/rạp/khu vực/ngày; sơ đồ ghế thường/VIP/đôi, trống/giữ/bán/bảo trì; giữ 5 phút.
- Combo, voucher, hóa đơn; MoMo sandbox; trạng thái đang xác minh/thành công/thất bại/hết hạn.
- Một QR/ghế, email xác nhận, lịch sử đơn/thanh toán, PDF vé.
- Yêu thích, đánh giá 1–5 sao và bình luận, nhắc phim sắp chiếu, nhắc suất trước 2 giờ, thông báo trong tài khoản.
- Tích điểm và lịch sử điểm; chưa đổi điểm.

### Admin
- Dashboard ngày/tuần/tháng/năm: doanh thu, vé, đơn, khách, phim đang/sắp chiếu, biểu đồ, top phim, lấp đầy.
- CRUD phim/thể loại/người tham gia; phim nổi bật/trạng thái; upload poster/banner/trailer MP4 và URL trailer.
- Rạp/địa chỉ/phòng/sơ đồ ghế/ghế đôi/bảo trì; lịch dạng bảng theo ngày; chặn trùng phòng và tự tính kết thúc.
- Giá theo rạp/ghế/ngày/khung giờ, phụ thu suất; snapshot giá.
- Tra cứu đơn/thanh toán, query MoMo, danh sách đối soát; xuất CSV giao dịch.
- Người dùng/role/khóa/mở/Staff theo rạp, lịch sử khách; bảo vệ Admin cuối cùng.
- Combo/đồ ăn/nước, voucher và quota; banner/bài viết/ưu đãi/thông báo/giới thiệu/liên hệ/chính sách; ẩn bình luận; nhật ký.

### Staff
- Login riêng; danh sách suất tại rạp phụ trách; camera QR hoặc tra mã đơn/vé.
- Xem phim/phòng/suất/ghế/trạng thái rồi xác nhận; một lần check-in/QR.
- Danh sách đã/chưa vào và số lượng. Không sửa giá/doanh thu/tài khoản.

## 3. UI và nguồn tham khảo

UI-Template.md làm chuẩn: trắng, xanh #1459A5, navy #111827, cam #F47B20; max-width 1232px; poster 2:3; grid 4/3/2; breakpoints 1090/850/620. Pug layout/include/mixin; thao tác mobile không phụ thuộc hover. Có loading/empty/error/expired/conflict/camera-denied. Không dựng nút giả.

Luồng: phim/rạp/ngày/suất → ghế → combo → xác nhận/voucher → MoMo → xác minh → vé.

| Ngày | URL Galaxy | Đã quan sát | Giới hạn |
|---|---|---|---|
| 05/11/2026 | https://www.galaxycine.vn/ | header, hero, tab phim, mobile menu | Nội dung tham khảo, không sao chép thương hiệu |
| 05/11/2026 | https://www.galaxycine.vn/phim-dang-chieu/ | poster, tuổi, điểm, danh sách | Không dùng dữ liệu/phim thực làm seed |
| 05/11/2026 | https://www.galaxycine.vn/dat-ve/bo-sua-bay/ | trailer, metadata, lịch theo rạp/ngày | Nội dung chỉ tham khảo bố cục |
| 05/11/2026 | https://www.galaxycine.vn/booking/ | khu vực → phim → suất; login/reset | Ghế/combo/payment bị chặn đăng nhập, chưa xác minh |
| 05/11/2026 | https://www.galaxycine.vn/rap-gia-ve/fb233b0f-edb4-4eb1-ade8-7f8b83ab2457/ | địa chỉ, chọn rạp, lịch, giá, chi tiết | Admin/Staff không có tham chiếu công khai |
| 05/11/2026 | https://www.galaxycine.vn/khuyen-mai/ | danh sách banner ưu đãi | Không sao chép ảnh/ưu đãi thực |

AI kiểm tra lại nhóm màn hình tương ứng khi triển khai; đánh dấu trung thực phần không truy cập được. So sánh desktop/mobile sau khi làm.

Cập nhật nhận diện 05/11/2026: logo SVG riêng kết hợp hình vé, chữ C và nút phát cam; dùng chung tại header, footer, trang xác thực và favicon. Đã kiểm tra header trên desktop và viewport mobile 390×844. Truy cập lại https://www.galaxycine.vn/ để đối chiếu header trong lần cập nhật này bị timeout; không có quan sát Galaxy mới. Logo được thiết kế riêng theo design tokens CinePrime.

## 4. Kiến trúc và dữ liệu

Java 25, Spring Boot 4.1.2, Maven Wrapper; Spring MVC/Security/JPA/Validation/Mail; Pug4J `.pug`; MySQL 8.4 InnoDB/utf8mb4; Flyway. Một executable JAR; JS thuần cho ghế, thanh toán, camera. Chưa thêm Redis/queue/microservices/Node server.

Cấu trúc Maven chuẩn, package vn.cineprime theo nghiệp vụ. Templates/static/migration trong resources; test trong src/test/java. Runtime upload qua biến môi trường; không ghi vào JAR. Không tools/ hoặc script Python.

Nhóm dữ liệu: account/role/staff cinema/reset token; movie/genre/person/cinema/room/seat/couple; showtime/seat inventory/pricing/hold; order/ticket/combo lines/payment/check-in; voucher/reservation/point ledger; favorite/review/subscription/notification; content/banner/site settings/audit/email outbox.

Trạng thái đơn PENDING_PAYMENT/PAID/EXPIRED/CANCELLED; payment CREATED/PENDING/SUCCEEDED/FAILED và cờ reconciliation; vé VALID/USED/VOID. Tách riêng, không xem đã trả tiền là đã vào rạp.

HTML render server; JSON chỉ cho sơ đồ/giữ ghế, thay combo/voucher, payment/IPN/status, tra/check-in, thông báo. Session + CSRF; kiểm role và ownership backend; hash password; rate limit login/reset/hold; token reset có hạn và dùng một lần; kiểm loại/kích thước upload. Không serialize entity chứa password/token ra client.

## 5. Nghiệp vụ

| Nhóm | Quy tắc |
|---|---|
| Thời gian/tiền | Asia/Ho_Chi_Minh, VND nguyên đồng |
| Đơn | Login; một suất; tối đa 8 ghế vật lý; một hold hoạt động/người |
| Hold | 5 phút khi Tiếp tục sau ghế; không gia hạn do reload/combo/voucher/payment; poll 5 giây |
| Ghế đôi | Hai ghế vật lý, bán nguyên cặp, hai QR, giá cặp rõ |
| Lịch | Đệm 15 phút; dừng bán trước 15 phút; chỉ hold khi còn đủ 5 phút |
| Check-in | Staff một rạp; từ -60 đến +30 phút so với bắt đầu; một lần/QR |
| Hủy | Chỉ đơn chưa thanh toán; chưa hoàn tiền |
| Đánh giá | Vé đã check-in, suất kết thúc; một/người/phim, sửa được; chỉ tính điểm của review hiển thị |
| Điểm | floor(thực trả/1000), cộng một lần, có ledger |
| Nhắc | Trước 2 giờ; mua sát giờ chỉ email xác nhận; nhắc phim lần đầu có suất tương lai mở bán |

Inventory unique(suất,ghế); khóa transaction theo thứ tự cố định; all-or-nothing. Kiểm expiry ngay trong nghiệp vụ và cleanup định kỳ. Chặn đổi phim/phòng/giờ/layout khi có hold hiệu lực/vé bán; hủy suất chưa bán sau khi giải phóng hold. Lưu lịch sử bằng inactive/soft delete.

Giá theo rạp × ghế × weekday/weekend × khung giờ + phụ thu suất. Weekend T7/CN; mẫu trước/sau17h, Admin sửa được. Thiếu giá chặn mở bán. Snapshot giá/tên vé/combo và tổng giảm; client không quyết tiền.

Một voucher/đơn, fixed/percent, min spend/cap/thời hạn/quota tổng và mỗi người; giữ quota cùng đơn, tiêu thụ khi paid, trả đúng một lần khi hủy/hết hạn. Combo không quản lý nguyên liệu.

MoMo captureWallet/autoCapture: server tạo payUrl; create thành công không phải paid. Redirect chỉ đọc trạng thái. IPN/query phải xác minh chữ ký, merchant, order, amount. Duplicate/reordered callback không lặp vé/điểm/quota/email. Thành công sau expiry ghi payment success + cần đối soát, không phát vé/chiếm lại ghế. Không giả định URL MoMo hết hạn 5 phút. HTTP timeout không phải hold TTL. Chỉ query/final success có thẩm quyền mới fulfill.

Email outbox retry; lỗi mail không rollback paid hoặc mất vé. Account xem/tải lại vé. Không hardcode hoặc log credentials.

## 6. Lộ trình và nghiệm thu

- [x] 0: Chốt phạm vi và phê duyệt.
- [x] 1: Build/JAR, MySQL/Flyway, Pug UTF-8, auth/role/CSRF, UI shell.
- [x] 2: CRUD catalog/cinema/room/seat/showtime/pricing, khám phá/lịch; bảng lịch theo ngày và xem sơ đồ phòng.
- [ ] 3: Mã nguồn hold/combo/voucher/invoice/adapter MoMo/outbox/QR/PDF đã có và test backend qua; còn nghiệm thu MoMo Test và SMTP thực tế khi có cấu hình.
- [x] 4: Staff camera/manual/check-in/statistics đã có; kiểm thử scope/time/concurrency trên MySQL. Camera thiết bị thật chưa xác minh.
- [x] 5: Profile/favorite/review/points/notifications/content/dashboard/CSV.
- [ ] 6: Seed/JAR/tests/README local đã có; HTTPS được người dùng hoãn, ưu tiên test local trước.

Test bắt buộc: race ghế và atomic multi-seat; TTL đúng 5 phút/đóng trang/reload; giả/sai tiền/duplicate/reorder/mất IPN/late success; race check-in/sai rạp/suất/time; overlap với đệm và sửa suất/layout; last voucher race/release/points once; snapshot giá; mail lỗi vẫn có vé; IDOR/RBAC/CSRF; PDF tiếng Việt/QR; desktop/mobile. Concurrency tests trên MySQL riêng, không dùng H2 để chứng minh locks. Không bắt buộc Docker.

Demo đủ phim tự tạo/rạp/ghế/suất/combo/voucher/3 role. JAR + MySQL + SMTP + HTTPS, env config. Cần MoMo test credentials/wallet và máy chủ/domain khi triển khai; không tuyên bố E2E/deploy khi chưa kiểm chứng.

## 7. Nguồn kỹ thuật

- https://docs.spring.io/spring-boot/system-requirements.html
- https://github.com/neuland/spring-pug4j
- https://developers.momo.vn/v3/vi/docs/payment/api/wallet/onetime/
- https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/
- https://developers.momo.vn/v3/vi/docs/payment/api/payment-api/query/
- https://learn.chatgpt.com/docs/agent-configuration/agents-md

## 8. Kết quả triển khai và cập nhật đã thống nhất

Ngày 06/11/2026 — tối ưu hiệu năng local:

- Dashboard loại bỏ query theo từng ghế/suất, dùng tổng hợp trong MySQL và một transaction đọc; kiểm thử 31 suất để chặn hồi quy số query, xác nhận tỷ lệ ghế bán/ghế hoạt động chính xác.
- Lịch chiếu lọc rạp/phim/ngày tại database và tải các bản ghi liên quan cùng truy vấn. API sơ đồ ghế tải ghế/tồn cùng truy vấn, tính giá một lần mỗi loại ghế trong request; không cache trạng thái ghế giữa các request. JSON không tải dữ liệu header/footer dư thừa.
- Pug cache template biên dịch mặc định bật; giữ nguyên BCrypt cost 12, kiểm tra khóa/đổi quyền từng request, CSRF và transaction đặt vé/check-in.
- Build JAR và 26 tests qua trên MySQL riêng, 0 failures/errors/skipped. Hai test hiệu năng mới kiểm tra query dashboard và sơ đồ 105 ghế, đồng thời xác nhận hold mới/hết hạn được phản ánh ngay.
- Chạy lại trong IntelliJ; HTTP local sau làm nóng: dashboard 74–108 ms (trước 5.648–6.146 ms), đăng nhập ba vai trò 231–254 ms, tài khoản 23–39 ms, sơ đồ ghế 21–24 ms. Chưa kiểm thử tải đồng thời ở quy mô production. Chi tiết cách bật/tắt cache và địa chỉ loopback trong README.

Ngày 05/11/2026:

- Build executable JAR thành công với Java 25 / Boot 4.1.2 / Pug4J 3.6.0. Resolver Pug được đăng ký tường minh để không bị resolver mặc định Boot che.
- MySQL 8.4.13 chạy biệt lập trên loopback, migrations V1/V2 và Hibernate validate qua. Không cài dịch vụ Windows; không dùng H2.
- 24 tests qua, 0 failures/errors/skipped trong lượt MySQL: tranh ghế/voucher/check-in; atomic/expiry/couple; snapshot; HMAC/callback duplicate/reordered/late/canceled; free voucher; reset; overlap; HTTP/RBAC/CSRF/ownership/session và login quay lại suất đã chọn; PDF tiếng Việt và QR decode. JavaScript qua node --check.
- Test cạnh tranh phát hiện stale persistence context/REPEATABLE READ; đã sửa READ COMMITTED và refresh tại lần lấy khóa đầu, rồi xác nhận lại cả bộ.
- Giao diện có Inter local, SVG tự tạo, carousel featured, mua vé nhanh phụ thuộc, filter/date strip, seat polling, countdown và QR scanner ZXing kèm license. Đã xem desktop/mobile; đã sửa tràn ngang seat grid và thêm logout mobile.
- Đơn có tổng bằng 0 sau voucher được xác nhận miễn phí, provider VOUCHER, không gọi MoMo; không giả kết quả gateway. Seed TRAINGHIEM giảm 100% để trải nghiệm local trước khi có khóa, vẫn kiểm TTL/quota và điểm theo thực trả.
- Cập nhật của người dùng: chưa cấu hình dịch vụ ngoài; có tài khoản MoMo nhưng chưa rõ ví cá nhân hay M4B; muốn Vercel sau này, hiện **test local trước**, chưa triển khai.
- Vercel hiện có hướng Container Functions; trước mốc đó cần đánh giá Java container, durable session/upload và scheduler/worker. Chưa tạo Dockerfile/vercel.json và chưa đổi stack. Nguồn: https://vercel.com/i/do-you-need-docker-to-deploy
- Chưa tuyên bố MoMo/SMTP/camera thiết bị thật/HTTPS đã nghiệm thu. README có hướng dẫn M4B Test và env; không lưu bí mật trong repository.
- Browser local đã xác minh Customer login → A1 → hold 5 phút → thêm combo → voucher TRAINGHIEM → giảm còn 0 → xác nhận miễn phí → PAID/VOUCHER và QR/PDF link. Cập nhật combo không gia hạn TTL; carousel và bộ chọn phim/rạp/ngày/suất phụ thuộc hoạt động. Dữ liệu đơn thử được giữ lại để người dùng xem trong tài khoản demo.
