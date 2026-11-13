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

    @PostMapping
    @Operation(summary = "카테고리 생성 또는 재사용")
    public ResponseEntity<CategoryResponse> createOrReuse(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CategoryInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(categories.createOrReuse(principal.userId(), input));
    }
}
