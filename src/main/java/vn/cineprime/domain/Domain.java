package vn.cineprime.domain;

import jakarta.persistence.*;
import java.time.*;

/** Persistent records use explicit foreign keys; services own aggregate transactions. */
public final class Domain {
    private Domain() {}
    @MappedSuperclass public static abstract class Row {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    }
    @Entity(name="Customer") @Table(name="users") public static class User extends Row {
        public String email, passwordHash, name, phone, avatar;
        public String role = "CUSTOMER";
        public Long cinemaId;
        public boolean enabled = true;
        public int authVersion;
    }
    @Entity @Table(name="reset_tokens") public static class ResetToken extends Row {
        public Long userId; public String tokenHash; public Instant expiresAt; public boolean used;
    }
    @Entity @Table(name="genres") public static class Genre extends Row { public String name; public boolean active=true; }
    @Entity @Table(name="people") public static class Person extends Row { public String name, biography; public boolean active=true; }
    @Entity @Table(name="movies") public static class Movie extends Row {
        public String title, slug;
        @Column(length=6000) public String description;
        public String poster, banner, trailer, ageRating="P", status="COMING_SOON";
        public int duration=90; public LocalDate releaseDate; public boolean featured, active=true;
    }
    @Entity @Table(name="movie_genres") public static class MovieGenre extends Row { public Long movieId, genreId; }
    @Entity @Table(name="credits") public static class Credit extends Row { public Long movieId, personId; public String kind="ACTOR"; }
    @Entity @Table(name="cinemas") public static class Cinema extends Row {
        public String name, city, address, phone;
        @Column(length=6000) public String description;
        public boolean active=true;
    }
    @Entity @Table(name="rooms") public static class Room extends Row { public Long cinemaId; public String name, format="2D"; public boolean active=true; }
    @Entity @Table(name="seats") public static class Seat extends Row {
        public Long roomId; public String label, rowLabel, type="STANDARD", coupleGroup;
        public int position; public boolean active=true;
    }
    @Entity @Table(name="showtimes") public static class Showtime extends Row {
        public Long movieId, roomId; public Instant startsAt, endsAt;
        public String language="Phụ đề", format="2D", status="DRAFT";
        public long surcharge;
    }
    @Entity @Table(name="prices") public static class Price extends Row {
        public Long cinemaId; public String seatType="STANDARD", dayType="WEEKDAY";
        public int fromMinute, toMinute=1440; public long amount;
    }
    @Entity @Table(name="products") public static class Product extends Row {
        public String name, kind="COMBO", image, description; public long price; public boolean active=true;
    }
    @Entity @Table(name="vouchers") public static class Voucher extends Row {
        public String code, description, kind="FIXED"; public long value, minimum, maximum;
        public int quota=100, perUser=1; public Instant startsAt, endsAt; public boolean active=true;
    }
    @Entity(name="BookingOrder") @Table(name="orders") public static class Order extends Row {
        public String code; public Long userId, showtimeId, voucherId;
        public String status="PENDING_PAYMENT";
        public Instant createdAt, expiresAt, paidAt;
        public long subtotal, discount, total; public boolean frozen;
        public String movieTitle, cinemaName, roomName; public Instant showStartsAt;
    }
    @Entity @Table(name="inventory") public static class Inventory extends Row {
        public Long showtimeId, seatId, orderId; public String status="AVAILABLE"; public Instant expiresAt;
    }
    @Entity @Table(name="order_seats") public static class OrderSeat extends Row {
        public Long orderId, seatId; public String label, seatType; public long price;
    }
    @Entity @Table(name="order_products") public static class OrderProduct extends Row {
        public Long orderId, productId; public String name; public int quantity; public long price;
    }
    @Entity @Table(name="voucher_uses") public static class VoucherUse extends Row {
        public Long voucherId, orderId, userId; public String status="RESERVED"; public Instant expiresAt;
    }
    @Entity @Table(name="payments") public static class Payment extends Row {
        public Long orderId; public String provider="MOMO", requestId, externalOrderId, transactionId;
        public String status="CREATED";
        @Column(length=2048) public String payUrl;
        public long amount;
        public Integer resultCode; public boolean reconciliation; public Instant createdAt, updatedAt;
    }
    @Entity @Table(name="tickets") public static class Ticket extends Row {
        public Long orderId, seatId, showtimeId; public String code, token, status="VALID";
        public Instant checkedInAt; public Long checkedInBy;
    }
    @Entity @Table(name="point_ledger") public static class Point extends Row { public Long userId, orderId; public long points; public Instant createdAt; }
    @Entity @Table(name="favorites") public static class Favorite extends Row { public Long userId, movieId; }
    @Entity @Table(name="reviews") public static class Review extends Row {
        public Long userId, movieId; public int stars;
        @Column(length=2000) public String comment;
        public boolean visible=true; public Instant createdAt;
    }
    @Entity @Table(name="subscriptions") public static class Subscription extends Row { public Long userId, movieId; public boolean email=true, sent; }
    @Entity @Table(name="notifications") public static class Notification extends Row {
        public Long userId; public String subject;
        @Column(length=6000) public String body;
        public boolean seen; public Instant createdAt;
    }
    @Entity @Table(name="contents") public static class Content extends Row {
        public String kind="ARTICLE", title, slug, image, link;
        @Column(length=20000) public String body;
        public boolean active=true; public Instant publishedAt;
    }
    @Entity @Table(name="audit_logs") public static class Audit extends Row {
        public Long actorId; public String action, target; public Instant createdAt;
    }
    @Entity @Table(name="mail_jobs") public static class MailJob extends Row {
        public Long userId; public String dedupKey, destination, subject;
        @Column(length=12000) public String body;
        public int attempts; public Instant nextAttemptAt, sentAt;
    }
}
