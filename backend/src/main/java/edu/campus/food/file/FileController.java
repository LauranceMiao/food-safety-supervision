package edu.campus.food.file;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService files;

    public FileController(FileService files) { this.files = files; }

    @PostMapping
    public Map<String, Object> upload(@RequestParam MultipartFile file, @RequestParam String purpose,
                                       @RequestParam long canteenId) throws IOException {
        return files.upload(file, purpose, canteenId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
        return files.download(id);
    }
}
