package kappa.spring4.mvc.utils;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.SimpleDateFormat;
import java.util.*;

@Component("fudutil")
public class FileUpDownUtil {

    // 자바 웹 프로그래밍의 파일업로드 지원
    // 1. oreilly사의 '자바 서블릿 프로그래밍' 도서의 예제 - cos.jar
    // 2. apache commons project - FileUpload (추천!)
    //  => commons.apache.org
    // 3. Servlet 3.x부터 파일업로드 내장 - MultipartFile

    private String uploadPath = "c:/Java/pdsupload/";
    //private String uploadPath = "/home/centos/pdsupload/";
    // 파일 업로드시 저장된 경로 지정

    // 업로드 처리 메서드
    public Map<String, String> procUpload(HttpServletRequest req) {

        Map<String, String> frmdata = new HashMap<>();

        String fname = "";

        // 요청한 폼데이터가 multipart 인지 확인
        RequestContext rctx = new ServletRequestContext(req);
        boolean isMultipart = FileUpload.isMultipartContent(rctx);

        try {
            if (isMultipart) { // 클라이언트 요청이 multipart라면
                // 업로드 처리 객체 생성
                DiskFileItemFactory df = new DiskFileItemFactory();
                ServletFileUpload upload = new ServletFileUpload(df);

                // 클라이언트 요청정보를 리스트에 저장
                List items = upload.parseRequest(req);
                Iterator<FileItem> params = items.iterator();

                // ex) fname => jobs.txt
                // 겹치치 않는 파일명을 위해 유니크한 임의의 값 생성 1
                //UUID uuid = UUID.randomUUID();

                // 겹치치 않는 파일명을 위해 유니크한 임의의 값 생성 2
                String fmt = "yyyyMMddHHmmss";
                SimpleDateFormat sdf = new SimpleDateFormat(fmt);
                String uuid = sdf.format(new Date());
                frmdata.put("uuid", uuid);

                // 리스트에 저장된 요청정보를 하나씩 꺼내서
                // 폼 데이터의 유형에 따라 각각 처리
                while(params.hasNext()) {
                    try {
                        FileItem item = (FileItem) params.next();

                        if (item.isFormField()) { // 텍스트 데이터라면
                            String name = item.getFieldName();
                            String val = item.getString("utf-8");
                            frmdata.put(name, val);
                        } else { // 파일 데이터라면
                            String ufname = item.getName(); // 파일 경로 추출

                            // 첨부파일이 없는 경우 if문 이후 코드 실행 안함
                            if (ufname.equals("") || ufname == null)
                                    continue;

                            // ex) ufname => c:\User\Downloads\jobs.txt
                            fname = ufname.substring(
                                    ufname.lastIndexOf("\\") + 1 ); // 파일명 추출

                            String fnames[] = fname.split("[.]");
                            //fname = fnames[0] + uuid.toString() + "." + fnames[1];
                            // jobs.txt => jobs + 20220117755735 + . + txt
                            fname = fnames[0] + uuid + "." + fnames[1];

                            // ex) fname => jobs123456789.txt
                            // ex) f => c:/Java/pdsupload/jobs123456789.txt
                            File f = new File(uploadPath + "/" + fname);
                            // 업로드한 파일을 시스템에 저장할때는 파일명+uuid 로 설정
                            item.write(f);  // 지정한 경로에 파일 저장

                            String name = item.getFieldName();
                            // 업로드한 파일정보를 디비에 저장할때는 원본파일명 그대로 설정
                            frmdata.put(name, fnames[0] + "." + fnames[1]);


                            // 파일 기타정보 처리
                            long fsize = item.getSize() / 1024;
                            String ftype = fnames[1].toLowerCase();

                            frmdata.put(name+"size", fsize+"");  // 파일크기
                            frmdata.put(name+"type", ftype);     // 파일확장자

                            // 파일명 처리 결과 확인
                            System.out.println(ufname + "/" + fname);
                            System.out.println(fsize + "/" + ftype);
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } // while

            } // if
        } catch (Exception ex) {
            ex.printStackTrace();
        } // try

        return frmdata;
    }

    // 다운로드 처리 메서드
    public void procDownload(
            HttpServletRequest req,
            HttpServletResponse res) throws IOException {

        // 파일명이 한글인 경우를 대비해서 작성해 둠
        req.setCharacterEncoding("utf-8");

        // 다운로드할 파일명을 알아냄
        String fName = req.getParameter("f");

        // HTTP 응답을 위해 stream 관련 변수 선언
        InputStream is = null;
        OutputStream os = null;
        File f = null;

        try {
            boolean skip = false;

            // 다운로드할 파일의 실제 위치 파악하고
            // 파일의 내용을 stream으로 미리 읽어둠
            try {
                f = new File(uploadPath, fName);
                is = new FileInputStream(f);
            } catch (Exception ex) {
                skip = true;
            }

            // HTTP 응답을 위한 준비작업
            res.reset();
            res.setContentType("application/octet-stream");
            // 응답 스트림의 내용은 이진형태로 구성되었음
            res.setHeader("Content-Description", "FileDownload");
            // 다운로드를 위해 임의로 작성

            if (!skip) {  // 다운로드할 파일이 존재한다면

                // 파일명이 한글인 경우 제대로 표시할 수 있도록
                // utf-8로 변환함
                fName = new String(
                        fName.getBytes("utf-8"),
                        "iso-8859-1");

                // 클릭시 다운로드 대화상자에 표시할 내용 정의
                res.setHeader("Content-Disposition",
                        "attachment; filename=\"" + fName + "\"");
                res.setHeader("Content-Type",
                        "application/octet-stream; charset=utf-8");
                res.setHeader("Content-Length", f.length() + "");

                // Http 응답으로 파일의 내용을 스트림으로 전송함
                os = res.getOutputStream();
                // 파일의 내용을 byte 배열에 저장함
                byte b[] = new byte[(int) f.length()];
                int cnt = 0;

                // 1byte씩 http 응답 스트림으로 쏨
                while ((cnt = is.read(b)) > 0) {
                    os.write(b, 0, cnt);
                }

            } else {  // 다운로드할 파일이 없다면
                res.setContentType("text/html; charset=utf-8");
                PrintWriter out = res.getWriter();
                out.print("<h1>다운로드할 파일이 없어요!!</h1>");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (os != null) os.close();
            if (is != null) is.close();
        } // try

    } // method

    // 다운로드 처리 메서드V2
    public void procDownloadV2(
            String fname, String uuid,
            HttpServletResponse res) throws IOException {

        // 다운로드할 파일이름 조합
        int pos = fname.lastIndexOf(".");
        String fName = fname.substring(0, pos) + uuid +
                       "." + fname.substring(pos+1);

        // HTTP 응답을 위해 stream 관련 변수 선언
        InputStream is = null;   // 파일 읽기
        OutputStream os = null;  // 파일 쓰기(전송)
        File f = null;

        try {
            boolean skip = false;

            // 다운로드할 파일의 실제 위치 파악하고
            // 파일의 내용을 stream으로 미리 읽어둠
            try {
                f = new File(uploadPath, fName);
                is = new FileInputStream(f);
            } catch (Exception ex) {
                skip = true;
            }

            // HTTP 응답을 위한 준비작업
            res.reset();
            res.setContentType("application/octet-stream");
            // 응답 스트림의 내용은 이진형태로 구성되었음
            res.setHeader("Content-Description", "FileDownload");
            // 다운로드를 위해 임의로 작성

            if (!skip) {  // 다운로드할 파일이 존재한다면

                // 파일명이 한글인 경우 제대로 표시할 수 있도록
                // utf-8로 변환함
                fname = new String(
                        fname.getBytes("utf-8"),
                        "iso-8859-1");

                // 클릭시 다운로드 대화상자에 표시할 내용 정의
                res.setHeader("Content-Disposition",
                        "attachment; filename=\"" + fname + "\"");
                res.setHeader("Content-Type",
                        "application/octet-stream; charset=utf-8");
                res.setHeader("Content-Length", f.length() + "");

                // Http 응답으로 파일의 내용을 스트림으로 전송함
                os = res.getOutputStream();
                // 파일의 내용을 byte 배열에 저장함
                byte b[] = new byte[(int) f.length()];
                int cnt = 0;

                // 1byte씩 http 응답 스트림으로 쏨
                while ((cnt = is.read(b)) > 0) {
                    os.write(b, 0, cnt);
                }

            } else {  // 다운로드할 파일이 없다면
                res.setContentType("text/html; charset=utf-8");
                PrintWriter out = res.getWriter();
                out.print("<h1>다운로드할 파일이 없어요!!</h1>");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (os != null) os.close();
            if (is != null) is.close();
        } // try

    } // method


    // MultipartFile로 구현된 업로드 처리 소스
    public String procUpload2(MultipartFile mf, String uuid) {
        String ofname = mf.getOriginalFilename();  // 원 파일명 가져옴

        // abc.txt          : fnames[1] => txt
        // abc.123.xyz.png  : fnames[3] => png
        int pos = ofname.lastIndexOf(".");
        String ftype = ofname.substring(pos + 1);
        String fname = ofname.substring(0, pos);

        String nfname = fname + uuid + "." + ftype;  // 새로운 파일명 생성

        try {
            mf.transferTo(new File(uploadPath + nfname));
            // 지정한 위치로 파일 저장

            // 업로드한 파일 퍼미션 지정 (리눅스 한정)
//            String perms = "rwxr-xr-x";
//            Path img = Paths.get(uploadPath + nfname);
//            Set<PosixFilePermission> pfp = PosixFilePermissions.fromString(perms);
//            Files.setPosixFilePermissions(img, pfp);  // linux

        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return ofname + "/" + (mf.getSize()/1024) + "/" + ftype;
    }

    // 겹치치 않는 파일명을 위해 유니크한 임의의 값 생성
    public String makeUUID() {
        String fmt = "yyyyMMddHHmmss";
        SimpleDateFormat sdf = new SimpleDateFormat(fmt);

        return sdf.format(new Date());
    }

}
