# CinePrime

Đồ án đặt vé xem phim bằng Spring Boot MVC, Pug4J và MySQL. Ba vai trò: Customer, Admin, Staff.

## Hiện trạng

Kế hoạch được duyệt và ứng dụng được triển khai ngày 05/11/2026. Có executable JAR, migration MySQL, dữ liệu demo, giao diện Customer/Admin/Staff, giữ ghế, combo/voucher, adapter MoMo sandbox, QR/PDF, hồ sơ, điểm, đánh giá, thông báo và báo cáo.

**Đã kiểm chứng:** build JAR; khởi động với MySQL 8.4.13; 26 kiểm thử (25 tích hợp MySQL/HTTP và 1 HMAC) đều qua, không bỏ qua test trong lượt nghiệm thu; Pug UTF-8, PDF tiếng Việt và giải mã QR. Đã kiểm tra giao diện bằng trình duyệt desktop/mobile và kiểm tra cú pháp JavaScript.

**Chưa kiểm chứng với dịch vụ ngoài:** tạo/trả tiền qua ví MoMo Test thực tế, SMTP thực tế, camera trên thiết bị thật và triển khai HTTPS. Chưa có khóa hoặc máy chủ được cung cấp. Không có chế độ tự giả thanh toán thành công. Callback trong test là dữ liệu kiểm thử có chữ ký, không phải bằng chứng giao dịch MoMo thực.

Đặc tả/checklist tại [Plan.md](Plan.md), chuẩn thiết kế tại [UI-Template.md](UI-Template.md), hướng dẫn AI tại [AGENTS.md](AGENTS.md). Checkout ban đầu chỉ có UI-Template.md; các file demo cũ nhắc trong tài liệu không có trong dự án này.

## Stack và môi trường

Tối ưu local đã kiểm chứng ngày 06/11/2026: dashboard Admin từ 5.648–6.146 ms xuống 74–108 ms (3 lượt sau khi làm nóng, cùng dữ liệu demo); POST đăng nhập 231–254 ms cho ba vai trò; trang tài khoản 23–39 ms; API sơ đồ ghế 21–24 ms. Đây là thời gian HTTP local, chưa phải kiểm thử tải nhiều người dùng. Dashboard tổng hợp số ghế/vé bằng query nhóm; sơ đồ ghế đọc trạng thái mới mỗi lần gọi và chỉ truy vấn giá theo loại ghế. Cache Pug chỉ giữ template biên dịch, không cache dữ liệu người dùng hoặc ghế. Thay template khi cache bật cần khởi động lại; có thể đặt `TEMPLATE_CACHE=false` khi thiết kế giao diện.

Trong cấu hình IntelliJ local hiện tại, mở `http://127.0.0.1:8080` để tránh độ trễ kết nối `localhost` quan sát được trên máy Windows này. Khi đổi từ localhost sang 127.0.0.1 cần đăng nhập lại vì cookie thuộc host khác.

Java 25, Maven Wrapper, Spring Boot MVC/Security/JPA/Mail, Pug4J `.pug`, JavaScript thuần, MySQL 8.4 InnoDB, Flyway. Không cần Node hoặc Docker. Đặt JAVA_HOME vào JDK thực, ví dụ `C:\Program Files\Java\jdk-25`, tránh Oracle javapath shim nếu `java -version` bị treo.

## Chạy trên Windows

Tạo database và tài khoản bằng MySQL Workbench hoặc mysql CLI (thay mật khẩu mẫu):

```sql
CREATE DATABASE cineprime CHARACTER SET utf8mb4;
CREATE USER 'cineprime'@'localhost' IDENTIFIED BY 'YOUR_DATABASE_PASSWORD';
GRANT ALL PRIVILEGES ON cineprime.* TO 'cineprime'@'localhost';
```

Trong PowerShell ở thư mục dự án:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:DB_URL='jdbc:mysql://localhost:3306/cineprime?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USER='cineprime'
$env:DB_PASSWORD='YOUR_DATABASE_PASSWORD'
$env:DEMO_SEED='true'
$env:DEMO_PASSWORD='YOUR_DEMO_PASSWORD_AT_LEAST_10_CHARACTERS'
.\mvnw.cmd -B -ntp package
java -jar target\cineprime-1.0.0.jar
```

Maven Wrapper tải Maven/dependency lần đầu. Trên Linux dùng `./mvnw package`. Flyway tự chạy migration; Hibernate chỉ validate, không tự đổi schema. Không sửa migration đã áp dụng; thêm migration mới.

Mở [CinePrime local](http://localhost:8080/). Seed chỉ chạy khi `DEMO_SEED=true` và bảng users trống, không ghi đè người dùng hiện có. Sau lần đầu có thể tắt DEMO_SEED. Password BCrypt yêu cầu ít nhất 10 ký tự, tối đa 72 byte UTF-8.

| Vai trò | Email demo | Mật khẩu | Trang |
|---|---|---|---|
| Admin | admin@cineprime.local | Giá trị DEMO_PASSWORD lúc seed | /admin |
| Staff | staff@cineprime.local | Giá trị DEMO_PASSWORD lúc seed | /staff |
| Customer | customer@cineprime.local | Giá trị DEMO_PASSWORD lúc seed | /account |

Phiên local được chuẩn bị tại http://127.0.0.1:8080/; mật khẩu các tài khoản demo được cấu hình riêng qua DEMO_PASSWORD và không công bố trong repository. MySQL portable đang chạy riêng trên 127.0.0.1:3307 trong thư mục tạm `cineprime-mysql-runtime`; không cài dịch vụ Windows. Đây là preview của phiên làm việc; khi thiết lập máy/database riêng, làm theo cấu hình phía trên và chọn DEMO_PASSWORD của bạn.

Dữ liệu gồm 8 phim hư cấu, 2 rạp, mỗi rạp 2 phòng/60 ghế, lịch tương lai 7 ngày, bảng giá, 3 sản phẩm, voucher CHAOHE/TRAINGHIEM và nội dung mẫu. Staff mẫu thuộc CinePrime Riverside. Lịch mẫu cố định theo ngày seed; tạo thêm lịch trong Admin khi cần. Địa chỉ @cineprime.local không nhận email; đăng ký email thật của bạn để thử SMTP.

**Test local trước khi có MoMo:** chọn ghế → nhập voucher `TRAINGHIEM` → Cập nhật đơn → Xác nhận vé miễn phí. Voucher giảm 100%, tối đa 3 lượt/tài khoản trong dữ liệu demo. Hệ thống phát QR/PDF và ghi provider VOUCHER, không cộng điểm vì thực trả 0. Đây là giao dịch ưu đãi miễn phí hợp lệ, không chứng minh tích hợp MoMo đã chạy. Staff vẫn áp dụng khung check-in theo giờ thật.

## Cấu hình

Tất cả qua biến môi trường, không cần file .env hoặc config bí mật trong repository.

| Biến | Mặc định / ý nghĩa |
|---|---|
| DB_URL / DB_USER / DB_PASSWORD | localhost:3306/cineprime / cineprime / trống |
| BASE_URL | http://localhost:8080; đổi thành URL HTTPS demo cho reset/redirect/IPN |
| SERVER_PORT / SERVER_ADDRESS | Theo Spring Boot; nên bind 127.0.0.1 khi đặt sau reverse proxy |
| COOKIE_SECURE | false khi local HTTP; true khi HTTPS |
| UPLOAD_DIR | ./uploads; dữ liệu runtime, không nằm trong JAR |
| TEMPLATE_CACHE | true mặc định; đặt false khi chỉnh template và cần thấy thay đổi ngay |
| DEMO_SEED / DEMO_PASSWORD | false / trống; bật tường minh để tạo dữ liệu |
| MOMO_PARTNER_CODE / MOMO_ACCESS_KEY / MOMO_SECRET_KEY | Khóa môi trường MoMo Test, chỉ ở server |
| MAIL_ENABLED | false; khi false email nằm chờ trong outbox |
| SMTP_HOST / SMTP_PORT | localhost / 587 |
| SMTP_USER / SMTP_PASSWORD | Thông tin SMTP |
| SMTP_AUTH / SMTP_TLS | true / true; điều chỉnh theo SMTP của bạn |
| MAIL_FROM | noreply@cineprime.example; thay bằng địa chỉ sender được SMTP cho phép |
| PDF_FONT | Trống để dùng font Noto Sans kèm theo; có thể chỉ định TTF khác |

### MoMo sandbox

- API One-time Payment `captureWallet`, `autoCapture=true`, endpoint test cố định `https://test-payment.momo.vn`.
- IPN công khai: `POST {BASE_URL}/payments/momo/ipn`; cần MoMo truy cập được, dùng HTTPS khi demo ngoài Internet.
- Redirect chỉ về trang đơn; không dùng query string trình duyệt để phát vé.
- Chữ ký HMAC-SHA256, merchant, request/order và số tiền được kiểm tra. Query từ server khôi phục khi mất IPN; có nút Kiểm tra thanh toán ở đơn và Admin.
- Hạn giữ ghế do CinePrime quản lý. Không giả định payUrl hết hạn sau 5 phút. Thành công muộn/canceled được ghi nhận SUCCEEDED + reconciliation, không phát vé và không tự tuyên bố hoàn tiền.
- Mã 9000 được giữ PENDING để xác minh tiếp, không xem mọi mã khác 0 là thất bại.
- MoMo yêu cầu tối thiểu 1.000đ. Nếu voucher làm tổng bằng 0, server xác nhận đơn miễn phí với provider VOUCHER, không gửi MoMo. Tổng dương dưới 1.000đ cần sửa voucher.

Tài liệu: [One-time Payment](https://developers.momo.vn/v3/vi/docs/payment/api/wallet/onetime/), [IPN](https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/), [Query](https://developers.momo.vn/v3/vi/docs/payment/api/payment-api/query/).

#### Lấy khóa và thử MoMo khi bạn sẵn sàng

1. Xác định tài khoản hiện có: ví MoMo cá nhân dùng để trả tiền khác với tài khoản merchant trên [MoMo for Business](https://business.momo.vn). Ứng dụng cần tài khoản M4B và quyền môi trường Test.
2. Trong M4B, tìm **Thông tin tích hợp / Integration Information** và chọn **Test**, lấy Partner Code, Access Key, Secret Key. Tên menu có thể thay đổi; theo [quy trình tích hợp chính thức](https://developers.momo.vn/v3/docs/payment/onboarding/integration-process/) và [hồ sơ merchant](https://developers.momo.vn/v3/vi/docs/payment/onboarding/merchant-profile/).
3. Đặt MOMO_PARTNER_CODE, MOMO_ACCESS_KEY, MOMO_SECRET_KEY trong terminal chạy ứng dụng, rồi khởi động lại JAR. Không dán khóa vào chat, source hoặc ảnh chụp màn hình.
4. Khi test IPN từ local, cần một URL HTTPS chuyển tiếp đến cổng 8080 mà MoMo truy cập được; khi đó đặt BASE_URL thành URL đó. Chưa cần triển khai website lên hosting. Nếu không có IPN, nút Kiểm tra thanh toán sử dụng query server-to-server sau khi gateway đã trả kết quả.
5. Dùng ví/app môi trường Test theo [hướng dẫn MoMo Test](https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions/). Không coi tài khoản/app MoMo production là ví sandbox. Làm theo hướng dẫn hiện hành của MoMo về thiết bị, ví và xác thực.
6. Thử thành công/hủy/thất bại; kiểm tra đơn PAID chỉ sau kết quả đã xác minh, QR, email (nếu bật SMTP) và đối soát khi trả tiền quá hạn. Phải dùng voucher khác TRAINGHIEM hoặc không voucher để có khoản thanh toán qua MoMo.

Theo cập nhật người dùng 05/11/2026: chưa cấu hình các dịch vụ ngoài; đã có tài khoản MoMo nhưng chưa xác định loại; ưu tiên nghiệm thu local trước.

### Email và nhắc lịch

MAIL_ENABLED=false không gửi ra ngoài. Khi bật, worker gửi outbox, retry với khoảng chờ tăng dần khi SMTP lỗi. Lỗi mail không làm mất vé. Outbox chống tạo công việc trùng; SMTP vẫn có thể giao lại email nếu tiến trình dừng sau khi SMTP đã nhận nhưng trước khi lưu sentAt.

Reset token dùng một lần/30 phút; đổi password vô hiệu phiên cũ. Nhắc suất trước 2 giờ, không nhắc thêm cho đơn mua sát giờ. Nhắc phim gửi một lần khi có suất tương lai mở bán; khách chọn nhận thêm email hoặc chỉ thông báo tài khoản.

### Upload và tài nguyên

Ảnh JPEG/PNG tối đa 5 MB, giới hạn 8000px; trailer MP4 tối đa 100 MB. Server kiểm tra nội dung, đổi tên UUID; file được phục vụ qua /media. Poster demo SVG là minh họa tự tạo. Inter dùng cho web, Noto Sans nhúng PDF; font và ZXing có license kèm trong static. Không cần CDN lúc sử dụng.

## Hướng dẫn demo ba vai trò

1. **Admin:** tạo rạp → phòng → tạo sơ đồ hàng/cột → chỉnh loại/cặp/bảo trì → thêm giá → phim/thể loại/đoàn phim → suất DRAFT/OPEN. Xem lịch theo ngày, voucher usage, đơn/đối soát, CSV và nhật ký.
2. **Customer:** chọn phim/rạp/ngày/suất → chọn ghế → Tiếp tục → combo/voucher → Cập nhật đơn → MoMo sandbox → xem QR/PDF. Thiếu khóa thì hiển thị rõ chưa cấu hình, không tự thanh toán. Một QR tương ứng một ghế; ghế đôi hai QR.
3. **Staff:** chọn ngày và đúng suất tại rạp phụ trách → quét QR hoặc nhập T-…/CP-… → kiểm tra thông tin → xác nhận. Camera cần localhost hoặc HTTPS và quyền từ người dùng. Có thể dùng mã thủ công khi không có camera.

Ghế giữ 5 phút, tối đa 8 ghế/đơn, một lượt giữ đang hoạt động/người. Dừng nhận hold trước suất 20 phút để đủ 5 phút trước giờ dừng bán. Reload/đổi combo không tăng hạn. Ghế đôi luôn chọn nguyên cặp. Check-in từ -60 đến +30 phút, dùng một lần. Không hủy vé đã trả tiền; dữ liệu giao dịch được giữ lịch sử.

## Kiểm thử

`mvnw test` chạy unit test và **bỏ qua integration test nếu không có TEST_DB_URL hợp lệ**. Để chạy đủ bộ, tạo schema riêng tên chính xác cineprime_test và cấp quyền cho tài khoản test:

```sql
CREATE DATABASE cineprime_test CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON cineprime_test.* TO 'cineprime'@'localhost';
```

```powershell
$env:TEST_DB_URL='jdbc:mysql://localhost:3306/cineprime_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:TEST_DB_USER='cineprime'
$env:TEST_DB_PASSWORD='YOUR_DATABASE_PASSWORD'
.\mvnw.cmd -B -ntp test
```

Integration test xóa dữ liệu các bảng trong **schema test** trước mỗi ca; từ chối tên schema khác cineprime_test. Không dùng database ứng dụng cho test. Kiểm tra báo cáo `target/surefire-reports`; kết quả đầy đủ hiện tại: **26 tests, 0 failures, 0 errors, 0 skipped** trên MySQL 8.4.13, READ COMMITTED.

Phạm vi test: cạnh tranh ghế/voucher/check-in; atomic hold; TTL biên 300s; không gia hạn; couple; snapshot giá; callback sai tiền/chữ ký/trùng/đảo/thành công muộn; đơn canceled; free voucher; reset token; overlap/đệm/layout; role/ownership/CSRF/khóa phiên; render trang Customer/Admin/Staff; PDF tiếng Việt và QR decode. Không dùng H2.

## Demo HTTPS

Ứng dụng đã đóng gói để chạy JAR với MySQL 8.4. Người dùng muốn dùng Vercel sau này nhưng hiện yêu cầu test local trước; **chưa triển khai công khai và chưa tạo cấu hình Vercel/Docker**.

Vercel hiện có hướng [Container Functions](https://vercel.com/i/do-you-need-docker-to-deploy) cho runtime tùy biến. Khi làm mốc đó, cần xác minh điều kiện tài khoản/gói, đóng gói Java, chuyển session/upload sang backing services bền vững và bố trí scheduler/email worker phù hợp vòng đời serverless. App local hiện dùng session trong process, upload trên disk và @Scheduled, nên không được xem là đã sẵn sàng chạy nguyên trạng trên Vercel. Không đổi stack sang Node/React chỉ để deploy.

- Cấu hình DB và upload directory tồn tại bền vững, SMTP, MoMo Test; BASE_URL là URL công khai; COOKIE_SECURE=true; TEMPLATE_CACHE=true.
- Chạy JAR dưới tài khoản dịch vụ, bind localhost sau reverse proxy có TLS. Proxy chuyển Host/X-Forwarded-Proto đúng; giới hạn upload phù hợp 105 MB.
- Cho phép IPN được truy cập công khai; chỉ mở cổng ứng dụng qua proxy, không mở MySQL ra Internet.
- Trước nâng cấp: sao lưu MySQL và UPLOAD_DIR. Migration chạy lúc khởi động; khôi phục backup khi cần rollback schema.
- Kiểm tra bằng tài khoản Customer thật của demo: mua qua MoMo Test, nhận IPN, email, PDF và Staff camera/check-in. Không đánh dấu nghiệm thu các bước này chỉ từ test backend.

## Kiến trúc và lưu ý triển khai

`domain` chứa JPA model/repository; `auth`, `booking`, `payment`, `staff`, `admin`, `media`, `notification`, `web` phụ trách nghiệp vụ. Các aggregate được khóa tại service transaction; MySQL READ COMMITTED và refresh khi lấy khóa tránh đọc entity cũ trong persistence context. Không tin tổng tiền từ client. Inventory unique(suất,ghế), QR/transaction/ledger/voucher có ràng buộc chống trùng.

Pug resolver được đăng ký tường minh vì resolver tên viewResolver mặc định của Boot có thể khiến điều kiện autoconfiguration của spring-pug4j không kích hoạt. Template dùng JEXL, không chạy Node/GraalJS.

Chưa triển khai Google Login, VNPay, hoàn tiền, đổi điểm và gợi ý phim. Các màn quản trị dùng form/bảng chung; chưa có kéo thả sơ đồ ghế hoặc quản lý kho nguyên liệu. Tài liệu/hình tham khảo Galaxy không được dùng để giả mạo thương hiệu hoặc sao chép dữ liệu thực.
