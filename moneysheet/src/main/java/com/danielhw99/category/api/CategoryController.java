package com.danielhw99.category.api;

import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.category.service.CategoryService;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    @Operation(summary = "카테고리 조회")
    public ResponseEntity<CategoriesResponse> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) CategoryScopeValue scope,
            @RequestParam(required = false) CategoryDirectionValue direction) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CategoriesResponse(categories.list(principal.userId(), scope, direction)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "카테고리 이름 수정", description = "이름만 수정합니다. 중복 이름은 409 CONFLICT/name 오류, 타인·미존재 ID는 404입니다.")
    public ResponseEntity<CategoryResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id, @Valid @RequestBody CategoryNameInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(categories.update(principal.userId(), parseId(id), input.name()));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    @Operation(summary = "카테고리 삭제", description = "예산·거래는 보존하고 미분류로 전환합니다. 이미 삭제됐거나 타인 소유인 ID는 404입니다.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        categories.delete(principal.userId(), parseId(id));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private Long parseId(String id) {
        try { return ApiId.parse(id).value(); }
        catch (IllegalArgumentException error) { throw new ApiException(ErrorCode.VALIDATION); }
    }

    @PostMapping
    @Operation(summary = "카테고리 생성 또는 재사용")
    public ResponseEntity<CategoryResponse> createOrReuse(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CategoryInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(categories.createOrReuse(principal.userId(), input));
    }
}
