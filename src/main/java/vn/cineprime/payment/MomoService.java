package vn.cineprime.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import vn.cineprime.booking.BookingService;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;

@Service
public class MomoService {
    @Value("${cineprime.momo.partner-code}") String partner;
    @Value("${cineprime.momo.access-key}") String access;
    @Value("${cineprime.momo.secret-key}") String secret;
    @Value("${cineprime.momo.endpoint}") String endpoint;
    @Value("${cineprime.base-url}") String baseUrl;
    private final Store db;private final BookingService bookings;
    public MomoService(Store db,BookingService bookings){this.db=db;this.bookings=bookings;}
    public boolean configured(){return !partner.isBlank()&&!access.isBlank()&&!secret.isBlank();}
    public static String sign(String secret,String raw){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String canonical(Map<String,?> data,String... keys){StringJoiner raw=new StringJoiner("&");for(String key:keys){Object v=key.equals("accessKey")?access:data.get(key);if(v==null)throw new IllegalArgumentException("Thiếu trường phản hồi MoMo.");raw.add(key+"="+v);}return raw.toString();}
    private Map<String,Object> post(String path,Map<String,Object> payload){
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(client);factory.setReadTimeout(Duration.ofSeconds(35));
        Map<?,?> result=RestClient.builder().requestFactory(factory).build().post().uri(endpoint+path).body(payload).retrieve().body(Map.class);
        if(result==null)throw new IllegalArgumentException("MoMo chưa trả kết quả. Hãy kiểm tra lại trạng thái, không tạo đơn mới.");
        Map<String,Object> data=new HashMap<>();result.forEach((k,v)->data.put(k.toString(),v));return data;
    }
    public String create(Long userId,Long orderId){
        if(!configured())throw new IllegalArgumentException("MoMo sandbox chưa được cấu hình. Ghế vẫn giữ theo hạn trên đơn; không có thanh toán mô phỏng.");
        Payment p=bookings.preparePayment(userId,orderId);
        if(p.payUrl!=null&&!p.payUrl.isBlank())return p.payUrl;
        Map<String,Object> body=new LinkedHashMap<>();body.put("partnerCode",partner);body.put("requestId",p.requestId);body.put("amount",p.amount);body.put("orderId",p.externalOrderId);body.put("orderInfo","CinePrime "+orderId);body.put("redirectUrl",baseUrl+"/orders/"+orderId);body.put("ipnUrl",baseUrl+"/payments/momo/ipn");body.put("extraData","");body.put("requestType","captureWallet");body.put("autoCapture",true);body.put("lang","vi");
        body.put("signature",sign(secret,canonical(body,"accessKey","amount","extraData","ipnUrl","orderId","orderInfo","partnerCode","redirectUrl","requestId","requestType")));
        Map<String,Object> result=post("/v2/gateway/api/create",body);
        if(!Objects.equals(result.get("resultCode"),0))throw new IllegalArgumentException("MoMo chưa tạo được đường dẫn thanh toán. Hãy xác minh trạng thái trước khi thử lại.");
        String url=Objects.toString(result.get("payUrl"),"");URI uri=URI.create(url);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||!(uri.getHost().equals("momo.vn")||uri.getHost().endsWith(".momo.vn")))throw new IllegalArgumentException("Đường dẫn MoMo không hợp lệ.");
        bookings.paymentCreated(p.id,url);return url;
    }
    public void ipn(Map<String,Object> data){
        if(!configured()||!partner.equals(data.get("partnerCode")))throw new IllegalArgumentException("Merchant không hợp lệ.");
        String raw=canonical(data,"accessKey","amount","extraData","message","orderId","orderInfo","orderType","partnerCode","payType","requestId","responseTime","resultCode","transId");
        String expected=sign(secret,raw),actual=Objects.toString(data.get("signature"),"");
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),actual.getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException("Chữ ký không hợp lệ.");
        Payment p=db.first(Payment.class,"where e.externalOrderId=?1",Objects.toString(data.get("orderId"))).orElseThrow(()->new IllegalArgumentException("Không tìm thấy giao dịch."));
        if(!p.requestId.equals(data.get("requestId")))throw new IllegalArgumentException("Request không khớp.");
        bookings.paymentResult(p.externalOrderId,Long.parseLong(data.get("amount").toString()),Objects.toString(data.get("transId")),Integer.parseInt(data.get("resultCode").toString()));
    }
    public void query(Long paymentId){
        if(!configured())throw new IllegalArgumentException("MoMo sandbox chưa cấu hình.");
        Payment p=db.get(Payment.class,paymentId);if(!p.provider.equals("MOMO"))throw new IllegalArgumentException("Đơn miễn phí bằng voucher không có giao dịch MoMo để truy vấn.");Map<String,Object> body=new LinkedHashMap<>();body.put("partnerCode",partner);body.put("orderId",p.externalOrderId);body.put("requestId",UUID.randomUUID().toString());body.put("lang","vi");
        body.put("signature",sign(secret,canonical(body,"accessKey","orderId","partnerCode")));
        Map<String,Object> result=post("/v2/gateway/api/query",body);
        if(!partner.equals(result.get("partnerCode"))||!p.externalOrderId.equals(result.get("orderId")))throw new IllegalArgumentException("Kết quả truy vấn không khớp.");
        // Query is authenticated server-to-server over HTTPS. Query requestId differs from create requestId.
        if(result.get("amount")==null||result.get("resultCode")==null)throw new IllegalArgumentException("MoMo chưa có kết quả cuối. Vui lòng thử lại sau.");
        bookings.paymentResult(p.externalOrderId,Long.parseLong(result.get("amount").toString()),Objects.toString(result.get("transId"),""),Integer.parseInt(result.get("resultCode").toString()));
    }
}
