package com.example.dicombridge.controller.image;

import com.example.dicombridge.domain.dto.thumbnail.ThumbnailWithFileDto;

import com.example.dicombridge.service.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@RestController
@RequestMapping("/studies")
@RequiredArgsConstructor
public class ImageRestController {
    private final ImageService imageService;

    /** Thumbnale **/
    @PostMapping("/getThumbnail/{studyKey}")
    public ResponseEntity<Map<String, ThumbnailWithFileDto>> getThumbnailData(@PathVariable String studyKey) throws IOException {
        Map<String, ThumbnailWithFileDto> images = imageService.getThumbnail(Integer.valueOf(studyKey));

        if (!images.isEmpty()) {
            return new ResponseEntity<>(images, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /** Seriesinsuid Count Check **/
    @GetMapping("/getSeriesInsUidCount/{seriesInsUid}")
    public ResponseEntity<Integer> seriesinsuidCount(@PathVariable String seriesInsUid) throws IOException{
        int count = imageService.seriesinsuidCount(seriesInsUid);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return ResponseEntity.ok().headers(headers).body(count);
    }

    /** Seriesinsuid Image By Count **/
    @GetMapping("/getSeriesInsUidIndex/{seriesInsUid}/{order}")
    public ResponseEntity<byte[]> getSeriesInsUidIndex(@PathVariable String seriesInsUid,
                                                       @PathVariable int order) throws IOException {
        File file = imageService.getFileByseriesinsuidNcount(seriesInsUid, order);

        byte[] data = Files.readAllBytes(file.toPath());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", seriesInsUid + ".jpg");

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /** Series Count **/
    @PostMapping("/seriescount/{studyinsuid}")
    public int seriesCount(@PathVariable("studyinsuid") String studyinsuid){
        return imageService.findMaxStudyKeyByStudyKey(studyinsuid);
    }

    /** Download **/
    @PostMapping("/download/{studyKey}")
    public ResponseEntity<byte[]> downloadImages(@PathVariable int studyKey) {
        try {
            List<ByteArrayOutputStream> imageStreams = imageService.getFiles(studyKey);
            byte[] zipFileData = imageService.createZipFile(imageStreams, studyKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", studyKey + ".zip");

            return new ResponseEntity<>(zipFileData, headers, HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Seriesinsuids By Studyinsuid **/
    @GetMapping("/getSeriesInsUids/{studyInsUid}")
    public List<String> getSeriesInsUids(@PathVariable String studyInsUid) {
        List<String> images = imageService.getSeriesInsUids(studyInsUid);
        return images;
    }

    /** Redis **/
    @PostMapping("/saveRedisValSeriesinsuid/{studyinsuid}")
    public List<String> saveRedisValSeriesinsuid (@PathVariable String studyinsuid) throws IOException {
        List<String> list = imageService.saveRedisValSeriesinsuid(studyinsuid);
        String keyname = studyinsuid;
        Jedis jedis = new Jedis("localhost", 6379);
        jedis.set(keyname, String.join(",", list));
        for(int i=0; i<list.size(); i++){
            List<File> image = imageService.getComparisonImage(list.get(i));
            String key = list.get(i); // seriesinsuid
            jedis.set(key, String.valueOf(image.size()));
            for(int j=0; j<image.size();j++){
                String uniqueKey = key + ":" + j;
                File file = image.get(j);
                byte[] data = Files.readAllBytes(file.toPath());
                jedis.set(uniqueKey.getBytes(),data);
                System.out.println("저장할 때 유니크키 : "+uniqueKey+" 값의 길이" +
                        " : "+data.length);
            }
        }
        //키:studyinsuid 벨류:seriesinsuid의 종류를 ,로 나눠서 저장
        // 저장된 값을 다시 리스트로 변환
        //List<String> retrievedList = Arrays.asList(storedValue.split(","));
        //키:seriesinsuid 벨류:seriesinsuid의 사진 갯수
        //키:seriesinsuid:이미지번호.getBytes() 벨류:이미지 바이트
        return null;
    }

    @GetMapping("/getSeriesInsUidIndexComparison/{seriesInsUid}/{order}")
    public ResponseEntity<byte[]> getSeriesInsUidIndexComparison(@PathVariable String seriesInsUid,
                                                       @PathVariable int order) throws IOException {
        String key = seriesInsUid;
        Jedis jedis = new Jedis("localhost", 6379);
        String seriesCountStr = jedis.get(key); // 시리즈 갯수
        int seriesCount = Integer.parseInt(seriesCountStr);
        if(order<=seriesCount){
            String uniqueKey = key + ":" + order;
            byte[] data = jedis.get(uniqueKey.getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", seriesInsUid + ".jpg");

            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        }

        return null;
    }

    @GetMapping("/getSeriesInsUidsComparison/{studyinsuidComparison}")
    public List<String> getSeriesInsUidsComparison(@PathVariable String studyinsuidComparison) {
        String keyname = studyinsuidComparison;
        // Redis 서버에 연결
        Jedis jedis = new Jedis("localhost", 6379);
        String storedData = jedis.get(keyname);
        List<String> retrievedList = Arrays.asList(storedData.split(","));
        return retrievedList;
    }
}
