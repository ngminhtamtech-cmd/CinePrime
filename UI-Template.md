# UI-TEMPLATE — CINEPRIME

> **Nguồn cảm hứng:** [Galaxy Cinema](https://www.galaxycine.vn/) — tham khảo cấu trúc trải nghiệm đặt vé và tổ chức nội dung, kiểm tra ngày 05/11/2026.  
> **Phạm vi:** Bộ thiết kế giao diện tham khảo cho website **CinePrime – Hệ thống đặt vé xem phim**. Không sử dụng logo, font độc quyền, mã nguồn hoặc dữ liệu phim thực của Galaxy Cinema.  
> **Trạng thái:** Có bản demo HTML/CSS/JS tương tác; **chưa có backend hoặc thanh toán thật**.

## 1. Mục tiêu giao diện

Thiết kế website đặt vé xem phim rõ ràng, chuyên nghiệp, ưu tiên **đặt vé nhanh**, giúp khách hàng đi theo luồng **Chọn phim → Chọn rạp → Chọn ngày → Chọn suất → Chọn ghế → Thanh toán → Nhận vé**. Áp dụng phong cách điện ảnh hiện đại: nền trắng làm nội dung chính, thanh điều hướng xanh đen, xanh dương nhận diện và cam cho nút hành động.

### Nguyên tắc

1. CTA đặt vé là ưu tiên số 1, dùng màu cam nhất quán.
2. Luồng đặt vé có thứ tự rõ và xác nhận lại thông tin cuối cùng.
3. Card phim tập trung poster; tên, tuổi, điểm, thể loại, thời lượng dễ đọc.
4. Không dùng màu sắc gây rối; tối ưu khoảng trắng và lưới 12 cột.
5. Giao diện khách responsive, dễ dùng trên điện thoại, tránh thao tác hover-only. Admin/Staff chỉ cần desktop (từ 12/11/2026).
6. Đáp ứng trạng thái tải, rỗng, lỗi, không có suất và ghế không còn.

## 2. Cấu trúc website tham khảo

### 2.1. Khung trang chủ

```text
┌─────────────────────────────────────────────────────────────────┐
│ Top Bar: thông báo chương trình thành viên / hỗ trợ            │
├─────────────────────────────────────────────────────────────────┤
│ Logo CinePrime     Tìm kiếm                    Vé   Tài khoản   │
├─────────────────────────────────────────────────────────────────┤
│ MUA VÉ | PHIM | LỊCH CHIẾU | KHUYẾN MÃI | GÓC ĐIỆN ẢNH | RẠP │
├─────────────────────────────────────────────────────────────────┤
│ Banner phim lớn / Hero Carousel            [CTA Đặt vé]       │
├─────────────────────────────────────────────────────────────────┤
│ MUA VÉ NHANH | Chọn phim | Chọn rạp | Chọn ngày | Suất | [MUA]│
├─────────────────────────────────────────────────────────────────┤
│ PHIM [Đang chiếu] [Sắp chiếu] [IMAX]         [Chọn khu vực]     │
│ [Poster 1] [Poster 2] [Poster 3] [Poster 4]                     │
│ Tên phim + tuổi + rating + thời lượng     [Xem thêm]           │
├─────────────────────────────────────────────────────────────────┤
│ LỊCH CHIẾU  [Ngày 1] [Ngày 2] ... [Ngày 7]                     │
│ Từng phim + các suất giờ đặt vé                             │
├─────────────────────────────────────────────────────────────────┤
│ Lợi ích đặt vé / ưu đãi thành viên                              │
├─────────────────────────────────────────────────────────────────┤
│ Khuyến mãi: banner 3 cột                                       │
├─────────────────────────────────────────────────────────────────┤
│ Góc điện ảnh: tin tức / cẩm nang / bình luận phim              │
├─────────────────────────────────────────────────────────────────┤
│ Footer: Giới thiệu - Hỗ trợ - Liên hệ - Điều khoản             │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2. Các màn hình cần thiết cho dự án hoàn chỉnh

| Màn hình | Thành phần | Chức năng | Ưu tiên |
|---|---|---|---|
| Trang chủ | Header, hero, mua vé nhanh, phim, lịch chiếu, khuyến mãi, footer | Khám phá và bắt đầu mua vé | P0 |
| Danh sách phim | Bộ lọc Đang chiếu/Sắp chiếu, tìm kiếm, card | Tìm phim | P0 |
| Chi tiết phim | Poster, tóm tắt, trailer, thể loại, thời lượng, tuổi, suất chiếu | Chọn lịch xem | P0 |
| Danh sách rạp | Vị trí, địa chỉ, suất chiếu, giá tham khảo | Chọn rạp | P0 |
| Đặt vé – Chọn suất | Ngày, rạp, phòng, loại định dạng, các giờ | Chọn suất | P0 |
| Đặt vé – Chọn ghế | Sơ đồ ghế, chú giải, giá từng loại, tổng tiền | Chọn ghế | P0 |
| Đặt vé – Combo | Combo bắp/nước, số lượng, giá | Upsell | P1 |
| Thanh toán | Đơn hàng, mã giảm giá, phương thức thanh toán | Xác nhận thanh toán | P0 |
| Xác nhận vé | Mã đơn, QR vé, phim/rạp/giờ/ghế | Nhận vé | P0 |
| Tài khoản | Đăng ký, đăng nhập, quên mật khẩu | Xác thực | P0 |
| Hồ sơ của tôi | Vé đã mua, lịch sử, đổi mật khẩu | Quản lý cá nhân | P1 |
| Khuyến mãi | Danh sách banner, chi tiết ưu đãi | Marketing | P1 |
| Admin Dashboard | Phim, rạp, phòng, ghế, suất, giá, đơn hàng, user, báo cáo | Vận hành | P0 |
| Staff Dashboard | Soát vé QR, kiểm tra suất, xác nhận check-in | Vận hành tại rạp | P1 |

> **Demo đi kèm hiện hỗ trợ:** giao diện trang chủ, tab phim, modal chi tiết, lịch chiếu, chọn ghế, 2 bước thanh toán minh họa, form tài khoản. Các màn hình khác là yêu cầu thiết kế cho bản triển khai Spring Boot.

## 3. Design System

### 3.0. Giao diện khách (từ 12/11/2026)

Trang khách dùng `static/css/site.css`; token khai báo ở `:root` đầu file. Bảng 3.1–3.3 bên dưới vẫn áp dụng cho Admin/Staff (`app.css`, `operations.css`).

| Token | Giá trị | Vai trò |
|---|---|---|
| `--bg` / `--bg-soft` | `#FFFFFF` / `#F5F6F8` | Nền trang / nền phân khu |
| `--ink` / `--muted` | `#14171C` / `#5B6472` | Chữ chính / chữ phụ |
| `--line` / `--line-strong` | `#E3E6EA` / `#CFD4DA` | Viền thẻ / viền ô nhập, nút phụ |
| `--navy` | `#111827` | Thanh menu, footer, dải thành viên, trạng thái đang chọn |
| `--accent` / `--accent-hover` | `#F47B20` / `#DD6A12` | Màu nhấn duy nhất: nút hành động chính, ghế đang chọn |
| `--accent-ink` | `#B4530B` | Chữ cam trên nền trắng (đủ tương phản) |

- Font: **Be Vietnam Pro** (OFL, tự host trong `static/fonts`, 400/500/600/700, tách bộ ký tự Latin / Latin mở rộng / tiếng Việt) → `Segoe UI` → `Arial`.
- Cỡ chữ: nội dung 16px/1.6; chú thích 14px; tên phim 17px/600; tiêu đề khối 28px/700 (mobile 22px); tiêu đề trang 36px/700 (mobile 28px); tiêu đề banner `clamp(32px, 4vw, 56px)`/700; nút 15px/600. Chữ thường theo câu, không viết hoa toàn bộ.
- Hình khối: bo góc 8px; viền 1px thay đổ bóng (chỉ hộp thoại có bóng); không gradient trang trí (chỉ lớp tối dưới chữ của banner); không ký tự trang trí.
- Khoảng cách: khối 72px (850px: 56px, 620px: 48px); vùng chạm tối thiểu 44px.
- Ảnh: chỉ ảnh thật do Admin tải lên. Ưu đãi/bài viết không có ảnh hiển thị thẻ chữ (loại, tiêu đề, trích đoạn, ngày); ảnh thẻ khuyến nghị 1200×675 (16:9).

**Hero (banner trang chủ):** quản lý tại Admin → Nội dung → Banner trang chủ. Chỉ hiện ảnh (chữ nằm sẵn trong ảnh), cả slide là link tới trang phim hoặc liên kết khuyến mãi. Slide giữa rộng tối đa 1360px (luôn chừa hai bên), tỉ lệ 3:1, bo góc trên; slide trước/sau ló ra hai bên, bấm vào thì chuyển tới. Mũi tên tròn sát mép (ẩn từ 850px), chấm vị trí + nút tạm dừng trong ảnh ở giữa phía dưới. Thanh mua vé nhanh nối liền mép dưới, rộng bằng slide: 4 bước đánh số cam (phim → rạp → ngày → suất) và nút "Mua vé nhanh"; mobile xếp dọc. Phim chưa có ảnh ngang dùng được không lên banner (Admin báo "Thiếu ảnh ngang"). Ảnh banner khuyến nghị 1920×640, chia ba mức và không bao giờ phóng to ảnh: rộng ≥ 1600px và tỉ lệ ≥ 16:9 thì phủ kín hero; ảnh ngang nhỏ hơn (rộng ≥ 560px, tỉ lệ ≥ 4:3) đặt giữa slide trên nền tối, đúng kích thước (tải lên được nhưng có cảnh báo). Ảnh dọc hoặc hẹp hơn 560px không nhận làm banner.

**Trang chi tiết phim:** dải đen đầu trang (cao 240–480px) đặt ảnh ngang của phim ở giữa, không phóng to, nút play tròn mở trailer; poster viền trắng đè lên mép dải; bên phải poster: breadcrumb, tên phim + nhãn tuổi, thời lượng/ngày khởi chiếu/điểm có icon, Thể loại/Đạo diễn/Diễn viên dạng chip, khoảng chiếu, nút hành động. Dưới: Nội dung phim, Lịch chiếu, Khán giả chia sẻ. Cột phải 320px "Phim đang chiếu" (thẻ ảnh ngang 16:9, điểm, nhãn tuổi); dưới 1090px cột này xuống cuối trang. Phim chưa có ảnh ngang thì không có dải đen.

Tự chuyển 5–10 giây (Admin chỉnh, mặc định 6), có chấm vị trí, mũi tên, nút tạm dừng, vuốt trên mobile; dừng khi rê chuột, khi focus bằng bàn phím, khi tab ẩn và khi hệ điều hành bật giảm chuyển động. Mobile: ảnh ở trên, chữ và nút ở dưới.

### 3.1. Bảng màu (design tokens)

| Token | Giá trị HEX | Vai trò |
|---|---|---|
| `--blue` | `#1459A5` | Nhận diện, tab active, liên kết |
| `--blue-2` | `#1A68BA` | Hover/secondary blue |
| `--orange` | `#F47B20` | CTA đặt vé, điểm nhấn, ghế đã chọn |
| `--orange-hover` | `#DC6811` | CTA hover |
| `--navy` | `#111827` | Menu/header tối |
| `--ink` | `#1E293B` | Chữ nội dung chính |
| `--muted` | `#788395` | Chữ hỗ trợ |
| `--line` | `#E8EDF3` | Viền/divider |
| `--soft` | `#F6F8FB` | Nền phân khu |
| `--surface` | `#FFFFFF` | Card/form |
| `--success` | `#279568` | Thành công |
| `--danger` | `#E34D46` | Mức phân loại T18 / lỗi |

*Không nên dùng đỏ và vàng làm gam chủ đạo. Tỷ lệ khuyến nghị:* nền trắng/xám nhạt 70–75%; xanh đen/xanh dương 20–25%; cam 5–10% (CTA, trạng thái chọn).

### 3.2. Typography

- Font: `Inter` → `Segoe UI` → `Arial` → `sans-serif`.
- Hero H1: 40–63 px, weight 800–850.
- H2 section: 22–25 px, weight 800.
- Card title: 13–16 px, weight 800.
- Body: 13–15 px, line-height 1.5–1.7.
- Metadata: 10–12 px, màu muted.
- Không sử dụng quá 3 kiểu weight trong cùng thành phần.

### 3.3. Spacing, grid và responsive

Phạm vi (12/11/2026): các quy tắc mobile/tablet/breakpoint dưới đây chỉ áp dụng cho giao diện khách. Màn Admin/Staff thiết kế và nghiệm thu cho desktop (1366×768 trở lên).

- Content max-width **1232px**, padding hai bên **24px** desktop / **16px** mobile.
- Grid phim: desktop **4 cột**, tablet **3 cột**, mobile **2 cột**.
- Poster: tỷ lệ **2:3**, bo góc **9px**.
- Section spacing: **68px** desktop / **50px** mobile.
- Khoảng cách card: ngang **21px**, dọc **29px**.
- Shadow: nhẹ, tập trung banner mua vé nhanh và modal.
- Breakpoints: `1090`, `850`, `620` px.
- Nút: bán kính **7px**, mục tiêu chạm trên điện thoại gần 44px.

## 4. Component Specifications

### Header

- Topbar thông báo, thông tin hỗ trợ (có thể ẩn mobile).
- Header logo + ô tìm kiếm + vé của tôi + đăng nhập/đăng ký.
- Navigation tối: Mua vé, Phim, Lịch chiếu, Khuyến mãi, Góc điện ảnh, Rạp/Giá vé.
- Mobile menu bật/tắt được, không tràn ngang.

### Hero Carousel

- 3 banner phim, overlay tối để bảo đảm chữ trắng tương phản tốt.
- Hiển thị: nhãn phim, tựa phim lớn, mô tả ngắn, nút Đặt vé và Xem chi tiết.
- Có điều hướng trước/sau và indicator.
- Hình banner phải là ảnh được sở hữu, mua bản quyền hoặc được phép sử dụng.

### Mua vé nhanh

- Select bắt buộc: phim → rạp → ngày → suất.
- CTA cam "MUA VÉ".
- Với backend thật, các select nên phụ thuộc nhau: đổi phim có thể thay tập rạp/suất; đổi ngày phải tải lại suất.
- Lỗi validation: tập trung vào trường thiếu; không điều hướng nếu chưa đủ dữ liệu.

### Movie Card

- Poster tỷ lệ 2:3, nhãn tuổi P/T13/T16/T18, IMAX (nếu có), điểm đánh giá (nếu có thật).
- Hover overlay desktop và nút chạm rõ ràng trên mobile.
- Thông tin sau poster: tên phim, thể loại, thời lượng.
- Với phim sắp chiếu thay CTA mua vé bằng "Xem chi tiết" / "Nhắc tôi" tùy nghiệp vụ.

### Lịch chiếu

- Dải 7 ngày; ngày chọn active xanh.
- Card phim với các giờ dạng nút, chọn giờ sang chọn ghế.
- Với bản thật: lọc theo khu vực, rạp, định dạng, trạng thái còn vé; ẩn giờ đã qua.

### Seat Map

- Phòng chiếu nhìn từ trên xuống, màn hình ở đầu sơ đồ.
- Màu ghế: thường `#E3E9EF`, VIP `#D4E8FA`, đã bán `#C8CDD3`, đang chọn `#F47B20`.
- Có chú giải màu và tên ghế; giới hạn mua ví dụ 8 ghế/giao dịch (quy định thực tế theo nghiệp vụ).
- Chỗ ngồi đã bán không thể chọn; giao diện demo giả lập ghế đã bán cố định.
- Tổng tiền cập nhật theo loại ghế; giá demo: thường **85.000đ**, VIP **105.000đ**.
- Backend thật cần giữ ghế tạm thời (TTL), kiểm tra xung đột cạnh tranh và tự giải phóng nếu chưa thanh toán.

### Modal, Toast và trạng thái

- Modal đóng được bằng nút X, click overlay, phím Escape.
- Toast ngắn cho lỗi thao tác / điều kiện chưa đủ.
- Bản triển khai thêm: skeleton loading, retry, empty states, offline/timeout, session expired, ghế vừa bị người khác mua.

## 5. User Flows

### 5.1. Khách hàng

```text
Trang chủ
   ↓
Chọn phim → Xem chi tiết
   ↓
Chọn rạp → Chọn ngày → Chọn suất chiếu
   ↓
Chọn ghế (xem sơ đồ + tổng tiền)
   ↓
[Chọn combo nếu có]
   ↓
Xác nhận đơn hàng → Thanh toán
   ↓
Webhook thanh toán xác thực thành công
   ↓
Tạo vé điện tử / QR → Lịch sử vé
```

### 5.2. Quản trị viên

```text
Đăng nhập Admin → Dashboard
   ├─ CRUD phim / diễn viên / thể loại
   ├─ CRUD rạp / phòng / sơ đồ ghế
   ├─ Quản lý suất chiếu / giá vé
   ├─ Quản lý khuyến mãi / banner
   ├─ Xem đơn hàng / hoàn vé (theo quy định)
   └─ Báo cáo doanh thu / công suất phòng
```

### 5.3. Nhân viên

```text
Đăng nhập Staff → Danh sách suất hôm nay
   ├─ Quét QR vé
   ├─ Xác minh trạng thái vé và suất
   └─ Check-in một lần / thông báo vé không hợp lệ
```

## 6. Dữ liệu và trạng thái UI cần backend cung cấp

| Resource | Trường dữ liệu tối thiểu |
|---|---|
| `Movie` | id, slug, title, description, posterUrl, bannerUrl, duration, ageRating, releaseDate, status |
| `Cinema` | id, name, address, city, status |
| `Room` | id, cinemaId, name, layoutConfig, screenType |
| `Seat` | id, roomId, row, number, type, active |
| `Showtime` | id, movieId, roomId, startsAt, endsAt, language, format, status |
| `SeatPrice` | showtimeId, seatType, amount, currency |
| `SeatHold` | showtimeId, seatId, user/session, expiresAt, status |
| `Order` | id, userId, showtimeId, total, status, expiresAt |
| `OrderItem/Ticket` | id, orderId, seatId, price, qrToken, checkInStatus |
| `Payment` | id, orderId, provider, transactionRef, status, paidAt |
| `Promotion` | id, title, rules, validFrom, validTo, quota, code |
| `Article` | id, title, excerpt, coverUrl, publishedAt |

**Bảo mật / giao dịch:** không tin tổng tiền từ frontend; backend phải tính lại theo giá vé; khóa ghế bằng transaction/unique constraint; xác thực webhook và idempotency; kiểm tra quyền role trước mọi API admin/staff; QR vé nên là token khó đoán có hạn sử dụng/định danh.

## 7. Hướng triển khai Spring Boot

### Cấu trúc gợi ý

```text
src/main/resources/
  templates/
    fragments/header.html
    fragments/footer.html
    home/index.html
    movie/list.html
    movie/detail.html
    booking/select-showtime.html
    booking/select-seats.html
    booking/checkout.html
    booking/success.html
    auth/login.html
    account/tickets.html
    admin/dashboard.html
    staff/checkin.html
  static/
    css/styles.css
    js/app.js
    images/...
```

Với **Spring Boot + Thymeleaf**, nên tách `index.html` thành fragment, đưa CSS/JS vào `static`, sau đó thay dữ liệu giả bằng controller + DTO. Nếu chọn REST API + frontend tách biệt, có thể tái sử dụng CSS và thiết kế, chuyển các view thành React/Vue component.

### Endpoint gợi ý

- `GET /api/movies?status=NOW_SHOWING`
- `GET /api/movies/{id}`
- `GET /api/cinemas?city=...`
- `GET /api/showtimes?movieId=...&cinemaId=...&date=...`
- `GET /api/showtimes/{id}/seats`
- `POST /api/bookings/holds` (giữ ghế, backend kiểm tra xung đột)
- `POST /api/bookings` (chốt đơn)
- `POST /api/payments/create`
- `POST /api/payments/webhook` (xác thực chữ ký)
- `GET /api/me/tickets`
- `POST /api/staff/tickets/check-in`

## 8. Checklist hoàn thiện trước khi bảo vệ đồ án

- [ ] Mọi nút và màn hình dùng được trên desktop, mobile.
- [ ] Phân quyền 3 nhóm: Customer, Admin, Staff.
- [ ] CRUD phim, rạp, phòng, ghế, suất chiếu; có validation và soft delete phù hợp.
- [ ] Không trùng suất trong cùng phòng và không đặt trùng ghế cùng suất.
- [ ] Có giữ ghế tạm và giới hạn thời gian thanh toán.
- [ ] Có lịch sử đặt vé, trạng thái đơn và QR check-in.
- [ ] Không tin giá tiền do client gửi lên.
- [ ] Có test: chức năng đặt vé, xung đột ghế, huỷ thanh toán, quyền người dùng.
- [ ] Có data demo đẹp, screenshot, tài khoản demo phân quyền.
- [ ] Có deploy thật, README kiến trúc và hướng dẫn sử dụng.

## 9. Các file kèm theo

- `index.html`: giao diện chính.
- `styles.css`: toàn bộ style và breakpoint.
- `script.js`: tương tác frontend.
- `assets/*.svg`: bộ ảnh poster/banner minh họa tự tạo.
- `UI-Template.html`: bản độc lập (HTML chứa CSS/JS/assets), mở trực tiếp không cần giải nén.
- `README.md`: hướng dẫn và giới hạn demo.

**Lưu ý bản quyền:** tham khảo cấu trúc và phương pháp thiết kế từ Galaxy Cinema, không sử dụng thiết kế/template này để giả mạo thương hiệu Galaxy Cinema. Đổi nhận diện và dùng nội dung có quyền sử dụng trước khi phát hành thương mại.
