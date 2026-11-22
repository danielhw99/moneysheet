package com.danielhw99.user.api;

import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.user.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me/avatar")
public class AvatarController {
    private final AvatarService service;

    public AvatarController(AvatarService service) { this.service = service; }

    @GetMapping(produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "본인 프로필 사진 조회", description = "현재 로그인한 본인의 사진만 조회합니다. 사진이 없으면 404입니다. 응답은 저장하거나 공유 캐시하지 않습니다.")
    public ResponseEntity<byte[]> read(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control", "no-store")
            .header("X-Content-Type-Options", "nosniff").body(service.read(principal.userId()));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로필 사진 저장", description = "file에 JPEG/PNG(최대 5MiB)를 전달합니다. 서버가 중앙 정사각형으로 잘라 256×256 JPEG로 재인코딩합니다. 최대 가로/세로 8192px, 2400만 화소이며 초과 업로드는 413입니다. 변경된 Profile을 반환하며 기존 로그인은 유지됩니다.")
    public Profile save(@AuthenticationPrincipal AuthPrincipal principal, @RequestPart("file") MultipartFile file) {
        return service.save(principal.userId(), file);
    }

    @DeleteMapping
    @Operation(summary = "프로필 사진 삭제", description = "사진이 없는 경우에도 성공합니다. avatarUrl이 null인 Profile을 200으로 반환하며 기본 프로필로 복원됩니다.")
    public Profile delete(@AuthenticationPrincipal AuthPrincipal principal) { return service.delete(principal.userId()); }
}
