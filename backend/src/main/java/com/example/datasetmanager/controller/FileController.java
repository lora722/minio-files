package com.example.datasetmanager.controller;

import com.example.datasetmanager.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class FileController {
    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "path", defaultValue = "") String path) {
        try {
            String objectName = path + file.getOriginalFilename();
            if (file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
                fileService.extractAndUploadZip(file, objectName);
            } else {
                fileService.uploadFile(file, objectName);
            }
            return ResponseEntity.ok("File uploaded successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/upload/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("partNumber") int partNumber,
            @RequestParam("path") String path) {
        try {
            fileService.uploadChunk(uploadId, partNumber, chunk.getBytes(), path);
            return ResponseEntity.ok("Chunk uploaded successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Chunk upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/upload/complete")
    public ResponseEntity<String> completeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("path") String path,
            @RequestParam("partNumbers") List<Integer> partNumbers) {
        try {
            fileService.completeMultipartUpload(uploadId, path, partNumbers);
            return ResponseEntity.ok("Upload completed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Upload completion failed: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listFiles(
            @RequestParam(value = "path", defaultValue = "") String path) {
        try {
            return ResponseEntity.ok(fileService.listFiles(path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @RequestParam("path") String path) {
        try {
            InputStream inputStream = fileService.downloadFile(path);
            String fileName = path.substring(path.lastIndexOf("/") + 1);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            
            // 根据文件类型设置 Content-Type
            String contentType = getContentType(fileName);
            headers.setContentType(MediaType.parseMediaType(contentType));
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<String> deleteFile(@RequestParam("path") String path) {
        try {
            fileService.deleteFile(path);
            return ResponseEntity.ok("File deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Delete failed: " + e.getMessage());
        }
    }

    private String getContentType(String fileName) {
        String extension = fileName.toLowerCase().substring(fileName.lastIndexOf(".") + 1);
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "pdf":
                return "application/pdf";
            case "txt":
                return "text/plain";
            case "html":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "application/javascript";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            default:
                return "application/octet-stream";
        }
    }
}