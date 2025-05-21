package com.example.datasetmanager.service;

import io.minio.*;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class FileService {
    @Autowired
    private MinioClient minioClient;

    private String bucket = "datasets";


    public void uploadFile(MultipartFile file, String objectName) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
    }

    public void uploadChunk(String uploadId, int partNumber, byte[] chunk, String objectName) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName + "/" + uploadId + "/" + partNumber)
                .stream(new ByteArrayInputStream(chunk), chunk.length, -1)
                .build());
    }

    public void completeMultipartUpload(String uploadId, String objectName, List<Integer> partNumbers) throws Exception {
        List<ComposeSource> sources = new ArrayList<>();
        for (Integer partNumber : partNumbers) {
            sources.add(ComposeSource.builder()
                    .bucket(bucket)
                    .object(objectName + "/" + uploadId + "/" + partNumber)
                    .build());
        }

        minioClient.composeObject(ComposeObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .sources(sources)
                .build());

        // 清理分片文件
        for (Integer partNumber : partNumbers) {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName + "/" + uploadId + "/" + partNumber)
                    .build());
        }
    }

    public void extractAndUploadZip(MultipartFile file, String objectPrefix) throws Exception {
        // 标准化前缀，确保末尾没有多余的 "/"
        if (objectPrefix.endsWith("/")) {
            objectPrefix = objectPrefix.substring(0, objectPrefix.length() - 1);
        }

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];

            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    // 清理 entry 名称，防止路径穿越攻击
                    String entryName = entry.getName().replace("\\", "/"); // Windows 压缩路径
                    if (entryName.contains("..")) continue;

                    // 拼接 MinIO 中的对象名（带前缀）
                    String objectName = objectPrefix + "/" + entryName;

                    // 使用 Piped 流式上传（避免一次性缓存在内存中）
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                        minioClient.putObject(PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(bais, baos.size(), -1)
                                .contentType("application/octet-stream")
                                .build());
                    }

                    zis.closeEntry();
                }
            }
        }
    }


    public List<Map<String, Object>> listFiles(String prefix) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();

        Set<String> seenDirs = new HashSet<>(); // 避免重复添加目录
        if (prefix.equals("/")){
            prefix ="";
        }
        Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(false) // 不递归，只看当前层
                .build());

        for (Result<Item> object : objects) {
            Item item = object.get();
            String fullName = item.objectName();

            String remaining = fullName.substring(prefix.length());

            if (remaining.contains("/")) {
                // 是目录（因为 recursive=false，只能看到一层）
                String dirName = remaining.substring(0, remaining.indexOf("/") + 1);
                if (seenDirs.add(dirName)) { // 避免重复
                    Map<String, Object> dirInfo = new HashMap<>();
                    dirInfo.put("name", dirName);
                    dirInfo.put("isDir", true);
                    dirInfo.put("size", 0);
                    dirInfo.put("lastModified", null);
                    result.add(dirInfo);
                }
            } else {
                // 是文件
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", remaining);
                fileInfo.put("isDir", false);
                fileInfo.put("size", item.size());
                fileInfo.put("lastModified", item.lastModified());
                result.add(fileInfo);
            }
        }

        return result;
    }

    public InputStream downloadFile(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }

    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }
} 