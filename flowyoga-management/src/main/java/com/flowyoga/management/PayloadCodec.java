package com.flowyoga.management;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import tools.jackson.databind.json.JsonMapper;
public final class PayloadCodec {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private PayloadCodec() {}
    public static String encode(Object value) { return JSON.writeValueAsString(value); }
    public static <T> T decode(String value,Class<T> type) { return JSON.readValue(value,type); }
    public static String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
