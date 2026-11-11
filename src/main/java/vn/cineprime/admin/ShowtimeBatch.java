package vn.cineprime.admin;

import java.util.*;

public final class ShowtimeBatch {
    private ShowtimeBatch() {}
    public static final int LIMIT=500;
    public record Input(Long cinemaId,String source,String targetStatus,List<Line> rows) {}
    public record Line(String rowKey,Integer sourceRow,String movieId,String roomId,String date,String time,String language,String format,String surcharge) {}
    public record Problem(String code,String field,String message,List<String> relatedRows) {
        public Problem(String code,String field,String message){this(code,field,message,List.of());}
    }
    public record Checked(Line input,String movieTitle,String roomName,String startsAt,String endsAt,String cleanupUntil,List<Problem> errors,List<String> warnings) {}
    public record Preview(List<Checked> rows,int total,int invalid,boolean canCommit,String fingerprint,String serverTime) {}
    public record Commit(Input input,String fingerprint,String requestKey) {}
    public record Created(String rowKey,Long showtimeId,String date) {}
    public record Result(String requestKey,Long cinemaId,String targetStatus,List<Created> created,String committedAt) {}
    public static final class Failure extends IllegalArgumentException {
        public final int status; public final String code; public final Preview preview;
        public Failure(int status,String code,String message,Preview preview){super(message);this.status=status;this.code=code;this.preview=preview;}
    }
    public static String text(String value){return value==null?"":value.trim();}
    public static Long id(String value){try{long n=Long.parseLong(text(value));return n>0?n:null;}catch(NumberFormatException e){return null;}}
}
