package com.shifu.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.shifu.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/media")
public class MediaController {

    /**
     * 图片上传根目录，指向 nginx 静态资源目录。
     * 原来写死在 SystemConstants 里，换个部署路径就得改代码，已改为配置项。
     */
    @Value("${shifu.upload.dir}")
    private String uploadDir;

    @PostMapping("note")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(uploadDir, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/note/delete")
    public Result deleteNoteImg(@RequestParam("name") String filename) {
        try {
            File root = new File(uploadDir).getCanonicalFile();
            File file = new File(root, filename).getCanonicalFile();
            // 名称来自请求参数，必须落在上传根目录内，避免 ../ 删到目录外的文件
            if (!file.toPath().startsWith(root.toPath())) {
                return Result.fail("错误的文件名称");
            }
            if (file.isDirectory()) {
                return Result.fail("错误的文件名称");
            }
            FileUtil.del(file);
            return Result.ok();
        } catch (IOException e) {
            throw new RuntimeException("文件删除失败", e);
        }
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(uploadDir, StrUtil.format("/notes/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/notes/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
