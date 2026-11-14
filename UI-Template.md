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

Trang khách dùng `static/css/site.css`; token khai báo ở `:root` đầu file. Admin/Staff theo mục **3.4** (phương án B, chốt 13/11/2026); bảng 3.1–3.3 là token cũ của `app.css`/`operations.css`, chỉ giữ để tra cứu cho tới khi 3.4 được triển khai.

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

**Bố cục gọn theo phác thảo (chốt 13/11/2026, đã triển khai mã):** canvas "CinePrime Admin/Staff — phác thảo", trang "Trang khách — theo phác thảo (13/11)". Mục tiêu: ở 1366×768 thấy trọn header + hero + mua vé nhanh + đầu hàng phim.

| Token mới | Giá trị | Vai trò |
|---|---|---|
| `--brand` / `--brand-soft` | `#1459A5` / `#E8F1FB` | Chữ "Cine" ở logo, liên kết "Xem tất cả →", mục menu đang mở trên mobile, ô chữ cái tài khoản |
| `--container` | `1328px` | 1280px nội dung + 2 lề 24px |
| `--header-h` | `64px` (mobile `56px`) | Header một hàng, dính đầu trang |
| `--hero-h` | `clamp(300px, 46svh, 400px)` | 300px ở laptop 1366×768, 400px ở màn lớn |
| `--section` | `56px` (≤1440 rộng hoặc ≤820 cao: `48px`; ≤850: `40px`) | Nhịp giữa các khối |

- Cỡ chữ: h1 32px (laptop 30px, mobile 28px); h2 24px (laptop/mobile 22px). Nút cam dùng **chữ đậm `--ink`** (chữ trắng trên `#F47B20` dưới 3:1).
- **Header:** một hàng: logo · Trang chủ · Phim ▾ (Đang chiếu, Sắp chiếu, Lịch chiếu) · Góc điện ảnh · Hệ thống rạp · Khuyến mãi · kính lúp (mở dòng tìm kiếm dưới header) · Đăng nhập (viền, bo 19px) + Đăng ký (xanh đen). Đã đăng nhập: nút tài khoản (chữ cái đầu + tên) mở menu Vé của tôi / Phim yêu thích / Điểm thưởng / Quản trị hoặc Soát vé / Đăng xuất. Menu xổ bằng click/Enter, đóng bằng Escape hoặc bấm ngoài. Bỏ dải chữ trên cùng và thanh menu xanh đen; thông báo (NOTICE) hiện thành dải cam nhạt mỏng, đóng được. Không có mục "Sự kiện". ≤850px: logo · nút menu; menu là tấm trắng toàn màn (ô tìm kiếm, mục 52px, nhóm Phim xổ tại chỗ, Đăng nhập/Đăng ký ở cuối), khoá cuộn nền.
- **Hero:** slide phim = cột chữ (chip định dạng/tuổi/trạng thái, tên phim, thể loại · thời lượng · ngôn ngữ có icon, mô tả 2 dòng, nút cam "Mua vé ngay"/"Đặt trước" + "Xem trailer", "Đạo diễn: …") + ảnh ngang 16:9 + poster 2:3 (poster ẩn ≤1090). Định dạng/ngôn ngữ lấy giá trị phổ biến nhất của các suất sắp tới, không có suất thì ẩn. Slide khuyến mãi vẫn chỉ ảnh, nằm giữa nền xanh đen. Một slide mỗi lúc (không ló hai bên). Điều khiển: thanh trắng giữa đáy gồm ‹ chấm · tạm dừng · "1 / 3" ›; >1440px mũi tên tròn 40px ra hai mép. ≤850: ảnh ngang trên, chữ dưới, nút chia 2 cột, vuốt để chuyển.
- **Mua vé nhanh:** dải trắng sát dưới hero, rộng bằng nội dung: 4 ô (số trong vòng tròn xanh đen, bước đang cần chọn số cam) + nút cam "Mua vé" 200px. ≤1090: ô có viền 2×2, nút cả hàng; ≤620: phim, rạp cả hàng, ngày | suất.
- **Khối phim:** tiêu đề có vạch trái 3px xanh đen + dòng phụ; tab nhỏ (đang chọn nền xanh đen); "Xem tất cả →" xanh. Lưới 6 cột (≤1280: 5, ≤1090: 4, ≤850: 3, ≤620: 2), khoảng 28/16px. Thẻ: poster 2:3, nhãn tuổi, tên 15px/600 tối đa 2 dòng, thể loại · thời lượng 13px, tim (thích; chưa đăng nhập thì dẫn tới Đăng nhập). Chuột: "Mua vé"/"Xem chi tiết" nổi trên chân poster khi rê hoặc focus; màn cảm ứng: nút chính luôn hiện dưới thẻ.
- **Footer:** nền `--bg-soft`, 5 cột: logo + mô tả · Về chúng tôi · Hỗ trợ khách hàng · Thành viên CinePrime · Liên hệ. Liên kết lấy từ nội dung ABOUT/POLICY/CONTACT đang bật (không có thì ghi "đang được cập nhật"), không ghi hotline/email giả, không icon mạng xã hội khi chưa có liên kết thật.

**Hero (banner trang chủ):** quản lý tại Admin → Nội dung → Banner trang chủ. Cả slide khuyến mãi là link tới liên kết khuyến mãi; slide phim dẫn tới trang phim (tên, ảnh, poster, nút). Phim chưa có ảnh ngang dùng được không lên banner (Admin báo "Thiếu ảnh ngang"). Ảnh banner khuyến nghị 1920×640, chia ba mức và không bao giờ phóng to ảnh: rộng ≥ 1600px và tỉ lệ ≥ 16:9 thì phủ kín khung; ảnh ngang nhỏ hơn (rộng ≥ 560px, tỉ lệ ≥ 4:3) đặt giữa khung trên nền tối, đúng kích thước (tải lên được nhưng có cảnh báo). Ảnh dọc hoặc hẹp hơn 560px không nhận làm banner. *(Bản 12/11 "chỉ ảnh, slide 3:1 ló hai bên" đã thay bằng bố cục trên.)*

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

### 3.4. Giao diện vận hành Admin/Staff — "Thẻ màu nhạt" (chốt 13/11/2026, phương án B)

> **Trạng thái:** người dùng chọn phương án B ngày 13/11/2026, thay hướng "Sổ suất chiếu" đen trắng (12/11) vì bản đó nhàm và khó quét mắt. **Đã triển khai 13/11/2026:** Admin — `static/css/admin-sheet.css` viết lại theo B (nạp sau cùng: nền `--canvas`, thẻ, chip vuông theo `tone-*`, thẻ số liệu có vạch + ô icon, sidebar hiện icon `navIcon`, nút xanh/cam); Tổng quan có ô icon và màu nghĩa cho 4 thẻ số liệu, số 0 hiện `0 đ` thay cho "—"; Đơn hàng hiện số 0 thật; nút "+ Thêm …" là nút cam (`.button.primary`), "Lọc" là nút xanh; trạng thái Đã hủy/Thất bại dùng tone `bad` (đỏ); cột mã bản ghi đổi thành `#` (hết trùng "Mã | Mã" ở Voucher); mức giảm voucher có đơn vị (`100%`, `20.000 đ`); breadcrumb có khoảng trắng. Staff — lớp màu B ở cuối `static/css/staff.css` (bố cục và luồng Soát vé/Bán quầy giữ nguyên): chip trạng thái suất và vé, thẻ kết quả soát tô nền theo kết quả, dải số thành thẻ có vạch, dải ca màu quầy, ghế VIP tím/Đôi cam, chú giải ghế thêm Thường/VIP/Đôi. Đã xem trên bản thử cổng 8084 (`cineprime_test`, 1440×900): Tổng quan, Đơn hàng, Phim, Voucher, Soát vé, Bán vé tại quầy. Chưa làm: trục giờ "Hôm nay" ở Tổng quan và Soát vé; các trình biên tập riêng (sơ đồ ghế, form phim chi tiết) chưa soát lại theo B. Nhóm Lịch chiếu đã làm lại theo B ngày 14/11/2026 (xem "Lịch chiếu" ở Màn chính). Chỉ desktop ≥ 1366×768. Skill dự án duy nhất: `/cineuxui <màn>` (`.claude/skills/cineuxui`) — quy trình phác thảo, sửa và soát UI cho cả khách lẫn vận hành. Bản phác thảo: canvas riêng tư "CinePrime Admin/Staff — phác thảo" trên claude.ai, trang "Bản 2 — Có màu" (bảng màu/chip, phương án A · B · C trên màn Đơn hàng).

Màu có nghĩa, không màu trang trí: mỗi màu gắn một ý nghĩa cố định trên mọi màn, nhìn là biết trạng thái, kênh bán, khu chức năng. Nền trang xám rất nhạt, khối là thẻ trắng viền 1px, ô số liệu có vạch màu và ô icon nhạt. Không gradient, không emoji, không bóng đổ trên thẻ.

**Nguyên tắc**

1. **Màu = nghĩa.** Dùng đúng bảng ngữ nghĩa bên dưới; không tô màu chỉ để đẹp. Mỗi chip luôn có chữ, không chỉ dựa vào màu.
2. **Thẻ trắng trên nền nhạt.** Khối là thẻ (`--card`, viền `--line`, bo 10px) trên nền `--canvas`; bảng, bộ lọc và tab của cùng một danh sách nằm chung một thẻ.
3. **Trạng thái là chip vuông nhạt.** "Đang hoạt động", "Đã thanh toán"… là ô bo 4px, nền nhạt + chữ đậm cùng tông + viền 1px. Không dùng ô 8px trơn, không pill tròn.
4. **Cam để tạo/bán, xanh để làm việc.** Nút cam (chữ `--ink`) cho hành động tạo hoặc bán, tối đa một mỗi màn; nút xanh cho lọc/xác nhận/lưu thường; liên kết, tab và mục đang chọn màu xanh.
5. **Số rõ ràng.** Chữ số tabular, cột tiền căn phải, tiền `170.000 đ`, phần trăm `100%`; giá trị 0 hiện `0` kèm dòng giải thích ngắn (không dải "—" lặp lại).
6. **Rỗng là một dòng.** Nói thẳng chuyện gì đang xảy ra và việc tiếp theo; nút chưa đủ điều kiện thì thay bằng dòng hướng dẫn, không để nút mờ.

**Token nền** (khai báo ở `:root`, không dùng hex rời trong rule)

| Token | Giá trị | Vai trò |
|---|---|---|
| `--canvas` | `#F4F6F9` | Nền vùng nội dung |
| `--card` | `#FFFFFF` | Thẻ, thanh đầu, sidebar |
| `--card-head` | `#F8F9FB` | Nền đầu bảng |
| `--ink` / `--ink-2` / `--muted` | `#14171C` / `#3D434D` / `#5B6472` | Chữ chính / chữ phụ dài / nhãn, chú thích |
| `--line` / `--line-soft` / `--line-strong` | `#E3E6EA` / `#EEF0F3` / `#CFD4DA` | Viền thẻ / kẻ dòng trong bảng / viền ô nhập, nút phụ |
| `--brand` / `--brand-soft` | `#1459A5` / `#E8F1FB` | Liên kết, tab, mục sidebar đang mở, nút xanh / nền mục đang mở |
| `--accent` / `--accent-ink` | `#F47B20` / `#A9480A` | Nút tạo/bán (chữ `--ink`), ghế đang chọn / chữ cam trên nền trắng |

**Màu ngữ nghĩa** (chip = nền / chữ / viền; vạch = vạch đầu thẻ số liệu)

| Nghĩa | Nền | Chữ | Viền | Vạch | Dùng cho |
|---|---|---|---|---|---|
| Ổn (`ok`) | `#E6F4EA` | `#1B6B3F` | `#C5E6CF` | `#1F8A4C` | Đang hoạt động · Đang chiếu · Đang hiển thị · Đã thanh toán · Đã vào · Hợp lệ |
| Thông tin (`info`) | `#E8F1FB` | `#1459A5` | `#C9DDF4` | `#1459A5` | Sắp chiếu · Đang giữ ghế · Chờ MoMo · Mở bán · Online |
| Cần xem (`warn`) | `#FFF5D6` | `#7A5300` | `#F1DE9E` | `#D9A400` | Chờ đối soát · Sắp hết hạn · Thiếu ảnh ngang · Vé đã dùng |
| Lỗi (`bad`) | `#FDECEC` | `#B42318` | `#F6CACA` | `#C2362F` | Đã huỷ · Thanh toán lỗi · Sai suất · Không thuộc rạp · Đã khoá |
| Tắt (`off`) | `#F1F2F4` | `#4B5563` | `#E0E3E8` | `#9AA2AD` | Đã tắt · Đang ẩn · Hết hạn · Đã kết thúc · Nháp · Chưa vào |
| Quầy (`counter`) | `#FEF0E4` | `#A9480A` | `#F9D4B4` | `#E06A12` | Tại quầy · ghế Đôi |
| Đặc biệt (`special`) | `#F1ECFB` | `#5B3BA8` | `#DDD1F4` | `#6B4BC2` | ghế VIP · Voucher |
| Bắp nước (`food`) | `#E3F4F4` | `#0F6466` | `#BFE3E3` | `#0F7C7E` | Combo · Đồ uống |

Nhãn tuổi giống trang khách, nền đặc chữ trắng 11/700: P `#1F8A4C` · K `#1459A5` · T13 `#B7791F` · T16 `#D9620C` · T18 `#C2362F`. Thể loại luôn là chip `off` chữ 500.

**Icon theo mục sidebar** (sidebar phẳng kiểu ứng dụng chat, người dùng chốt 13/11/2026, thay cho nhóm đóng/mở): mỗi mục có một icon nét riêng, mixin `navIcon(id)` trong `templates/admin/layout.pug` (khung 24×24, 20px, nét 1.6, không tô nền, không màu riêng từng mục):

| Mục (`id`) | Hình |
|---|---|
| Tổng quan (`overview`) | 4 ô chữ nhật lệch cao |
| Đơn hàng (`orders`) | hoá đơn răng cưa đáy + 3 dòng |
| Phim (`movies`) | cuộn phim |
| Lịch chiếu (`schedule`) | khung lịch + 2 khoen + dòng |
| Rạp & phòng (`cinemas`) | toà nhà mái nhọn + cửa |
| Ca tại quầy (`shifts`) | hai người |
| Kinh doanh (`business`) | thẻ giá |
| Nội dung (`content`) | khung ảnh |
| Hệ thống (`system`) | bánh răng |

Icon màu `--muted`; mục đang mở nền `--brand-soft`, icon và chữ `--brand` 600.

**Chữ:** Be Vietnam Pro (đã tự host). Thang cỡ: 12 nhãn/chip · 13 bảng · 14 nội dung, ô nhập, sidebar · 16/600 tiêu đề thẻ · 28/700 tiêu đề trang · 26–28/700 số trong thẻ số liệu. Mã vé/mã đơn dùng `ui-monospace, Consolas, monospace` 600. Viết hoa đầu câu.

**Hình khối và khoảng cách:** thẻ bo 10px; ô số liệu, nút, ô nhập bo 6px; chip bo 4px; ô icon 36px bo 8px; ghế bo 3px. Lưới bội số 4px; khoảng cách giữa các thẻ 12–16px; đệm thẻ 14–16px. Dòng bảng ≥ 44px, ô nhập/nút 38px, chip 24px. Bóng chỉ cho hộp thoại và menu nổi: `0 8px 24px rgba(20,23,28,.16)`. Focus: viền `--brand` 2px cách 2px.

**Khung**

- **Thanh trên cùng** trắng, kẻ dưới `--line`. **Cụm đầu trái giữ nguyên như hiện tại** (người dùng chốt): nút ẩn/hiện sidebar, mũi tên lùi/tiến, logo `cineprime-mark.svg` + "Cine" xanh "Prime" cam. Bên phải (Admin, chốt 13/11/2026): chỉ nút viền "Xem website" có icon mở tab mới; tên người dùng (ô tròn chữ cái đầu + tên + vai trò) và "Đăng xuất" (một dòng như mục sidebar, rê chuột tô `--bad-bg`) nằm ở đáy sidebar, kẻ trên `--line`. Staff giữ nguyên tên + "Đăng xuất" trên thanh đầu.
- **Admin:** sidebar trắng 232px, kẻ phải, thu gọn được (Ctrl+B). **Danh sách phẳng, không nhóm đóng/mở** (chốt 13/11/2026): mỗi mục cao 36px = icon 20px + tên 14/500, cách 12px; nhãn mục "Vận hành", "Quản lý" là chữ 12/500 `--muted`, không bấm được. Chín mục theo thứ tự: Tổng quan · Đơn hàng · *Vận hành:* Phim · Lịch chiếu · Rạp & phòng · Ca tại quầy · *Quản lý:* Kinh doanh · Nội dung · Hệ thống — định nghĩa ở `NAV` trong `AdminController`. Nội dung đệm 24/32px trên nền `--canvas`.
- **Trang gộp có tab** (`.hub-head` + `.hub-tabs`): mục có nhiều màn mở ra tiêu đề khu (28/700) và **dải tab phân đoạn** (người dùng yêu cầu thêm vách ngăn, nổi bật hơn — 13/11/2026): thẻ trắng viền `--line-strong` bo 8px, mỗi tab cao 42px đệm ngang 20px, chữ 14/600 `--ink-2`, ngăn nhau bằng vạch dọc 1px; tab đang mở nền `--brand-soft`, chữ `--brand`, gạch dưới 3px `--brand`. Tab kênh ở Đơn hàng (`.channel-tabs`) dùng cùng kiểu. Mỗi tab giữ URL riêng; tab theo rạp mang theo `cinemaId`. Tab chỉ hiện ở màn danh sách, không ở form sửa. Lịch chiếu = Lịch theo ngày · Suất chiếu; Rạp & phòng = Rạp · Phòng · Ghế / Bảo trì; Kinh doanh = Bảng giá · Bắp nước / Combo · Voucher; Nội dung = Banner trang chủ · Bài viết / Ưu đãi / Chính sách · Bình luận; Hệ thống = Người dùng · Nhật ký hoạt động. Khi có tab, trang không in lại h1 riêng; nút tạo ghi rõ đối tượng ("+ Thêm voucher").
- **Staff:** không sidebar; hai tab "Soát vé" · "Bán vé tại quầy" trên thanh đầu (tab đang mở chữ `--brand` 600 + gạch dưới 2px). Tên rạp phụ trách in đậm bên phải. Nút ẩn/hiện ở cụm trái thu gọn cột danh sách suất.

**Thành phần**

- **Đầu trang:** breadcrumb (`Quản trị / Đơn hàng`, có khoảng trắng hai bên dấu `/`) → tiêu đề 28/700 (không kèm icon hay ô màu) → một dòng mô tả `--muted`; nút bên phải (cam nếu là tạo mới: "+ Thêm phim", "+ Thêm banner").
- **Thẻ số liệu:** 4 thẻ một hàng, vạch trên 3px theo nghĩa, ô icon 36px nền nhạt cùng nghĩa (icon nét 1.8px), nhãn 12px, số 26–28/700, một dòng phụ tô chữ cùng nghĩa. Thẻ trung tính (tổng, khớp bộ lọc) dùng `info`.
- **Thẻ danh sách:** trong một thẻ: tab kênh (tab đang mở có chip đếm `info`) → hàng bộ lọc (nhãn trên ô, nút "Lọc" xanh) → bảng (đầu bảng nền `--card-head`, kẻ `--line-soft`) → dòng phân trang. Cột trạng thái, kênh, loại dùng chip.
- **Hành động trong dòng:** "Sửa" nền `--brand-soft` chữ `--brand`; "Xoá" nền trắng viền đỏ nhạt chữ `bad`; các hành động phụ trong menu "⋯".
- **Danh sách "Cần để ý":** mỗi dòng chip nghĩa + tiêu đề 600 + dòng chi tiết + liên kết; không có việc thì một dòng với chip `ok`.
- **Trục giờ "Hôm nay"** (Tổng quan, Soát vé): hàng theo phòng, thước giờ; mỗi suất là thanh nền `info` nhạt, phần đậm = tỉ lệ lấp đầy, suất đang chiếu viền `--brand` 2px; vạch "bây giờ" cam 2px kèm nhãn giờ `--accent-ink`.
- **Ghế:** Trống viền `--line-strong`; Thường/VIP/Đôi theo `off`/`special`/`counter` khi cần phân loại; Đang chọn nền `--accent` chữ `--ink`; Quầy này giữ nền `--brand` chữ trắng; Đã bán / nơi khác giữ nền `#CFD4DA`; Bảo trì viền nét đứt.

**Màn chính**

- **Tổng quan (Admin):** đầu trang + bộ lọc kỳ/ngày/rạp + "Xuất CSV" → 4 thẻ số liệu (Doanh thu `info`, Tiền vào `ok`, Hoàn tiền `bad`, Vé · đơn `special`) → trục giờ Hôm nay → hai cột: thẻ Dòng tiền | thẻ Cần để ý + Ca quầy đang mở → thẻ Đơn mới hôm nay.
- **Đơn hàng và các trang danh sách** (Phim, Bảng giá, Combo, Voucher, Bài viết, Banner, Bình luận, Người dùng): đầu trang → (thẻ số liệu nếu có) → thẻ danh sách. Phim: chip tuổi cạnh tên, chip `Đang chiếu`/`Sắp chiếu`/`Đã kết thúc` và chip `Đang hiển thị`/`Đang ẩn` tách nghĩa. Voucher: mức giảm có đơn vị (`100%`, `20.000 đ`).
- **Soát vé (Staff):** giữ bố cục ba cột đã triển khai. Kết quả soát là một thẻ lớn tô nền theo nghĩa (Hợp lệ `ok`, Vé đã dùng `warn`, Sai suất/Không thuộc rạp `bad`) với chữ trạng thái 28/700, ghế, suất, mã đơn; nút "Cho vào" cam. Bảng vé dùng chip Đã vào `ok` / Chưa vào `off`. Dải số Hiệu lực/Đã vào/Chưa vào/Tỉ lệ là thẻ số liệu nhỏ.
- **Lịch chiếu (Admin, chốt 14/11/2026, phác thảo trên canvas "CinePrime Admin/Staff — phác thảo", trang "Lịch chiếu — 3 tab + ngăn Thêm suất (14/11)"):** một khu, ba tab `.hub-tabs` **Theo ngày** (`/admin/schedule`) · **Danh sách** (`/admin/manage/showtimes`) · **Xếp lịch tuần** (`/admin/showtimes/new`). Ba tab dùng chung **một thanh ngữ cảnh** (thẻ trắng: Rạp · ← ngày/tuần → · Hôm nay · lọc Phim, Phòng, Trạng thái); rạp, phòng, phim, ngày, trạng thái đi theo khi đổi tab. Bỏ hộp "Rạp đang quản lý", bỏ liên kết chéo giữa các tab, bỏ ô tìm "Phim / Phòng" trùng với ô chọn ở Theo ngày. Một nút cam "+ Thêm suất chiếu" (mở Xếp lịch tuần với ngăn Thêm suất). Phím tắt ghi bằng `kbd` ở góc phải dòng tab: `N` thêm suất, `←`/`→` đổi ngày (tuần), `T` hôm nay (tuần này), `Esc` đóng ngăn. CSS: `static/css/showtime-hub.css` (Theo ngày, Danh sách), `showtime-batch.css` (Xếp lịch tuần), `showtime-editor.css` (hộp Sửa suất) — chỉ dùng token, không hex rời ngoài khai báo `--film-0…7`.
  - **Theo ngày:** 4 thẻ số liệu nhỏ (Suất trong ngày `info` · Đang mở bán `ok` · Nháp, chưa bán `off` kèm liên kết "Mở bán N suất →" · Phòng chưa có suất `warn`) → thẻ "Trục giờ theo phòng": mỗi phòng một hàng (tên, định dạng · số ghế · số suất), thước giờ từ 08:00 (giãn theo suất), mỗi suất là thanh `info` nhạt (Nháp `off` viền đứt, đã qua mờ, đang chiếu viền `--brand` 2px), vạch đậm 4px đáy thanh = tỉ lệ ghế đã bán, đuôi `--line-soft` = 15 phút dọn phòng, vạch cam "bây giờ" + nhãn giờ `--accent-ink`. Bấm thanh → hộp Sửa suất; bấm khoảng trống → Xếp lịch tuần mở ngăn Thêm suất đúng phòng, ngày, giờ (làm tròn 15 phút); khoảng trống ≥ 3 giờ hiện nút "+ Thêm suất từ HH:MM" khi rê chuột. Thẻ "Cần để ý": khoảng trống dài, suất nháp, phòng chưa có suất; không có gì thì một dòng chip `ok`.
  - **Danh sách:** 20 dòng/trang, xếp theo phòng rồi giờ, dòng nhóm phòng nền `--card-head` (định dạng · số ghế · số suất trong ngày). Cột: ô chọn · Giờ chiếu · Phim · Định dạng · Ngôn ngữ · Đã bán (thanh 64px + "x / y") · Phụ thu (căn phải) · Trạng thái (chip) · Sửa. Chọn dòng → dải `--brand-soft` trên bảng: "Đã chọn N suất · a nháp · b đang mở bán" + "Mở bán a suất nháp" (xanh) + "Huỷ N suất" (viền đỏ) + "Bỏ chọn"; tất cả hoặc không (API `POST /api/admin/showtime-batches/showtimes/bulk`). Suất có vé/giữ ghế, đã bắt đầu hoặc đã huỷ không chọn được (ô mờ có lời giải thích).
  - **Xếp lịch tuần:** thanh ngữ cảnh riêng (Rạp · tuần · Cả tuần/Theo giờ · + Thêm suất · Chép ngày · Làm mới · Excel); hai chế độ cùng công cụ; không còn dải hướng dẫn 3 bước. Ô suất: nền theo trạng thái (Mở bán `info`, Nháp `off`, Mới chưa lưu nền trắng viền đứt `--accent-ink`, lỗi `bad`, đang xem trước viền đứt `--faint`), vạch trái 3px = màu phim (`--film-0…7`, chỉ để nhận ra phim, không mang trạng thái). **Ngăn "Thêm suất chiếu"** (cột phải 384px, cạnh lịch, không che lịch; thay hộp thoại thêm suất + ngăn "Nhiều suất nối tiếp"): Phòng (chip) → Phim (danh sách có thứ tự, "+ Thêm phim"; từ 2 phim hiện "Xen kẽ / Lần lượt", Lần lượt có bộ đếm số suất mỗi phim) → Ngày (7 chip, ngày đã qua tắt, ngày ngoài thời gian chiếu viền đứt `warn`; chọn nhanh Cả tuần · T2–T6 · Cuối tuần) → Giờ ("Một suất / Nhiều suất nối tiếp", Suất đầu từ · Suất cuối bắt đầu trước · "N suất/ngày") → mỗi ngày một dải giờ 08:00–02:00 (xám = suất đã có, số = suất sắp thêm, kéo để dời và ghim) và các ô giờ sửa được (sửa = ghim, "Bỏ ghim"). Đổi phòng/phim khi có giờ ghim thì hỏi trước; đổi giờ suất cuối không bỏ ghim. Thanh đáy: "N suất mới chưa lưu · x cần sửa · y chưa đủ điều kiện mở bán" + **Lưu nháp** (viền, lưu thẳng khi không có lỗi/cảnh báo) + **Lưu và mở bán N suất** (cam, luôn có bước xác nhận). Bỏ ô "Sau khi tạo".
  - **Hộp Sửa suất:** giữ bố cục; chip trạng thái theo bảng nghĩa (Đang bán vé `info`, Nháp `off`, Có vé / giữ ghế `warn`), dải suất của phòng trong ngày dùng token. Server từ chối dời hoặc mở bán suất về giờ đã qua.
- **Bán vé tại quầy (Staff):** dải ca nền `counter` nhạt (ca, đơn, vé, tiền mặt trong két, "Đơn trong ca", "Đóng ca"); ba cột 1 · Suất → 2 · Ghế → 3 · Đơn; nút chính cam "Giữ N ghế · 5 phút" chỉ hiện khi đã chọn ghế.

**Không làm:** gradient, emoji, bóng đổ trên thẻ thường; màu không có nghĩa (thể loại nhiều màu, icon tô màu ngẫu nhiên); pill tròn cho trạng thái; chỉ dùng màu không có chữ; nhiều hơn một nút cam mỗi màn; nút đen nặng cho "Thêm mới"; dải "—" lặp lại cho số 0; ký hiệu "₫"; nhóm sidebar rỗng; thay đổi cụm logo + nút sidebar.

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
