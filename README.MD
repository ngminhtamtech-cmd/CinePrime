# CinePrime

Đồ án đặt vé xem phim bằng Spring Boot MVC, Pug4J và MySQL. Ba vai trò: Customer, Admin, Staff.

## Tổng quan công nghệ và nghiệp vụ

### 1. Mô hình công nghệ (Tech stack)

- Ngôn ngữ phía máy chủ: Java 25.
- Framework: Spring Boot MVC, đóng gói một file JAR chạy được.
- Giao diện: Pug4J (render HTML phía máy chủ), CSS thuần.
- Truy cập dữ liệu: JPA/Hibernate (`ddl-auto: validate`), migration bằng Flyway.
- Cơ sở dữ liệu: MySQL 8.
- Bảo mật: Spring Security, session, CSRF, băm mật khẩu.
- Thanh toán: MoMo (captureWallet, IPN, truy vấn, xác thực HMAC).
- Tiện ích: PDFBox (vé PDF), mã QR, ZXing (quét QR bằng camera), email outbox.
- Kiểm thử: JUnit; kiểm thử tích hợp chạy trên MySQL thật, không dùng H2.

### 2. Logic nghiệp vụ

- Giữ ghế 5 phút do máy chủ quyết định; tải lại trang, đổi combo, đổi voucher hay thanh toán đều không kéo dài thời gian giữ.
- Mỗi người một lượt giữ ghế, tối đa 8 ghế; ghế đôi bán theo cặp.
- Chỉ cho giữ ghế khi còn ít nhất 5 phút trước giờ đóng bán (15 phút trước giờ chiếu).
- Tiền tính bằng VND nguyên, chỉ tính ở backend; đơn lưu ảnh chụp giá, tên và giảm giá.
- Thanh toán xử lý lặp lại an toàn (idempotent), kiểm tra chữ ký, merchant, mã đơn, số tiền. Thanh toán thành công đến sau khi hết hạn giữ ghế thì ghi nhận để đối soát, không phát vé.
- Check-in: mỗi mã QR dùng một lần, trong khoảng 60 phút trước đến 30 phút sau giờ chiếu, chỉ tại rạp của nhân viên.
- Thời gian nghiệp vụ theo múi giờ Asia/Ho_Chi_Minh, tầng DB/JDBC dùng UTC.
- Đổi vai trò hoặc khóa tài khoản làm các phiên đăng nhập cũ mất hiệu lực (`authVersion`).

### 3. Ngôn ngữ JavaScript

- JavaScript thuần (không framework, không Node ở runtime), nằm trong `static/js`.
- Dùng cho: sơ đồ ghế và giữ ghế, chọn combo/voucher, trạng thái thanh toán, quét QR check-in, thông báo, màn xếp suất chiếu, xem phòng 3D (three.js vendor, chỉ tải khi mở).
- Máy chủ vẫn là nơi kiểm tra cuối cùng; JS chỉ hỗ trợ hiển thị và thao tác.
- Màn xếp suất chiếu: bảng lịch cả tuần theo phòng, hộp thêm suất, chế độ theo giờ có kéo thả, nhập/xuất Excel.

### 4. Cơ sở dữ liệu: MySQL

- Quản lý bằng Flyway (`db/migration`), không sửa migration đã áp dụng.
- Ràng buộc duy nhất: tồn kho ghế theo (suất chiếu, ghế), mã QR, giao dịch, sổ điểm, lượt dùng voucher.
- Giao dịch nguyên tử (tất cả hoặc không gì cả) ở mức READ COMMITTED; khóa bi quan theo thứ tự cố định để tránh tranh chấp.
- Các tranh chấp (ghế, voucher, check-in) được kiểm chứng trên MySQL thật.

### 5. Phân tích nghiệp vụ

#### 5.1. Nghiệp vụ của Admin

- Quản lý phim: thêm, sửa, ẩn trên một form một trang có xem trước. Mỗi phim bắt buộc có ngày khởi chiếu và ngày kết thúc; trạng thái Sắp chiếu / Đang chiếu / Đã kết thúc tự suy ra theo ngày Việt Nam. Suất chiếu chỉ được xếp trong khoảng này; không rút ngắn khoảng ngày khi còn suất nằm ngoài.
- Quản lý rạp, phòng chiếu, sơ đồ ghế, giá theo phòng.
- Quản lý suất chiếu: xếp lịch theo tuần cho từng phòng, thêm hàng loạt, nhập/xuất Excel, kiểm tra trùng giờ và thời gian dọn phòng.
- Quản lý combo, voucher, điểm thưởng.
- Quản lý người dùng và phân quyền (Customer, Admin, Staff), khóa tài khoản.
- Xem đơn hàng, đối soát thanh toán, báo cáo doanh thu và xuất CSV, nhật ký thao tác (audit).

#### 5.2. Nghiệp vụ của Staff

- Check-in vé bằng quét QR hoặc nhập mã thủ công.
- Chỉ xử lý vé thuộc rạp được phân công; mỗi vé chỉ vào một lần.
- Xem danh sách vé, suất chiếu trong ngày tại rạp của mình.
- Nhân viên chưa được phân rạp thấy thông báo chờ phân công.

#### 5.3. Nghiệp vụ của Khách hàng

- Xem danh sách phim, chi tiết phim, lịch chiếu theo rạp và ngày. Đặt trước được suất đã mở bán của phim Sắp chiếu; phim Đã kết thúc hiện mờ, không đặt vé và rời danh sách sau 7 ngày.
- Đăng ký, đăng nhập, quên và đặt lại mật khẩu.
- Chọn ghế (giữ 5 phút), thêm combo, áp voucher, dùng điểm.
- Thanh toán qua MoMo; nhận vé có mã QR và vé PDF.
- Xem lịch sử đơn, nhận email và thông báo nhắc suất chiếu.

## Stack và môi trường

Số đo của đợt tối ưu local ngày 06/11/2026, **trước khi cải tổ UX/UI điều hành**: dashboard Admin từ 5.648–6.146 ms xuống 74–108 ms (3 lượt sau khi làm nóng, cùng dữ liệu demo); POST đăng nhập 231–254 ms cho ba vai trò; trang tài khoản 23–39 ms; API sơ đồ ghế 21–24 ms. Đợt UX/UI giữ các test hồi quy số query; chưa đo lại thời gian HTTP hoặc tải nhiều người dùng cho dashboard mới. Dashboard tổng hợp số ghế/vé bằng query nhóm; sơ đồ ghế đọc trạng thái mới mỗi lần gọi và chỉ truy vấn giá theo loại ghế. Cache Pug chỉ giữ template biên dịch, không cache dữ liệu người dùng hoặc ghế. Thay template khi cache bật cần khởi động lại; có thể đặt `TEMPLATE_CACHE=false` khi thiết kế giao diện.

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

`V4__movie_end_date.sql` (11/11/2026) thêm ngày kết thúc bắt buộc cho phim và tự điền cho phim cũ: ngày kết thúc = muộn hơn giữa (ngày khởi chiếu + 30 ngày) và ngày suất chưa hủy cuối cùng; phim chưa có ngày khởi chiếu lấy ngày suất đầu hoặc ngày chạy migration; phim Ngừng chiếu chuyển thành ẩn. Lệnh DDL trên MySQL không rollback được, nên **sao lưu database trước lần khởi động đầu tiên** với bản có V4:

```powershell
& 'C:\ProgramData\CinePrime\MySQL\bin\mysqldump.exe' -h 127.0.0.1 -P 3307 -u cineprime -p --single-transaction --no-tablespaces --result-file=cineprime-before-v4.sql cineprime
```

Sau khi chạy, kiểm tra lại ngày kết thúc của từng phim trong Admin → Phim.

`V5__banners.sql` (12/11/2026) thêm bảng `banners` (banner trang chủ) và `settings` (thời gian tự chuyển, mặc định 6 giây). Nội dung loại BANNER cũ được chép sang banner khuyến mãi (chỉ bật nếu có ảnh) rồi ẩn khỏi Nội dung; ảnh minh họa `/images/poster-*.svg` gắn ở bài viết/ưu đãi và banner phim được gỡ (poster phim giữ nguyên). Sao lưu trước như với V4, ví dụ `--result-file=cineprime-before-v5.sql`. `V6__movie_banners.sql` tạo banner phim cho mọi phim đang hiển thị và chưa hết ngày kết thúc (phim mới nhất trước); từ đó mỗi phim được lưu hoặc Hiện lại trong Admin tự có một banner ở cuối danh sách.

Mở [CinePrime local](http://localhost:8080/). Seed chỉ chạy khi `DEMO_SEED=true` và bảng users trống, không ghi đè người dùng hiện có. Sau lần đầu có thể tắt DEMO_SEED. Password BCrypt yêu cầu ít nhất 10 ký tự, tối đa 72 byte UTF-8.

| Vai trò | Email demo | Mật khẩu | Trang |
|---|---|---|---|
| Admin | admin@cineprime.local | Giá trị DEMO_PASSWORD lúc seed | /admin |
| Staff | staff@cineprime.local | Giá trị DEMO_PASSWORD lúc seed | /staff |
| Customer | customer@cineprime.local | Giá trị DEMO_PASSWORD lúc seed | /account |

Phiên local được chuẩn bị tại http://127.0.0.1:8080/; mật khẩu các tài khoản demo được cấu hình riêng qua DEMO_PASSWORD và không công bố trong repository. Cập nhật máy local ngày 06/11/2026: MySQL 8.4.13 đã chuyển sang `C:\ProgramData\CinePrime\MySQL` và Windows Service `CinePrimeMySQL` với Startup type **Automatic**, cổng `127.0.0.1:3307`. Sau khi bật máy, chờ service sẵn sàng rồi mở IntelliJ và Run cấu hình CinePrime Local. Xem trạng thái bằng `Get-Service CinePrimeMySQL` hoặc Services (services.msc). MySQL Workbench chỉ là ứng dụng quản lý, không cần mở để website hoạt động. Dữ liệu cũ và bản sao lưu trước khi chuyển service được giữ ngoài repository. Khi thiết lập máy/database riêng, làm theo cấu hình phía trên và chọn DEMO_PASSWORD của bạn.

Dữ liệu gồm 8 phim hư cấu, 2 rạp, mỗi rạp 2 phòng/60 ghế, lịch tương lai 7 ngày, bảng giá, 3 sản phẩm, voucher CHAOHE/TRAINGHIEM và nội dung mẫu. Staff mẫu thuộc CinePrime Riverside. Lịch mẫu cố định theo ngày seed; tạo thêm lịch trong Admin khi cần. Địa chỉ @cineprime.local không nhận email; đăng ký email thật của bạn để thử SMTP.

**Test local trước khi có MoMo:** với bộ dữ liệu demo, chọn ghế → nhập voucher `TRAINGHIEM` → Áp dụng combo / mã ưu đãi → Xác nhận vé miễn phí. Voucher giảm 100%, tối đa 3 lượt/tài khoản trong dữ liệu demo. Hệ thống phát QR/PDF và ghi provider VOUCHER, không cộng điểm vì thực trả 0. Đây là giao dịch ưu đãi miễn phí hợp lệ, không chứng minh tích hợp MoMo đã chạy. Staff vẫn áp dụng khung check-in theo giờ thật.

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

Ảnh và trailer được tải ngay trong form dùng chúng (phim, banner, combo, bài viết, ảnh đại diện) qua `/admin/media` hoặc form tài khoản; không còn trang Upload riêng. Ảnh JPEG/PNG/WEBP tối đa 5 MB, giới hạn 8000px mỗi chiều; trailer MP4 tối đa 100 MB. Server kiểm tra nội dung, đổi tên UUID; file được phục vụ qua /media. Poster demo SVG là minh họa tự tạo, không dùng thay ảnh thiếu của phim mới. Ảnh banner (banner phim trong form phim và Admin → Nội dung → Banner trang chủ) khuyến nghị 1920×640: rộng ≥ 1600px (tỉ lệ ≥ 16:9) phủ kín banner; ảnh ngang từ 560px (tỉ lệ ≥ 4:3) vẫn tải lên được kèm cảnh báo và hiện dạng khung ảnh đúng kích thước, không kéo giãn; ảnh dọc hoặc hẹp hơn bị từ chối. Phim được lưu trong Admin tự có banner phim; Admin → Nội dung → Banner trang chủ cho sắp thứ tự, bật/tắt, đặt lịch bắt đầu/kết thúc, thay ảnh riêng và thêm banner khuyến mãi. Banner chỉ hiện ảnh (kiểu khung có slide hai bên ló ra, thanh mua vé nhanh nối bên dưới); phim chưa có ảnh ngang dùng được không lên banner và Admin hiện "Thiếu ảnh ngang"; phim đã kết thúc tự rời trang chủ. Không có banner nào đang hiển thị thì trang chủ dùng phim nổi bật, rồi đến phim đang chiếu. Be Vietnam Pro dùng cho trang khách, Inter cho Admin/Staff, Noto Sans nhúng PDF; font và ZXing có license kèm trong static. Không cần CDN lúc sử dụng.

## Hướng dẫn demo ba vai trò

### Giao diện khách

Từ 13/11/2026, trang khách dùng bố cục gọn theo phác thảo của người dùng (UI-Template §3.0): header một hàng, hero có chữ + ảnh ngang + poster, dải mua vé nhanh, lưới phim 6 cột có nút thích, footer 5 cột. Đã kiểm tra trên JAR ở 1366×650, 1920×950 và 375×812. Chưa thử trên điện thoại thật.

### Giao diện điều hành Admin/Staff

Từ 12/11/2026, Admin và Staff chỉ hỗ trợ desktop (1366×768 trở lên); chỉ giao diện khách cần responsive trên điện thoại. Các phần mobile của màn điều hành đã làm trước đó vẫn còn nhưng không được bảo đảm hay nghiệm thu nữa.

Sau khi cập nhật mã, khởi động lại cấu hình **CinePrime Local** trong IntelliJ để dùng giao diện mới. Bản JAR ở `target/cineprime-1.0.0.jar`. Nghiệm thu giao diện được chạy riêng trên schema test và không thay dữ liệu ứng dụng đang dùng. Ảnh minh chứng local ở `target/admin-desktop.jpg`, `target/admin-mobile.jpg`, `target/staff-desktop.jpg`, `target/staff-mobile.jpg` (build artifacts, không đưa vào repository).

Admin và Staff dùng header điều hành riêng, không có menu mua vé/footer khách hàng. Admin có sidebar phân nhóm, nhớ nhóm mở và vị trí cuộn riêng theo tài khoản trên trình duyệt; màn nhỏ dùng drawer và Escape để đóng. Các danh sách Admin có tìm kiếm, bộ lọc và **5 dòng/trang**, với Trước/Sau; Lưu/Hủy giữ ngữ cảnh danh sách. Form có nhóm thu gọn, hiển thị lỗi cùng dữ liệu đã nhập; không giữ lại mật khẩu ban đầu sau lỗi. Giá nhập nguyên VND, giờ bảng giá nhập HH:mm (kết thúc ngày có thể 24:00). Các khóa lịch sử vẫn được kiểm tra lại tại backend.

Thể loại, Diễn viên / Đạo diễn, Thể loại của phim, Đoàn phim, Bài viết / Ưu đãi / Chính sách và Người dùng thêm và sửa bằng pop-up ngay trên danh sách (bài viết dùng pop-up rộng 2 cột, đường dẫn tự tạo từ tiêu đề). Pop-up gửi tới cùng endpoint lưu như form trang; lỗi hiện tại đúng trường và giữ nội dung đã nhập; đóng khi còn thay đổi chưa lưu sẽ hỏi lại. Không có JavaScript thì nút vẫn mở trang form cũ. Form banner gồm hai cột: chọn loại, phim/liên kết, kéo-thả ảnh, công tắc bật và lịch thu gọn ở bên trái; khung xem trước 3:1 bên phải cho biết ảnh sẽ phủ kín, nằm giữa khung hay chưa dùng được. Trang Upload riêng đã bỏ: ảnh và trailer được tải ngay trong form tương ứng.

Lịch/suất quản lý theo rạp → ngày → phim, dùng giờ kết thúc đã lưu và ghi +1 ngày khi qua nửa đêm. Phòng, Ghế/Bảo trì và Bảng giá có một rạp hiện rõ trong URL; nhớ rạp gần nhất trong session theo tài khoản, mặc định rạp hoạt động đầu tiên theo tên. Chọn phòng trước khi xem bảng ghế; sơ đồ phòng vẫn đủ ghế. Người dùng có tab User/Staff/Admin. Không thêm role mới; Staff thu tiền mặt tại quầy theo ca (xem bên dưới).

Dashboard chia trang độc lập 5 dòng cho đơn mới (có tên khách), suất sắp nhận khách, đối soát, lần thanh toán lỗi, nhóm phim, lấp đầy và bảng biểu đồ. Phân trang tài chính đọc cùng snapshot, không thay KPI/mốc chốt/CSV hoặc tạo thêm snapshot. Vận hành tự cập nhật 30 giây; khi xem trang sau giữ dòng đang đọc và báo Có cập nhật mới, bấm Làm mới khối để về đầu. Cursor vận hành hết hạn sau 15 phút cần làm mới khối. Các lựa chọn dropdown, sơ đồ ghế và CSV toàn bộ phạm vi không bị cắt thành 5 dòng. **MoMo được người dùng chuyển sang đợt sau.** Từ 12/11/2026 có bán vé tại quầy bằng tiền mặt; Tổng quan tách doanh thu Online/Quầy và Admin xem ca ở **Kinh doanh → Ca bán vé tại quầy** (`/admin/shifts`).

Dashboard mặc định Hôm nay, cho chọn kỳ/ngày/rạp (bao gồm rạp đã ngừng để xem lịch sử). Doanh thu là tổng đơn PAID theo paidAt, gồm vé + combo − giảm giá; đơn miễn phí có đơn/vé nhưng 0đ. Vé bán tính theo ngày thanh toán và ghế vật lý. “Giá trị đơn theo phim” chưa phân bổ riêng tiền vé/combo. “Lấp đầy” dùng ngày suất chiếu, vé VALID + USED chia ghế hoạt động, không gồm VOID. Tổng khách/phim ở cuối là danh mục hiện tại toàn hệ thống.

KPI/biểu đồ/CSV doanh thu sử dụng chung một bản chụp, giữ tối đa 2 bản trong session trong 15 phút. Tải CSV dùng đúng mốc đang xem, không tự cộng các thanh toán phát sinh sau đó. Bản cũ hết hạn hoặc bị thay bởi tab báo cáo thứ ba sẽ yêu cầu làm mới. Báo cáo lấy khoảng [đầu kỳ, mốc chốt), VND nguyên đồng. Kỳ hiện tại so cùng tiến độ kỳ trước; kỳ đã kết thúc so toàn kỳ trước. Mẫu trước bằng 0 hoặc kỳ tương lai hiển thị “—”/“Chưa bắt đầu”. File “Nhật ký giao dịch” cũ gồm mọi thời gian/trạng thái và có thể nhiều dòng/đơn, nên không cộng cột tiền để đối chiếu doanh thu.

Khối vận hành Admin tự đọc lại mỗi 30 giây theo rạp và thời gian hiện tại; không làm đổi bản chụp tài chính. Staff tự chọn suất được gợi ý, quét QR hoặc nhập mã, kiểm tra thông tin rồi xác nhận từng vé. Kết quả được giữ đến khi bấm “Quét vé tiếp”; mã đơn nhiều vé vẫn xác nhận riêng từng ghế. Camera không cần bật lại sau mỗi vé. Thống kê/bảng vé đọc server mỗi 5 giây, dừng khi tab ẩn. Nếu mất phản hồi sau xác nhận, phải kiểm tra lại trạng thái, không tự gửi lại check-in. Camera thực cần thiết bị và quyền localhost/HTTPS; chưa coi kiểm tra giao diện là nghiệm thu camera thiết bị thật.

1. **Admin:** tạo rạp → phòng → chọn mẫu phòng rồi tô loại ghế/ghế đôi/bảo trì/lối đi (có hoàn tác, xem 3D) → thêm giá → phim/thể loại/đoàn phim → suất DRAFT/OPEN. Xem lịch theo ngày, voucher usage, đơn/đối soát, CSV và nhật ký.
2. **Customer:** chọn phim/rạp/ngày/suất → chọn ghế → Tiếp tục → combo/voucher → Cập nhật đơn → MoMo sandbox → xem QR/PDF. Thiếu khóa thì hiển thị rõ chưa cấu hình, không tự thanh toán. Một QR tương ứng một ghế; ghế đôi hai QR.
3. **Staff:** chọn ngày và đúng suất tại rạp phụ trách → quét QR hoặc nhập T-…/CP-… → kiểm tra thông tin → xác nhận. Camera cần localhost hoặc HTTPS và quyền từ người dùng. Có thể dùng mã thủ công khi không có camera.
4. **Staff bán tại quầy** (tab **Bán vé tại quầy**, `/staff/counter`): mở ca với tiền đầu ca → chọn suất → chọn ghế → Giữ ghế 5 phút → thêm combo → (tùy chọn) tìm tài khoản khách bằng đúng SĐT/email để tích điểm → nhập tiền khách đưa (phím mệnh giá cộng dồn từng tờ) → Thu tiền & phát vé → In vé (khổ 80mm, mỗi vé một QR + hóa đơn) → Check-in luôn nếu khách vào ngay. Cuối ca bấm Đóng ca, nhập tiền đếm được; lệch tiền phải ghi chú, sau đó in phiếu tổng kết.

Ghế giữ 5 phút, tối đa 8 ghế/đơn, một lượt giữ đang hoạt động/người. Dừng nhận hold online trước suất 20 phút để đủ 5 phút trước giờ dừng bán; quầy nhận giữ ghế đến 5 phút trước giờ chiếu và thu tiền đến trước giờ chiếu. Reload/đổi combo không tăng hạn. Ghế đôi luôn chọn nguyên cặp. Check-in từ -60 đến +30 phút, dùng một lần. Khách không hủy vé đã trả tiền; riêng đơn tiền mặt tại quầy, Staff đã bán có thể hủy kèm lý do trong 15 phút sau khi bán nếu chưa ai check-in, ca còn mở và chưa tới giờ chiếu (vé vô hiệu, ghế bán lại được, tiền mặt hoàn tại quầy, điểm bị trừ). Dữ liệu giao dịch được giữ lịch sử.

## Kiểm thử

Quy trình database (từ 12/11/2026): chạy đủ bộ test và thử giao diện trên `cineprime_test` trước. Khi bộ test MySQL qua (0 failures/errors/skipped), thay đổi được áp dụng luôn vào database chuẩn `cineprime` (migration Flyway mới, dữ liệu cấu hình cần thiết); test thất bại thì không đụng `cineprime`. Không reset dữ liệu `cineprime`; sao lưu bằng mysqldump trước migration có DDL hoặc sửa dữ liệu hàng loạt.

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

Integration test xóa dữ liệu các bảng trong **schema test** trước mỗi ca; từ chối tên schema khác cineprime_test. Không dùng database ứng dụng cho test. Kiểm tra báo cáo `target/surefire-reports`; kết quả đầy đủ hiện tại (12/11/2026, sau bán vé tại quầy): **135 tests, 0 failures, 0 errors, 0 skipped** trên MySQL 8.4.13, gồm 105 MySQL/HTTP và 30 unit/render/Excel/HMAC; log `target/counter-full.log`. Số liệu phía sau câu này là các mốc cũ. Transaction ghi giữ READ COMMITTED; báo cáo dùng REPEATABLE_READ riêng. Log đầy đủ gần nhất `target/week-table-full.log`, BUILD SUCCESS ngày 11/11/2026 lúc 01:21; lượt trước đó là `target/day-board-full.log` ngày 10/11. Không cộng các lượt chạy lại hoặc mô phỏng vào số test. Các log và số liệu ở mốc cũ trong Plan.md giữ giá trị lịch sử; XML surefire phản ánh lượt gần nhất của từng suite.

Nghiệm thu liên kết luồng: trên JAR/schema test đã tạo rạp → phòng → sơ đồ theo hàng → giá → suất; thêm thể loại ở tab khác và làm mới giữ dữ liệu, lưu phim nhiều thể loại rồi User tự lọc đúng. Couple 95.001đ/ghế cho tổng cả cặp 190.002đ; hủy đơn chờ từ Admin trả toàn bộ ghế, User thấy Đã hủy. Các ca backend kiểm tra race/phiên bản cũ/khóa/CSRF/TTL/đối soát và giới hạn query. Màn mới đã xem tại 1366×768, 390×844 và 620/850/1090, không tràn ngang trang; ghế mobile 44×44px, Escape trả focus. Ảnh dữ liệu giả tại `target/linked-flow-*.jpg`; dữ liệu giả không nhập vào cineprime. Mọi tình huống mất mạng/chậm và camera thiết bị thật chưa nghiệm thu lại trong đợt này.

Nghiệm thu bổ sung phim/lịch: JPG/PNG/WEBP và chặn loại/giới hạn ảnh ở backend; YouTube host/ID, ẩn–giữ ghế đồng thời, xóa phim chưa dùng, lịch trùng ngoài trang đầu/qua ngày và hai Admin cùng thêm, giữ endsAt có giao dịch, đơn miễn phí và Staff chưa phân công. Trình duyệt JAR đã thử upload WEBP/PNG, file lỗi khóa Lưu và giữ ảnh cũ, Ẩn/Hiện, hero/trailer, giữ dữ liệu khi lịch trùng và quay về đúng rạp/ngày sau lưu. Khối ba đơn fixture cao 263px ở 1366×768. Ảnh tại `target/movie-fix-*.jpg` (dữ liệu kiểm thử, không commit). YouTube tải được thông tin/điều khiển player, chưa kiểm chứng phát liên tục/âm thanh mọi video; camera vật lý và mọi tình huống mạng/upload chậm chưa nghiệm thu đầy đủ. Trạng thái trống website sau reset đã xem trên database ứng dụng.

Kiểm tra JavaScript bằng Node VM với DOM/fetch giả lập cũng qua: form đúng loại, khóa Lưu khi đang upload/thay ảnh lỗi, phản hồi cũ không ghi đè ảnh mới và Bỏ lựa chọn khôi phục ảnh gốc (`target/movie-fix-js-behavior.log`). Kiểm tra này tách khỏi số tests Maven và không thay kiểm tra mạng thực tế trên thiết bị.

Phạm vi test: cạnh tranh ghế/voucher/check-in; atomic hold; TTL biên 300s; không gia hạn; couple; snapshot giá; callback sai tiền/chữ ký/trùng/đảo/thành công muộn; đơn canceled; free voucher; reset token; overlap/đệm/layout; role/ownership/CSRF/khóa phiên; render trang Customer/Admin/Staff; PDF tiếng Việt và QR decode. Không dùng H2.

Các ca mới bảo vệ tổng doanh thu = tổng biểu đồ/nhóm phim/CSV; tiền combo và voucher; vé miễn phí; giao dịch thử lại/thành công muộn; biên nửa đêm Việt Nam và mốc chốt không bao gồm; snapshot không đổi khi có thanh toán mới, khác session, hết hạn và tab thứ ba; so sánh tháng ngắn/năm nhuận; thống kê loại vé VOID không làm giảm tiền PAID; biên giờ check-in, đọc lại sau xác nhận, CSRF/phạm vi rạp/phiên hết hạn; form giữ VND, HH:mm/24:00 và giây của suất đã khóa; tìm/lọc/phân trang và upload runtime. Luồng Staff trên trình duyệt JAR đã thử mã sai, Enter chỉ tra cứu, xác nhận thủ công, giữ kết quả, cập nhật thống kê và tra lại vé USED không còn nút xác nhận. Không coi các ca này là thử camera vật lý hoặc giao dịch MoMo thực.

Nghiệm thu vòng đời phim và UX Admin ngày 11/11/2026: **124 tests, 0 failures/errors/skipped** (96 MySQL/HTTP). V4 đã chạy thử trên bản sao dữ liệu `cineprime` khôi phục vào `cineprime_test` (kèm 5 phim trường hợp khó): ngày điền đúng dự tính, không suất nào nằm ngoài khoảng chiếu, app khởi động và trang công khai trả 200. Trên JAR/`cineprime_test` cổng 8081 (1440×900, 375×812) đã thử form phim mới (thêm, sửa, lỗi server tại ô), Tổng quan, sidebar thu gọn, danh sách phim và menu "⋯". Chưa thử upload ảnh thật qua form mới và chưa migrate `cineprime`. Chi tiết trong Plan.md mục 8.

Nghiệm thu đợt Admin gọn ngày 06/11/2026: 0/1/5/6/11 dòng, bộ lọc trước phân trang, ba role, cursor có đơn mới chen vào, giờ qua ngày từ endsAt đã lưu, session/snapshot/CSV và số query đều đã kiểm tra trên MySQL riêng. Trên executable JAR/schema test, đã thử chuyển trang độc lập; thêm đơn mới khi đang ở trang 2 vẫn giữ dòng/focus, báo Có cập nhật mới và giữ nguyên KPI/mã CSV. Sidebar giữ nhóm/vị trí cuộn qua nhiều chức năng, hai Admin có tùy chọn riêng; mobile Escape trả focus về nút menu. Phòng/ghế/giá/lịch đúng rạp, lọc phim và đường quay về trang sửa hoạt động. Màn Phim có 5 dòng cùng phân trang trong viewport 1366×768; 390×844 và breakpoint 620/850/1090 không tràn ngang trang trong các lượt đã xem. Staff và Customer kiểm tra lại không nhận CSS/JS Admin. Ảnh QA trong target/admin-compact-*.jpg (dữ liệu kiểm thử, không commit).

Nghiệm thu bán vé tại quầy ngày 12/11/2026 trên JAR/`cineprime_test` cổng 8081 (dữ liệu demo, 1366×768 và 390×844): mở ca, giữ 2 ghế VIP, combo, gắn khách theo email, thu 300.000 ₫ thối 55.000 ₫, in vé tiếng Việt có QR, hủy qua dialog (két và ghế trả lại), bán vãng lai + Check-in luôn, đóng ca lệch phải có ghi chú, phiếu tổng kết; Admin xem ca, chi tiết đơn quầy/vãng lai và doanh thu tách kênh. Chưa thử máy in nhiệt thật và quét vé in bằng camera thật. Migration **V7** đổi ràng buộc vé/điểm và cho phép đơn không có tài khoản; nó tự chạy khi khởi động lại CinePrime Local trên `cineprime` (chưa chạy trên database đó trong đợt này). Chi tiết ở Plan.md mục 8.

JAR mới tại target/cineprime-1.0.0.jar. Preview 8081 đã dừng sau nghiệm thu; ứng dụng 8080 và database cineprime không bị reset/sửa để thử UI. Khởi động lại cấu hình CinePrime Local trong IntelliJ để nạp mã và template mới.

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

Chưa triển khai Google Login, VNPay, hoàn tiền, đổi điểm và gợi ý phim. Các màn quản trị dùng form/bảng chung; sơ đồ ghế sửa bằng cách tô ghế (không kéo thả di chuyển ghế); chưa có quản lý kho nguyên liệu. Xem 3D là hình minh họa dựng từ hàng/cột, không phải kích thước thật của phòng. Tài liệu/hình tham khảo Galaxy không được dùng để giả mạo thương hiệu hoặc sao chép dữ liệu thực.

## Xếp suất chiếu trên bảng cả tuần

Trong Admin → Suất chiếu, Lịch theo ngày hoặc Thiết lập phòng, chọn **Thêm suất chiếu**. Mọi điểm thêm mở `/admin/showtimes/new` và giữ ngữ cảnh rạp/phòng/phim/ngày. `/admin/showtimes/batch` cũ chuyển hướng, giữ cả mã tra cứu kết quả. Đường tạo đơn cũ bị chặn tại controller và service; sửa suất có ID vẫn dùng editor hiện có.

1. Chọn rạp và tuần ở thanh trên. Màn mặc định **Cả tuần** có danh sách phòng phía trên bảng; mỗi nút phòng ghi số suất, số suất mới và số lỗi của phòng trong tuần. Chọn một phòng để xem lịch riêng của phòng đó: Thứ 2 đến Chủ nhật mỗi ngày một cột. Mỗi suất là một nút ghi giờ bắt đầu – kết thúc và tên phim: xanh là đang bán vé, xám là nháp, cam là mới chưa lưu, đỏ là trùng lịch cần sửa. Đầu cột ghi số suất, số suất mới và số lỗi của ngày. Dải **Phim của phòng này** đếm suất theo phim; bấm một phim để làm nổi các suất của phim đó.
2. Bấm **+ Thêm** ở ngày muốn chiếu. Hộp **Thêm suất chiếu** điền sẵn phòng và ngày, gợi ý giờ trống sớm nhất vừa độ dài phim (hôm nay thì không sớm hơn giờ hiện tại). Chọn phim; tick thêm các ngày muốn lặp lại hoặc bấm Cả tuần; chọn **Một suất** hoặc **Nhiều suất nối tiếp** (các suất sát nhau từ giờ bắt đầu, suất cuối bắt đầu không muộn hơn giờ đã nhập, tự nhảy qua suất đang có rồi xếp tiếp). Dòng báo trước liệt kê từng suất sẽ thêm theo ngày và những suất không thêm kèm lý do; nút chính ghi đúng số suất. Ngôn ngữ, định dạng, phụ thu nằm trong mục thu gọn của hộp. Bấm thêm thì suất vào bảng với màu cam, chưa ghi database.
3. Bấm một suất cam để sửa phim/phòng/ngày/giờ lẻ từng phút/ngôn ngữ/định dạng/phụ thu, nút × để bỏ. Bấm suất đã lưu để xem thông tin và mở trang sửa riêng. Lỗi do server trả về ghi ngay trên nút suất, trên đầu cột và thanh dưới; **Tới suất cần sửa** đưa đến suất lỗi đầu tiên. **Xem danh sách** mở danh sách suất mới năm dòng/trang, có nguồn Excel và bộ lọc chỉ dòng lỗi.
4. **Theo giờ** (nút trên thanh, hoặc bấm tên ngày ở đầu cột) mở bảng một ngày, mỗi phòng một cột theo trục giờ **Hiển thị từ … đến** (mặc định 09:00–23:00, tự mở rộng). Ở đây chọn phim trong danh sách bên trái rồi bấm vào cột phòng: bóng xem trước ghi giờ bắt đầu → hết phim → hết dọn phòng theo bước 5 phút, tự dính sát suất trước hoặc lùi cho vừa khoảng trống. Kéo suất cam để dời giờ hoặc sang phòng khác; phím Delete bỏ suất. **+ Thêm suất** mở cùng hộp ở bước 2; **Chép ngày** chép các suất mới của ngày đang xem sang ngày khác trong tuần, suất trùng lịch hoặc đã qua giờ không được thêm và được liệt kê.
5. Menu **Excel** có Xuất lịch tuần, Nhập Excel và Tải mẫu trống. Xuất hoặc tải mẫu tạo Excel v3 bốn sheet **Hướng dẫn – Lịch hiện tại – Đăng ký suất mới – Danh mục**. Phần đăng ký có Phim/Phòng/bảy cột ngày cụ thể/Ngôn ngữ/Định dạng/Phụ thu; một ô nhận nhiều giờ, ví dụ `09:00; 14:30; 19:00`. Cũng chấp nhận dấu phẩy hoặc xuống dòng; một giờ có thể dùng ô giờ Excel. Không đổi ngày trên tiêu đề. Mặc định mở form tại A2, có 500 dòng trống và dropdown.
6. **Lịch hiện tại** chỉ để tham khảo, được lọc/cố định tiêu đề/bảo vệ nhưng vẫn chọn và copy được; có kết thúc/hết vệ sinh hiển thị cả ngày và trạng thái. Cột tham khảo khác form v3: chỉ sao chép giờ cần dùng vào đúng ô ngày, không sao chép nguyên dòng. Import chỉ bổ sung phần đăng ký vào danh sách đang chọn, giữ file/sheet/dòng/ô/thứ tự giờ. Không tạo lại hoặc cập nhật các suất tham khảo.
7. `.xlsx` tối đa 5 MB, toàn đợt tối đa **500 suất sau tách giờ**; lịch tham khảo không tính vào 500. Bỏ dòng hoàn toàn trống, giữ dòng thiếu hoặc giờ sai để sửa. Sai rạp/tuần, file lỗi hoặc vượt giới hạn không bổ sung file và không làm mất đợt cũ. Giữ parser v1/v2: v1 coi mọi dòng Lịch chiếu là suất mới, v2 đọc phần đăng ký từng suất; không tự bỏ trùng. Mẫu cũ phải có ngày thuộc tuần đang xếp. Ngôn ngữ trống là Phụ đề, định dạng theo phòng, phụ thu là số nguyên VND không âm, mặc định 0. Không dùng công thức.
8. Chọn **Bán vé ngay sau khi tạo** (mặc định, suất Mở bán) hoặc **Lưu nháp · chưa bán vé** (suất Nháp, khách chưa đặt được) rồi bấm **Tạo N suất**. Hệ thống kiểm tra lại toàn đợt và mở một hộp xác nhận tóm tắt theo ngày/phòng, có thể mở xem từng suất; chỉ **Xác nhận tạo** mới ghi dữ liệu. Một lỗi chặn cả đợt: hộp xác nhận nêu số suất cần sửa và khóa nút xác nhận. Chỉ tạo suất tương lai, tính thời lượng riêng từng phim và 15 phút dọn phòng; Nháp/Mở bán chiếm lịch, Đã hủy không chiếm. Backend kiểm tra lại dưới khóa; dữ liệu đã đổi thì phải xem lại.
9. Mỗi đợt thuộc một rạp/một tuần, tối đa 500 suất. Đổi tuần/rạp hoặc rời trang khi còn suất chưa lưu có cảnh báo; không tự lưu nháp. Khi chưa rõ kết quả ghi, giữ trang và dùng **Kiểm tra kết quả lưu** hoặc **Gửi lại cùng yêu cầu**; không lập đợt khác. Mã trên URL cho phép tra lại sau khi tải lại trang.
10. Màn hẹp (≤ 850px) xếp bảy ngày của phòng đang chọn thành bảy hàng ở bảng Cả tuần; ở ≤ 620px chế độ Theo giờ xem một phòng mỗi lần, nút suất, nút × và nút thêm có vùng chạm 44px. Kéo chỉ dùng chuột; trên màn chạm đổi giờ/phòng qua hộp sửa.

API `/api/admin/showtime-batches/calendar` nhận `cinemaId`, `weekStart` là Thứ Hai, `roomId` tùy chọn để đọc cả rạp hoặc một phòng; `date` vẫn là adapter một ngày. Sự kiện/vệ sinh tại biên tuần được tải đầy đủ, không bị cắt theo trang/bộ lọc phim. Template/export nhận `weekStart` để xuất v3; lời gọi export `from/to` cũ giữ v2 và giới hạn 31 ngày/20.000 suất tham khảo. Giữ `Input/Line`, fingerprint, transaction, requestKey, source `MIXED`, quyền Admin/session/CSRF và khóa bảo vệ vé/giữ ghế; không thêm migration/thư viện.

JAR: `target/cineprime-1.0.0.jar`. Bằng chứng của bảng cả tuần: `target/week-table-full.log` (122 kiểm thử); kết quả chi tiết và lịch sử các bản trước ở Plan.md. Chưa có dời/hủy suất đã lưu ngay trên bảng, mở bán nhiều suất Nháp, xếp lịch tối ưu hoặc tự lưu nháp. Giới hạn nghiệm thu ghi ở phần Hiện trạng và Plan.md.
