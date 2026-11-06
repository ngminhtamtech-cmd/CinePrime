package vn.cineprime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.cineprime.payment.MomoService;
class MomoSignatureTest {
    @Test void hmacMatchesPublishedSha256TestVector(){assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",MomoService.sign("key","The quick brown fox jumps over the lazy dog"));}
}
