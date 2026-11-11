# CinePrime — đặc tả và kế hoạch triển khai

**Trạng thái: APPROVED — người dùng yêu cầu triển khai ngày 05/11/2026.**

## 1. Mục tiêu và tài liệu

Đồ án website đặt vé hoàn chỉnh cho Customer, Admin, Staff; phát triển trên Windows và chuẩn bị demo HTTPS. Toàn bộ chức năng cơ bản thuộc bản đầu. Google Login, VNPay, hoàn tiền, đổi điểm và gợi ý cá nhân thuộc lộ trình sau.

Ba tài liệu gốc: Plan.md (đặc tả và nghiệm thu), README.MD (cách chạy và hiện trạng), AGENTS.md (hướng dẫn AI). Ban đầu dự án chỉ có UI-Template.md; không có các file demo được nhắc trong tài liệu đó. Không thêm cổng duyệt từng mốc.

## 2. Chức năng

### Customer
- Tài khoản: đăng ký/đăng nhập/đăng xuất, quên/reset password qua email, hồ sơ/avatar/đổi password.
- Phim: đang/sắp chiếu/vừa kết thúc, tìm theo tên, lọc thể loại/ngày/tuổi; poster/banner/trailer, thời lượng, diễn viên/đạo diễn, điểm/bình luận. Đặt trước suất đã mở bán của phim sắp chiếu; phim đã kết thúc hiện mờ, khóa đặt vé 7 ngày rồi rời danh sách.
- Lịch theo phim/rạp/khu vực/ngày; sơ đồ ghế thường/VIP/đôi, trống/giữ/bán/bảo trì; giữ 5 phút.
- Combo, voucher, hóa đơn; MoMo sandbox; trạng thái đang xác minh/thành công/thất bại/hết hạn.
- Một QR/ghế, email xác nhận, lịch sử đơn/thanh toán, PDF vé.
- Yêu thích, đánh giá 1–5 sao và bình luận, nhắc phim sắp chiếu, nhắc suất trước 2 giờ, thông báo trong tài khoản.
- Tích điểm và lịch sử điểm; chưa đổi điểm.

### Admin
- Dashboard ngày/tuần/tháng/năm: doanh thu, vé, đơn, khách, phim đang/sắp chiếu, biểu đồ, top phim, lấp đầy.
- CRUD phim/thể loại/người tham gia; phim nổi bật/ẩn hiện; ngày khởi chiếu và ngày kết thúc bắt buộc; upload poster/banner/trailer MP4 và URL trailer.
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
| 06/11/2026 | https://www.galaxycine.vn/lich-chieu/ | Phần công khai chọn vị trí/phim/suất, các bước đặt vé, menu và footer qua nội dung trang | Không khảo sát khu vực sau đăng nhập; không có tham chiếu Admin/Staff công khai. Admin/Staff thiết kế theo nghiệp vụ CinePrime |
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
| Vòng đời phim | Khoảng chiếu [ngày khởi chiếu, ngày kết thúc] bắt buộc, theo ngày Việt Nam, tính lúc đọc: trước khoảng = Sắp chiếu, trong khoảng = Đang chiếu, sau khoảng = Đã kết thúc. Suất (nháp hoặc mở bán) chỉ được bắt đầu trong khoảng; sửa phim không được bỏ suất chưa hủy ra ngoài khoảng; khóa thời lượng khi còn suất chưa hủy sắp tới. Suất mở bán của phim Sắp chiếu đặt trước được. Phim Đã kết thúc hiện mờ, không đặt vé, 7 ngày sau rời danh sách công khai; trang chi tiết vẫn mở khi phim chưa ẩn. Gỡ khẩn cấp dùng Ẩn (`active=false`), không còn trạng thái Ngừng chiếu |
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

### Form Admin từng bước — được duyệt 07/11/2026

> Cập nhật 11/11/2026: form **phim** không còn đi theo các bước dưới đây; đã thay bằng form một trang (xem mục "Vòng đời phim có ngày kết thúc và làm lại UX Admin"). Các form khác vẫn dùng cấu trúc bước.

- [x] Form dài thêm/sửa dùng một form DOM, thanh tiến trình, từng trang con và footer cố định; tạo mới đi tuần tự, chỉnh sửa chuyển nhóm trực tiếp, chỉ trang Xác nhận có nút ghi dữ liệu. Form ngắn Lưu trực tiếp. Không lưu nháp vào storage hoặc database.
- [x] Phim giữ bốn nhóm Thể loại → Thông tin → Poster/Banner → Trailer/Hiển thị; Thông tin có trang con tên/đường dẫn, tuổi/thời lượng/ngày, mô tả, đạo diễn và diễn viên. Bộ chọn tìm kiếm/năm mục mỗi trang giữ lựa chọn khi làm mới; chỉ ô văn bản dài cuộn riêng.
- [x] Thêm/sửa Đạo diễn/Diễn viên hiện có cùng phim và thể loại trong một transaction; payload cũ giữ liên kết. Khóa phim/quan hệ được đồng bộ với đường ghi đoàn phim cũ; đọc tên theo lô, tránh N+1. Không thêm role hoặc schema.
- [x] Suất, voucher, nội dung, người dùng, rạp và sản phẩm có cấu trúc bước riêng. Giữ ngữ cảnh rạp/phòng, trường khóa, CSRF, giá nguyên VND/24:00 và Lưu/kết thúc/tiếp tục. Workspace ghế/giá chuyển riêng danh sách–form, giữ bản xem trước.
- [x] Upload chặn Kế tiếp/Lưu khi đang tải hoặc lỗi; lỗi backend có mã trường, mở đúng bước và focus lại; giữ tên, mô tả, lựa chọn và ảnh đã tải. POST vẫn dùng route cũ; chế độ JS nhận JSON, lỗi giữ DOM, mất phản hồi không tự gửi lại. Mật khẩu không có trong xác nhận/lỗi và phải nhập lại khi tạo tài khoản gặp lỗi.
- [x] Regression đầy đủ: **73 tests, 0 failures/errors/skipped**, gồm 64 MySQL/HTTP và 9 unit/HMAC/render; log `target/form-wizard-full.log`. Bản đóng gói cuối kiểm tra lại Pug 2 test qua tại `target/form-wizard-final-package.log`; không cộng lượt chạy lại vào số test.
- [x] HTTP từ executable JAR: 14 form trả 200, script mới và tiếng Việt sau giải mã HTML entities đúng; `target/form-wizard-jar-verification.json`. GUI trên cineprime_test đã thêm người tham gia ở tab khác → gán hai vai trò → upload → xác nhận → lỗi trailer giữ dữ liệu → sửa và lưu → mở lại chỉnh sửa chuyển nhóm trực tiếp. Focus lỗi cuối đã đo được `field-trailer`.
- [x] Đã đo 1366×768, 1920×1080, 390×844 và breakpoint 620/850/1090 trên bản xem thử Pug; form không cuộn trang, footer trong viewport. JAR xác nhận lại desktop/mobile; ảnh `target/form-wizard-desktop.jpg`, `target/form-wizard-mobile.jpg`. Viewport thu nhỏ 390×480 kiểm tra ô nhập/footer không bị che; chưa xác minh bàn phím điện thoại thật.
- [x] Dữ liệu ứng dụng giữ nguyên: checksum **30/30 bảng khớp** trước/sau, gồm Flyway; 1 phim, 1 rạp, 3 phòng, 180 ghế, 6 giá. Không reset cineprime, không thay mật khẩu hoặc tự gán danh mục. Bằng chứng `target/form-wizard-app-before.json`, `target/form-wizard-app-after.json`, `target/form-wizard-data-verification.json`.
- [ ] Cần kiểm tra thủ công thêm dialog CinePrime bỏ thay đổi (Quay lại/Bỏ và kết thúc), mất phản hồi ghi và upload bị chậm/lỗi chủ động. Lượt GUI cuối bị kẹt ở hộp thoại native của phiên trình duyệt cũ; đã thay Hủy bằng HTML dialog và xác nhận tài nguyên JS mới nằm trong JAR, chưa đánh dấu các thao tác này đã nghiệm thu đầy đủ. Camera/điện thoại thật và dịch vụ ngoài vẫn ngoài đợt này.

Tham khảo: thử mở lại https://www.galaxycine.vn/lich-chieu/ ngày 07/11/2026 bị redirect loop; không có quan sát Galaxy mới hoặc phần sau đăng nhập. Bố cục form dựa trên bản phác thảo của người dùng và nghiệp vụ CinePrime, giữ design tokens hiện có.

### Màn làm việc sơ đồ tập trung — được duyệt 07/11/2026

Phạm vi đã chốt: Admin mặc định thu sidebar, desktop không cuộn trang; sơ đồ zoom/pan và bảng công cụ 320–360px cuộn riêng, mobile dùng ngăn dưới. Chỉnh ghế/cặp vào bản xem trước; giá thêm/sửa ngay tại chỗ, lưu độc lập. Tạo phòng ưu tiên Lưu và kết thúc, thiết lập ghế và tạo suất tùy chọn. Giữ dữ liệu ứng dụng và mọi khóa/kiểm tra giá/snapshot; không thêm schema, framework hoặc thanh toán.

Tham khảo công khai ngày 07/11/2026: https://www.galaxycine.vn/lich-chieu/ — các bước chọn phim/rạp/suất, ghế, thức ăn, thanh toán công khai; không khảo sát sau đăng nhập. Không gian quản trị thiết kế từ nghiệp vụ CinePrime.

- [x] Khung viewport, drawer, zoom/pan, bảng chỉnh ghế/cặp và bản nháp không mất khi chuyển vùng.
- [x] Giá tại chỗ: đọc database 5 dòng/trang, ADMIN/CSRF/no-store, phiên bản riêng, khóa rạp → giá thống nhất và giữ snapshot.
- [x] Lưu phòng và kết thúc, lưu sơ đồ không ép chuyển trang, cảnh báo rời khi chưa lưu.
- [x] MySQL regression đầy đủ, JAR/Pug UTF-8, desktop/mobile/breakpoint và các trạng thái mạng mô phỏng.
- [x] Đối chiếu dữ liệu giữ nguyên, ảnh nghiệm thu, README và bản local mới.

Kết quả nghiệm thu ngày 07/11/2026:

- **68 tests, 0 failures/errors/skipped**, gồm 61 tích hợp MySQL/HTTP và 7 unit/HMAC; JAR BUILD SUCCESS lúc 17:25:52, log `target/workspace-full-final.log`. Giữ các ca tiền/snapshot, tranh ghế/voucher/check-in, callback trùng/muộn và PDF tiếng Việt. Lượt đầu ba ca nhận 429 do dùng chung cửa sổ đăng nhập; fixture nay cô lập bộ đếm giữa các ca, không đổi bảo mật production. Ca mới xác minh lần đăng nhập thứ 16 trong một phút thực sự bị chặn. Lượt lỗi đầu vẫn lưu ở `target/workspace-full.log`.
- Giá inline thêm/sửa từ phòng nhưng áp dụng theo rạp; API đọc 5 dòng/trang tại database, POST ADMIN/CSRF, phiên bản riêng chống ghi đè. Khóa đọc phòng giữ ngữ cảnh rạp; mọi đường ghi giá khóa rạp trước bản ghi giá. Kiểm thử tạo trùng đồng thời, sửa cũ 409, khác rạp, 24:00, số nguyên VND, bảo vệ giá cho suất đang mở và không đổi OrderSeat/KPI của đơn cũ.
- GUI executable JAR/schema test: Lưu và kết thúc quay đúng danh sách mà không tạo ghế/suất; đi tiếp tạo sơ đồ 6×10 với hàng VIP/Couple, sửa cả cặp, thêm rồi xóa ghế chưa dùng, lưu/mở lại đúng. Bản xem trước và cả trường đang chỉnh chưa Áp dụng được giữ khi đổi vùng; rời hoặc chọn ghế khác có cảnh báo. Lưu sơ đồ không chuyển trang. Phòng có lịch vẫn khóa theo quy tắc cũ.
- Thêm giá ngay trong bảng bên cạnh; nút Thêm giá/chọn dòng sửa đưa focus vào form. Giá 95.001đ/ghế hiển thị 190.002đ/cặp; sửa tại chỗ, giá trùng giữ dữ liệu. Hai tab sửa cùng giá: tab dùng phiên bản cũ bị chặn và vẫn giữ số tiền đang nhập. Không phát sinh thanh toán thật.
- Desktop 1366×768 và 1920×1080: sơ đồ 60 và 600 vị trí, trang không cuộn và thanh lưu luôn trong viewport. Vừa khung/100%/zoom, phím điều hướng và pan được kiểm tra; mobile 390×844 dùng ngăn công cụ đóng/mở, ghế tại 100% là 44×44px. Đổi kích thước reset vùng nhìn để tránh sơ đồ ra ngoài khung; mobile mặc định 100% từ góc đầu sơ đồ, Vừa khung vẫn cho xem tổng thể. Breakpoint 620/850/1090 không tràn trang. Escape đóng công cụ/drawer và trả focus. Ảnh `target/workspace-desktop.jpg`, `workspace-price-inline.jpg`, `workspace-mobile.jpg`, `workspace-600-desktop.jpg` là dữ liệu test, không commit.
- Kiểm tra Node VM với DOM/fetch giả lập qua (`target/workspace-js-behavior.log`): phản hồi ban đầu trả chậm khi mở thẳng Giá không làm mất sơ đồ; phản hồi cũ bị bỏ; mất/phản hồi lưu không đầy đủ không tự gửi lại, giữ VND; chống gửi lặp và khóa input khi ghi; lỗi đọc sau khi lưu thành công không biến thành thông báo lưu thất bại. Đây là mô phỏng, không tính thêm vào 68 tests và không thay kiểm tra mọi dạng mất mạng thực tế. Toàn bộ JS qua `node --check`, `git diff --check` qua.
- **Không reset cineprime.** Mốc đầu và sau khi tiếp tục khác ở users/audit_logs: nhật ký ghi SAVE_users do Admin hiện hữu lúc 15:41; đã giữ cập nhật Staff này, không hoàn tác checksum cũ. 28 bảng còn lại khớp. Từ mốc tiếp tục đến sau chạy JAR mới, checksum đủ **30/30 bảng khớp** (`target/workspace-app-resumed-before.json`, `workspace-app-final.json`); ghi chú `workspace-app-resume-note.json`. Giữ 1 phim, 1 rạp, 3 phòng, 180 ghế, 6 khung giá và giao dịch hiện hữu.
- Bản mới chạy loopback **http://127.0.0.1:8080/**, seed=false, tác vụ nền bật; preview 8081 đã dừng. Trang chủ/phim/JS mới HTTP 200 và UTF-8 đúng; API giá không đăng nhập HTTP 401. Không đăng nhập tài khoản thật do không có mật khẩu, không đổi mật khẩu/phân công. Camera thiết bị thật, MoMo/SMTP/hosting và mọi kiểu mạng lỗi thực tiếp tục ngoài nghiệm thu.

### Liên kết phim/phòng/ghế/giá — được duyệt 07/11/2026

Phạm vi: phim chọn nhiều thể loại ngay trong form, bắt buộc thể loại hoạt động khi hiển thị; User tự áp dụng bộ lọc. Luồng rạp → phòng → sơ đồ xem trước theo hàng → giá chung của rạp → suất; giữ bước đã lưu và ngữ cảnh. Ghế/Bảo trì có sửa ghế/cặp nguyên tử, chỉ xóa vật lý ghế chưa được tham chiếu; xem inventory theo suất và hủy toàn bộ đơn chờ để giải phóng. Danh mục đọc lại khi quay về tab/đổi ngữ cảnh, không ghi đè nội dung nhập. Giá giữ rạp × loại ghế × ngày × khung giờ, 24:00 và nguyên VND; combo/voucher toàn hệ thống. Giữ chặn khóa sơ đồ hiện tại (kể cả OPEN đã qua), snapshot, TTL và callback. Không reset dữ liệu ứng dụng, không thêm thanh toán/deploy.

Tham khảo công khai ngày 07/11/2026: https://www.galaxycine.vn/lich-chieu/ — các bước Chọn phim/rạp/suất → ghế → thức ăn → thanh toán công khai; không khảo sát sau đăng nhập. Admin/Staff từ nghiệp vụ CinePrime.

- [x] Form phim/thể loại và lọc database theo EXISTS, metadata nhóm, kiểm tra ngừng thể loại cuối cùng.
- [x] Ngữ cảnh form, các bước thiết lập phòng/ghế/giá/suất, readonly readiness theo ngày giờ thực.
- [x] Sơ đồ hàng/ghế/cặp, trạng thái theo suất, giải phóng qua hủy đơn chờ; chặn lịch sử/khóa, phân trang 5 dòng.
- [x] Regression MySQL riêng không bỏ qua, JAR/Pug UTF-8, desktop/mobile/breakpoint, ảnh nghiệm thu.
- [x] Đối chiếu dữ liệu ứng dụng giữ nguyên, cập nhật README và chạy bản local mới.

Kết quả xác minh đợt liên kết luồng ngày 07/11/2026:

- Lượt cuối `target/linked-flow-acceptance.log`: **63 tests, 0 failures/errors/skipped** (56 tích hợp MySQL/HTTP và 7 unit/HMAC), BUILD SUCCESS lúc 12:49:59. Lần gọi Maven `test package` thực hiện đủ bộ một lần; Surefire bỏ lượt gọi lặp của cùng cấu hình ở pha package, không có test case bị bỏ qua. Kiểm tra cú pháp toàn bộ JavaScript và `git diff --check` qua.
- Ca mới bao phủ phim nhiều thể loại/legacy payload/lỗi rollback/thể loại ngừng; cạnh tranh gán và ngừng thể loại; query nhóm không tăng theo số phim; luồng HTTP đúng rạp; sơ đồ hàng/cặp/ghế lẻ/tạo đồng thời/phiên bản cũ/ghế đã được tham chiếu; giá đúng loại ngày/giờ, 24:00 và VND lẻ; quyền Admin/CSRF; hủy toàn bộ đơn trả ghế/voucher, chặn đơn PAID, đơn quá TTL là EXPIRED và callback sau hủy đi đối soát. Lịch ngày và danh sách suất giữ rạp/phòng/phim/ngày qua liên kết; lọc phòng tại database trước phân trang. Giữ các test tiền, snapshot, callback trùng/muộn và Staff check-in của bộ cũ.
- GUI từ executable JAR trên **cineprime_test**: tạo rạp → phòng → 36 ghế (Standard theo mặc định, hàng B VIP, hàng F ba cặp Couple); cấu hình ghế lẻ bị chặn. Chỉnh ngừng F1/F2 cùng cặp rồi mở lại đúng 34 ghế hoạt động. Thử nghiệm phát hiện va chạm mã cặp khi chỉnh cặp cũ; đã sửa giữ mã cũ/UUID cho cặp mới và xác minh lưu lại đúng.
- Tạo thể loại ở tab khác khi form phim đang nhập: Làm mới giữ tên và lựa chọn cũ, hiện danh mục mới; lưu phim hai thể loại, User đổi dropdown tự lọc ra đúng một phim. Form giá giữ rạp/phòng sau làm mới, không tự điền tiền. Thêm giá cuối tuần 00:00–24:00; preview ngày thường báo thiếu, ngày cuối tuần đủ điều kiện. Tạo suất đúng phim/rạp/phòng.
- User chọn F3 tự chọn cả F3/F4, tổng **190.002đ** từ giá 95.001đ/ghế vật lý. Đơn đang chờ chưa phát vé; Admin mở đơn từ sơ đồ theo suất, hủy toàn bộ đơn trả cả hai ghế; User đọc lại thấy Đã hủy. Không gọi MoMo hoặc tạo thanh toán thật.
- Desktop **1366×768**, mobile **390×844**, breakpoint **620/850/1090**: các màn sơ đồ mới không tràn ngang trang, nội dung sơ đồ có vùng cuộn riêng; mobile ghế đạt 44×44px. Chú giải loại/trạng thái tách riêng, aria-label đọc tên/loại/trạng thái/giá; Escape đóng drawer và trả focus về nút mở. Phòng có suất vẫn khóa sơ đồ, nêu tên suất gây khóa; không mở rộng quyền bảo trì. Ảnh trong `target/linked-flow-*.jpg`, gồm genres, user-filter, user-seats, user-mobile, admin-mobile và inventory-final (dữ liệu giả, không commit).
- Không kiểm tra lại camera thiết bị thật, mọi độ trễ mạng hoặc cấu hình dịch vụ ngoài trong đợt này. Cơ chế abort/loại phản hồi cũ và phiên bản sơ đồ được triển khai; concurrency và phiên bản cũ đã kiểm tra tại backend. Phần media/trailer có kết quả riêng của đợt trước bên dưới.
- Dữ liệu ứng dụng **không reset**: checksum EXTENDED đủ 30 bảng khớp trước/sau toàn bộ kiểm thử và sau khởi động JAR (`target/linked-flow-app-before.json`, `target/linked-flow-app-after.json`). Giữ 1 phim, 1 rạp, 1 phòng, 60 ghế, 0 giá do người dùng nhập; không gán thể loại hoặc tạo giá thay người dùng. JAR mới chạy loopback **http://127.0.0.1:8080/**, seed=false, tác vụ nền bật; HTTP trang chủ/phim/JS mới trả 200 và tên phim tiếng Việt đúng. Preview 8081 đã dừng. Mật khẩu tài khoản thật không có trong cấu hình nên không đăng nhập thay; đăng nhập Admin/User đã kiểm tra trên schema test. README đã cập nhật luồng, số test, giới hạn và phân biệt reset cũ với dữ liệu hiện tại.

### Sửa phim/lịch và dọn dữ liệu local — được duyệt 06/11/2026

Phạm vi: form phim riêng với upload tự động JPG/PNG/WEBP, preview và giữ dữ liệu lỗi; YouTube watch/share/shorts/embed chuẩn hóa, banner phim thật và player chỉ tải khi mở; Ẩn/Hiện có dialog và kết quả, xóa vĩnh viễn chỉ phim chưa dùng; form suất theo rạp/phòng/ngày/giờ và preview xung đột toàn phạm vi, giữ đệm 15 phút; làm rõ đơn chưa thu và thu gọn dashboard. Giữ số tiền snapshot, TTL, callback và check-in. Không thêm MoMo/cash/hosting.

Quyết định đã chốt: ẩn chặn đơn mới nhưng đơn giữ trước đó còn hạn được hoàn tất, vé cũ/check-in và lịch sử giữ nguyên. Có Xóa phim chỉ khi chưa có suất/yêu thích/bình luận/nhắc phim. Ảnh từ máy, không nhận URL ảnh ngoài; hero dùng ảnh + nút Xem trailer. Giữ stack và giới hạn 5 dòng, mục tiêu chạm 44px. Dùng TwelveMonkeys ImageIO WebP 3.15.0; không thêm framework frontend hoặc đổi schema.

Tham khảo công khai ngày 06/11/2026: https://www.galaxycine.vn/lich-chieu/ (chọn vị trí/phim/suất, không khảo sát sau đăng nhập); https://developers.google.com/youtube/player_parameters (URL nhúng); https://github.com/haraldk/TwelveMonkeys (đọc WebP/SPI). Admin/Staff theo nghiệp vụ CinePrime.

- [x] Backend media/type/YouTube, public visibility, khóa ẩn–giữ ghế và bảo vệ endsAt.
- [x] Form phim/suất, dialog/feedback, banner/trailer, dashboard gọn và nhãn chưa thu tiền.
- [x] MySQL regression, JAR desktop/mobile và ảnh nghiệm thu thực tế; phạm vi xác minh ghi bên dưới.
- [x] Dừng local, backup DB + uploads ngoài repo, khôi phục kiểm chứng, transaction DELETE đúng FK trên cineprime. Giữ accounts/password/role/avatar/combo/voucher/genre/people/content; bỏ phân công rạp Staff, dọn điểm/lượt voucher/thông báo giao dịch, giữ audit và ghi sự kiện reset. Không tắt FK, không reset ID/Flyway; seed tắt, session cũ bỏ.
- [x] Bàn giao dữ liệu phim/rạp/đơn/vé bằng 0, Staff chưa phân công có hướng dẫn, app mới chạy local và README cập nhật đúng kết quả.

Kết quả nghiệm thu ngày 07/11/2026:

- Đầy đủ **54 tests, 0 failures/errors/skipped**: 47 tích hợp MySQL/HTTP và 7 unit/HMAC, log `target/movie-fix-full.log`. Hai ca phim/banner và lưu suất chạy lại sau sửa điều hướng đều qua (`target/movie-fix-final-targeted.log`). JAR cuối đóng gói thành công; JavaScript qua `node --check`. Giữ các ca tài chính, callback trùng/muộn, voucher, concurrency giữ ghế/check-in và query regression của bộ cũ.
- Kiểm tra hành vi JavaScript trực tiếp từ mã nguồn bằng Node VM/DOM và fetch giả lập: form khác không kích hoạt editor suất; upload đang chờ khóa Lưu; phản hồi upload cũ không ghi đè lựa chọn mới; thay ảnh lỗi khóa Lưu; bỏ lựa chọn khôi phục ảnh gốc (`target/movie-fix-js-behavior.log`). Đây là kiểm tra mô phỏng, không tính thêm vào 54 tests Maven và không thay kiểm tra mạng trên thiết bị thật.
- Ca mới kiểm tra JPG/PNG/WEBP, tồn tại file và khởi tạo lại MediaService, chuẩn hóa/chặn URL YouTube giả; banner nổi bật không giới hạn ba phim; ẩn đồng thời giữ ghế và tiếp tục đơn/vé cũ; xóa phim chưa dùng, chặn quan hệ khách; xung đột ngoài trang đầu/qua ngày/đệm 14:59 và 15:00; hai Admin thêm trùng; giữ endsAt đã có giao dịch; Staff chưa phân công và chuỗi tạo rạp → gán Staff → ghế/giá → phim/suất → đơn miễn phí trên schema test.
- Trình duyệt chạy executable JAR/schema test: upload tự động WEBP poster/PNG banner, lưu/mở lại đúng đường dẫn; JAR khởi động lại đọc được file runtime. File sai định dạng khóa Lưu; Bỏ lựa chọn mới giữ ảnh cũ. Dialog Ẩn có Hủy/Xác nhận, Ẩn/Hiện có thông báo; xóa thật được kiểm tra ở backend, dialog xóa trên trình duyệt chỉ mở và hủy.
- Hero hiện đúng một slide, chuyển banner và nút trailer hoạt động. YouTube player tải được thông tin/điều khiển từ video thực; Escape gỡ player và trả focus. Chưa xác nhận phát liên tục/âm thanh cho mọi video; video bị chủ sở hữu chặn nhúng có liên kết mở YouTube.
- Form suất chọn rạp/phòng, hiện xung đột nằm ngoài năm dòng thông thường; Lưu bị chặn giữ nguyên dữ liệu. Lưu thành công quay lại đúng rạp/ngày/phim/phòng vừa lưu. Dashboard ba đơn fixture cao 263px ở 1366×768; không tràn ngang ở 390×844, 620/850/1090 trong màn đã xem. Ảnh QA: `target/movie-fix-*.jpg`, không commit. Chưa mô phỏng đầy đủ mọi độ trễ/mất mạng của upload trong trình duyệt; mã có abort và loại phản hồi cũ.
- Reset thực hiện lúc **01:01 ngày 07/11/2026**, sau dừng ứng dụng: backup **`D:\CinePrime_Backups\20261107_010044`**, gồm dump toàn DB, hai file uploads, SHA-256 và `verification.json`. Khôi phục vào schema biệt lập hiện có `cineprime_test`, khớp số dòng và CHECKSUM EXTENDED cả **30 bảng**; đã dọn bản sao khỏi schema test sau xác minh. Cấu hình quyền chỉ cho hai schema này, không tạo schema thứ ba.
- Trước reset: 9 phim, 2 rạp, 124 suất, 3 đơn, 1 vé. Sau transaction DELETE và khởi động lại: phim/rạp/phòng/ghế/giá/suất/đơn/thanh toán/vé cùng các quan hệ giao dịch bằng 0; tổng PAID 0đ. FK luôn bật trong transaction reset ứng dụng, không reset AUTO_INCREMENT/Flyway. Giữ 4 tài khoản, 3 combo/sản phẩm, 2 voucher, 4 thể loại, 2 người tham gia, 9 nội dung; đối chiếu nguyên mật khẩu/role/profile, checksum danh mục giữ lại; audit cũ giữ và thêm RESET_LOCAL_DATA. Uploads không xóa.
- Bản JAR cuối chạy tại **http://127.0.0.1:8080/** với seed=false; trang chủ/phim trống đã xem trong trình duyệt, không sinh lại demo. Không có mật khẩu đăng nhập tài khoản thật trong cấu hình hiện tại nên không xác nhận đăng nhập Admin/Staff sau reset; màn Staff chưa phân công đã qua HTTP trên schema test. Người dùng dùng mật khẩu hiện có và gán Staff lại sau khi tạo rạp. MoMo/SMTP thực, camera vật lý và hosting tiếp tục chưa nghiệm thu.

### Thu gọn Admin — phê duyệt ngày 06/11/2026 (đợt bổ sung)

Phạm vi được duyệt: toàn bộ danh sách Admin 5 bản ghi/trang với Trước/Sau; sidebar nhớ nhóm/vị trí cuộn riêng theo tài khoản; lịch/suất theo rạp → ngày → phim; phòng/ghế/giá có một rạp đang chọn; ba tab User/Staff/Admin. Giữ Ngày/Tuần/Tháng/Năm, nhận diện CinePrime, định nghĩa tiền và mọi chặn nghiệp vụ hiện tại. **MoMo chuyển sang đợt sau; đợt này không thêm trả tại quầy hoặc quyền thu tiền.** Các hình tham khảo chỉ định hướng nút/badge/trạng thái, không phải yêu cầu cài skill, thêm bình luận/giao vận/email.

Tham khảo công khai ngày 06/11/2026: https://www.galaxycine.vn/lich-chieu/ — phần chọn vị trí, phim, suất và các bước đặt vé công khai. Không khảo sát vùng yêu cầu đăng nhập; Admin/Staff thiết kế từ nghiệp vụ CinePrime.

- [x] Phân trang cố định 5 tại database cho danh sách quản lý, lịch, đơn, nhật ký, lượt voucher; bộ lọc trước phân trang, giữ ngữ cảnh Lưu/Hủy. Sơ đồ ghế đầy đủ và các lựa chọn trong dropdown không chịu giới hạn 5 dòng.
- [x] Sidebar 6 ID ổn định, localStorage `cineprime.admin.nav.v2.<adminId>`, khôi phục nhóm/vị trí cuộn và mở thêm nhóm trang đích; drawer tiếp tục hỗ trợ bàn phím/Escape/focus. CSS thu gọn riêng `.admin-compact`.
- [x] Phân trang độc lập các khối vận hành bằng cursor ký HMAC, mốc thời gian và giới hạn ID; mỗi trang tối đa 5. Đơn mới join tên khách; khối đang xem trang sau giữ dòng cũ khi polling và báo Có cập nhật mới. Làm mới từng khối về trang đầu; tab ẩn/phiên hết hạn dừng polling.
- [x] API `/api/admin/reports/{snapshotId}/{section}` đọc chart/topMovies/occupancy của cùng snapshot trong session, không tạo báo cáo mới; nhóm phim giữ đầy đủ và chia 5. KPI/biểu đồ/CSV vẫn tính toàn kỳ. Bảng dữ liệu biểu đồ 5 mốc/trang; biểu đồ giữ toàn kỳ và có trạng thái 0 ngắn.
- [x] Lịch/suất theo rạp/ngày/phim; thời gian gọn từ startsAt/endsAt đã lưu, ghi +1 ngày. Rạp ghi trong URL, nhớ trong session theo Admin; mặc định rạp hoạt động đầu tiên theo tên. Ghế yêu cầu chọn phòng; giá lọc loại ghế/ngày, giữ 24:00 và giá mỗi ghế vật lý; ba tab tài khoản dùng role hiện có.
- [x] Nghiệm thu bổ sung: 44 tests (40 MySQL/HTTP + 4 unit/HMAC), 0 lỗi/thất bại/bỏ qua; 4 ca liên quan chạy lại sau chỉnh breadcrumb/bộ lọc cũng qua. Executable JAR/Pug UTF-8; desktop 1366×768, mobile 390×844 và breakpoint 620/850/1090; ảnh QA trong target/admin-compact-*.jpg. Log đầy đủ target/admin-compact-tests.log; lượt cuối target/admin-compact-final-targeted.log.

Kết quả nghiệm thu đợt bổ sung:

- Chuyển trang tài chính giữ nguyên mã báo cáo/mốc chốt/KPI/CSV; đủ các nhóm phim và trang sau. Mọi widget đã quan sát đều tối đa 5 dòng. Biểu đồ 0 không có thanh giả; card ít dữ liệu có chiều cao riêng (185px và 269px trong trường hợp rỗng đã thử).
- Trình duyệt JAR: đơn mới chen vào khi đang đọc trang 2 giữ nguyên năm dòng và focus, có thông báo cập nhật; làm mới khối về đầu, trang cuối vô hiệu hóa Sau. Ba trang tài chính vẫn độc lập với trang vận hành.
- Sidebar giữ bốn/năm nhóm mở cùng vị trí cuộn qua Phòng → Phim → Sửa → Danh sách; Admin thứ hai chỉ có nhóm mặc định, không dùng tùy chọn của Admin thứ nhất. Mobile drawer/Escape trả focus đúng nút mở.
- Lịch lọc phim trước phân trang, đổi rạp xóa phim không phù hợp. Phòng hiện tên rạp rõ; ghế không tải danh sách trước khi chọn phòng; bảng giá hiển thị 00:00–24:00, 95.000đ/ghế đôi vật lý và 190.000đ/cặp trong fixture. Ba tab tài khoản lọc đúng role; HTTP từ chối hạ quyền/khóa Admin cuối cùng, dữ liệu và phiên Admin vẫn hợp lệ sau khi từ chối (target/admin-compact-last-admin.log).
- Desktop 1366×768: năm phim và footer phân trang nằm trọn viewport (đáy footer 736px). Mobile 390×844, 620/850/1090 không tràn ngang trang trong các màn đã xem. Staff vẫn có luồng chọn suất/thống kê, không nạp admin.js; Customer không nạp operations.css. Không kiểm tra camera vật lý trong đợt này.
- JavaScript qua node --check. Preview 8081/schema cineprime_test đã dừng; không reset database ứng dụng hoặc dừng 8080. Cần khởi động lại CinePrime Local để dùng JAR/template mới. MoMo, thu tiền tại quầy, hosting và các dịch vụ ngoài tiếp tục hoãn.

Giới hạn vận hành: cursor sống tối đa 15 phút và chỉ dùng đúng khối/rạp; hết hạn cần bấm Làm mới khối. Đây là ảnh chụp phạm vi đọc để hạn chế dịch trang khi có bản ghi mới, không khóa việc thay đổi trạng thái nghiệp vụ của bản ghi. Snapshot tài chính vẫn tối đa hai bản/15 phút, transaction đọc REPEATABLE_READ; transaction ghi không đổi. Không đổi schema nghiệp vụ, engine hoặc frontend framework.

### UX/UI điều hành — phê duyệt ngày 06/11/2026

Người dùng yêu cầu triển khai toàn bộ theo từng đợt: layout riêng; dashboard cân bằng tài chính/vận hành, mặc định Hôm nay; lọc rạp; so sánh cùng tiến độ kỳ trước; CSV doanh thu riêng; Staff dùng PC/mobile, chủ động chọn suất và xác nhận từng vé; quét tiếp bằng nút; form chia nhóm/giữ dữ liệu lỗi/upload tại trường.

- [x] Tách service báo cáo chỉ đọc REPEATABLE_READ, giữ READ COMMITTED cho transaction ghi. Doanh thu từ snapshot đơn PAID theo paidAt; giữ nguyên nghiệp vụ thanh toán/giữ ghế.
- [x] Layout vận hành riêng, sidebar nhóm/active/drawer; dashboard 3 KPI, SVG theo giờ/ngày/tháng, khối vận hành 30s và bảng có phân trang.
- [x] Snapshot tài chính trong session, tối đa 2 bản/15 phút; CSV một dòng/đơn dùng đúng bản chụp; file nhật ký giao dịch cũ giữ ngữ nghĩa và nhãn riêng.
- [x] Staff: chọn suất, kết quả có lý do từ backend, xác nhận từng vé, giữ camera/khóa nhận QR khi xem kết quả, nút Quét vé tiếp; thống kê/bảng 5s, xử lý kết quả chưa rõ và phiên hết hạn.
- [x] Bảng quản lý có metadata Việt hóa, bộ lọc/phân trang, tên tham chiếu; form nhóm, giờ HH:mm/24:00, VND nguyên đồng, upload/preview, cảnh báo chưa lưu và khóa có lý do.
- [x] Nghiệm thu local: 38 tests trên MySQL, executable JAR/Pug UTF-8, desktop/mobile và cập nhật README theo kết quả thực tế. Camera thiết bị thật vẫn chưa kiểm chứng.

Định nghĩa bổ sung: lấp đầy theo ngày suất dùng vé VALID + USED / ghế hoạt động, loại VOID; không thay doanh thu đơn nếu trạng thái vé thay đổi. Tổng khách/phim là danh mục hiện tại toàn hệ thống. Kỳ đang diễn ra so cùng tiến độ kỳ trước (chặn tháng ngắn/năm nhuận), kỳ hoàn tất so toàn kỳ; mẫu số 0/kỳ tương lai không có phần trăm. Phạm vi tài chính là [đầu kỳ, mốc chốt), cùng mốc server trong một bản chụp. Vận hành hiện tại có nhãn riêng và không thay snapshot tiền. Không thêm hoàn tiền, phân bổ tiền vé/combo hoặc nút đóng đối soát.

Kết quả ngày 06/11/2026:
- Build executable JAR thành công; 38 tests (34 MySQL/HTTP, 4 unit/HMAC), 0 failures/errors/skipped. Bộ kiểm tra tranh ghế/voucher/check-in, callback/late success, PDF tiếng Việt/QR và số query trước đây vẫn qua.
- Đối chiếu doanh thu với biểu đồ, nhóm phim và CSV; kiểm tra đơn miễn phí/combo/giảm giá/thử lại/đối soát, midnight Việt Nam, mốc chốt, rạp đổi tên/ngừng; snapshot cũ không đổi khi có thanh toán mới, không tải được giữa hai session, hết hạn đúng 15 phút và bị thay khi mở báo cáo thứ ba.
- Staff trên JAR/schema test: mã không tồn tại có hướng dẫn; Enter chỉ tra cứu; xác nhận ghế thủ công cập nhật số đã/chưa vào và tỷ lệ, giữ kết quả không reload; tra lại vé USED không có nút xác nhận. Biên giờ, khác rạp, vô hiệu, concurrency và khóa phiên được kiểm tra bằng MySQL/HTTP.
- Desktop 1366×768/mobile 390×844 và 620/850/1090: kiểm tra header 60px, drawer/Escape/focus, không tràn ngang trang; form giá hiển thị VND/cả cặp, lỗi giữ giá/giờ đã nhập. JavaScript qua node --check; git diff --check qua. Ảnh QA nằm trong target, không commit.
- Preview test cổng 8081 đã dừng; không sửa dữ liệu ứng dụng ở cổng 8080. Người dùng khởi động lại CinePrime Local trong IntelliJ để dùng mã/giao diện mới. Không thay Flyway schema, engine, thư viện frontend hoặc dịch vụ ngoài.

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

### Thêm suất chiếu hàng loạt — được duyệt 08/11/2026

Mốc này ghi lại bản đầu và bằng chứng kiểm chứng của bản đó. Luồng thêm mới hiện tại được thay bằng lịch tuần ở mục kế tiếp; các mô tả một phim/chọn cách nhập/lịch ngày tại đây là lịch sử.

Phạm vi: nhập tay một phim/một rạp qua lịch ngày mọi phòng, thêm/sửa/xóa danh sách chờ và sao chép sang các ngày đích; Excel một rạp/nhiều phim, mỗi dòng một suất, mẫu trống và xuất lịch cũ để chỉnh rồi tạo mới. Hai cách dùng chung kiểm tra/xác nhận, mặc định Nháp, tùy chọn Mở bán. Chỉ tạo suất tương lai; mọi lỗi chặn cả đợt. Chưa thêm sửa/hủy/mở bán lịch cũ hàng loạt hoặc lưu nháp tự động.

Tham khảo công khai ngày 08/11/2026: thử https://www.galaxycine.vn/lich-chieu/ bị redirect loop, không có quan sát mới hoặc phần sau đăng nhập. Admin thiết kế từ nghiệp vụ CinePrime và design tokens UI-Template.md.

- [x] Mã nguồn lịch phòng gồm Nháp/Mở bán/vệ sinh/khoảng trống/suất đang xếp; tính cả qua nửa đêm. Desktop lịch cuộn riêng, công cụ bên cạnh; mobile danh sách khoảng trống từng phòng, thao tác 44px.
- [x] Mẫu .xlsx ba sheet Hướng dẫn/Lịch chiếu/Danh mục, dropdown mã kèm tên, ngày/giờ Excel hoặc văn bản, VND nguyên; sửa/xóa dòng lỗi trên web, giữ số dòng Excel. Xuất toàn bộ rạp/khoảng ngày, không phụ thuộc phân trang.
- [x] Giới hạn 500 suất, file 5 MB và tổng XML giải nén 32 MB/100 mục ZIP, xuất 31 ngày; không tính công thức/liên kết ngoài, không lưu file vào source/JAR. Apache POI poi-ooxml 6.0.0, nguồn https://poi.apache.org/download.html.
- [x] Dùng chung quy tắc lịch/mở bán với tạo đơn lẻ; khóa phòng theo ID tăng dần trước Movie/Cinema, chuẩn hóa chuyển phòng suất/ghế. Ghi suất/inventory/audit/biên nhận cùng transaction; inventory INSERT SELECT theo suất thay vòng lặp từng ghế.
- [x] Flyway V3 chỉ thêm bảng showtime_batch_results, unique(actor_id,request_key); kiểm tra fingerprint và lịch lại dưới khóa, phát lại cùng key trả cùng kết quả. Không tự gửi lại khi chưa rõ kết quả; tra cứu hoặc gửi lại nguyên yêu cầu/cùng key. Bản ghi chưa xuất hiện chưa chứng minh giao dịch đã thất bại.
- [x] Hồi quy MySQL, JAR/Pug UTF-8, kiểm tra desktop/mobile và đối chiếu dữ liệu ứng dụng; kết quả và giới hạn kiểm chứng ghi bên dưới.

Kết quả kiểm chứng ngày 08/11/2026:

- Hồi quy đầy đủ: **92 tests, 0 failures/errors/skipped**, gồm **81 MySQL/HTTP và 11 unit/HMAC/render/Excel**. Log `target/batch-full-final.log`, BUILD SUCCESS 15:23:01. Lượt đầu `target/batch-full.log` có một lỗi ZXing NotFound ở ca QR cũ; chạy riêng QR/PDF rồi toàn bộ lần cuối đều qua, không sửa mã QR/PDF để che lỗi. Không cộng lượt chạy lại vào 92 tests. Sau khi siết nguồn/trạng thái đúng giá trị chuẩn, chạy lại 6 ca liên quan và đóng gói đều qua (`target/batch-final-package.log`); các lượt xác nhận không cộng dồn vào số test.
- Ca tải tối đa tạo **500 suất OPEN × 600 ghế = 300.000 Inventory**, preview + commit **14.485 ms** trên máy local (`target/batch-load.json`). Đây là đo trên schema test, không là cam kết tốc độ trên mọi máy. Test đồng thời lô/đơn lẻ chỉ một bên thắng; cùng request key trả cùng kết quả; lỗi CHECK khi ghi inventory của suất thứ hai rollback mọi suất/inventory/biên nhận. DRAFT thiếu giá cảnh báo, OPEN chặn; có kiểm tra tràn số tiền.
- HTTP từ executable JAR trên cineprime_test: Pug tiếng Việt đúng; xuất lịch 4 dòng, import nguyên báo 4 trùng, đổi ngày tạo đủ 4 mới, replay cùng key trả đúng ID cũ và tra kết quả được (`target/batch-jar-verification.json`). JAR cuối xác nhận lại Pug UTF-8, JS/CSS phục vụ khớp SHA-256 với source, preview hợp lệ, nguồn nhập sai trả 422 và biên nhận chưa có trả 202 (`target/batch-final-jar-verification.json`). File xuất có đúng ba sheet tiếng Việt, hai dropdown và các ô ngày/giờ/VND kiểu số (`target/batch-excel-verification.json`, `target/batch-export.xlsx`). Công thức, sai rạp, sai kiểu, file trên 5 MB và ZIP giải nén quá giới hạn có ca kiểm thử.
- GUI JAR: chọn phim/vùng trống → thêm suất → chuyển ngày giữ danh sách → sao chép hai ngày → thêm dòng trùng báo lỗi cả hai dòng, khóa Tạo → sửa giờ → xác nhận; Quay lại trong dialog giữ đủ 4 dòng, xác nhận tạo đủ 4 Nháp và có liên kết ba ngày kết quả. Desktop 1366×768 và mobile 390×844 đã xem; mobile dùng danh sách khoảng trống, nút đo 44px, không tràn ngang và thanh xác nhận trong viewport. Ảnh `target/batch-mobile.jpg`. Tinh chỉnh cuối thanh cuộn desktop/panel giữ trong viewport được đóng gói; chưa quan sát lại qua GUI sau tinh chỉnh vì công cụ bị dừng.
- Mô phỏng Node VM với DOM/fetch giả (`target/batch-js-behavior.cjs`, `target/batch-js-behavior.json`) qua ba trường hợp mất phản hồi sau ghi, biên nhận chưa có rồi gửi lại cùng nguyên yêu cầu, và JSON phản hồi bị cắt. Giữ dòng/khóa input, không tự retry; lỗi đọc lịch sau thành công không biến thành báo lưu thất bại. Không tính mô phỏng vào 92 tests hoặc coi là kiểm tra mất mạng trên trình duyệt thật.
- Checksum **30/30 bảng cineprime khớp** trước/sau, gồm Flyway (`target/batch-app-before.txt`, `target/batch-app-after.txt`, `target/batch-data-verification.json`). Không reset ứng dụng, không đổi mật khẩu, không gán danh mục/giá và không tạo suất thử trong dữ liệu thật. Flyway V3 đã chạy trên cineprime_test; ứng dụng đang chạy ở 8080 được giữ nguyên, cần restart Run cấu hình local để nạp mã mới và tự áp dụng V3. JAR xem thử 8081 đã dừng.
- [ ] Chưa nghiệm thu đủ thao tác chọn/tải file bằng GUI, mất mạng trình duyệt thật, 1920×1080 và toàn bộ breakpoint. Tự động chọn file Chrome thiếu quyền file URL; công cụ Windows sau đó dừng do không xác định được URL đủ chắc chắn để thực thi policy. Không thay quyền extension để vượt chặn. Vòng xuất/import đã kiểm qua HTTP/JAR và MySQL như trên; người dùng có thể thử chọn file bình thường trong trình duyệt.

### Thống nhất thêm suất bằng lịch tuần — được duyệt 08/11/2026

Thay luồng thêm mới ở mốc trước bằng một màn `/admin/showtimes/new`. Tick và Excel cùng bổ sung vào một đợt nhiều phim tại một rạp. Desktop bảy cột Thứ Hai–Chủ nhật, giờ dọc 00:00–24:00, một phòng đang xem; mobile một ngày. Giữ Spring MVC/Pug4J/CSS Grid/JavaScript thuần/MySQL và design tokens; không thêm migration hoặc thư viện frontend ở mốc này.

Tham khảo công khai lại ngày 08/11/2026: `https://www.galaxycine.vn/lich-chieu/` tiếp tục lỗi chuyển hướng; không khảo sát được nội dung mới hoặc phần Admin sau đăng nhập. Lịch tuần thiết kế theo nghiệp vụ CinePrime và hướng tương tác người dùng yêu cầu, không tuyên bố sao chép giao diện Galaxy.

- [x] Mọi điểm thêm tại danh sách/lịch vận hành/thiết lập phòng về màn chung, giữ rạp/phòng/phim/ngày. URL batch cũ chuyển hướng và giữ requestKey. GET editor không ID chuyển hướng; POST và service tạo đơn cũ không ghi dữ liệu. Editor có ID tiếp tục sửa và giữ bảo vệ vé/giữ ghế.
- [x] Tick mốc 15 phút, bỏ tick, các khối phim/thời lượng riêng cùng vệ sinh; mọi phim Nháp/Mở bán chiếm phòng, Đã hủy không chiếm. Đổi phim/phòng/tuần giữ lựa chọn, đổi rạp có cảnh báo. Chỉnh nhanh mọi trường và giờ lẻ; sao chép ngày/phòng giữ dòng lỗi để sửa.
- [x] Excel bổ sung vào tick/file cũ; giữ tên file, dòng Excel, nguồn; không gộp trùng hoặc dịch giờ. Tổng trên 500/file không đọc được giữ nguyên đợt cũ. Panel toàn đợt, lọc lỗi, năm dòng/trang và điều hướng tới tuần/phòng dòng chọn.
- [x] API tuần `cinemaId/roomId/weekStart`, kiểm tra Thứ Hai/phòng thuộc rạp, adapter `date` một ngày. Một truy vấn sự kiện cho tuần, không cắt/phân trang; tính vệ sinh tràn từ tuần trước và suất đầu tuần sau trong phạm vi phim tối đa 600 phút cộng 15 phút vệ sinh.
- [x] Cho nhiều phim và `source=MIXED`, giữ cấu trúc dòng/hash/biên nhận cũ. Tick tức thì, preview debounce 300 ms, bỏ phản hồi phiên bản cũ; lỗi tải lịch không tạo ô rảnh giả. Nút tạo chỉ bật với preview mới hợp lệ. Tái sử dụng kiểm tra lại dưới khóa, rollback toàn bộ, inventory theo lô và chống gửi lặp.

Kết quả kiểm chứng lịch tuần ngày 08/11/2026:

- Hồi quy MySQL riêng đầy đủ: **96 tests, 0 failures/errors/skipped**, gồm **85 MySQL/HTTP và 11 unit/render/Excel/HMAC** (`target/week-full-final.log`, BUILD SUCCESS 19:30:59). Các ca mới gồm chuyển hướng/chặn tạo đơn, biên tuần/vệ sinh/lookahead, truy vấn không tăng theo số suất, nhiều phim/MIXED và cạnh tranh tạo đợt với sửa suất. Các test tạo đơn cũ được chuyển sang API batch, các ca sửa/bảo vệ giao dịch giữ nguyên. Lượt đầu `target/week-full.log` có hai kỳ vọng cũ về GET form tạo đơn và nhãn thêm trong thiết lập phòng; đã chuyển sang kiểm tra editor có ID và liên kết lịch tuần, rồi chạy lại đầy đủ đều qua. Không cộng các lượt chạy lại vào số ca.
- Lượt tải tối đa cuối tạo **500 suất OPEN × 600 ghế = 300.000 Inventory**; preview + commit **13.100 ms** trên máy local. Lượt trước trong cùng đợt đo 24.849 ms; đây là số đo local, không cam kết thời gian mọi máy. `target/batch-load.json` được test cập nhật theo lượt chạy mới nhất, các con số ở mục lịch sử là lượt đo trước đó. JavaScript cuối chạy kiểm tra cú pháp/mô phỏng rồi đóng gói lại (`target/week-final-package.log`), không chạy lặp bộ MySQL chỉ vì thay tài nguyên JS.
- GUI executable JAR trên `cineprime_test`: tick, đổi phim, đổi phòng/tuần giữ đợt; chỉnh 11:55; sao chép ngày gây xung đột được giữ lại; import `batch-export.xlsx` bổ sung bốn dòng vào ba suất đang chọn, hiển thị bảy dòng với nguồn/số dòng và báo trùng giữa tick/Excel. Sửa/bỏ lỗi còn sáu suất thuộc hai phim/bốn ngày; Quay lại dialog giữ đợt; xác nhận tạo đủ sáu Nháp và liên kết ngày kết quả. Không có lỗi console trong lượt này. Đây là dữ liệu giả trên schema test.
- GUI bố cục cuối: desktop 1366×768, 1920×1080; mobile 390×844; breakpoint 620×844, 850×768 và 1090×768 đã xem. Không tràn ngang trang; lịch cuộn riêng và có cuộn ngang nếu bảy cột không vừa vùng lịch. Mobile một cột, vùng tick đo 44px, nút sao chép và thanh tạo trong viewport. Đã thu gọn margin từ CSS nền, kiểm lại sau đóng gói. Ảnh `target/week-desktop.jpg`, `week-mobile.jpg`, `week-620.jpg`, `week-850.jpg`, `week-1090.jpg`, `week-1920.jpg`; số đo `target/week-responsive.json`. Đổi tuần giữ vị trí cuộn; mũi tên xuống chuyển đúng 08:00 → 08:15, Space tick thêm đúng suất.
- Dừng server preview rồi Làm mới: hiện lỗi tải lịch, **0 ô tick**, giữ **2 suất đang chọn**, Tạo bị khóa (`target/week-calendar-load-failure.jpg`). Mất phản hồi sau commit chỉ được mô phỏng, chưa nghiệm thu trên mạng thật.
- HTTP executable JAR cuối: Pug UTF-8 đúng, ba tài nguyên JS/CSS phục vụ khớp SHA-256 với source, lịch trả đúng bảy ngày, biên nhận chưa có trả 202 (`target/week-jar-verification.json`). Bản tuần dùng đúng các giới hạn file/Excel đã kiểm ở mốc trước.
- Mô phỏng JavaScript mới: `target/week-calendar-check.cjs` kiểm biên tuần/tháng/năm, giờ Việt Nam độc lập timezone trình duyệt, ngày/giờ sai không tự chuẩn hóa, khoảng vệ sinh, từng phim/phòng, cap và phiên bản preview. `target/week-ui-check.cjs`/`.json` chạy mã UI với DOM/fetch giả, qua tám trường hợp: mất phản hồi rồi tra biên nhận, biên nhận 202 rồi gửi lại nguyên key/payload, JSON bị cắt, preview đến sai thứ tự, import giữ dòng lỗi/cap giữ đợt, đổi tuần trong khi danh mục phòng/rạp tải chậm, phản hồi xác nhận 409 tải lại lịch/giữ lỗi và tick lấy thiết lập mới nhất nhưng giữ thiết lập suất cũ. Các tinh chỉnh bất đồng bộ/thiết lập cuối được kiểm qua mô phỏng, chưa lặp lại thao tác tương ứng bằng GUI. Không cộng mô phỏng vào số test Maven, không coi là thử mất mạng trình duyệt thật.
- Chưa nghiệm thu mất phản hồi commit trên mạng thật và trình đọc màn hình. Import chọn file đã thực hiện được bằng in-app browser; nút Tải mẫu đã bấm nhưng công cụ không nhận sự kiện download sau timeout 5 giây nên chưa xác nhận file tải về qua GUI. Không thay quyền extension. Mẫu/xuất vẫn có kiểm chứng API/JAR và ca HTTP riêng.
- Checksum **31/31 bảng cineprime khớp** trước/sau (`target/week-app-before.txt`, `week-app-after.txt`, `week-data-verification.json`). Server ứng dụng 8080 giữ nguyên; JAR preview 8081 do tác nhân tạo đã dừng. Restart Run local để nạp luồng lịch tuần mới; mốc này không thêm Flyway migration.

### Xuất lịch kèm form đăng ký suất mới — được duyệt 09/11/2026

Luồng: xuất lịch → điền form Excel → import bổ sung lên lịch tuần → kiểm tra → xác nhận tạo. Thay cấu trúc file xuất ở các mốc trước bằng mẫu phiên bản 2; giữ hỗ trợ đọc phiên bản 1. Chỉ thêm suất mới, không cập nhật lịch đã lưu qua Excel. Giữ Java 25/Pug4J/JavaScript thuần/MySQL và Apache POI 6.0.0, không thêm migration hoặc thư viện.

Tham khảo công khai ngày 09/11/2026: `https://www.galaxycine.vn/lich-chieu/` tiếp tục lỗi redirect loop. Không có quan sát mới hoặc khảo sát sau đăng nhập; phần Excel/Admin xây dựng theo nghiệp vụ đã chốt và design tokens CinePrime.

- [x] Bốn sheet Hướng dẫn/Lịch hiện tại/Đăng ký suất mới/Danh mục. Xuất mọi Nháp/Mở bán trong rạp/phạm vi chọn; sắp ngày → phòng → giờ. Lịch hiện tại có bảy cột đầu giống form, thêm kết thúc/hết vệ sinh có ngày và trạng thái; lọc, cố định tiêu đề, bảo vệ khỏi sửa nhầm nhưng cho chọn/copy.
- [x] Form 500 dòng trống có đường viền, định dạng ngày/giờ/VND, dropdown phim/phòng và mở tại A2. Tải mẫu trống có cùng cấu trúc, không có suất tham khảo. Hướng dẫn rõ chỉ sao chép bảy cột đầu rồi đổi ngày/giờ.
- [x] Reader v2 chỉ đọc Đăng ký suất mới, không dùng Lịch hiện tại để ghi database. Giữ dòng thiếu với số dòng Excel; bỏ dòng hoàn toàn trống. Không có dòng mới trả 422/EMPTY_REGISTRATION, thông báo tên sheet; giữ nguyên đợt đang chọn.
- [x] Giữ URL API và Input/Line; import-preview thêm `excel.templateVersion`, `excel.sheetName`, `excel.warning` bên cạnh input/preview. UI ghi nguồn file/sheet/dòng; v1 giữ quy tắc mọi dòng là suất mới và cảnh báo sau cả preview chung. Không tự suy đoán phiên bản, gộp trùng hoặc dịch giờ.
- [x] Giữ 5 MB, giải nén 32 MB/100 mục ZIP, không tính công thức/liên kết ngoài; một rạp/file, xuất tối đa 31 ngày/20.000 suất tham khảo. Chỉ tối đa 500 suất mới của cả đợt, không đếm lịch tham khảo. Session/CSRF/Admin, fingerprint/khóa/rollback/chống lặp dùng nghiệp vụ hiện có.

Kiểm chứng ngày 09/11/2026:

- Hồi quy đầy đủ và đóng gói executable JAR: **106 tests, 0 failures/errors/skipped**, gồm **87 MySQL/HTTP và 19 unit/render/Excel/HMAC** (`target/excel-v2-full-final.log`, BUILD SUCCESS 14:58:18). Không cộng lượt unit/focused hoặc mô phỏng vào 106 ca. Các ca cũ về ghế, voucher, callback, check-in, sửa suất, cạnh tranh/rollback/idempotency tiếp tục qua trên MySQL riêng.

- 10 unit Excel qua (`target/excel-v2-unit-retry.log`): cấu trúc bốn sheet/500 dòng, bảo vệ và copy/filter, 100 suất tham khảo chỉ đọc ba đăng ký, 600 suất tham khảo không tính vào cap, file chưa điền form, dòng thiếu/sai cột, v1/cảnh báo, thiếu sheet/tiêu đề và 501 dòng mới. Lượt chạy unit trong sandbox bị kẹt gắn Mockito agent; chạy ngoài sandbox qua, không cần thay thư viện.
- Năm ca MySQL/HTTP Excel chạy riêng đều qua (`target/excel-v2-focused-final.log`). Ca 100 suất cũ + ba đăng ký tạo đúng ba suất mới, không sửa giờ suất cũ; file chưa điền form trả 422 không ghi suất. Lượt đầu kỳ vọng sai inventory của Nháp (chỉ Mở bán tạo inventory), đã sửa assertion theo nghiệp vụ hiện có. Lượt kiểm tra song song bị Excel khóa file fixture; test đã bỏ ghi đè file QA và kiểm workbook trong bộ nhớ.
- Microsoft Excel thực tế: mở được file bốn sheet, mặc định vào form A2; xem lịch tham khảo, mở dropdown phim; chọn/copy bảy cột từ sheet được bảo vệ sang form, đổi ngày 29/12/2026 và giờ 11:55, Save As thành `target/excel-v2-excel-edited.xlsx`. File dữ liệu giả có 100 suất tham khảo và một dòng đăng ký; không chỉnh giấy phép/cấu hình bảo mật Excel. Đã đóng cửa sổ Excel do tác nhân mở.
- Mười mô phỏng UI/DOM/fetch qua (`target/excel-v2-ui-check.cjs`, `.json`): giữ các ca lịch tuần cũ và thêm nguồn v2, file trống/lỗi giữ draft/thông báo, nguồn và cảnh báo v1 vẫn hiện sau preview MIXED. Mô phỏng không cộng vào số test Maven hoặc thay nghiệm thu mạng thật.
- GUI executable JAR trên cineprime_test: tick một suất 27/12, import file đã Save As bằng Microsoft Excel chỉ thêm một suất 29/12 lúc 11:55; tổng hai suất. Import file 100 tham khảo chưa điền form báo EMPTY_REGISTRATION và giữ hai lựa chọn/nút Tạo. Import thêm file ba đăng ký thành năm dòng, báo đúng hai dòng trùng giữa hai file và khóa Tạo; lọc lỗi/bỏ dòng trùng còn bốn hợp lệ. Chọn dòng Excel đưa về đúng tuần/phòng, editor có nguồn file/sheet/dòng và giờ lẻ. Xác nhận tạo đủ bốn Nháp; SQL/biên nhận xác nhận database từ một suất cũ thành năm suất, không tạo lại 100 tham khảo (`target/excel-v2-test-created.txt`).
- Desktop 1366×768 và mobile 390×844 đã xem nguồn Excel/dòng lỗi; mobile không tràn ngang, panel cuộn và nút chạm sử dụng được. Ảnh `target/excel-v2-preview.jpg`, `excel-v2-mobile.jpg`, `excel-v2-created.jpg`. Nhập file v1 thật vẫn có bốn dòng, nguồn Lịch chiếu/dòng Excel và cảnh báo sau preview (`target/excel-v2-legacy-warning.jpg`); các dòng v1 thử này không được xác nhận tạo. Không có lỗi console trong lượt kiểm tra.
- HTTP JAR cuối: Pug UTF-8 đúng, ba JS/CSS khớp SHA-256 với source; file Save As bằng Excel đọc đúng **một** đăng ký 29/12/2026 11:55/version 2, file chưa điền trả 422/EMPTY_REGISTRATION; v1 trả version/sheet/warning đúng (`target/excel-v2-jar-verification.json`). Xuất lại từ JAR có bốn sheet, năm suất tham khảo đã lưu, 500 dòng form trống và hai dropdown (`target/excel-v2-export-verification.json`, `excel-v2-jar-export.xlsx`).
- Checksum **31/31 bảng cineprime khớp** trước/sau (`target/excel-v2-app-before.txt`, `excel-v2-app-after.txt`, `excel-v2-data-verification.json`). Chỉ tạo dữ liệu thử trong cineprime_test. Excel/tab kiểm thử đã đóng, JAR preview 8081 đã dừng; giữ nguyên ứng dụng 8080. Restart Run local để dùng mẫu mới.
- Giới hạn nghiệm thu: nút tải file bằng GUI chưa được xác nhận thành file tải về bởi công cụ browser; đã kiểm endpoint xuất/tải mẫu qua HTTP và mở/điền/lưu file bằng Microsoft Excel thực tế. Chưa thử lại mất phản hồi commit trên mạng thật hoặc trình đọc màn hình; cơ chế giao dịch/chống lặp có hồi quy MySQL và mô phỏng UI.

### Thiết kế lại bảng phim × tuần và Excel v3 — được duyệt 09/11/2026

Người dùng duyệt kế hoạch cuối và yêu cầu bắt đầu thực hiện, bám sát bản phác thảo đã chỉnh nút. Mục này thay bố cục giờ dọc/một phòng và quy tắc đợt nhiều tuần ở các mốc trước. Giữ stack, design tokens, dữ liệu ứng dụng, editor sửa suất và toàn bộ bảo vệ giao dịch. Không thêm migration/thư viện hoặc triển khai hosting.

Tham khảo công khai ngày 09/11/2026 trong đợt này: https://www.galaxycine.vn/lich-chieu/ đọc được vùng chọn vị trí/phim/suất và các bước ghế → thức ăn → thanh toán → xác nhận. Không khảo sát phần sau đăng nhập hoặc Admin/Staff; giao diện quản trị xây dựng từ nghiệp vụ và phác thảo người dùng. Các lần redirect lỗi ở mục lịch sử được giữ nguyên theo kết quả lúc đó.

- [x] API tuần hỗ trợ bỏ roomId để đọc toàn rạp; danh mục phim/poster/thời lượng tải theo lô, giữ sự kiện/vệ sinh biên tuần. Các đợt mới chỉ gồm ngày bắt đầu thuộc một tuần; phát lại biên nhận cũ trước quy tắc mới, giữ Input/Line/hash.
- [x] Bảng mỗi phim × bảy ngày, suất có giờ/phòng/trạng thái; phim đang/sắp chiếu chưa có lịch vẫn có hàng. Bộ lọc độc lập form; mọi ô có Thêm giờ, ô trống không được coi là phòng rảnh. Lịch có phân trang phim nhưng dữ liệu chiếm phòng luôn đầy đủ.
- [x] Form ngày × giờ, gợi ý giờ rảnh, chỉnh riêng ngày với khóa ổn định. Preview chung trước khi bổ sung cả nhóm; không bỏ bớt ngày hoặc tự dịch giờ. Suất đã chọn giữ thiết lập riêng; chỉnh/bỏ và sao chép trong tuần. Đổi tuần/rạp cảnh báo cả nhóm còn trong form; không tự lưu nháp.
- [x] Danh sách toàn đợt năm dòng/trang, lọc lỗi/nguồn Excel; lỗi ngoài bộ lọc vẫn chặn lưu. Trạng thái chung Nháp/Mở bán, xác nhận Lưu N suất mới; giữ kiểm tra lại dưới khóa, fingerprint, transaction và requestKey/tra kết quả chưa rõ.
- [x] Excel v3 bốn sheet, form 500 dòng với Phim/Phòng/bảy ngày/Ngôn ngữ/Định dạng/Phụ thu. Mỗi ô nhận giờ Excel hoặc nhiều giờ HH:mm ngăn bằng ; , hay xuống dòng. Giữ dòng thiếu/giờ sai/công thức như lỗi, nguồn file/sheet/dòng/ô/thứ tự giờ nằm ngoài payload ghi. Giới hạn 500 tính sau tách giờ; file sai rạp/tuần/giới hạn không bổ sung.
- [x] Giữ parser v1/v2 và API from/to tối đa 31 ngày/20.000 tham khảo; tuần trên UI xuất v3 qua weekStart. Tham khảo chỉ đọc, không tạo lại/cập nhật; 5 MB/32 MB/100 mục ZIP, không tính công thức hoặc liên kết ngoài.
- [x] Hồi quy đầy đủ MySQL riêng, tải 500 suất cùng tuần, kiểm tra JAR/Pug UTF-8, Excel thực tế và GUI desktop/mobile/breakpoint.
- [x] Đối chiếu checksum dữ liệu ứng dụng và ghi kết quả đã kiểm chứng/giới hạn công cụ tại đây.

Phạm vi không bổ sung kéo thả, tự tối ưu lịch, sửa/hủy suất đã lưu hàng loạt hoặc tự lưu nháp.

Kết quả kiểm chứng đợt thiết kế lại ngày 09/11/2026:

- Hồi quy đầy đủ **115 tests, 0 failures/errors/skipped**, gồm **91 MySQL/HTTP và 24 unit/render/Excel/HMAC** (`target/weekly-matrix-full.log`, BUILD SUCCESS 19:26:51). Bao gồm toàn phòng, phim chưa có lịch, một tuần/đợt, Chủ nhật kết thúc/vệ sinh sang thứ Hai, phát lại biên nhận nhiều tuần cũ, Excel v3 và các bảo vệ cạnh tranh/rollback/CSRF/tài khoản khóa hiện có. Điều chỉnh các fixture cũ để đợt mới thuộc một tuần; dữ liệu biên tuần được tạo qua hai đợt riêng.
- Sau chỉnh thông báo trùng cụ thể, thêm ca render Pug mới và file Excel QA, chạy lại **21 tests liên quan đều qua** (`target/weekly-matrix-package.log`: 3 MySQL/HTTP, 15 Excel, 3 render/form). Không cộng các lượt chạy lại vào 115. Các tinh chỉnh JS/CSS cuối được kiểm cú pháp, mô phỏng, GUI rồi đóng gói (`target/weekly-matrix-final-package.log`); không lặp toàn bộ MySQL chỉ vì thay tài nguyên.
- Tải tối đa **500 suất OPEN trong cùng tuần × 600 ghế = 300.000 inventory**, preview + commit **21.105 ms** trên máy local (`target/batch-load.json`). Đây là phép đo local, không cam kết mọi máy. Không dùng H2 để kết luận khóa giao dịch.
- Sáu mô phỏng UI/DOM/fetch qua (`target/weekly-matrix-ui-check.cjs`, `.json`): nhóm lỗi không bổ sung, giữ chỉnh riêng ngày khi thêm/bỏ giờ chung, bộ lọc độc lập, import/cap/sai tuần/lỗi tải giữ đợt, preview sai thứ tự, cảnh báo đổi tuần khi form còn giờ, sao chép toàn bộ hoặc không bổ sung, lỗi ngoài bộ lọc chặn lưu và mất phản hồi/202/gửi lại nguyên requestKey/payload. Mô phỏng không cộng vào số test Maven.
- GUI executable JAR trên **cineprime_test**: bảng ba phim/hai phòng, phim chưa có lịch vẫn hiển thị. Chọn 09:00 cho cả tuần báo đúng suất phim khác vào Thứ Tư; đổi riêng sang 11:55, thêm đủ bảy suất. Đổi bộ lọc phòng giữ bảy suất; đổi tuần rồi Quay lại giữ nguyên. Import file đã Save As bằng Excel bổ sung đúng ba suất, tổng mười; nguồn hiển thị C2, giờ thứ hai trong C2 và H2. Import file chưa điền giữ đủ mười và báo EMPTY_REGISTRATION. Xác nhận trên mobile tạo đủ **10 Nháp**, tra biên nhận đúng mười và xuất lại có **31 suất tham khảo** (21 trước + 10 mới).
- Microsoft Excel thực tế mở được v3 với bốn sheet, đúng ngày Thứ Hai–Chủ nhật, dropdown và ô dữ liệu không bị khóa. Đã nhập `15:30; 21:00` vào C2 và Save As `target/weekly-matrix-excel-edited.xlsx`; backend đọc đúng ba suất: 07/12 lúc 15:30 và 21:00, 12/12 lúc 15:30 (`target/weekly-matrix-excel-real-verification.json`). Không sửa giấy phép hoặc cài đặt bảo mật Excel; cửa sổ Excel do tác nhân mở đã đóng.
- Nút **Xuất lịch tuần** đã tải thành file thực tế qua GUI (`Downloads/cineprime-lich-2026-12-07.xlsx`); đọc lại đúng 21 suất tham khảo và form 500 dòng trống. HTTP từ JAR cuối kiểm Pug UTF-8, ba tài nguyên JS/CSS khớp byte với source, ba phim/hai phòng, workbook v3 và biên nhận tạo mười (`target/weekly-matrix-jar-verification.json`).
- GUI bản cuối kiểm thêm nhóm phòng 2, mục giờ từng ngày tự đóng sau khi bổ sung. Nhập lại file vừa tạo trước đó giữ ba dòng trùng database, chỉ rõ phim/phòng/giờ/file/ô và khóa lưu; lọc lỗi/bỏ ba dòng giữ bảy suất hợp lệ. Dừng riêng server test rồi Làm mới: lịch báo không tải được, giữ **7 suất đang chọn**, khóa cả Thêm nhóm và Lưu. Đã bỏ bảy suất thử chưa lưu trước khi đóng tab (`target/weekly-matrix-gui-verification.json`, `weekly-matrix-load-failure.jpg`).
- Desktop **1366×768, 1920×1080**, mobile **390×844**, breakpoint **620/850/1090** đã xem và đo. Không tràn ngang trang, lịch có cuộn riêng; ở chiều rộng hẹp bảng có thể cuộn ngang bên trong vùng lịch. Mobile đổi thành danh sách ngày trong từng phim, nút thêm đo tối thiểu **44px**, thanh lưu trong viewport (`target/weekly-matrix-responsive.json`, `weekly-matrix-final-*.jpg`). Đã sửa xung đột CSS nowrap khiến tên phim tràn ô và ngưỡng media phân số của viewport kiểm thử. Không có lỗi console trong luồng chính; lỗi mạng khi chủ động tắt server là tình huống kiểm thử.
- Checksum **31/31 bảng cineprime khớp** trước/sau, gồm Flyway (`target/weekly-matrix-app-before.txt`, `weekly-matrix-app-after.txt`, `weekly-matrix-data-verification.json`). Chỉ thêm dữ liệu giả trong cineprime_test; không tạo phim/phòng/suất hoặc đổi dữ liệu ứng dụng. JAR preview 8081 và tab/Excel thử đã đóng; ứng dụng 8080 giữ nguyên. Restart Run local để nạp bản mới.
- Giới hạn còn lại: mất phản hồi commit được hồi quy MySQL và mô phỏng UI, chưa chủ động cắt mạng trình duyệt đúng thời điểm sau commit. Chưa thử trình đọc màn hình, điện thoại/bàn phím thiết bị thật hoặc tải đồng thời nhiều người dùng. Không coi những phần đó đã nghiệm thu thực tế.

### Tự phân loại phim và lịch tuần tick / kéo thả — được duyệt 09/11/2026

Người dùng yêu cầu PLEASE IMPLEMENT THIS PLAN sau khi khảo sát và trao đổi: phim tự phân loại theo ngày Việt Nam; Admin chọn phim/phòng rồi tick trực tiếp trên lịch giờ trong tuần hoặc nhập Excel, kéo thả các suất chưa lưu trong phiên, kiểm tra cuối và xác nhận tạo lịch. Mặc định Mở bán. Mục này thay bố cục phim × ngày và luồng nhập nhóm ở mốc trước.

Tham khảo công khai ngày 09/11/2026: https://www.galaxycine.vn/lich-chieu/ — đọc được chọn vị trí/phim/suất và các bước ghế → thức ăn → thanh toán → xác nhận. Không khảo sát sau đăng nhập hoặc Admin/Staff; lịch quản trị xây dựng theo thao tác người dùng đã chốt và design tokens CinePrime.

- [x] Trạng thái hiệu lực dùng chung theo Asia/Ho_Chi_Minh, tính lúc đọc: trước ngày khởi chiếu là Sắp chiếu, từ ngày đó là Đang chiếu; Ngừng chiếu ưu tiên, Ẩn phim độc lập, thiếu ngày giữ trạng thái cũ. Bộ lọc/count thực hiện cùng quy tắc trong MySQL trước phân trang; không cần cron/migration hoặc ghi phim lúc GET.
- [x] Form phim chọn Tự động theo ngày / Ngừng chiếu, có diễn giải trạng thái và thiếu ngày. CTA/nhắc mở bán căn cứ suất OPEN còn nhận hold, phòng/rạp/phim hoạt động; giữ bán trước khởi chiếu và thông báo một lần.
- [x] Một danh sách chờ: tick thêm ngay, dấu chọn để bỏ, đổi phim/phòng giữ đợt. Lịch phòng có bảy ngày, giờ dọc cách 15 phút, mọi phim Nháp/Mở bán và vùng dọn phòng; mobile một ngày. Kéo suất chưa lưu trong cùng phòng/tuần, bám 15 phút; chỉnh dialog giữ chính xác từng phút. Suất đã lưu dùng editor riêng và bảo vệ vé/hold cũ.
- [x] Excel v1–v3 và nguồn file/sheet/dòng/ô giữ nguyên. Import bổ sung; lỗi ngoài phòng đang xem vẫn có trong danh sách và chặn xác nhận. Giữ giới hạn 500 suất / một rạp / một tuần.
- [x] Lưu và kiểm tra gọi preview mới, màn kiểm tra cuối có giờ bắt đầu/kết thúc/hết dọn phòng, lỗi và nguồn từng dòng. Chỉ Xác nhận tạo gọi commit. Mặc định OPEN, tùy chọn DRAFT ghi rõ khách chưa đặt được. Giữ khóa/fingerprint/rollback toàn bộ/requestKey/biên nhận; phản hồi không đầy đủ không xóa đợt hoặc tự retry.
- [x] Hồi quy MySQL đầy đủ, JAR/Pug UTF-8 và GUI desktop/mobile/breakpoint.
- [x] Đối chiếu dữ liệu ứng dụng, cập nhật kết quả nghiệm thu và giới hạn thực tế.


Kết quả kiểm chứng 09–10/11/2026:

- Hồi quy đầy đủ **122 tests, 0 failures/errors/skipped**, gồm **95 MySQL/HTTP và 27 unit/render/Excel/HMAC**. Log `target/timeline-full.log`, BUILD SUCCESS 09/11 lúc 23:17:01. Các ca mới kiểm đúng nửa đêm Việt Nam, khởi động sau ngày khởi chiếu, đổi ngày tiến/lùi, fallback thiếu ngày, Ngừng chiếu/Ẩn, phân trang/filter/count đồng nhất, phim chưa mở bán vẫn nhắc và bán trước khởi chiếu. Giữ hồi quy ghế/voucher/check-in/callback/PDF tiếng Việt/QR, rollback và replay. Lượt tải 500 OPEN × 600 ghế tạo 300.000 inventory, preview + commit **17.027 ms** trên local (`target/batch-load.json`), không là cam kết tốc độ mọi máy.
- Sáu nhóm mô phỏng DOM/fetch đều qua (`target/timeline-ui-check.cjs`, `.json`): tick sáu suất và xác nhận một lần, dọn phòng đúng biên, kéo con trỏ/thả lỗi/Escape, giữ phút và nguồn Excel, phản hồi preview cũ, đổi tuần giữ lựa chọn khi hủy, lỗi đọc lịch, mất hoặc thiếu phản hồi commit, tra 202 và gửi lại nguyên key/payload. Không cộng mô phỏng hoặc lượt chạy unit ban đầu vào 122 tests.
- GUI JAR trên **cineprime_test** ngày 10/11: tick sáu suất trên hai ngày, Quay lại kiểm tra vẫn đủ sáu, xác nhận tạo đúng **6 Mở bán**. Đợt kế tiếp kéo suất 09:00 sang Thứ Năm 09:30, import thêm hai giờ từ C2, giữ đúng 11:55 và nguồn file/sheet/ô/thứ tự; mobile đổi ngày cho dòng 11:55 rồi xác nhận tạo đúng **3 Mở bán**. SQL/biên nhận xác nhận hai đợt tạo tổng **9 mới**, 45 inventory mới (50 kể cả 5 ban đầu), không lặp suất. Bằng chứng `target/timeline-test-created.txt`.
- Kéo vào phim khác đã có lịch bị chặn và giữ vị trí cũ; suất đã lưu không có tay nắm. ArrowDown chuyển 08:45 → 09:00, Space thêm đúng một suất. Nhập lại file giữ cả dòng trùng ở phòng khác và chặn xác nhận toàn đợt. Chủ động dừng server test rồi Làm mới: **0 ô tick, giữ 1 suất chưa lưu, khóa Lưu**; đã bỏ suất thử đó trước khi đóng tab. `target/timeline-gui-verification.json`, `timeline-load-failure.png`.
- Đã tinh chỉnh sau GUI: lịch mở ở 08:00 và giữ cuộn khi preview/tải lại; thanh ngữ cảnh desktop luôn hiện phim/phòng/tuần. Kéo dùng pointer capture; hủy/thả lỗi giữ dòng gốc. Màn kiểm tra mobile xuống dòng, không còn nowrap gây tràn. Các chỉnh JS/CSS cuối được chạy lại mô phỏng/cú pháp, đóng gói rồi kiểm GUI; không chạy lại toàn bộ MySQL chỉ vì tài nguyên. JAR cuối `target/timeline-final-package.log`.
- Desktop **1366×768, 1920×1080**, mobile **390×844**, breakpoint **620/850/1090** đều không tràn ngang trang; thanh Lưu trong viewport. Mobile một ngày, ô giờ **44px**, dialog đổi ngày/giờ và kiểm tra cuối hoạt động. `target/timeline-responsive.json`, `timeline-desktop.png`, `timeline-mobile.png`, `timeline-review-mobile.png` và `timeline-390/620/850/1090/1366/1920.png`. Không có lỗi console trong luồng chính; lỗi mạng chủ động tắt server được ghi riêng.
- HTTP executable JAR: Pug UTF-8 đúng; **4 tài nguyên JS/CSS khớp SHA-256 source**; phim ngày 08/11 hiện Đang chiếu dù cột status cũ còn COMING_SOON; khách và Staff thấy phòng/lịch Mở bán mới, phim tương lai vẫn bán trước được. `target/timeline-jar-verification.json`.
- Checksum **31/31 bảng cineprime khớp** trước/sau, gồm Flyway: `target/timeline-app-before.txt`, `timeline-app-after.txt`, `timeline-data-verification.json`. Chỉ tạo dữ liệu giả trong cineprime_test. Không đổi mật khẩu, seed, dữ liệu phim/phòng/lịch/giá ứng dụng; không thêm migration/thư viện. JAR test 8081 và tab đã đóng; không dừng ứng dụng 8080. Restart cấu hình CinePrime Local hiện có để nạp mã mới.
- Giới hạn: mất phản hồi sau commit được hồi quy MySQL và mô phỏng UI; chưa chủ động cắt mạng đúng thời điểm sau commit. Chưa thử trình đọc màn hình hoặc điện thoại/bàn phím thiết bị thật. MoMo/SMTP thực và hosting vẫn ngoài đợt này.

### Bảng xếp suất theo ngày × phòng — được duyệt 10/11/2026

Người dùng duyệt phương án ("Duyệt, bắt đầu") sau khi rà lại màn tick/kéo thả: khung lịch chỉ thấy khoảng 4/24 giờ, mỗi lúc một phòng và một phim, mỗi suất một lần bấm theo mốc 15 phút, ô bị khóa không nêu lý do, nhiều tầng điều khiển. Mục này thay bố cục một phòng × bảy ngày của mốc trước. Giữ nguyên API lịch/preview/commit/biên nhận, kiểm tra lại dưới khóa, fingerprint, requestKey, Excel v1–v3, giới hạn một rạp/một tuần/500 suất và quyết định không tự lưu nháp. Đợt 1–2 không thêm migration, thư viện hoặc đổi backend.

Tham khảo công khai ngày 10/11/2026: https://www.galaxycine.vn/lich-chieu/ — đọc được các bước Chọn phim/rạp/suất → ghế → thức ăn → thanh toán → xác nhận và danh sách vị trí. Không khảo sát sau đăng nhập; không có tham chiếu Admin công khai, bảng xếp suất thiết kế từ nghiệp vụ CinePrime và design tokens hiện có.

Mặc định theo đề xuất đã duyệt: khung giờ hiển thị 09:00–23:00 và tự mở rộng khi có suất nằm ngoài; xếp liên tiếp làm tròn lên 5 phút; chép ngày thêm các suất hợp lệ và liệt kê suất bị bỏ.

- [x] Đợt 1: bảng một ngày, mỗi phòng một cột; bảy tab ngày ghi số suất đã lưu/mới/lỗi; bảng phim bên cạnh; bấm cột phòng để thêm với bóng xem trước, tự dính sau suất trước và lùi cho vừa khoảng trống; kéo suất chưa lưu giữa các phòng; lỗi ghi trên khối; nút × bỏ suất; một thanh điều khiển, menu Excel; một hộp xác nhận tóm tắt theo ngày/phòng. Bỏ nút Mở lịch xếp suất và lưới ô 15 phút.
- [x] Đợt 2: xếp liên tiếp từ một giờ bắt đầu đến giờ suất cuối, dừng trước suất đã có; chép các suất mới của ngày đang xem sang ngày khác trong tuần.
- [x] Mobile một ngày/một phòng, vùng chạm 44px; đường bàn phím qua form thêm theo giờ.
- [x] Hồi quy MySQL riêng đầy đủ, JAR/Pug UTF-8, desktop/mobile/breakpoint và đối chiếu dữ liệu ứng dụng.
- [ ] Đợt 3 (chưa làm, trao đổi riêng): dời/hủy suất đã lưu ngay trên bảng và mở bán nhiều suất Nháp; cần thêm API.

Điều chỉnh trong lúc làm, so với bản phác thảo đã duyệt: ô phòng/giờ, Thêm một suất, Xếp liên tiếp và Chép ngày nằm trên một thanh ngang phía trên bảng thay vì trong cột bên trái, để danh sách phim đủ chỗ ở màn 768px; thiết lập ngôn ngữ/định dạng/phụ thu thu gọn dưới danh sách phim. Chiều cao bảng được tính để kết thúc ngay trên thanh dưới; mỗi giờ tối thiểu 26px (66px trên mobile).

Kết quả kiểm chứng ngày 10/11/2026:

- Hồi quy đầy đủ **122 tests, 0 failures/errors/skipped**, gồm **95 MySQL/HTTP và 27 unit/render/Excel/HMAC**; log `target/day-board-full.log`, BUILD SUCCESS lúc 20:17. Ca render Pug được đổi theo giao diện mới (có Xếp liên tiếp, Chép ngày, `batch-board`, `batch-days`; không còn `batch-open`). Backend và các ca tiền/ghế/voucher/check-in/callback không đổi.
- Hàm tính thuần trong `showtime-calendar.js` (dính sau suất trước, lùi vừa khoảng trống, giờ trống đầu tiên, xếp liên tiếp, chép ngày, giới hạn 500, suất qua nửa đêm) qua bộ kiểm tra Node riêng; không cộng vào 122 tests.
- GUI executable JAR trên **cineprime_test**, cổng 8081, 1366×768: bóng xem trước hợp lệ và bóng báo "khoảng trống chỉ có 50 phút" ở lịch kín; bấm thêm 09:05 rồi bấm tiếp ra 11:00 (dính sát); Xếp liên tiếp phim 100 phút tạo 09:00, 10:55, 12:50, 14:45, 16:40, 18:35, 20:30, 22:25; Thêm một suất gợi ý nối tiếp 09:00 → 11:05 → 13:10 với phim 109 phút; kéo 11:00 → 15:35. Chép 10 suất sang Thứ 5 thêm đủ 10, sang Thứ 2 đã kín lịch thì không thêm suất nào và liệt kê lý do. Xác nhận tạo đúng **20 Mở bán**; SQL: bốn nhóm ngày/phòng đúng giờ, inventory 60 ghế mỗi suất, một biên nhận, nhật ký `CREATE_SHOWTIME_BATCH · 20 suất`.
- Sửa giờ trong hộp sửa để hai suất chồng nhau: cả hai khối đỏ kèm lý do của server, tab ngày ghi 2 lỗi, có nút Tới suất cần sửa; bỏ một suất bằng × thì hợp lệ lại. Nhập file mẫu trống: báo chưa có suất mới và giữ nguyên đợt. Suất đã lưu mở hộp thông tin có liên kết trang sửa. Đổi tuần khi còn suất chưa lưu có cảnh báo và giữ đợt khi chọn ở lại.
- Nhánh nhập Excel thành công được thử bằng phản hồi `import-preview` giả lập trong trình duyệt: hai suất được gộp vào đợt, chuyển sang ngày của file, ghi nguồn file/sheet/dòng/ô/thứ tự giờ, và server thật kiểm tra lại cả ba suất. Chưa chọn file Excel thật qua hộp thoại trình duyệt trong lượt này.
- Bố cục: 1366×768 hiện trọn 09:00–23:00 không cần cuộn bảng; 1920×1080 mỗi giờ 48px, không cuộn; 850 và 620 không tràn ngang; 390×844 một cột phòng, nút phòng và nút × 44px, thanh dưới 147px. Ở 1090×768 thanh công cụ xuống hai hàng, bảng giữ chiều cao tối thiểu 340px nên phần đáy nằm dưới thanh dưới cho đến khi cuộn trang.
- JAR cuối (không dùng file ngoài JAR): `showtime-batch.js`, `showtime-calendar.js`, `showtime-batch.css` khớp SHA-256 với source; trang UTF-8, nhãn tiếng Việt đúng; API lịch không đăng nhập trả 401. Tóm tắt tại `target/day-board-verification.json`.
- Số dòng của đủ **31 bảng cineprime** trước/sau giống nhau, dòng nhật ký cuối vẫn là ngày 07/11; lượt này không lấy CHECKSUM. Chỉ tạo dữ liệu giả trong cineprime_test (hiện còn dữ liệu demo seed). Không dừng tiến trình 8080; JAR thử 8081 đã dừng. Restart cấu hình CinePrime Local để nạp template/JS/CSS mới.
- Giới hạn: hộp xác nhận ở trạng thái còn lỗi chưa mở thử trên GUI; kéo chỉ có đường chuột, màn chạm dùng hộp sửa; chưa thử trình đọc màn hình, điện thoại thật, mất mạng đúng lúc ghi hoặc nhiều Admin cùng xếp. MoMo/SMTP thực và hosting vẫn ngoài đợt này.

### Bảng lịch cả tuần (phòng × ngày) — được duyệt 10/11/2026, làm xong 11/11/2026

Người dùng xem bảng ngày × phòng và nhận xét còn khó dùng: chỉ thấy một ngày, lưới giờ phần lớn để trống, nhiều nút mờ không nêu lý do, phải biết trước "chọn phim rồi bấm lưới". Bốn điểm người dùng chốt qua câu hỏi: (1) màn chính là bảng phòng × ngày, mỗi ô liệt kê giờ chiếu; (2) đủ Thứ 2 đến Chủ nhật; (3) thêm suất qua một hộp hướng dẫn; (4) bảng theo giờ giữ làm chế độ xem phụ. Chỉ đổi Pug/JS/CSS và một ca render; không đổi backend, API, migration, Excel hay thư viện. Không khảo sát Galaxy mới; dùng lại tham chiếu ngày 10/11/2026 ở mục trên, bảng tuần thiết kế từ nghiệp vụ CinePrime.

- [x] Bảng **Cả tuần** là màn mặc định: mỗi phòng một hàng, bảy cột ngày, mỗi suất là một nút `giờ + tên phim` tô theo trạng thái; đầu cột ghi số suất/mới/lỗi và mở chế độ Theo giờ của ngày đó; ô ngày đã qua và phòng ngừng hoạt động không có nút thêm.
- [x] Hộp **Thêm suất chiếu** mở từ "+ Thêm" của ô: điền sẵn phòng và ngày, gợi ý giờ trống đầu tiên vừa độ dài phim, chọn nhiều ngày lặp lại, Một suất hoặc Nhiều suất nối tiếp, dòng báo trước liệt kê suất sẽ thêm và suất không thêm kèm lý do. Thay Thêm một suất và Xếp liên tiếp của bản trước.
- [x] Nhiều suất nối tiếp **nhảy qua** suất đang có rồi xếp tiếp (hàm thuần `fillDay`, `plan`); `chain` cũ dừng ở lần trùng đầu không còn được giao diện gọi.
- [x] Chế độ **Theo giờ** giữ bấm lưới, bóng xem trước, kéo thả, Chép ngày; thanh công cụ còn "+ Thêm suất", Hiển thị từ/đến, Chép ngày.
- [x] Dải ba bước khi chưa có suất mới; dải "Phim trong tuần" đếm suất theo phim và làm nổi phim được bấm; lời văn mới (Bán vé ngay sau khi tạo / Lưu nháp · chưa bán vé; Đang bán vé / Nháp, chưa bán / Mới, chưa lưu / Trùng lịch, cần sửa).
- [x] Màn ≤ 850px: bảng tuần hiện một ngày mỗi lần với bảy tab ngày; vùng chạm 44px ở ≤ 620px.
- [x] Hồi quy MySQL riêng đầy đủ và thử trên executable JAR.

Điều chỉnh so với kế hoạch đã duyệt: nút Làm mới giữ trên thanh trên thay vì vào menu Excel; dải phim và chú thích nằm phía trên bảng (không phải bên dưới) để bảng kết thúc ngay trên thanh dưới; giờ gợi ý tính theo độ dài phim đang chọn chứ không chỉ là phút trống đầu tiên.

Kết quả kiểm chứng ngày 11/11/2026:

- Hồi quy đầy đủ **122 tests, 0 failures/errors/skipped** (95 MySQL/HTTP, 27 unit/render/Excel/HMAC); log `target/week-table-full.log`, BUILD SUCCESS lúc 01:21. Ca render Pug đổi tên thành `weekTableRendersAddDialogDayBoardAndFinalConfirmation` và kiểm `batch-week`, `batch-add-dialog`, "Nhiều suất nối tiếp", "Bán vé ngay sau khi tạo", vẫn có `batch-board`, không còn `batch-fill`.
- Hàm thuần `fillDay`/`plan` qua bộ kiểm tra Node riêng ngoài repo: nhảy qua suất đã lưu và suất đang xếp, không xếp vào giờ đã qua của hôm nay, ngày đã qua/phòng kín/đầu vào sai trả một lý do, trần 500, suất khuya không chạm suất sáng hôm sau, lặp nhiều ngày và áp lại lần hai không thêm trùng. Không cộng vào 122 tests.
- GUI trên **cineprime_test**, cổng 8081, 1366×768: mở trang thấy cả tuần của mọi phòng. "+ Thêm" ở Phòng 1/Thứ 3 tuần 23/11 gợi ý 09:00; Nhiều suất nối tiếp 09:00–13:00 lặp ba ngày báo trước 6 suất và đúng 6 suất vào bảng. Sửa giờ để hai suất chồng nhau: hai nút đỏ kèm lý do của server, đầu cột ghi 2 lỗi, Tới suất cần sửa đưa tiêu điểm đến đúng nút; **hộp xác nhận khi còn lỗi** (mục chưa kiểm ở lượt trước) hiện "2 suất cần sửa", khóa nút xác nhận và mở sẵn bảng từng suất. Chuyển Theo giờ thấy đúng hai khối lỗi; bỏ một suất bằng ×, Chép ngày sang Thứ 6 thêm 1 suất; quay lại Cả tuần giữ nguyên đợt. Xác nhận tạo **6 suất**; SQL: đúng giờ 24–27/11, `OPEN`, 60 dòng inventory mỗi suất.
- Sau khi đã có suất lưu: Một suất 09:00 lặp sang ngày đã có suất báo "Không thêm 1 suất" kèm lý do và vẫn thêm ngày còn trống; Nhiều suất nối tiếp ở ngày đã có 09:00 và 11:10 xếp tiếp 13:20, 15:30; giờ gợi ý ở ô đó là 13:20. Suất đã lưu mở hộp thông tin có liên kết trang sửa. Đổi tuần khi còn suất chưa lưu có cảnh báo và giữ đợt khi ở lại.
- Bố cục trên JAR cuối: 1366×768, 1920×1080 và 1090×768 đủ bảy cột, không tràn ngang, bảng kết thúc trên thanh dưới; 850 và 620 hiện một ngày với bảy tab; 390×844 không tràn ngang, nút thêm 44px, hộp thêm suất vừa màn. Ba tài nguyên `showtime-batch.js`, `showtime-calendar.js`, `showtime-batch.css` do JAR cuối phục vụ khớp SHA-256 với source; trang UTF-8.
- Trong lúc thử phát hiện và sửa: giờ gợi ý không vừa độ dài phim; tab ngày bị ẩn ở màn hẹp do một bộ chọn CSS có độ ưu tiên của ID; dải phim chiếm ba dòng (nay một dòng cuộn ngang); bảng từng suất trong hộp xác nhận tràn ngang (nay `table-layout: fixed`).
- Lượt này không chạy lệnh ghi nào vào `cineprime`; kiểm tra chỉ đọc cho thấy dòng nhật ký cuối vẫn là 07/11. Không đếm lại đủ 31 bảng trước/sau. Ứng dụng 8080 không chạy trong lúc thử; JAR thử 8081 đã dừng; `cineprime_test` còn dữ liệu demo seed.
- Giới hạn: Kéo thả trong Theo giờ, nhập/xuất Excel và file mẫu trống không được thử lại trên GUI lượt này (mã không đổi, dựa vào kết quả 10/11 và các ca Maven). Chưa chọn file Excel thật qua hộp thoại trình duyệt. Chưa thử bàn phím đầy đủ, trình đọc màn hình, điện thoại thật, mất mạng đúng lúc ghi. Dải phim có thanh cuộn ngang khi rạp có nhiều phim. Thông báo trùng lịch của server còn in ngày giờ dạng `2026-11-24T09:30` (backend, không sửa trong đợt này). Đợt dời/hủy suất đã lưu ngay trên bảng vẫn chưa làm.

Điều chỉnh theo yêu cầu người dùng ngày 11/11/2026 (sau khi xem bảng phòng × ngày): **mỗi phòng là một lịch riêng**. Phía trên bảng có danh sách phòng, mỗi nút ghi định dạng, số suất trong tuần, số suất mới và số lỗi; bảng Cả tuần chỉ hiện phòng đang chọn với bảy cột ngày, mỗi suất ghi giờ bắt đầu – kết thúc và tên phim. Màn ≤ 850px hiện bảy ngày thành bảy hàng thay cho một ngày mỗi lần, không còn tab ngày ở chế độ Cả tuần. Dải phim đếm theo phòng đang chọn. Sau khi thêm suất, tới suất lỗi hoặc nhập Excel, bảng chuyển sang phòng liên quan. Chế độ Theo giờ vẫn hiện mọi phòng của một ngày.

- Kiểm chứng: **122 tests, 0 failures/errors/skipped**, log `target/week-table-full.log`, BUILD SUCCESS 11/11/2026 lúc 08:29. Trên JAR/`cineprime_test` cổng 8081, 1366×768: chọn Phòng 2, "+ Thêm" mở hộp với Phòng 2 điền sẵn, Nhiều suất nối tiếp 09:00–15:00 lặp hai ngày báo trước 6 và vào bảng đúng 6; nút phòng ghi "6 suất · 6 mới"; Phòng 1 vẫn trống; hộp xác nhận tóm tắt đúng Phòng 2 và tạo được 6 suất. 1090×768 đủ bảy cột; 850 và 390×844 hiện bảy hàng ngày; không tràn ngang.
- Không thử lại ở lượt điều chỉnh này: hiển thị lỗi, Tới suất cần sửa khi lỗi nằm ở phòng khác, nhập Excel, kéo thả, 1920×1080 và 620. Chưa đối chiếu SQL cho 6 suất của lượt này (giao diện báo đã tạo và tải lại lịch từ server).

### Vòng đời phim có ngày kết thúc và làm lại UX Admin — được duyệt 11/11/2026

Người dùng nhận xét phim chưa có điểm kết thúc (sau ngày khởi chiếu phim "Đang chiếu" mãi cho tới khi Admin đổi tay sang Ngừng chiếu) và UX/UI Admin chưa đạt: form phim 9 bước, nút mở rộng và mũi tên phân trang ở Tổng quan kém thẩm mỹ, thông tin trong bảng khó đọc, muốn thu gọn sidebar mượt. Quyết định đã chốt: ngày kết thúc **bắt buộc**; suất chỉ nằm trong khoảng chiếu; khách **đặt trước ngay khi Admin mở bán suất**; phim hết lịch **mờ + khóa 7 ngày rồi tự ẩn**; dữ liệu cũ tự điền ngày kết thúc theo suất cuối; phạm vi UI gồm form phim, Tổng quan, bảng danh sách và sidebar. Làm trên nhánh `feature/movie-lifecycle-admin-ux`, chỉ merge khi người dùng đồng ý.

Không khảo sát Galaxy ở lượt này: phần sửa là Admin (thiết kế từ nghiệp vụ) và thẻ phim công khai chỉ thêm nhãn/nút theo quy tắc mới.

- [x] Migration `V4__movie_end_date.sql`: thêm `end_date`; điền `release_date` trống bằng ngày suất đầu (hoặc hôm nay) và lùi về ngày suất đầu nếu có suất sớm hơn; `end_date` = max(khởi chiếu + 30 ngày, ngày suất chưa hủy cuối); STOPPED → ẩn; hai cột NOT NULL và CHECK `end_date >= release_date`. Không sửa V1–V3.
- [x] `MovieLifecycle`: Sắp chiếu / Đang chiếu / Đã kết thúc theo ngày Việt Nam, cùng biểu thức HQL cho lọc/đếm/phân trang; mốc 7 ngày công khai; kiểm khoảng chiếu của suất.
- [x] Ràng buộc: lịch tuần/Excel (`OUT_OF_RELEASE_WINDOW`), form sửa suất và kiểm tra sẵn sàng mở bán chặn suất ngoài khoảng; lưu phim chặn khoảng mới bỏ suất ra ngoài và chặn đổi thời lượng khi còn suất (kể cả nháp) sắp tới; đặt vé chặn suất sau ngày kết thúc.
- [x] Khách: nhãn "Khởi chiếu dd/MM/yyyy", nút ĐẶT TRƯỚC, thẻ phim đã kết thúc mờ không có link đặt vé, bộ lọc "Vừa kết thúc", banner trang chủ bỏ phim đã kết thúc, trang chi tiết ghi khoảng chiếu.
- [x] Form phim một trang (`admin/movie-form.pug`, `admin-movie.js`, `admin-movie.css`): 4 thẻ (cơ bản, lịch phát hành, nội dung/đoàn phim, hình ảnh/trailer), cột xem trước dính, checklist sẵn sàng, công tắc hiển thị/nổi bật, chọn nhanh 2/4/6/8 tuần và dải thời gian, tìm đạo diễn/diễn viên dạng chip, lỗi tại chỗ (client và server). Upload/slug/trailer dùng lại `admin-editor.js`.
- [x] Sidebar thu gọn trên desktop (nút ☰, nút Thu gọn, Ctrl + B) thành thanh biểu tượng SVG, nhóm mở dạng menu bay; nhớ trạng thái và áp dụng trước lần vẽ đầu bằng `admin-nav-pref.js` (CSP không cho script inline). Mobile giữ ngăn kéo.
- [x] Tổng quan: nút mở rộng dạng viên có mũi tên xoay, định nghĩa số liệu dạng danh sách, câu kỳ so sánh viết lại; phân trang mũi tên nhỏ "x / y", ẩn mũi tên khi chỉ một trang; trạng thái trống gọn.
- [x] Bảng danh sách: nhãn trạng thái có màu và chữ rõ (Đang hiển thị/Đã ngừng/Sắp hết · còn N ngày…), nút Sửa gọn, thao tác rủi ro trong menu "⋯" ghi đúng hành động, ẩn nút ngừng với dòng đã ngừng.

Kết quả kiểm chứng ngày 11/11/2026:

- Hồi quy đầy đủ sau từng giai đoạn: **124 tests, 0 failures/errors/skipped** (96 MySQL/HTTP trên `cineprime_test`, 28 unit/render/Excel/HMAC). Ca mới: ba trạng thái và ranh giới ngày Việt Nam, mốc 7 ngày, khoảng chiếu của suất ở lịch tuần/form sửa suất, chặn rút/lùi khoảng ngày khi còn suất, bắt buộc ngày kết thúc, khóa thời lượng do suất nháp, đặt trước và chặn đặt vé sau kết thúc.
- Migration trên **bản sao dữ liệu thật**: dump chỉ đọc từ `cineprime` khôi phục vào `cineprime_test` ở V3, thêm 5 phim trường hợp khó (STOPPED, chưa có ngày có/không suất, suất trước ngày khởi chiếu, suất xa hơn 30 ngày kèm suất hủy). JAR mới áp V4 thành công; cả 6 phim ra đúng ngày dự tính, 0 suất chưa hủy nằm ngoài khoảng, hai cột NOT NULL và CHECK có mặt; Hibernate validate qua và trang công khai trả 200. Đã xóa file dump tạm. `cineprime` **chưa** được migrate.
- GUI trên JAR/`cineprime_test` cổng 8081 với dữ liệu demo, 1440×900 và 375×812: thêm phim mới và lưu; sửa phim có 24 suất (dải thời gian, checklist); đổi thời lượng bị server chặn, giữ dữ liệu và đánh dấu đúng ô; Tổng quan với nút mở rộng mới; sidebar thu gọn, menu bay, giữ trạng thái khi chuyển trang; danh sách phim và menu "⋯"; ngăn kéo mobile.
- Chưa kiểm tra trên GUI: upload ảnh thật qua form mới (mã upload không đổi), chỉnh cuối đặt cột xem trước dưới form trên mobile và menu "⋯" định vị cố định từ CSS (chỉ qua test tự động), 1920×1080 và breakpoint 620/850/1090 cho các trang mới, trang khách với phim đã kết thúc trên GUI (có ca test HTTP/catalog).
