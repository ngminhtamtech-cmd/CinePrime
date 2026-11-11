package vn.cineprime;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.cineprime.admin.*;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShowtimeExcelTest {
    private static final String[] HEADERS={"Phim","Phòng","Ngày chiếu","Giờ bắt đầu","Ngôn ngữ","Định dạng","Phụ thu"};
    ShowtimeExcel fixture(int count){
        Store db=mock(Store.class);Cinema c=new Cinema();c.id=1L;c.name="Rạp kiểm thử tiếng Việt";
        Movie m=new Movie();m.id=1L;m.title="Phim tiếng Việt";m.duration=100;
        List<Room> rooms=new ArrayList<>();for(int n=1;n<=20;n++){Room r=new Room();r.id=(long)n;r.cinemaId=1L;r.name="Phòng %02d".formatted(n);rooms.add(r);}
        List<Showtime> shows=new ArrayList<>();for(int n=0;n<count;n++){Showtime s=new Showtime();s.id=(long)n+1;s.movieId=1L;s.roomId=(long)(n%20)+1;s.startsAt=LocalDate.of(2026,12,1).atTime(23,30).atZone(ReportService.ZONE).toInstant().plusSeconds((n/20)*10800L);s.endsAt=s.startsAt.plusSeconds(6000);s.status=n%2==0?"DRAFT":"OPEN";shows.add(s);}
        when(db.get(Cinema.class,1L)).thenReturn(c);when(db.list(Movie.class,"order by e.title,e.id")).thenReturn(List.of(m));when(db.list(Room.class,"where e.cinemaId=?1 order by e.name,e.id",1L)).thenReturn(rooms);
        when(db.entityName(Showtime.class)).thenReturn("Showtime");when(db.entityName(Room.class)).thenReturn("Room");
        when(db.query(anyString(),eq(Showtime.class),eq(1L),any(Instant.class),any(Instant.class))).thenReturn(shows);
        return new ShowtimeExcel(db);
    }
    byte[] export(ShowtimeExcel excel){return excel.export(1L,LocalDate.of(2026,12,1),LocalDate.of(2026,12,7),false);}
    byte[] bytes(XSSFWorkbook book)throws IOException{var out=new ByteArrayOutputStream();book.write(out);return out.toByteArray();}
    MockMultipartFile file(byte[] data){return new MockMultipartFile("file","đăng-ký.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",data);}
    Row fill(Sheet sheet,int index){Row row=sheet.getRow(index);if(row==null)row=sheet.createRow(index);for(int n=0;n<7;n++)if(row.getCell(n)==null)row.createCell(n);row.getCell(0).setCellValue("M1 — Tên cũ");row.getCell(1).setCellValue("R1 — Phòng cũ");row.getCell(2).setCellValue("20/12/2026");row.getCell(3).setCellValue("11:55");return row;}
    @Test void versionTwoHasReadyFormAndProtectedCopyableReference()throws Exception{
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(fixture(2))))){
            assertEquals(List.of("Hướng dẫn",ShowtimeExcel.REFERENCE,ShowtimeExcel.REGISTRATION,"Danh mục"),java.util.stream.IntStream.range(0,book.getNumberOfSheets()).mapToObj(book::getSheetName).toList());assertEquals(2,book.getActiveSheetIndex());
            var data=book.getSheet(ShowtimeExcel.REGISTRATION);assertEquals(500,data.getLastRowNum());assertEquals(501,data.getPhysicalNumberOfRows());assertEquals("A2",data.getActiveCell().formatAsString());assertEquals(2,data.getDataValidations().size());assertFalse(data.getRow(1).getCell(2).getCellStyle().getLocked());assertEquals(BorderStyle.THIN,data.getRow(500).getCell(6).getCellStyle().getBorderBottom());
            var reference=book.getSheet(ShowtimeExcel.REFERENCE);assertTrue(reference.getProtect());assertFalse(reference.getCTWorksheet().getSheetProtection().getSelectLockedCells());assertFalse(reference.getCTWorksheet().getSheetProtection().getAutoFilter());assertNotNull(reference.getPaneInformation());assertTrue(reference.getCTWorksheet().isSetAutoFilter());
            for(int n=0;n<7;n++)assertEquals(data.getRow(0).getCell(n).getStringCellValue(),reference.getRow(0).getCell(n).getStringCellValue());
            assertEquals(LocalDateTime.of(2026,12,2,1,10),reference.getRow(1).getCell(7).getLocalDateTimeCellValue());assertEquals(LocalDateTime.of(2026,12,2,1,25),reference.getRow(1).getCell(8).getLocalDateTimeCellValue());assertEquals("Nháp",reference.getRow(1).getCell(9).getStringCellValue());assertEquals("dd/mm/yyyy hh:mm",reference.getRow(1).getCell(7).getCellStyle().getDataFormatString());
        }
    }
    @Test void hundredReferenceShowsOnlyReadThreeRegistrationsWithTypedDates()throws Exception{
        var excel=fixture(100);try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(excel)))){
            assertEquals(100,book.getSheet(ShowtimeExcel.REFERENCE).getLastRowNum());var sheet=book.getSheet(ShowtimeExcel.REGISTRATION);
            fill(sheet,1).getCell(2).setCellValue(LocalDate.of(2026,12,20).atStartOfDay());sheet.getRow(1).getCell(3).setCellValue((11*60+55)/1440d);sheet.getRow(1).getCell(6).setCellValue(15000);
            fill(sheet,2);fill(sheet,4);book.getSheet(ShowtimeExcel.REFERENCE).getRow(1).getCell(0).setCellFormula("1+1");
            var result=excel.readWorkbook(1L,"DRAFT",file(bytes(book)));assertEquals(3,result.input().rows().size());assertEquals(List.of(2,3,5),result.input().rows().stream().map(ShowtimeBatch.Line::sourceRow).toList());assertEquals(2,result.excel().templateVersion());assertEquals(ShowtimeExcel.REGISTRATION,result.excel().sheetName());assertTrue(result.excel().warning().isBlank());
            var row=result.input().rows().getFirst();assertEquals("1",row.movieId());assertEquals("1",row.roomId());assertEquals("2026-12-20",row.date());assertEquals("11:55",row.time());assertEquals("15000",row.surcharge());
        }
    }
    @Test void moreThanFiveHundredReferenceRowsDoNotUseTheNewShowLimit()throws Exception{
        var excel=fixture(600);try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(excel)))){assertEquals(600,book.getSheet(ShowtimeExcel.REFERENCE).getLastRowNum());fill(book.getSheet(ShowtimeExcel.REGISTRATION),1);assertEquals(1,excel.read(1L,"DRAFT",file(bytes(book))).rows().size());}
    }
    @Test void untouchedExportOrBlankTemplateHasClearEmptyRegistrationFailure(){
        var excel=fixture(100);for(byte[] data:List.of(export(excel),excel.export(1L,null,null,true))){var error=assertThrows(ShowtimeBatch.Failure.class,()->excel.read(1L,"DRAFT",file(data)));assertEquals("EMPTY_REGISTRATION",error.code);assertEquals(422,error.status);assertTrue(error.getMessage().contains(ShowtimeExcel.REGISTRATION));}
    }
    @Test void partialRowsKeepTheirExactExcelRowAndExtraCellsAreNotSilentlySkipped()throws Exception{
        var excel=fixture(0);try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(excel)))){var sheet=book.getSheet(ShowtimeExcel.REGISTRATION);sheet.getRow(100).getCell(0).setCellValue("M1");var result=excel.read(1L,"DRAFT",file(bytes(book)));assertEquals(1,result.rows().size());assertEquals(101,result.rows().getFirst().sourceRow());assertEquals("",result.rows().getFirst().date());assertEquals("",result.rows().getFirst().roomId());sheet.getRow(101).createCell(8).setCellValue("không đúng cột");var error=assertThrows(IllegalArgumentException.class,()->excel.read(1L,"DRAFT",file(bytes(book))));assertTrue(error.getMessage().contains("dòng 102"));assertTrue(error.getMessage().contains(ShowtimeExcel.REGISTRATION));}
    }
    @Test void versionOneStillTreatsEveryRowAsNewAndReturnsWarning()throws Exception{
        try(var book=new XSSFWorkbook()){
            var help=book.createSheet("Hướng dẫn");help.createRow(0).createCell(1).setCellValue("1");help.createRow(1).createCell(1).setCellValue("1");var sheet=book.createSheet(ShowtimeExcel.LEGACY);var header=sheet.createRow(0);for(int n=0;n<7;n++)header.createCell(n).setCellValue(HEADERS[n]);book.createSheet("Danh mục");fill(sheet,1);fill(sheet,3);
            var result=new ShowtimeExcel(null).readWorkbook(1L,"DRAFT",file(bytes(book)));assertEquals(2,result.input().rows().size());assertEquals(1,result.excel().templateVersion());assertEquals(ShowtimeExcel.LEGACY,result.excel().sheetName());assertTrue(result.excel().warning().contains("mọi dòng"));
        }
    }
    @Test void versionTwoNeverFallsBackToLegacySheetAndRejectsWrongHeaders()throws Exception{
        var excel=fixture(0);try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(excel)))){
            fill(book.getSheet(ShowtimeExcel.REGISTRATION),1);book.getSheet(ShowtimeExcel.REGISTRATION).getRow(0).getCell(0).setCellValue("Sai cột");assertThrows(IllegalArgumentException.class,()->excel.read(1L,"DRAFT",file(bytes(book))));
            book.setSheetName(2,ShowtimeExcel.LEGACY);var error=assertThrows(IllegalArgumentException.class,()->excel.read(1L,"DRAFT",file(bytes(book))));assertTrue(error.getMessage().contains("Thiếu sheet "+ShowtimeExcel.REGISTRATION));
        }
    }
    @Test void fiveHundredAndOneNewRowsAreRejectedIndependentlyOfReferenceCount()throws Exception{
        var excel=fixture(1);try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(excel)))){for(int n=1;n<=501;n++)fill(book.getSheet(ShowtimeExcel.REGISTRATION),n);var error=assertThrows(IllegalArgumentException.class,()->excel.read(1L,"DRAFT",file(bytes(book))));assertTrue(error.getMessage().contains("500 suất mới"));}
    }
    @Test void oversizedFileIsRejectedBeforeParsing(){
        var excel=new ShowtimeExcel(null);
        var failure=assertThrows(ShowtimeBatch.Failure.class,()->excel.read(1L,"DRAFT",new MockMultipartFile("file","big.xlsx","",new byte[5*1024*1024+1])));
        assertEquals(413,failure.status);
    }
    @Test void highlyCompressedWorkbookIsRejectedBeforeWorkbookAllocation()throws Exception{
        var output=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));byte[] zeros=new byte[8192];for(int n=0;n<=4096;n++)zip.write(zeros);zip.closeEntry();}
        assertTrue(output.size()<5*1024*1024);
        var failure=assertThrows(IllegalArgumentException.class,()->new ShowtimeExcel(null).read(1L,"DRAFT",new MockMultipartFile("file","compressed.xlsx","",output.toByteArray())));
        assertTrue(failure.getMessage().contains("giải nén"));
    }
    @Test void versionThreeUsesSevenRealDatesAndOnlyExpandsNewHours()throws Exception{
        var excel=fixture(600);LocalDate week=LocalDate.of(2026,11,30);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(excel.exportWeek(1L,week,false)))){
            assertEquals("3",book.getSheet("Hướng dẫn").getRow(0).getCell(1).getStringCellValue());assertEquals(600,book.getSheet(ShowtimeExcel.REFERENCE).getLastRowNum());
            var sheet=book.getSheet(ShowtimeExcel.REGISTRATION);assertEquals(500,sheet.getLastRowNum());assertEquals(12,sheet.getRow(0).getLastCellNum());assertTrue(sheet.getProtect());assertFalse(sheet.getRow(1).getCell(2).getCellStyle().getLocked());
            for(int d=0;d<7;d++)assertEquals(week.plusDays(d),sheet.getRow(0).getCell(d+2).getLocalDateTimeCellValue().toLocalDate());
            var row=sheet.getRow(2);row.getCell(0).setCellValue("M1 — Đổi tên vẫn đúng mã");row.getCell(1).setCellValue("R1");row.getCell(2).setCellValue("09:00; 11:55\n15:30");row.getCell(3).setCellValue(20d/24);row.getCell(11).setCellValue(15000);
            var parsed=excel.readWorkbook(1L,"DRAFT",file(bytes(book)),week);assertEquals(4,parsed.input().rows().size());assertEquals(3,parsed.excel().templateVersion());assertEquals(4,parsed.excel().cells().size());assertEquals(List.of("09:00","11:55","15:30","20:00"),parsed.input().rows().stream().map(ShowtimeBatch.Line::time).toList());
            var source=parsed.excel().cells().get(parsed.input().rows().get(1).rowKey());assertEquals("C3",source.cell());assertEquals(2,source.token());assertEquals("đăng-ký.xlsx",source.fileName());assertEquals("15000",parsed.input().rows().getFirst().surcharge());
            java.nio.file.Files.write(java.nio.file.Path.of("target/weekly-matrix-excel-v3.xlsx"),bytes(book));
        }
    }
    @Test void versionThreeKeepsIncompleteRowsBadTokensAndFormulas()throws Exception{
        var excel=fixture(0);try(var book=new XSSFWorkbook(new ByteArrayInputStream(excel.exportWeek(1L,LocalDate.of(2026,12,7),true)))){
            var sheet=book.getSheet(ShowtimeExcel.REGISTRATION);sheet.getRow(1).getCell(0).setCellValue("M1");sheet.getRow(4).getCell(3).setCellValue("11:55, sai giờ");sheet.getRow(5).getCell(8).setCellFormula("1/2");
            var parsed=excel.readWorkbook(1L,"DRAFT",file(bytes(book)));assertEquals(4,parsed.input().rows().size());assertEquals("",parsed.input().rows().getFirst().time());assertEquals(List.of(2,5,5,6),parsed.input().rows().stream().map(ShowtimeBatch.Line::sourceRow).toList());assertTrue(parsed.input().rows().getLast().time().contains("Công thức"));assertEquals("I6",parsed.excel().cells().get(parsed.input().rows().getLast().rowKey()).cell());
        }
    }
    @Test void versionThreeEmptyFormAndWrongWeekOrHeadersAreRejected()throws Exception{
        var excel=fixture(100);LocalDate week=LocalDate.of(2026,12,7);byte[] bytes=excel.exportWeek(1L,week,false);
        assertEquals("EMPTY_REGISTRATION",assertThrows(ShowtimeBatch.Failure.class,()->excel.readWorkbook(1L,"DRAFT",file(bytes),week)).code);
        assertTrue(assertThrows(IllegalArgumentException.class,()->excel.readWorkbook(1L,"DRAFT",file(bytes),week.plusDays(7))).getMessage().contains("File thuộc tuần"));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){book.getSheet(ShowtimeExcel.REGISTRATION).getRow(0).getCell(2).setCellValue(week.plusDays(1).atStartOfDay());assertTrue(assertThrows(IllegalArgumentException.class,()->excel.read(1L,"DRAFT",file(bytes(book)))).getMessage().contains("Bảy cột ngày"));}
        assertThrows(IllegalArgumentException.class,()->excel.exportWeek(1L,week.plusDays(1),true));
    }
    @Test void versionThreeLimitCountsExpandedShowsNotWorksheetRows()throws Exception{
        var excel=fixture(0);try(var book=new XSSFWorkbook(new ByteArrayInputStream(excel.exportWeek(1L,LocalDate.of(2026,12,7),true)))){
            var row=book.getSheet(ShowtimeExcel.REGISTRATION).getRow(1);row.getCell(0).setCellValue("M1");row.getCell(1).setCellValue("R1");row.getCell(2).setCellValue(String.join(";",Collections.nCopies(500,"11:55")));assertEquals(500,excel.read(1L,"DRAFT",file(bytes(book))).rows().size());row.getCell(3).setCellValue("12:00");assertTrue(assertThrows(IllegalArgumentException.class,()->excel.read(1L,"DRAFT",file(bytes(book)))).getMessage().contains("500 suất mới"));
        }
    }
    @Test void legacyRowsOutsideSelectedWeekAreRejectedWithoutDroppingRows()throws Exception{
        var excel=fixture(0);try(var book=new XSSFWorkbook(new ByteArrayInputStream(export(excel)))){fill(book.getSheet(ShowtimeExcel.REGISTRATION),1);var error=assertThrows(IllegalArgumentException.class,()->excel.readWorkbook(1L,"DRAFT",file(bytes(book)),LocalDate.of(2026,12,21)));assertTrue(error.getMessage().contains("ngoài tuần"));assertEquals(1,excel.read(1L,"DRAFT",file(bytes(book))).rows().size());}
    }
}
