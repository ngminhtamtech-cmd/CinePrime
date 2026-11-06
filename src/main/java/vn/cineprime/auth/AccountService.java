package vn.cineprime.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.cineprime.domain.Store;
import vn.cineprime.notification.Notifications;
import static vn.cineprime.domain.Domain.*;

@Service @Transactional
public class AccountService {
    private final Store db; private final PasswordEncoder encoder; private final Clock clock; private final Notifications notices;
    @Value("${cineprime.base-url}") String baseUrl;
    public AccountService(Store db,PasswordEncoder encoder,Clock clock,Notifications notices){this.db=db;this.encoder=encoder;this.clock=clock;this.notices=notices;}
    public static String token(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public void passwordValid(String raw){if(raw==null||raw.length()<10||raw.getBytes(StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Mật khẩu cần ít nhất 10 ký tự và tối đa 72 byte UTF-8.");}
    public User register(String email,String name,String password){
        passwordValid(password);email=email.trim().toLowerCase(Locale.ROOT);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.length()>254||name.isBlank()||name.length()>100)throw new IllegalArgumentException("Kiểm tra email và họ tên.");
        if(db.first(User.class,"where e.email=?1",email).isPresent())throw new IllegalArgumentException("Không thể đăng ký email này. Hãy đăng nhập hoặc khôi phục mật khẩu.");
        User u=new User();u.email=email;u.name=name.trim();u.passwordHash=encoder.encode(password);return db.save(u);
    }
    public void forgot(String email){
        db.first(User.class,"where e.email=?1 and e.enabled=true",email.trim().toLowerCase(Locale.ROOT)).ifPresent(u->{
            db.lock(User.class,u.id);
            for(ResetToken old:db.list(ResetToken.class,"where e.userId=?1 and e.used=false",u.id))old.used=true;
            String raw=token();ResetToken t=new ResetToken();t.userId=u.id;t.tokenHash=hash(raw);t.expiresAt=clock.instant().plusSeconds(1800);db.save(t);
            notices.mail(u,"reset:"+t.tokenHash,"Đặt lại mật khẩu CinePrime","Liên kết dùng một lần, hết hạn sau 30 phút:\n"+baseUrl+"/reset-password?token="+raw);
        });
    }
    public void reset(String raw,String password){
        passwordValid(password);ResetToken found=db.first(ResetToken.class,"where e.tokenHash=?1",hash(raw)).orElseThrow(()->new IllegalArgumentException("Liên kết không hợp lệ hoặc đã hết hạn."));
        User user=db.lock(User.class,found.userId);ResetToken t=db.lock(ResetToken.class,found.id);
        if(t.used||!clock.instant().isBefore(t.expiresAt))throw new IllegalArgumentException("Liên kết không hợp lệ hoặc đã hết hạn.");
        user.passwordHash=encoder.encode(password);user.authVersion++;t.used=true;
    }
    public void changePassword(Long id,String old,String next){User u=db.lock(User.class,id);if(!encoder.matches(old,u.passwordHash))throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");passwordValid(next);u.passwordHash=encoder.encode(next);u.authVersion++;}
    public void profile(Long id,String name,String phone,String avatar){User u=db.lock(User.class,id);if(name.isBlank()||name.length()>100||phone.length()>30)throw new IllegalArgumentException("Thông tin hồ sơ không hợp lệ.");u.name=name.trim();u.phone=phone;if(avatar!=null)u.avatar=avatar;}
}
